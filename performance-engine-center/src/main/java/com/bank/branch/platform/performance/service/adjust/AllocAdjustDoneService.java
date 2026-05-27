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
 * 「已审批」查询服务。
 * <p>数据流同 my-todos 但走 workflow listMyDoneBusinessKeys + findDoneTaskRespByBusinessKeys。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocAdjustDoneService {

    private static final String BIZ_TYPE = "ALLOC_ADJUST";
    private static final String BUSINESS_KEY_PREFIX = BIZ_TYPE + ":";

    private final TodoQueryApi workflowTodoApi;
    private final PerfAllocAdjustTodoMapper mapper;

    public PageResult<AdjustTodoRespDTO> listMyDones(String empId,
                                                    String keyword,
                                                    String allocDim,
                                                    String bizKind,
                                                    LocalDate dateFrom,
                                                    LocalDate dateTo,
                                                    int pageNo,
                                                    int pageSize) {
        log.debug("[AllocAdjustDoneService.listMyDones] empId={}, kw={}, dim={}, kind={}, from={}, to={}, page={}/{}",
                empId, keyword, allocDim, bizKind, dateFrom, dateTo, pageNo, pageSize);

        // 双路合并：workflow done keys + 业务表 APPROVED/REJECTED 申请 ID
        List<String> wfKeys = workflowTodoApi.listMyDoneBusinessKeys(empId, BIZ_TYPE);
        List<String> wfIds = wfKeys.stream()
                .map(k -> k.startsWith(BUSINESS_KEY_PREFIX) ? k.substring(BUSINESS_KEY_PREFIX.length()) : k)
                .collect(Collectors.toList());

        // 从业务表补充 APPROVED/REJECTED 的申请（包含驳回后 workflow 查不到的记录）
        List<String> bizIds = mapper.selectFinishedApplyIds();
        java.util.Set<String> allIdSet = new java.util.LinkedHashSet<>(wfIds);
        allIdSet.addAll(bizIds);
        List<String> applyIds = new java.util.ArrayList<>(allIdSet);

        if (applyIds.isEmpty()) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }

        LocalDateTime fromDt = dateFrom == null ? null : dateFrom.atStartOfDay();
        LocalDateTime toExclusive = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay();

        long total = mapper.countMyDones(applyIds, keyword, allocDim, bizKind, fromDt, toExclusive);
        if (total == 0) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }
        int offset = (pageNo - 1) * pageSize;
        List<PerfAllocAdjustApply> applies = mapper.selectMyDones(
                applyIds, keyword, allocDim, bizKind, fromDt, toExclusive, offset, pageSize);

        List<String> pageKeys = applies.stream()
                .map(a -> BUSINESS_KEY_PREFIX + a.getId())
                .collect(Collectors.toList());
        Map<String, TaskRespDTO> metaMap = workflowTodoApi.findDoneTaskRespByBusinessKeys(empId, pageKeys);

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
        d.setCustType(a.getCustType());
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
