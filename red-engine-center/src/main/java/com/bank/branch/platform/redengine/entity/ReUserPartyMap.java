package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 红色引擎-平台用户党组织映射 */
@Data
@TableName("RE_USER_PARTY_MAP")
public class ReUserPartyMap {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 平台用户工号(PT_USER.USER_ID) */
    private String userId;
    /** 党组织ID(RE_PARTY_ORG.id) */
    private Long partyOrgId;
    /** 党内角色:ORG_REVIEWER/BRANCH_REVIEWER/SECRETARY/REPORTER */
    private String partyRole;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
