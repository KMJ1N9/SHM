package com.shm.core.mapper;

import com.shm.common.model.dto.ProductSearchQuery;
import com.shm.common.model.entity.Product;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 商品 Mapper（对应 products 表，复杂查询在 ProductMapper.xml）
 */
@Mapper
public interface ProductMapper {

    @Select("SELECT id, seller_id, title, description, category, `condition`, original_price, price, trade_location, negotiable, images, status, created_at, updated_at FROM products WHERE id = #{id}")
    Product findById(Long id);

    /** 带悲观锁查询（用于下单时锁定商品防并发） */
    @Select("SELECT id, seller_id, title, description, category, `condition`, original_price, price, trade_location, negotiable, images, status, created_at, updated_at FROM products WHERE id = #{id} FOR UPDATE")
    Product findByIdForUpdate(Long id);

    @Insert("INSERT INTO products (seller_id, title, description, category, `condition`, original_price, price, trade_location, negotiable, images, status) " +
            "VALUES (#{sellerId}, #{title}, #{description}, #{category}, #{condition}, #{originalPrice}, #{price}, #{tradeLocation}, #{negotiable}, #{images}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Product product);

    @Update("UPDATE products SET title = #{title}, description = #{description}, category = #{category}, `condition` = #{condition}, " +
            "original_price = #{originalPrice}, price = #{price}, trade_location = #{tradeLocation}, negotiable = #{negotiable}, images = #{images} WHERE id = #{id}")
    int update(Product product);

    @Update("UPDATE products SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Select("<script>" +
            "SELECT id, seller_id, title, description, category, `condition`, original_price, price, trade_location, negotiable, images, status, created_at, updated_at " +
            "FROM products WHERE seller_id = #{sellerId}" +
            "<if test='status != null and status != \"\" and status != \"all\"'> AND status = #{status}</if>" +
            "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<Product> findBySellerId(@Param("sellerId") Long sellerId, @Param("status") String status,
                                  @Param("offset") int offset, @Param("limit") int limit);

    /** 卖家商品计数（与 findBySellerId 相同的过滤条件） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM products WHERE seller_id = #{sellerId}" +
            "<if test='status != null and status != \"\" and status != \"all\"'> AND status = #{status}</if>" +
            "</script>")
    long countBySellerId(@Param("sellerId") Long sellerId, @Param("status") String status);

    /**
     * 复杂搜索查询 — 实现在 ProductMapper.xml
     */
    List<Product> listWithFilters(@Param("query") ProductSearchQuery query,
                                   @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 复杂搜索计数 — 实现在 ProductMapper.xml
     */
    long countWithFilters(@Param("query") ProductSearchQuery query);
}
