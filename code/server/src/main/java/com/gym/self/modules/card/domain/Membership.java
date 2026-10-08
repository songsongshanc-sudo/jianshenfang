package com.gym.self.modules.card.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("membership")
public class Membership {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long storeId;
    private Long orderId;
    private Long cardProductId;
    private String source;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String status;
    private Integer crossStore;
    private LocalDateTime createdAt;
}
