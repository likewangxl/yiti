package com.bank.branch.platform.performance.controller.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 版本控制 Controller 层响应 DTO.
 *
 * <p>隐藏 entity 内部字段（createdTime / updatedTime / updatedBy / publishSource /
 * publishBy / publishTime），只暴露业务必需字段，防止 entity 泄漏到 API 层。
 *
 * <p>字段对应关系：
 * <ul>
 *   <li>id             — sys_control.id</li>
 *   <li>scopeDim       — sys_control.scope_dim</li>
 *   <li>latestDataDate — sys_control.latest_data_date</li>
 *   <li>currentVersion — sys_control.current_version</li>
 *   <li>isValid        — sys_control.is_valid</li>
 *   <li>remark         — sys_control.remark（切版备注）</li>
 * </ul>
 */
@Data
public class SysControlRespDTO {

    /** 控制ID (varchar(32) 主键). */
    private String id;

    /** 维度: EMP / ORG / CUST. */
    private String scopeDim;

    /** 最新数据日期. */
    private LocalDate latestDataDate;

    /** 当前有效版本号. */
    private String currentVersion;

    /** 是否有效: 1 有效, 0 失效. */
    private Integer isValid;

    /** 切版备注（业务可见）. */
    private String remark;
}
