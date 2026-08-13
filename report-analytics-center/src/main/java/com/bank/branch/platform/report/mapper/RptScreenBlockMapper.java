package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

/**
 * 大屏区块 Mapper.
 */
@Mapper
public interface RptScreenBlockMapper extends BaseMapper<RptScreenBlock> {

    /**
     * 统计引用某数据源的区块数（bind_json.$.dsId 命中），用于数据源删除保护.
     */
    int countByDsId(@Param("dsId") Long dsId);

    /**
     * 仅删除指定屏且指定 ID 的草稿孤儿块。
     *
     * <p>画布保存会在 RPT_SCREEN 的版本 CAS 成功后调用；屏 ID 条件必须保留，避免客户端
     * 传入或并发读取到其他屏 blockId 时扩大删除范围。</p>
     */
    int deleteByScreenIdAndIds(@Param("screenId") Long screenId,
                               @Param("blockIds") Collection<Long> blockIds);
}
