package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 字典响应DTO
 * <p>
 * 用于管理端字典详情返回，包含完整的字典项信息。
 * </p>
 */
@Data
public class DictRespDTO {

    /** 字典ID */
    private String id;

    /** 字典类型编码 */
    private String dictType;

    /** 字典项编码 */
    private String dictCode;

    /** 字典标签（用于前端显示） */
    private String dictLabel;

    /** 字典值（用于存储/传输） */
    private String dictValue;

    /** 排序号 */
    private Integer sortOrder;

    /** 状态：ACTIVE-启用, DISABLED-禁用 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间（ISO 8601） */
    private String createdTime;

    /** 更新时间（ISO 8601） */
    private String updatedTime;
}
