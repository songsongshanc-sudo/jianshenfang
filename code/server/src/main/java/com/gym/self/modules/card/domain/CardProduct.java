package com.gym.self.modules.card.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("card_product")
public class CardProduct {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long storeId;
    private String name;
    private Long priceFen;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String displayText;
    private Integer validDays;
    private String durationMode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer stockTotal;
    private Integer stockSold;
    private Integer stockReserved;
    private String unlockType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer unlockDays;
    private Integer crossStore;
    private Integer homeVisible;
    private Integer sortNo;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
