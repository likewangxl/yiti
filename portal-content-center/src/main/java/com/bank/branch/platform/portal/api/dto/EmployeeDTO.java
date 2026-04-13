package com.bank.branch.platform.portal.api.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工通讯录信息传输对象（不可变）
 *
 * <p>跨模块 API 返回类型，由 AddressBookApi 对外提供。
 * mobile 字段为脱敏后的手机号（如 138****8888）。</p>
 */
@Value
@Builder
public class EmployeeDTO {

    /** 员工工号 */
    String empId;

    /** 员工姓名 */
    String empName;

    /** 手机号（脱敏后） */
    String mobile;

    /** 邮箱 */
    String email;

    /** 机构编码 */
    String orgCode;

    /** 机构名称 */
    String orgName;

    /** 岗位代码 */
    String position;

    /** 岗位显示名（字典翻译后，Service 层填充） */
    String positionDesc;

    /** 自我描述 */
    String selfDesc;

    /** 负责产品ID列表 */
    List<String> responsibleProductIds;

    /** 状态 ACTIVE/RESIGNED */
    String status;

    /** 最后更新时间 */
    LocalDateTime updatedTime;
}
