package com.delta.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.common.utils.ImageListUtils;
import com.delta.product.entity.Product;
import com.delta.product.enums.ProductLimitTypeEnum;
import com.delta.product.mapper.ProductMapper;
import com.delta.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Override
    public boolean save(Product product) {
        normalizeProduct(product);
        return super.save(product);
    }

    @Override
    public boolean updateById(Product product) {
        normalizeProduct(product);
        return super.updateById(product);
    }

    @Override
    public boolean saveOrUpdate(Product product) {
        normalizeProduct(product);
        return super.saveOrUpdate(product);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Product product = new Product();
        product.setId(id);
        product.setStatus(status);
        super.updateById(product);
    }

    private void normalizeProduct(Product product) {
        if (product == null) {
            return;
        }
        product.setImages(ImageListUtils.normalize(product.getImages()));
        normalizePerUserLimit(product);
    }

    private void normalizePerUserLimit(Product product) {
        ProductLimitTypeEnum limitType = ProductLimitTypeEnum.resolve(
                product.getPerUserLimitType(),
                product.getPerUserLimitEnabled(),
                product.getPerUserLimitCount());
        product.setPerUserLimitType(limitType.getCode());
        if (!limitType.isLimited()) {
            product.setPerUserLimitEnabled(0);
            product.setPerUserLimitCount(null);
            return;
        }
        product.setPerUserLimitEnabled(1);
        product.setPerUserLimitCount(1);
    }

}
