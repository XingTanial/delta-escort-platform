package com.delta.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.delta.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String openid;
    private String nickname;
    private String avatar;
    private String phone;
    private Integer status;
    @TableLogic
    private Integer deleted;

    /** 余额（非DB字段） */
    @TableField(exist = false)
    private BigDecimal balance;
}
