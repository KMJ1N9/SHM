package com.shm.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.dto.review.CreateReviewRequest;
import com.shm.common.model.entity.Notification;
import com.shm.common.model.entity.Order;
import com.shm.common.model.entity.Review;
import com.shm.core.config.CreditProperties;
import com.shm.core.repository.NotificationRepository;
import com.shm.core.repository.OrderRepository;
import com.shm.core.repository.ReviewRepository;
import com.shm.core.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 评价服务（与 Node.js services/review.js 行为完全一致）
 *
 * <p>三维评分：沟通态度、守时程度、描述一致度，各 1-5 分。
 * <p>总分 ≥ 12（均分 ≥ 4）→ 被评价方信誉分 +1
 * <p>总分 ≤ 6（均分 ≤ 2）→ 被评价方信誉分 -5
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepo;
    private final OrderRepository orderRepo;
    private final UserRepository userRepo;
    private final NotificationRepository notificationRepo;
    private final ObjectMapper objectMapper;
    private final CreditProperties creditProps;

    public ReviewService(ReviewRepository reviewRepo, OrderRepository orderRepo,
                         UserRepository userRepo, NotificationRepository notificationRepo,
                         ObjectMapper objectMapper, CreditProperties creditProps) {
        this.reviewRepo = reviewRepo;
        this.orderRepo = orderRepo;
        this.userRepo = userRepo;
        this.notificationRepo = notificationRepo;
        this.objectMapper = objectMapper;
        this.creditProps = creditProps;
    }

    /**
     * 创建评价（与 Node.js reviewService.create 一致）
     */
    @Transactional
    public Map<String, Object> create(Long reviewerId, CreateReviewRequest data) {
        // 验证订单
        Order order = orderRepo.findById(data.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!"completed".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "仅已完成订单可评价");
        }

        // 验证评价人参与了该订单
        if (!order.getBuyerId().equals(reviewerId) && !order.getSellerId().equals(reviewerId)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "你未参与该订单，无法评价");
        }

        // 验证被评价人参与了该订单
        if (!order.getBuyerId().equals(data.getRevieweeId()) && !order.getSellerId().equals(data.getRevieweeId())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "被评价人未参与该订单");
        }

        // 不能评价自己
        if (data.getRevieweeId().equals(reviewerId)) {
            throw new BusinessException(ErrorCode.CANNOT_REVIEW_OWN);
        }

        // 防重复
        Review existing = reviewRepo.findByOrderAndReviewer(data.getOrderId(), reviewerId);
        if (existing != null) {
            throw new BusinessException(ErrorCode.ALREADY_REVIEWED);
        }

        Review review = Review.builder()
                .orderId(data.getOrderId())
                .reviewerId(reviewerId)
                .revieweeId(data.getRevieweeId())
                .communicationScore(data.getCommunicationScore())
                .punctualityScore(data.getPunctualityScore())
                .accuracyScore(data.getAccuracyScore())
                .comment(data.getComment())
                .build();

        Review created = reviewRepo.create(review);

        // 计算总分并联动信誉分
        int sum = data.getCommunicationScore() + data.getPunctualityScore() + data.getAccuracyScore();
        if (sum >= 12) {
            userRepo.updateCreditScore(data.getRevieweeId(), creditProps.getRewardPositive(), creditProps.getMax());
            createCreditNotification(data.getRevieweeId(), "好评奖励", data.getOrderId());
        } else if (sum <= 6) {
            userRepo.updateCreditScore(data.getRevieweeId(), creditProps.getDeductNegative(), creditProps.getMax());
            createCreditNotification(data.getRevieweeId(), "差评扣分", data.getOrderId());
        }

        log.info("评价创建: reviewId={}, reviewerId={}, revieweeId={}, sum={}", created.getId(), reviewerId, data.getRevieweeId(), sum);

        return toReviewMap(created);
    }

    /**
     * 获取某订单的所有评价（与 Node.js reviewService.listByOrder 完全一致）
     *
     * <p>按 order_id 查询，最多 2 条评价（买卖双方各一条）。
     */
    public List<Map<String, Object>> listByOrder(Long orderId) {
        List<Review> reviews = reviewRepo.findByOrderId(orderId);
        return reviews.stream()
                .map(this::toReviewMap)
                .toList();
    }

    /**
     * 获取某用户的评价列表（含聚合统计，与 Node.js reviewService.listByUser 一致）
     */
    public Map<String, Object> listByUser(Long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Review> reviews = reviewRepo.findByRevieweeId(userId, offset, pageSize);
        long total = reviewRepo.countByRevieweeId(userId);

        // 聚合评分统计
        Map<String, Object> avgScores = reviewRepo.getAvgScores(userId);

        List<Map<String, Object>> list = reviews.stream()
                .map(this::toReviewMap)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("summary", avgScores != null ? avgScores : Map.of());
        return result;
    }

    // ---- 辅助方法 ----

    private void createCreditNotification(Long userId, String reason, Long orderId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("order_id", orderId);
        meta.put("reason", reason);
        String metadata;
        try {
            metadata = objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            metadata = "{}";
        }
        Notification notification = Notification.builder()
                .userId(userId)
                .type("credit_change")
                .title("信誉分变动")
                .content(reason)
                .isRead(false)
                .metadata(metadata)
                .build();
        notificationRepo.insert(notification);
    }

    private Map<String, Object> toReviewMap(Review r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", r.getId());
        map.put("order_id", r.getOrderId());
        map.put("reviewer_id", r.getReviewerId());
        map.put("reviewee_id", r.getRevieweeId());
        map.put("communication_score", r.getCommunicationScore());
        map.put("punctuality_score", r.getPunctualityScore());
        map.put("accuracy_score", r.getAccuracyScore());
        map.put("comment", r.getComment());
        map.put("created_at", r.getCreatedAt());
        return map;
    }
}
