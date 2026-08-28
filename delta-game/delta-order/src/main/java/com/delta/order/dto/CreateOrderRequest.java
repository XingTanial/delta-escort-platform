package com.delta.order.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateOrderRequest {
    private Long productId;
    private String specInfo;
    private BigDecimal amount;
    private String gameAccount;
    private String contact;
    private String remark;
    private Long designatedPlayerId;
    private java.util.Map<String, String> extraFields;
}
