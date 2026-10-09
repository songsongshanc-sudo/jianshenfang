package com.gym.self.modules.gate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("door_log")
public class DoorLog {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long storeId;
    private String deviceSn;
    private Long membershipId;
    private String channel;
    private String result;
    private LocalDateTime createdAt;
    private LocalDateTime recogTime;
    private Integer passStatus;
    private String memberKey;
}
