package com.delta.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.delta.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String name;
    private String subtitle;
    private String description;
    private String coverImage;
    private String images;
    private BigDecimal price;
    private Integer status;
    private Integer sortOrder;
    private Integer salesCount;
    private Integer isRecommend;
    /** 所属热门推荐分类（isRecommend=1 时可选），为空则仅按 isRecommend 展示在「全部」 */
    private Long recommendCategoryId;
    /** 商品平均评分 */
    private java.math.BigDecimal avgRating;
    /** 评价数量 */
    private Integer reviewCount;
    /** 商品级抽佣比例(0~1)，为null则使用系统默认 */
    private java.math.BigDecimal commissionRate;
    /** 每人限购类型（0-不限购 1-永久限购一次 2-7天限购一次 3-1个月限购一次） */
    private Integer perUserLimitType;
    /** 是否开启每人限购（兼容旧字段，保存时自动同步） */
    private Integer perUserLimitEnabled;
    /** 每个用户最多可购买次数（兼容旧字段，保存时自动同步为 1） */
    private Integer perUserLimitCount;
    @TableLogic
    private Integer deleted;
}
