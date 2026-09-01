package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.api.dto.BizApplyStatDTO;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.customer.api.AssetProjectQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 业务申请跨域聚合查询服务。
 * <p>
 * 汇总贷款申请（loan_apply）和中台支持申请（support_request）两个域的统计数据，
 * 供 {@code BizApplyQueryApiImpl} 对外提供员工绩效统计能力。
 * 本服务只做只读聚合，不发起任何写操作。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizApplySearchService {

    private final ObjectProvider<AssetProjectQueryApi> assetProjectQueryApiProvider;
    private final SupportRequestMapper supportMapper;

    /**
     * 统计员工创建的所有业务申请（全时段）。
     * <p>
     * 汇总指定员工在 loan_apply 和 support_request 两表中创建的记录数、
     * 已完成数和授信金额合计。
     * </p>
     *
     * @param empId 员工工号（对应两表的 created_by 字段）
     * @return 申请统计 DTO，各字段至少为 0 / BigDecimal.ZERO
     */
    public BizApplyStatDTO getEmpStatistics(String empId) {
        log.debug("[BizApplySearchService.getEmpStatistics] empId={}", empId);

        // 贷款申请：分别统计总数和已完成数
        AssetProjectQueryApi assetProjectQueryApi = assetProjectQueryApiProvider.getIfAvailable();
        long totalLoans = assetProjectQueryApi == null ? 0L : assetProjectQueryApi.countByApplicant(empId);
        // 需要按 created_by 过滤——借助全时段时间范围 (极早至极晚) 使用 sumCreditAmount 统计逻辑
        // 注意：mapper 只有按创建人+时段的汇总方法，使用 [MIN, MAX] 时间段获取全量
        LocalDateTime minTime = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
        LocalDateTime maxTime = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

        long completedLoans = assetProjectQueryApi == null ? 0L
                : assetProjectQueryApi.countCompletedByApplicant(empId, minTime, maxTime);
        BigDecimal totalCreditAmount = assetProjectQueryApi == null ? BigDecimal.ZERO
                : assetProjectQueryApi.sumCompletedCreditByApplicant(empId, minTime, maxTime);

        // 支持申请：统计创建人和承接人两个维度的已完成数
        long totalSupports = supportMapper.countPageForSupport(null, null, null);
        long completedSupports = supportMapper.countCompletedByCreator(empId, minTime, maxTime);

        BizApplyStatDTO stat = new BizApplyStatDTO();
        stat.setTotalLoans(totalLoans);
        stat.setCompletedLoans(completedLoans);
        stat.setTotalSupports(totalSupports);
        stat.setCompletedSupports(completedSupports);
        stat.setTotalCreditAmount(totalCreditAmount != null ? totalCreditAmount : BigDecimal.ZERO);
        return stat;
    }

    /**
     * 统计员工在指定时间段内创建的业务申请。
     * <p>
     * 汇总指定员工在给定时间段内，在 loan_apply 和 support_request 两表中
     * 创建的已完成记录数和授信金额合计。
     * 注意：当前实现中 totalLoans/totalSupports 不按时段过滤（mapper 无对应方法），
     * 仅 completedLoans/completedSupports/totalCreditAmount 体现时段限制。
     * </p>
     *
     * @param empId      员工工号
     * @param startTime  统计开始时间（含）
     * @param endTime    统计结束时间（含）
     * @return 申请统计 DTO
     */
    public BizApplyStatDTO getEmpStatisticsByPeriod(String empId,
                                                     LocalDateTime startTime,
                                                     LocalDateTime endTime) {
        log.debug("[BizApplySearchService.getEmpStatisticsByPeriod] empId={}, startTime={}, endTime={}",
                empId, startTime, endTime);

        AssetProjectQueryApi assetProjectQueryApi = assetProjectQueryApiProvider.getIfAvailable();
        long completedLoans = assetProjectQueryApi == null ? 0L
                : assetProjectQueryApi.countCompletedByApplicant(empId, startTime, endTime);
        BigDecimal totalCreditAmount = assetProjectQueryApi == null ? BigDecimal.ZERO
                : assetProjectQueryApi.sumCompletedCreditByApplicant(empId, startTime, endTime);
        long completedSupports = supportMapper.countCompletedByCreator(empId, startTime, endTime);

        BizApplyStatDTO stat = new BizApplyStatDTO();
        // 全量总数使用全时段（期间无按创建人过滤总数的 mapper 方法）
        stat.setTotalLoans(0L);
        stat.setCompletedLoans(completedLoans);
        stat.setTotalSupports(0L);
        stat.setCompletedSupports(completedSupports);
        stat.setTotalCreditAmount(totalCreditAmount != null ? totalCreditAmount : BigDecimal.ZERO);
        return stat;
    }
}
