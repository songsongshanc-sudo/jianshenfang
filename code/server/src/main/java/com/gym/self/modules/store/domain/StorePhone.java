package com.gym.self.modules.store.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;

@Data
@TableName("store_phone")
public class StorePhone {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long storeId;
    private String phoneType;
    private String phone;
    private LocalTime timeStart;
    private LocalTime timeEnd;
    private Integer sortNo;
}
