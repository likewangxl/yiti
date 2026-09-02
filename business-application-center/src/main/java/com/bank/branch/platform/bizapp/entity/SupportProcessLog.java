package com.bank.branch.platform.bizapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 中台支持办理过程记录，对应 MARKETING_SUPPORT_PROCESS_LOG。 */
@Data
@TableName("MARKETING_SUPPORT_PROCESS_LOG")
public class SupportProcessLog {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String supportRequestId;
    /** 移动端幂等键；同一申请内唯一。 */
    private String clientUuid;
    /** PROCESS / RESULT 等记录类型。 */
    private String logType;
    private String content;
    private LocalDateTime checkinTime;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String locationAddress;
    private String createdBy;
    private LocalDateTime createdTime;
    private Integer deleted;
}
