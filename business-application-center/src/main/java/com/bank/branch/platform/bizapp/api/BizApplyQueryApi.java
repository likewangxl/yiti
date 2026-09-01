package com.bank.branch.platform.bizapp.api;

import com.bank.branch.platform.bizapp.api.dto.BizApplyStatDTO;
import com.bank.branch.platform.bizapp.api.dto.RunningAppCountDTO;

import java.time.LocalDateTime;

/**
 * 业务申请综合查询接口。
 * <p>
 * 跨贷款申请和中台支持申请两个域进行聚合查询，
 * 供绩效计算中心、报表分析中心等外部模块使用。
 * 实现类位于 {@code facade/BizApplyQueryApiImpl}。
 * </p>
 */
public interface BizApplyQueryApi {

    /**
     * 统计客户正在运行的业务申请数量（贷款+支持）。
     *
     * @param custId 客户ID
     * @return 运行中的贷款申请数和支持申请数
     */
    RunningAppCountDTO countRunningApplications(String custId);

    /**
     * 判断客户是否有正在运行的贷款申请。
     *
     * @param custId 客户ID
     * @return 存在运行中贷款申请时返回 true
     */
    boolean hasRunningLoan(String custId);

    /**
     * 判断客户是否有正在运行的支持申请。
     *
     * @param custId 客户ID
     * @return 存在运行中支持申请时返回 true
     */
    boolean hasRunningSupport(String custId);

    /**
     * 统计员工的业务申请汇总数据（全时段）。
     *
     * @param empId 员工工号
     * @return 申请统计数据（贷款/支持总数、完成数、授信金额）
     */
    BizApplyStatDTO getEmpStatistics(String empId);

    /**
     * 统计员工在指定时间段内的业务申请汇总数据。
     *
     * @param empId  员工工号
     * @param start  统计开始时间（含）
     * @param end    统计结束时间（含）
     * @return 申请统计数据（贷款/支持总数、完成数、授信金额）
     */
    BizApplyStatDTO getEmpStatisticsByPeriod(String empId, LocalDateTime start, LocalDateTime end);
}
