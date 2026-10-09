package com.gym.self.modules.gate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gate_device")
public class GateDevice {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long storeId;
    private String deviceSn;
    private String secret;
    private String name;
    private String status;
    private LocalDateTime lastBeatAt;
    private String token;
    private String firmware;
}
