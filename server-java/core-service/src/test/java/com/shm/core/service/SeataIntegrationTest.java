package com.shm.core.service;

import com.shm.common.model.dto.admin.AdminLogRequest;
import com.shm.core.config.CreditProperties;
import com.shm.core.feign.AdminLogFeign;
import com.shm.core.feign.ImConnectorFeign;
import com.shm.core.lock.DistributedLocker;
import com.shm.core.repository.NotificationRepository;
import com.shm.core.repository.OrderRepository;
import com.shm.core.repository.ProductRepository;
import com.shm.core.repository.UserRepository;
import io.seata.spring.annotation.GlobalTransactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Seata 分布式事务集成测试（Phase 13.2.4）
 *
 * <h3>前置条件</h3>
 * <ul>
 *   <li>Seata Server 运行中（file 模式或 Nacos 模式，端口 8091）</li>
 *   <li>MySQL 可达，undo_log 表已创建（V003__seata_undo_log.sql）</li>
 *   <li>seata.enabled=true（application.yml 或环境变量 SEATA_ENABLED=true）</li>
 * </ul>
 *
 * <h3>测试分层</h3>
 * <ul>
 *   <li><b>配置验证（可运行）</b> — 验证 @GlobalTransactional 注解是否配置在正确的方法上</li>
 *   <li><b>Feign Fallback 测试（可运行）</b> — 验证 Seata 未启用时 Feign 降级行为</li>
 *   <li><b>Seata 全局事务测试（@Disabled）</b> — 需要 Seata Server 运行</li>
 * </ul>
 *
 * @see OrderService#confirm(Long, Long)
 * @see com.shm.admin.service.ReportAdminService#resolveTicket(Long, Long, String, String, int)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Seata 分布式事务集成测试")
class SeataIntegrationTest {

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
    private ImConnectorFeign imConnectorFeign;
    @Mock
    private DistributedLocker distributedLocker;
    @Mock
    private AdminLogFeign adminLogFeign;

    // ============================================================
    // 配置验证（无需 Seata Server）
    // ============================================================

    @Test
    @DisplayName("配置验证 — OrderService.confirm 应标注 @GlobalTransactional")
    void confirmMethod_shouldHaveGlobalTransactionalAnnotation() throws NoSuchMethodException {
        Method confirmMethod = OrderService.class.getMethod("confirm", Long.class, Long.class);

        GlobalTransactional annotation = confirmMethod.getAnnotation(GlobalTransactional.class);
        assertNotNull(annotation, "confirm() 方法必须标注 @GlobalTransactional");

        assertEquals("confirm-order", annotation.name(), "@GlobalTransactional name 应为 confirm-order");
        assertEquals(300000, annotation.timeoutMills(), "@GlobalTransactional timeoutMills 应为 300000");
    }

    @Test
    @DisplayName("配置验证 — ReportAdminService.resolveTicket 应标注 @GlobalTransactional")
    void resolveTicketMethod_shouldHaveGlobalTransactionalAnnotation() throws NoSuchMethodException {
        Class<?> reportAdminServiceClass;
        try {
            reportAdminServiceClass = Class.forName("com.shm.admin.service.ReportAdminService");
        } catch (ClassNotFoundException e) {
            // admin-service 不在 core-service 测试 classpath 中，跳过跨模块注解验证
            assumeTrue(false, "admin-service 不在 classpath 中，跳过跨模块 @GlobalTransactional 注解验证");
            return; // unreachable, assumeTrue(false) 已终止测试
        }

        Method resolveMethod = reportAdminServiceClass.getMethod(
                "resolveTicket", Long.class, Long.class, String.class, String.class, int.class);

        GlobalTransactional annotation = resolveMethod.getAnnotation(GlobalTransactional.class);
        assertNotNull(annotation, "resolveTicket() 方法必须标注 @GlobalTransactional");

        assertEquals("resolve-ticket", annotation.name(), "@GlobalTransactional name 应为 resolve-ticket");
        assertEquals(300000, annotation.timeoutMills(), "@GlobalTransactional timeoutMills 应为 300000");
    }

    // ============================================================
    // Feign Fallback 测试（Seata 未启用时仍可验证降级行为）
    // ============================================================

