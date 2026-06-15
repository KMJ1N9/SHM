package com.shm.admin.mapper;

import com.shm.common.model.entity.Product;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * Admin 端商品 Mapper（与 Node.js productRepo.listAll 行为完全一致）
 *
 * <p>管理端商品列表：含全部状态（active/reserved/sold/off_shelf/deleted），
 * JOIN 卖家信息，支持筛选。
 */
@Mapper
public interface ProductMapper {

    /** 管理端商品列表（含全部状态 + JOIN 卖家），与 Node.js adminService.listAllProducts 一致 */
    List<Map<String, Object>> listAll(@Param("status") String status,
                                       @Param("keyword") String keyword,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /** 管理端商品计数 */
    long countAll(@Param("status") String status,
                  @Param("keyword") String keyword);

    /** 更新商品状态（下架等操作） */
    @Update("UPDATE products SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 根据 ID 查商品 */
    @Select("SELECT id, seller_id, title, description, category, `condition`, original_price, price, trade_location, negotiable, images, status, created_at, updated_at FROM products WHERE id = #{id}")
    Product findById(Long id);
}
