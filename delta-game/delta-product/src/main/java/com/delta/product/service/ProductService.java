package com.delta.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.product.entity.Product;

public interface ProductService extends IService<Product> {
    /** 上下架 */
    void updateStatus(Long id, Integer status);
}
