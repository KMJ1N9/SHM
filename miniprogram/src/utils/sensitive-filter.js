/**
 * 前端 DFA 敏感词过滤器
 *
 * 与 server/src/utils/sensitive-filter.js 算法完全一致：
 * - DFA 确定有限状态自动机，O(n) 时间复杂度
 * - 启动时从嵌入式词库构建前缀树，常驻内存
 * - 支持 replace() 将敏感词替换为 ***
 *
 * 使用：
 *   import { replaceSensitive } from '@/utils/sensitive-filter';
 *   const clean = replaceSensitive(rawText);
 */

// ── 嵌入式词库（与 server/data/sensitive-words.txt 同步） ──────────
// 格式：| 分隔，每行一个词，# 开头为注释（编译时会被 strip）
const RAW_DICT = [
  // 1. 欺诈/诈骗/灰产
  '诈骗|骗子|虚假|钓鱼|套现|刷单|代购|代付|洗钱|盗号|刷钻|刷qb|刷Q币|刷点券|刷车|刷皮肤',
  '代练|代挂|外挂|辅助|脚本|自动发货|秒杀器|抢购器|刷信誉|刷好评|刷销量|刷粉丝|刷流量',
  '薅羊毛|撸羊毛|反撸|互刷|拼团骗|转卖骗|虚假发货|空包|假快递|货到付款骗|到付骗',
  '押金骗|定金骗|预付款骗|截图骗|转账截图|假转账|伪造截图|PS记录|P图记录|虚假聊天',
  '伪造聊天|假支付|模拟支付|虚假链接|钓鱼链接|钓鱼网站|仿冒登录|假客服|冒充客服',
  '冒充官方|假冒平台|假冒咸鱼|假冒转转|冒充老师|冒充同学|冒充辅导员|冒充学长|冒充学姐',
  '校园贷|套路贷|裸贷|培训贷|刷单贷|分期骗|信用套现|花呗套现|借呗套现|白条套现',
  '校园代理骗|兼职骗|打字员骗|手工活骗|快递录入骗|刷单返利|先款骗|先付款|私下交易',
  '站外交易|微信转账|支付宝转账|银行卡转账|直接转账|不走平台|绕开平台|脱离平台',

  // 2. 色情/低俗
  '裸聊|援交|包夜|约炮|一夜情|性服务|卖淫|嫖娼|招嫖|小姐|包养|求包养|找金主|找干爹',
  '求干爹|找sugar|sugar daddy|援交妹|上门服务|特殊服务|全套|半套|快餐|楼凤|茶艺|品茶',
  '新茶|嫩模|外围|商务伴游|伴游|私拍|约拍|私房照|裸照|艳照|色情|黄色|涉黄|黄片|AV',
  '成人|激情|情色|色诱|诱惑视频|色情直播|直播裸露|大尺度|18禁|成人内容|成人用品',
  '情趣用品|避孕套|安全套|春药|催情|迷奸|迷晕|失身酒|听话水|GHB|迷幻药',

  // 3. 赌博/彩票
  '赌博|赌场|六合彩|时时彩|百家乐|老虎机|赌球|赌马|赌狗|网赌|线上赌场|真人赌场',
  '真人视讯|博彩|竞彩|北京赛车|PK10|快三|快乐彩|11选5|大乐透代买|私彩|黑彩',
  '外围博彩|滚球|赌盘|盘口|赔率|下注|投注|押注|坐庄|庄家|赌资|赌注|赌金|筹码',
  '洗码|返水|反水',

  // 4. 违禁品/危险品
  '枪支|弹药|毒品|大麻|冰毒|海洛因|摇头丸|管制刀具|迷药|窃听器|假币|假钞|伪钞',
  '假文凭|假毕业证|假学位证|办假证|代办学历|伪造证书|伪造证明|伪造印章|刻章',
  '发票代开|代开发票|电子烟|香烟|烟草|卷烟|走私烟|免税烟|违禁药品|处方药|安眠药',
  '精神类药物|兴奋剂|瘦肉精|作弊器|考试作弊|作弊耳机|隐形耳机|作弊笔|四六级答案',
  '四级答案|六级答案|考研答案|期末答案|代考|替考|枪手|代写论文|代写作业|论文代写',
  '作业代写|代课|代签到',

  // 5. 广告引流/骚扰
  '加微信|加我微信|扫码加|关注公众号|点击链接|下载APP|注册送|免费领取|加QQ|加我QQ',
  '加我好友|私聊我|扫码领|扫码送|免费送|免费领|不花钱|白给|白送|免费拿|关注我',
  '看我主页|进群|加群|拉群|微信群|QQ群|扫码进群|互粉|互关|互赞|刷访问量|刷播放量',
  '引流|导流|私域流量|公众号推广|朋友圈推广|点击广告|点击赚钱|看广告赚钱|转发赚钱',
  '分享赚钱|推广链接|邀请码|我的邀请码|注册邀请|拉新奖励|裂变|传销|直销',

  // 6. 侮辱/歧视/人身攻击
  '傻逼|SB|傻叉|煞笔|沙比|傻比|沙雕|啥比|傻毴|纱布|脑残|NC|弱智|白痴|BC|智障',
  '废物|辣鸡|垃圾人|人渣|败类|贱人|骚货|婊子|绿茶婊|心机婊|圣母婊',
  '戏精|杠精|喷子|键盘侠|地域黑|种族歧视|性别歧视|人身攻击|语言暴力',
  '网暴|网络暴力|校园霸凌|霸凌|欺凌',
  '狗日的|狗逼|狗比|狗东西|王八蛋|王八|混蛋|畜生|禽兽|不是人',
  '不要脸|无耻|下贱|下流|卑鄙|龌龊|神经病|变态|BT|恶心',
  '去死|滚蛋|滚开|滚远点|放屁|狗屁|狗屎|吃屎',
  '死全家|全家死光|不得好死|断子绝孙|龟儿子|龟孙|兔崽子',
  '穷逼|丑逼|土鳖|土包子|乡巴佬|村炮|菜逼|菜鸡|弱鸡|废物点心',
  '死妈|你妈死了|死爹|死胖子|肥猪|死肥猪',
  // 6a. 脏话拼音缩写
  'wcnm|WCNM|cnmb|CNMB|nmsl|NMSL|tmd|TMD|nmb|NMB|cnm|CNM|wc|ntm|NTM',
  'mlgb|MLGB|mmp|MMP|rnm|RNM|rnmb|RNMB|gnmb|GNMB|nnd|NND|jb|JB|zb|ZB|j8',
  // 6b. 中文脏话
  '操你妈|操尼玛|草拟吗|艹你妈|我操|我艹|我草|窝草|你妈逼|尼玛逼|尼玛|你玛',
  '他妈的|踏马的|特么的|他马的|妈的|妈的逼|日你妈|日你妈逼|草泥马|草拟马|操泥马',
  '艹|操你|去你妈|滚你妈|滚你妈逼|妈了个逼|妈卖批',
  '几把|几巴|鸡8|鸡吧|鸡巴|麻痹|马币|装逼|装B|装比',
  '去你大爷|你大爷的|你妹的|丫的|你丫的|骚逼|骚B|骚比|贱逼|贱B|浪逼',

  // 7. 校园违规/破坏秩序
  '代点名|代跑|代刷体育|代刷晨跑|代刷晚跑|代刷体测|代拿快递|代买饭|代取外卖',
  '偷电|偷水|偷网|蹭网|破解校园网|宿舍违禁电器|电热毯|电热水壶|电煮锅|电饭煲',
  '电磁炉|洗衣机转让违规|偷外卖|偷快递|偷自行车|偷电动车',

  // 8. 政治敏感
  '翻墙|VPN代理|VPN账号|梯子|科学上网|机场节点|SS节点|V2Ray|Clash|Trojan',
  '反党|反华|分裂|独立|颜色革命|六四|法轮功|邪教|传教|非法集会|示威|游行|罢工|暴动',

  // 9. 其他违规/垃圾信息
  '无意义|重复发帖|灌水|刷屏|广告帖|垃圾帖|虚假信息|不实信息|造谣|传谣|谣言',
  '误导信息|标题党|虚假宣传|夸大宣传|虚假标价|价格欺诈|以次充好|假货|仿品|高仿',
  '精仿|A货|山寨|冒牌|盗版|破解版|翻新|改装|拼装机|假配置|假参数|虚标容量',
  '虚标内存|虚标存储|扩容盘|扩容卡|假U盘|山寨手机|山寨耳机|山寨手表|山寨鞋',
  '莆田鞋|厂货|尾单|原单|跟单|追单|海关货|老鼠货|水货',
].join('|');

