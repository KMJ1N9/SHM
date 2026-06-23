package com.shm.core.feign;

import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.dto.admin.AdminLogRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AdminLogFeign Sentinel Fallback（Phase 13）
 *
 * <p>当 admin-service 不可用时抛出异常，触发 Seata 全局事务回滚。
 * 审计日志是 Seata 事务分支，不可降级静默丢弃。
 */
@Component
public class AdminLogFeignFallback implements AdminLogFeign {

    private static final Logger log = LoggerFactory.getLogger(AdminLogFeignFallback.class);

    @Override
    public Map<String, Object> createLog(AdminLogRequest request) {
        log.error("[Seata] Admin Service 降级触发回滚: action={}, targetId={}", request.getAction(), request.getTargetId());
        throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Seata 分支降级: admin-service 审计日志不可用");
    }
}
