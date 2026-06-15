package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.PerfAllocAdjustApply;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业绩分配调整申请（主表）Mapper：列表分页 + 详情主记录.
 *
 * <p>类名加 {@code Rpt} 前缀，避免与 performance 模块同名 {@code PerfAllocAdjustApplyMapper}
 * 的 Spring 默认 bean 名（decapitalized 简单类名）冲突（ConflictingBeanDefinitionException）。</p>
 */
@Mapper
public interface RptAllocAdjustApplyMapper extends BaseMapper<PerfAllocAdjustApply> {
}
