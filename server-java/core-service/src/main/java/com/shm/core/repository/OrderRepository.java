package com.shm.core.repository;

import com.shm.common.model.entity.Order;
import com.shm.core.mapper.OrderMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 订单 Repository（封装 OrderMapper，对应 Node.js repository/order.js）
 */
@Repository
public class OrderRepository {

    private final OrderMapper mapper;

    public OrderRepository(OrderMapper mapper) {
        this.mapper = mapper;
    }

    public Order findById(Long id) {
        return mapper.findById(id);
    }

    /** 带悲观锁查询（用于订单状态变更防并发） */
    public Order findByIdForUpdate(Long id) {
        return mapper.findByIdForUpdate(id);
    }

    public Order create(Order order) {
        mapper.insert(order);
        return order;
    }

    public int updateStatus(Order order) {
        return mapper.updateStatus(order);
    }

    public Order findByIdempotentKey(String key) {
        return mapper.findByIdempotentKey(key);
    }

    public List<Order> listByUserRole(Long userId, String role, String status, int offset, int limit) {
        return mapper.listByUserRole(userId, role, status, offset, limit);
    }

    public long countByUserRole(Long userId, String role, String status) {
        return mapper.countByUserRole(userId, role, status);
    }
}
