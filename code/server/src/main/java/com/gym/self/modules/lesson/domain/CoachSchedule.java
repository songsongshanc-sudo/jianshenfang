package com.gym.self.modules.lesson.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("coach_schedule")
public class CoachSchedule {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long coachId;
    private String startHm;
    private Integer sortNo;
    private LocalDateTime createdAt;
}
