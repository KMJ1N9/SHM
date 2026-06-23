/**
 * HTTP 请求封装
 *
 * 基于 uni.request 的统一网络请求层，提供：
 *   - 自动附加 Bearer token（从 Storage 读取）
 *   - 统一响应拦截：code !== 0 走错误分支
 *   - Token 过期（1002）自动刷新后重试原请求
 *   - 封禁（1004）/ 版本不匹配（1003）强制跳转登录
 *   - 网络异常统一转可读错误
 *
 * 使用：
 *   import { get, post, put, del } from '@/api';
 *   const data = await post('/auth/login', { code: 'xxx' });
 */

// ── 后端切换：注释掉当前行，取消注释目标行即可 ──────────────
//
// Node.js 后端 (端口 3000)：
// const BASE_URL = 'http://localhost:3000/api';               // ← 模拟器
// const BASE_URL = 'http://10.115.248.247:3000/api';        // ← 真机 (热点)
// const BASE_URL = 'http://10.96.197.124:3000/api';          // ← 真机 (校园网)
//
// Java 后端 (Gateway 端口 8080)：
// const BASE_URL = 'http://localhost:8080/api';               // ← 模拟器
   const BASE_URL = 'http://10.115.248.247:8080/api';          // ← 真机 (热点)
// const BASE_URL = 'http://10.96.197.124:8080/api';           // ← 真机 (校园网)
// ────────────────────────────────────────────────────────────────

/** 图片服务器 origin，从 BASE_URL 派生 */
const IMAGE_ORIGIN = BASE_URL.replace(/\/api$/, '');

/**
 * 将数据库中的图片 URL 解析为当前环境可访问的 URL
 *
 * 问题背景：开发环境下上传的图片 URL 写死为 http://localhost:3000/images/...，
 * 真机调试时 localhost 指向手机自身，导致图片加载失败。
 * 此函数将 localhost 替换为当前 BASE_URL 的 host，使真机和模拟器都可用。
 *
 * @param {string|null|undefined} url - 数据库中的图片 URL
 * @returns {string} 当前环境可访问的图片 URL（null/undefined 返回空字符串）
 */
function resolveImageUrl(url) {
  if (!url) return '';
  // 替换 hardcoded localhost:3000 → 当前服务器 origin（适配模拟器/真机/IP 变更）
  let resolved = url.replace(/^http:\/\/localhost:3000/, IMAGE_ORIGIN);
  // 处理图片上传时 BASE_URL 的 IP 与当前 BASE_URL 的 IP 不同的情况。
  // 开发环境下图片 URL 格式为 http://<ip>:3000/images/...，
  // 当开发者 PC 的局域网 IP 变更后，数据库中已存的旧 IP URL 需要替换为当前 IP。
  // COS 正式上传返回的是 https://<域名> URL，不会被此正则匹配。
  const ipMatch = resolved.match(/^http:\/\/(\d+\.\d+\.\d+\.\d+):3000/);
  if (ipMatch) {
    const matchedOrigin = ipMatch[0];
    if (matchedOrigin !== IMAGE_ORIGIN) {
      resolved = resolved.replace(matchedOrigin, IMAGE_ORIGIN);
    }
  }
  return resolved;
}

/** 刷新中标记——避免并发请求同时刷新 Token */
let isRefreshing = false;
/** 刷新等待队列——刷新完成后批量重试 */
let refreshQueue = [];

/**
 * 处理刷新完成后的队列
 * @param {Error|null} error
 */
function flushRefreshQueue(error) {
  refreshQueue.forEach(({ resolve, reject }) => {
    if (error) {
      reject(error);
    } else {
      resolve();
    }
  });
  refreshQueue = [];
}

/**
 * 从 Storage 读取 Token
 */
function getAccessToken() {
  try {
    return uni.getStorageSync('accessToken') || '';
  } catch {
    return '';
  }
}

function getRefreshToken() {
  try {
    return uni.getStorageSync('refreshToken') || '';
  } catch {
    return '';
  }
}

/**
 * 存储 Token 到 Storage
 */
function saveTokens(accessToken, refreshToken) {
  try {
    uni.setStorageSync('accessToken', accessToken);
    if (refreshToken) {
      uni.setStorageSync('refreshToken', refreshToken);
    }
  } catch {
    // Storage 写入失败不应中断业务流程（如存储已满）
    console.warn('[api] Token 存储失败');
  }
}

/**
 * 清除所有 Token 和用户信息
 */
function clearAuth() {
  try {
    uni.removeStorageSync('accessToken');
    uni.removeStorageSync('refreshToken');
    uni.removeStorageSync('userInfo');
  } catch {
    // 忽略
  }
}

/**
 * 强制跳转登录页面
 *
 * 如果当前已在登录页，跳过跳转——防止后台轮询持续触发 401 →
 * redirectToLogin → reLaunch → 登录页反复刷新的死循环。
 */
function redirectToLogin() {
  clearAuth();
  // 检查当前是否已在登录页，避免死循环刷新
  const pages = getCurrentPages();
  const currentPage = pages[pages.length - 1];
  if (currentPage && currentPage.route === 'pages/auth/login') {
    return;
  }
  // 使用 reLaunch 清空页面栈，防止返回到需要鉴权的页面
  uni.reLaunch({ url: '/pages/auth/login' });
}

