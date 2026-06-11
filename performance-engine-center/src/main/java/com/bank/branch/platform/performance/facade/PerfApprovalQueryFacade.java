package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.PerfApprovalQueryApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustApprovalItemDTO;
import com.bank.branch.platform.performance.api.dto.AllocAdjustDetailDTO;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustDoneService;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustService;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustTodoService;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramNodeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link PerfApprovalQueryApi} 实现：合并「我的待审批」+「本人已审批」两个列表，
 * 补齐申请人姓名 / 客户名称，按申请时间倒序后内存分页。
 *
 * <p>权限逻辑零改动、零漂移：直接委托项目内已有的
 * {@link AllocAdjustTodoService#listMyTodos} 与 {@link AllocAdjustDoneService#listMyDones}，
 * empId 透传（外部渠道身份由上游 callpu 认证）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfApprovalQueryFacade implements PerfApprovalQueryApi {

    /** 默认每页大小（与手机端约定一致）。 */
    private static final int DEFAULT_PAGE_SIZE = 15;

    /**
     * 单用户待办 / 已办各自的拉取上限。
     * <p>因合并 + 排序需在内存完成，先各取上限再分页；超过上限会被截断并打 warn 日志。
     * 单个审批人的在办 + 已办分配调整量级通常远小于此值。</p>
     */
    private static final int FETCH_CAP = 500;

    private final AllocAdjustTodoService allocAdjustTodoService;
    private final AllocAdjustDoneService allocAdjustDoneService;
    private final UserApi userApi;
    private final PerfAllocAdjustApplyMapper allocAdjustApplyMapper;
    private final AllocAdjustService allocAdjustService;
    private final WorkflowQueryApi workflowQueryApi;

    @Override
    public PageResult<AllocAdjustApprovalItemDTO> listAllocAdjustApprovals(String empId, int pageNo, int pageSize) {
        return listAllocAdjustApprovals(empId, null, pageNo, pageSize);
    }

    @Override
    public PageResult<AllocAdjustApprovalItemDTO> listAllocAdjustApprovals(
            String empId, String statusFilter, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        log.info("[PerfApprovalQueryFacade.listAllocAdjustApprovals] empId={}, statusFilter={}, pageNo={}, pageSize={}",
                empId, statusFilter, safePageNo, safePageSize);

        boolean wantTodo = statusFilter == null || "PENDING".equals(statusFilter);
        boolean wantDone = statusFilter == null || "DONE".equals(statusFilter);

        // 1. 拉取待办 + 已办（各自上限 FETCH_CAP，参数过滤全空 = 不限关键字/维度/日期）
        //    待办走 *ByEmp 版本：callpu 无会话上下文，候选组按入参 empId 查库解析（避免 AUTH-40105）；
        //    已办按 taskAssignee(empId) 查询，本就不依赖候选组/登录态，无需区分。
        PageResult<AdjustTodoRespDTO> todoPage = wantTodo
                ? allocAdjustTodoService.listMyTodosByEmp(empId, null, null, null, null, null, 1, FETCH_CAP)
                : PageResult.of(1, FETCH_CAP, 0L, Collections.emptyList());
        PageResult<AdjustTodoRespDTO> donePage = wantDone
                ? allocAdjustDoneService.listMyDones(empId, null, null, null, null, null, 1, FETCH_CAP)
                : PageResult.of(1, FETCH_CAP, 0L, Collections.emptyList());

        if (todoPage.getTotal() > FETCH_CAP || donePage.getTotal() > FETCH_CAP) {
            log.warn("[PerfApprovalQueryFacade] empId={} 审批记录超过拉取上限 {}，合并列表可能被截断"
                            + "（todoTotal={}, doneTotal={}）",
                    empId, FETCH_CAP, todoPage.getTotal(), donePage.getTotal());
        }

        // 2. 合并去重（同一申请若既在待办又在已办，待办优先：仍可操作，显示为待审批）+ 补齐申请人姓名
        Map<String, String> empNameCache = new HashMap<>();
        Map<String, AllocAdjustApprovalItemDTO> byId = new LinkedHashMap<>();
        for (AdjustTodoRespDTO t : todoPage.getRecords()) {
            byId.put(t.getId(), toItem(t, "TODO", empNameCache));
        }
        for (AdjustTodoRespDTO d : donePage.getRecords()) {
            byId.putIfAbsent(d.getId(), toItem(d, "DONE", empNameCache));
        }

        // 3. 按申请时间倒序
        List<AllocAdjustApprovalItemDTO> merged = new ArrayList<>(byId.values());
        merged.sort(Comparator.comparing(AllocAdjustApprovalItemDTO::getApplyTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        // 4. 内存分页
        long total = merged.size();
        int from = (safePageNo - 1) * safePageSize;
        if (from >= merged.size()) {
            return PageResult.of(safePageNo, safePageSize, total, Collections.emptyList());
        }
        int to = Math.min(merged.size(), from + safePageSize);
        return PageResult.of(safePageNo, safePageSize, total, new ArrayList<>(merged.subList(from, to)));
    }

    @Override
    public PageResult<AllocAdjustApprovalItemDTO> listMyAllocAdjustApplications(
            String empId, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        log.info("[PerfApprovalQueryFacade.listMyAllocAdjustApplications] empId={}, pageNo={}, pageSize={}",
                empId, safePageNo, safePageSize);

        long total = allocAdjustApplyMapper.countByConditions(null, null, null, null, empId);
        int offset = (safePageNo - 1) * safePageSize;
        if (offset >= total) {
            return PageResult.of(safePageNo, safePageSize, total, Collections.emptyList());
        }
        List<PerfAllocAdjustApply> rows = allocAdjustApplyMapper.selectByConditions(
                null, null, null, null, empId, offset, safePageSize);

        Map<String, String> empNameCache = new HashMap<>();
        List<AllocAdjustApprovalItemDTO> records = new ArrayList<>(rows.size());
        for (PerfAllocAdjustApply e : rows) {
            records.add(AllocAdjustApprovalItemDTO.builder()
                    .perfAdjustNo(e.getId())
                    .applyNo(e.getApplyNo())
                    .custId(e.getCustId())
                    .custName(e.getCustName())
                    .createdBy(e.getCreatedBy())
                    .applyFullname(resolveEmpName(e.getCreatedBy(), empNameCache))
                    .applyTime(e.getCreatedTime())
                    .status(e.getStatus())
                    .category("MINE")
                    .build());
        }
        return PageResult.of(safePageNo, safePageSize, total, records);
    }

    /**
     * 把待办/已办的内部 DTO 转为对外审批项，并补齐申请人姓名。
     *
     * <p>客户名称直接取 {@code AdjustTodoRespDTO.custName}（即 PERF_ALLOC_ADJUST_APPLY.cust_name 快照），
     * 与管理端已办 {@code AllocAdjustDoneService.mergeToDto} 完全同源，保证两端「客户名称」一致，
     * 不再用 {@code CustomerQueryApi} 实时重查（避免客户改名后两端显示不一致）。</p>
     */
    private AllocAdjustApprovalItemDTO toItem(AdjustTodoRespDTO src,
                                             String category,
                                             Map<String, String> empNameCache) {
        return AllocAdjustApprovalItemDTO.builder()
                .perfAdjustNo(src.getId())
                .applyNo(src.getApplyNo())
                .custId(src.getCustId())
                .custName(src.getCustName())
                .createdBy(src.getCreatedBy())
                .applyFullname(resolveEmpName(src.getCreatedBy(), empNameCache))
                .applyTime(src.getCreatedTime())
                .status(src.getStatus())
                .category(category)
                .build();
    }

    /**
     * USER_ID → 姓名，按 USER_ID 缓存（含查不到的 null，避免重复 RPC）。
     *
     * <p>申请人 createdBy 物理存的是 PT_USER.USER_ID（短代理键），故必须走按 USER_ID 解析的
     * {@link UserApi#getUserName}（口径与管理端 {@code AllocAdjustService.getByIdDto} 一致）。
     * 早期误用 portal 通讯录 {@code AddressBookApi.getEmployee}（入参为工号 USERNAME），
     * 把 USER_ID 当工号查 → 永远查不到 → 申请人姓名恒为空，本次修正。</p>
     */
    private String resolveEmpName(String empId, Map<String, String> cache) {
        if (empId == null || empId.isBlank()) {
            return null;
        }
        if (cache.containsKey(empId)) {
            return cache.get(empId);
        }
        String name = userApi.getUserName(empId);
        cache.put(empId, name);
        return name;
    }

    @Override
    public AllocAdjustDetailDTO getAllocAdjustDetail(String perfAdjustNo, String empId) {
        log.info("[PerfApprovalQueryFacade.getAllocAdjustDetail] perfAdjustNo={}, empId={}", perfAdjustNo, empId);
        AllocAdjustService.ApplyWithItems loaded = allocAdjustService.getById(perfAdjustNo);
        PerfAllocAdjustApply apply = loaded.getApply();
        List<PerfAllocAdjustItem> items = loaded.getItems();

        String status = apply.getStatus();
        boolean canDelete = empId != null && empId.equals(apply.getCreatedBy())
                && ("IN_APPROVAL".equals(status) || "DRAFT".equals(status));
        boolean canApprove = "IN_APPROVAL".equals(status) && isMyTodo(empId, perfAdjustNo);

        List<AllocAdjustDetailDTO.AllocItem> allocaters = new ArrayList<>();
        if (items != null) {
            for (PerfAllocAdjustItem it : items) {
                boolean origin = "ORIGIN".equals(it.getItemKind());
                allocaters.add(AllocAdjustDetailDTO.AllocItem.builder()
                        .empId(it.getEmpId())
                        .username(it.getUsername())
                        .fullname(it.getEmpChnName())
                        .ratio(it.getRatio() == null ? null : it.getRatio().toPlainString())
                        .isOriginal(origin ? 1 : 2)
                        .build());
            }
        }

        // 当前/下一审批节点：IN_APPROVAL 取 Flowable 活动节点 + 静态链路推下一节点；终态走文案
        String[] nodes = resolveNodes(apply);

        Map<String, String> empNameCache = new HashMap<>();
        return AllocAdjustDetailDTO.builder()
                .perfAdjustNo(apply.getId())
                .applyNo(apply.getApplyNo())
                .custId(apply.getCustId())
                .custName(apply.getCustName())
                .custType(apply.getCustType())
                .allocDim(apply.getAllocDim())
                .bizKind(apply.getBizKind())
                .accountNo(apply.getAccountNo())
                .status(status)
                .reason(apply.getRemark())
                .createdBy(apply.getCreatedBy())
                .applyFullname(resolveEmpName(apply.getCreatedBy(), empNameCache))
                .applyTime(apply.getCreatedTime())
                .canDelete(canDelete)
                .canApprove(canApprove)
                .currentNode(nodes[0])
                .nextNode(nodes[1])
                .allocaters(allocaters)
                .build();
    }

    /**
     * 解析「当前节点 / 下一节点」中文名。
     *
     * <ul>
     *   <li>IN_APPROVAL：按 {@code processInstanceId} 查 Flowable 活动 userTask 作为当前节点，
     *       再用 {@link AllocAdjustNodeProgress} 按 custType 静态推算下一节点；</li>
     *   <li>终态（APPROVED/REJECTED/WITHDRAWN/DRAFT）：当前节点显示终态文案，下一节点「无」；</li>
     *   <li>查询异常 / 无活动节点 / 无 pid：当前「审批中」、下一节点留空，不抛异常。</li>
     * </ul>
     *
     * @return 长度恒为 2 的数组：[0]=当前节点，[1]=下一节点
     */
    private String[] resolveNodes(PerfAllocAdjustApply apply) {
        String status = apply.getStatus();
        if (!"IN_APPROVAL".equals(status)) {
            String cur;
            switch (status == null ? "" : status) {
                case "APPROVED": cur = "已完成"; break;
                case "REJECTED": cur = "已拒绝"; break;
                case "WITHDRAWN": cur = "已撤回"; break;
                case "DRAFT": cur = "草稿"; break;
                default: cur = "—";
            }
            return new String[]{cur, "无"};
        }

        String pid = apply.getProcessInstanceId();
        if (pid == null || pid.isBlank()) {
            return new String[]{"审批中", ""};
        }
        try {
            ProcessDiagramDTO diagram = workflowQueryApi.getProcessNodes(pid);
            ProcessDiagramNodeDTO active = null;
            if (diagram != null && diagram.getNodes() != null) {
                for (ProcessDiagramNodeDTO n : diagram.getNodes()) {
                    if ("ACTIVE".equals(n.getStatus()) && "userTask".equals(n.getNodeType())) {
                        active = n;
                        break;
                    }
                }
            }
            if (active == null) {
                return new String[]{"审批中", ""};
            }
            String next = AllocAdjustNodeProgress.nextNodeName(apply.getCustType(), active.getNodeKey());
            return new String[]{active.getNodeName(), next};
        } catch (Exception e) {
            log.warn("[PerfApprovalQueryFacade.resolveNodes] 查询流程节点失败 pid={}, err={}", pid, e.getMessage());
            return new String[]{"审批中", ""};
        }
    }

    /** 该申请是否为 empId 的 Flowable 待办（命中即可审批）。 */
    private boolean isMyTodo(String empId, String perfAdjustNo) {
        if (empId == null) {
            return false;
        }
        PageResult<AdjustTodoRespDTO> todos =
                allocAdjustTodoService.listMyTodosByEmp(empId, null, null, null, null, null, 1, FETCH_CAP);
        return todos.getRecords().stream().anyMatch(t -> perfAdjustNo.equals(t.getId()));
    }
}
