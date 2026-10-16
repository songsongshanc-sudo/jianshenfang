package com.gym.self.modules.lesson.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("lesson_booking")
public class LessonBooking {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long packId;
    private Long coachId;
    private Long storeId;
    private LocalDate classDate;
    private String startHm;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer seatRow;
    private Integer seatCol;
    private String seatNo;
    private String status;
    private LocalDateTime pendingUntil;
    private String rejectReason;
    private String cancelReason;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime refundedAt;
    /** COACH=教练端 STORE=门店管理端 */
    private String actedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
