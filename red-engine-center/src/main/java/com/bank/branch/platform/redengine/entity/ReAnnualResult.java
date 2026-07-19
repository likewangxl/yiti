package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 红色引擎-年度考核结果。
 * 注意：RE_ANNUAL_RESULT 表无 deleted 列（年度归档结果不支持软删），不标 @TableLogic。
 */
@Data
@TableName("RE_ANNUAL_RESULT")
public class ReAnnualResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 党支部ID */
    private Long orgId;
    /** 维度一得分(满35) */
    private BigDecimal dim1Score;
    /** 维度二得分(满50) */
    private BigDecimal dim2Score;
    /** 维度三得分(满10) */
    private BigDecimal dim3Score;
    /** 维度四得分(满5) */
    private BigDecimal dim4Score;
    /** 原始总分(满100) */
    private BigDecimal totalScore;
    /** 折算得分(total*0.4) */
    private BigDecimal finalScore;
    /** 评优资格:1保留 0拦截(<60) */
    private Integer isQualified;
    /** 考核年度 */
    private Integer evalYear;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
