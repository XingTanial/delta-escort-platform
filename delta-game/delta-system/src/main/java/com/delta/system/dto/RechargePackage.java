package com.delta.system.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 余额充值套餐：amount 为实际支付金额，bonus 为额外赠送余额。 */
@Data
public class RechargePackage {
    private String id;
    private String name;
    private BigDecimal amount;
    private BigDecimal bonus;
    private Boolean enabled;
    private Integer sort;
}
