package com.bank.branch.platform.bizapp.facade;

import com.bank.branch.platform.bizapp.api.SupportApi;
import com.bank.branch.platform.bizapp.api.converter.SupportRequestDTOConverter;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 中场支持申请对外查询接口实现。
 * <p>
 * 实现 {@link SupportApi} 接口，直接委托 {@link SupportRequestMapper} 完成只读查询。
 * 所有查询结果通过 {@link SupportRequestDTOConverter} 转换为跨模块传输对象，
 * converter 负责补充 custName/productName/supportDeptName 冗余字段并确保不暴露 deleted 字段。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportApiImpl implements SupportApi {

    /** 批量查询入参上限，超限直接拒绝，避免大查询打垮数据库（依据文档契约 §7.1）。 */
    private static final int MAX_BATCH_SIZE = 500;

    private final SupportRequestMapper supportRequestMapper;
    private final SupportRequestDTOConverter supportRequestDTOConverter;

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
        return Optional.ofNullable(entity).map(supportRequestDTOConverter::toDTO);
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
        return Optional.ofNullable(entity).map(supportRequestDTOConverter::toDTO);
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
        // 使用批量转换避免 N+1
        return supportRequestDTOConverter.toDTOList(entities);
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
        // 使用批量转换避免 N+1
        return supportRequestDTOConverter.toDTOList(entities);
    }

    /**
     * 批量按ID查询支持申请。
     * <p>
     * 依据文档契约 §7.1，入参 ID 列表上限为 500 条，超限抛 IllegalArgumentException。
     * </p>
     *
     * @param requestIds 申请ID列表，不可超过 {@value #MAX_BATCH_SIZE} 条
     * @return 支持申请 DTO 列表，无数据时返回空列表
     * @throws IllegalArgumentException 当 requestIds 超过 {@value #MAX_BATCH_SIZE} 条时
     */
    @Override
    public List<SupportRequestDTO> getSupportRequestBatch(List<String> requestIds) {
        log.debug("[SupportApiImpl.getSupportRequestBatch] count={}", requestIds == null ? 0 : requestIds.size());
        if (requestIds == null || requestIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 依据文档契约 §7.1：批量接口入参上限 MAX_BATCH_SIZE 条，超限直接拒绝，避免大查询打垮数据库
        if (requestIds.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "requestIds size cannot exceed " + MAX_BATCH_SIZE + ", actual: " + requestIds.size());
        }
        List<SupportRequest> entities = supportRequestMapper.selectByIds(requestIds);
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        // 使用批量转换避免 N+1
        return supportRequestDTOConverter.toDTOList(entities);
    }
}
