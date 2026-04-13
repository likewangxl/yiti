package com.bank.branch.platform.portal.controller.dto.nav;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 导航更新请求 DTO（B.2）
 *
 * <p>所有字段均为可选，仅传入需要更新的字段。</p>
 */
@Data
public class NavUpdateReqDTO {

    /** 导航名称 */
    @Size(max = 100, message = "导航名称最长100字符")
    private String navName;

    /** 导航URL */
    @Size(max = 500, message = "导航URL最长500字符")
    @Pattern(regexp = "^https?://.+", message = "导航URL必须以 http:// 或 https:// 开头")
    private String navUrl;

    /** 图标 */
    @Size(max = 100, message = "图标标识最长100字符")
    private String navIcon;

    /** 导航分类 */
    @Size(max = 50, message = "导航分类最长50字符")
    private String navCategory;

    /** 排序号 */
    @Min(value = 0, message = "排序号不能为负数")
    private Integer sortOrder;

    /** 状态 ACTIVE/DISABLED */
    @Pattern(regexp = "ACTIVE|DISABLED", message = "状态只能为 ACTIVE 或 DISABLED")
    private String status;
}
