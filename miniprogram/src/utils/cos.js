/**
 * COS 直传工具
 *
 * 前端直传腾讯云 COS 的完整流程：
 *   1. 调用 getCredential() 获取 STS 临时凭证 + policy
 *   2. uni.chooseImage 选择图片（格式/大小客户端校验）
 *   3. uni.uploadFile 直传 COS（图片不经过服务器）
 *   4. 返回 COS 图片 URL 列表
 *
 * 安全约束（由后端 policy 控制）：
 *   - 按用户 ID 隔离上传路径（user_${userId}/）
 *   - 限制 content-type 白名单（image/jpeg, image/jpg, image/png, image/webp，含扩展名回退）
 *   - 限制单文件大小（默认 5MB）
 *   - 临时凭证 30 分钟过期
 */

import { getCredential } from '@/api/product';
import { BASE_URL } from '@/api/index';

/** 允许的 MIME 类型列表（含 image/jpg——微信 Android 端对 .jpg 返回的非标准类型） */
const ALLOWED_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
/** 允许的扩展名（用于 MIME 类型不可靠时的回退校验） */
const ALLOWED_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.webp'];

/**
 * 判断文件类型是否合法（先查 MIME 类型，再降级查扩展名——微信各平台返回的 type 不一致）
 * @param {Object} file - tempFile 对象（含 type 和 path 属性）
 * @returns {boolean}
 */
function isValidImageType(file) {
  if (file.type && ALLOWED_TYPES.includes(file.type)) return true;
  // MIME 类型不匹配时降级检查文件扩展名（如 Android 端 .jpg 可能返回 image/jpg 甚至空字符串）
  const fileName = file.path || file.name || '';
  const ext = fileName.split('.').pop()?.toLowerCase();
  if (ext && ALLOWED_EXTENSIONS.includes('.' + ext)) return true;
  return false;
}

/** 单张图片最大体积（5MB，与后端 COS policy 对齐） */
const MAX_SIZE = 5 * 1024 * 1024;

/** 一次性最多选择图片数（与后端 MAX_IMAGES 对齐） */
const MAX_COUNT = 6;

/**
 * 选择图片并直传 COS
 *
 * 流程：
 *   uni.chooseImage（或使用预选文件） → 获取临时凭证 → 逐个上传 COS → 返回 URL 数组
 *
 * @param {Array} [preselectedFiles] - 可选，已选好的 tempFiles 数组（来自 ImageUploader）
 *   传入时跳过 uni.chooseImage 选图步骤，直接上传，仍做格式/大小校验
 * @returns {Promise<string[]>} COS 图片 URL 列表
 */
export async function chooseAndUpload(preselectedFiles) {
  // 1. 选择图片（格式 + 大小客户端预检）
  let files;
  if (preselectedFiles && preselectedFiles.length > 0) {
    // 使用预选文件，但仍做格式和大小校验（与 chooseImages 一致）
    const invalid = preselectedFiles.filter((f) => !isValidImageType(f));
    if (invalid.length > 0) {
      uni.showToast({
        title: `已自动过滤 ${invalid.length} 张不支持的图片`,
        icon: 'none',
        duration: 2000,
      });
      return [];
    }
    const oversized = preselectedFiles.filter((f) => f.size > MAX_SIZE);
    if (oversized.length > 0) {
      uni.showToast({
        title: `单张图片不能超过 ${MAX_SIZE / 1024 / 1024}MB`,
        icon: 'none',
        duration: 2000,
      });
      return [];
    }
    files = preselectedFiles;
  } else {
    files = await chooseImages();
  }
  if (!files || files.length === 0) {
    return [];
  }

  // 2. 获取 STS 临时凭证
  let credential;
  try {
    credential = await getCredential();
  } catch (err) {
    throw new Error('获取上传凭证失败，请稍后重试');
  }

  // 3. 开发环境占位符凭证 → 上传到服务器而非 COS
  if (credential.mock) {
    const token = getAccessToken();
    const urls = [];
    for (const file of files) {
      try {
        const url = await uploadToServer(file, token);
        urls.push(url);
      } catch (err) {
        throw new Error(err.message || '图片上传失败，请重试');
      }
    }
    return urls;
  }

  // 4. 逐个上传
  const urls = [];
  for (const file of files) {
    try {
      const url = await uploadOne(file, credential);
      urls.push(url);
    } catch (err) {
      throw new Error(err.message || '图片上传失败，请重试');
    }
  }

  return urls;
}

/**
 * uni.chooseImage 包装——客户端格式/大小预检
 * @returns {Promise<Array>} 选中的图片文件信息数组
 */
function chooseImages() {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count: MAX_COUNT,
      sizeType: ['compressed'],      // 压缩图（微信自动压缩）
      sourceType: ['album', 'camera'],
      success: (res) => {
        // 格式校验
        const invalid = res.tempFiles.filter(
          (f) => !isValidImageType(f)
        );
        if (invalid.length > 0) {
          uni.showToast({
            title: `已自动过滤 ${invalid.length} 张不支持的图片`,
            icon: 'none',
            duration: 2000,
          });
          resolve([]);
          return;
        }

        // 大小校验
        const oversized = res.tempFiles.filter((f) => f.size > MAX_SIZE);
        if (oversized.length > 0) {
          uni.showToast({
            title: `单张图片不能超过 ${MAX_SIZE / 1024 / 1024}MB`,
            icon: 'none',
            duration: 2000,
          });
          resolve([]);
          return;
        }

        resolve(res.tempFiles);
      },
      fail: (err) => {
        // 用户取消选择不算错误
        if (err.errMsg && err.errMsg.includes('cancel')) {
          resolve([]);
        } else {
          reject(new Error('选择图片失败'));
        }
      },
    });
  });
}

