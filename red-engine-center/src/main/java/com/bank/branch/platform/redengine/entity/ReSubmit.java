package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 红色引擎-材料上报 */
@Data
@TableName("RE_SUBMIT")
public class ReSubmit {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 党组织ID */
    private Long orgId;
    /** 提交人平台工号 */
    private String submitterId;
    /** 考核维度(dim1~dim4) */
    private String dimension;
    /** 考核项编码(如1.1) */
    private String itemCode;
    /** 考核项名称 */
    private String itemName;
    /** 该考核项满分上限 */
    private BigDecimal maxScore;
    /** 项目名称 */
    private String projectName;
    /** 上报类型(1月度 2季度 3年度) */
    private Integer submitType;
    /** 上报日期 */
    private LocalDate submitDate;
    /** 状态(0草稿 1已提交 2已通过 3已驳回) */
    private Integer status;
    /** 表单数据(JSON) */
    private String formData;
    /** 附件URL列表(JSON数组,兼容迁移数据) */
    private String fileUrls;
    /** 审核意见 */
    private String reviewFeedback;
    /** 审核人平台工号 */
    private String reviewerId;
    /** 审核时间 */
    private LocalDateTime reviewDate;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
