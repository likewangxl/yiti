package com.bank.branch.platform.yundun.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 违规管理统一分页参数。 */
@Data
public class ViolationPageQuery {
    @Min(value = 1, message = "pageNo 不能小于 1")
    private Integer pageNo = 1;
    @Min(value = 1, message = "pageSize 不能小于 1")
    @Max(value = 100, message = "pageSize 不能超过 100")
    private Integer pageSize = 20;

    /** 返回归一化页码。 */
    public int normalizedPageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    /** 返回归一化分页大小。 */
    public int normalizedPageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
