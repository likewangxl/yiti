package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.redengine.api.dto.RePartyOrgTreeDTO;
import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.bank.branch.platform.redengine.service.RePartyOrgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 红色引擎-党组织管理端点。
 * <p>移植自 redengine {@code OrgController}（{@code system.controller}）。
 * URL 与 Task 4 权限种子对照表严格一致：GET {@code /api/re/orgs/tree} 对应资源 P_RE_ORG_TREE，
 * GET {@code /api/re/orgs/{id}} 对应 P_RE_ORG_GET，POST/PUT/DELETE 分别对应
 * P_RE_ORG_ADD/P_RE_ORG_UPD/P_RE_ORG_DEL（仅 SYS_ADMIN 授权，DELETE 为高危操作，审计强制填写原因）。</p>
 * <p>源系统 {@code GET /api/org/{parentId}/children} 端点未纳入 Task 4 权限种子（未注册到
 * PT_RESOURCE），本次移植不落地——getOrgTree 已一次性返回完整父子树，前端按需在树上取子节点即可，
 * 单独的子节点查询端点属冗余能力（YAGNI）。</p>
 */
@Slf4j
@Tag(name = "红色引擎-党组织管理")
@RestController
@RequestMapping("/api/re/orgs")
@RequiredArgsConstructor
public class ReOrgController {

    private final RePartyOrgService rePartyOrgService;

    @Operation(summary = "党组织树")
    @GetMapping("/tree")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.LIST)
    public ResponseWrapper<List<RePartyOrgTreeDTO>> getOrgTree() {
        List<RePartyOrgTreeDTO> tree = rePartyOrgService.getOrgTree();
        log.info("[ReOrgController.getOrgTree] rootCount={}", tree.size());
        return ResponseWrapper.success(tree);
    }

    @Operation(summary = "党组织详情")
    @GetMapping("/{id}")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<RePartyOrgTreeDTO> getOrg(@PathVariable Long id) {
        RePartyOrgTreeDTO org = rePartyOrgService.getById(id);
        log.info("[ReOrgController.getOrg] id={}, found={}", id, org != null);
        return ResponseWrapper.success(org);
    }

    @Operation(summary = "新增党组织")
    @PostMapping
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.WRITE)
    @AuditLog(action = "RE_ORG_ADD", resourceType = "RE_PARTY_ORG")
    public ResponseWrapper<Long> addOrg(@RequestBody RePartyOrg org) {
        Long id = rePartyOrgService.add(org);
        log.info("[ReOrgController.addOrg] id={}, orgName={}", id, org.getOrgName());
        return ResponseWrapper.success(id);
    }

    @Operation(summary = "修改党组织")
    @PutMapping("/{id}")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.WRITE)
    @AuditLog(action = "RE_ORG_UPD", resourceType = "RE_PARTY_ORG")
    public ResponseWrapper<Void> updateOrg(@PathVariable Long id, @RequestBody RePartyOrg org) {
        rePartyOrgService.update(id, org);
        log.info("[ReOrgController.updateOrg] id={}", id);
        return ResponseWrapper.success();
    }

    @Operation(summary = "删除党组织(高危)")
    @DeleteMapping("/{id}")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.DELETE)
    @AuditLog(action = "RE_ORG_DEL", resourceType = "RE_PARTY_ORG", reasonRequired = true)
    public ResponseWrapper<Void> deleteOrg(@PathVariable Long id) {
        rePartyOrgService.delete(id);
        log.info("[ReOrgController.deleteOrg] id={}", id);
        return ResponseWrapper.success();
    }
}
