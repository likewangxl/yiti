package com.bank.branch.platform.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 业绩分配调整明细 PERF_ALLOC_ADJUST_ITEM 贫血实体（只读报表用）.
 *
 * <p>一个申请对应多行分配明细，以 {@code apply_id} 关联主表 PERF_ALLOC_ADJUST_APPLY.id。
 * 即「业绩分配数据」（详情页用）。</p>
 */
@Data
@TableName("PERF_ALLOC_ADJUST_ITEM")
public class PerfAllocAdjustItem {

    /** 明细ID（主键）. */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 申请ID（关联主表）. */
    private String applyId;

    /** 账号. */
    private String acctNo;

    /** 分配人工号（业务工号）. */
    private String empId;

    /** 用户名（PT_USER.username）. */
    private String username;

    /** 分配人姓名. */
    private String empChnName;

    /** 机构编码. */
    private String orgCode;

    /** 机构名称. */
    private String orgName;

    /** 分配比例. */
    private BigDecimal ratio;

    /** 备注. */
    private String remark;
}
