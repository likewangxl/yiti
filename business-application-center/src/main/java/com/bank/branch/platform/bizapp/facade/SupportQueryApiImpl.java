package com.bank.branch.platform.bizapp.facade;

import com.bank.branch.platform.bizapp.api.SupportQueryApi;
import com.bank.branch.platform.bizapp.api.converter.SupportRequestDTOConverter;
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

/**
 * 中台支持申请对外分页/统计查询接口实现。
 * <p>
 * 实现 {@link SupportQueryApi} 接口，委托 {@link SupportRequestMapper} 执行分页与聚合查询。
 * 所有查询结果通过 {@link SupportRequestDTOConverter} 转换，确保不暴露 deleted 字段且补充冗余展示字段。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportQueryApiImpl implements SupportQueryApi {

    private final SupportRequestMapper supportRequestMapper;
    private final SupportRequestDTOConverter supportRequestDTOConverter;

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
                // 使用批量转换避免 N+1
                : supportRequestDTOConverter.toDTOList(entities);

        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 按创建人工号和时间范围统计已完成的支持申请数量。
     *
     * @param empId      创建人工号
     * @param startTime  统计开始时间
     * @param endTime    统计结束时间
     * @return 已完成申请数量
     */
    @Override
    public long countCompletedByCreator(String empId, LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("[SupportQueryApiImpl.countCompletedByCreator] empId={}, startTime={}, endTime={}", empId, startTime, endTime);
        return supportRequestMapper.countCompletedByCreator(empId, startTime, endTime);
    }

    /**
     * 按承接人工号和时间范围统计已完成的支持申请数量。
     *
     * @param empId      承接办理人工号
     * @param startTime  统计开始时间
     * @param endTime    统计结束时间
     * @return 已完成申请数量
     */
    @Override
    public long countCompletedByAssignee(String empId, LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("[SupportQueryApiImpl.countCompletedByAssignee] empId={}, startTime={}, endTime={}", empId, startTime, endTime);
        return supportRequestMapper.countCompletedByAssignee(empId, startTime, endTime);
    }
}
