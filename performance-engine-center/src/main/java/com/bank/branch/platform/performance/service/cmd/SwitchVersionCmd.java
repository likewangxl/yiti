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
 * <p>V1.0.3（Task B7）：新增 publishSource 字段，用于写入 sys_control.publish_source。
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

    /** 切换原因 / 备注 (必填, 用于审计，写入 remark 字段). */
    private String reason;

    /** 操作人 empId (由 Facade 根据 CurrentUser 填充，写入 publish_by 字段). */
    private String operator;

    /** 发布来源：MANUAL / AUTO / ROLLBACK（V1.0.3 新增，写入 publish_source 字段）. */
    private String publishSource;
}
