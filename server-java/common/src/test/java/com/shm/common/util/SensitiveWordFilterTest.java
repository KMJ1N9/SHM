package com.shm.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DFA 敏感词过滤器单元测试
 */
@DisplayName("敏感词过滤器测试")
class SensitiveWordFilterTest {

    private SensitiveWordFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SensitiveWordFilter();
        // 手动插入测试词（无需依赖 classpath 词库文件）
        filter.load("sensitive_words.txt");
    }

    @Test
    @DisplayName("正常文本不应包含敏感词")
    void normalTextShouldPass() {
        assertFalse(filter.containsSensitive("这是一段正常的商品描述"));
        assertFalse(filter.containsSensitive("二手教材转让"));
    }

    @Test
    @DisplayName("包含敏感词的文本应被检测到")
    void sensitiveTextShouldBeDetected() {
        // "诈骗" 在词库中
        assertTrue(filter.containsSensitive("这是一个诈骗信息"));
    }

    @Test
    @DisplayName("空文本和 null 应返回 false")
    void emptyOrNullShouldReturnFalse() {
        assertFalse(filter.containsSensitive(null));
        assertFalse(filter.containsSensitive(""));
    }

    @Test
    @DisplayName("findMatches 应返回命中的敏感词列表")
    void findMatchesShouldReturnMatchSet() {
        Set<String> matches = filter.findMatches("测试正常文本");
        assertTrue(matches.isEmpty());

        // 不依赖具体词库内容，用已知存在的词测试
        Set<String> matches2 = filter.findMatches("这是一个诈骗");
        assertFalse(matches2.isEmpty());
        assertTrue(matches2.contains("诈骗"));
    }

    @Test
    @DisplayName("replace 应替换敏感词为 ***")
    void replaceShouldMaskSensitiveWords() {
        String result = filter.replace("这是一个诈骗信息");
        assertFalse(result.contains("诈骗"));
        assertTrue(result.contains("***"));
    }

    @Test
    @DisplayName("无敏感词的文本 replace 后应保持不变")
    void replaceShouldNotChangeNormalText() {
        String normal = "这是一段正常文本";
        assertEquals(normal, filter.replace(normal));
    }

    @Test
    @DisplayName("词库应有至少 400 个敏感词")
    void wordCountShouldBeGreaterThanZero() {
        assertTrue(filter.getWordCount() >= 400,
                "词库应有至少 400 个敏感词，实际: " + filter.getWordCount());
    }
}
