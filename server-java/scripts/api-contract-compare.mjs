/**
 * API 契约对比脚本（Phase 11.3）
 *
 * 对比 Node.js (3000) 和 Java Gateway (8080) 的 API 响应。
 * 每个后端独立登录获取各自的 JWT token。
 * 用法: node scripts/api-contract-compare.mjs
 */

const NODE_URL = process.env.NODE_URL || 'http://localhost:3000';
const JAVA_URL = process.env.JAVA_URL || 'http://localhost:8080';

let pass = 0, fail = 0, skip = 0;
const diffs = [];
let nodeToken = '';
let javaToken = '';

// ---- 工具函数 ----

async function request(url, method = 'GET', body = null, token = '') {
    const opts = {
        method,
        headers: { 'Content-Type': 'application/json' },
    };
    if (token) {
        opts.headers['Authorization'] = `Bearer ${token}`;
    }
    if (body && method !== 'GET') {
        opts.body = JSON.stringify(body);
    }
    try {
        const res = await fetch(url, opts);
        const text = await res.text();
        let json = null;
        try { json = JSON.parse(text); } catch {}
        return { status: res.status, body: json, raw: text };
    } catch (e) {
        return { status: 0, body: null, raw: '', error: e.message };
    }
}

function sortKeys(obj) {
    if (obj === null || obj === undefined) return obj;
    if (Array.isArray(obj)) return obj.map(sortKeys);
    if (typeof obj === 'object') {
        const sorted = {};
        Object.keys(obj).sort().forEach(k => {
            sorted[k] = sortKeys(obj[k]);
        });
        return sorted;
    }
    return obj;
}

function stripDynamic(obj) {
    if (obj === null || obj === undefined) return obj;
    if (Array.isArray(obj)) return obj.map(stripDynamic);
    if (typeof obj === 'object') {
        const cleaned = {};
        for (const [k, v] of Object.entries(obj)) {
            // 跳过动态字段
            if (['timestamp', 'token', 'accessToken', 'refreshToken',
                 'access_token', 'refresh_token', 'createdAt', 'updatedAt',
                 'created_at', 'updated_at', 'expires_in', 'expiresIn',
                 'traceId', 'id', 'exp', 'iat'].includes(k)) continue;
            cleaned[k] = stripDynamic(v);
        }
        return cleaned;
    }
    return obj;
}

function deepEqual(a, b, path = '', opts = {}) {
    if (a === b) return [];
    if (typeof a !== typeof b) {
        return [{ path: `${path}: type mismatch (${typeof a} vs ${typeof b})`, level: 'ERROR' }];
    }
    if (a === null || b === null) {
        if (a !== b) return [{ path: `${path}: ${JSON.stringify(a)} vs ${JSON.stringify(b)}`, level: 'ERROR' }];
        return [];
    }
    if (Array.isArray(a) && Array.isArray(b)) {
        const issues = [];
        const maxLen = Math.max(a.length, b.length);
        for (let i = 0; i < maxLen; i++) {
            if (i >= a.length) { issues.push({ path: `${path}[${i}]: missing in Node.js`, level: 'ERROR' }); continue; }
            if (i >= b.length) { issues.push({ path: `${path}[${i}]: missing in Java`, level: 'ERROR' }); continue; }
            issues.push(...deepEqual(a[i], b[i], `${path}[${i}]`, opts));
        }
        return issues;
    }
    if (typeof a === 'object' && typeof b === 'object') {
        const issues = [];
        const allKeys = new Set([...Object.keys(a), ...Object.keys(b)]);
        for (const k of allKeys) {
            if (!(k in a)) {
                // Java 多出的字段 → 前向兼容增强
                issues.push({ path: `${path}.${k}: Java enhancement (not in Node.js)`, level: 'ENHANCEMENT' });
                continue;
            }
            if (!(k in b)) {
                // Node.js 有但 Java 没有 → 破坏性缺失
                issues.push({ path: `${path}.${k}: missing in Java`, level: 'BREAKING' });
                continue;
            }
            issues.push(...deepEqual(a[k], b[k], `${path}.${k}`, opts));
        }
        return issues;
    }
    return [{ path: `${path}: ${JSON.stringify(a)} vs ${JSON.stringify(b)}`, level: 'ERROR' }];
}

