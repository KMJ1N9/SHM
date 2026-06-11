/**
 * 商品数据访问层
 *
 * 封装所有 products 表 + product_images 表的 SQL 操作。
 */

const { query, pool, transaction } = require('../models/db');

const PRODUCT_FIELDS = [
  'id', 'seller_id', 'title', 'description', 'category', '`condition`',
  'original_price', 'price', 'trade_location', 'negotiable', 'images',
  'status', 'created_at', 'updated_at',
].join(', ');

const LIST_FIELDS = [
  'p.id', 'p.seller_id', 'p.title', 'p.category', 'p.`condition`',
  'p.price', 'p.original_price', 'p.trade_location', 'p.negotiable',
  'p.images', 'p.status', 'p.created_at',
  'u.nickname AS seller_nickname',
  'u.avatar AS seller_avatar', 'u.credit_score AS seller_credit_score',
].join(', ');

const productRepo = {
  /**
   * 商品列表（首页瀑布流 + 搜索 + 筛选 + 排序 + 分页）
   * @param {Object} filters
   * @returns {Promise<{list: Array, total: number, page: number, pageSize: number}>}
   */
  async list(filters) {
    const {
      keyword, category, condition, priceMin, priceMax,
      sort = 'latest',
    } = filters;
    // query string 参数均为字符串，LIMIT/OFFSET 需要整数
    const page = Math.max(1, parseInt(filters.page, 10) || 1);
    const pageSize = Math.min(50, Math.max(1, parseInt(filters.pageSize, 10) || 20));


    const conditions = ['p.status = ?'];
    const params = ['active'];

    // 关键词搜索（FULLTEXT 优先，LIKE 兜底）
    if (keyword) {
      conditions.push(
        '(MATCH(p.title, p.description) AGAINST(? IN BOOLEAN MODE) OR p.title LIKE ? OR u.nickname LIKE ? OR p.category LIKE ?)'
      );
      params.push(keyword, `%${keyword}%`, `%${keyword}%`, `%${keyword}%`);
    }
    if (category) {
      conditions.push('p.category = ?');
      params.push(category);
    }
    if (condition) {
      conditions.push('p.`condition` = ?');
      params.push(condition);
    }
    if (priceMin !== undefined) {
      conditions.push('p.price >= ?');
      params.push(priceMin);
    }
    if (priceMax !== undefined) {
      conditions.push('p.price <= ?');
      params.push(priceMax);
    }

    const where = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';

    // 排序
    let orderBy;
    switch (sort) {
      case 'priceAsc':  orderBy = 'ORDER BY p.price ASC'; break;
      case 'priceDesc': orderBy = 'ORDER BY p.price DESC'; break;
      default:          orderBy = 'ORDER BY p.created_at DESC'; break;
    }

    // 计数（需要 JOIN users 因为 WHERE 可能引用 u.nickname）
    const [countResult] = await query(
      `SELECT COUNT(*) AS total FROM products p JOIN users u ON p.seller_id = u.id ${where}`,
      params
    );

    // 列表查询（JOIN 卖家信息）
    const offset = (page - 1) * pageSize;
    const rows = await query(
      `SELECT ${LIST_FIELDS}
       FROM products p
       JOIN users u ON p.seller_id = u.id
       ${where} ${orderBy}
       LIMIT ? OFFSET ?`,
      [...params, pageSize, offset]
    );

    return {
      list: rows,
      total: countResult.total,
      page,
      pageSize: pageSize,
    };
  },

  /**
   * 游标分页商品列表（首页无限滚动场景）
   *
   * 使用 WHERE id < cursor ORDER BY id DESC 替代 LIMIT OFFSET，
   * 避免大数据量下 OFFSET 的性能退化（O(n) 扫描跳过行）。
   * COUNT 查询不含 cursor 条件（总数应统计全部符合条件的行）。
   *
   * @param {Object} filters - { cursor?, limit?, keyword?, category?, condition?, priceMin?, priceMax?, sort? }
   * @returns {Promise<{list: Array, total: number, cursor: number|null, hasMore: boolean}>}
   */
  async listByCursor(filters) {
    const {
      keyword, category, condition, priceMin, priceMax, sort = 'latest',
    } = filters;
    const limit = Math.min(50, Math.max(1, parseInt(filters.limit, 10) || 20));
    const rawCursor = parseInt(filters.cursor, 10);
    const cursor = Number.isNaN(rawCursor) ? null : rawCursor;

    const whereConditions = ['p.status = ?'];
    const whereParams = ['active'];

    // 游标条件（仅列表查询追加，COUNT 不参与）
    let cursorClause = '';
    const cursorParams = [];
    if (cursor) {
      cursorClause = 'AND p.id < ?';
      cursorParams.push(cursor);
    }

    // 关键词搜索（FULLTEXT 优先，LIKE 兜底）
    if (keyword) {
      whereConditions.push(
        '(MATCH(p.title, p.description) AGAINST(? IN BOOLEAN MODE) OR p.title LIKE ? OR u.nickname LIKE ? OR p.category LIKE ?)'
      );
      whereParams.push(keyword, `%${keyword}%`, `%${keyword}%`, `%${keyword}%`);
    }
    if (category) {
      whereConditions.push('p.category = ?');
      whereParams.push(category);
    }
    if (condition) {
      whereConditions.push('p.`condition` = ?');
      whereParams.push(condition);
    }
    if (priceMin !== undefined) {
      whereConditions.push('p.price >= ?');
      whereParams.push(priceMin);
    }
    if (priceMax !== undefined) {
      whereConditions.push('p.price <= ?');
      whereParams.push(priceMax);
    }

    const where = `WHERE ${whereConditions.join(' AND ')}`;

    // 排序（游标基于 id DESC，价格排序另行处理）
    let orderBy;
    switch (sort) {
      case 'priceAsc':  orderBy = 'ORDER BY p.price ASC, p.id DESC'; break;
      case 'priceDesc': orderBy = 'ORDER BY p.price DESC, p.id DESC'; break;
      default:          orderBy = 'ORDER BY p.id DESC'; break;
    }

    // 列表查询（含游标条件，多取 1 条判断 hasMore）
    const rows = await query(
      `SELECT ${LIST_FIELDS}
       FROM products p
       JOIN users u ON p.seller_id = u.id
       ${where} ${cursorClause}
       ${orderBy}
       LIMIT ?`,
      [...whereParams, ...cursorParams, limit + 1]
    );

    const hasMore = rows.length > limit;
    if (hasMore) rows.pop();

    // COUNT 查询（不含游标条件，统计全部符合条件的行）
    const [countRow] = await query(
      `SELECT COUNT(*) AS total FROM products p JOIN users u ON p.seller_id = u.id ${where}`,
      whereParams
    );

    return {
      list: rows,
      total: countRow.total,
      cursor: rows.length > 0 ? rows[rows.length - 1].id : null,
      hasMore,
    };
  },

  /**
   * 商品详情（含卖家公开信息）
   * @param {number} id
   * @param {import('mysql2/promise').PoolConnection} [conn] - 事务连接（传入时自动加 FOR UPDATE 行锁）
   * @returns {Promise<Object|null>}
   */
  async findById(id, conn) {
    const db = conn || pool;
    // 事务内查询加行锁，防止 TOCTOU 竞态
    const forUpdate = conn ? ' FOR UPDATE' : '';
    const [rows] = await db.query(
      `SELECT p.id, p.seller_id, p.title, p.description, p.category, p.\`condition\`,
              p.original_price, p.price, p.trade_location, p.negotiable, p.images,
              p.status, p.created_at, p.updated_at,
              u.nickname AS seller_nickname, u.avatar AS seller_avatar,
              u.class_name AS seller_class_name, u.dorm_building AS seller_dorm_building,
              u.credit_score AS seller_credit_score
       FROM products p
       JOIN users u ON p.seller_id = u.id
       WHERE p.id = ?${forUpdate}`,
      [id]
    );
    // db.query() returns [rows, fields]; extract first row (or null if not found)
    return rows[0] || null;
  },

  /**
   * 创建商品
   * @param {Object} data
   * @returns {Promise<Object>}
   */
  async create(data) {
    const result = await query(
      `INSERT INTO products
       (seller_id, title, description, category, \`condition\`, original_price, price, trade_location, negotiable, images)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        data.seller_id, data.title, data.description || null,
        data.category, data.condition, data.original_price,
        data.price, data.trade_location, data.negotiable ? 1 : 0,
        JSON.stringify(data.images || []),
      ]
    );
    return this.findById(result.insertId);
  },

  /**
   * 更新商品
   * @param {number} id
   * @param {Object} updates
   * @returns {Promise<Object>}
   */
  async update(id, updates) {
    const allowedFields = [
      'title', 'description', 'category', 'condition',
      'original_price', 'price', 'trade_location', 'negotiable', 'images',
    ];
    const fields = [];
    const params = [];

    for (const key of allowedFields) {
      if (updates[key] !== undefined) {
        fields.push(key === 'images' ? `${key} = ?` : `\`${key}\` = ?`);
        params.push(key === 'images' ? JSON.stringify(updates[key]) : updates[key]);
      }
    }

    if (fields.length === 0) return this.findById(id);

    params.push(id);
    await query(
      `UPDATE products SET ${fields.join(', ')} WHERE id = ?`,
      params
    );
    return this.findById(id);
  },

  /**
   * 更新商品状态
   * @param {number} id
   * @param {string} status
   * @param {import('mysql2/promise').PoolConnection} [conn] - 事务连接
   * @returns {Promise<Object>}
   */
  async updateStatus(id, status, conn) {
    const db = conn || pool;
    await db.query(
      'UPDATE products SET status = ? WHERE id = ?',
      [status, id]
    );
    return this.findById(id, conn);
  },

  /**
   * 管理端商品列表（不过滤 status，管理员可见全部状态）。
   * @param {Object} filters
   * @param {string} [filters.status]   - 状态筛选（可选，不传=全部）
   * @param {string} [filters.keyword]  - 搜索关键词（标题/卖家昵称）
   * @param {string} [filters.sort]     - 排序
   * @param {number} [filters.page]     - 页码
   * @param {number} [filters.pageSize] - 每页条数
   * @returns {Promise<{list: Array, total: number, page: number, pageSize: number}>}
   */
  async listAll(filters) {
    const { keyword, status, sort = 'latest' } = filters;
    const page = Math.max(1, parseInt(filters.page, 10) || 1);
    const pageSize = Math.min(50, Math.max(1, parseInt(filters.pageSize, 10) || 20));

    const conditions = [];
    const params = [];

    // 可选状态筛选
    if (status) {
      conditions.push('p.status = ?');
      params.push(status);
    }

    // 关键词搜索
    if (keyword) {
      conditions.push(
        '(MATCH(p.title, p.description) AGAINST(? IN BOOLEAN MODE) OR p.title LIKE ? OR u.nickname LIKE ?)'
      );
      params.push(keyword, `%${keyword}%`, `%${keyword}%`);
    }

    const where = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';

    let orderBy;
    switch (sort) {
      case 'priceAsc':  orderBy = 'ORDER BY p.price ASC'; break;
      case 'priceDesc': orderBy = 'ORDER BY p.price DESC'; break;
      default:          orderBy = 'ORDER BY p.created_at DESC'; break;
    }

    const [countResult] = await query(
      `SELECT COUNT(*) AS total FROM products p JOIN users u ON p.seller_id = u.id ${where}`,
      params
    );

    const offset = (page - 1) * pageSize;
    const rows = await query(
      `SELECT ${LIST_FIELDS}
       FROM products p
       JOIN users u ON p.seller_id = u.id
       ${where} ${orderBy}
       LIMIT ? OFFSET ?`,
      [...params, pageSize, offset]
    );

    return {
      list: rows,
      total: countResult.total,
      page,
      pageSize,
    };
  },

  /**
   * 我发布的商品列表
   * @param {number} sellerId
   * @param {Object} filters - { status?, page, pageSize }
   * @returns {Promise<{list: Array, total: number}>}
   */
  async findBySeller(sellerId, filters = {}) {
    const { status = 'all', page: rawPage, pageSize: rawPageSize } = filters;
    // 强制整数解析（query string 参数均为 string 类型，LIMIT/OFFSET 需要整数）
    const page = Math.max(1, parseInt(rawPage, 10) || 1);
    const pageSize = Math.min(50, Math.max(1, parseInt(rawPageSize, 10) || 20));
    const conditions = ['seller_id = ?'];
    const params = [sellerId];

    if (status && status !== 'all') {
      conditions.push('status = ?');
      params.push(status);
    }

    const where = `WHERE ${conditions.join(' AND ')}`;

    const [countResult] = await query(
      `SELECT COUNT(*) AS total FROM products ${where}`,
      params
    );

    const offset = (page - 1) * pageSize;
    const rows = await query(
      `SELECT ${PRODUCT_FIELDS} FROM products ${where} ORDER BY created_at DESC LIMIT ? OFFSET ?`,
      [...params, pageSize, offset]
    );

    return { list: rows, total: countResult.total };
  },
};

module.exports = productRepo;
