package com.shm.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.common.constant.CacheConstants;
import com.shm.common.constant.RedisKeys;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.dto.ProductSearchQuery;
import com.shm.common.model.dto.product.PublishProductRequest;
import com.shm.common.model.dto.product.UpdateProductRequest;
import com.shm.common.model.entity.Product;
import com.shm.common.model.entity.User;
import com.shm.common.model.page.PageResult;
import com.shm.common.util.SensitiveWordFilter;
import com.shm.core.config.CreditProperties;
import com.shm.core.repository.ProductRepository;
import com.shm.core.repository.ReviewRepository;
import com.shm.core.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 商品服务（与 Node.js services/product.js 行为完全一致）
 *
 * <p>处理商品列表、详情、发布、编辑、删除（软删除）、我发布的。
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private static final int MAX_IMAGES = 6;

    // 缓存 TTL 常量参见 CacheConstants（P2-1：消除双重定义）

    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final ReviewRepository reviewRepo;
    private final SensitiveWordFilter sensitiveFilter;
    private final ObjectMapper objectMapper;
    private final CreditProperties creditProps;
    private final StringRedisTemplate redis;

    public ProductService(ProductRepository productRepo, UserRepository userRepo,
                          ReviewRepository reviewRepo, SensitiveWordFilter sensitiveFilter,
                          ObjectMapper objectMapper, CreditProperties creditProps,
                          StringRedisTemplate redis) {
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.reviewRepo = reviewRepo;
        this.sensitiveFilter = sensitiveFilter;
        this.objectMapper = objectMapper;
        this.creditProps = creditProps;
        this.redis = redis;
    }

    // ============================================================
    // 商品列表
    // ============================================================

    /**
     * 商品列表（与 Node.js productService.list 一致）
     *
     * <p>支持关键词搜索 + 分类/成色/价格筛选 + 排序 + 偏移分页。
     * <p>Redis 缓存（Cache-Aside 模式）：命中直接返回，未命中查 DB 后写入缓存。
     * 写操作后清除所有商品列表缓存（{@link #evictProductListCache}）。
     */
    public Map<String, Object> list(ProductSearchQuery query, int page, int pageSize) {
        String cacheKey = buildListCacheKey(query, page, pageSize);

        // 1. 尝试从缓存读取
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) {
                if (RedisKeys.EMPTY_PREFIX.equals(cached)) {
                    // 空值缓存 — 防穿透，返回空结果
                    return buildEmptyListResult(page, pageSize);
                }
                Map<String, Object> result = objectMapper.readValue(
                        cached, new TypeReference<Map<String, Object>>() {});
                log.debug("商品列表缓存命中: key={}", cacheKey);
                return result;
            }
        } catch (Exception e) {
            log.warn("商品列表缓存读取失败，降级查 DB: key={}, error={}", cacheKey, e.getMessage());
        }

        // 2. 缓存未命中，查 DB
        int offset = (page - 1) * pageSize;
        List<Product> products = productRepo.listWithFilters(query, offset, pageSize);
        long total = productRepo.countWithFilters(query);

        // 批量获取卖家信息
        Set<Long> sellerIds = products.stream()
                .map(Product::getSellerId)
                .collect(Collectors.toSet());
        Map<Long, User> sellerMap = userRepo.findByIds(sellerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 转换为 API 格式
        List<Map<String, Object>> list = products.stream()
                .map(p -> toListRow(p, sellerMap.get(p.getSellerId())))
                .collect(Collectors.toList());

        PageResult<Map<String, Object>> pageResult = PageResult.of(list, total, page, pageSize);
        Map<String, Object> result = Map.of("list", pageResult.getList(), "total", pageResult.getTotal(),
                "page", pageResult.getPage(), "pageSize", pageResult.getPageSize());

        // 3. 写入缓存（Cache-Aside：先写 DB，再写缓存）
        try {
            String json = objectMapper.writeValueAsString(result);
            long ttl = cacheTtlWithJitter();
            if (total == 0) {
                // 空结果：缓存空值标记，短 TTL 防穿透
                redis.opsForValue().set(cacheKey, RedisKeys.EMPTY_PREFIX, CacheConstants.EMPTY_VALUE_TTL_SECONDS, TimeUnit.SECONDS);
                log.debug("商品列表空值缓存: key={}, ttl={}s", cacheKey, CacheConstants.EMPTY_VALUE_TTL_SECONDS);
            } else {
                redis.opsForValue().set(cacheKey, json, ttl, TimeUnit.SECONDS);
                log.debug("商品列表缓存写入: key={}, ttl={}s", cacheKey, ttl);
            }
        } catch (Exception e) {
            log.warn("商品列表缓存写入失败: key={}, error={}", cacheKey, e.getMessage());
        }

        return result;
    }

    // ============================================================
    // 商品详情
    // ============================================================

    /**
     * 商品详情（与 Node.js productService.detail 一致）
     *
     * <p>off_shelf 仅卖家和管理员可查看。
     */
    public Map<String, Object> detail(Long productId, Long currentUserId, String currentUserRole) {
        Product product = productRepo.findById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }

        // deleted 状态对所有用户不可见
        if ("deleted".equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }

        // off_shelf 权限判断
        if ("off_shelf".equals(product.getStatus())) {
            boolean isOwner = currentUserId != null && currentUserId.equals(product.getSellerId());
            boolean isAdmin = "admin".equals(currentUserRole);
            if (!isOwner && !isAdmin) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
            }
        }

        // 卖家信息
        User seller = userRepo.findById(product.getSellerId());

        // 评价摘要
        Map<String, Object> avgScores = reviewRepo.getAvgScores(product.getSellerId());

        Map<String, Object> sellerInfo = new LinkedHashMap<>();
        sellerInfo.put("id", seller != null ? seller.getId() : product.getSellerId());
        sellerInfo.put("nickname", seller != null ? seller.getNickname() : "");
        sellerInfo.put("avatar", seller != null ? seller.getAvatar() : "");
        sellerInfo.put("credit_score", seller != null ? seller.getCreditScore() : 0);
        sellerInfo.put("class_name", seller != null ? seller.getClassName() : "");
        sellerInfo.put("dorm_building", seller != null ? seller.getDormBuilding() : "");

        Map<String, Object> reviewSummary = new LinkedHashMap<>();
        reviewSummary.put("total", avgScores != null ? avgScores.getOrDefault("total", 0L) : 0L);
        reviewSummary.put("avg_communication", toDouble(avgScores, "avg_communication"));
        reviewSummary.put("avg_punctuality", toDouble(avgScores, "avg_punctuality"));
        reviewSummary.put("avg_accuracy", toDouble(avgScores, "avg_accuracy"));

        Map<String, Object> result = toDetailMap(product, sellerInfo, reviewSummary);
        return result;
    }

    // ============================================================
    // 发布商品
    // ============================================================

    /**
     * 发布商品（与 Node.js productService.create 一致）
     */
    @Transactional
    public Map<String, Object> create(Long sellerId, int creditScore, PublishProductRequest data) {
        // 信誉分检查
        if (creditScore < creditProps.getPublishThreshold()) {
            throw new BusinessException(ErrorCode.CREDIT_TOO_LOW_PUBLISH, "信誉分不足（需 ≥ " + creditProps.getPublishThreshold() + "），无法发布商品");
        }

        // 图片数量检查
        List<String> images = data.getImages() != null ? data.getImages() : List.of();
        if (images.size() > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.TOO_MANY_IMAGES, "图片数量超过限制（最多 6 张）");
        }

        // 售价不能高于原价
        if (data.getPrice().compareTo(data.getOriginalPrice()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "售价不能高于原价");
        }

        // 敏感词过滤
        checkSensitive(data.getTitle(), "title");
        if (data.getDescription() != null) checkSensitive(data.getDescription(), "description");
        checkSensitive(data.getTradeLocation(), "trade_location");

        // 构建商品
        Product product = Product.builder()
                .sellerId(sellerId)
                .title(data.getTitle())
                .description(data.getDescription())
                .category(data.getCategory())
                .condition(data.getCondition())
                .originalPrice(data.getOriginalPrice())
                .price(data.getPrice())
                .tradeLocation(data.getTradeLocation())
                .negotiable(data.getNegotiable() != null ? data.getNegotiable() : true)
                .images(toJson(images))
                .status("active")
                .build();

        Product created = productRepo.create(product);

        log.info("商品发布: productId={}, userId={}", created.getId(), sellerId);

        // 清除商品列表缓存（新商品可能导致列表变化）
        evictProductListCache();

        User seller = userRepo.findById(sellerId);
        return toDetailMap(created, buildSellerInfo(seller), null);
    }

    // ============================================================
    // 编辑商品
    // ============================================================

    /**
     * 编辑商品（与 Node.js productService.update 一致）
     */
    @Transactional
    public Map<String, Object> update(Long productId, Long userId, UpdateProductRequest updates) {
        Product product = productRepo.findById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        if (!product.getSellerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_OWNER);
        }

        // sold / frozen / deleted 状态不可编辑
        if (Set.of("sold", "frozen", "deleted").contains(product.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ACTIVE, "商品状态不允许编辑");
        }

        // 售价不能高于原价
        BigDecimal effectivePrice = updates.getPrice() != null ? updates.getPrice() : product.getPrice();
        BigDecimal effectiveOriginal = updates.getOriginalPrice() != null ? updates.getOriginalPrice() : product.getOriginalPrice();
        if (effectivePrice.compareTo(effectiveOriginal) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "售价不能高于原价");
        }

        // 图片数量检查
        if (updates.getImages() != null && updates.getImages().size() > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.TOO_MANY_IMAGES, "图片数量超过限制（最多 6 张）");
        }

        // 敏感词过滤
        if (updates.getTitle() != null) checkSensitive(updates.getTitle(), "title");
        if (updates.getDescription() != null) checkSensitive(updates.getDescription(), "description");
        if (updates.getTradeLocation() != null) checkSensitive(updates.getTradeLocation(), "trade_location");

        // 应用更新
        Product toUpdate = Product.builder()
                .id(productId)
                .title(updates.getTitle() != null ? updates.getTitle() : product.getTitle())
                .description(updates.getDescription() != null ? updates.getDescription() : product.getDescription())
                .category(updates.getCategory() != null ? updates.getCategory() : product.getCategory())
                .condition(updates.getCondition() != null ? updates.getCondition() : product.getCondition())
                .originalPrice(updates.getOriginalPrice() != null ? updates.getOriginalPrice() : product.getOriginalPrice())
                .price(updates.getPrice() != null ? updates.getPrice() : product.getPrice())
                .tradeLocation(updates.getTradeLocation() != null ? updates.getTradeLocation() : product.getTradeLocation())
                .negotiable(updates.getNegotiable() != null ? updates.getNegotiable() : product.getNegotiable())
                .images(updates.getImages() != null ? toJson(updates.getImages()) : product.getImages())
                .build();

        productRepo.update(toUpdate);

        log.info("商品编辑: productId={}, userId={}", productId, userId);

        // 清除商品列表缓存（编辑可能导致列表变化）
        evictProductListCache();

        Product updated = productRepo.findById(productId);
        User seller = userRepo.findById(updated.getSellerId());
        return toDetailMap(updated, buildSellerInfo(seller), null);
    }

    // ============================================================
    // 删除商品（软删除）
    // ============================================================

    /**
     * 删除商品（软删除，与 Node.js productService.delete 一致）
     */
    @Transactional
    public void delete(Long productId, Long userId) {
        Product product = productRepo.findByIdForUpdate(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        if (!product.getSellerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_OWNER);
        }
        if (!"active".equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "商品状态不允许删除");
        }

        productRepo.updateStatus(productId, "deleted");

        log.info("商品删除: productId={}, userId={}", productId, userId);

        // 清除商品列表缓存
        evictProductListCache();
    }

    // ============================================================
    // 我发布的商品
    // ============================================================

    /**
     * 我发布的商品列表（与 Node.js productService.findBySeller 一致）
     */
    public Map<String, Object> findBySeller(Long sellerId, String status, int page, int pageSize) {
        int offset = (page - 1) * pageSize;

        // 简化：使用 ProductSearchQuery 的列表 + 手动过滤
        // 直接使用 mapper 的 findBySellerId
        List<Product> products = productRepo.findBySellerId(sellerId, status, offset, pageSize);
        long total = productRepo.countBySellerId(sellerId, status);

        List<Map<String, Object>> list = products.stream()
                .map(p -> {
                    Map<String, Object> row = toListRow(p, null);
                    row.put("seller", null); // 自己的商品不需要卖家信息
                    return row;
                })
                .collect(Collectors.toList());

        return Map.of("list", list, "total", total,
                "page", page, "pageSize", pageSize);
    }

    // ============================================================
    // 内部辅助方法
    // ============================================================

    /** 敏感词检查 */
    private void checkSensitive(String text, String fieldName) {
        if (text != null && sensitiveFilter.containsSensitive(text)) {
            throw new BusinessException(ErrorCode.SENSITIVE_WORD);
        }
    }

    /** List<String> → JSON string */
    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            return "[]";
        }
    }

    /** JSON string → List<String> */
    private List<String> parseImages(String imagesJson) {
        if (imagesJson == null || imagesJson.isEmpty()) return List.of();
        try {
            return objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("图片 JSON 解析失败: {}", imagesJson);
            return List.of();
        }
    }

    /** 提取第一张图片作为封面 */
    private String extractCover(List<String> images) {
        return images.isEmpty() ? null : images.get(0);
    }

    /** 构建卖家信息 Map */
    private Map<String, Object> buildSellerInfo(User seller) {
        if (seller == null) return Map.of();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", seller.getId());
        info.put("nickname", seller.getNickname());
        info.put("avatar", seller.getAvatar());
        info.put("credit_score", seller.getCreditScore());
        return info;
    }

    /** 转换列表行为 API 格式（与 Node.js 契约对齐：negotiable→0/1, price→string） */
    private Map<String, Object> toListRow(Product p, User seller) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", p.getId());
        row.put("seller_id", p.getSellerId());
        row.put("title", p.getTitle());
        // Node.js 列表不返回 description
        row.put("category", p.getCategory());
        row.put("condition", p.getCondition());
        // price/original_price 转为格式化字符串，与 Node.js mysql2 DECIMAL 返回一致
        row.put("original_price", formatPrice(p.getOriginalPrice()));
        row.put("price", formatPrice(p.getPrice()));
        row.put("trade_location", p.getTradeLocation());
        // negotiable → 0/1 整数
        row.put("negotiable", p.getNegotiable() != null && p.getNegotiable() ? 1 : 0);
        row.put("images", parseImages(p.getImages()));
        row.put("cover_image", extractCover(parseImages(p.getImages())));
        row.put("status", p.getStatus());
        row.put("created_at", p.getCreatedAt());
        // Node.js 列表不返回 updated_at

        if (seller != null) {
            row.put("seller", buildSellerInfo(seller));
        }
        return row;
    }

    /** 格式化价格：BigDecimal → 两位小数字符串（与 Node.js mysql2 一致） */
    private String formatPrice(BigDecimal price) {
        if (price == null) return null;
        return price.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /** 转换详情为 API 格式 */
    private Map<String, Object> toDetailMap(Product p, Map<String, Object> sellerInfo,
                                             Map<String, Object> reviewSummary) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("seller_id", p.getSellerId());
        map.put("title", p.getTitle());
        map.put("description", p.getDescription());
        map.put("category", p.getCategory());
        map.put("condition", p.getCondition());
        map.put("original_price", formatPrice(p.getOriginalPrice()));
        map.put("price", formatPrice(p.getPrice()));
        map.put("trade_location", p.getTradeLocation());
        map.put("negotiable", p.getNegotiable() != null && p.getNegotiable() ? 1 : 0);
        map.put("images", parseImages(p.getImages()));
        map.put("status", p.getStatus());
        map.put("created_at", p.getCreatedAt());
        map.put("updated_at", p.getUpdatedAt());
        map.put("seller", sellerInfo);
        if (reviewSummary != null) {
            map.put("review_summary", reviewSummary);
        }
        return map;
    }

    /** 安全地从 avgScores Map 中取 double 值 */
    private double toDouble(Map<String, Object> map, String key) {
        if (map == null) return 0;
        Object val = map.get(key);
        if (val == null) return 0;
        if (val instanceof Number n) return n.doubleValue();
        return 0;
    }

    // ============================================================
    // Redis 缓存辅助方法（Phase 10）
    // ============================================================

    /** 构建商品列表缓存 key */
    private String buildListCacheKey(ProductSearchQuery query, int page, int pageSize) {
        int hash = Objects.hash(query.getKeyword(), query.getCategory(), query.getCondition(),
                query.getPriceMin(), query.getPriceMax(), query.getSort());
        return RedisKeys.PRODUCT_LIST + ":" + page + ":" + pageSize + ":" + (hash & 0x7FFFFFFF);
    }

    /**
     * 清除所有商品列表缓存（写操作后调用）
     *
     * <p>使用 SCAN 非阻塞游标迭代代替 KEYS 命令，避免生产环境 O(N) 阻塞 Redis。
     * <p>SCAN 每次返回约 100 个 key，累积后批量删除。
     */
    private void evictProductListCache() {
        try {
            String pattern = RedisKeys.PRODUCT_LIST + ":*";
            var options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100)
                    .build();
            java.util.List<String> keysToDelete = new java.util.ArrayList<>();
            try (Cursor<String> cursor = redis.scan(options)) {
                while (cursor.hasNext()) {
                    keysToDelete.add(cursor.next());
                }
            }
            if (!keysToDelete.isEmpty()) {
                redis.delete(keysToDelete);
                log.debug("商品列表缓存已清除: count={}", keysToDelete.size());
            }
        } catch (Exception e) {
            log.warn("商品列表缓存清除失败，将在 TTL 后自动过期: {}", e.getMessage());
        }
    }

    /**
     * 缓存 TTL + 随机偏移（防雪崩）
     *
     * <p>委托 {@link CacheConstants#cacheTtlWithJitter}（P2-2：统一双向抖动）。
     */
    private long cacheTtlWithJitter() {
        return CacheConstants.cacheTtlWithJitter(
                CacheConstants.PRODUCT_LIST_TTL_SECONDS,
                CacheConstants.PRODUCT_LIST_JITTER_SECONDS);
    }

    /** 构建空结果的 API 响应 */
    private Map<String, Object> buildEmptyListResult(int page, int pageSize) {
        return Map.of("list", List.of(), "total", 0L, "page", page, "pageSize", pageSize);
    }
}
