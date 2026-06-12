package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户业绩分配关系 cust_alloc_relation 贫血实体.
 *
 * <p>对齐 DDL：主键 varchar(32) String；V1.0 Mapper 只读，写入由 V1.2 调整审批流程提供
 * （本版本仅暴露只读 Mapper 方法）.
 *
 * <p>DDL 字段映射（docs/schema/ddl-performance.sql § 14）：
 * <ul>
 *   <li>{@code id}（主键 varchar(32)）</li>
 *   <li>{@code cust_id}（客户 ID）</li>
 *   <li>{@code alloc_dim}（调整维度：RULE/ACCOUNT）</li>
 *   <li>{@code biz_kind}（业务种类，可空）</li>
 *   <li>{@code account_no}（账号，账号维度必填）</li>
 *   <li>{@code emp_id}（员工工号；数据范围过滤基准）</li>
 *   <li>{@code ratio}（分配比例 0-100, decimal(5,2)）</li>
 *   <li>{@code effective_date} / {@code end_date}（时间线）</li>
 *   <li>{@code source_batch_id} / {@code source_process_date}（来源批次）</li>
 *   <li>审计：created_by / created_time / updated_by / updated_time</li>
 * </ul>
 *
 * <p>索引：idx_cust_id / idx_emp_id / idx_effective_date（DDL 已建）.
 *
 * <p>时间线语义：某条记录"在 asOfDate 生效"的判定为
 * {@code effective_date &lt;= asOfDate AND (end_date IS NULL OR end_date &gt;= asOfDate)}.
 */
@Data
@TableName("CUST_ALLOC_RELATION")
public class CustAllocRelation {

    /** 分配关系 ID（varchar(32) 主键）. */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 客户 ID. */
    private String custId;

    /** 调整维度：RULE / ACCOUNT. */
    private String allocDim;

    /** 业务种类（nullable）. */
    private String bizKind;

    /** 账号（账号维度必填，nullable）. */
    private String accountNo;

    /** 客户类型：CORP/RETAIL（来源审批申请 apply.cust_type）. */
    private String custType;

    /** 是否原分配关系：1=是（被新分配取代），2=否（当前新分配）. */
    private String isOriginal;

    /** 员工工号（普通用户数据范围过滤基准：emp_id = currentUserId）. */
    private String empId;

    /** 员工姓名（快照，反显直接读，不再 UserApi 补全）. */
    private String fullname;

    /** 部门编号（快照）. */
    private String deptNo;

    /** 部门名称（快照）. */
    private String deptName;

    /** 分配比例（0-100, decimal(5,2)）. */
    private BigDecimal ratio;

    /** 生效日期. */
    private LocalDate effectiveDate;

    /** 失效日期（null 表示长期有效）. */
    private LocalDate endDate;

    /** 来源批次号（可选）. */
    private String sourceBatchId;

    /** 来源业务日期（可选）. */
    private LocalDate sourceProcessDate;

    /** 创建人. */
    private String createdBy;

    /** 创建时间（insert 由 DB CURRENT_TIMESTAMP 默认值填充）. */
    private LocalDateTime createdTime;

    /** 更新人. */
    private String updatedBy;

    /** 更新时间. */
    private LocalDateTime updatedTime;
}
