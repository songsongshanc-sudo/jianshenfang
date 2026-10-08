package com.gym.self.modules.content.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("banner")
public class Banner {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String title;
    private String imageUrl;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String linkUrl;
    private Integer sortNo;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
