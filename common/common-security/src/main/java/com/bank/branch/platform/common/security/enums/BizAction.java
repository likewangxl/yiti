package com.bank.branch.platform.common.security.enums;

import lombok.Getter;
import lombok.AllArgsConstructor;

/**
 * 业务操作枚举
 * 定义系统中所有可执行的业务操作类型，用于细粒度权限控制
 */
@Getter
@AllArgsConstructor
public enum BizAction {
    READ("READ", "查看详情"),
    LIST("LIST", "查询列表"),
    WRITE("WRITE", "新增/编辑"),
    DELETE("DELETE", "删除"),
    TRANSFER("TRANSFER", "转移"),
    APPROVE("APPROVE", "审批通过"),
    REJECT("REJECT", "审批驳回"),
    IMPORT("IMPORT", "数据导入"),
    EXPORT("EXPORT", "数据导出"),
    EXECUTE("EXECUTE", "执行操作"),
    CONFIG("CONFIG", "配置管理"),
    RECALC("RECALC", "重新计算"),
    JOB_TRIGGER("JOB_TRIGGER", "触发定时任务"),
    PERMISSION_CHANGE("PERMISSION_CHANGE", "权限变更"),
    EXECUTE_SQL("EXECUTE_SQL", "执行 SQL");

    private final String code;
    private final String description;
}