async function compareApi(method, path, body = null, label = null) {
    if (!label) label = `${method} ${path}`;

    const [nodeRes, javaRes] = await Promise.all([
        request(`${NODE_URL}${path}`, method, body, nodeToken),
        request(`${JAVA_URL}${path}`, method, body, javaToken),
    ]);

    // 检查连接性
    if (nodeRes.status === 0) {
        console.log(`  [SKIP] ${label} — Node.js 不可达`);
        skip++;
        return;
    }
    if (javaRes.status === 0) {
        console.log(`  [SKIP] ${label} — Java 不可达 (${javaRes.error})`);
        skip++;
        return;
    }

    // 比较 HTTP 状态码
    if (nodeRes.status !== javaRes.status) {
        console.log(`  [FAIL] ${label} — HTTP: Node=${nodeRes.status} Java=${javaRes.status}`);
        console.log(`         Node body: ${JSON.stringify(nodeRes.body)?.substring(0, 200)}`);
        console.log(`         Java body: ${JSON.stringify(javaRes.body)?.substring(0, 200)}`);
        fail++;
        diffs.push({ label, type: 'status', node: nodeRes.status, java: javaRes.status,
                     nodeBody: nodeRes.body, javaBody: javaRes.body });
        return;
    }

    // 比较响应结构（跳过动态字段后深度比较）
    const nodeStripped = stripDynamic(sortKeys(nodeRes.body));
    const javaStripped = stripDynamic(sortKeys(javaRes.body));
    const issues = deepEqual(nodeStripped, javaStripped);

    const breaking = issues.filter(i => i.level === 'BREAKING' || i.level === 'ERROR');
    const enhancements = issues.filter(i => i.level === 'ENHANCEMENT');

    if (breaking.length === 0 && enhancements.length === 0) {
        console.log(`  [PASS] ${label} (HTTP ${nodeRes.status})`);
        pass++;
    } else if (breaking.length === 0 && enhancements.length > 0) {
        console.log(`  [PASS*] ${label} — ${enhancements.length} 项 Java 增强:`);
        enhancements.forEach(i => console.log(`          ${i.path}`));
        pass++;
    } else {
        console.log(`  [FAIL] ${label} — ${breaking.length} 处差异:`);
        breaking.forEach(i => console.log(`          ${i.path}`));
        if (enhancements.length > 0) {
            console.log(`         (+ ${enhancements.length} 项 Java 增强)`);
        }
        fail++;
        diffs.push({ label, type: 'body', issues, node: nodeRes.body, java: javaRes.body });
    }
}

// 提取 token：兼容 camelCase 和 snake_case
function extractToken(data) {
    return data?.accessToken || data?.access_token || data?.token || '';
}

// ---- 主流程 ----

