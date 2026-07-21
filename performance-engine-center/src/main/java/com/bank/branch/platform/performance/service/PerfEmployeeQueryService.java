package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.dto.PerfEmpOptionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 绩效域员工查询服务：为目标值等页面提供「按关键字搜员工」的轻量输入建议。
 *
 * <p><b>为什么绩效模块要自己开一个而不是复用现成端点</b>（2026-07-21）：
 * <ul>
 *   <li>{@code /api/admin/users}：字段对（返回 username），但那是<b>管理员用户管理</b>接口，
 *       资源 {@code A_USER_LIST} 只授予少数管理角色。绩效页面调它会让资财部经办人等业务角色
 *       进页面即 403，且为了两个页面把账号管理信息开放给业务角色并不划算。</li>
 *   <li>{@code /api/employees}（通讯录）：权限够，但数据源是 {@code ADDRBOOK_EMPLOYEE}，
 *       覆盖面远小于 {@code PT_USER}（实测 12 vs 82），大量员工搜不到。</li>
 *   <li>{@code /api/reports/employees/search}：权限与覆盖面都够（同样走 {@code pageUsers}），
 *       但返回的 id 是 {@code USER_ID} 而非工号，与目标值 {@code subject_id} 对不上。</li>
 * </ul>
 * 三者各差一项，故此处新开：走 {@code pageUsers}（{@code PT_USER} 全表）保证覆盖面，
 * 只吐工号保证口径，挂 {@code PERF_CONFIG} 权限保证业务角色可用。
 *
 * <p>本服务<b>不做数据范围收窄</b>：目标值本就是给全行任意员工下达的，
 * 按查询者机构过滤会导致资财部无法给分行员工设目标。可见性由 {@code PERF_CONFIG} 这层
 * 功能权限控制——能配绩效的人本就能看到全员名单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfEmployeeQueryService {

    /** 单次返回上限，防止前端传超大 limit 打爆查询 */
    private static final int MAX_LIMIT = 50;

    private final UserApi userApi;

    /**
     * 按关键字搜索员工（工号/姓名模糊匹配，匹配逻辑由 {@code UserApi#pageUsers} 承担）。
     *
     * @param keyword 关键字，空表示不过滤（由上游 {@code pageUsers} 处理）
     * @param limit   期望条数，收敛到 [1, {@value #MAX_LIMIT}]
     * @return 员工选项列表，按上游顺序；无结果返回空列表（不返回 null）
     */
    public List<PerfEmpOptionDTO> searchEmployees(String keyword, int limit) {
        int size = Math.min(Math.max(limit, 1), MAX_LIMIT);
        PageResult<UserDTO> page = userApi.pageUsers(keyword, 1, size);
        if (page == null || page.getRecords() == null) {
            return List.of();
        }
        List<PerfEmpOptionDTO> out = new ArrayList<>(page.getRecords().size());
        for (UserDTO u : page.getRecords()) {
            // 工号为空的记录直接跳过：本列表的用途就是给 subject_id 提供候选值，
            // 空工号选中后必然提交失败，不如不出现在候选里。
            if (u.getUsername() == null || u.getUsername().isBlank()) {
                continue;
            }
            out.add(new PerfEmpOptionDTO(u.getUsername(), u.getDisplayName(), u.getMainOrgName()));
        }
        return out;
    }
}
