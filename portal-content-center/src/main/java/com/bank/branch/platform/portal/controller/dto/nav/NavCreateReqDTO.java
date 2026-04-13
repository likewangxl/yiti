package com.bank.branch.platform.portal.controller.dto.nav;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 导航新增请求 DTO（B.1）
 */
@Data
public class NavCreateReqDTO {

    /** 导航名称 */
    @NotBlank(message = "导航名称不能为空")
    @Size(max = 100, message = "导航名称最长100字符")
    private String navName;

    /** 导航URL */
    @NotBlank(message = "导航URL不能为空")
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
}