/**
 * 调用刷新接口获取新 Token
 * @returns {Promise<{accessToken: string, refreshToken: string}>}
 */
async function callRefreshAPI() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new Error('无刷新令牌');
  }

  const [err, res] = await uniRequest({
    url: `${BASE_URL}/auth/refresh`,
    method: 'POST',
    data: { refresh_token: refreshToken },
  });

  if (err) {
    throw err;
  }

  const body = res.data;
  if (body.code !== 0) {
    // 刷新令牌也过期了——需重新登录
    if (body.code === 1002 || body.code === 1003) {
      redirectToLogin();
    }
    throw new Error(body.message || '刷新失败');
  }

  const { accessToken, refreshToken: newRefreshToken } = body.data;
  saveTokens(accessToken, newRefreshToken);
  return { accessToken, refreshToken: newRefreshToken };
}

/**
 * 包装 uni.request 为 Promise 风格
 * @param {Object} options
 * @returns {Promise<[Error|null, Object]>}
 */
function uniRequest(options) {
  return new Promise((resolve) => {
    uni.request({
      ...options,
      success: (res) => resolve([null, res]),
      fail: (err) => resolve([err, null]),
    });
  });
}

/**
 * 核心请求函数
 *
 * @param {Object} options
 * @param {string} options.url - 请求路径（相对于 BASE_URL，如 '/auth/login'）
 * @param {string} options.method - HTTP 方法
 * @param {Object} [options.data] - 请求体（POST/PUT）
 * @param {Object} [options.params] - URL 查询参数（GET）
 * @param {boolean} [options.skipAuth] - 跳过 Token 附加（用于登录等无需鉴权的接口）
 * @param {number} [options.retryCount] - 内部用：当前重试次数
 * @returns {Promise<*>} 直接返回 response.data.data
 */
async function request(options) {
  const {
    url,
    method = 'GET',
    data,
    params,
    skipAuth = false,
  } = options;

  // 构建请求头
  const headers = { 'Content-Type': 'application/json' };
  if (!skipAuth) {
    const token = getAccessToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  }

  // 拼接查询参数
  let fullUrl = `${BASE_URL}${url}`;
  if (params) {
    const query = Object.entries(params)
      .filter(([, v]) => v !== undefined && v !== null)
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
      .join('&');
    if (query) {
      fullUrl += `?${query}`;
    }
  }

  const [networkErr, res] = await uniRequest({
    url: fullUrl,
    method,
    data,
    header: headers,
  });

  // 网络层错误（无网络、DNS 解析失败等）
  if (networkErr) {
    throw new Error('网络连接失败，请检查网络后重试');
  }

  const body = res.data;

  // 业务成功
  if (body.code === 0) {
    return body.data;
  }

  // 未登录（无 Token 或 Token 格式错误）
  if (body.code === 1001) {
    redirectToLogin();
    throw new Error('请先登录');
  }

  // Token 过期 → 尝试无感刷新
  if (body.code === 1002 && !skipAuth) {
    if (isRefreshing) {
      // 已有刷新在进行中，排队等待
      await new Promise((resolve, reject) => {
        refreshQueue.push({ resolve, reject });
      });
      // 刷新完成后重试原请求
      return request({ ...options, retryCount: (options.retryCount || 0) + 1 });
    }

    isRefreshing = true;
    try {
      await callRefreshAPI();
      flushRefreshQueue(null);
    } catch (refreshErr) {
      flushRefreshQueue(refreshErr);
      redirectToLogin();
      throw new Error('登录已过期，请重新登录');
    } finally {
      isRefreshing = false;
    }

    // 刷新成功，重试原请求（最多重试 1 次防死循环）
    const retryCount = (options.retryCount || 0) + 1;
    if (retryCount > 1) {
      redirectToLogin();
      throw new Error('登录已过期，请重新登录');
    }
    return request({ ...options, retryCount });
  }

  // Token 版本不匹配（被踢下线）
  if (body.code === 1003) {
    redirectToLogin();
    throw new Error(body.message || '账号已在其他设备登录');
  }

  // 账号已封禁
  if (body.code === 1004) {
    clearAuth();
    uni.reLaunch({ url: '/pages/auth/login' });
    throw new Error(body.message || '账号已被限制使用');
  }

  // 其他业务错误（参数校验失败、业务冲突等）
  const error = new Error(body.message || '请求失败，请稍后重试');
  error.code = body.code;
  throw error;
}

/**
 * GET 请求
 */
export function get(url, params) {
  return request({ url, method: 'GET', params });
}

/**
 * POST 请求
 */
export function post(url, data, skipAuth = false) {
  return request({ url, method: 'POST', data, skipAuth });
}

/**
 * PUT 请求
 */
export function put(url, data) {
  return request({ url, method: 'PUT', data });
}

/**
 * DELETE 请求
 */
export function del(url) {
  return request({ url, method: 'DELETE' });
}

export { BASE_URL, saveTokens, clearAuth, redirectToLogin, resolveImageUrl };
