package com.bank.branch.platform.customer.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理后台批量分配触达任务请求 DTO。
 * <p>
 * 用于将多个触达任务重新分配给指定员工，
 * 仅允许对 PENDING 或 IN_PROGRESS 状态的任务进行重分配。
 * </p>
 */
@Data
public class AdminBatchAssignReqDTO {

    /**
     * 待分配的任务 ID 列表（至少 1 个，最多 100 个）。
     */
    @NotEmpty(message = "任务ID列表不能为空")
    @Size(max = 100, message = "单次批量分配最多 100 个任务")
    private List<String> taskIds;

    /**
     * 新的执行人员工工号。
     */
    @NotBlank(message = "新执行人工号不能为空")
    private String newAssigneeEmpId;

    /**
     * 批量改派原因，用于高危操作审计，不能为空或空白。
     */
    @NotBlank(message = "批量分配原因不能为空")
    @Size(max = 500, message = "批量分配原因不能超过 500 个字符")
    private String reason;
}
