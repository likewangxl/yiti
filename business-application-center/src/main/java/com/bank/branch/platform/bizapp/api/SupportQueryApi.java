package com.bank.branch.platform.bizapp.api;

import com.bank.branch.platform.bizapp.api.dto.SupportQueryConditionDTO;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;
import com.bank.branch.platform.common.web.PageResult;

import java.time.LocalDateTime;

/**
 * 中台支持申请对外分页/统计查询接口。
 * <p>
 * 供报表分析中心、绩效计算中心等模块使用。
 * 实现类位于 {@code facade/SupportQueryApiImpl}。
 * </p>
 */
public interface SupportQueryApi {

    /**
     * 分页查询支持申请（发起侧视图）。
     *
     * @param condition 查询条件（关键字、状态、机构、承接部门、分页参数）
     * @return 分页结果
     */
    PageResult<SupportRequestDTO> pageQuery(SupportQueryConditionDTO condition);

    /**
     * 按创建人工号和时间范围统计已完成的支持申请数量。
     *
     * @param empId      创建人工号
     * @param startTime  统计开始时间（含）
     * @param endTime    统计结束时间（含）
     * @return 已完成申请数量
     */
    long countCompletedByCreator(String empId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按承接人工号和时间范围统计已完成的支持申请数量。
     *
     * @param empId      承接办理人工号
     * @param startTime  统计开始时间（含）
     * @param endTime    统计结束时间（含）
     * @return 已完成申请数量
     */
    long countCompletedByAssignee(String empId, LocalDateTime startTime, LocalDateTime endTime);
}
