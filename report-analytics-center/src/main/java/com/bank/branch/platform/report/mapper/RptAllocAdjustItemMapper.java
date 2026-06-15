package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.PerfAllocAdjustItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业绩分配调整明细 Mapper：按 apply_id 取一组分配明细（详情页用）.
 *
 * <p>类名加 {@code Rpt} 前缀，避免与 performance 模块同名 {@code PerfAllocAdjustItemMapper}
 * 的 Spring 默认 bean 名冲突（ConflictingBeanDefinitionException）。</p>
 */
@Mapper
public interface RptAllocAdjustItemMapper extends BaseMapper<PerfAllocAdjustItem> {
}
