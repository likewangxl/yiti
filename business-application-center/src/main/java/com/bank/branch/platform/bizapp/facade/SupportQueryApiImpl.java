package com.bank.branch.platform.bizapp.facade;

import com.bank.branch.platform.bizapp.api.SupportQueryApi;
import com.bank.branch.platform.bizapp.api.dto.SupportQueryConditionDTO;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.common.web.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 中场支持申请对外分页/统计查询接口实现。
 * <p>
 * 实现 {@link SupportQueryApi} 接口，委托 {@link SupportRequestMapper} 执行分页与聚合查询。
 * 分页使用发起侧视图（SUPPORT）接口，通过 keyword/status/ownerOrgId 过滤。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportQueryApiImpl implements SupportQueryApi {

    private final SupportRequestMapper supportRequestMapper;

    /**
     * 分页查询支持申请（发起侧视图）。
     *
     * @param condition 查询条件（关键字、状态、机构、承接部门、分页参数）
     * @return 分页结果
     */
    @Override
    public PageResult<SupportRequestDTO> pageQuery(SupportQueryConditionDTO condition) {
        log.debug("[SupportQueryApiImpl.pageQuery] condition={}", condition);
        int pageNo = condition.getPageNo();
        int pageSize = condition.getPageSize();
        // 计算数据库偏移量（从第1页开始）
        int offset = (pageNo - 1) * pageSize;

        long total = supportRequestMapper.countPageForSupport(condition.getKeyword(),
                condition.getStatus(), condition.getOwnerOrgId());
        if (total == 0) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }

        List<SupportRequest> entities = supportRequestMapper.selectPageForSupport(
                condition.getKeyword(), condition.getStatus(),
                condition.getOwnerOrgId(), offset, pageSize);
        List<SupportRequestDTO> records = entities == null ? Collections.emptyList()
                : entities.stream().map(this::toDTO).collect(Collectors.toList());

        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 按创建人工号和时间范围统计已完成的支持申请数量。
     *
     * @param empId  创建人工号
     * @param start  统计开始时间
     * @param end    统计结束时间
     * @return 已完成申请数量
     */
    @Override
    public long countCompletedByCreator(String empId, LocalDateTime start, LocalDateTime end) {
        log.debug("[SupportQueryApiImpl.countCompletedByCreator] empId={}, start={}, end={}", empId, start, end);
        return supportRequestMapper.countCompletedByCreator(empId, start, end);
    }

    /**
     * 按承接人工号和时间范围统计已完成的支持申请数量。
     *
     * @param empId  承接办理人工号
     * @param start  统计开始时间
     * @param end    统计结束时间
     * @return 已完成申请数量
     */
    @Override
    public long countCompletedByAssignee(String empId, LocalDateTime start, LocalDateTime end) {
        log.debug("[SupportQueryApiImpl.countCompletedByAssignee] empId={}, start={}, end={}", empId, start, end);
        return supportRequestMapper.countCompletedByAssignee(empId, start, end);
    }

    // ------------------------------------------------------------------
    // 私有转换方法
    // ------------------------------------------------------------------

    /**
     * 将 SupportRequest 实体转换为对外 DTO。
     *
     * @param entity 支持申请实体
     * @return 对外 DTO
     */
    private SupportRequestDTO toDTO(SupportRequest entity) {
        SupportRequestDTO dto = new SupportRequestDTO();
        dto.setId(entity.getId());
        dto.setRequestNo(entity.getRequestNo());
        dto.setSubmitGroupId(entity.getSubmitGroupId());
        dto.setCustId(entity.getCustId());
        dto.setSourceTouchTaskId(entity.getSourceTouchTaskId());
        dto.setProductId(entity.getProductId());
        dto.setSupportDeptId(entity.getSupportDeptId());
        dto.setOtherDemand(entity.getOtherDemand());
        dto.setDispatchEmpId(entity.getDispatchEmpId());
        dto.setDispatchTime(entity.getDispatchTime());
        dto.setAssignedEmpId(entity.getAssignedEmpId());
        dto.setStatus(entity.getStatus());
        dto.setBusinessKey(entity.getBusinessKey());
        dto.setProcessInstanceId(entity.getProcessInstanceId());
        dto.setOwnerOrgId(entity.getOwnerOrgId());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedTime(entity.getCreatedTime());
        dto.setUpdatedBy(entity.getUpdatedBy());
        dto.setUpdatedTime(entity.getUpdatedTime());
        dto.setDeleted(entity.getDeleted());
        return dto;
    }
}
