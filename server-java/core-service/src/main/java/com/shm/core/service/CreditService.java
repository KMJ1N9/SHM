package com.shm.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.entity.Notification;
import com.shm.common.model.entity.User;
import com.shm.core.config.CreditProperties;
import com.shm.core.repository.NotificationRepository;
import com.shm.core.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 信誉分服务（与 Node.js services/credit.js 行为完全一致）
 *
 * <p>处理信誉分查询、变动记录查询、信誉分变动（加分/扣分）。
 * <p>信誉分配置：发布阈值 60 / 交易阈值 30 / 上限 200 / 交易奖励 +2 / 好评奖励 +1 / 差评扣分 -5
 */
@Service
public class CreditService {

    private static final Logger log = LoggerFactory.getLogger(CreditService.class);

    private final UserRepository userRepo;
    private final NotificationRepository notificationRepo;
    private final ObjectMapper objectMapper;
    private final CreditProperties creditProps;

    public CreditService(UserRepository userRepo, NotificationRepository notificationRepo,
                        ObjectMapper objectMapper, CreditProperties creditProps) {
        this.userRepo = userRepo;
        this.notificationRepo = notificationRepo;
        this.objectMapper = objectMapper;
        this.creditProps = creditProps;
    }

    /**
     * 我的信誉分 + 变动记录（与 Node.js creditService.my 一致）
     *
     * <p>支持分页，pageSize 上限 50。
     */
    public Map<String, Object> my(Long userId, int page, int pageSize) {
        User user = userRepo.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        int offset = (page - 1) * pageSize;
        List<Notification> changeLogs = notificationRepo.listByUserId(userId, "credit_change", offset, pageSize);
        long total = notificationRepo.countByUserId(userId, "credit_change");

        List<Map<String, Object>> logList = changeLogs.stream()
                .map(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("reason", n.getContent());
                    m.put("metadata", parseJson(n.getMetadata()));
                    m.put("created_at", n.getCreatedAt());
                    return m;
                })
                .toList();

        return Map.of("score", user.getCreditScore(), "change_log", logList,
                "total", total, "page", page, "pageSize", pageSize);
    }

    /**
     * 查看某用户信誉分（公开）（与 Node.js creditService.userPublic 一致）
     */
    public Map<String, Object> userPublic(Long userId) {
        User user = userRepo.findPublicById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return Map.of("user_id", user.getId(), "score", user.getCreditScore());
    }

    /**
     * 信誉分变动（内部调用，供其他 service 使用，与 Node.js creditService.changeScore 一致）
     *
     * @param userId 用户 ID
     * @param delta  变动值（正+ / 负-）
     * @param reason 变动原因
     * @param refId  关联的业务 ID（可选）
     * @return 变动详情：userId, previousScore, currentScore, delta, reason
     */
    public Map<String, Object> changeScore(Long userId, int delta, String reason, Long refId) {
        User current = userRepo.findById(userId);
        if (current == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        int previousScore = current.getCreditScore();
        userRepo.updateCreditScore(userId, delta, creditProps.getMax());

        // 重新查询以获取更新后的分数
        User updated = userRepo.findById(userId);
        int currentScore = updated != null ? updated.getCreditScore() : previousScore + delta;

        // 写入变动通知（metadata 含完整信息）
        Map<String, Object> metadataMap = new LinkedHashMap<>();
        metadataMap.put("delta", delta);
        metadataMap.put("previous_score", previousScore);
        metadataMap.put("current_score", currentScore);
        metadataMap.put("reason", reason);
        if (refId != null) {
            metadataMap.put("ref_id", refId);
        }
        String metadata = toJson(metadataMap);

        Notification notification = Notification.builder()
                .userId(userId)
                .type("credit_change")
                .title("信誉分变动")
                .content(reason)
                .isRead(false)
                .metadata(metadata)
                .build();
        notificationRepo.insert(notification);

        log.info("信誉分变动: userId={}, delta={}, prev={}, curr={}, reason={}",
                userId, delta, previousScore, currentScore, reason);

        return Map.of("user_id", userId, "previous_score", previousScore,
                "current_score", currentScore, "delta", delta, "reason", reason);
    }

    // ---- 辅助方法 ----

    /** Object → JSON string */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            return "{}";
        }
    }

    /** 安全解析 JSON string → Object */
    private Object parseJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
