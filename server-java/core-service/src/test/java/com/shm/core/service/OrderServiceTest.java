package com.shm.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.dto.order.CreateOrderRequest;
import com.shm.common.model.entity.Order;
import com.shm.common.model.entity.Product;
import com.shm.common.model.entity.User;
import com.shm.core.config.CreditProperties;
import com.shm.core.feign.AdminLogFeign;
import com.shm.core.lock.DistributedLocker;
import com.shm.core.mq.OrderEventPublisher;
import com.shm.core.repository.NotificationRepository;
import com.shm.core.repository.OrderRepository;
import com.shm.core.repository.ProductRepository;
import com.shm.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderService 单元测试（Phase 11 11.1.5a）
 *
 * <p>覆盖订单创建（含分布式锁/幂等）/列表/详情/面交/确认收货/取消，Mock 依赖层。
 * <p>Redis 分布式锁 + IM Feign 推送的完整测试见集成测试，此处测核心业务逻辑。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepo;
    @Mock
    private ProductRepository productRepo;
    @Mock
    private UserRepository userRepo;
    @Mock
    private NotificationRepository notificationRepo;
    @Mock
    private CreditProperties creditProps;
    @Mock
    private OrderEventPublisher orderEventPublisher;
    @Mock
    private DistributedLocker distributedLocker;
    @Mock
    private AdminLogFeign adminLogFeign;
    @Mock
    private RLock rLock;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepo, productRepo, userRepo,
                notificationRepo, objectMapper, creditProps,
                new ObjectProvider<OrderEventPublisher>() {
                    @Override public OrderEventPublisher getObject() { return orderEventPublisher; }
                },
                distributedLocker, adminLogFeign);
        lenient().when(rLock.getName()).thenReturn("shm:lock:test");
    }

    /** Mock 分布式锁获取成功 */
    private void mockLockAcquired() {
        when(distributedLocker.tryAcquire(anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(rLock);
    }

    /** Mock 分布式锁获取失败 */
    private void mockLockConflict() {
        when(distributedLocker.tryAcquire(anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(null);
    }

    // ============================================================
    // create — 创建订单
    // ============================================================

    @Test
    void create_success_shouldLockAndCreateOrder() {
        mockLockAcquired();

        CreateOrderRequest req = new CreateOrderRequest();
        req.setProductId(100L);

        when(creditProps.getTradeThreshold()).thenReturn(60);

        // 幂等检查无已有订单
        when(orderRepo.findByIdempotentKey("1_100")).thenReturn(null);

        // 锁定商品
        Product product = Product.builder()
                .id(100L).sellerId(10L).title("测试商品").status("active")
                .originalPrice(new BigDecimal("100")).price(new BigDecimal("50"))
                .tradeLocation("图书馆").images("[]").negotiable(true)
                .description("描述").category("电子产品").condition("良好")
                .build();
        when(productRepo.findByIdForUpdate(100L)).thenReturn(product);

        User seller = User.builder().id(10L).nickname("卖家").build();
        when(userRepo.findById(10L)).thenReturn(seller);

        // 创建订单
        Order created = Order.builder()
                .id(1L).productId(100L).buyerId(1L).sellerId(10L)
                .status("pending").idempotentKey("1_100")
                .productSnapshot("{}")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(orderRepo.create(any(Order.class))).thenReturn(created);

        // MQ 发布（默认不抛出异常）
        doNothing().when(orderEventPublisher).publishOrderEvent(any());

        Map<String, Object> result = orderService.create(1L, 80, req);

        assertEquals(1L, result.get("id"));
        assertEquals("pending", result.get("status"));
        assertEquals(true, result.get("created"));

        // 验证锁释放
        verify(distributedLocker).release(rLock);
        // 验证商品状态更新为 reserved
        verify(productRepo).updateStatus(100L, "reserved");
    }

    @Test
    void create_idempotent_shouldReturnExistingOrder() {
        mockLockAcquired();

        CreateOrderRequest req = new CreateOrderRequest();
        req.setProductId(100L);
        when(creditProps.getTradeThreshold()).thenReturn(60);

        Order existing = Order.builder()
                .id(5L).productId(100L).buyerId(1L).sellerId(10L)
                .status("pending").idempotentKey("1_100")
                .productSnapshot("{}")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(orderRepo.findByIdempotentKey("1_100")).thenReturn(existing);

        Map<String, Object> result = orderService.create(1L, 80, req);

        assertEquals(5L, result.get("id"));
        assertEquals(false, result.get("created"));
        // 不应创建新订单
        verify(orderRepo, never()).create(any());
    }

    @Test
    void create_lockConflict_existingOrder_shouldReturnIdempotent() {
        mockLockConflict();

        CreateOrderRequest req = new CreateOrderRequest();
        req.setProductId(100L);
        when(creditProps.getTradeThreshold()).thenReturn(60);

        // 已有幂等订单
        Order existing = Order.builder()
                .id(6L).productId(100L).buyerId(1L).sellerId(10L)
                .status("pending").idempotentKey("1_100")
                .productSnapshot("{}")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(orderRepo.findByIdempotentKey("1_100")).thenReturn(existing);

        Map<String, Object> result = orderService.create(1L, 80, req);

        assertEquals(6L, result.get("id"));
        assertEquals(false, result.get("created"));
    }

    @Test
    void create_lockConflict_noExistingOrder_shouldThrowRateLimited() {
        mockLockConflict();

        CreateOrderRequest req = new CreateOrderRequest();
        req.setProductId(100L);
        when(creditProps.getTradeThreshold()).thenReturn(60);
        when(orderRepo.findByIdempotentKey("1_100")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.create(1L, 80, req));

        assertEquals(ErrorCode.RATE_LIMITED.getCode(), ex.getCode());
    }

    @Test
    void create_lowCredit_shouldThrowBusinessException() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setProductId(100L);
        when(creditProps.getTradeThreshold()).thenReturn(70);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.create(1L, 50, req));

        assertEquals(ErrorCode.CREDIT_TOO_LOW_TRADE.getCode(), ex.getCode());
    }

    @Test
    void create_cannotBuyOwn_shouldThrowBusinessException() {
        mockLockAcquired();

        CreateOrderRequest req = new CreateOrderRequest();
        req.setProductId(100L);
        when(creditProps.getTradeThreshold()).thenReturn(60);
        when(orderRepo.findByIdempotentKey("1_100")).thenReturn(null);

        // 自己卖的商品
        Product product = Product.builder()
                .id(100L).sellerId(1L).status("active") // sellerId == buyerId
                .price(new BigDecimal("50")).build();
        when(productRepo.findByIdForUpdate(100L)).thenReturn(product);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.create(1L, 80, req));

        assertEquals(ErrorCode.CANNOT_BUY_OWN.getCode(), ex.getCode());
        verify(distributedLocker).release(rLock);
    }

    @Test
    void create_productNotActive_shouldThrowBusinessException() {
        mockLockAcquired();

        CreateOrderRequest req = new CreateOrderRequest();
        req.setProductId(100L);
        when(creditProps.getTradeThreshold()).thenReturn(60);
        when(orderRepo.findByIdempotentKey("1_100")).thenReturn(null);

        Product product = Product.builder()
                .id(100L).sellerId(10L).status("reserved").build();
        when(productRepo.findByIdForUpdate(100L)).thenReturn(product);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.create(1L, 80, req));

        assertEquals(ErrorCode.PRODUCT_NOT_ACTIVE.getCode(), ex.getCode());
        verify(distributedLocker).release(rLock);
    }

    // ============================================================
    // list — 订单列表
    // ============================================================

    @Test
    void list_asBuyer_shouldReturnOrdersWithSellerInfo() {
        Order o = Order.builder()
                .id(1L).productId(100L).buyerId(1L).sellerId(10L)
                .status("pending").idempotentKey("1_100")
                .productSnapshot("{}")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(orderRepo.listByUserRole(1L, "buyer", null, 0, 20)).thenReturn(List.of(o));
        when(orderRepo.countByUserRole(1L, "buyer", null)).thenReturn(1L);

        User seller = User.builder().id(10L).nickname("卖家A").avatar("/s.png").build();
        User buyer = User.builder().id(1L).nickname("买家").build();
        when(userRepo.findByIds(anySet())).thenReturn(List.of(seller, buyer));

        Map<String, Object> result = orderService.list(1L, "buyer", null, 1, 20);

        assertEquals(1L, result.get("total"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
        assertEquals(1, list.size());
        assertEquals("卖家A", list.get(0).get("seller_nickname"));
    }

    // ============================================================
    // detail — 订单详情
    // ============================================================

    @Test
    void detail_success_shouldReturnOrder() {
        Order o = buildOrder(1L, 100L, 1L, 10L, "pending");
        when(orderRepo.findById(1L)).thenReturn(o);

        Map<String, Object> result = orderService.detail(1L, 1L);

        assertEquals(1L, result.get("id"));
        assertEquals("pending", result.get("status"));
    }

    @Test
    void detail_notFound_shouldThrowBusinessException() {
        when(orderRepo.findById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.detail(999L, 1L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void detail_notParticipant_shouldThrowBusinessException() {
        Order o = buildOrder(1L, 100L, 1L, 10L, "pending");
        when(orderRepo.findById(1L)).thenReturn(o);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.detail(1L, 99L));

        assertEquals(ErrorCode.NOT_OWNER.getCode(), ex.getCode());
    }

    // ============================================================
    // markAsMet — 标记面交
    // ============================================================

    @Test
    void markAsMet_success_shouldUpdateStatus() {
        Order o = buildOrder(1L, 100L, 1L, 10L, "pending");
        when(orderRepo.findByIdForUpdate(1L)).thenReturn(o);

        // MQ 发布（默认不抛出异常）
        doNothing().when(orderEventPublisher).publishOrderEvent(any());

        Order updated = buildOrder(1L, 100L, 1L, 10L, "met");
        updated.setMetAt(LocalDateTime.now());
        when(orderRepo.findById(1L)).thenReturn(updated);

        Map<String, Object> result = orderService.markAsMet(1L, 1L);

        assertEquals("met", result.get("status"));
        verify(orderRepo).updateStatus(any(Order.class));
    }

    @Test
    void markAsMet_notPending_shouldThrowBusinessException() {
        Order o = buildOrder(1L, 100L, 1L, 10L, "met");
        when(orderRepo.findByIdForUpdate(1L)).thenReturn(o);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.markAsMet(1L, 1L));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void markAsMet_notParticipant_shouldThrowBusinessException() {
        Order o = buildOrder(1L, 100L, 1L, 10L, "pending");
        when(orderRepo.findByIdForUpdate(1L)).thenReturn(o);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.markAsMet(1L, 99L));

        assertEquals(ErrorCode.NOT_OWNER.getCode(), ex.getCode());
    }

    // ============================================================
    // confirm — 确认收货
    // ============================================================

    @Test
    void confirm_success_shouldCompleteAndRewardSeller() {
        Order o = buildOrder(1L, 100L, 1L, 10L, "met");
        o.setProductId(100L);
        when(orderRepo.findByIdForUpdate(1L)).thenReturn(o);

        when(creditProps.getRewardTransaction()).thenReturn(2);
        when(creditProps.getMax()).thenReturn(200);

        doNothing().when(orderEventPublisher).publishOrderEvent(any());

        // Phase 13: Seata 跨服务分支 — 审计日志 Feign
        when(adminLogFeign.createLog(any())).thenReturn(Map.of("code", 0));

        Order completed = buildOrder(1L, 100L, 1L, 10L, "completed");
        completed.setConfirmedAt(LocalDateTime.now());
        completed.setProductId(100L);
        when(orderRepo.findById(1L)).thenReturn(completed);

        Map<String, Object> result = orderService.confirm(1L, 1L);

        assertEquals("completed", result.get("status"));
        verify(productRepo).updateStatus(100L, "sold");
        verify(userRepo).updateCreditScore(eq(10L), eq(2), eq(200));
    }

    @Test
    void confirm_notBuyer_shouldThrowBusinessException() {
        Order o = buildOrder(1L, 100L, 1L, 10L, "met");
        when(orderRepo.findByIdForUpdate(1L)).thenReturn(o);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.confirm(1L, 10L)); // seller tries to confirm

        assertEquals(ErrorCode.NOT_OWNER.getCode(), ex.getCode());
    }

    @Test
    void confirm_notMet_shouldThrowBusinessException() {
        Order o = buildOrder(1L, 100L, 1L, 10L, "pending");
        when(orderRepo.findByIdForUpdate(1L)).thenReturn(o);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.confirm(1L, 1L));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID.getCode(), ex.getCode());
    }

    // ============================================================
    // cancel — 取消订单
    // ============================================================

    @Test
    void cancel_pendingByBuyer_shouldCancelAndRestoreProduct() {
        Order o = buildOrder(1L, 100L, 1L, 10L, "pending");
        o.setProductId(100L);
        when(orderRepo.findByIdForUpdate(1L)).thenReturn(o);

        doNothing().when(orderEventPublisher).publishOrderEvent(any());

        Order cancelled = buildOrder(1L, 100L, 1L, 10L, "cancelled");
        cancelled.setCancelledBy("buyer");
        cancelled.setProductId(100L);
        when(orderRepo.findById(1L)).thenReturn(cancelled);

        Map<String, Object> result = orderService.cancel(1L, 1L);

        assertEquals("cancelled", result.get("status"));
        assertEquals("buyer", result.get("cancelled_by"));
        verify(productRepo).updateStatus(100L, "active");
    }

    @Test
    void cancel_pendingBySeller_shouldCancelAndRestoreProduct() {
        Order o = buildOrder(1L, 100L, 1L, 10L, "pending");
        o.setProductId(100L);
        when(orderRepo.findByIdForUpdate(1L)).thenReturn(o);

        doNothing().when(orderEventPublisher).publishOrderEvent(any());

        Order cancelled = buildOrder(1L, 100L, 1L, 10L, "cancelled");
        cancelled.setCancelledBy("seller");
        cancelled.setProductId(100L);
        when(orderRepo.findById(1L)).thenReturn(cancelled);

        Map<String, Object> result = orderService.cancel(1L, 10L);

        assertEquals("cancelled", result.get("status"));
        assertEquals("seller", result.get("cancelled_by"));
    }

    @Test
    void cancel_metByBuyer_shouldCancel() {
        Order o = buildOrder(1L, 100L, 1L, 10L, "met");
        o.setProductId(100L);
        when(orderRepo.findByIdForUpdate(1L)).thenReturn(o);

        doNothing().when(orderEventPublisher).publishOrderEvent(any());

        Order cancelled = buildOrder(1L, 100L, 1L, 10L, "cancelled");
        cancelled.setCancelledBy("buyer");
        cancelled.setProductId(100L);
        when(orderRepo.findById(1L)).thenReturn(cancelled);

        Map<String, Object> result = orderService.cancel(1L, 1L);

        assertEquals("cancelled", result.get("status"));
    }

    @Test
    void cancel_metBySeller_shouldThrowBusinessException() {
        // met 状态仅买家可取消
        Order o = buildOrder(1L, 100L, 1L, 10L, "met");
        when(orderRepo.findByIdForUpdate(1L)).thenReturn(o);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.cancel(1L, 10L));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void cancel_completed_shouldThrowBusinessException() {
        Order o = buildOrder(1L, 100L, 1L, 10L, "completed");
        when(orderRepo.findByIdForUpdate(1L)).thenReturn(o);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.cancel(1L, 1L));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void cancel_notParticipant_shouldThrowBusinessException() {
        Order o = buildOrder(1L, 100L, 1L, 10L, "pending");
        when(orderRepo.findByIdForUpdate(1L)).thenReturn(o);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                orderService.cancel(1L, 99L));

        assertEquals(ErrorCode.NOT_OWNER.getCode(), ex.getCode());
    }

    // ---- 辅助 ----

    private Order buildOrder(Long id, Long productId, Long buyerId, Long sellerId, String status) {
        return Order.builder()
                .id(id).productId(productId).buyerId(buyerId).sellerId(sellerId)
                .status(status).idempotentKey(buyerId + "_" + productId)
                .productSnapshot("{}")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }
}
