package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 字典项传输对象
 * <p>
 * 用于跨模块传递字典数据，包含字典项的核心展示与存储字段。
 * </p>
 */
@Data
public class DictItemDTO {

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
}