/**
 * 从 Storage 读取 Access Token（供上传时附加鉴权头）
 * @returns {string}
 */
function getAccessToken() {
  try {
    return uni.getStorageSync('accessToken') || '';
  } catch {
    return '';
  }
}

/**
 * 开发环境回退：上传单张图片到 Express 服务器
 *
 * 仅在 COS 凭证为占位符时使用。图片存储于 server/public/images/，
 * 由 Express 静态文件中间件提供服务。
 *
 * @param {Object} file  - uni.chooseImage 返回的 tempFile
 * @param {string} token - Bearer token
 * @returns {Promise<string>} 可访问的图片 URL（相对路径，如 /images/user_6/xxx.jpg）
 */
function uploadToServer(file, token) {
  const uploadUrl = `${BASE_URL}/upload/image`;

  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: uploadUrl,
      filePath: file.path,
      name: 'file',
      header: {
        Authorization: `Bearer ${token}`,
      },
      success: (res) => {
        if (res.statusCode === 200 || res.statusCode === 201) {
          try {
            const body = JSON.parse(res.data);
            if (body.code === 0 && body.data && body.data.url) {
              // 服务端返回相对路径（如 /images/user_6/xxx.jpg），
              // 拼接为完整 HTTP URL（小程序 <image> 需要完整 URL）
              const origin = BASE_URL.replace(/\/api$/, '');
              resolve(origin + body.data.url);
            } else {
              reject(new Error(body.message || '上传失败'));
            }
          } catch {
            reject(new Error('服务器响应异常'));
          }
        } else {
          reject(new Error(`上传失败（${res.statusCode}）`));
        }
      },
      fail: (err) => {
        reject(new Error(err.errMsg || '网络异常，上传失败'));
      },
    });
  });
}

/**
 * 上传单张图片到 COS
 *
 * 使用 COS POST Object API，表单字段由后端 STS 服务生成。
 *
 * @param {Object} file       - uni.chooseImage 返回的 tempFile
 * @param {string} file.path  - 临时文件路径
 * @param {Object} credential - 后端 getCredential 响应
 * @returns {Promise<string>} COS 图片 URL
 */
function uploadOne(file, credential) {
  const {
    bucket,
    region,
    prefix,
    policy,
    cdnBaseUrl,
    credentials,
  } = credential;

  // 生成唯一文件名：时间戳 + 6 位随机
  const timestamp = Date.now();
  const random = Math.random().toString(36).substring(2, 8);
  const ext = getExtension(file.type);
  const key = `${prefix}${timestamp}_${random}.${ext}`;

  // COS POST Object 端点
  const uploadUrl = `https://${bucket}.cos.${region}.myqcloud.com/`;

  return new Promise((resolve, reject) => {
    // 构建 COS POST Object 必需的表单字段
    //
    // 字段必须与后端 policy conditions 完全匹配，否则 COS 返回 403：
    //   { bucket }              → 需要 bucket 字段（精确匹配）
    //   starts-with Content-Type → 需要 Content-Type 字段（以 image/ 开头）
    //   starts-with key         → 需要 key 字段（以 user_{id}/ 开头）
    //   content-length-range    → COS 服务端自动校验文件大小
    //
    // 参见：https://cloud.tencent.com/document/product/436/14690
    const formData = {
      key,
      bucket,
      policy,
      'Content-Type': normalizeContentType(file.type),
      success_action_status: '200',
      Signature: credentials.signKey,
    };

    // 仅真实 STS 临时凭证时附加 security token（永久密钥鉴权时为空，不传此字段）
    if (credentials.sessionToken) {
      formData['x-cos-security-token'] = credentials.sessionToken;
    }

    uni.uploadFile({
      url: uploadUrl,
      filePath: file.path,
      name: 'file',
      formData,
      success: (res) => {
        if (res.statusCode === 200 || res.statusCode === 204) {
          // 拼接最终访问 URL
          const baseUrl = cdnBaseUrl || `https://${bucket}.cos.${region}.myqcloud.com`;
          resolve(`${baseUrl}/${key}`);
        } else {
          // 增强错误诊断：记录 COS 返回的 XML 错误体
          console.error('[COS Upload] 失败 —', {
            statusCode: res.statusCode,
            bucket,
            key,
            bodyPreview: typeof res.data === 'string' ? res.data.substring(0, 300) : String(res.data || '').substring(0, 300),
          });
          reject(new Error(`上传失败（${res.statusCode}）`));
        }
      },
      fail: (err) => {
        console.error('[COS Upload] 网络异常 —', err.errMsg || err.message || err);
        reject(new Error(err.errMsg || '网络异常，上传失败'));
      },
    });
  });
}

/**
 * 规范化 Content-Type（处理微信非标准 MIME 类型）
 *
 * COS POST Object policy 的 starts-with Content-Type 条件要求表单
 * 包含 Content-Type 字段。微信 Android 端对 .jpg 文件可能返回
 * 非标准的 image/jpg（应为 image/jpeg），需要规范化为 COS 识别的值。
 *
 * @param {string} type - 文件 MIME 类型
 * @returns {string} 规范化后的 Content-Type
 */
function normalizeContentType(type) {
  if (type === 'image/jpg') return 'image/jpeg';
  return type || 'image/jpeg';
}

/**
 * 根据 MIME type 返回文件扩展名
 * @param {string} mimeType
 * @returns {string}
 */
function getExtension(mimeType) {
  const map = {
    'image/jpeg': 'jpg',
    'image/jpg': 'jpg',  // 微信 Android 端对 .jpg 返回的非标准 MIME 类型
    'image/png': 'png',   // 修复：原为 image\png（反斜杠拼写错误）
    'image/webp': 'webp',
  };
  return map[mimeType] || 'jpg';
}
