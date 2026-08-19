package com.bank.branch.platform.yundun.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 人员违规问责响应模型。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AccountabilityViolationDTO extends AccountabilityViolationSaveReq {
    private Long id;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
