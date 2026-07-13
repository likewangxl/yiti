package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.workflow.api.dto.ProcessMonitorItemDTO;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审批流监控查询 Service。
 * <p>
 * 按数据范围（{@link DataScopeContext}）过滤流程实例：系统管理员或范围为 ALL 时不加机构过滤；
 * 范围为 ORG/ORG_SUBTREE 时按机构编码集合过滤（通过 {@code WF_PROCESS_ORG} 参与机构快照表 EXISTS 过滤）；
 * 范围未知/为空时 Fail-Close（直接返回空结果，不下发任何数据）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessMonitorService {

    private final BizProcessMapMapper bizProcessMapMapper;
    private final CurrentUserApi currentUserApi;
    private final OrgApi orgApi;

    /**
     * 分页查询审批流监控列表。
     *
     * @param status    流程状态（可空，精确匹配）
     * @param bizType   业务类型（可空，精确匹配）
     * @param keyword   标题关键字（可空，模糊匹配）
     * @param startedBy 发起人工号（可空，精确匹配）
     * @param pageNo    页码（从1开始，小于1按1处理）
     * @param pageSize  每页大小（默认20，最大100）
     * @return 分页结果
     */
    public PageResult<ProcessMonitorItemDTO> query(String status, String bizType, String keyword,
                                                     String startedBy, int pageNo, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int pn = Math.max(pageNo, 1);
        int offset = (pn - 1) * size;

        // null=全行不过滤；空集(非null)=Fail-Close，此人看不到任何机构数据
        Collection<String> orgScope = resolveOrgScope();
        // 空集合（非 null）必须在此短路返回，绝不能让空集合进入 SQL —— XML 里 IN () 是 MySQL 语法错误
        if (orgScope != null && orgScope.isEmpty()) {
            return PageResult.of(pn, size, 0, List.of());
        }

        long total = bizProcessMapMapper.countMonitor(status, bizType, keyword, startedBy, orgScope);
        // total==0 时跳过分页查询，省掉一次零命中的 DB 往返
        List<BizProcessMap> rows = total == 0 ? List.of() : bizProcessMapMapper.selectMonitorPage(
                status, bizType, keyword, startedBy, orgScope, offset, size);

        List<ProcessMonitorItemDTO> dtos = toDtos(rows);
        return PageResult.of(pn, size, total, dtos);
    }

    /**
     * 解析当前请求的机构过滤范围。
     *
     * @return null=不加机构过滤（系统管理员或 ALL 范围）；非 null 集合=按机构编码过滤
     *         （空集合表示 Fail-Close，调用方须短路返回空结果，不得下发到 SQL）
     */
    private Collection<String> resolveOrgScope() {
        if (currentUserApi.isSystemAdmin()) {
            return null;
        }
        DataScopeContext ctx = DataScopeContext.current();
        if (ctx == null || ctx.getScope() == null) {
            return Set.of(); // Fail-Close：无数据范围上下文，一律视为无权限
        }
        switch (ctx.getScope()) {
            case ALL:
                return null;
            case ORG:
                return ctx.getOrgCode() == null ? Set.of() : Set.of(ctx.getOrgCode());
            case ORG_SUBTREE:
                return ctx.getOrgSubtreeCodes() == null ? Set.of() : ctx.getOrgSubtreeCodes();
            default:
                return Set.of(); // 其余范围（SELF/SELF_CREATED/SELF_ASSIGNED/WORKFLOW_PARTICIPANT）本查询不支持，Fail-Close
        }
    }

    /**
     * 将实体列表转换为 DTO 列表，批量补全发起人/当前处理人的主机构编码，避免 N+1 查询。
     */
    private List<ProcessMonitorItemDTO> toDtos(List<BizProcessMap> rows) {
        Set<String> emps = new HashSet<>();
        rows.forEach(r -> {
            if (r.getStartUser() != null) {
                emps.add(r.getStartUser());
            }
            if (r.getCurrentAssignee() != null) {
                emps.add(r.getCurrentAssignee());
            }
        });
        // 当前单页最多 100 条，理论上最多 200 个不同工号；OrgApi 结果有缓存，逐个调用可接受。
        Map<String, String> empToOrg = new HashMap<>();
        for (String emp : emps) {
            OrgDTO org = orgApi.getUserMainOrg(emp);
            if (org != null) {
                empToOrg.put(emp, org.getOrgCode());
            }
        }

        return rows.stream().map(r -> {
            ProcessMonitorItemDTO dto = new ProcessMonitorItemDTO();
            dto.setProcessInstanceId(r.getProcessInstanceId());
            dto.setBusinessKey(r.getBusinessKey());
            dto.setBizType(r.getBizType());
            dto.setTitle(r.getTitle());
            dto.setProcessStatus(r.getProcessStatus());
            dto.setStartUser(r.getStartUser());
            dto.setStartUserOrgCode(empToOrg.get(r.getStartUser()));
            dto.setCurrentAssignee(r.getCurrentAssignee());
            dto.setCurrentAssigneeOrgCode(empToOrg.get(r.getCurrentAssignee()));
            dto.setStartTime(r.getStartTime());
            dto.setEndTime(r.getEndTime());
            return dto;
        }).collect(Collectors.toList());
    }
}
