package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustTodoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 「我的申请」查询服务。
 * <p>createdBy 由 controller 从 CurrentUserApi 取硬注入；service 第一个参数 empId 始终是当前用户。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocAdjustMineService {

    private final PerfAllocAdjustTodoMapper mapper;

    public PageResult<AdjustTodoRespDTO> listMyApplies(String empId,
                                                       String keyword,
                                                       String allocDim,
                                                       String bizKind,
                                                       String status,
                                                       LocalDate dateFrom,
                                                       LocalDate dateTo,
                                                       int pageNo,
                                                       int pageSize) {
        log.debug("[AllocAdjustMineService.listMyApplies] empId={}, kw={}, dim={}, kind={}, status={}, from={}, to={}, page={}/{}",
                empId, keyword, allocDim, bizKind, status, dateFrom, dateTo, pageNo, pageSize);

        LocalDateTime fromDt = dateFrom == null ? null : dateFrom.atStartOfDay();
        LocalDateTime toExclusive = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay();

        long total = mapper.countMyApplies(empId, keyword, allocDim, bizKind, status, fromDt, toExclusive);
        if (total == 0) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }
        int offset = (pageNo - 1) * pageSize;
        List<PerfAllocAdjustApply> applies = mapper.selectMyApplies(
                empId, keyword, allocDim, bizKind, status, fromDt, toExclusive, offset, pageSize);

        List<AdjustTodoRespDTO> records = applies.stream()
                .map(this::mergeToDto)
                .collect(Collectors.toList());

        return PageResult.of(pageNo, pageSize, total, records);
    }

    private AdjustTodoRespDTO mergeToDto(PerfAllocAdjustApply a) {
        AdjustTodoRespDTO d = new AdjustTodoRespDTO();
        d.setId(a.getId());
        d.setApplyNo(a.getApplyNo());
        d.setCustId(a.getCustId());
        d.setAllocDim(a.getAllocDim());
        d.setBizKind(a.getBizKind());
        d.setOwnerOrgId(a.getOwnerOrgId());
        d.setCreatedBy(a.getCreatedBy());
        d.setCreatedTime(a.getCreatedTime());
        d.setBusinessKey(a.getBusinessKey());
        d.setStatus(a.getStatus());
        // task 字段不填（mine 不显示 workflow 信息）
        return d;
    }
}
