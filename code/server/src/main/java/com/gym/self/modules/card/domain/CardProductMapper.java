package com.gym.self.modules.card.domain;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CardProductMapper extends BaseMapper<CardProduct> {

    @Update("""
            UPDATE card_product
            SET stock_reserved = stock_reserved + 1, updated_at = #{now}
            WHERE id = #{id} AND deleted = 0 AND stock_total IS NOT NULL
              AND stock_total - stock_sold - stock_reserved >= 1
            """)
    int reserveOne(@Param("id") long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE card_product
            SET stock_reserved = stock_reserved - 1, stock_sold = stock_sold + 1, updated_at = #{now}
            WHERE id = #{id} AND stock_total IS NOT NULL AND stock_reserved > 0
            """)
    int sellReserved(@Param("id") long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE card_product
            SET stock_sold = stock_sold + 1, updated_at = #{now}
            WHERE id = #{id} AND stock_total IS NOT NULL
              AND stock_total - stock_sold - stock_reserved >= 1
            """)
    int sellOne(@Param("id") long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE card_product
            SET stock_reserved = stock_reserved - 1, updated_at = #{now}
            WHERE id = #{id} AND stock_total IS NOT NULL AND stock_reserved > 0
            """)
    int releaseOne(@Param("id") long id, @Param("now") LocalDateTime now);
}
