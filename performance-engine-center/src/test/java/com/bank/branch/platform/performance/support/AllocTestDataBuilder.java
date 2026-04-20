package com.bank.branch.platform.performance.support;

import com.bank.branch.platform.performance.entity.CustAllocRelation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * P1-F 客户业绩分配关系子域测试数据构造器.
 *
 * <p>统一构造带 {@code TEST_AR_} 前缀的 CustAllocRelation 实体，避免污染生产数据；
 * 单线程 IT 的事务会回滚，这里的前缀主要保证并发 / 手工排查时可识别.
 *
 * <p>由于 CustAllocRelationMapper 只读（V1.0 无 insert 方法），测试数据通过 JdbcTemplate
 * 在 IT 中直接 {@code INSERT INTO cust_alloc_relation ...} 准备。本 Builder 只负责
 * 构造 Entity 对象以便 IT 组织测试数据.
 */
public final class AllocTestDataBuilder implements TestDataBuilder {

    /** 统一测试客户 ID 前缀（cust_id 使用）. */
    public static final String CUST_PREFIX = "TEST_AR_";

    private AllocTestDataBuilder() {
    }

    /**
     * 构造一条默认规则维度的分配关系（生效日期=今天, end_date=null, ratio=100, alloc_dim=RULE）.
     *
     * @param custSuffix 客户 ID 后缀（结果为 {@code TEST_AR_{custSuffix}}）
     * @param empId      员工工号
     * @param bizKind    业务种类
     * @return 分配关系实体
     */
    public static CustAllocRelation relation(String custSuffix, String empId, String bizKind) {
        return relation(custSuffix, empId, bizKind, LocalDate.now(), null);
    }

    /**
     * 构造自定义时间线的分配关系（规则维度, ratio=100）.
     *
     * @param custSuffix    客户 ID 后缀
     * @param empId         员工工号
     * @param bizKind       业务种类
     * @param effectiveDate 生效日期
     * @param endDate       失效日期（null = 长期有效）
     * @return 分配关系实体
     */
    public static CustAllocRelation relation(String custSuffix, String empId, String bizKind,
                                             LocalDate effectiveDate, LocalDate endDate) {
        return relation(custSuffix, empId, bizKind, effectiveDate, endDate, new BigDecimal("100.00"));
    }

    /**
     * 完整构造.
     *
     * @param custSuffix    客户 ID 后缀
     * @param empId         员工工号
     * @param bizKind       业务种类
     * @param effectiveDate 生效日期
     * @param endDate       失效日期（null = 长期有效）
     * @param ratio         分配比例
     * @return 分配关系实体
     */
    public static CustAllocRelation relation(String custSuffix, String empId, String bizKind,
                                             LocalDate effectiveDate, LocalDate endDate,
                                             BigDecimal ratio) {
        CustAllocRelation r = new CustAllocRelation();
        r.setId(randomId());
        r.setCustId(CUST_PREFIX + custSuffix);
        r.setAllocDim("RULE");
        r.setBizKind(bizKind);
        r.setAccountNo(null);
        r.setEmpId(empId);
        r.setRatio(ratio);
        r.setEffectiveDate(effectiveDate);
        r.setEndDate(endDate);
        r.setSourceBatchId(null);
        r.setSourceProcessDate(null);
        r.setCreatedBy("TEST_BUILDER");
        r.setUpdatedBy(null);
        // created_time / updated_time 由 DB 默认值填充
        return r;
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
