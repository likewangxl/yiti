package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 红色引擎四维明细项只读投影。
 *
 * <p>当前隔离库没有名为 {@code RE_ITEM_CODE} 的物理表，启用明细项存放在平台字典表
 * {@code SYS_DICT} 的 {@code dict_type=RE_ITEM_CODE} 分组中。本投影只允许任务导出读取
 * 该分组，不在红色引擎复制或维护字典数据。</p>
 */
@Data
@TableName("SYS_DICT")
public class ReItemCode {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String dictType;
    private String dictCode;
    private String dictLabel;
    private String dictValue;
    private Integer sortOrder;
    private String status;
}
