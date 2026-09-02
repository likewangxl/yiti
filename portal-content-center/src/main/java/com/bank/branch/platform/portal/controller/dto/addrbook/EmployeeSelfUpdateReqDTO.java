package com.bank.branch.platform.portal.controller.dto.addrbook;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 当前用户自助维护通讯录请求。
 *
 * <p>请求故意不接收用户 ID，也不包含姓名、机构、岗位或状态等主数据字段；
 * 当前用户由认证上下文确定，只允许维护电话、邮箱和负责产品关系。</p>
 */
@Data
public class EmployeeSelfUpdateReqDTO {

    /** 电话号码；空字符串由服务层按清空处理。 */
    @Size(max = 20, message = "手机号最长20字符")
    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    private String mobile;

    /** 邮箱；空字符串由服务层按清空处理。 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱最长100字符")
    private String email;

    /** 负责产品 ID 列表；传空列表表示清空关系。 */
    private List<String> responsibleProductIds;
}
