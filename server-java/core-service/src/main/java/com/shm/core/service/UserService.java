package com.shm.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.common.constant.CacheConstants;
import com.shm.common.constant.RedisKeys;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.dto.user.ContactDTO;
import com.shm.common.model.dto.user.UpdateProfileRequest;
import com.shm.common.model.dto.user.UserPublicDTO;
import com.shm.common.model.entity.User;
import com.shm.common.util.SensitiveWordFilter;
import com.shm.core.repository.ReviewRepository;
import com.shm.core.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户服务（与 Node.js services/user.js 行为完全一致）
 *
 * <p>处理用户公开信息查询、客服/管理员联系方式、个人资料编辑。
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    // 缓存 TTL 常量参见 CacheConstants（P2-1：消除双重定义）

    private final UserRepository userRepo;
    private final ReviewRepository reviewRepo;
    private final SensitiveWordFilter sensitiveFilter;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public UserService(UserRepository userRepo, ReviewRepository reviewRepo,
                       SensitiveWordFilter sensitiveFilter, StringRedisTemplate redis,
                       ObjectMapper objectMapper) {
        this.userRepo = userRepo;
        this.reviewRepo = reviewRepo;
        this.sensitiveFilter = sensitiveFilter;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * 查看用户公开信息（与 Node.js userService.getById 一致）
     *
     * <p>返回公开字段 + 评价聚合统计。
     * <p>Redis 缓存（Cache-Aside 模式）：TTL 10min ± jitter，防穿透空值缓存 1min。
     *
     * @param userId 目标用户 ID
     * @return 用户公开信息 + review_summary
     */
    public Map<String, Object> getPublicProfile(Long userId) {
        String cacheKey = RedisKeys.userPublicKey(userId);

        // 1. 尝试从缓存读取
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) {
                if (RedisKeys.EMPTY_PREFIX.equals(cached)) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
                }
                Map<String, Object> result = objectMapper.readValue(
                        cached, new TypeReference<Map<String, Object>>() {});
                log.debug("用户信息缓存命中: userId={}", userId);
                return result;
            }
        } catch (BusinessException e) {
            throw e; // 空值缓存 → 直接抛异常
        } catch (Exception e) {
            log.warn("用户信息缓存读取失败，降级查 DB: userId={}, error={}", userId, e.getMessage());
        }

        // 2. 缓存未命中，查 DB
        User user = userRepo.findPublicById(userId);
        if (user == null) {
            // 防穿透：缓存空值（短 TTL）
            try {
                redis.opsForValue().set(cacheKey, RedisKeys.EMPTY_PREFIX, CacheConstants.EMPTY_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (Exception ignored) { /* 缓存写入失败不阻塞业务 */ }
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        UserPublicDTO profile = UserPublicDTO.from(user);

        // 评价聚合统计
        Map<String, Object> avgScores = reviewRepo.getAvgScores(userId);
        Object total = avgScores != null ? avgScores.getOrDefault("total", 0L) : 0L;
        Object avgCommunication = avgScores != null ? avgScores.getOrDefault("avg_communication", null) : null;
        Object avgPunctuality = avgScores != null ? avgScores.getOrDefault("avg_punctuality", null) : null;
        Object avgAccuracy = avgScores != null ? avgScores.getOrDefault("avg_accuracy", null) : null;

        Map<String, Object> result = Map.of(
                "user", profile,
                "review_summary", Map.of(
                        "total", total,
                        "avg_communication", avgCommunication != null ? avgCommunication : 0,
                        "avg_punctuality", avgPunctuality != null ? avgPunctuality : 0,
                        "avg_accuracy", avgAccuracy != null ? avgAccuracy : 0
                )
        );

        // 3. 写入缓存（Cache-Aside）
        try {
            String json = objectMapper.writeValueAsString(result);
            long ttl = CacheConstants.cacheTtlWithJitter(
                    CacheConstants.USER_PUBLIC_TTL_SECONDS,
                    CacheConstants.USER_PUBLIC_JITTER_SECONDS);
            redis.opsForValue().set(cacheKey, json, ttl, TimeUnit.SECONDS);
            log.debug("用户信息缓存写入: userId={}, ttl={}s", userId, ttl);
        } catch (Exception e) {
            log.warn("用户信息缓存写入失败: userId={}, error={}", userId, e.getMessage());
        }

        return result;
    }

    /**
     * 获取客服联系方式（与 Node.js userService.getCSContact 一致）
     *
     * @return 客服的 id, nickname, avatar
     */
    public ContactDTO getCSContact() {
        User cs = userRepo.findCSUser();
        if (cs == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "客服不存在");
        }
        return ContactDTO.builder()
                .id(cs.getId())
                .nickname(cs.getNickname())
                .avatar(cs.getAvatar() != null ? cs.getAvatar() : "")
                .build();
    }

    /**
     * 获取管理员联系方式（与 Node.js userService.getAdminContact 一致）
     *
     * @return 管理员的 id, nickname, avatar
     */
    public ContactDTO getAdminContact() {
        User admin = userRepo.findAdminUser();
        if (admin == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "管理员不存在");
        }
        return ContactDTO.builder()
                .id(admin.getId())
                .nickname(admin.getNickname())
                .avatar(admin.getAvatar() != null ? admin.getAvatar() : "")
                .build();
    }

    /**
     * 编辑个人资料（与 Node.js userService.updateProfile 一致）
     *
     * <p>只更新白名单字段（nickname, avatar, class_name, dorm_building），
     * nickname 需通过敏感词过滤。
     *
     * @param userId  当前用户 ID
     * @param updates 要更新的字段（全部可选）
     * @return 更新后的用户信息
     */
    public Map<String, Object> updateProfile(Long userId, UpdateProfileRequest updates) {
        // 敏感词过滤：nickname
        if (updates.getNickname() != null && !updates.getNickname().isEmpty()) {
            if (sensitiveFilter.containsSensitive(updates.getNickname())) {
                throw new BusinessException(ErrorCode.SENSITIVE_WORD);
            }
        }

        // 只更新传入的非 null 字段（通过 User entity 传递）
        User user = new User();
        user.setId(userId);
        if (updates.getNickname() != null) user.setNickname(updates.getNickname());
        if (updates.getAvatar() != null) user.setAvatar(updates.getAvatar());
        if (updates.getClassName() != null) user.setClassName(updates.getClassName());
        if (updates.getDormBuilding() != null) user.setDormBuilding(updates.getDormBuilding());

        int affected = userRepo.updateProfile(user);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 重新查询返回最新数据
        User updated = userRepo.findById(userId);

        log.info("用户资料更新: userId={}", userId);

        // 清除用户缓存（资料变更后失效）
        evictUserCache(userId);

        return Map.of(
                "id", updated.getId(),
                "nickname", updated.getNickname(),
                "avatar", updated.getAvatar(),
                "class_name", updated.getClassName(),
                "dorm_building", updated.getDormBuilding()
        );
    }

    // ============================================================
    // Redis 缓存辅助方法（Phase 10）
    // ============================================================

    /** 清除用户缓存 */
    private void evictUserCache(Long userId) {
        try {
            String key = RedisKeys.userPublicKey(userId);
            redis.delete(key);
            log.debug("用户缓存已清除: userId={}", userId);
        } catch (Exception e) {
            log.warn("用户缓存清除失败，将在 TTL 后自动过期: userId={}, error={}", userId, e.getMessage());
        }
    }
}