// ── DFA 节点 ──────────────────────────────────────────────────

class DfaNode {
  constructor() {
    /** @type {Map<string, DfaNode>} */
    this.children = new Map();
    this.isEnd = false;
  }
}

// ── 单例 ──────────────────────────────────────────────────────

let root = null;
let loaded = false;

/**
 * 从嵌入式词库构建 DFA 树
 */
function buildTree() {
  root = new DfaNode();
  const words = RAW_DICT.split('|').filter(w => w.length > 0);
  for (const word of words) {
    let node = root;
    for (const ch of word) {
      if (!node.children.has(ch)) {
        node.children.set(ch, new DfaNode());
      }
      node = node.children.get(ch);
    }
    node.isEnd = true;
  }
  loaded = true;
}

/**
 * 懒加载：首次调用时自动构建 DFA 树
 */
function ensureLoaded() {
  if (!loaded) {
    buildTree();
  }
}

/**
 * 替换文本中的敏感词为 ***
 *
 * @param {string} text - 待处理文本
 * @param {string} [replacement='***'] - 替换为的字符串
 * @returns {string} 替换后的文本
 */
export function replaceSensitive(text, replacement = '***') {
  if (!text || typeof text !== 'string') return text;

  ensureLoaded();

  let result = '';
  const len = text.length;
  let i = 0;

  while (i < len) {
    let node = root;
    let j = i;
    let matchedLen = 0;

    while (j < len) {
      const ch = text[j];
      if (!node.children.has(ch)) break;
      node = node.children.get(ch);
      j++;
      if (node.isEnd) {
        matchedLen = j - i; // 最长匹配
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
 * 检查文本是否包含敏感词
 *
 * @param {string} text - 待检测文本
 * @returns {boolean}
 */
export function hasSensitive(text) {
  if (!text || typeof text !== 'string') return false;

  ensureLoaded();

  const len = text.length;
  for (let i = 0; i < len; i++) {
    let node = root;
    let j = i;
    while (j < len) {
      const ch = text[j];
      if (!node.children.has(ch)) break;
      node = node.children.get(ch);
      j++;
      if (node.isEnd) return true;
    }
  }
  return false;
}
