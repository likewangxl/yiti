package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
import com.bank.branch.platform.customer.api.converter.TouchTaskDTOConverter;
import com.bank.branch.platform.customer.api.dto.TouchTaskDTO;
import com.bank.branch.platform.customer.api.dto.TouchTaskSummaryDTO;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 触达任务对外查询接口实现（契约 §5，8 个方法）。
 * <p>
 * 实现 {@link TouchTaskQueryApi} 接口，直接委托 {@link TouchTaskMapper} 完成只读查询。
 * 所有返回值通过 {@link TouchTaskDTOConverter} 转换为 DTO，避免暴露实体给外部模块。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TouchTaskQueryApiImpl implements TouchTaskQueryApi {

    private final TouchTaskMapper touchTaskMapper;

    /**
     * 获取触达任务详情。
     *
     * @param taskId 任务ID
     * @return 触达任务 DTO Optional；不存在时返回 empty
     */
    @Override
    public Optional<TouchTaskDTO> getTouchTask(String taskId) {
        log.debug("[TouchTaskQueryApiImpl.getTouchTask] taskId={}", taskId);
        return Optional.ofNullable(TouchTaskDTOConverter.toDTO(touchTaskMapper.selectById(taskId)));
    }

    /**
     * 按业务键查询触达任务（格式 TOUCH:{taskId}）。
     *
     * @param businessKey 工作流业务键
     * @return 触达任务 DTO Optional；不存在时返回 empty
     */
    @Override
    public Optional<TouchTaskDTO> getTouchTaskByBusinessKey(String businessKey) {
        log.debug("[TouchTaskQueryApiImpl.getTouchTaskByBusinessKey] businessKey={}", businessKey);
        if (businessKey == null || !businessKey.startsWith("TOUCH:")) {
            return Optional.empty();
        }
        String taskId = businessKey.substring("TOUCH:".length());
        if (taskId.isBlank() || taskId.contains(":")) {
            return Optional.empty();
        }
        return Optional.ofNullable(TouchTaskDTOConverter.toDTO(touchTaskMapper.selectById(taskId)));
    }

    /**
     * 查询员工的触达任务列表（status 可空 → 全状态）。
     *
     * @param empId  员工工号
     * @param status 任务状态，为 null 时查询全状态
     * @return 触达任务 DTO 列表
     */
    @Override
    public List<TouchTaskDTO> getEmpTouchTasks(String empId, String status) {
        log.debug("[TouchTaskQueryApiImpl.getEmpTouchTasks] empId={}, status={}", empId, status);
        if (status != null) {
            return TouchTaskDTOConverter.toDTOList(touchTaskMapper.selectByEmpAndStatus(empId, status));
        } else {
            return TouchTaskDTOConverter.toDTOList(touchTaskMapper.selectByEmp(empId));
        }
    }

    /**
     * 统计员工进行中触达任务数（PENDING + IN_PROGRESS）。
     *
     * @param empId 员工工号
     * @return 进行中任务总数
     */
    @Override
    public int countRunningTouchTasks(String empId) {
        log.debug("[TouchTaskQueryApiImpl.countRunningTouchTasks] empId={}", empId);
        Long count = touchTaskMapper.countByEmpAndStatuses(empId, List.of("PENDING", "IN_PROGRESS"));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 查询客户的触达历史（按创建时间倒序，含所有状态）。
     *
     * @param custId 客户ID
     * @return 触达任务 DTO 列表
     */
    @Override
    public List<TouchTaskDTO> getCustomerTouchHistory(String custId) {
        log.debug("[TouchTaskQueryApiImpl.getCustomerTouchHistory] custId={}", custId);
        return TouchTaskDTOConverter.toDTOList(touchTaskMapper.selectByCustOrderByCreatedDesc(custId));
    }

    /**
     * 查询客户在指定机构的触达历史。
     *
     * @param custId  客户ID
     * @param orgCode 机构代码
     * @return 触达任务 DTO 列表
     */
    @Override
    public List<TouchTaskDTO> getCustomerTouchHistoryByOrg(String custId, String orgCode) {
        log.debug("[TouchTaskQueryApiImpl.getCustomerTouchHistoryByOrg] custId={}, orgCode={}", custId, orgCode);
        return TouchTaskDTOConverter.toDTOList(touchTaskMapper.selectByCustAndOrg(custId, orgCode));
    }

    /**
     * 校验客户是否已完成首次触达（FIRST_TOUCH 且 SUCCESS 且同机构）。
     *
     * @param custId  客户ID
     * @param orgCode 机构代码
     * @return true 表示已完成首次触达
     */
    @Override
    public boolean hasCompletedFirstTouch(String custId, String orgCode) {
        log.debug("[TouchTaskQueryApiImpl.hasCompletedFirstTouch] custId={}, orgCode={}", custId, orgCode);
        Long count = touchTaskMapper.countFirstTouchSuccess(custId, orgCode);
        return count != null && count > 0L;
    }

    /**
     * 统计机构触达汇总（startDate/endDate 为 yyyy-MM-dd）。
     *
     * @param orgCode   机构代码
     * @param startDate 统计开始日期
     * @param endDate   统计结束日期
     * @return 触达任务汇总 DTO
     */
    @Override
    public TouchTaskSummaryDTO getOrgTouchSummary(String orgCode, String startDate, String endDate) {
        log.debug("[TouchTaskQueryApiImpl.getOrgTouchSummary] orgCode={}, startDate={}, endDate={}",
                orgCode, startDate, endDate);
        TouchTaskSummaryDTO dto = new TouchTaskSummaryDTO();
        dto.setOrgId(orgCode);
        dto.setTotalCount(touchTaskMapper.countByOrgBetween(orgCode, startDate, endDate, null));
        dto.setPendingCount(touchTaskMapper.countByOrgBetween(orgCode, startDate, endDate, "PENDING"));
        dto.setInProgressCount(touchTaskMapper.countByOrgBetween(orgCode, startDate, endDate, "IN_PROGRESS"));
        dto.setSuccessCount(touchTaskMapper.countByOrgBetween(orgCode, startDate, endDate, "SUCCESS"));
        dto.setCancelledCount(touchTaskMapper.countByOrgBetween(orgCode, startDate, endDate, "CANCELLED"));
        dto.setSlaWarningCount(touchTaskMapper.countSlaWarningByOrgBetween(orgCode, startDate, endDate));
        dto.setAvgDurationHours(touchTaskMapper.avgDurationHoursByOrgBetween(orgCode, startDate, endDate));
        return dto;
    }
}
