package com.bank.branch.platform.common.security.enums;

import lombok.Getter;
import lombok.AllArgsConstructor;

/**
 * 业务类型枚举
 * 定义系统中所有业务模块的类型标识，用于权限控制和资源分类
 */
@Getter
@AllArgsConstructor
public enum BizType {
    NAV("NAV", "导航菜单"),
    ADDRBOOK("ADDRBOOK", "通讯录"),
    PRODUCT("PRODUCT", "产品管理"),
    DOC("DOC", "文档管理"),
    TAG("TAG", "标签管理"),
    LEAD("LEAD", "线索管理"),
    CUSTOMER("CUSTOMER", "客户管理"),
    CUSTOMER_POOL("CUSTOMER_POOL", "客户池管理"),
    CLAIM("CLAIM", "认领管理"),
    TOUCH_TASK("TOUCH_TASK", "触达任务"),
    TOUCH_REPORT("TOUCH_REPORT", "触达报告"),
    LOAN("LOAN", "贷款业务"),
    SUPPORT("SUPPORT", "支撑类业务"),
    SUPPORT_DEPT("SUPPORT_DEPT", "支撑部门管理"),
    REPORT("REPORT", "报表分析"),
    PERF_CONFIG("PERF_CONFIG", "绩效配置"),
    KPI_CALC("KPI_CALC", "考核计算"),
    SYS_CONFIG("SYS_CONFIG", "系统配置"),
    ORG("ORG", "组织机构"),
    EVAL("EVAL", "内部评价");

    private final String code;
    private final String description;
}
