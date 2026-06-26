package com.shm.core.task;

import com.shm.common.model.entity.Order;
import com.shm.core.mapper.OrderMapper;
import com.shm.core.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时自动取消定时任务（Phase 14）
 *
 * <p>每 60 秒扫描超过 24 小时未处理的 pending 订单，自动取消并恢复商品状态。
 */
@Component
public class OrderTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutTask.class);

    private final OrderMapper orderMapper;
    private final ProductRepository productRepository;

    public OrderTimeoutTask(OrderMapper orderMapper, ProductRepository productRepository) {
        this.orderMapper = orderMapper;
        this.productRepository = productRepository;
    }

    @Scheduled(fixedDelay = 60_000) // 60 seconds
    @Transactional
    public void cancelTimeoutOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<Order> timeoutOrders = orderMapper.selectPendingOlderThan(cutoff);
        if (timeoutOrders.isEmpty()) {
            return;
        }

        log.info("发现 {} 个超时订单，开始自动取消", timeoutOrders.size());
        for (Order order : timeoutOrders) {
            orderMapper.cancelTimeout(order.getId(), "system");
            productRepository.updateStatus(order.getProductId(), "active");
            log.info("超时订单已自动取消: orderId={}, productId={}",
                    order.getId(), order.getProductId());
        }
    }
}
