package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.api.dto.TouchTaskDTO;
import com.bank.branch.platform.customer.api.dto.TouchTaskSummaryDTO;

import java.util.List;
import java.util.Optional;

/**
 * 触达任务对外查询接口（契约 §5）。
 * <p>
 * 供其他模块查询触达任务状态使用，只提供只读查询，不暴露创建/更新操作。
 * 返回值均为 DTO，不暴露实体；集合方法在无数据时返回空集合。
 * </p>
 *
 * @author customer-marketing-center
 * @since V1.0
 */
public interface TouchTaskQueryApi {

    /**
     * 获取触达任务详情。
     *
     * @param taskId 任务ID
     * @return 触达任务 DTO，不存在时返回 {@link Optional#empty()}
     */
    Optional<TouchTaskDTO> getTouchTask(String taskId);

    /**
     * 按业务键查询触达任务（格式 TOUCH:{taskId}）。
     *
     * @param businessKey 工作流业务键
     * @return 触达任务 DTO，不存在时返回 {@link Optional#empty()}
     */
    Optional<TouchTaskDTO> getTouchTaskByBusinessKey(String businessKey);

    /**
     * 查询员工的触达任务列表（status 可空 → 全状态）。
     *
     * @param empId  员工工号
     * @param status 任务状态（PENDING/IN_PROGRESS/SUCCESS/CANCELLED），为 null 时返回全状态
     * @return 触达任务 DTO 列表，无数据时返回空列表
     */
    List<TouchTaskDTO> getEmpTouchTasks(String empId, String status);

    /**
     * 统计员工进行中触达任务数（PENDING + IN_PROGRESS）。
     * <p>
     * 缓存：cust:emp:{empId}:touch:running，TTL 1min。
     * </p>
     *
     * @param empId 员工工号
     * @return 进行中任务数量
     */
    int countRunningTouchTasks(String empId);

    /**
     * 查询客户的触达历史（按创建时间倒序，含所有状态）。
     *
     * @param custId 客户ID
     * @return 触达任务 DTO 列表，无数据时返回空列表
     */
    List<TouchTaskDTO> getCustomerTouchHistory(String custId);

    /**
     * 查询客户在指定机构的触达历史。
     *
     * @param custId   客户ID
     * @param orgCode  机构代码
     * @return 触达任务 DTO 列表，无数据时返回空列表
     */
    List<TouchTaskDTO> getCustomerTouchHistoryByOrg(String custId, String orgCode);

    /**
     * 校验客户是否已完成首次触达（FIRST_TOUCH 且 SUCCESS 且同机构）。
     *
     * @param custId  客户ID
     * @param orgCode 机构代码
     * @return true 表示已完成首次触达
     */
    boolean hasCompletedFirstTouch(String custId, String orgCode);

    /**
     * 统计机构触达汇总（startDate/endDate 为 yyyy-MM-dd）。
     *
     * @param orgCode   机构代码
     * @param startDate 统计开始日期（格式 yyyy-MM-dd）
     * @param endDate   统计结束日期（格式 yyyy-MM-dd）
     * @return 触达任务汇总 DTO
     */
    TouchTaskSummaryDTO getOrgTouchSummary(String orgCode, String startDate, String endDate);
}
