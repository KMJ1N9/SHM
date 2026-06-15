package com.shm.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReviewService 单元测试（Phase 11 11.1.5a）
 *
 * <p>覆盖评价创建（含信誉分联动）/按订单查询/按用户分页查询，Mock Repository 层。
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepo;
    @Mock
    private OrderRepository orderRepo;
    @Mock
    private UserRepository userRepo;
    @Mock
    private NotificationRepository notificationRepo;
    @Mock
    private CreditProperties creditProps;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepo, orderRepo, userRepo,
                notificationRepo, objectMapper, creditProps);
    }

    // ============================================================
    // create — 创建评价
    // ============================================================

    @Test
    void create_highScore_shouldRewardCredit() {
        // Given
        CreateReviewRequest req = buildRequest(5L, 10L, 20L, 5, 4, 4); // sum=13 ≥ 12
        Order order = Order.builder()
                .id(5L).buyerId(10L).sellerId(20L).status("completed").build();
        when(orderRepo.findById(5L)).thenReturn(order);
        when(reviewRepo.findByOrderAndReviewer(5L, 10L)).thenReturn(null);

        Review created = Review.builder()
                .id(1L).orderId(5L).reviewerId(10L).revieweeId(20L)
                .communicationScore(5).punctualityScore(4).accuracyScore(4)
                .comment("很好").createdAt(LocalDateTime.now()).build();
        when(reviewRepo.create(any(Review.class))).thenReturn(created);
        when(creditProps.getRewardPositive()).thenReturn(1);
        when(creditProps.getMax()).thenReturn(200);

        // When
        Map<String, Object> result = reviewService.create(10L, req);

        // Then
        assertEquals(1L, result.get("id"));
        assertEquals(5, result.get("communication_score"));

        // 信誉分 +1
        verify(userRepo).updateCreditScore(eq(20L), eq(1), eq(200));
        verify(notificationRepo).insert(any(Notification.class));
    }

    @Test
    void create_lowScore_shouldDeductCredit() {
        // Given: sum=5 ≤ 6 → 扣 5 分
        CreateReviewRequest req = buildRequest(5L, 10L, 20L, 2, 2, 1);
        Order order = Order.builder()
                .id(5L).buyerId(10L).sellerId(20L).status("completed").build();
        when(orderRepo.findById(5L)).thenReturn(order);
        when(reviewRepo.findByOrderAndReviewer(5L, 10L)).thenReturn(null);

        Review created = Review.builder()
                .id(2L).orderId(5L).reviewerId(10L).revieweeId(20L)
                .communicationScore(2).punctualityScore(2).accuracyScore(1)
                .comment("差").createdAt(LocalDateTime.now()).build();
        when(reviewRepo.create(any(Review.class))).thenReturn(created);
        when(creditProps.getMax()).thenReturn(200);
        when(creditProps.getDeductNegative()).thenReturn(-5);

        reviewService.create(10L, req);

        verify(userRepo).updateCreditScore(eq(20L), eq(-5), eq(200));
        verify(notificationRepo).insert(any(Notification.class));
    }

    @Test
    void create_midScore_shouldNotChangeCredit() {
        // Given: sum=9 (6 < 9 < 12) → 不变
        CreateReviewRequest req = buildRequest(5L, 10L, 20L, 3, 3, 3);
        Order order = Order.builder()
                .id(5L).buyerId(10L).sellerId(20L).status("completed").build();
        when(orderRepo.findById(5L)).thenReturn(order);
        when(reviewRepo.findByOrderAndReviewer(5L, 10L)).thenReturn(null);

        Review created = Review.builder()
                .id(3L).orderId(5L).reviewerId(10L).revieweeId(20L)
                .communicationScore(3).punctualityScore(3).accuracyScore(3)
                .comment("一般").createdAt(LocalDateTime.now()).build();
        when(reviewRepo.create(any(Review.class))).thenReturn(created);

        reviewService.create(10L, req);

        // 信誉分不变
        verify(userRepo, never()).updateCreditScore(anyLong(), anyInt(), anyInt());
        verify(notificationRepo, never()).insert(any());
    }

    @Test
    void create_orderNotFound_shouldThrowBusinessException() {
        CreateReviewRequest req = buildRequest(999L, 10L, 20L, 4, 4, 4);
        when(orderRepo.findById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reviewService.create(10L, req));

        assertEquals(ErrorCode.ORDER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void create_orderNotCompleted_shouldThrowBusinessException() {
        CreateReviewRequest req = buildRequest(5L, 10L, 20L, 4, 4, 4);
        Order order = Order.builder().id(5L).status("pending").build();
        when(orderRepo.findById(5L)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reviewService.create(10L, req));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void create_notOrderParticipant_shouldThrowBusinessException() {
        CreateReviewRequest req = buildRequest(5L, 10L, 20L, 4, 4, 4);
        // reviewer 不是买卖双方
        Order order = Order.builder()
                .id(5L).buyerId(30L).sellerId(20L).status("completed").build();
        when(orderRepo.findById(5L)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reviewService.create(10L, req));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void create_revieweeNotInOrder_shouldThrowBusinessException() {
        CreateReviewRequest req = buildRequest(5L, 10L, 99L, 4, 4, 4);
        Order order = Order.builder()
                .id(5L).buyerId(10L).sellerId(20L).status("completed").build();
        when(orderRepo.findById(5L)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reviewService.create(10L, req));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void create_selfReview_shouldThrowBusinessException() {
        CreateReviewRequest req = buildRequest(5L, 10L, 10L, 5, 5, 5);
        Order order = Order.builder()
                .id(5L).buyerId(10L).sellerId(20L).status("completed").build();
        when(orderRepo.findById(5L)).thenReturn(order);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reviewService.create(10L, req));

        assertEquals(ErrorCode.CANNOT_REVIEW_OWN.getCode(), ex.getCode());
    }

    @Test
    void create_alreadyReviewed_shouldThrowBusinessException() {
        CreateReviewRequest req = buildRequest(5L, 10L, 20L, 4, 4, 4);
        Order order = Order.builder()
                .id(5L).buyerId(10L).sellerId(20L).status("completed").build();
        when(orderRepo.findById(5L)).thenReturn(order);
        when(reviewRepo.findByOrderAndReviewer(5L, 10L))
                .thenReturn(Review.builder().id(1L).build());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                reviewService.create(10L, req));

        assertEquals(ErrorCode.ALREADY_REVIEWED.getCode(), ex.getCode());
    }

    // ============================================================
    // listByOrder — 按订单查评价
    // ============================================================

    @Test
    void listByOrder_shouldReturnReviews() {
        List<Review> reviews = List.of(
                Review.builder().id(1L).orderId(5L).reviewerId(10L).revieweeId(20L)
                        .communicationScore(5).punctualityScore(4).accuracyScore(4)
                        .comment("好评").createdAt(LocalDateTime.now()).build()
        );
        when(reviewRepo.findByOrderId(5L)).thenReturn(reviews);

        List<Map<String, Object>> result = reviewService.listByOrder(5L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).get("id"));
        assertEquals(5, result.get(0).get("communication_score"));
    }

    // ============================================================
    // listByUser — 按用户查评价（分页 + 聚合）
    // ============================================================

    @Test
    void listByUser_shouldReturnPaginatedReviewsWithSummary() {
        List<Review> reviews = List.of(
                Review.builder().id(1L).orderId(5L).reviewerId(10L).revieweeId(20L)
                        .communicationScore(4).punctualityScore(4).accuracyScore(4)
                        .comment("不错").createdAt(LocalDateTime.now()).build()
        );
        when(reviewRepo.findByRevieweeId(20L, 0, 20)).thenReturn(reviews);
        when(reviewRepo.countByRevieweeId(20L)).thenReturn(1L);
        when(reviewRepo.getAvgScores(20L)).thenReturn(Map.of(
                "total", 1L, "avg_communication", 4.0,
                "avg_punctuality", 4.0, "avg_accuracy", 4.0));

        Map<String, Object> result = reviewService.listByUser(20L, 1, 20);

        assertEquals(1L, result.get("total"));
        assertEquals(1, result.get("page"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
        assertEquals(1, list.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertNotNull(summary);
        assertEquals(1L, summary.get("total"));
    }

    // ---- 辅助 ----

    private CreateReviewRequest buildRequest(Long orderId, Long reviewerId,
                                              Long revieweeId, int comm, int punct, int acc) {
        CreateReviewRequest req = new CreateReviewRequest();
        req.setOrderId(orderId);
        req.setRevieweeId(revieweeId);
        req.setCommunicationScore(comm);
        req.setPunctualityScore(punct);
        req.setAccuracyScore(acc);
        req.setComment("测试评价");
        return req;
    }
}
