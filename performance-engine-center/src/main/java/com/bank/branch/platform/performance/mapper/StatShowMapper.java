package com.bank.branch.platform.performance.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 财务统计展示表只读查询 Mapper（XAN_M9B_EMP_STAT_SHOW3 / XAN_M98_CUST_STAT_SHOW3）.
 *
 * <p>两张表为外部数仓抽数落地的"展示表"：列全为 varchar(300)、无主键、列宽达 56 / 93 列，
 * 且数仓侧可能增删列。为避免 56+93 个字段的脆弱实体映射，统一以
 * {@code SELECT *} → {@link LinkedHashMap} 行投影返回（保留列顺序），仅对少量有业务意义的
 * 列做条件过滤 + 分页。该方式与 report 模块 dynamic-query 行投影一致。
 *
 * <p>只读：仅 SELECT，无写入。表名在 XML 中固定字面量，过滤值一律 {@code #{}} 占位，无注入风险。
 */
@Mapper
public interface StatShowMapper {

    /**
     * 分页查询员工维度财务统计展示表（XAN_M9B_EMP_STAT_SHOW3）.
     *
     * @param statisDt 统计日期（可空，精确匹配）
     * @param branchNo 机构号（可空，精确匹配）
     * @param empId    员工号（可空，精确匹配）
     * @param indType  指标类型（可空，精确匹配）
     * @param keyword  关键字（可空，对 EMP_NAME 模糊匹配）
     * @param offset   偏移量
     * @param pageSize 页大小
     * @return 行投影列表（列名 → 值）
     */
    List<LinkedHashMap<String, Object>> pageEmpStat(@Param("statisDt") String statisDt,
                                                    @Param("branchNo") String branchNo,
                                                    @Param("empId") String empId,
                                                    @Param("indType") String indType,
                                                    @Param("keyword") String keyword,
                                                    @Param("offset") int offset,
                                                    @Param("pageSize") int pageSize);

    /**
     * 统计员工维度展示表总行数（与 {@link #pageEmpStat} 同条件）.
     */
    long countEmpStat(@Param("statisDt") String statisDt,
                      @Param("branchNo") String branchNo,
                      @Param("empId") String empId,
                      @Param("indType") String indType,
                      @Param("keyword") String keyword);

    /**
     * 分页查询客户维度财务统计展示表（XAN_M98_CUST_STAT_SHOW3）.
     *
     * @param statisDt 统计日期（可空，精确匹配）
     * @param branchNo 机构号（可空，精确匹配）
     * @param custId   客户号（可空，精确匹配）
     * @param custType 客户类型（可空，精确匹配 CUST_TYPE_CD）
     * @param keyword  关键字（可空，对 CUST_NAME 模糊匹配）
     * @param offset   偏移量
     * @param pageSize 页大小
     * @return 行投影列表（列名 → 值）
     */
    List<LinkedHashMap<String, Object>> pageCustStat(@Param("statisDt") String statisDt,
                                                     @Param("branchNo") String branchNo,
                                                     @Param("custId") String custId,
                                                     @Param("custType") String custType,
                                                     @Param("keyword") String keyword,
                                                     @Param("offset") int offset,
                                                     @Param("pageSize") int pageSize);

    /**
     * 统计客户维度展示表总行数（与 {@link #pageCustStat} 同条件）.
     */
    long countCustStat(@Param("statisDt") String statisDt,
                       @Param("branchNo") String branchNo,
                       @Param("custId") String custId,
                       @Param("custType") String custType,
                       @Param("keyword") String keyword);

    /**
     * 按客户号查客户名称（XAN_M98_CUST_STAT_SHOW3，仅取一条 {@code LIMIT 1}）.
     *
     * <p>外部渠道（callpu CASH_GETCUST_INFO）客户号查名专用：只投影 {@code CUST_NAME} 一列，
     * 按 {@code CUST_ID} 精确匹配；该展示表无主键、同一客户号可能多行，故 {@code LIMIT 1} 取首条。</p>
     *
     * @param custId 客户号（对应 {@code CUST_ID} 列）
     * @return 客户名称；无匹配时返回 {@code null}
     */
    String selectCustNameByCustId(@Param("custId") String custId);
}
