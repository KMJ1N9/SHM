package com.shm.core.mapper;

import com.shm.common.model.entity.Review;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 评价 Mapper（对应 reviews 表）
 */
@Mapper
public interface ReviewMapper {

    @Select("SELECT id, order_id, reviewer_id, reviewee_id, communication_score, punctuality_score, accuracy_score, comment, created_at FROM reviews WHERE id = #{id}")
    Review findById(Long id);

    @Insert("INSERT INTO reviews (order_id, reviewer_id, reviewee_id, communication_score, punctuality_score, accuracy_score, comment) " +
            "VALUES (#{orderId}, #{reviewerId}, #{revieweeId}, #{communicationScore}, #{punctualityScore}, #{accuracyScore}, #{comment})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Review review);

    @Select("SELECT id, order_id, reviewer_id, reviewee_id, communication_score, punctuality_score, accuracy_score, comment, created_at FROM reviews WHERE reviewee_id = #{userId} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Review> findByRevieweeId(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM reviews WHERE reviewee_id = #{userId}")
    long countByRevieweeId(Long userId);

    @Select("SELECT id, order_id, reviewer_id, reviewee_id, communication_score, punctuality_score, accuracy_score, comment, created_at FROM reviews WHERE order_id = #{orderId} ORDER BY created_at DESC")
    List<Review> findByOrderId(Long orderId);

    @Select("SELECT id, order_id, reviewer_id, reviewee_id, communication_score, punctuality_score, accuracy_score, comment, created_at FROM reviews WHERE order_id = #{orderId} AND reviewer_id = #{reviewerId}")
    Review findByOrderAndReviewer(@Param("orderId") Long orderId, @Param("reviewerId") Long reviewerId);

    @Select("SELECT AVG(communication_score) AS avg_communication, AVG(punctuality_score) AS avg_punctuality, AVG(accuracy_score) AS avg_accuracy, COUNT(*) AS total FROM reviews WHERE reviewee_id = #{userId}")
    Map<String, Object> getAvgScores(Long userId);
}
