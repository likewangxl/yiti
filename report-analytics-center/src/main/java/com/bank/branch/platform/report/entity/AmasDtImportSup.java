package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 数据导入信息（表头定义） amas_dt_import_sup 贫血实体（只读，历史数据查询 - 数据导入查询用）.
 *
 * <p>每行 = 一个批次（{@code DT_BATCHNUM}）下的一个列定义：
 * {@code DT_TITLE_NO} 列编号、{@code DT_TITLE_NAME} 列名、{@code DT_TITLE_SNO} 列序。
 * 实际单元格数据在 {@link AmasDtImportDetail}。表无物理主键，{@code DT_BATCHNUM} 仅作 MP 占位主键，不做单条 CRUD。</p>
 */
@Data
@TableName("amas_dt_import_sup")
public class AmasDtImportSup {

    /** 数据批次号（占位主键，仅供 MP，不做单条 CRUD）. */
    @TableId(value = "DT_BATCHNUM", type = IdType.INPUT)
    private String dtBatchnum;

    /** 数据名称. */
    private String dtName;

    /** 表头编号. */
    private String dtTitleNo;

    /** 表头序号. */
    private Long dtTitleSno;

    /** 表头名称. */
    private String dtTitleName;

    /** 创建时间. */
    private String dtCreateTime;

    /** 创建人工号. */
    private String dtCreateUsername;

    /** 创建人姓名. */
    private String dtCreateFullname;

    /** 是否展示：1,展示；2,隐藏. */
    private String dtIsshow;

    /** 说明. */
    private String dtExplain;
}
