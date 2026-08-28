package com.delta.pay.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.delta.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment")
public class Payment extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String paymentNo;
    private Long orderId;
    private Long userId;
    /** 业务类型: ORDER-订单支付 PLAYER_DEPOSIT-打手押金 RECHARGE-余额充值 */
    private String bizType;
    private BigDecimal amount;
    /** 余额充值赠送金额，支付单创建时锁定，避免后台改套餐影响历史订单。 */
    private BigDecimal rechargeBonus;
    private String payMethod;
    private String status;
    private String wxTransactionId;
    private String wxPrepayId;
    private BigDecimal refundAmount;
    private String refundNo;
    private String refundReason;
    private LocalDateTime refundTime;
    private LocalDateTime paidAt;
}
