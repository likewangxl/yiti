package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 红色引擎-党员评分 */
@Data
@TableName("RE_MEMBER_SCORE")
public class ReMemberScore {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 党组织ID */
    private Long orgId;
    /** 党员平台工号 */
    private String userId;
    /** 党员姓名 */
    private String memberName;
    /** 考核期间(YYYY-MM) */
    private String scorePeriod;
    /** 党员得分 */
    private BigDecimal score;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
