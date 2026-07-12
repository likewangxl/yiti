package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 大屏区块 Mapper.
 */
@Mapper
public interface RptScreenBlockMapper extends BaseMapper<RptScreenBlock> {

    /**
     * 统计引用某数据源的区块数（bind_json.$.dsId 命中），用于数据源删除保护.
     */
    int countByDsId(@Param("dsId") Long dsId);
}
