package com.bank.branch.platform.bizapp.api;

import com.bank.branch.platform.bizapp.api.dto.LoanApplyDTO;
import com.bank.branch.platform.bizapp.api.dto.LoanQueryConditionDTO;
import com.bank.branch.platform.common.web.PageResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 贷款申请对外分页/统计查询接口。
 * <p>
 * 供报表分析中心、绩效计算中心等需要批量统计的模块使用。
 * 实现类位于 {@code facade/LoanQueryApiImpl}。
 * </p>
 */
public interface LoanQueryApi {

    /**
     * 分页查询贷款申请。
     *
     * @param condition 查询条件（关键字、状态、机构、分页参数）
     * @return 分页结果
     */
    PageResult<LoanApplyDTO> pageQuery(LoanQueryConditionDTO condition);

    /**
     * 按机构和时间范围统计已完成的贷款申请数量。
     *
     * @param orgId  机构代码
     * @param start  统计开始时间（含）
     * @param end    统计结束时间（含）
     * @return 已完成申请数量
     */
    long countCompletedByOrg(String orgId, LocalDateTime start, LocalDateTime end);

    /**
     * 按员工工号和时间范围汇总授信金额。
     *
     * @param empId  员工工号（创建人）
     * @param start  统计开始时间（含）
     * @param end    统计结束时间（含）
     * @return 授信金额汇总，无数据时返回 BigDecimal.ZERO
     */
    BigDecimal sumCreditAmountByEmp(String empId, LocalDateTime start, LocalDateTime end);
}
