package com.shm.core.controller;

import com.shm.common.model.dto.review.CreateReviewRequest;
import com.shm.common.util.ResponseBuilder;
import com.shm.core.security.CurrentUser;
import com.shm.core.security.UserPrincipal;
import com.shm.core.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 评价控制器（与 Node.js controllers/review.js 行为完全一致）
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * POST /api/reviews — 创建评价
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@CurrentUser UserPrincipal user,
                                      @Valid @RequestBody CreateReviewRequest request) {
        return ResponseBuilder.ok(reviewService.create(user.getUserId(), request));
    }

    /**
     * GET /api/reviews — 评价列表
     * query: order_id → 该订单的评价；user_id → 该用户的评价
     */
    @GetMapping
    public Map<String, Object> list(@RequestParam(name = "order_id", required = false) Long orderId,
                                    @RequestParam(name = "user_id", required = false) Long userId,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        pageSize = Math.min(pageSize, 50);
        if (orderId != null) {
            return ResponseBuilder.ok(Map.of("list", reviewService.listByOrder(orderId)));
        }
        if (userId != null) {
            return ResponseBuilder.ok(reviewService.listByUser(userId, page, pageSize));
        }
        throw new com.shm.common.exception.BusinessException(
                com.shm.common.exception.ErrorCode.VALIDATION_ERROR, "请提供 order_id 或 user_id");
    }
}
