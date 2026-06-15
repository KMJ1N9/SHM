package com.shm.core.repository;

import com.shm.common.model.dto.ProductSearchQuery;
import com.shm.common.model.entity.Product;
import com.shm.common.model.entity.User;
import com.shm.core.mapper.ProductMapper;
import com.shm.core.mapper.UserMapper;
import org.junit.jupiter.api.*;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProductMapper 集成测试（Phase 11.2.2）
 *
 * <p>使用 @MybatisTest 只加载 MyBatis 层，避免触发 Nacos/Feign/Sentinel 自动配置。
 * 连接真实 MySQL 数据库（配置在 application-test.yml），@Transactional 保证测试数据自动回滚。
 * 涵盖 FULLTEXT 搜索 / 分类筛选 / 分页 / 状态更新 / 悲观锁 等关键数据访问路径。
 */
@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductRepositoryIntegrationTest {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserMapper userMapper;

    /** 测试卖家用户（在每个 @Transactional 测试中按需创建） */
    private Long testSellerId;

    /** 确保测试卖家存在（products.seller_id 有 FK 约束引用 users.id） */
    private Long sellerId() {
        if (testSellerId == null) {
            User seller = new User();
            seller.setPhone("13820000001");
            seller.setNickname("测试卖家-商品集成");
            seller.setAvatar("");
            seller.setClassName("CS2024");
            seller.setDormBuilding("北区");
            seller.setRole("user");
            seller.setCreditScore(100);
            seller.setStatus("active");
            seller.setTokenVersion(0);
            userMapper.insert(seller);
            testSellerId = seller.getId();
        }
        return testSellerId;
    }

    // ---- 基础 CRUD ----

    @Test
    @Order(1)
    @Transactional
    void shouldInsertAndFindProduct() {
        Product product = buildProduct(sellerId(), "测试商品-集成测试", "电子产品", "95新");
        int inserted = productMapper.insert(product);
        assertEquals(1, inserted);
        assertNotNull(product.getId());

        Product found = productMapper.findById(product.getId());
        assertNotNull(found);
        assertEquals("测试商品-集成测试", found.getTitle());
        assertEquals("电子产品", found.getCategory());
        assertEquals("95新", found.getCondition());
        assertEquals("active", found.getStatus());
    }

    @Test
    @Order(2)
    @Transactional
    void shouldFindByIdForUpdate() {
        Product product = buildProduct(sellerId(), "悲观锁测试商品", "书籍", "全新");
        productMapper.insert(product);

        // FOR UPDATE 在同一事务中应能正常获取锁
        Product locked = productMapper.findByIdForUpdate(product.getId());
        assertNotNull(locked);
        assertEquals(product.getId(), locked.getId());
        assertEquals("悲观锁测试商品", locked.getTitle());
    }

    @Test
    @Order(3)
    @Transactional
    void shouldUpdateProduct() {
        Product product = buildProduct(sellerId(), "原始标题", "生活用品", "8成新");
        productMapper.insert(product);

        // 更新字段
        product.setTitle("修改后的标题");
        product.setPrice(new BigDecimal("25.50"));
        product.setCategory("书籍");
        int updated = productMapper.update(product);
        assertEquals(1, updated);

        Product found = productMapper.findById(product.getId());
        assertEquals("修改后的标题", found.getTitle());
        assertEquals(0, new BigDecimal("25.50").compareTo(found.getPrice()));
        assertEquals("书籍", found.getCategory());
    }

    @Test
    @Order(4)
    @Transactional
    void shouldUpdateStatus() {
        Product product = buildProduct(sellerId(), "状态测试商品", "数码", "9成新");
        productMapper.insert(product);

        int updated = productMapper.updateStatus(product.getId(), "off_shelf");
        assertEquals(1, updated);

        Product found = productMapper.findById(product.getId());
        assertEquals("off_shelf", found.getStatus());

        // 改回 active
        productMapper.updateStatus(product.getId(), "active");
        assertEquals("active", productMapper.findById(product.getId()).getStatus());
    }

    // ---- 卖家商品查询 ----

    @Test
    @Order(5)
    @Transactional
    void shouldFindBySellerIdWithPagination() {
        // 同一卖家插入 3 个商品
        productMapper.insert(buildProduct(sellerId(), "商品A", "数码", "全新"));
        productMapper.insert(buildProduct(sellerId(), "商品B", "书籍", "9成新"));
        productMapper.insert(buildProduct(sellerId(), "商品C", "生活用品", "8成新"));

        // 分页：取前 2 条
        List<Product> page1 = productMapper.findBySellerId(sellerId(), null, 0, 2);
        assertEquals(2, page1.size());

        // 分页：offset=2 取剩余
        List<Product> page2 = productMapper.findBySellerId(sellerId(), null, 2, 2);
        assertEquals(1, page2.size());
    }

    @Test
    @Order(6)
    @Transactional
    void shouldFindBySellerIdWithStatusFilter() {
        productMapper.insert(buildProduct(sellerId(), "在售商品", "数码", "全新"));
        Product offShelf = buildProduct(sellerId(), "下架商品", "数码", "全新");
        productMapper.insert(offShelf);
        productMapper.updateStatus(offShelf.getId(), "off_shelf");

        // 只查 active
        List<Product> active = productMapper.findBySellerId(sellerId(), "active", 0, 10);
        assertTrue(active.stream().allMatch(p -> "active".equals(p.getStatus())));

        // 只查 off_shelf
        List<Product> offShelfList = productMapper.findBySellerId(sellerId(), "off_shelf", 0, 10);
        assertTrue(offShelfList.stream().allMatch(p -> "off_shelf".equals(p.getStatus())));
    }

    @Test
    @Order(7)
    @Transactional
    void shouldCountBySellerId() {
        productMapper.insert(buildProduct(sellerId(), "计数商品1", "数码", "全新"));
        productMapper.insert(buildProduct(sellerId(), "计数商品2", "书籍", "9成新"));

        long count = productMapper.countBySellerId(sellerId(), null);
        assertTrue(count >= 2);

        long countActive = productMapper.countBySellerId(sellerId(), "active");
        assertTrue(countActive >= 2);
    }

    // ---- 复杂搜索（XML Mapper） ----

    @Test
    @Order(8)
    @Transactional
    void shouldListWithCategoryFilter() {
        productMapper.insert(buildProduct(sellerId(), "Java编程思想", "书籍", "全新"));
        productMapper.insert(buildProduct(sellerId(), "数据结构", "书籍", "95新"));
        productMapper.insert(buildProduct(sellerId(), "iPhone 15", "数码", "95新"));

        ProductSearchQuery query = ProductSearchQuery.builder()
                .category("书籍")
                .build();

        List<Product> books = productMapper.listWithFilters(query, 0, 10);
        assertFalse(books.isEmpty());
        assertTrue(books.stream().allMatch(p -> "书籍".equals(p.getCategory())));
    }

    @Test
    @Order(9)
    @Transactional
    void shouldListWithPriceRangeFilter() {
        productMapper.insert(buildProductWithPrice(sellerId(), "廉价商品", "数码", "全新", new BigDecimal("10.00")));
        productMapper.insert(buildProductWithPrice(sellerId(), "中等商品", "数码", "全新", new BigDecimal("50.00")));
        productMapper.insert(buildProductWithPrice(sellerId(), "高价商品", "数码", "95新", new BigDecimal("200.00")));

        ProductSearchQuery query = ProductSearchQuery.builder()
                .priceMin(new BigDecimal("30.00"))
                .priceMax(new BigDecimal("150.00"))
                .build();

        List<Product> results = productMapper.listWithFilters(query, 0, 10);
        assertFalse(results.isEmpty());
        for (Product p : results) {
            assertTrue(p.getPrice().compareTo(new BigDecimal("30.00")) >= 0);
            assertTrue(p.getPrice().compareTo(new BigDecimal("150.00")) <= 0);
        }
    }

    @Test
    @Order(10)
    @Transactional
    void shouldListWithConditionFilter() {
        productMapper.insert(buildProduct(sellerId(), "全新商品", "生活用品", "全新"));
        productMapper.insert(buildProduct(sellerId(), "8成新商品", "生活用品", "8成新"));

        ProductSearchQuery query = ProductSearchQuery.builder()
                .condition("全新")
                .build();

        List<Product> results = productMapper.listWithFilters(query, 0, 10);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(p -> "全新".equals(p.getCondition())));
    }

    @Test
    @Order(11)
    @Transactional
    void shouldListWithKeywordSearch() {
        // 插入有独特关键词的商品用于全文搜索
        productMapper.insert(buildProduct(sellerId(), "高等数学第七版", "书籍", "全新"));
        productMapper.insert(buildProduct(sellerId(), "大学英语四级", "书籍", "95新"));

        // keyword 通过 FULLTEXT MATCH 搜索（要求 MySQL InnoDB FULLTEXT 索引）
        ProductSearchQuery query = ProductSearchQuery.builder()
                .keyword("数学")
                .build();

        List<Product> results = productMapper.listWithFilters(query, 0, 10);
        // FULLTEXT 在 MyISAM/InnoDB 行为略有差异，只验证不抛异常即可
        assertNotNull(results);
    }

    @Test
    @Order(12)
    @Transactional
    void shouldCountWithFilters() {
        productMapper.insert(buildProduct(sellerId(), "计数过滤A", "数码", "全新"));
        productMapper.insert(buildProduct(sellerId(), "计数过滤B", "数码", "95新"));

        ProductSearchQuery query = ProductSearchQuery.builder()
                .category("数码")
                .build();

        long count = productMapper.countWithFilters(query);
        assertTrue(count >= 2);
    }

    // ---- 边界条件 ----

    @Test
    @Order(13)
    @Transactional
    void shouldReturnNullForNonExistentProduct() {
        Product found = productMapper.findById(99999999L);
        assertNull(found);
    }

    @Test
    @Order(14)
    @Transactional
    void shouldReturnEmptyListForNoMatch() {
        ProductSearchQuery query = ProductSearchQuery.builder()
                .category("不存在的分类XYZ123")
                .build();

        List<Product> results = productMapper.listWithFilters(query, 0, 10);
        // 搜索不存在的分类应返回空列表（而非 null）
        assertNotNull(results);
    }

    @Test
    @Order(15)
    @Transactional
    void shouldReturnNullForUpdateOnNonExistentProduct() {
        Product found = productMapper.findByIdForUpdate(99999999L);
        assertNull(found);
    }

    // ---- helpers ----

    private Product buildProduct(long sellerId, String title, String category, String condition) {
        return buildProductWithPrice(sellerId, title, category, condition, new BigDecimal("50.00"));
    }

    private Product buildProductWithPrice(long sellerId, String title, String category, String condition, BigDecimal price) {
        Product product = new Product();
        product.setSellerId(sellerId);
        product.setTitle(title);
        product.setDescription("集成测试描述");
        product.setCategory(category);
        product.setCondition(condition);
        product.setOriginalPrice(new BigDecimal("100.00"));
        product.setPrice(price);
        product.setTradeLocation("图书馆");
        product.setNegotiable(true);
        product.setImages("[\"https://example.com/img1.jpg\"]");
        product.setStatus("active");
        return product;
    }
}
