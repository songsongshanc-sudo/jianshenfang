package com.gym.self.modules.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("payment")
public class Payment {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long orderId;
    private String mchId;
    private String prepayId;
    private String transactionId;
    private Long amountFen;
    private String status;
    private String rawNotify;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
