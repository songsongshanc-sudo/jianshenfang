package com.gym.self.modules.lesson.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lesson_appeal")
public class LessonAppeal {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long bookingId;
    private Long userId;
    private String reason;
    private String status;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
