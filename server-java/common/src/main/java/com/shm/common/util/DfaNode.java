package com.shm.common.util;

import java.util.HashMap;
import java.util.Map;

/**
 * DFA 节点：每个字符映射到下一个节点
 * <p>
 * isEnd = true 表示从根到当前节点的路径构成一个敏感词。
 * 与 Node.js {@code SensitiveFilter.DFANode} 结构完全一致。
 */
public class DfaNode {

    /** 子节点映射：字符 → 下一节点 */
    public final Map<Character, DfaNode> children = new HashMap<>();

    /** 是否为敏感词结尾 */
    public boolean isEnd = false;
}
