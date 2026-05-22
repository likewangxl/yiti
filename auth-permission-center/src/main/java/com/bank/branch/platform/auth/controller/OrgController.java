package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.OrgCreateReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.OrgTreeNodeDTO;
import com.bank.branch.platform.auth.api.dto.OrgUpdateReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgUserDTO;
import com.bank.branch.platform.auth.security.context.CurrentUserProvider;
import com.bank.branch.platform.auth.service.OrgService;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织机构控制器
 * 提供机构树查询和当前用户机构子树查询接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orgs")
@Tag(name = "组织机构", description = "机构树查询接口")
public class OrgController {

    private final OrgService orgService;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 获取完整组织机构树
     * 从数据库一次性加载所有机构，在内存中构建层级树形结构
     *
     * @return 根节点列表（含完整子树）
     */
    @GetMapping("/tree")
    @Operation(summary = "获取完整组织机构树", description = "返回完整的机构树形结构，含所有层级")
    public ResponseWrapper<List<OrgTreeNodeDTO>> getOrgTree() {
        log.debug("[OrgController.getOrgTree] 获取完整机构树");
        List<OrgTreeNodeDTO> tree = orgService.getOrgTree();
        return ResponseWrapper.success(tree);
    }

    /**
     * 获取当前用户所在机构的子树
     * 基于当前登录用户的主机构编码，查询其机构子树（含自身）
     *
     * @return 当前用户机构子树中所有机构的列表
     */
    @GetMapping("/subtree")
    @Operation(summary = "获取当前用户机构子树", description = "返回当前用户主机构及其下属所有机构列表（树形结构）")
    public ResponseWrapper<List<OrgTreeNodeDTO>> getOrgSubtree() {
        CurrentUserContext ctx = currentUserProvider.get();
        String orgCode = ctx.mainOrgCode();
        log.debug("[OrgController.getOrgSubtree] 获取机构子树 orgCode={}", orgCode);
        List<OrgTreeNodeDTO> nodes = orgService.getOrgSubtreeAsTree(orgCode);
        return ResponseWrapper.success(nodes);
    }

    /**
     * 根据机构编码查询该机构下的所有用户（G.2）
     *
     * @param orgCode 机构编码
     * @param keyword 关键字（工号/姓名），可选
     * @param pageNo 页码，默认 1
     * @param pageSize 每页条数，默认 20
     * @return 机构下的用户列表（分页）
     */
    @GetMapping("/{orgCode}/users")
    @Operation(summary = "查询机构下的用户列表", description = "根据机构编码查询该机构绑定的所有用户（分页）")
    @BizAuth(bizType = BizType.ORG, action = BizAction.READ)
    public ResponseWrapper<OrgUserDTO> getOrgUsers(
            @PathVariable String orgCode,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[OrgController.getOrgUsers] orgCode={}, keyword={}, pageNo={}, pageSize={}", orgCode, keyword, pageNo, pageSize);
        PageResult<OrgUserDTO> result = orgService.getOrgUsers(orgCode, keyword, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /** 新增机构 */
    @PostMapping("")
    @Operation(summary = "新增机构")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<OrgDTO> createOrg(@Valid @RequestBody OrgCreateReqDTO req) {
        log.info("[OrgController.createOrg] orgCode={}", req.getOrgCode());
        return ResponseWrapper.success(orgService.createOrg(req));
    }

    /** 更新机构（只支持改 orgName） */
    @PutMapping("/{orgCode}")
    @Operation(summary = "更新机构信息")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<OrgDTO> updateOrg(@PathVariable String orgCode,
                                              @Valid @RequestBody OrgUpdateReqDTO req) {
        log.info("[OrgController.updateOrg] orgCode={}", orgCode);
        return ResponseWrapper.success(orgService.updateOrg(orgCode, req));
    }

    /** 删除机构（前置校验：无下级机构 + 无用户） */
    @DeleteMapping("/{orgCode}")
    @Operation(summary = "删除机构")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> deleteOrg(@PathVariable String orgCode) {
        log.info("[OrgController.deleteOrg] orgCode={}", orgCode);
        orgService.deleteOrg(orgCode);
        return ResponseWrapper.success(null);
    }
}
