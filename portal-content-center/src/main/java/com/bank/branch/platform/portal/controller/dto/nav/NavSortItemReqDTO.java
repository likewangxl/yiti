package com.bank.branch.platform.portal.controller.dto.nav;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 导航排序项 DTO（B.3 批量排序）
 */
@Data
public class NavSortItemReqDTO {

    /** 导航ID */
    @NotNull(message = "导航ID不能为空")
    private String id;

    /** 排序号 */
    @NotNull(message = "排序号不能为空")
    @Min(value = 0, message = "排序号不能为负数")
    private Integer sortOrder;
}
