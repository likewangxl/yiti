package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 红色引擎-党组织（党委/党支部树） */
@Data
@TableName("RE_PARTY_ORG")
public class RePartyOrg {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orgName;
    private Long parentId;
    private Integer orgLevel;
    private String orgCode;
    private String orgType;
    private String principal;
    private String contactPhone;
    private String orgAddress;
    /** 支部书记平台用户工号 */
    private String secretaryId;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
