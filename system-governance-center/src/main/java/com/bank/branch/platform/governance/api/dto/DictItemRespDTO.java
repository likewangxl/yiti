package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 字典项响应DTO
 * 对应文档 A.2/A.3/A.4 的响应类型
 */
@Data
public class DictItemRespDTO {
    /** 字典ID（32位UUID） */
    private String id;
    /** 字典类型 */
    private String dictType;
    /** 字典编码 */
    private String dictCode;
    /** 字典标签（用于前端显示） */
    private String dictLabel;
    /** 字典值（用于存储/传输） */
    private String dictValue;
    /** 排序号 */
    private Integer sortOrder;
    /** 状态 ACTIVE/DISABLED */
    private String status;
}