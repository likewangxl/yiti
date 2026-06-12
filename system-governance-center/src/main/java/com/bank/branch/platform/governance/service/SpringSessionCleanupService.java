package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.governance.mapper.SpringSessionMaintenanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Spring Session 表清理服务。
 * <p>定时清理孤儿 SPRING_SESSION_ATTRIBUTES（GoldenDB 去外键后无级联删除）。
 * 由 {@code SpringSessionCleanupQuartzJob} 在低峰期调度调用，集群只跑一份（Quartz）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpringSessionCleanupService {

    private final SpringSessionMaintenanceMapper springSessionMaintenanceMapper;

    /**
     * 清理孤儿 session 属性。
     *
     * @return 删除行数
     */
    public int cleanOrphanAttributes() {
        int deleted = springSessionMaintenanceMapper.deleteOrphanAttributes();
        log.info("[SpringSessionCleanup] 清理孤儿 SPRING_SESSION_ATTRIBUTES {} 行", deleted);
        return deleted;
    }
}
