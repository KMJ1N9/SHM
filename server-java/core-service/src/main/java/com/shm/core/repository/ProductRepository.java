package com.shm.core.repository;

import com.shm.common.model.dto.ProductSearchQuery;
import com.shm.common.model.entity.Product;
import com.shm.core.mapper.ProductMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 商品 Repository（封装 ProductMapper，对应 Node.js repository/product.js）
 */
@Repository
public class ProductRepository {

    private final ProductMapper mapper;

    public ProductRepository(ProductMapper mapper) {
        this.mapper = mapper;
    }

    public Product findById(Long id) {
        return mapper.findById(id);
    }

    /** 带悲观锁查询（用于下单时锁定商品防并发） */
    public Product findByIdForUpdate(Long id) {
        return mapper.findByIdForUpdate(id);
    }

    public Product create(Product product) {
        mapper.insert(product);
        return product;
    }

    public int update(Product product) {
        return mapper.update(product);
    }

    public int updateStatus(Long id, String status) {
        return mapper.updateStatus(id, status);
    }

    public List<Product> findBySellerId(Long sellerId, String status, int offset, int limit) {
        return mapper.findBySellerId(sellerId, status, offset, limit);
    }

    public long countBySellerId(Long sellerId, String status) {
        return mapper.countBySellerId(sellerId, status);
    }

    public List<Product> listWithFilters(ProductSearchQuery query, int offset, int limit) {
        return mapper.listWithFilters(query, offset, limit);
    }

    public long countWithFilters(ProductSearchQuery query) {
        return mapper.countWithFilters(query);
    }
}
