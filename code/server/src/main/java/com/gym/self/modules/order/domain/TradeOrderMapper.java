package com.gym.self.modules.order.domain;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {

    @Select("SELECT id FROM trade_order WHERE id = #{id} FOR UPDATE")
    Long lockById(long id);
}
