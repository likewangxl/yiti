package com.bank.branch.platform.performance.api;

import java.util.Optional;

/**
 * 客户财务统计展示表（XAN_M98_CUST_STAT_SHOW3）对外只读查询 Api.
 *
 * <p>面向外部渠道（soap-gateway-center 的 callpu {@code CASH_GETCUST_INFO} 客户号查名）：
 * 按客户号在客户维度展示表中查客户名称。该表为外部数仓抽数落地表，由 performance-engine-center
 * 持有；跨模块调用方仅通过本 {@code *Api} 访问，禁止直连其 mapper/表。</p>
 *
 * <p>跨模块调用契约：本接口是 performance-engine-center 暴露给其他模块的客户号查名入口。</p>
 */
public interface CustStatQueryApi {

    /**
     * 按客户号查客户名称（XAN_M98_CUST_STAT_SHOW3，取一条）.
     *
     * @param custId 客户号（对应 {@code CUST_ID} 列；外部渠道前端输入）
     * @return 客户名称；custId 为空或查无匹配时返回 {@link Optional#empty()}
     * @deprecated 客户号查名已统一改走客户主档 {@code CUST_MASTER}，请使用
     *             {@link #getCustNameFromMaster(String)}（与 PC 管理端
     *             {@code StatShowController#getCustMasterName} 同一口径）。
     */
    @Deprecated
    Optional<String> getCustNameByCustId(String custId);

    /**
     * 按客户编号从客户主档 {@code CUST_MASTER} 查客户名称（{@code cust_master.cust_no} 列）.
     *
     * <p>替代原 {@link #getCustNameByCustId(String)} 从外部统计表 XAN_M98_CUST_STAT_SHOW3 取名的方式，
     * 与 PC 管理端「新建调整申请页客户名称反显」（{@code StatShowController#getCustMasterName}）走同一
     * 客户营销中心 {@code CustomerQueryApi.getCustomerByCustNo} 口径。custNo 为空、客户主档无该编号或
     * 名称为空白均返回 {@link Optional#empty()}（永不返回空白名）。</p>
     *
     * @param custNo 客户编号（{@code cust_master.cust_no}；外部渠道前端输入）
     * @return 客户名称（去空白后非空）；客户主档无匹配时返回 {@link Optional#empty()}
     */
    Optional<String> getCustNameFromMaster(String custNo);
}
