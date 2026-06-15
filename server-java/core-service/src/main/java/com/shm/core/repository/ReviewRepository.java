package com.shm.core.repository;

import com.shm.common.model.entity.Review;
import com.shm.core.mapper.ReviewMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 评价 Repository（封装 ReviewMapper，对应 Node.js repository/review.js）
 */
@Repository
public class ReviewRepository {

    private final ReviewMapper mapper;

    public ReviewRepository(ReviewMapper mapper) {
        this.mapper = mapper;
    }

    public Review findById(Long id) {
        return mapper.findById(id);
    }

    public Review create(Review review) {
        mapper.insert(review);
        return review;
    }

    public List<Review> findByRevieweeId(Long userId, int offset, int limit) {
        return mapper.findByRevieweeId(userId, offset, limit);
    }

    public long countByRevieweeId(Long userId) {
        return mapper.countByRevieweeId(userId);
    }

    /** 按订单 ID 查询所有评价（与 Node.js reviewRepo.findByOrder 一致） */
    public List<Review> findByOrderId(Long orderId) {
        return mapper.findByOrderId(orderId);
    }

    public Review findByOrderAndReviewer(Long orderId, Long reviewerId) {
        return mapper.findByOrderAndReviewer(orderId, reviewerId);
    }

    /**
     * 获取用户评价平均分
     *
     * @return Map 含 avg_communication, avg_punctuality, avg_accuracy, total
     */
    public Map<String, Object> getAvgScores(Long userId) {
        return mapper.getAvgScores(userId);
    }
}
