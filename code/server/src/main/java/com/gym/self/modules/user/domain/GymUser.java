package com.gym.self.modules.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`user`")
public class GymUser {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String openid;
    private String unionid;
    private String phone;
    private String memberNo;
    private String nickname;
    private String avatarUrl;
    private String registerStatus;
    private LocalDateTime registeredAt;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
