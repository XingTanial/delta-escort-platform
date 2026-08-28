package com.delta.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("`order`")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long productId;
    private String productName;
    private String specInfo;
    private BigDecimal amount;
    /** 下单时快照的抽佣比例（0~1，如 0.1 表示 10%），来自 sys_config settlement.commission_rate */
    private BigDecimal commissionRate;
    private String gameAccount;
    private String contact;
    private String remark;
    private String extraFields;
    private Integer requiredPlayerCount;
    private Long designatedPlayerId;
    private Long playerId;
    private String status;
    private LocalDateTime payDeadline;
    private LocalDateTime assignTime;
    private LocalDateTime acceptTime;
    private LocalDateTime teammateDeadline;
    private LocalDateTime startTime;
    private LocalDateTime completeTime;
    private LocalDateTime confirmTime;
    private LocalDateTime autoConfirmDeadline;
    private Integer settled;
    private BigDecimal settleAmount;
    private LocalDateTime settleTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 打手昵称（非DB字段，列表展示用） */
    @TableField(exist = false)
    private String playerName;
    /** 打手头像（非DB字段，列表展示用） */
    @TableField(exist = false)
    private String playerAvatar;
    /** 用户昵称（非DB字段，打手端展示用） */
    @TableField(exist = false)
    private String userNickname;
    /** 用户头像（非DB字段，打手端展示用） */
    @TableField(exist = false)
    private String userAvatar;
    /** 队友列表（非DB字段，详情展示用） */
    @TableField(exist = false)
    private List<OrderPlayer> teammates;
}
