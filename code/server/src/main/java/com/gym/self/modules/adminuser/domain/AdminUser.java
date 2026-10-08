package com.gym.self.modules.adminuser.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_user")
public class AdminUser {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String username;
    private String passwordHash;
    private String role;
    private Long storeId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
