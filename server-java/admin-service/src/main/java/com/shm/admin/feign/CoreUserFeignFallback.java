package com.shm.admin.feign;

import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.dto.internal.PenaltyRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * CoreUserFeign Sentinel Fallback（Phase 13）
 *
 * <p>当 core-service 不可用时抛出异常，触发 Seata 全局事务回滚。
 * 用户处罚是 Seata 事务分支，不可降级静默丢弃。
 */
@Component
public class CoreUserFeignFallback implements CoreUserFeign {

    private static final Logger log = LoggerFactory.getLogger(CoreUserFeignFallback.class);

    @Override
    public Map<String, Object> applyPenalty(Long userId, PenaltyRequest request) {
        log.error("[Seata] Core Service 降级触发回滚: userId={}, penalty={}", userId, request.getPenalty());
        throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Seata 分支降级: core-service 处罚服务不可用");
    }
}
