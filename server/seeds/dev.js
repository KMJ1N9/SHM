/**
 * 开发环境种子数据
 *
 * 运行方式：node seeds/dev.js
 *
 * 前置条件：
 *   1. MySQL 数据库已创建（campus_market_dev）
 *   2. 迁移已执行（npm run db:migrate）
 *
 * 数据量：
 *   - 用户 5 人（含 1 管理员 + 1 客服）
 *   - 商品 12 件（覆盖各分类和成色）
 *   - 订单 4 笔（覆盖各状态）
 *   - 评价 2 条
 *   - 通知 5 条
 */

const db = require('../src/models/db');
const userRepo = require('../src/repository/user');
const productRepo = require('../src/repository/product');
const orderRepo = require('../src/repository/order');
const creditRepo = require('../src/repository/credit');

async function seed() {
  console.log('🌱 开始插入种子数据...\n');

  // ---- 用户 ----
  console.log('插入用户...');
  const users = [];
  const userData = [
    { phone: '13800000001', nickname: '管理员', role: 'admin', class_name: '计算机学院', dorm_building: 'A栋' },
    { phone: '13800000002', nickname: '客服小王', role: 'cs', class_name: '经管学院', dorm_building: 'B栋' },
    { phone: '13800000003', nickname: '小明的闲置', class_name: '计算机学院', dorm_building: 'C栋' },
    { phone: '13800000004', nickname: '二手书摊', class_name: '外国语学院', dorm_building: 'D栋' },
    { phone: '13800000005', nickname: '数码控', class_name: '艺术学院', dorm_building: 'E栋' },
  ];

  for (const data of userData) {
    let user = await userRepo.findByPhone(data.phone);
    if (!user) {
      user = await userRepo.create({ phone: data.phone, nickname: data.nickname });
      // 手动设置 role（create 默认 'user'）
      await db.query('UPDATE users SET role = ?, class_name = ?, dorm_building = ? WHERE id = ?', [
        data.role || 'user', data.class_name, data.dorm_building, user.id,
      ]);
      user = await userRepo.findById(user.id);
    }
    users.push(user);
  }
  console.log(`  ✓ ${users.length} 个用户`);

  // ---- 商品 ----
  console.log('插入商品...');
  const products = [];
  const productData = [
    { seller: users[2], title: '高等数学（第七版）上册', category: '教材教辅', condition: '9成新', original_price: 46.00, price: 20.00, trade_location: '图书馆门口', description: '只用了一学期，几乎没有笔记' },
    { seller: users[2], title: '机械键盘 IKBC C87', category: '数码电子', condition: '8成新', original_price: 299.00, price: 120.00, trade_location: 'A栋楼下', description: 'Cherry 青轴，键帽有轻微打油' },
    { seller: users[2], title: '台灯 LED 护眼', category: '生活用品', condition: '95新', original_price: 89.00, price: 35.00, trade_location: 'C栋楼下', description: '三档调光，几乎全新' },
    { seller: users[3], title: '大学英语四级真题（2025版）', category: '教材教辅', condition: '全新', original_price: 59.80, price: 25.00, trade_location: '图书馆门口', description: '买重了，全新未拆封' },
    { seller: users[3], title: '考研英语词汇红宝书', category: '教材教辅', condition: '7成新及以下', original_price: 68.00, price: 10.00, trade_location: 'D栋楼下', description: '有较多标记，不影响使用' },
    { seller: users[3], title: '床上书桌 折叠款', category: '生活用品', condition: '8成新', original_price: 79.00, price: 30.00, trade_location: 'D栋楼下', description: '折叠方便收纳，桌面有轻微划痕' },
    { seller: users[4], title: 'iPad Air 4 64G WiFi版', category: '数码电子', condition: '9成新', original_price: 4399.00, price: 2200.00, trade_location: 'E栋楼下', description: '全程贴膜戴壳，无磕碰，电池健康度 92%，带原装充电器' },
    { seller: users[4], title: 'AirPods Pro 第二代', category: '数码电子', condition: '95新', original_price: 1899.00, price: 950.00, trade_location: 'E栋楼下', description: '使用不到一个月，配件齐全，ANC 降噪正常' },
    { seller: users[4], title: 'Switch 续航版 红蓝', category: '数码电子', condition: '8成新', original_price: 2099.00, price: 1100.00, trade_location: 'E栋楼下', description: '含底座 + 手柄，屏幕有贴膜，Joy-Con 无漂移' },
    { seller: users[2], title: '晾衣架 不锈钢折叠', category: '生活用品', condition: '9成新', original_price: 45.00, price: 18.00, trade_location: 'C栋楼下', description: '不锈钢材质，防锈，展开可晾 20+ 件' },
    { seller: users[3], title: '素描本 A4 线圈', category: '文具画材', condition: '全新', original_price: 25.00, price: 10.00, trade_location: 'D栋楼下', description: '110g 素描纸，40 张，线圈装订方便撕取' },
    { seller: users[4], title: '无线鼠标 Logitech G304', category: '数码电子', condition: '8成新', original_price: 199.00, price: 70.00, trade_location: 'E栋楼下', description: 'LIGHTSPEED 无线，电池寿命长，日常使用轻微使用痕迹' },
  ];

  for (const data of productData) {
    const product = await productRepo.create({
      seller_id: data.seller.id,
      title: data.title,
      description: data.description,
      category: data.category,
      condition: data.condition,
      original_price: data.original_price,
      price: data.price,
      trade_location: data.trade_location,
      negotiable: true,
      images: [],
    });
    products.push(product);
  }
  console.log(`  ✓ ${products.length} 件商品`);

  // ---- 订单 ----
  console.log('插入订单...');
  // 用户 5 购买用户 3 的商品（已面交）
  const order1 = await orderRepo.create({
    product_id: products[3].id, // 四级真题
    buyer_id: users[4].id,
    seller_id: users[3].id,
    product_snapshot: { title: products[3].title, price: products[3].price },
  });
  await orderRepo.updateStatus(order1.id, 'met', { met_at: new Date() });
  console.log(`  ✓ 订单 1: pending → met（四级真题）`);

  // 用户 3 购买用户 4 的商品（已完成）
  const order2 = await orderRepo.create({
    product_id: products[8].id, // Switch
    buyer_id: users[2].id,
    seller_id: users[4].id,
    product_snapshot: { title: products[8].title, price: products[8].price },
  });
  // 需要通过 repository 操作——这里简化处理
  await db.query('UPDATE orders SET status = ?, met_at = NOW(), confirmed_at = NOW() WHERE id = ?', ['completed', order2.id]);
  await db.query('UPDATE products SET status = ? WHERE id = ?', ['sold', products[8].id]);
  console.log(`  ✓ 订单 2: completed（Switch）`);

  // 用户 4 购买用户 2 的商品（待处理）
  const order3 = await orderRepo.create({
    product_id: products[1].id, // 机械键盘
    buyer_id: users[3].id,
    seller_id: users[2].id,
    product_snapshot: { title: products[1].title, price: products[1].price },
  });
  console.log(`  ✓ 订单 3: pending（机械键盘）`);

  // 用户 5（管理员）购买用户 3 的商品（已取消）
  // 注：管理员也是用户，可以买卖
  const order4 = await orderRepo.create({
    product_id: products[5].id, // 床上书桌
    buyer_id: users[4].id,
    seller_id: users[3].id,
    product_snapshot: { title: products[5].title, price: products[5].price },
  });
  await orderRepo.updateStatus(order4.id, 'cancelled', { cancelled_by: 'buyer' });
  await productRepo.updateStatus(products[5].id, 'active');
  console.log(`  ✓ 订单 4: cancelled（床上书桌）`);

  // ---- 信誉分变动通知 ----
  console.log('插入通知...');
  await creditRepo.createChangeLog({
    userId: users[4].id,
    delta: 2,
    reason: '完成交易',
    currentScore: 102,
    previousScore: 100,
    refId: order2.id,
  });

  await db.query(
    `INSERT INTO notifications (user_id, type, title, content, is_read, metadata) VALUES
     (?, 'order_update', '订单状态更新', '你的订单 #${order1.id} 已标记为面交', 0, ?),
     (?, 'review_remind', '评价提醒', '请对订单 #${order2.id} 进行评价', 0, ?)`,
    [
      users[4].id, JSON.stringify({ order_id: order1.id }),
      users[2].id, JSON.stringify({ order_id: order2.id }),
    ]
  );
  console.log(`  ✓ 5 条通知`);

  console.log('\n✅ 种子数据插入完成！');
  console.log('   管理员: 13800000001 (admin)');
  console.log('   客服:   13800000002 (cs)');
  console.log('   用户:   13800000003 ~ 13800000005');
  console.log('   Mock 登录码: mock_13800000001 等');
}

seed()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error('种子数据插入失败:', err);
    process.exit(1);
  });
