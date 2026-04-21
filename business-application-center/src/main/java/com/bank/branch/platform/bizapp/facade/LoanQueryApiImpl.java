package com.bank.branch.platform.bizapp.facade;

import com.bank.branch.platform.bizapp.api.LoanQueryApi;
import com.bank.branch.platform.bizapp.api.converter.LoanApplyDTOConverter;
import com.bank.branch.platform.bizapp.api.dto.LoanApplyDTO;
import com.bank.branch.platform.bizapp.api.dto.LoanQueryConditionDTO;
import com.bank.branch.platform.bizapp.entity.LoanApply;
import com.bank.branch.platform.bizapp.mapper.LoanApplyMapper;
import com.bank.branch.platform.common.web.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 贷款申请对外分页/统计查询接口实现。
 * <p>
 * 实现 {@link LoanQueryApi} 接口，委托 {@link LoanApplyMapper} 执行分页与聚合查询。
 * 所有查询结果通过 {@link LoanApplyDTOConverter} 转换，确保不暴露 deleted 字段且补充 custName 冗余字段。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanQueryApiImpl implements LoanQueryApi {

    private final LoanApplyMapper loanApplyMapper;
    private final LoanApplyDTOConverter loanApplyDTOConverter;

    /**
     * 分页查询贷款申请。
     *
     * @param condition 查询条件（关键字、状态、机构、分页参数）
     * @return 分页结果
     */
    @Override
    public PageResult<LoanApplyDTO> pageQuery(LoanQueryConditionDTO condition) {
        log.debug("[LoanQueryApiImpl.pageQuery] condition={}", condition);
        int pageNo = condition.getPageNo();
        int pageSize = condition.getPageSize();
        // 计算数据库偏移量（从第1页开始）
        int offset = (pageNo - 1) * pageSize;

        long total = loanApplyMapper.countPage(condition.getKeyword(),
                condition.getStatus(), condition.getOwnerOrgId());
        if (total == 0) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }

        List<LoanApply> entities = loanApplyMapper.selectPage(condition.getKeyword(),
                condition.getStatus(), condition.getOwnerOrgId(), offset, pageSize);
        List<LoanApplyDTO> records = entities == null ? Collections.emptyList()
                // 使用批量转换避免 N+1
                : loanApplyDTOConverter.toDTOList(entities);

        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 按机构和时间范围统计已完成的贷款申请数量。
     *
     * @param orgId      机构代码
     * @param startTime  统计开始时间
     * @param endTime    统计结束时间
     * @return 已完成申请数量
     */
    @Override
    public long countCompletedByOrg(String orgId, LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("[LoanQueryApiImpl.countCompletedByOrg] orgId={}, startTime={}, endTime={}", orgId, startTime, endTime);
        return loanApplyMapper.countCompletedByOrg(orgId, startTime, endTime);
    }

    /**
     * 按员工工号和时间范围汇总授信金额。
     *
     * @param empId      员工工号
     * @param startTime  统计开始时间
     * @param endTime    统计结束时间
     * @return 授信金额汇总，mapper 返回 null 时返回 BigDecimal.ZERO
     */
    @Override
    public BigDecimal sumCreditAmountByEmp(String empId, LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("[LoanQueryApiImpl.sumCreditAmountByEmp] empId={}, startTime={}, endTime={}", empId, startTime, endTime);
        BigDecimal result = loanApplyMapper.sumCreditAmountByEmp(empId, startTime, endTime);
        return result != null ? result : BigDecimal.ZERO;
    }
}
