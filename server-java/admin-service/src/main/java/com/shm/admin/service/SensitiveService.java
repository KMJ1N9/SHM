package com.shm.admin.service;

import com.shm.common.util.SensitiveWordFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 敏感词库管理服务（与 Node.js services/admin.js 敏感词管理部分行为完全一致）
 *
 * <p>提供词库统计、重新加载、文本检查功能。
 */
@Service
public class SensitiveService {

    private static final Logger log = LoggerFactory.getLogger(SensitiveService.class);

    private final SensitiveWordFilter sensitiveFilter;

    public SensitiveService(SensitiveWordFilter sensitiveFilter) {
        this.sensitiveFilter = sensitiveFilter;
    }

    /**
     * 敏感词库统计（与 Node.js adminService.sensitiveStats 一致）
     */
    public Map<String, Object> stats() {
        return Map.of("word_count", sensitiveFilter.getWordCount());
    }

    /**
     * 重新加载敏感词库（与 Node.js adminService.reloadSensitive 一致）
     */
    public Map<String, Object> reload(Long adminId) {
        sensitiveFilter.reload();
        log.info("敏感词库重新加载: adminId={}", adminId);
        return Map.of("success", true, "word_count", sensitiveFilter.getWordCount());
    }

    /**
     * 检查文本是否包含敏感词（与 Node.js adminService.checkSensitive 一致）
     */
    public Map<String, Object> check(String text) {
        Set<String> words = sensitiveFilter.findMatches(text);
        return Map.of(
                "has_sensitive", !words.isEmpty(),
                "words", words.isEmpty() ? List.of() : words.stream().sorted().toList()
        );
    }
}
