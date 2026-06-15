package com.shm.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.dto.report.CreateReportRequest;
import com.shm.common.model.entity.Notification;
import com.shm.common.model.entity.Order;
import com.shm.common.model.entity.Product;
import com.shm.common.model.entity.Report;
import com.shm.common.model.entity.User;
import com.shm.core.feign.ImConnectorFeign;
import com.shm.core.repository.NotificationRepository;
import com.shm.core.repository.OrderRepository;
import com.shm.core.repository.ProductRepository;
import com.shm.core.repository.ReportRepository;
import com.shm.core.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 举报服务（与 Node.js services/report.js 行为完全一致）
 *
 * <p>处理举报创建、我的举报列表查询、举报详情。
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepo;
    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final NotificationRepository notificationRepo;
    private final ImConnectorFeign imConnectorFeign;
    private final ObjectMapper objectMapper;

    public ReportService(ReportRepository reportRepo, OrderRepository orderRepo,
                         ProductRepository productRepo, UserRepository userRepo,
                         NotificationRepository notificationRepo,
                         ImConnectorFeign imConnectorFeign,
                         ObjectMapper objectMapper) {
        this.reportRepo = reportRepo;
        this.orderRepo = orderRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.notificationRepo = notificationRepo;
        this.imConnectorFeign = imConnectorFeign;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建举报（与 Node.js reportService.create 一致）
     */
    public Map<String, Object> create(Long reporterId, CreateReportRequest data) {
        // 举报自己检查
        if (data.getReportedUserId().equals(reporterId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能举报自己");
        }

        // 重复举报检查（与 Node.js hasActiveReport 一致）
        long activeCount = reportRepo.countActiveByReporter(reporterId,
                data.getReportedUserId(), data.getProductId(), data.getOrderId());
        if (activeCount > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_REPORT);
        }

        // 如果关联了订单，验证
        if (data.getOrderId() != null) {
            Order order = orderRepo.findById(data.getOrderId());
            if (order == null) {
                throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
            }
            if (!order.getBuyerId().equals(reporterId) && !order.getSellerId().equals(reporterId)) {
                throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "你未参与该订单，无法举报");
            }
        }

        // 如果关联了商品，验证
        if (data.getProductId() != null) {
            Product product = productRepo.findById(data.getProductId());
            if (product == null) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            }
        }

        // 证据图片序列化（与 Node.js 一致：空值存 "[]" 而非 null，防止前端 JSON.parse(null) 抛异常）
        String evidenceJson = data.getEvidenceImages() != null && !data.getEvidenceImages().isEmpty()
                ? toJson(data.getEvidenceImages()) : "[]";

        Report report = Report.builder()
                .reporterId(reporterId)
                .reportedUserId(data.getReportedUserId())
                .productId(data.getProductId())
                .orderId(data.getOrderId())
                .type(data.getType())
                .description(data.getDescription())
                .evidenceImages(evidenceJson)
                .status("pending")
                .build();

        Report created = reportRepo.insert(report);

        log.info("举报创建: reportId={}, reporterId={}, reportedUserId={}", created.getId(), reporterId, data.getReportedUserId());

        // 通知所有管理员有新的举报
        notifyAdmins(created);

        return toReportMap(created);
    }

    /**
     * 通知所有管理员 — 有新举报提交
     *
     * <p>双重投递（与 OrderService.notifyUser 模式一致）：
     * <ol>
     *   <li>写入 notifications 表（站内通知，持久化）</li>
     *   <li>通过 Feign 调用 IM Connector 推送实时消息</li>
     * </ol>
     *
     * <p>IM 推送失败不影响主流程（仅记录日志），避免 IM 服务异常阻塞举报事务。
     */
    private void notifyAdmins(Report report) {
        List<User> admins = userRepo.findAdminUsers();
        if (admins.isEmpty()) {
            log.warn("未找到管理员用户，跳过举报通知: reportId={}", report.getId());
            return;
        }

        String type = "report_new";
        String title = "新举报";
        String content = "用户举报了「" + report.getType() + "」问题，请及时处理";
        String metadata = toJson(Map.of("report_id", report.getId(),
                "route", "/pages/report/detail?id=" + report.getId()));

        for (User admin : admins) {
            // 1. 写入站内通知（DB 持久化）
            Notification notification = Notification.builder()
                    .userId(admin.getId())
                    .type(type)
                    .title(title)
                    .content(content)
                    .isRead(false)
                    .metadata(metadata)
                    .build();
            notificationRepo.insert(notification);
            log.info("管理员举报通知已写入: adminId={}, reportId={}", admin.getId(), report.getId());

            // 2. 推送 IM 实时消息（静默降级）
            try {
                Map<String, Object> imResult = imConnectorFeign.sendSystemMessage(
                        String.valueOf(admin.getId()), title, content, report.getId());
                if (imResult != null && imResult.containsKey("code")) {
                    int code = ((Number) imResult.get("code")).intValue();
                    if (code != 0) {
                        log.warn("管理员 IM 推送失败: adminId={}, code={}, message={}",
                                admin.getId(), code, imResult.get("message"));
                    }
                }
            } catch (Exception e) {
                log.warn("管理员 IM 推送异常（已降级为站内通知）: adminId={}, error={}",
                        admin.getId(), e.getMessage());
            }
        }
    }

    /**
     * 我的举报列表（与 Node.js reportService.list 完全一致）
     * <p>DB 层按 reporter_id 过滤后再分页，total 也是本人举报数。
     */
    public Map<String, Object> list(Long reporterId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Report> reports = reportRepo.listWithFilters(reporterId, null, null, offset, pageSize);
        long total = reportRepo.countWithFilters(reporterId, null, null);

        List<Map<String, Object>> list = reports.stream()
                .map(this::toReportMap)
                .toList();

        return Map.of("list", list, "total", total, "page", page, "pageSize", pageSize);
    }

    /**
     * 举报详情（仅举报人本人或 cs/admin 可查看，与 Node.js reportService.detail 一致）
     *
     * <p>含举报人/被举报人昵称头像，与 Node.js JOIN users 行为对齐。
     */
    public Map<String, Object> detail(Long reportId, Long userId, String userRole) {
        Report report = reportRepo.findById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }

        // 权限：本人的举报 或 cs/admin 角色
        if (!report.getReporterId().equals(userId) && !"cs".equals(userRole) && !"admin".equals(userRole)) {
            throw new BusinessException(ErrorCode.NOT_OWNER);
        }

        // 批量查询举报人和被举报人信息
        Set<Long> userIds = new HashSet<>();
        userIds.add(report.getReporterId());
        userIds.add(report.getReportedUserId());
        Map<Long, User> userMap = userRepo.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return toReportDetailMap(report, userMap);
    }

    // ---- 辅助方法 ----

    private Map<String, Object> toReportMap(Report r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", r.getId());
        map.put("reporter_id", r.getReporterId());
        map.put("reported_user_id", r.getReportedUserId());
        map.put("product_id", r.getProductId());
        map.put("order_id", r.getOrderId());
        map.put("type", r.getType());
        map.put("description", r.getDescription());
        map.put("evidence_images", parseJson(r.getEvidenceImages()));
        map.put("status", r.getStatus());
        map.put("resolution", r.getResolution());
        map.put("created_at", r.getCreatedAt());
        map.put("resolved_at", r.getResolvedAt());
        return map;
    }

    /** 举报详情 — 含举报人/被举报人昵称头像 */
    private Map<String, Object> toReportDetailMap(Report r, Map<Long, User> userMap) {
        Map<String, Object> map = toReportMap(r);
        User reporter = userMap.get(r.getReporterId());
        User reported = userMap.get(r.getReportedUserId());
        if (reporter != null) {
            map.put("reporter_nickname", reporter.getNickname());
            map.put("reporter_avatar", reporter.getAvatar());
        }
        if (reported != null) {
            map.put("reported_user_nickname", reported.getNickname());
            map.put("reported_user_avatar", reported.getAvatar());
        }
        return map;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            return "[]";
        }
    }

    private Object parseJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
