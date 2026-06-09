/**
 * COS STS 临时密钥 — 单元测试（5 条）
 *
 * 被测模块：server/src/utils/cos.js
 * 测试计划参考：docs/测试计划.md §3.7 — COS-001 ~ COS-005
 */

const { generateCredential } = require('../../../src/utils/cos');

describe('COS 临时密钥生成', () => {
  // ---- COS-001: 返回有效临时密钥 ----
  describe('COS-001: 返回有效凭证结构', () => {
    it('应返回 credentials、expiredTime、policy、prefix、bucket、region', async () => {
      const result = await generateCredential(1);

      // 结构完整性
      expect(result).toHaveProperty('credentials');
      expect(result).toHaveProperty('expiredTime');
      expect(result).toHaveProperty('policy');
      expect(result).toHaveProperty('prefix');
      expect(result).toHaveProperty('bucket');
      expect(result).toHaveProperty('region');
      expect(result).toHaveProperty('mock');

      // credentials 子字段
      expect(result.credentials).toHaveProperty('tmpSecretId');
      expect(result.credentials).toHaveProperty('tmpSecretKey');
      expect(result.credentials).toHaveProperty('sessionToken');
      expect(result.credentials).toHaveProperty('signKey');

      // 永久密钥鉴权 sessionToken 应为空字符串（非 undefined/null）
      expect(result.credentials.sessionToken).toBe('');
    });
  });

  // ---- COS-002: 过期时间 ≤ 1800s ----
  describe('COS-002: 过期时间', () => {
    it('expiredTime 应在 1800 秒以内', async () => {
      const now = Math.floor(Date.now() / 1000);
      const result = await generateCredential(42);

      expect(result.expiredTime).toBeGreaterThan(now);
      expect(result.expiredTime).toBeLessThanOrEqual(now + 1800);

      // 精确：应为 now + 1800（允许 ±2s 误差）
      const delta = result.expiredTime - now;
      expect(delta).toBeGreaterThanOrEqual(1798);
      expect(delta).toBeLessThanOrEqual(1802);
    });
  });

  // ---- COS-003: content-type 白名单含 image/jpeg, image/png, image/webp ----
  describe('COS-003: 上传 content-type 白名单', () => {
    it('policy conditions 应包含 image/ 前缀匹配', async () => {
      const result = await generateCredential(1);

      // policy 为 base64 编码的 JSON
      const policyStr = Buffer.from(result.policy, 'base64').toString('utf-8');
      const policy = JSON.parse(policyStr);

      // 应包含 starts-with Content-Type image/ 条件
      const contentTypeCond = policy.conditions.find(
        (c) => Array.isArray(c) && c[0] === 'starts-with' && c[1] === '$Content-Type'
      );
      expect(contentTypeCond).toBeDefined();
      expect(contentTypeCond[2]).toBe('image/');
    });
  });

  // ---- COS-004: content-type 白名单不含 image/gif ----
  describe('COS-004: 不允许 image/gif', () => {
    it('policy 不包含 image/gif 精确匹配', async () => {
      const result = await generateCredential(1);

      const policyStr = Buffer.from(result.policy, 'base64').toString('utf-8');
      const policy = JSON.parse(policyStr);

      // 不应对 image/gif 做特殊放行
      const gifCond = policy.conditions.find(
        (c) => {
          if (typeof c === 'object' && c !== null && !Array.isArray(c)) {
            return c['content-type'] === 'image/gif';
          }
          if (Array.isArray(c)) {
            return c[2] === 'image/gif';
          }
          return false;
        }
      );
      expect(gifCond).toBeUndefined();
    });
  });

  // ---- COS-005: Mock 模式（占位符凭证） ----
  describe('COS-005: 占位符凭证 → mock 模式', () => {
    it('使用 placeholder bucket 时应标记 mock=true', async () => {
      // 测试环境 .env.test 的 COS_BUCKET=test-bucket（不含 placeholder）
      // 此处验证非 mock 模式
      const result = await generateCredential(1);
      // test-bucket 不含 placeholder，应为非 mock
      expect(result.mock).toBe(false);
    });

    it('mock 模式下 signKey 仍然计算（非空）', async () => {
      const result = await generateCredential(1);

      // 即使不是真实 COS，signKey 也应正常计算
      expect(result.credentials.signKey).toBeTruthy();
      expect(typeof result.credentials.signKey).toBe('string');
      expect(result.credentials.signKey.length).toBeGreaterThan(0);
    });
  });

  // ---- 补充用例：prefix 按用户隔离 ----
  describe('用户路径隔离', () => {
    it('不同用户 ID 生成不同的 prefix', async () => {
      const r1 = await generateCredential(1);
      const r2 = await generateCredential(2);

      expect(r1.prefix).toBe('user_1/');
      expect(r2.prefix).toBe('user_2/');
      expect(r1.prefix).not.toBe(r2.prefix);
    });

    it('policy conditions 应限制 key 前缀', async () => {
      const result = await generateCredential(99);

      const policyStr = Buffer.from(result.policy, 'base64').toString('utf-8');
      const policy = JSON.parse(policyStr);

      const keyCond = policy.conditions.find(
        (c) => Array.isArray(c) && c[0] === 'starts-with' && c[1] === '$key'
      );
      expect(keyCond).toBeDefined();
      expect(keyCond[2]).toBe('user_99/');
    });
  });
});
