package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 正式触达工作日志实体，对应 MARKETING_TOUCH_WORKLOG。 */
@Data
@TableName("MARKETING_TOUCH_WORKLOG")
public class TouchWorklog {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String worklogNo;
    private String legacyWorklogId;
    private Long taskId;
    private Long custId;
    private String customerNameSnapshot;
    private String unifiedCreditCodeSnapshot;
    private String customerTagSnapshot;
    private String operatorEmpId;
    private String operatorOrgId;
    private LocalDateTime touchTime;
    private String touchMethod;
    private String touchPoints;
    private String touchResult;
    private Integer isFirstTouch;
    private String accountOpenProgress;
    private String locationStatus;
    private LocalDateTime locationTime;
    private String locationAddress;
    private String locationCityArea;
    private String locationRemark;
    private String clientUuid;
    private String recordStatus;
    private String voidBy;
    private LocalDateTime voidTime;
    private String voidReason;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
