package com.bank.branch.platform.bizapp.facade;

import com.bank.branch.platform.bizapp.api.SupportApi;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 中场支持申请对外查询接口实现。
 * <p>
 * 实现 {@link SupportApi} 接口，直接委托 {@link SupportRequestMapper} 完成只读查询。
 * 所有查询结果通过私有 {@code toDTO()} 方法转换为跨模块传输对象。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportApiImpl implements SupportApi {

    private final SupportRequestMapper supportRequestMapper;

    /**
     * 按申请ID查询支持申请。
     *
     * @param requestId 申请ID
     * @return 支持申请 DTO，不存在时返回空 Optional
     */
    @Override
    public Optional<SupportRequestDTO> getSupportRequest(String requestId) {
        log.debug("[SupportApiImpl.getSupportRequest] requestId={}", requestId);
        SupportRequest entity = supportRequestMapper.selectById(requestId);
        return Optional.ofNullable(entity).map(this::toDTO);
    }

    /**
     * 按流程业务键查询支持申请。
     *
     * @param businessKey 业务键，格式为 SUPPORT:{id}
     * @return 支持申请 DTO，不存在时返回空 Optional
     */
    @Override
    public Optional<SupportRequestDTO> getSupportRequestByBusinessKey(String businessKey) {
        log.debug("[SupportApiImpl.getSupportRequestByBusinessKey] businessKey={}", businessKey);
        SupportRequest entity = supportRequestMapper.selectByBusinessKey(businessKey);
        return Optional.ofNullable(entity).map(this::toDTO);
    }

    /**
     * 查询客户的支持申请历史列表。
     *
     * @param custId 客户ID
     * @return 支持申请 DTO 列表，无数据时返回空列表
     */
    @Override
    public List<SupportRequestDTO> getCustomerSupportHistory(String custId) {
        log.debug("[SupportApiImpl.getCustomerSupportHistory] custId={}", custId);
        List<SupportRequest> entities = supportRequestMapper.selectByCustId(custId);
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * 查询同一批次提交分组下的所有支持申请。
     *
     * @param submitGroupId 提交分组ID
     * @return 支持申请 DTO 列表，无数据时返回空列表
     */
    @Override
    public List<SupportRequestDTO> getBySubmitGroup(String submitGroupId) {
        log.debug("[SupportApiImpl.getBySubmitGroup] submitGroupId={}", submitGroupId);
        List<SupportRequest> entities = supportRequestMapper.selectBySubmitGroupId(submitGroupId);
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * 批量按ID查询支持申请。
     *
     * @param requestIds 申请ID列表
     * @return 支持申请 DTO 列表，无数据时返回空列表
     */
    @Override
    public List<SupportRequestDTO> getSupportRequestBatch(List<String> requestIds) {
        log.debug("[SupportApiImpl.getSupportRequestBatch] count={}", requestIds == null ? 0 : requestIds.size());
        if (requestIds == null || requestIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SupportRequest> entities = supportRequestMapper.selectByIds(requestIds);
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // 私有转换方法
    // ------------------------------------------------------------------

    /**
     * 将 SupportRequest 实体转换为对外 DTO。
     * 手动逐字段赋值，避免引入额外的 mapping 框架依赖。
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
