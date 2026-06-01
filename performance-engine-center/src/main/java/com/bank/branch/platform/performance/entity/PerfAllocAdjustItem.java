package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分配关系调整明细 PERF_ALLOC_ADJUST_ITEM 贫血实体 (V1.2 Q2).
 *
 * <p>字段严格对齐生产 DDL (docs/schema/ddl-performance.sql §16)：
 * <ul>
 *   <li>{@code id} varchar(32) 主键</li>
 *   <li>{@code apply_id} 父申请 ID</li>
 *   <li>{@code emp_id} 调整后归属员工工号</li>
 *   <li>{@code ratio} 调整后分配比例（0-100, decimal(5,2)）</li>
 *   <li>{@code created_time} 创建时间（DB 默认）</li>
 * </ul>
 *
 * <p>UK：{@code (apply_id, emp_id)} 保证同一申请同员工不重复登记.
 *
 * <p>设计说明：仅记录调整<strong>后</strong>的员工与比例；原始分配的
 * "新老对比"由审批通过后的 {@code CUST_ALLOC_RELATION} 时间线保证（旧记录通过
 * {@code end_date} 切分，新记录 {@code effective_date} 由审批时间确定）.
 */
@Data
@TableName("PERF_ALLOC_ADJUST_ITEM")
public class PerfAllocAdjustItem {

    /** 明细 ID（varchar(32) 主键）. */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 父申请 ID（关联 PERF_ALLOC_ADJUST_APPLY.id）. */
    private String applyId;

    /** 调整后归属员工工号. */
    private String empId;

    /** 员工登录名（提交时快照，PT_USER.USERNAME；解析不到回退为 emp_id）. */
    private String username;

    /** 员工中文姓名（提交时快照，PT_USER.USERCHNNAME）. */
    private String empChnName;

    /** 所属部门号（提交时快照，员工主机构 ORG_CODE）. */
    private String orgCode;

    /** 所属部门名称（提交时快照，员工主机构 ORG_NAME）. */
    private String orgName;

    /** 调整后分配比例（0-100, decimal(5,2)）. */
    private BigDecimal ratio;

    /** 说明（可空）. */
    private String remark;

    /** 创建时间（DB 默认 CURRENT_TIMESTAMP）. */
    private LocalDateTime createdTime;
}
