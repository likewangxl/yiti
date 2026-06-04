package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustTodoMapper;
import com.bank.branch.platform.workflow.api.TodoQueryApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 「我的待审批 - 业绩调整」查询服务。
 * <p>
 * 数据流：
 * 1. workflow TodoQueryApi 拿 ALLOC_ADJUST 待办的 businessKey
 * 2. applyIds = businessKey 列表去前缀
 * 3. mapper IN(applyIds) + 业务字段过滤 + 分页
 * 4. workflow 反查 TaskRespDTO, merge 进 AdjustTodoRespDTO
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocAdjustTodoService {

    private static final String BIZ_TYPE = "ALLOC_ADJUST";
    private static final String BUSINESS_KEY_PREFIX = BIZ_TYPE + ":";

    private final TodoQueryApi workflowTodoApi;
    private final PerfAllocAdjustTodoMapper mapper;

    /**
     * 查询当前用户的业绩调整待审批列表。
     *
     * @param empId    当前员工 ID（从 CurrentUserApi 传入，不接受前端参数防越权）
     * @param keyword  关键字（apply_no/cust_id 模糊匹配），可空
     * @param allocDim 维度过滤（CUST/ORG/EMP），可空
     * @param bizKind  业务类型（LOAN/DEPOSIT 等），可空
     * @param dateFrom 申请时间起（闭），可空
     * @param dateTo   申请时间止（闭，service 内部转 +1day exclusive），可空
     * @param pageNo   页码（从 1）
     * @param pageSize 每页大小
     */
    public PageResult<AdjustTodoRespDTO> listMyTodos(String empId,
                                                    String keyword,
                                                    String allocDim,
                                                    String bizKind,
                                                    LocalDate dateFrom,
                                                    LocalDate dateTo,
                                                    int pageNo,
                                                    int pageSize) {
        // PC 管理端会话链路：候选组取「当前登录用户」（走 workflow 会话版方法）
        return doListMyTodos(empId, keyword, allocDim, bizKind, dateFrom, dateTo, pageNo, pageSize, false);
    }

    /**
     * 同 {@link #listMyTodos}，但候选组按传入 empId 查库解析（<b>不依赖登录会话</b>）。
     * <p>供 callpu / SOAP 网关等<b>无会话上下文</b>链路使用（如 PERF_LIST 手机端待审批），
     * empId 由上游渠道认证后透传；PC 管理端请继续用 {@link #listMyTodos} 以保持会话登录语义。</p>
     */
    public PageResult<AdjustTodoRespDTO> listMyTodosByEmp(String empId,
                                                          String keyword,
                                                          String allocDim,
                                                          String bizKind,
                                                          LocalDate dateFrom,
                                                          LocalDate dateTo,
                                                          int pageNo,
                                                          int pageSize) {
        return doListMyTodos(empId, keyword, allocDim, bizKind, dateFrom, dateTo, pageNo, pageSize, true);
    }

    /**
     * 「我的待审批」公共实现。
     *
     * @param sessionLess true=无会话链路（候选组按 empId 查库解析，走 workflow *ByEmp 方法）；
     *                    false=PC 会话链路（候选组取当前登录用户，走 workflow 会话版方法）
     */
    private PageResult<AdjustTodoRespDTO> doListMyTodos(String empId,
                                                        String keyword,
                                                        String allocDim,
                                                        String bizKind,
                                                        LocalDate dateFrom,
                                                        LocalDate dateTo,
                                                        int pageNo,
                                                        int pageSize,
                                                        boolean sessionLess) {
        log.debug("[AllocAdjustTodoService.listMyTodos] empId={}, kw={}, dim={}, kind={}, from={}, to={}, page={}/{}, sessionLess={}",
                empId, keyword, allocDim, bizKind, dateFrom, dateTo, pageNo, pageSize, sessionLess);

        List<String> allTodoKeys = sessionLess
                ? workflowTodoApi.listTodoBusinessKeysByEmp(empId, BIZ_TYPE)
                : workflowTodoApi.listMyTodoBusinessKeys(empId, BIZ_TYPE);
        if (allTodoKeys.isEmpty()) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }

        List<String> applyIds = allTodoKeys.stream()
                .map(k -> k.startsWith(BUSINESS_KEY_PREFIX) ? k.substring(BUSINESS_KEY_PREFIX.length()) : k)
                .collect(Collectors.toList());

        LocalDateTime fromDt = dateFrom == null ? null : dateFrom.atStartOfDay();
        LocalDateTime toExclusive = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay();

        long total = mapper.countMyTodos(applyIds, keyword, allocDim, bizKind, fromDt, toExclusive);
        if (total == 0) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }
        int offset = (pageNo - 1) * pageSize;
        List<PerfAllocAdjustApply> applies = mapper.selectMyTodos(
                applyIds, keyword, allocDim, bizKind, fromDt, toExclusive, offset, pageSize);

        List<String> pageKeys = applies.stream()
                .map(a -> BUSINESS_KEY_PREFIX + a.getId())
                .collect(Collectors.toList());
        Map<String, TaskRespDTO> metaMap = sessionLess
                ? workflowTodoApi.findTaskRespByBusinessKeysByEmp(empId, pageKeys)
                : workflowTodoApi.findTaskRespByBusinessKeys(empId, pageKeys);

        List<AdjustTodoRespDTO> records = applies.stream()
                .map(a -> mergeToDto(a, metaMap.get(BUSINESS_KEY_PREFIX + a.getId())))
                .collect(Collectors.toList());

        return PageResult.of(pageNo, pageSize, total, records);
    }

    private AdjustTodoRespDTO mergeToDto(PerfAllocAdjustApply a, TaskRespDTO t) {
        AdjustTodoRespDTO d = new AdjustTodoRespDTO();
        d.setId(a.getId());
        d.setApplyNo(a.getApplyNo());
        d.setCustId(a.getCustId());
        d.setCustName(a.getCustName());
        d.setAllocDim(a.getAllocDim());
        d.setBizKind(a.getBizKind());
        d.setOwnerOrgId(a.getOwnerOrgId());
        d.setCreatedBy(a.getCreatedBy());
        d.setCreatedTime(a.getCreatedTime());
        d.setBusinessKey(a.getBusinessKey());
        d.setStatus(a.getStatus());
        if (t != null) {
            d.setTaskId(t.getTaskId());
            d.setNodeKey(t.getNodeKey());
            d.setTaskName(t.getTaskName());
            d.setTitle(t.getTitle());
            d.setClaimable(t.getClaimable());
            d.setStartUser(t.getStartUser());
            d.setStartUserName(t.getStartUserName());
            d.setStartOrgId(t.getStartOrgId());
            d.setStartOrgName(t.getStartOrgName());
            d.setStartTime(t.getStartTime());
            d.setTaskCreateTime(t.getTaskCreateTime());
            d.setSlaStatus(t.getSlaStatus());
        }
        return d;
    }
}
