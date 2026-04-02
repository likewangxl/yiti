package com.bank.branch.platform.common.security.context;

import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import lombok.Data;
import java.util.Set;

/**
 * 数据范围上下文
 * 基于 ThreadLocal 存储当前请求的数据权限范围信息
 * 在请求结束后必须通过 clear() 方法清理，防止内存泄漏
 */
@Data
public class DataScopeContext {
    private static final ThreadLocal<DataScopeContext> HOLDER = new ThreadLocal<>();

    /** 业务类型 */
    private BizType bizType;
    /** 业务操作 */
    private BizAction action;
    /** 数据范围类型 */
    private DataScopeType scope;
    /** 当前用户员工ID */
    private String empId;
    /** 当前用户机构编码 */
    private String orgCode;
    /** 当前用户机构及下属机构编码集合 */
    private Set<String> orgSubtreeCodes;
    /** 候选组标识集合 */
    private Set<String> candidateGroupKeys;

    /** 获取当前线程的数据范围上下文 */
    public static DataScopeContext current() { return HOLDER.get(); }

    /** 设置当前线程的数据范围上下文 */
    public static void set(DataScopeContext ctx) { HOLDER.set(ctx); }

    /** 清除当前线程的数据范围上下文 */
    public static void clear() { HOLDER.remove(); }
}
