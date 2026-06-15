package com.shm.core.controller;

import com.shm.common.util.ResponseBuilder;
import com.shm.core.security.CurrentUser;
import com.shm.core.security.UserPrincipal;
import com.shm.core.service.CreditService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 信誉分控制器（与 Node.js controllers/credit.js 行为完全一致）
 */
@RestController
public class CreditController {

    private final CreditService creditService;

    public CreditController(CreditService creditService) {
        this.creditService = creditService;
    }

    /**
     * GET /api/credit — 我的信誉分 + 变动记录
     */
    @GetMapping("/api/credit")
    public Map<String, Object> my(@CurrentUser UserPrincipal user,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int pageSize) {
        pageSize = Math.min(pageSize, 50);
        return ResponseBuilder.ok(creditService.my(user.getUserId(), page, pageSize));
    }

    /**
     * GET /api/users/:id/credit — 查看某用户信誉分（公开）
     */
    @GetMapping("/api/users/{id}/credit")
    public Map<String, Object> userPublic(@PathVariable Long id) {
        return ResponseBuilder.ok(creditService.userPublic(id));
    }
}
