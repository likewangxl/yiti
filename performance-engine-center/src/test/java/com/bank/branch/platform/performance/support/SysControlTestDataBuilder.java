package com.bank.branch.platform.performance.support;

import com.bank.branch.platform.performance.entity.SysControl;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * P1-A SysControl 测试数据构造器.
 * <p>统一构造带测试前缀的 SysControl 实体, 避免污染生产数据.
 */
public final class SysControlTestDataBuilder implements TestDataBuilder {

    private SysControlTestDataBuilder() {
    }

    /**
     * 构造单线程 IT 使用的 SysControl 记录 (前缀 TEST_SC_).
     *
     * @param idSuffix       id 后缀 (例如 "001")
     * @param scopeDim       维度
     * @param latestDataDate 最新数据日期
     * @param currentVersion 版本号
     * @param isValid        1 有效, 0 失效
     * @return SysControl 实体
     */
    public static SysControl buildTest(String idSuffix,
                                       String scopeDim,
                                       LocalDate latestDataDate,
                                       String currentVersion,
                                       int isValid) {
        SysControl sc = new SysControl();
        sc.setId("TEST_SC_" + idSuffix);
        sc.setScopeDim(scopeDim);
        sc.setLatestDataDate(latestDataDate);
        sc.setCurrentVersion(currentVersion);
        sc.setIsValid(isValid);
        LocalDateTime now = LocalDateTime.now();
        sc.setCreatedTime(now);
        sc.setUpdatedTime(now);
        return sc;
    }

    /**
     * 构造并发 IT 使用的 SysControl 记录 (前缀 CONCUR_SC_).
     */
    public static SysControl buildConcurrent(String idSuffix,
                                             String scopeDim,
                                             LocalDate latestDataDate,
                                             String currentVersion,
                                             int isValid) {
        SysControl sc = buildTest(idSuffix, scopeDim, latestDataDate, currentVersion, isValid);
        sc.setId("CONCUR_SC_" + idSuffix);
        return sc;
    }
}
