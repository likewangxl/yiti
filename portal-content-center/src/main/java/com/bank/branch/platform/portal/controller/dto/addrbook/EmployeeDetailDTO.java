package com.bank.branch.platform.portal.controller.dto.addrbook;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通讯录员工详情 DTO（C.2 详情响应）
 *
 * <p>在 EmployeeDTO 基础上扩展维护人信息、编辑权限标识和负责产品简要列表。</p>
 */
@Data
public class EmployeeDetailDTO {

    /** 员工工号 */
    private String empId;

    /** 员工姓名 */
    private String empName;

    /** 手机号（脱敏后） */
    private String mobile;

    /** 邮箱 */
    private String email;

    /** 机构编码 */
    private String orgCode;

    /** 机构名称 */
    private String orgName;

    /** 岗位代码 */
    private String position;

    /** 岗位显示名 */
    private String positionDesc;

    /** 自我描述 */
    private String selfDesc;

    /** 负责产品ID列表 */
    private List<String> responsibleProductIds;

    /** 状态 ACTIVE/RESIGNED */
    private String status;

    /** 最后更新时间 */
    private LocalDateTime updatedTime;

    // ===== 详情页额外字段 =====

    /** 维护人工号 */
    private String maintainerEmpId;

    /** 维护人姓名 */
    private String maintainerEmpName;

    /** 当前用户是否可编辑 */
    private Boolean canEdit;

    /** 负责产品简要列表 */
    private List<ProductBriefDTO> responsibleProducts;
}