async function main() {
    console.log('╔══════════════════════════════════════════════╗');
    console.log('║   API 契约对比报告 — Phase 11.3              ║');
    console.log(`║   Node.js: ${NODE_URL}                    ║`);
    console.log(`║   Java:    ${JAVA_URL}                    ║`);
    console.log('╚══════════════════════════════════════════════╝\n');

    // ====================================================================
    // 0. Health — 无需认证
    // ====================================================================
    console.log('── Health ──');
    await compareApi('GET', '/api/health');

    // ====================================================================
    // 1. Auth 模块 (11.3.2) — 各自登录获取独立 token
    // ====================================================================
    console.log('\n── Auth 模块 ──');
    const loginBody = { code: 'mock_test_code' };

    // 获取 Node.js token（使用与种子数据匹配的测试 code）
    const nodeLogin = await request(`${NODE_URL}/api/auth/login`, 'POST', loginBody);
    if (nodeLogin.status === 200) {
        nodeToken = extractToken(nodeLogin.body?.data);
        console.log(`  [INFO] Node.js 登录成功${nodeToken ? '，已获取 token' : '，但未找到 token 字段'}`);
    } else {
        console.log(`  [WARN] Node.js 登录失败 HTTP ${nodeLogin.status}: ${JSON.stringify(nodeLogin.body)?.substring(0, 200)}`);
    }

    // 获取 Java token
    const javaLogin = await request(`${JAVA_URL}/api/auth/login`, 'POST', loginBody);
    if (javaLogin.status === 200) {
        javaToken = extractToken(javaLogin.body?.data);
        console.log(`  [INFO] Java 登录成功${javaToken ? '，已获取 token' : '，但未找到 token 字段'}`);
    } else {
        console.log(`  [WARN] Java 登录失败 HTTP ${javaLogin.status}: ${JSON.stringify(javaLogin.body)?.substring(0, 200)}`);
    }

    // 对比登录响应（用无 token 的 request 以避免干扰）
    // 保存当前 token，登录对比不需要认证
    const savedNode = nodeToken, savedJava = javaToken;
    nodeToken = ''; javaToken = '';
    await compareApi('POST', '/api/auth/login', loginBody);
    nodeToken = savedNode; javaToken = savedJava;

    if (nodeToken && javaToken) {
        await compareApi('GET', '/api/auth/me');
        // refresh 需要发送 refresh_token（双方都接受 snake_case 输入）
        const nodeRefreshBody = { refresh_token: nodeLogin.body?.data?.refreshToken || '' };
        await compareApi('POST', '/api/auth/refresh', nodeRefreshBody);
    } else {
        console.log('  [SKIP] GET /api/auth/me (缺少 token)');
        console.log('  [SKIP] POST /api/auth/refresh (缺少 token)');
        skip += 2;
    }

    // ====================================================================
    // 2. Product 模块 (11.3.3)
    // ====================================================================
    console.log('\n── Product 模块 ──');
    await compareApi('GET', '/api/products?page=1&page_size=20');
    await compareApi('GET', '/api/products/my?page=1&page_size=20');

    // ====================================================================
    // 3. Order 模块 (11.3.4)
    // ====================================================================
    console.log('\n── Order 模块 ──');
    await compareApi('GET', '/api/orders?page=1&page_size=20');

    // ====================================================================
    // 4. Notification 模块
    // ====================================================================
    console.log('\n── Notification 模块 ──');
    await compareApi('GET', '/api/notifications?page=1&page_size=20');
    await compareApi('GET', '/api/notifications/unread-count');

    // ====================================================================
    // 5. Credit 模块 (11.3.5)
    // ====================================================================
    console.log('\n── Credit 模块 ──');
    await compareApi('GET', '/api/credit');

    // ====================================================================
    // 6. User 模块
    // ====================================================================
    console.log('\n── User 模块 ──');
    await compareApi('GET', '/api/users/cs/contact');

    // ====================================================================
    // 7. Admin 模块 (管理端, 11.3.6)
    // ====================================================================
    console.log('\n── Admin 模块 ──');
    await compareApi('GET', '/api/admin/analytics/overview');
    await compareApi('GET', '/api/admin/analytics/categories');
    await compareApi('GET', '/api/admin/sensitive/stats');

    // ====================================================================
    // 汇总
    // ====================================================================
    const total = pass + fail + skip;
    console.log('\n══════════════════════════════════════════════');
    console.log(`  结果: ${pass} PASS / ${fail} FAIL / ${skip} SKIP`);
    console.log(`  总计: ${total} API`);
    const pct = total > 0 ? Math.round(pass / (pass + fail) * 100) : 0;
    console.log(`  通过率: ${pct}%`);
    console.log('══════════════════════════════════════════════\n');

    if (diffs.length > 0) {
        console.log('差异详情:');
        diffs.forEach((d, i) => {
            console.log(`\n${i + 1}. ${d.label} [${d.type}]`);
            if (d.type === 'status') {
                console.log(`   Node.js: HTTP ${d.node} — ${JSON.stringify(d.nodeBody)?.substring(0, 200)}`);
                console.log(`   Java:    HTTP ${d.java} — ${JSON.stringify(d.javaBody)?.substring(0, 200)}`);
            } else if (d.type === 'body') {
                d.issues.forEach(issue => console.log(`   - ${issue}`));
            }
        });
    }

    process.exit(fail > 0 ? 1 : 0);
}

main().catch(e => {
    console.error('Fatal error:', e);
    process.exit(2);
});
