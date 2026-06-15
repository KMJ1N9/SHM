package com.shm.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DFA 敏感词过滤器
 * <p>
 * 使用确定有限状态自动机（DFA）算法：
 * <ul>
 *   <li>时间复杂度 O(n)，n 为待检测文本长度</li>
 *   <li>空间复杂度 O(m * k)，m 为词库大小，k 为平均词长</li>
 *   <li>本地词库，无网络依赖</li>
 * </ul>
 * 与 Node.js {@code utils/sensitive-filter.js} 算法完全一致。
 */
@Component
public class SensitiveWordFilter {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordFilter.class);

    /** 默认词库 classpath 路径 */
    private static final String DEFAULT_DICT_PATH = "sensitive_words.txt";

    /** 替换符号 */
    private static final String DEFAULT_REPLACEMENT = "***";

    /** DFA 根节点 */
    private volatile DfaNode root = new DfaNode();

    /** 词库大小 */
    private int wordCount = 0;

    /** 是否已加载 */
    private volatile boolean loaded = false;

    /**
     * 加载词库（启动时自动调用）
     */
    public void load() {
        load(DEFAULT_DICT_PATH);
    }

    /**
     * 从 classpath 加载词库
     *
     * @param classpathResource classpath 下的词库文件名
     */
    public void load(String classpathResource) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(classpathResource)) {
            if (is == null) {
                log.warn("[SENSITIVE] 词库文件不存在: {}，使用空词库。", classpathResource);
                loaded = true;
                return;
            }

            List<String> words = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        words.add(line);
                    }
                }
            }

            // 重建 DFA 树
            root = new DfaNode();
            for (String word : words) {
                insert(word);
            }

            wordCount = words.size();
            loaded = true;
            log.info("[SENSITIVE] 词库加载完成，共 {} 个敏感词", words.size());
        } catch (IOException e) {
            log.error("[SENSITIVE] 词库加载失败: {}", e.getMessage(), e);
            loaded = true;
        }
    }

    /**
     * 插入单个敏感词到 DFA 树
     */
    private void insert(String word) {
        DfaNode node = root;
        for (char c : word.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new DfaNode());
        }
        node.isEnd = true;
    }

    /**
     * 检测文本是否包含敏感词
     *
     * @param text 待检测文本
     * @return true 表示包含敏感词
     */
    public boolean containsSensitive(String text) {
        return !findMatches(text).isEmpty();
    }

    /**
     * 查找文本中命中的所有敏感词
     *
     * @param text 待检测文本
     * @return 命中的敏感词列表（去重）
     */
    public Set<String> findMatches(String text) {
        Set<String> matched = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return matched;
        }

        ensureLoaded();

        int len = text.length();
        for (int i = 0; i < len; i++) {
            DfaNode node = root;
            int j = i;
            while (j < len) {
                char c = text.charAt(j);
                DfaNode child = node.children.get(c);
                if (child == null) {
                    break;
                }
                node = child;
                j++;
                if (node.isEnd) {
                    matched.add(text.substring(i, j));
                    break; // 最短匹配，继续扫描下一个起始位置
                }
            }
        }

        return matched;
    }

    /**
     * 替换文本中的敏感词为 ***
     *
     * @param text 待处理文本
     * @return 替换后的文本
     */
    public String replace(String text) {
        return replace(text, DEFAULT_REPLACEMENT);
    }

    /**
     * 替换文本中的敏感词为指定字符串
     *
     * @param text        待处理文本
     * @param replacement 替换为的字符串
     * @return 替换后的文本
     */
    public String replace(String text, String replacement) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        ensureLoaded();

        StringBuilder result = new StringBuilder();
        int len = text.length();
        int i = 0;

        while (i < len) {
            DfaNode node = root;
            int j = i;
            int matchedLen = 0;

            while (j < len) {
                char c = text.charAt(j);
                DfaNode child = node.children.get(c);
                if (child == null) {
                    break;
                }
                node = child;
                j++;
                if (node.isEnd) {
                    matchedLen = j - i; // 取最长匹配
                }
            }

            if (matchedLen > 0) {
                result.append(replacement);
                i += matchedLen;
            } else {
                result.append(text.charAt(i));
                i++;
            }
        }

        return result.toString();
    }

    /**
     * 重新加载词库
     */
    public void reload() {
        loaded = false;
        load();
    }

    /**
     * 获取当前词库大小
     */
    public int getWordCount() {
        return wordCount;
    }

    private void ensureLoaded() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    load();
                }
            }
        }
    }
}
