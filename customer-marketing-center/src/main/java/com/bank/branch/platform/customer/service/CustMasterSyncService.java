package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 客户信息同步服务。
 * <p>
 * 把外部客户统计表 {@code XAN_M98_CUST_STAT_SHOW3}（由渠道网关 callpu 落地的 M98 报表）中、
 * 指定统计日期（STATIS_DT）下、客户编号在客户主档 {@code CUST_MASTER} 尚不存在的客户，
 * 按 CUST_ID / CUST_NAME 去重后插入客户主档，并记录统计日期与创建时间。
 * </p>
 * <p>
 * 由 {@code CustMasterSyncJob}（Quartz 定时任务，job_key=CUST_INFO_SYNC）每日触发，默认同步昨日数据。
 * 客户编号已存在的不重复插入；客户名称唯一键冲突由 {@code INSERT IGNORE} 静默跳过。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustMasterSyncService {

    private final CustMasterMapper custMasterMapper;

    /**
     * 同步昨日统计客户到客户主档（定时任务默认入口）。
     *
     * @return 新增客户主档记录数
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncYesterday() {
        String statisDt = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        return syncByStatisDt(statisDt);
    }

    /**
     * 同步指定统计日期的统计客户到客户主档。
     * <p>
     * 在 {@code XAN_M98_CUST_STAT_SHOW3} 中按 STATIS_DT={@code statisDt} 过滤，查找客户编号
     * 在 {@code CUST_MASTER} 中不存在的记录，对其按 CUST_ID、CUST_NAME 分组后，把 cust_id、cust_name
     * 写入客户主档，并记录统计日期与创建时间。
     * </p>
     *
     * @param statisDt 统计日期（yyyy-MM-dd）
     * @return 新增客户主档记录数
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncByStatisDt(String statisDt) {
        int inserted = custMasterMapper.syncNewCustomersFromStat(statisDt);
        log.info("[CustMasterSync] 统计日期={} 同步新增客户主档 {} 条", statisDt, inserted);
        return inserted;
    }
}
