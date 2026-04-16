package com.bank.branch.platform.performance.service.cmd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 版本切换命令对象.
 *
 * <p>由 Controller 层构造, 透传到 Facade → Service. 事务与分布式锁的结构参数.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwitchVersionCmd {

    /** 维度: EMP / ORG / CUST. */
    private String scopeDim;

    /** 新版本的数据日期. */
    private LocalDate dataDate;

    /** 新版本号. */
    private String newVersion;

    /** 切换原因 (必填, 用于审计). */
    private String reason;

    /** 操作人 empId (由 Facade 根据 CurrentUser 填充). */
    private String operator;
}
