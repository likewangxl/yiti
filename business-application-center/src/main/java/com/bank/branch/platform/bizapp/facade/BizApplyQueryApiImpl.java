package com.bank.branch.platform.bizapp.facade;

import com.bank.branch.platform.bizapp.api.BizApplyQueryApi;
import com.bank.branch.platform.bizapp.api.dto.BizApplyStatDTO;
import com.bank.branch.platform.bizapp.api.dto.RunningAppCountDTO;
import com.bank.branch.platform.bizapp.mapper.LoanApplyMapper;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.bizapp.service.BizApplySearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务申请综合查询接口实现。
 * <p>
 * 实现 {@link BizApplyQueryApi} 接口，聚合贷款申请和中场支持申请两个域的查询。
 * 简单计数查询直接委托 Mapper；复杂聚合统计委托 {@link BizApplySearchService}。
 * </p>
 * <p>
 * 注：本 Facade 仅返回计数 / 统计类 DTO（RunningAppCountDTO / BizApplyStatDTO），
 * 不返回 LoanApplyDTO / SupportRequestDTO，因此不依赖 Converter 层（Task 1.2）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BizApplyQueryApiImpl implements BizApplyQueryApi {

    private final LoanApplyMapper loanApplyMapper;
    private final SupportRequestMapper supportRequestMapper;
    private final BizApplySearchService bizApplySearchService;

    /**
     * 统计客户正在运行的业务申请数量（贷款+支持）。
     * <p>
     * 贷款运行中状态：IN_APPROVAL；
     * 支持申请运行中状态：通过 supportRequestMapper.countRunningByCustomer() 统计。
     * </p>
     *
     * @param custId 客户ID
     * @return 运行中的贷款申请数和支持申请数
     */
    @Override
    public RunningAppCountDTO countRunningApplications(String custId) {
        log.debug("[BizApplyQueryApiImpl.countRunningApplications] custId={}", custId);
        long runningLoans = countRunningLoansByCustId(custId);
        long runningSupports = supportRequestMapper.countRunningByCustomer(custId);

        RunningAppCountDTO dto = new RunningAppCountDTO();
        dto.setRunningLoanCount(runningLoans);
        dto.setRunningSupportCount(runningSupports);
        return dto;
    }

    /**
     * 判断客户是否有正在运行的贷款申请。
     *
     * @param custId 客户ID
     * @return 存在运行中贷款申请时返回 true
     */
    @Override
    public boolean hasRunningLoan(String custId) {
        log.debug("[BizApplyQueryApiImpl.hasRunningLoan] custId={}", custId);
        return countRunningLoansByCustId(custId) > 0;
    }

    /**
     * 判断客户是否有正在运行的支持申请。
     *
     * @param custId 客户ID
     * @return 存在运行中支持申请时返回 true
     */
    @Override
    public boolean hasRunningSupport(String custId) {
        log.debug("[BizApplyQueryApiImpl.hasRunningSupport] custId={}", custId);
        return supportRequestMapper.countRunningByCustomer(custId) > 0;
    }

    /**
     * 统计员工的业务申请汇总数据（全时段）。
     *
     * @param empId 员工工号
     * @return 申请统计数据
     */
    @Override
    public BizApplyStatDTO getEmpStatistics(String empId) {
        log.debug("[BizApplyQueryApiImpl.getEmpStatistics] empId={}", empId);
        return bizApplySearchService.getEmpStatistics(empId);
    }

    /**
     * 统计员工在指定时间段内的业务申请汇总数据。
     *
     * @param empId  员工工号
     * @param start  统计开始时间（含）
     * @param end    统计结束时间（含）
     * @return 申请统计数据
     */
    @Override
    public BizApplyStatDTO getEmpStatisticsByPeriod(String empId,
                                                     LocalDateTime start,
                                                     LocalDateTime end) {
        log.debug("[BizApplyQueryApiImpl.getEmpStatisticsByPeriod] empId={}, start={}, end={}",
                empId, start, end);
        return bizApplySearchService.getEmpStatisticsByPeriod(empId, start, end);
    }

    // ------------------------------------------------------------------
    // 私有辅助方法
    // ------------------------------------------------------------------

    /**
     * 统计客户正在运行的贷款申请数量（IN_APPROVAL 状态）。
     * <p>
     * LoanApplyMapper 无专用的按客户ID统计运行中数量方法，
     * 通过 selectByCustId 获取全量后在内存中按状态过滤计数，
     * 业务上单个客户的贷款申请列表量有限，此方式可接受。
     * </p>
     *
     * @param custId 客户ID
     * @return 运行中贷款申请数
     */
    private long countRunningLoansByCustId(String custId) {
        List<com.bank.branch.platform.bizapp.entity.LoanApply> loans =
                loanApplyMapper.selectByCustId(custId);
        if (loans == null || loans.isEmpty()) {
            return 0L;
        }
        // IN_APPROVAL 表示贷款申请正在审批流程中，是唯一的"运行中"状态
        return loans.stream()
                .filter(l -> "IN_APPROVAL".equals(l.getStatus()))
                .count();
    }
}
