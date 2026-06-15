package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 业绩调整分配信息 AMAS_PERFORMANCE_ALLOCATION 贫血实体（只读报表用）.
 *
 * <p>一个业绩调整编号对应多行分配人记录，以 {@code PERF_ADJUST_NO} 关联主表。
 * DDL 无物理主键且 PERF_ADJUST_NO 非唯一（一对多），@TableId(INPUT) 仅为满足
 * MyBatis-Plus 实体要求；本表只通过 {@code selectList(条件)} 读取，不做 ById 操作。</p>
 */
@Data
@TableName("AMAS_PERFORMANCE_ALLOCATION")
public class AmasPerformanceAllocation {

    /** 业绩调整编号（关联主表，非唯一）. */
    @TableId(value = "PERF_ADJUST_NO", type = IdType.INPUT)
    private String perfAdjustNo;

    /** 分配人工号. */
    private String username;

    /** 姓名. */
    private String fullname;

    /** 部门. */
    private String dept;

    /** 部门名称（列名无下划线，显式指定避免驼峰转换为 dept_name）. */
    @TableField("DEPTNAME")
    private String deptName;

    /** 分配比例. */
    private String ratio;

    /** 是否原分配关系：1,是；2,否. */
    private String isOriginal;

    /** 审核状态：0,待审批；1,同意；2,拒绝. */
    private String apprStatus;
}
