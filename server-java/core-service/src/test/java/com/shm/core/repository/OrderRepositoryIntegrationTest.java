package com.shm.core.repository;

import com.shm.common.model.entity.Order;
import com.shm.common.model.entity.Product;
import com.shm.common.model.entity.User;
import com.shm.core.mapper.OrderMapper;
import com.shm.core.mapper.ProductMapper;
import com.shm.core.mapper.UserMapper;
import org.junit.jupiter.api.*;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderMapper 集成测试（Phase 11.2.3）
 *
 * <p>使用 @MybatisTest 只加载 MyBatis 层，避免触发 Nacos/Feign/Sentinel 自动配置。
 * 连接真实 MySQL 数据库，@Transactional 保证测试数据自动回滚。
 * 涵盖 CRUD / 状态流转 / 幂等键 / 角色查询 / FOR UPDATE 等关键数据访问路径。
 */
@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderRepositoryIntegrationTest {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProductMapper productMapper;

    private User testBuyer;
    private User testSeller;

    /** 在每个 @Transactional 测试前创建 buyer 和 seller */
    private void ensureUsers() {
        if (testBuyer == null) {
            testBuyer = buildUser("13810000001", "集成测试买家");
            userMapper.insert(testBuyer);
        }
        if (testSeller == null) {
            testSeller = buildUser("13810000002", "集成测试卖家");
            userMapper.insert(testSeller);
        }
    }

    private Product createTestProduct(Long sellerId, String title) {
        Product product = new Product();
        product.setSellerId(sellerId);
        product.setTitle(title);
        product.setDescription("订单测试商品");
        product.setCategory("数码");
        product.setCondition("全新");
        product.setOriginalPrice(new java.math.BigDecimal("100.00"));
        product.setPrice(new java.math.BigDecimal("50.00"));
        product.setTradeLocation("图书馆");
        product.setNegotiable(true);
        product.setImages("[]");
        product.setStatus("active");
        productMapper.insert(product);
        return product;
    }

    // ---- 基础 CRUD ----

    @Test
    @org.junit.jupiter.api.Order(1)
    @Transactional
    void shouldInsertAndFindOrder() {
        ensureUsers();
        Product product = createTestProduct(testSeller.getId(), "订单测试商品A");

        Order order = buildOrder(product.getId(), testBuyer.getId(), testSeller.getId(), "order_test_A_001");
        int inserted = orderMapper.insert(order);
        assertEquals(1, inserted);
        assertNotNull(order.getId());

        Order found = orderMapper.findById(order.getId());
        assertNotNull(found);
        assertEquals(product.getId(), found.getProductId());
        assertEquals(testBuyer.getId(), found.getBuyerId());
        assertEquals(testSeller.getId(), found.getSellerId());
        assertEquals("pending", found.getStatus());
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @Transactional
    void shouldFindByIdForUpdate() {
        ensureUsers();
        Product product = createTestProduct(testSeller.getId(), "订单测试商品B");

        Order order = buildOrder(product.getId(), testBuyer.getId(), testSeller.getId(), "order_test_B_001");
        orderMapper.insert(order);

        // FOR UPDATE 在同一事务中应能正常获取行锁
        Order locked = orderMapper.findByIdForUpdate(order.getId());
        assertNotNull(locked);
        assertEquals(order.getId(), locked.getId());
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @Transactional
    void shouldUpdateStatusAndTrackMetAt() {
        ensureUsers();
        Product product = createTestProduct(testSeller.getId(), "订单测试商品C");

        Order order = buildOrder(product.getId(), testBuyer.getId(), testSeller.getId(), "order_test_C_001");
        orderMapper.insert(order);

        // 状态流转：pending → met
        order.setStatus("met");
        order.setMetAt(java.time.LocalDateTime.now());
        int updated = orderMapper.updateStatus(order);
        assertEquals(1, updated);

        Order found = orderMapper.findById(order.getId());
        assertEquals("met", found.getStatus());
        assertNotNull(found.getMetAt());
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @Transactional
    void shouldUpdateStatusWithCancelledBy() {
        ensureUsers();
        Product product = createTestProduct(testSeller.getId(), "订单测试商品D");

        Order order = buildOrder(product.getId(), testBuyer.getId(), testSeller.getId(), "order_test_D_001");
        orderMapper.insert(order);

        // 状态流转：pending → cancelled
        order.setStatus("cancelled");
        order.setCancelledBy("buyer");
        int updated = orderMapper.updateStatus(order);
        assertEquals(1, updated);

        Order found = orderMapper.findById(order.getId());
        assertEquals("cancelled", found.getStatus());
        assertEquals("buyer", found.getCancelledBy());
    }

    // ---- 幂等键 ----

    @Test
    @org.junit.jupiter.api.Order(5)
    @Transactional
    void shouldFindByIdempotentKey() {
        ensureUsers();
        Product product = createTestProduct(testSeller.getId(), "订单测试商品E");

        String idempotentKey = "order_test_E_" + testBuyer.getId() + "_" + product.getId();
        Order order = buildOrder(product.getId(), testBuyer.getId(), testSeller.getId(), idempotentKey);
        orderMapper.insert(order);

        Order found = orderMapper.findByIdempotentKey(idempotentKey);
        assertNotNull(found);
        assertEquals(order.getId(), found.getId());
        assertEquals(idempotentKey, found.getIdempotentKey());
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @Transactional
    void shouldReturnNullForNonExistentIdempotentKey() {
        Order found = orderMapper.findByIdempotentKey("non_existent_key_99999");
        assertNull(found);
    }

    // ---- 按角色查询 ----

    @Test
    @org.junit.jupiter.api.Order(7)
    @Transactional
    void shouldListByBuyerRole() {
        ensureUsers();
        Product product = createTestProduct(testSeller.getId(), "订单测试商品F");

        Order order = buildOrder(product.getId(), testBuyer.getId(), testSeller.getId(), "order_test_F_001");
        orderMapper.insert(order);

        // 以买家身份查询
        List<Order> buyerOrders = orderMapper.listByUserRole(testBuyer.getId(), "buyer", null, 0, 10);
        assertNotNull(buyerOrders);
        assertFalse(buyerOrders.isEmpty());
        assertTrue(buyerOrders.stream().allMatch(o -> testBuyer.getId().equals(o.getBuyerId())));
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @Transactional
    void shouldListBySellerRole() {
        ensureUsers();
        Product product = createTestProduct(testSeller.getId(), "订单测试商品G");

        Order order = buildOrder(product.getId(), testBuyer.getId(), testSeller.getId(), "order_test_G_001");
        orderMapper.insert(order);

        // 以卖家身份查询
        List<Order> sellerOrders = orderMapper.listByUserRole(testSeller.getId(), "seller", null, 0, 10);
        assertNotNull(sellerOrders);
        assertFalse(sellerOrders.isEmpty());
        assertTrue(sellerOrders.stream().allMatch(o -> testSeller.getId().equals(o.getSellerId())));
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @Transactional
    void shouldListByUserRoleWithStatusFilter() {
        ensureUsers();
        Product product = createTestProduct(testSeller.getId(), "订单测试商品H");

        // 创建 pending 订单
        Order pendingOrder = buildOrder(product.getId(), testBuyer.getId(), testSeller.getId(), "order_test_H_001");
        orderMapper.insert(pendingOrder);

        // 创建 cancelled 订单
        Order cancelledOrder = buildOrder(product.getId(), testBuyer.getId(), testSeller.getId(), "order_test_H_002");
        orderMapper.insert(cancelledOrder);
        cancelledOrder.setStatus("cancelled");
        cancelledOrder.setCancelledBy("buyer");
        orderMapper.updateStatus(cancelledOrder);

        // 只查 pending
        List<Order> pending = orderMapper.listByUserRole(testBuyer.getId(), "buyer", "pending", 0, 10);
        assertTrue(pending.stream().allMatch(o -> "pending".equals(o.getStatus())));

        // 只查 cancelled
        List<Order> cancelled = orderMapper.listByUserRole(testBuyer.getId(), "buyer", "cancelled", 0, 10);
        assertTrue(cancelled.stream().allMatch(o -> "cancelled".equals(o.getStatus())));
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @Transactional
    void shouldCountByUserRole() {
        ensureUsers();
        Product product = createTestProduct(testSeller.getId(), "订单测试商品I");

        orderMapper.insert(buildOrder(product.getId(), testBuyer.getId(), testSeller.getId(), "order_test_I_001"));
        orderMapper.insert(buildOrder(product.getId(), testBuyer.getId(), testSeller.getId(), "order_test_I_002"));

        long count = orderMapper.countByUserRole(testBuyer.getId(), "buyer", null);
        assertTrue(count >= 2);
    }

    // ---- 边界条件 ----

    @Test
    @org.junit.jupiter.api.Order(11)
    @Transactional
    void shouldReturnNullForNonExistentOrder() {
        Order found = orderMapper.findById(99999999L);
        assertNull(found);
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @Transactional
    void shouldReturnEmptyListForNonExistentUser() {
        List<Order> orders = orderMapper.listByUserRole(99999999L, "buyer", null, 0, 10);
        assertNotNull(orders);
        assertTrue(orders.isEmpty());
    }

    // ---- helpers ----

    private Order buildOrder(Long productId, Long buyerId, Long sellerId, String idempotentKey) {
        Order order = new Order();
        order.setProductId(productId);
        order.setBuyerId(buyerId);
        order.setSellerId(sellerId);
        order.setStatus("pending");
        order.setIdempotentKey(idempotentKey);
        order.setProductSnapshot("{\"title\":\"快照商品\"}");
        return order;
    }

    private User buildUser(String phone, String nickname) {
        User user = new User();
        user.setPhone(phone);
        user.setNickname(nickname);
        user.setAvatar("");
        user.setClassName("CS2024");
        user.setDormBuilding("北区");
        user.setRole("user");
        user.setCreditScore(100);
        user.setStatus("active");
        user.setTokenVersion(0);
        return user;
    }
}
