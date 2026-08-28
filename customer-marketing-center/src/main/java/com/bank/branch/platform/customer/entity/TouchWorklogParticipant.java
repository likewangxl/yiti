package com.bank.branch.platform.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 触达工作日志参与人，对应 MARKETING_TOUCH_WORKLOG_PARTICIPANT。 */
@Data
@TableName("MARKETING_TOUCH_WORKLOG_PARTICIPANT")
public class TouchWorklogParticipant {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long worklogId;
    private String participantEmpId;
    private String participantOrgId;
    private String participantRole;
    private String createdBy;
    private LocalDateTime createdTime;
}