    @Test
    @DisplayName("Feign Fallback — AdminLogFeign 降级应抛出异常触发 Seata 回滚")
    void adminLogFeignFallback_shouldThrowException() {
        // 构造 Fallback 实例并验证其行为：降级必须抛异常以触发 Seata 回滚
        com.shm.core.feign.AdminLogFeignFallback fallback = new com.shm.core.feign.AdminLogFeignFallback();

        AdminLogRequest request = new AdminLogRequest(1L, "buyer_confirm", "order", 100L,
                "测试确认收货");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> fallback.createLog(request),
                "Fallback 应抛出异常触发 Seata 回滚");
        assertTrue(ex.getMessage().contains("Seata"), "异常消息应包含 Seata 上下文");
    }

    /**
     * CoreUserFeignFallback 测试在 admin-service 模块中。
     * 参见: ReportAdminServiceTest (admin-service/src/test/.../ReportAdminServiceTest.java)
     */

    // ============================================================
    // Seata 全局事务测试（需要 Seata Server — @Disabled 占位）
    // ============================================================

    /**
     * <h3>测试场景：确认收货 — Seata 正常流程</h3>
     *
     * <pre>
     *   Given: 订单状态 = met，商品状态 = reserved
     *   When: 买家确认收货
     *   Then:
     *     1. orders.status → "completed"（core 本地事务）
     *     2. products.status → "sold"（core 本地事务）
     *     3. users.credit_score += 2（core 本地事务）
     *     4. admin_logs 写入确认操作记录（Feign → admin-service 分支事务）
     *     5. undo_log 中存在对应的 xid 记录
     * </pre>
     *
     * <p>前置条件：MySQL 可达 + Seata Server 运行 + seata.enabled=true
     */
    @Test
    @Disabled("需要 Seata Server 运行（docker run seataio/seata-server:2.0.0）")
    @DisplayName("Seata 正常流程 — 确认收货跨服务事务提交")
    void seataNormalFlow_confirmOrder_shouldCommitBothBranches() {
        // 此测试需要完整 Spring Context（@SpringBootTest），非纯 Mockito 测试
        // 实现要点：
        // 1. 创建 met 状态订单（通过 Repository 直接插入）
        // 2. 调用 orderService.confirm(orderId, buyerId)
        // 3. 验证 orders.status = "completed", products.status = "sold"
        // 4. 验证 admin_logs 表有记录（通过 Feign 调用 admin-service）
        // 5. 查询 undo_log 表确认 xid 存在
        fail("此测试需要 Seata Server 运行环境。启用条件："
                + "1) docker run seataio/seata-server:2.0.0 "
                + "2) SEATA_ENABLED=true "
                + "3) MySQL 可达");
    }

    /**
     * <h3>测试场景：确认收货 — Seata 回滚流程</h3>
     *
     * <pre>
     *   Given: 订单状态 = met
     *   When: 买家确认收货，但 admin-service Feign 调用失败
     *   Then:
     *     1. 全局事务回滚
     *     2. orders.status 保持 "met"（未变成 "completed"）
     *     3. products.status 保持 "reserved"（未变成 "sold"）
     *     4. users.credit_score 未增加
     *     5. undo_log 中日志状态 = 1（全局完成，回滚已执行）
     * </pre>
     */
    @Test
    @Disabled("需要 Seata Server 运行 + admin-service 不可用")
    @DisplayName("Seata 回滚流程 — Feign 调用失败应回滚所有分支")
    void seataRollbackFlow_feignFailure_shouldRollbackAllBranches() {
        // 实现要点：
        // 1. 停止 admin-service（或 Mock admin-service 返回故障）
        // 2. 调用 orderService.confirm()
        // 3. 捕获异常（应抛出 BusinessException 或 Seata 回滚异常）
        // 4. 重新查询 DB：orders/products/users 状态应未改变
        fail("此测试需要 Seata Server + admin-service 可控环境");
    }

    /**
     * <h3>测试场景：裁决工单 — Seata 跨服务处罚</h3>
     *
     * <pre>
     *   Given: 工单状态 = processing
     *   When: 管理员裁决，penalty="deduct_credit"
     *   Then:
     *     1. reports.status → "resolved"（admin 本地事务）
     *     2. users.credit_score 扣减（Feign → core-service 分支事务）
     *     3. notifications 写入通知（admin 本地事务）
     *     4. admin_logs 写入操作记录（admin 本地事务）
     * </pre>
     */
    @Test
    @Disabled("需要 Seata Server 运行")
    @DisplayName("Seata 跨服务处罚 — 裁决工单应原子提交")
    void seataCrossService_resolveTicket_shouldCommitAllBranches() {
        fail("此测试需要 Seata Server 运行环境");
    }
}
