package com.gym.self.modules.store.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("store")
public class Store {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String code;
    private String name;
    private String province;
    private String city;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String coverUrl;
    private String businessHours;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String wifiSsid;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String wifiPassword;
    private String mchId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
