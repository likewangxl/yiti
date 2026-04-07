package com.bank.branch.platform.governance.api.dto;

import lombok.Data;

/**
 * 字典类型响应DTO
 * <p>
 * 用于 A.1 字典类型列表查询接口（GET /api/sys/dicts），
 * 返回按字典类型聚合的汇总信息。
 * </p>
 */
@Data
public class DictTypeRespDTO {

    /** 字典类型编码 */
    private String dictType;

    /** 字典类型显示名称（取该类型下首条记录的 remark 或 dictType） */
    private String dictTypeLabel;

    /** 该类型下字典项数量 */
    private Integer itemCount;

    /** 状态（取该类型下是否有 ACTIVE 项：ACTIVE/DISABLED） */
    private String status;
}
