package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.workflow.entity.WfNodeCandidateConf;
import com.bank.branch.platform.workflow.mapper.NodeCandidateConfMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * CandidateResolverService 单元测试。
 * <p>
 * 验证候选人解析逻辑：根据 candidateType 为候选值添加正确前缀，
 * 并正确处理空配置和多配置合并场景。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class CandidateResolverServiceTest {

    @Mock
    private NodeCandidateConfMapper nodeCandidateConfMapper;

    @InjectMocks
    private CandidateResolverService candidateResolverService;

    /**
     * 当候选类型为 ROLE 时，返回的每个值应带有 "ROLE:" 前缀。
     */
    @Test
    void resolve_roleType_returnsRolePrefixed() {
        WfNodeCandidateConf conf = new WfNodeCandidateConf();
        conf.setCandidateType("ROLE");
        conf.setCandidateValue("[\"CUST_MANAGER\",\"TEAM_LEAD\"]");

        when(nodeCandidateConfMapper.selectByProcessDefKeyAndNodeKey("loan", "approve"))
                .thenReturn(List.of(conf));

        List<String> result = candidateResolverService.resolveCandidates("loan", "approve");

        assertThat(result).containsExactly("ROLE:CUST_MANAGER", "ROLE:TEAM_LEAD");
    }

    /**
     * 当候选类型为 ORG 时，返回的每个值应带有 "ORG:" 前缀。
     */
    @Test
    void resolve_orgType_returnsOrgPrefixed() {
        WfNodeCandidateConf conf = new WfNodeCandidateConf();
        conf.setCandidateType("ORG");
        conf.setCandidateValue("[\"ORG001\"]");

        when(nodeCandidateConfMapper.selectByProcessDefKeyAndNodeKey("loan", "review"))
                .thenReturn(List.of(conf));

        List<String> result = candidateResolverService.resolveCandidates("loan", "review");

        assertThat(result).containsExactly("ORG:ORG001");
    }

    /**
     * 当候选类型为 USER 时，返回的每个值应带有 "USER:" 前缀。
     */
    @Test
    void resolve_userType_returnsUserPrefixed() {
        WfNodeCandidateConf conf = new WfNodeCandidateConf();
        conf.setCandidateType("USER");
        conf.setCandidateValue("[\"E001\"]");

        when(nodeCandidateConfMapper.selectByProcessDefKeyAndNodeKey("loan", "sign"))
                .thenReturn(List.of(conf));

        List<String> result = candidateResolverService.resolveCandidates("loan", "sign");

        assertThat(result).containsExactly("USER:E001");
    }

    /**
     * 当 mapper 返回空列表时，应返回空列表而不抛异常。
     */
    @Test
    void resolve_noConfig_returnsEmptyList() {
        when(nodeCandidateConfMapper.selectByProcessDefKeyAndNodeKey("loan", "unknown"))
                .thenReturn(Collections.emptyList());

        List<String> result = candidateResolverService.resolveCandidates("loan", "unknown");

        assertThat(result).isEmpty();
    }

    /**
     * 当 mapper 返回多条配置（ROLE + ORG）时，所有值应合并到单个列表中。
     */
    @Test
    void resolve_multipleConfigs_mergesAll() {
        WfNodeCandidateConf roleConf = new WfNodeCandidateConf();
        roleConf.setCandidateType("ROLE");
        roleConf.setCandidateValue("[\"CUST_MANAGER\"]");

        WfNodeCandidateConf orgConf = new WfNodeCandidateConf();
        orgConf.setCandidateType("ORG");
        orgConf.setCandidateValue("[\"ORG001\",\"ORG002\"]");

        when(nodeCandidateConfMapper.selectByProcessDefKeyAndNodeKey("loan", "approve"))
                .thenReturn(List.of(roleConf, orgConf));

        List<String> result = candidateResolverService.resolveCandidates("loan", "approve");

        assertThat(result).containsExactly("ROLE:CUST_MANAGER", "ORG:ORG001", "ORG:ORG002");
    }

    /**
     * resolveApproveOrgScope：取节点候选配置中首个非空 approveOrgScope。
     */
    @Test
    void resolveApproveOrgScope_returnsConfiguredScope() {
        WfNodeCandidateConf conf = new WfNodeCandidateConf();
        conf.setCandidateType("ROLE");
        conf.setCandidateValue("[\"BRANCH_HEAD\"]");
        conf.setApproveOrgScope("PARENT");

        when(nodeCandidateConfMapper.selectByProcessDefKeyAndNodeKey("DSN_x", "approval_1"))
                .thenReturn(List.of(conf));

        assertThat(candidateResolverService.resolveApproveOrgScope("DSN_x", "approval_1"))
                .isEqualTo("PARENT");
    }

    /**
     * resolveApproveOrgScope：未配置（无 conf / 全空）时返回 null。
     */
    @Test
    void resolveApproveOrgScope_noScope_returnsNull() {
        WfNodeCandidateConf conf = new WfNodeCandidateConf();
        conf.setCandidateType("ROLE");
        conf.setCandidateValue("[\"BRANCH_HEAD\"]");
        // approveOrgScope 未设置（null）

        when(nodeCandidateConfMapper.selectByProcessDefKeyAndNodeKey("DSN_x", "approval_2"))
                .thenReturn(List.of(conf));

        assertThat(candidateResolverService.resolveApproveOrgScope("DSN_x", "approval_2")).isNull();
    }

    /**
     * 测试 malicious/malformed JSON 时 parseCandidateValue 应返回空列表，不向上传播异常
     */
    @Test
    void resolve_malformedJson_gracefullySkipsInvalidConfig() {
        WfNodeCandidateConf validConf = new WfNodeCandidateConf();
        validConf.setCandidateType("USER");
        validConf.setCandidateValue("[\"E001\"]");

        WfNodeCandidateConf malformedConf = new WfNodeCandidateConf();
        malformedConf.setCandidateType("ROLE");
        malformedConf.setCandidateValue("{ NOT VALID JSON }");

        when(nodeCandidateConfMapper.selectByProcessDefKeyAndNodeKey("loan", "approve"))
                .thenReturn(List.of(validConf, malformedConf));

        List<String> result = candidateResolverService.resolveCandidates("loan", "approve");

        // 正常的 USER 配置应解析成功，malformed 被跳过
        assertThat(result).containsExactly("USER:E001");
    }
}
