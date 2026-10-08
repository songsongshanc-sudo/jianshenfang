package com.gym.self.modules.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("trade_order")
public class TradeOrder {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long storeId;
    private String bizType;
    private Long productId;
    private String productName;
    private Long amountFen;
    private String status;
    private Long agreementId;
    private Integer agreementVersion;
    private String idempotencyKey;
    private LocalDateTime expireAt;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
