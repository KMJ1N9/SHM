package com.shm.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.common.constant.RedisKeys;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.dto.order.CreateOrderRequest;
import com.shm.common.model.entity.Notification;
import com.shm.common.model.entity.Order;
import com.shm.common.model.entity.Product;
import com.shm.common.model.entity.User;
import com.shm.common.model.dto.admin.AdminLogRequest;
import com.shm.common.model.dto.message.OrderEventMessage;
import com.shm.core.config.CreditProperties;
import com.shm.core.feign.AdminLogFeign;
import com.shm.core.lock.DistributedLocker;
import com.shm.core.mq.OrderEventPublisher;
import com.shm.core.repository.NotificationRepository;
import com.shm.core.repository.OrderRepository;
import com.shm.core.repository.ProductRepository;
import com.shm.core.repository.UserRepository;
import io.seata.spring.annotation.GlobalTransactional;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 订单服务（与 Node.js services/order.js 行为完全一致）
 *
 * <p>处理订单创建、列表、详情、面交确认、收货确认、取消。
 *
 * <p>订单状态机：
 * <pre>
 *   pending → met → completed
 *   pending → cancelled
 *   met → cancelled (仅买家)
 * </pre>
 *
 * <p>幂等性：${buyer_id}_${product_id} 作为 idempotent_key。
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final NotificationRepository notificationRepo;
    private final ObjectMapper objectMapper;
    private final CreditProperties creditProps;
    private final ObjectProvider<OrderEventPublisher> orderEventPublisher;
    private final DistributedLocker distributedLocker;
    private final AdminLogFeign adminLogFeign;

    /** 下单分布式锁 TTL（秒） */
    private static final long ORDER_LOCK_TTL = 30;

    public OrderService(OrderRepository orderRepo, ProductRepository productRepo,
                        UserRepository userRepo, NotificationRepository notificationRepo,
                        ObjectMapper objectMapper, CreditProperties creditProps,
                        ObjectProvider<OrderEventPublisher> orderEventPublisher, DistributedLocker distributedLocker,
                        AdminLogFeign adminLogFeign) {
        this.orderRepo = orderRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.notificationRepo = notificationRepo;
        this.objectMapper = objectMapper;
        this.creditProps = creditProps;
        this.orderEventPublisher = orderEventPublisher;
        this.distributedLocker = distributedLocker;
        this.adminLogFeign = adminLogFeign;
    }

    // ============================================================
    // 创建订单
    // ============================================================

    /**
     * 创建订单（与 Node.js orderService.create 一致）
     *
     * <p>事务内操作：锁定商品 → 校验 → 更新商品状态 → 插入订单。
     */
    @Transactional
    public Map<String, Object> create(Long buyerId, int creditScore, CreateOrderRequest data) {
        // 信誉分阈值检查
        if (creditScore < creditProps.getTradeThreshold()) {
            throw new BusinessException(ErrorCode.CREDIT_TOO_LOW_TRADE, "信誉分不足（需 ≥ " + creditProps.getTradeThreshold() + "），无法参与交易");
        }

        // 分布式锁（防重复下单，Phase 16 Redisson 增强：WatchDog + 可重入）
        String lockKey = RedisKeys.orderLockKey(buyerId, data.getProductId());
        RLock lock = distributedLocker.tryAcquire(lockKey, ORDER_LOCK_TTL, TimeUnit.SECONDS);
        if (lock == null) {
            log.warn("下单分布式锁冲突: buyerId={}, productId={}", buyerId, data.getProductId());
            // 获取锁失败 → 检查是否是幂等请求（并发场景下第一个请求已创建订单）
            String idempotentKey = buyerId + "_" + data.getProductId();
            Order existing = orderRepo.findByIdempotentKey(idempotentKey);
            if (existing != null) {
                Map<Long, User> lockUsers = fetchUsersForOrder(existing.getBuyerId(), existing.getSellerId());
                Map<String, Object> result = toOrderMap(existing, lockUsers);
                result.put("created", false);
                return result;
            }
            throw new BusinessException(ErrorCode.RATE_LIMITED, "操作过于频繁，请稍后再试");
        }

        try {
            return doCreate(buyerId, creditScore, data);
        } finally {
            distributedLocker.release(lock);
        }
    }

    /** 下单核心逻辑（锁内执行） */
    private Map<String, Object> doCreate(Long buyerId, int creditScore, CreateOrderRequest data) {
        // 幂等性检查
        String idempotentKey = buyerId + "_" + data.getProductId();
        Order existing = orderRepo.findByIdempotentKey(idempotentKey);
        if (existing != null) {
            Map<Long, User> idemUsers = fetchUsersForOrder(existing.getBuyerId(), existing.getSellerId());
            Map<String, Object> result = toOrderMap(existing, idemUsers);
            result.put("created", false);
            return result;
        }

        // 锁定商品（FOR UPDATE 防并发）
        Product product = productRepo.findByIdForUpdate(data.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        if (product.getSellerId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.CANNOT_BUY_OWN);
        }
        if (!"active".equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ACTIVE);
        }

        // 获取买卖家信息（卖家用于快照，买家用于响应）
        User seller = userRepo.findById(product.getSellerId());
        User buyer = userRepo.findById(buyerId);
        Map<Long, User> orderUsers = new HashMap<>();
        if (seller != null) orderUsers.put(seller.getId(), seller);
        if (buyer != null) orderUsers.put(buyer.getId(), buyer);

        // 构建商品快照
        Map<String, Object> snapshot = buildProductSnapshot(product, seller);

        // 更新商品状态 → reserved
        productRepo.updateStatus(product.getId(), "reserved");

        // 创建订单（捕获唯一约束冲突，处理并发重复提交）
        Order order = Order.builder()
                .productId(data.getProductId())
                .buyerId(buyerId)
                .sellerId(product.getSellerId())
                .status("pending")
                .idempotentKey(idempotentKey)
                .productSnapshot(toJson(snapshot))
                .build();

        Order created;
        try {
            created = orderRepo.create(order);
        } catch (DuplicateKeyException e) {
            // 并发竞态：另一个请求在 check 和 insert 之间创建了相同幂等键的订单
            Order concurrent = orderRepo.findByIdempotentKey(idempotentKey);
            if (concurrent != null) {
                log.warn("幂等键冲突（并发）: idempotentKey={}, existingOrderId={}", idempotentKey, concurrent.getId());
                Map<Long, User> dupUsers = fetchUsersForOrder(concurrent.getBuyerId(), concurrent.getSellerId());
                Map<String, Object> result = toOrderMap(concurrent, dupUsers);
                result.put("created", false);
                return result;
            }
            throw e; // 意外情况 — 非幂等键导致的唯一约束冲突
        }

        log.info("订单创建: orderId={}, buyerId={}, productId={}", created.getId(), buyerId, data.getProductId());

        // IM 通知：通知卖家有人想要购买
        notifyUser(product.getSellerId(), "order_update", "新订单",
                "有人想要购买你的商品「" + product.getTitle() + "」", created.getId());

        Map<String, Object> result = toOrderMap(created, orderUsers);
        result.put("created", true);
        return result;
    }

    // ============================================================
    // 订单列表
    // ============================================================

    /**
     * 我的订单列表（与 Node.js orderService.list 一致）
     *
     * <p>批量查询买卖家信息，填充 nickname/avatar，与 Node.js JOIN users 行为对齐。
     */
    public Map<String, Object> list(Long userId, String role, String status, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Order> orders = orderRepo.listByUserRole(userId, role, status, offset, pageSize);
        long total = orderRepo.countByUserRole(userId, role, status);

        // 批量查询买卖家信息
        Set<Long> userIds = new HashSet<>();
        for (Order o : orders) {
            userIds.add(o.getBuyerId());
            userIds.add(o.getSellerId());
        }
        Map<Long, User> userMap = userRepo.findByIds(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> list = orders.stream()
                .map(o -> toOrderListRow(o, userMap))
                .toList();

        return Map.of("list", list, "total", total, "page", page, "pageSize", pageSize);
    }

    // ============================================================
    // 订单详情
    // ============================================================

    /**
     * 订单详情（与 Node.js orderService.detail 一致）
     */
    public Map<String, Object> detail(Long orderId, Long userId) {
        Order order = orderRepo.findById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_OWNER);
        }
        return toOrderMap(order, fetchUsersForOrder(order.getBuyerId(), order.getSellerId()));
    }

    // ============================================================
    // 标记面交
    // ============================================================

    /**
     * 标记面交（与 Node.js orderService.markAsMet 一致）
     */
    @Transactional
    public Map<String, Object> markAsMet(Long orderId, Long userId) {
        Order order = orderRepo.findByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_OWNER);
        }
        if (!"pending".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "仅待面交状态的订单可标记面交");
        }

        Order update = Order.builder()
                .id(orderId)
                .status("met")
                .metAt(LocalDateTime.now())
                .build();
        orderRepo.updateStatus(update);

        log.info("面交确认: orderId={}, userId={}", orderId, userId);

        // IM 通知：通知对方已确认面交
        Long notifyTarget = order.getBuyerId().equals(userId) ? order.getSellerId() : order.getBuyerId();
        notifyUser(notifyTarget, "order_update", "面交确认",
                "对方已确认面交，请尽快完成交易", orderId);

        Order updated = orderRepo.findById(orderId);
        return toOrderMap(updated, fetchUsersForOrder(updated.getBuyerId(), updated.getSellerId()));
    }

    // ============================================================
    // 确认收货
    // ============================================================

    /**
     * 确认收货（仅买家，与 Node.js orderService.confirm 一致）
     *
     * <p>事务内原子操作：FOR UPDATE 锁订单 → 校验 → 更新订单 + 商品 → 卖家信誉分+2。
     *
     * <h3>Phase 13 — Seata 全局事务</h3>
     * <p>{@code @GlobalTransactional} 将本方法注册为 Seata 全局事务入口。
     * core-service 本地写操作（订单/商品/用户/通知）为分支 1，
     * Feign 调用 admin-service 写审计日志为分支 2。
     * 任一分支失败 → Seata TC 协调两阶段回滚（undo_log 快照回滚）。
     */
    @GlobalTransactional(timeoutMills = 300000, name = "confirm-order")
    @Transactional
    public Map<String, Object> confirm(Long orderId, Long userId) {
        Order order = orderRepo.findByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getBuyerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_OWNER);
        }
        if (!"met".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "订单尚未面交，不能确认收货");
        }

        // 更新订单状态 → completed
        Order update = Order.builder()
                .id(orderId)
                .status("completed")
                .confirmedAt(LocalDateTime.now())
                .build();
        orderRepo.updateStatus(update);

        // 更新商品状态 → sold
        productRepo.updateStatus(order.getProductId(), "sold");

        // 卖家信誉分 +2
        userRepo.updateCreditScore(order.getSellerId(), creditProps.getRewardTransaction(), creditProps.getMax());

        log.info("订单确认收货: orderId={}, userId={}", orderId, userId);

        // IM 通知：通知双方订单已完成，请互相评价
        notifyUser(order.getBuyerId(), "order_update", "订单完成",
                "订单已完成，请互相评价", orderId);
        notifyUser(order.getSellerId(), "order_update", "订单完成",
                "订单已完成，请互相评价", orderId);

        // Phase 13 — Seata 跨服务事务分支：Feign 调用 admin-service 写入审计日志
        try {
            AdminLogRequest logReq = new AdminLogRequest(
                    userId, "buyer_confirm", "order", orderId,
                    "买家确认收货, productId=" + order.getProductId()
                            + ", sellerId=" + order.getSellerId());
            Map<String, Object> logResult = adminLogFeign.createLog(logReq);
            if (logResult != null) {
                int code = ((Number) logResult.getOrDefault("code", 0)).intValue();
                if (code != 0) {
                    log.warn("审计日志写入失败（Seata 分支将回滚）: code={}, message={}", code, logResult.get("message"));
                    throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                            "审计日志写入失败: " + logResult.get("message"));
                }
            }
        } catch (Exception e) {
            log.warn("审计日志写入异常（Seata 分支将回滚）: error={}", e.getMessage());
            throw e; // 抛出异常触发 Seata 回滚
        }

        Order updated = orderRepo.findById(orderId);
        return toOrderMap(updated, fetchUsersForOrder(updated.getBuyerId(), updated.getSellerId()));
    }

    // ============================================================
    // 取消订单
    // ============================================================

    /**
     * 取消订单（与 Node.js orderService.cancel 一致）
     *
     * <p>pending 状态买卖双方均可取消；met 状态仅买家可取消。
     * <p>事务内原子操作：FOR UPDATE 锁订单 → 校验 → 更新订单 + 恢复商品状态。
     */
    @Transactional
    public Map<String, Object> cancel(Long orderId, Long userId) {
        Order order = orderRepo.findByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }

        boolean isBuyer = order.getBuyerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);

        if (!isBuyer && !isSeller) {
            throw new BusinessException(ErrorCode.NOT_OWNER);
        }

        // 确定取消方
        String cancelledBy;
        if ("pending".equals(order.getStatus())) {
            cancelledBy = isBuyer ? "buyer" : "seller";
        } else if ("met".equals(order.getStatus()) && isBuyer) {
            cancelledBy = "buyer";
        } else {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "当前订单状态不可取消");
        }

        // 更新订单状态 → cancelled
        Order update = Order.builder()
                .id(orderId)
                .status("cancelled")
                .cancelledBy(cancelledBy)
                .build();
        orderRepo.updateStatus(update);

        // 恢复商品状态 → active
        productRepo.updateStatus(order.getProductId(), "active");

        log.info("订单取消: orderId={}, userId={}, cancelledBy={}", orderId, userId, cancelledBy);

        // IM 通知：通知对方订单已取消
        Long notifyTarget = isBuyer ? order.getSellerId() : order.getBuyerId();
        String cancelRole = "buyer".equals(cancelledBy) ? "买家" : "卖家";
        notifyUser(notifyTarget, "order_update", "订单取消",
                "订单已被" + cancelRole + "取消", orderId);

        Order updated = orderRepo.findById(orderId);
        return toOrderMap(updated, fetchUsersForOrder(updated.getBuyerId(), updated.getSellerId()));
    }

    // ============================================================
    // 内部辅助方法
    // ============================================================

    /** 构建商品快照 */
    private Map<String, Object> buildProductSnapshot(Product product, User seller) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", product.getTitle());
        snapshot.put("description", product.getDescription());
        snapshot.put("category", product.getCategory());
        snapshot.put("condition", product.getCondition());
        snapshot.put("price", product.getPrice());
        snapshot.put("original_price", product.getOriginalPrice());
        snapshot.put("trade_location", product.getTradeLocation());
        snapshot.put("images", parseJson(product.getImages()));
        snapshot.put("negotiable", product.getNegotiable());
        snapshot.put("seller_id", product.getSellerId());
        if (seller != null) {
            snapshot.put("seller_nickname", seller.getNickname());
            snapshot.put("seller_avatar", seller.getAvatar());
        }
        return snapshot;
    }

    /** 对象 → JSON string */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            return "{}";
        }
    }

    /** 订单实体 → API 响应 Map（含买卖家昵称/头像，与 Node.js JOIN users 行为对齐） */
    private Map<String, Object> toOrderMap(Order o, Map<Long, User> userMap) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", o.getId());
        map.put("product_id", o.getProductId());
        map.put("buyer_id", o.getBuyerId());
        map.put("seller_id", o.getSellerId());
        map.put("status", o.getStatus());
        map.put("cancelled_by", o.getCancelledBy());
        map.put("idempotent_key", o.getIdempotentKey());
        map.put("product_snapshot", parseJson(o.getProductSnapshot()));
        map.put("met_at", o.getMetAt());
        map.put("confirmed_at", o.getConfirmedAt());
        map.put("created_at", o.getCreatedAt());
        map.put("updated_at", o.getUpdatedAt());

        // 买卖家昵称/头像（与 Node.js JOIN users 行为对齐）
        if (userMap != null) {
            User buyer = userMap.get(o.getBuyerId());
            User seller = userMap.get(o.getSellerId());
            map.put("buyer_nickname", buyer != null ? buyer.getNickname() : "");
            map.put("buyer_avatar", buyer != null ? buyer.getAvatar() : "");
            map.put("seller_nickname", seller != null ? seller.getNickname() : "");
            map.put("seller_avatar", seller != null ? seller.getAvatar() : "");
        } else {
            map.put("buyer_nickname", "");
            map.put("buyer_avatar", "");
            map.put("seller_nickname", "");
            map.put("seller_avatar", "");
        }
        return map;
    }

    /** 订单列表行 — 委托 toOrderMap（userMap 已包含买卖家） */
    private Map<String, Object> toOrderListRow(Order o, Map<Long, User> userMap) {
        return toOrderMap(o, userMap);
    }

    /** 批量查询订单的买卖家信息 */
    private Map<Long, User> fetchUsersForOrder(Long buyerId, Long sellerId) {
        return userRepo.findByIds(Set.of(buyerId, sellerId)).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
    }

    /** 安全解析 JSON string → Object */
    private Object parseJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    /**
     * 发送订单状态变更的系统通知（与 Node.js IM 通知行为对齐）
     *
     * <p>双重投递：
     * <ol>
     *   <li>写入 notifications 表（站内通知，持久化）</li>
     *   <li>通过 Feign 调用 IM Connector 推送实时消息（即时通知）</li>
     * </ol>
     *
     * <p>IM 推送失败不影响主流程（仅记录日志），避免 IM 服务异常阻塞订单事务。
     */
    private void notifyUser(Long userId, String type, String title, String content, Long orderId) {
        // 1. 写入站内通知（DB 持久化）
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("order_id", orderId);
        String metadata = toJson(meta);
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .isRead(false)
                .metadata(metadata)
                .build();
        notificationRepo.insert(notification);
        log.info("站内通知已写入: userId={}, type={}, orderId={}", userId, type, orderId);

        // 2. 通过 RocketMQ 异步推送 IM 实时消息（由 ImPushOrderConsumer 消费）
        try {
            OrderEventMessage event = OrderEventMessage.builder()
                    .orderId(orderId)
                    .type(type)
                    .title(title)
                    .content(content)
                    .targetUid(String.valueOf(userId))
                    .timestamp(System.currentTimeMillis())
                    .build();
            orderEventPublisher.ifAvailable(p -> p.publishOrderEvent(event));
        } catch (Exception e) {
            // MQ 不可用时静默降级——站内通知已写入，用户下次打开小程序可见
            log.warn("订单事件发布异常（已降级为站内通知）: userId={}, error={}", userId, e.getMessage());
        }
    }
}
