package com.gym.self.modules.visit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("visit_pass")
public class VisitPass {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long orderId;
    private Long depositFen;
    private Integer durationMinutes;
    private Integer warnMinutes;
    private LocalDateTime validUntil;
    private String status;
    private Long storeId;
    private LocalDateTime enteredAt;
    private LocalDateTime dueExitAt;
    private LocalDateTime exitedAt;
    private LocalDateTime forfeitedAt;
    private LocalDateTime refundedAt;
    private String refundError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
