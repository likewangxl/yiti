package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 绩效域员工选项 DTO：目标值等页面「按工号选人」的输入建议项。
 *
 * <p><b>为什么不复用 {@code /api/reports/employees/search}</b>：那个端点返回的 id 是
 * {@code UserDTO.empId}，而 {@code UserFacade} 里是 {@code dto.setEmpId(u.getUserId())}——
 * 即 {@code PT_USER.USER_ID}（短代理键，如 {@code E40001}）。但绩效目标值的
 * {@code subject_id} 存的是 {@code PT_USER.USERNAME}（工号，如 {@code finance_zhou}），
 * 两者不是同一列。直接复用会让用户选中的人在提交时被后端判定为「员工不存在」。
 * 故本 DTO 明确只暴露 {@link #username}（工号），不暴露 USER_ID，从类型上杜绝再次混淆。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PerfEmpOptionDTO {

    /**
     * 员工工号 = {@code PT_USER.USERNAME}，也就是目标值 {@code subject_id} 应当写入的值。
     * <b>不是</b> {@code PT_USER.USER_ID}。
     */
    private String username;

    /** 员工姓名（{@code PT_USER.USERCHNNAME}） */
    private String displayName;

    /** 主机构名称，用于同名员工的区分展示，可能为空 */
    private String orgName;
}
