package com.bank.branch.platform.report.service;

import com.bank.branch.platform.report.dto.resp.EmpDashboardRespDTO;
import com.bank.branch.platform.report.dto.resp.OrgDashboardRespDTO;
import com.bank.branch.platform.report.dto.resp.PresidentDashboardRespDTO;

import java.time.LocalDate;

/**
 * 仪表盘服务（M2 阶段，仅装配 C.1 分行行长仪表盘）.
 *
 * <p>M2.3 起扩展机构 / 员工仪表盘到 {@code OrgDashboardRespDTO} / {@code EmpDashboardRespDTO}.
 *
 * <p>权限规则：
 * <ul>
 *   <li>{@link #getPresidentDashboard(LocalDate)}：必须有 R_PRESIDENT 角色，否则抛 RPT-40301</li>
 * </ul>
 *
 * <p>缓存策略：{@code rpt:dashboard:president} TTL=5min，按 {@code (dataDate, orgCode)} 区分.
 */
public interface DashboardService {

    /**
     * 获取分行行长仪表盘.
     *
     * @param dataDate 数据日期（null 时回填 today）
     * @return 仪表盘 5 区块数据
     */
    PresidentDashboardRespDTO getPresidentDashboard(LocalDate dataDate);

    /**
     * 获取机构仪表盘（C.2 GET /dashboard/org/{orgCode}）.
     *
     * @param orgCode  目标机构编码
     * @param dataDate 数据日期（null 时回填 today）
     */
    OrgDashboardRespDTO getOrgDashboard(String orgCode, LocalDate dataDate);

    /**
     * 获取员工仪表盘（C.3 GET /dashboard/emp/{empId}）.
     *
     * @param empId    目标员工
     * @param dataDate 数据日期（null 时回填 today）
     */
    EmpDashboardRespDTO getEmpDashboard(String empId, LocalDate dataDate);
}
