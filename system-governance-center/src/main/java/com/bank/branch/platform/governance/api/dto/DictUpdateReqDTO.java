package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典更新请求DTO
 * <p>
 * 用于部分更新字典项，所有字段均为可选，仅更新非 null 字段。
 * </p>
 */
@Data
public class DictUpdateReqDTO {

    /** 字典标签（用于前端显示） */
    @Size(max = 200, message = "字典标签长度不能超过200")
    private String dictLabel;

    /** 字典值（用于存储/传输） */
    @Size(max = 500, message = "字典值长度不能超过500")
    private String dictValue;

    /** 排序号 */
    @Min(value = 0, message = "排序号不能为负数")
    private Integer sortOrder;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
