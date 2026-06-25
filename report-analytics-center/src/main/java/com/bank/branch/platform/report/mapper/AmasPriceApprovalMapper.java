package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.AmasPriceApproval;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 价格审批 Mapper（只读）：分页/详情查询走 BaseMapper + LambdaQueryWrapper，无自定义 XML.
 */
@Mapper
public interface AmasPriceApprovalMapper extends BaseMapper<AmasPriceApproval> {
}
