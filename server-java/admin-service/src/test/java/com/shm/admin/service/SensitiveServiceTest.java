package com.shm.admin.service;

import com.shm.common.util.SensitiveWordFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SensitiveService 单元测试（Phase 11 11.1.6）
 *
 * <p>覆盖词库统计/重新加载/文本检查，Mock SensitiveWordFilter 层。
 */
@ExtendWith(MockitoExtension.class)
class SensitiveServiceTest {

    @Mock
    private SensitiveWordFilter sensitiveFilter;

    private SensitiveService sensitiveService;

    @BeforeEach
    void setUp() {
        sensitiveService = new SensitiveService(sensitiveFilter);
    }

    // ============================================================
    // stats — 敏感词库统计
    // ============================================================

    @Test
    void stats_shouldReturnWordCount() {
        when(sensitiveFilter.getWordCount()).thenReturn(150);

        Map<String, Object> result = sensitiveService.stats();

        assertEquals(150, result.get("word_count"));
    }

    @Test
    void stats_zeroWords_shouldReturnZero() {
        when(sensitiveFilter.getWordCount()).thenReturn(0);

        Map<String, Object> result = sensitiveService.stats();

        assertEquals(0, result.get("word_count"));
    }

    // ============================================================
    // reload — 重新加载敏感词库
    // ============================================================

    @Test
    void reload_shouldCallFilterReloadAndReturnCount() {
        when(sensitiveFilter.getWordCount()).thenReturn(200);

        Map<String, Object> result = sensitiveService.reload(1L);

        verify(sensitiveFilter).reload();
        assertEquals(true, result.get("success"));
        assertEquals(200, result.get("word_count"));
    }

    // ============================================================
    // check — 检查文本
    // ============================================================

    @Test
    void check_noSensitive_shouldReturnEmptyWords() {
        when(sensitiveFilter.findMatches("正常文本")).thenReturn(Collections.emptySet());

        Map<String, Object> result = sensitiveService.check("正常文本");

        assertEquals(false, result.get("has_sensitive"));
        @SuppressWarnings("unchecked")
        java.util.List<String> words = (java.util.List<String>) result.get("words");
        assertTrue(words.isEmpty());
    }

    @Test
    void check_withSensitive_shouldReturnMatchedWords() {
        when(sensitiveFilter.findMatches("包含敏感词的文本"))
                .thenReturn(Set.of("敏感词"));

        Map<String, Object> result = sensitiveService.check("包含敏感词的文本");

        assertEquals(true, result.get("has_sensitive"));
        @SuppressWarnings("unchecked")
        java.util.List<String> words = (java.util.List<String>) result.get("words");
        assertEquals(1, words.size());
        assertTrue(words.contains("敏感词"));
    }

    @Test
    void check_multipleSensitiveWords_shouldReturnSorted() {
        when(sensitiveFilter.findMatches("a b c"))
                .thenReturn(Set.of("c", "a", "b"));

        Map<String, Object> result = sensitiveService.check("a b c");

        assertEquals(true, result.get("has_sensitive"));
        @SuppressWarnings("unchecked")
        java.util.List<String> words = (java.util.List<String>) result.get("words");
        assertEquals(3, words.size());
        assertEquals("a", words.get(0)); // 已排序
        assertEquals("b", words.get(1));
        assertEquals("c", words.get(2));
    }
}
