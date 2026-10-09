package com.gym.self.modules.gate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gate_sync_task")
public class GateSyncTask {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long deviceId;
    private Long userId;
    private String cmd;
    private String status;
    private Integer attempts;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
