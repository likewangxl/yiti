package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 人员标签新增成员请求（支持一次添加多个工号）。 */
@Data
public class PersonTagMemberAddReqDTO {

    /** 员工工号列表（PT_USER.USERNAME 口径）. */
    @NotEmpty(message = "工号列表不能为空")
    private List<String> usernames;
}
