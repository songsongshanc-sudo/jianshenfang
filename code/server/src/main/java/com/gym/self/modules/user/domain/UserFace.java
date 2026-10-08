package com.gym.self.modules.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_face")
public class UserFace {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private String objectKey;
    private String vendorFaceId;
    private String status;
    private String failReason;
    private LocalDateTime createdAt;
}
