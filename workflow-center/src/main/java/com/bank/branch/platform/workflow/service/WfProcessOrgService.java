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
            recordOrg(processInstanceId, org.getOrgCode(), source);
        } catch (RuntimeException ex) {
            log.warn("[WfProcessOrgService.record] 写入参与机构异常, empId={}, pi={}, source={}",
                    empId, processInstanceId, source, ex);
        }
    }

    /**
     * 直接按机构编码记录"该机构参与了此流程实例"。幂等（uk_pi_org + INSERT IGNORE）。
     * <p>
     * 与 {@link #record(String, String, String)} 的区别：后者从"某个人"反查其主机构，
     * 适用于已有具体办理人（START/ASSIGN/CLAIM/APPROVE/TRANSFER）的场景；本方法用于
     * <b>只知道机构、没有具体办理人</b>的场景——典型是层级角色审批节点的候选人模式
     * （任务 assignee 为 NULL，只解析出"该由哪个机构审批"），此时不写快照会导致该机构
     * 在有人签收前对审批流监控页完全不可见。
     * </p>
     * <p>参数为空时静默跳过；异常整体兜住不外抛，理由同 {@link #record}。</p>
     *
     * @param processInstanceId 流程实例ID
     * @param orgCode           参与机构编码
     * @param source            来源标记（START/ASSIGN/CANDIDATE/CLAIM/APPROVE/TRANSFER/BACKFILL）
     */
    public void recordOrg(String processInstanceId, String orgCode, String source) {
        if (processInstanceId == null || orgCode == null || orgCode.isBlank()) {
            return;
        }
        try {
            WfProcessOrg row = new WfProcessOrg();
            row.setId(UUID.randomUUID().toString().replace("-", ""));
            row.setProcessInstanceId(processInstanceId);
            row.setOrgCode(orgCode);
            row.setSource(source);
            row.setFirstSeenTime(LocalDateTime.now());
            wfProcessOrgMapper.insertIgnore(row);
        } catch (RuntimeException ex) {
            log.warn("[WfProcessOrgService.recordOrg] 写入参与机构异常, orgCode={}, pi={}, source={}",
                    orgCode, processInstanceId, source, ex);
        }
    }
}
