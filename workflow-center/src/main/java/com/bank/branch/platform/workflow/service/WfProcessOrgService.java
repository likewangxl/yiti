package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.workflow.entity.WfProcessOrg;
import com.bank.branch.platform.workflow.mapper.WfProcessOrgMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/** 审批流参与机构快照写入唯一入口（所有挂点都调这里，避免遗漏）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfProcessOrgService {

    private final WfProcessOrgMapper wfProcessOrgMapper;
    private final OrgApi orgApi;

    /**
     * 记录"某工号（其主机构）参与了该流程实例"。幂等。
     * empId 为空、或机构查不到时静默跳过（不阻断主流程）。
     */
    public void record(String processInstanceId, String empId, String source) {
        if (empId == null || empId.isBlank() || processInstanceId == null) {
            return;
        }
        // 本方法是全挂点唯一写入入口，绝不能把异常抛回调用方（Flowable 监听器/流程启动/审批 REST）。
        // 整体兜底：机构查询（OrgApi 在 empId 无 EXT_USER_ORG 映射时抛 BizException(AUTH-40403)）
        // 与落库（insertIgnore 遇死锁/锁等待超时/连接断开时抛 DataAccessException，"IGNORE" 只吞
        // 唯一键冲突，不吞这些）都可能抛未受检异常，必须一并兜住，否则会 500 一个已提交的操作
        // 或打断 Flowable 自己的命令执行。
        try {
            OrgDTO org = orgApi.getUserMainOrg(empId);
            if (org == null || org.getOrgCode() == null) {
                log.warn("[WfProcessOrgService.record] 机构未知, empId={}, pi={}", empId, processInstanceId);
                return;
            }
            WfProcessOrg row = new WfProcessOrg();
            row.setId(UUID.randomUUID().toString().replace("-", ""));
            row.setProcessInstanceId(processInstanceId);
            row.setOrgCode(org.getOrgCode());
            row.setSource(source);
            row.setFirstSeenTime(LocalDateTime.now());
            wfProcessOrgMapper.insertIgnore(row);
        } catch (RuntimeException ex) {
            log.warn("[WfProcessOrgService.record] 写入参与机构异常, empId={}, pi={}, source={}",
                    empId, processInstanceId, source, ex);
        }
    }
}
