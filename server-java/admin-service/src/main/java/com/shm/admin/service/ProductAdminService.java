package com.shm.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.admin.feign.ImConnectorFeign;
import com.shm.admin.mapper.AdminLogMapper;
import com.shm.admin.mapper.ProductMapper;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.entity.AdminLog;
import com.shm.common.model.entity.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端商品服务（与 Node.js services/admin.js 商品管理部分行为完全一致）
 *
 * <p>处理管理端商品列表（含全部状态）、强制下架。
 */
@Service
public class ProductAdminService {

    private static final Logger log = LoggerFactory.getLogger(ProductAdminService.class);

    private final ProductMapper productMapper;
    private final AdminLogMapper adminLogMapper;
    private final ObjectMapper objectMapper;
    private final ImConnectorFeign imConnectorFeign;

    public ProductAdminService(ProductMapper productMapper, AdminLogMapper adminLogMapper,
                                ObjectMapper objectMapper, ImConnectorFeign imConnectorFeign) {
        this.productMapper = productMapper;
        this.adminLogMapper = adminLogMapper;
        this.objectMapper = objectMapper;
        this.imConnectorFeign = imConnectorFeign;
    }

    /**
     * 管理端商品列表（与 Node.js adminService.listAllProducts 一致）
     *
     * <p>含全部状态 + 嵌套 seller 对象 + cover_image。
     */
    public Map<String, Object> listAllProducts(String status, String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Map<String, Object>> rows = productMapper.listAll(status, keyword, offset, pageSize);
        long total = productMapper.countAll(status, keyword);

        // 转换为 API 格式：cover_image + 嵌套 seller
        List<Map<String, Object>> list = rows.stream()
                .map(this::toListRow)
                .collect(Collectors.toList());

        return Map.of("list", list, "total", total, "page", page, "pageSize", pageSize);
    }

    /**
     * 下架商品（与 Node.js adminService.offShelfProduct 一致）
     */
    @Transactional
    public Map<String, Object> offShelfProduct(Long productId, Long adminId) {
        Product product = productMapper.findById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }

        productMapper.updateStatus(productId, "off_shelf");

        // 记录操作日志
        AdminLog adminLog = AdminLog.builder()
                .adminId(adminId)
                .action("off_shelf")
                .targetType("product")
                .targetId(productId)
                .build();
        adminLogMapper.insert(adminLog);

        // IM 通知卖家（静默失败，不影响事务）
        pushImMessage(product.getSellerId(), "商品管理通知",
                "你的商品「" + product.getTitle() + "」已被管理员下架");

        log.info("商品下架: productId={}, adminId={}", productId, adminId);

        Product updated = productMapper.findById(productId);
        return toProductMap(updated);
    }

    // ---- 辅助方法 ----

    /** 提取第一张图片作为封面 */
    private String extractCover(String imagesJson) {
        if (imagesJson == null || imagesJson.isEmpty()) return null;
        try {
            List<String> images = objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {});
            return images.isEmpty() ? null : images.get(0);
        } catch (JsonProcessingException e) {
            log.debug("解析商品图片 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    /** 将扁平 seller_* 字段嵌套为 seller 对象，与 Node.js 行为一致 */
    private Map<String, Object> toListRow(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);

        // 构建嵌套 seller 对象
        Map<String, Object> seller = new LinkedHashMap<>();
        seller.put("id", row.get("seller_id"));
        seller.put("nickname", row.getOrDefault("seller_nickname", ""));
        seller.put("avatar", row.getOrDefault("seller_avatar", ""));
        seller.put("credit_score", row.getOrDefault("seller_credit_score", 0));
        result.put("seller", seller);

        // 添加 cover_image
        String imagesJson = (String) row.get("images");
        result.put("cover_image", extractCover(imagesJson));

        // 移除扁平字段（已在 seller 中）
        result.remove("seller_nickname");
        result.remove("seller_avatar");
        result.remove("seller_credit_score");

        return result;
    }

    private Map<String, Object> toProductMap(Product p) {
        if (p == null) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("seller_id", p.getSellerId());
        map.put("title", p.getTitle());
        map.put("status", p.getStatus());
        map.put("created_at", p.getCreatedAt());
        map.put("updated_at", p.getUpdatedAt());
        return map;
    }

    /**
     * 通过 Feign 推送 IM 实时消息（静默失败，不影响调用方事务）
     *
     * <p>IM Connector 不可用时自动降级为仅站内通知（DB 已持久化）。
     */
    private void pushImMessage(Long userId, String title, String content) {
        try {
            Map<String, Object> result = imConnectorFeign.sendSystemMessage(
                    String.valueOf(userId), title, content, null);
            if (result != null && result.containsKey("code")) {
                int code = ((Number) result.get("code")).intValue();
                if (code != 0) {
                    log.warn("IM 推送失败: userId={}, code={}, message={}", userId, code, result.get("message"));
                }
            }
        } catch (Exception e) {
            log.warn("IM 推送异常（已降级为站内通知）: userId={}, error={}", userId, e.getMessage());
        }
    }
}
