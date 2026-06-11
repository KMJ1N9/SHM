/**
 * DFA 敏感词过滤器
 *
 * 使用确定有限状态自动机（DFA）算法：
 * - 时间复杂度 O(n)，n 为待检测文本长度
 * - 空间复杂度 O(m * k)，m 为词库大小，k 为平均词长
 * - 本地词库，无网络依赖
 *
 * 使用方式：
 *   const filter = require('./utils/sensitive-filter');
 *   const result = filter.check('待检测文本');
 *   if (result.hasSensitive) { throw sensitiveWord(); }
 */

const path = require('path');
const fs = require('fs');
const { business: businessLogger } = require('./logger');

// 默认词库路径
const DICT_PATH = path.resolve(__dirname, '../../data/sensitive-words.txt');

/**
 * DFA 节点：每个字符映射到下一个节点
 * isEnd = true 表示从根到当前节点的路径构成一个敏感词
 */
class DFANode {
  constructor() {
    this.children = new Map();
    this.isEnd = false;
  }
}

class SensitiveFilter {
  constructor() {
    this.root = new DFANode();
    this.wordCount = 0;
    this._loaded = false;
  }

  /**
   * 加载词库（启动时自动调用）
   * @param {string} [dictPath] - 词库路径
   */
  load(dictPath) {
    const filePath = dictPath || DICT_PATH;
    try {
      if (!fs.existsSync(filePath)) {
        console.warn(`[SENSITIVE] 词库文件不存在: ${filePath}，使用空词库。`);
        this._loaded = true;
        return;
      }

      const content = fs.readFileSync(filePath, 'utf-8');
      const words = content
        .split(/[\r\n]+/)
        .map(w => w.trim())
        .filter(w => w.length > 0 && !w.startsWith('#')); // 支持 # 注释

      // 重建 DFA 树
      this.root = new DFANode();
      for (const word of words) {
        this._insert(word);
      }

      this.wordCount = words.length;
      this._loaded = true;
      businessLogger.info('[SENSITIVE] 词库加载完成', { count: words.length });
    } catch (err) {
      console.error(`[SENSITIVE] 词库加载失败: ${err.message}`);
      this._loaded = true;
    }
  }

  /**
   * 插入单个敏感词到 DFA 树
   * @param {string} word
   */
  _insert(word) {
    let node = this.root;
    for (const char of word) {
      if (!node.children.has(char)) {
        node.children.set(char, new DFANode());
      }
      node = node.children.get(char);
    }
    node.isEnd = true;
  }

  /**
   * 检测文本是否包含敏感词
   *
   * @param {string} text - 待检测文本
   * @returns {{hasSensitive: boolean, words: string[]}} 命中结果
   */
  check(text) {
    if (!text || typeof text !== 'string') {
      return { hasSensitive: false, words: [] };
    }

    if (!this._loaded) {
      this.load();
    }

    const matched = [];
    const len = text.length;

    for (let i = 0; i < len; i++) {
      let node = this.root;
      let j = i;

      while (j < len && node.children.has(text[j])) {
        node = node.children.get(text[j]);
        j++;
        if (node.isEnd) {
          matched.push(text.substring(i, j));
          break; // 找到一个匹配即可（最短匹配），继续扫描下一个起始位置
        }
      }
    }

    return {
      hasSensitive: matched.length > 0,
      words: [...new Set(matched)], // 去重
    };
  }

  /**
   * 替换敏感词为 ***
   * @param {string} text
   * @param {string} [replacement='***']
   * @returns {string}
   */
  replace(text, replacement = '***') {
    if (!text || typeof text !== 'string') return text;

    if (!this._loaded) {
      this.load();
    }

    let result = '';
    const len = text.length;
    let i = 0;

    while (i < len) {
      let node = this.root;
      let j = i;
      let matchedLen = 0;

      while (j < len && node.children.has(text[j])) {
        node = node.children.get(text[j]);
        j++;
        if (node.isEnd) {
          matchedLen = j - i;
        }
      }

      if (matchedLen > 0) {
        result += replacement;
        i += matchedLen;
      } else {
        result += text[i];
        i++;
      }
    }

    return result;
  }

  /**
   * 重新加载词库
   */
  reload() {
    this._loaded = false;
    this.load();
  }

  /**
   * 获取当前词库大小
   * @returns {number}
   */
  getWordCount() {
    return this.wordCount;
  }
}

// 单例导出，启动时自动加载
const filter = new SensitiveFilter();

module.exports = filter;
