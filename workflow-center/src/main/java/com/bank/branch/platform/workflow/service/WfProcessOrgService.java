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
        OrgDTO org;
        try {
            org = orgApi.getUserMainOrg(empId);
        } catch (RuntimeException ex) {
            // OrgApi.getUserMainOrg 在 empId 无 EXT_USER_ORG 映射时会抛 BizException(AUTH-40403)，
            // 本方法是全挂点唯一写入入口，绝不能把异常抛回调用方（Flowable 监听器/流程启动），兜住并跳过。
            log.warn("[WfProcessOrgService.record] 机构查询异常, empId={}, pi={}", empId, processInstanceId, ex);
            return;
        }
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
    }
}
