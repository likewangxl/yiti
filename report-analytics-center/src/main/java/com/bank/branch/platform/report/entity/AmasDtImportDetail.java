package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 数据导入明细（单元格数据） amas_dt_import_details 贫血实体（只读）.
 *
 * <p>每行 = 一个单元格：{@code DT_BATCHNUM}+{@code DT_TITLE_NO} 定位到列，{@code DT_DETAILS} 是值，
 * {@code DT_FLAG} 标识同一逻辑行，{@code DT_DETAILS_SNO} 全局序。按 DT_FLAG 透视成行。
 * 表无物理主键，{@code DT_DETAILS_SNO} 仅作 MP 占位主键。</p>
 */
@Data
@TableName("amas_dt_import_details")
public class AmasDtImportDetail {

    /** 序号（占位主键，仅供 MP）. */
    @TableId(value = "DT_DETAILS_SNO", type = IdType.INPUT)
    private Long dtDetailsSno;

    /** 数据批次号. */
    private String dtBatchnum;

    /** 表头编号（关联 sup.DT_TITLE_NO）. */
    private String dtTitleNo;

    /** 数据（单元格值）. */
    private String dtDetails;

    /** 行标识（同一逻辑行的多个单元格共用）. */
    private String dtFlag;
}
