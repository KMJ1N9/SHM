const http = require('http');

function request(hostname, port, path, method, body) {
  return new Promise((resolve) => {
    const options = {
      hostname, port, path,
      method: method || 'GET',
      headers: { 'Content-Type': 'application/json' },
      timeout: 10000
    };
    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve({ ok: true, status: res.statusCode, body: JSON.parse(data) });
        } catch(e) {
          resolve({ ok: true, status: res.statusCode, body: data.substring(0, 500) });
        }
      });
    });
    req.on('error', (e) => resolve({ ok: false, error: e.message }));
    req.on('timeout', () => { req.destroy(); resolve({ ok: false, error: 'timeout' }); });
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

async function main() {
  console.log('========================================');
  console.log('  校园二手交易 - Java 后端 API 验证');
  console.log('========================================\n');

  // ── 1. Health Check ──
  console.log('【1】服务健康检查\n');
  const healthChecks = [
    ['Gateway → core-service', 'localhost', 8080, '/api/health'],
    ['core-service (直接)', 'localhost', 8081, '/api/health'],
    ['admin-service (直接)', 'localhost', 8082, '/api/health'],
    ['im-connector (直接)', 'localhost', 8083, '/api/health'],
  ];
  for (const [name, host, port, path] of healthChecks) {
    const r = await request(host, port, path, 'GET');
    const icon = r.ok && r.status === 200 ? '✅' : '❌';
    console.log(`  ${icon} ${name}: ${r.status} ${JSON.stringify(r.body)}`);
  }

  // ── 2. Auth APIs ──
  console.log('\n【2】Auth API\n');

  // 2.1 测试登录（无真实微信 code，预期返回错误但能验证路由）
  const loginRes = await request('localhost', 8080, '/api/auth/login', 'POST', {
    code: 'test_code_123',
    nickName: 'TestUser',
    avatarUrl: 'https://example.com/avatar.png'
  });
  console.log(`  POST /api/auth/login → ${loginRes.status} ${JSON.stringify(loginRes.body)}`);

  // ── 3. Product APIs ──
  console.log('\n【3】Product API\n');

  const prodList = await request('localhost', 8080, '/api/products?page=1&pageSize=5', 'GET');
  console.log(`  GET /api/products → ${prodList.status} ${JSON.stringify(prodList.body).substring(0, 300)}`);

  const prodDetail = await request('localhost', 8080, '/api/products/1', 'GET');
  console.log(`  GET /api/products/1 → ${prodDetail.status} ${JSON.stringify(prodDetail.body).substring(0, 300)}`);

  // ── 4. User APIs ──
  console.log('\n【4】User API\n');

  const userList = await request('localhost', 8080, '/api/users?page=1&pageSize=3', 'GET');
  console.log(`  GET /api/users → ${userList.status} ${JSON.stringify(userList.body).substring(0, 300)}`);

  // ── 5. Order APIs ──
  console.log('\n【5】Order API\n');

  const orderList = await request('localhost', 8080, '/api/orders?page=1&pageSize=3', 'GET');
  console.log(`  GET /api/orders → ${orderList.status} ${JSON.stringify(orderList.body).substring(0, 300)}`);

  // ── 6. Review APIs ──
  console.log('\n【6】Review API\n');

  const reviews = await request('localhost', 8080, '/api/reviews?page=1&pageSize=3', 'GET');
  console.log(`  GET /api/reviews → ${reviews.status} ${JSON.stringify(reviews.body).substring(0, 300)}`);

  // ── 7. Report APIs ──
  console.log('\n【7】Report API\n');

  const reportsGet = await request('localhost', 8080, '/api/reports?page=1&pageSize=3', 'GET');
  console.log(`  GET /api/reports → ${reportsGet.status} ${JSON.stringify(reportsGet.body).substring(0, 300)}`);

  const reportsPost = await request('localhost', 8080, '/api/reports', 'POST', {
    reportedUserId: 2,
    type: '商品问题',
    description: '测试举报'
  });
  console.log(`  POST /api/reports → ${reportsPost.status} ${JSON.stringify(reportsPost.body).substring(0, 300)}`);

  // ── 8. Admin APIs ──
  console.log('\n【8】Admin API\n');

  const adminUsers = await request('localhost', 8080, '/api/admin/users?page=1&pageSize=3', 'GET');
  console.log(`  GET /api/admin/users → ${adminUsers.status} ${JSON.stringify(adminUsers.body).substring(0, 300)}`);

  const adminProducts = await request('localhost', 8080, '/api/admin/products?page=1&pageSize=3', 'GET');
  console.log(`  GET /api/admin/products → ${adminProducts.status} ${JSON.stringify(adminProducts.body).substring(0, 300)}`);

  // ── 9. Upload API ──
  console.log('\n【9】Upload API\n');

  const uploadRes = await request('localhost', 8080, '/api/upload/sts', 'POST', {});
  console.log(`  POST /api/upload/sts → ${uploadRes.status} ${JSON.stringify(uploadRes.body).substring(0, 300)}`);

  // ── Summary ──
  console.log('\n========================================');
  console.log('  验证完成');
  console.log('========================================');
}

main().catch(console.error);
