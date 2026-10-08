package com.gym.self.modules.content.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_config")
public class AppConfig {

    @TableId(value = "config_key", type = IdType.INPUT)
    private String configKey;
    private String configValue;
    private LocalDateTime updatedAt;
}
