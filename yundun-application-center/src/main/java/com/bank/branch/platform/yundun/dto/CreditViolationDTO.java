package com.bank.branch.platform.yundun.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 信贷风险责任认定响应模型。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreditViolationDTO extends CreditViolationSaveReq {
    private Long id;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /**
     * 当前分页内的客户分组键，仅用于避免不同客户名称脱敏后发生表格合并碰撞。
     * 该值不包含客户名称，详情接口不返回该分组键。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String clientNameGroupKey;
}
