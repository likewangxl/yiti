package com.bank.branch.platform.portal.controller.dto.addrbook;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 通讯录员工更新请求 DTO（C.3）
 *
 * <p>所有字段均为可选，仅传入需要更新的字段。</p>
 */
@Data
public class EmployeeUpdateReqDTO {

    /** 手机号 */
    @Size(max = 20, message = "手机号最长20字符")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String mobile;

    /** 邮箱 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱最长100字符")
    private String email;

    /** 岗位 */
    @Size(max = 100, message = "岗位最长100字符")
    private String position;

    /** 自我描述 */
    @Size(max = 1000, message = "自我描述最长1000字符")
    private String selfDesc;

    /** 负责产品ID列表 */
    private List<String> responsibleProductIds;
}
