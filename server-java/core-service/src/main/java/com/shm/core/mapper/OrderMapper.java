package com.shm.core.mapper;

import com.shm.common.model.entity.Order;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 订单 Mapper（对应 orders 表，复杂查询在 OrderMapper.xml）
 */
@Mapper
public interface OrderMapper {

    @Select("SELECT id, product_id, buyer_id, seller_id, status, cancelled_by, idempotent_key, product_snapshot, met_at, confirmed_at, created_at, updated_at FROM orders WHERE id = #{id}")
    Order findById(Long id);

    /** 带悲观锁查询（用于 confirmOrder/cancelOrder 防并发） */
    @Select("SELECT id, product_id, buyer_id, seller_id, status, cancelled_by, idempotent_key, product_snapshot, met_at, confirmed_at, created_at, updated_at FROM orders WHERE id = #{id} FOR UPDATE")
    Order findByIdForUpdate(Long id);

    @Insert("INSERT INTO orders (product_id, buyer_id, seller_id, status, idempotent_key, product_snapshot) " +
            "VALUES (#{productId}, #{buyerId}, #{sellerId}, #{status}, #{idempotentKey}, #{productSnapshot})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Order order);

    @Update("UPDATE orders SET status = #{status}, cancelled_by = #{cancelledBy}, met_at = #{metAt}, confirmed_at = #{confirmedAt} WHERE id = #{id}")
    int updateStatus(Order order);

    @Select("SELECT id, product_id, buyer_id, seller_id, status, cancelled_by, idempotent_key, product_snapshot, met_at, confirmed_at, created_at, updated_at FROM orders WHERE idempotent_key = #{key}")
    Order findByIdempotentKey(String key);

    /**
     * 按用户角色查询订单列表 — 实现在 OrderMapper.xml
     */
    List<Order> listByUserRole(@Param("userId") Long userId, @Param("role") String role,
                                @Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 按用户角色计数 — 实现在 OrderMapper.xml
     */
    long countByUserRole(@Param("userId") Long userId, @Param("role") String role, @Param("status") String status);
}
