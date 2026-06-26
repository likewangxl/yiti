package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.portal.mapper.GuaranteeSyncMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 担保信息每日同步服务。
 *
 * <p>由 {@code GuaranteeSyncJob}（Quartz 定时任务，job_key=GUARANTEE_INFO_SYNC，每日 06:30）触发，
 * 把前一日 {@code clms_ed_credit_info} 的担保类授信额度数据同步进 {@code zh_guarantee_info}。</p>
 *
 * <p>同步逻辑（先更新后插入，保证已有客户刷新、新客户补录）：</p>
 * <ol>
 *   <li>统计前一日 clms 记录数，为 0 直接返回（当日无数据，不做任何写操作）；</li>
 *   <li>{@code updateToGuarantee} 用当日担保类额度更新担保表已存在客户；</li>
 *   <li>{@code saveToGuarantee} 插入担保表尚不存在的担保类客户；</li>
 *   <li>{@code saveToCcmsBusiness} 把当日 000020 类额度归集进合同表；</li>
 *   <li>{@code updateToGuaranteeUpdate} 刷新担保表 update_time。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuaranteeSyncService {

    private final GuaranteeSyncMapper guaranteeSyncMapper;

    /**
     * 同步前一日 clms 担保数据到担保表（定时任务入口）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void processing() {
        String yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        // 前置判空：当日无 clms 数据则直接跳过，避免空跑
        int clmsCount = guaranteeSyncMapper.getClmsCount(yesterday);
        if (clmsCount == 0) {
            log.info("[GuaranteeSync] 日期={} clms 无数据，跳过同步", yesterday);
            return;
        }
        // 先更新表里数据，没有再插入数据
        int updated = guaranteeSyncMapper.updateToGuarantee(yesterday);
        int insertedGuarantee = guaranteeSyncMapper.saveToGuarantee(yesterday);
        int insertedCcms = guaranteeSyncMapper.saveToCcmsBusiness(yesterday);
        guaranteeSyncMapper.updateToGuaranteeUpdate();
        log.info("[GuaranteeSync] 日期={} 同步完成：clms={} 更新担保={} 新增担保={} 归集合同={}",
                yesterday, clmsCount, updated, insertedGuarantee, insertedCcms);
    }
}
