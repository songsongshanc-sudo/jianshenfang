package com.gym.self.modules.store.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("store_guide")
public class StoreGuide {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long storeId;
    private String imageUrl;
    private String caption;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
