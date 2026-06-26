package com.bank.branch.platform.portal.mapper;

import com.bank.branch.platform.portal.entity.ZhGuaranteeInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 担保信息每日同步 Mapper（定时任务 GUARANTEE_INFO_SYNC 专用）。
 *
 * <p>数据来源 {@code clms_ed_credit_info}（贷记额度信息，hive 同步表），目标 {@code zh_guarantee_info}
 * 与 {@code ccms_business_contract}。全部为跨表/聚合自定义 SQL，BaseMapper 覆盖不到，统一落
 * {@code GuaranteeSyncMapper.xml}。SQL 口径见 {@code db/} 下同名截图。</p>
 */
@Mapper
public interface GuaranteeSyncMapper extends BaseMapper<ZhGuaranteeInfo> {

    /**
     * 统计指定日期 clms 贷记额度记录数（同步前置判空，0 则当日无数据直接跳过）。
     *
     * @param ydate 统计日期（yyyy-MM-dd）
     * @return 记录数
     */
    int getClmsCount(@Param("ydate") String ydate);

    /**
     * 用 clms 当日担保类额度数据更新 {@code zh_guarantee_info} 已存在客户（按客户名匹配）。
     *
     * @param ydate 统计日期（yyyy-MM-dd）
     * @return 受影响行数
     */
    int updateToGuarantee(@Param("ydate") String ydate);

    /**
     * 把 clms 当日担保类、且 {@code zh_guarantee_info} 尚不存在的客户插入担保表。
     *
     * @param ydate 统计日期（yyyy-MM-dd）
     * @return 插入行数
     */
    int saveToGuarantee(@Param("ydate") String ydate);

    /**
     * 把 clms 当日 000020 类额度数据归集插入 {@code ccms_business_contract}。
     *
     * @param ydate 统计日期（yyyy-MM-dd）
     * @return 插入行数
     */
    int saveToCcmsBusiness(@Param("ydate") String ydate);

    /**
     * 刷新 {@code zh_guarantee_info} 全表 update_time 为当前时间。
     *
     * @return 受影响行数
     */
    int updateToGuaranteeUpdate();
}
