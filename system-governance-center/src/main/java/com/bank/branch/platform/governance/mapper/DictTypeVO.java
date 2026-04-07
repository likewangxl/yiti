package com.bank.branch.platform.governance.mapper;

import lombok.Data;

/**
 * 字典类型聚合视图对象（用于 mapper 查询结果）。
 */
@Data
public class DictTypeVO {

    /** 字典类型编码 */
    private String dictType;

    /** 字典类型标签（remark 或 dictType） */
    private String remark;

    /** 该类型下所有字典项数量 */
    private Long totalCount;

    /** 该类型下启用状态字典项数量 */
    private Long activeCount;
}
