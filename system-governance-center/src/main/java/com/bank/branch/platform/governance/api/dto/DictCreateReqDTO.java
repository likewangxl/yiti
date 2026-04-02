package com.bank.branch.platform.governance.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字典新增请求DTO
 * <p>
 * 用于新增字典项时的入参校验，dictType + dictCode 构成唯一约束。
 * </p>
 */
@Data
public class DictCreateReqDTO {

    /** 字典类型编码（大写字母+下划线） */
    @NotBlank(message = "字典类型不能为空")
    @Size(max = 100, message = "字典类型长度不能超过100")
    @Pattern(regexp = "^[A-Z_]+$", message = "字典类型只允许大写字母和下划线")
    private String dictType;

    /** 字典项编码 */
    @NotBlank(message = "字典编码不能为空")
    @Size(max = 100, message = "字典编码长度不能超过100")
    private String dictCode;

    /** 字典标签（用于前端显示） */
    @NotBlank(message = "字典标签不能为空")
    @Size(max = 200, message = "字典标签长度不能超过200")
    private String dictLabel;

    /** 字典值（用于存储/传输） */
    @NotBlank(message = "字典值不能为空")
    @Size(max = 500, message = "字典值长度不能超过500")
    private String dictValue;

    /** 排序号，默认为0 */
    @Min(value = 0, message = "排序号不能为负数")
    private Integer sortOrder;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
