package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.api.LeadApi;
import com.bank.branch.platform.customer.api.dto.LeadDTO;
import com.bank.branch.platform.customer.dto.req.LeadCreateReqDTO;
import com.bank.branch.platform.customer.dto.req.LeadDeleteVersionReqDTO;
import com.bank.branch.platform.customer.dto.req.LeadEditVersionReqDTO;
import com.bank.branch.platform.customer.dto.req.LeadSubmitReqDTO;
import com.bank.branch.platform.customer.dto.req.LeadUpdateReqDTO;
import com.bank.branch.platform.customer.dto.resp.LeadRespDTO;
import com.bank.branch.platform.customer.dto.resp.MainManagerLookupRespDTO;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.service.LeadEntryService;
import com.bank.branch.platform.customer.service.LeadService;
import com.bank.branch.platform.customer.service.LeadVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
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
 * 线索管理 REST 控制器。
 * <p>
 * 提供线索的 CRUD、提交审批及版本管理接口。
 * 所有写操作需要 {@link BizAction#WRITE} 权限，查询需要 {@link BizAction#LIST} 权限。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leads")
@Validated
@Tag(name = "线索管理")
public class LeadController {

    private final LeadService leadService;
    private final LeadEntryService leadEntryService;
    private final LeadVersionService leadVersionService;
    private final CurrentUserApi currentUserApi;
    private final LeadApi leadApi;

    /**
     * 分页查询线索列表（只查最新版本 is_latest=1）。
     *
     * @param keyword    关键词（模糊匹配客户名称/统一信用代码）
     * @param status     线索状态过滤
     * @param ownerOrgId 归属机构代码过滤
     * @param pageNo     页码，默认 1
     * @param pageSize   每页大小，默认 20
     * @return 分页线索列表
     */
    @GetMapping
    @BizAuth(bizType = BizType.LEAD, action = BizAction.LIST)
    @Operation(summary = "分页查询线索列表")
    public ResponseWrapper<CustLead> listPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String ownerOrgId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[LeadController.listPage] keyword={}, status={}, ownerOrgId={}, pageNo={}, pageSize={}",
                keyword, status, ownerOrgId, pageNo, pageSize);
        PageResult<CustLead> result = leadEntryService.listCreatedPage(
                keyword, status, ownerOrgId, pageNo, pageSize,
                currentUserApi.getCurrentEmpId());
        return ResponseWrapper.page(result);
    }

    /**
     * 查询线索详情。
     *
     * @param id 线索ID
     * @return 线索详情
     */
    @GetMapping("/{id}")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.READ)
    @Operation(summary = "查询线索详情")
    public ResponseWrapper<LeadRespDTO> getById(@PathVariable String id) {
        log.info("[LeadController.getById] id={}", id);
        return ResponseWrapper.success(leadEntryService.getCreatedDetail(
                id, currentUserApi.getCurrentEmpId()));
    }

    /** 查询存量客户及当前主办权，供录入页确定分配方式。 */
    @GetMapping("/main-manager")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.READ)
    @Operation(summary = "查询存量客户主办权")
    public ResponseWrapper<MainManagerLookupRespDTO> lookupMainManager(
            @RequestParam(required = false) String unifiedCreditCode,
            @RequestParam(required = false) String custName) {
        return ResponseWrapper.success(leadEntryService.lookupMainManager(unifiedCreditCode, custName));
    }

    /**
     * 新建线索草稿。
     *
     * @param req 创建请求 DTO
     * @return 新建线索 ID
     */
    @PostMapping
    @BizAuth(bizType = BizType.LEAD, action = BizAction.WRITE)
    @Operation(summary = "新建线索草稿")
    public ResponseWrapper<String> create(@Valid @RequestBody LeadCreateReqDTO req) {
        log.info("[LeadController.create] custName={}", req.getCustName());
        String empId = currentUserApi.getCurrentEmpId();
        String orgCode = currentUserApi.getCurrentOrgCode();
        CustLead lead = leadEntryService.createDraft(req, empId, orgCode, currentUserApi.isSystemAdmin());
        return ResponseWrapper.success(lead.getId());
    }

    /**
     * 编辑线索草稿（只允许 DRAFT 状态）。
     *
     * @param id  线索ID
     * @param req 更新请求 DTO
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.WRITE)
    @Operation(summary = "编辑线索草稿")
    public ResponseWrapper<Void> update(@PathVariable String id,
                                        @RequestBody LeadUpdateReqDTO req) {
        log.info("[LeadController.update] id={}", id);
        String empId = currentUserApi.getCurrentEmpId();
        leadEntryService.updateDraft(id, req, empId, currentUserApi.getCurrentOrgCode(),
                currentUserApi.isSystemAdmin());
        return ResponseWrapper.success();
    }

    /**
     * 删除线索草稿（只允许 DRAFT 状态，逻辑删除）。
     *
     * @param id 线索ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.DELETE)
    @Operation(summary = "删除线索草稿")
    public ResponseWrapper<Void> delete(@PathVariable String id) {
        log.info("[LeadController.delete] id={}", id);
        String empId = currentUserApi.getCurrentEmpId();
        leadService.deleteDraft(id, empId);
        return ResponseWrapper.success();
    }

    /**
     * 提交线索审批（只允许 DRAFT 状态）。
     *
     * @param id  线索ID
     * @param req 提交请求 DTO（可为空 body）
     * @return 操作结果
     */
    @PostMapping("/{id}/submit")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.WRITE)
    @Operation(summary = "提交线索审批")
    public ResponseWrapper<Void> submit(@PathVariable String id,
                                        @RequestBody(required = false) LeadSubmitReqDTO req) {
        log.info("[LeadController.submit] id={}", id);
        String empId = currentUserApi.getCurrentEmpId();
        String orgCode = currentUserApi.getCurrentOrgCode();
        leadService.submitForApproval(id, empId, orgCode);
        return ResponseWrapper.success();
    }

    /**
     * 为已有客户创建修改版本线索。
     *
     * @param req 创建修改版本请求 DTO
     * @return 新版本线索 ID
     */
    @PostMapping("/edit-version")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.WRITE)
    @Operation(summary = "创建修改版本线索")
    public ResponseWrapper<String> createEditVersion(@Valid @RequestBody LeadEditVersionReqDTO req) {
        log.info("[LeadController.createEditVersion] sourceCustId={}", req.getSourceCustId());
        String empId = currentUserApi.getCurrentEmpId();
        String orgCode = currentUserApi.getCurrentOrgCode();
        CustLead lead = leadVersionService.createEditVersion(
                req.getSourceCustId(),
                req.getCustName(), req.getUnifiedCreditCode(),
                req.getContactPerson(), req.getContactMobile(),
                req.getIndustry(), req.getGroupType(), req.getCustomerType(),
                req.getIsKeystone(), req.getEnterpriseType(), req.getGroupName(),
                req.getIsAccountOpened(), req.getTouchRestricted(), req.getCustomerDesc(),
                req.getCreditAmount(), req.getCreditExposureAmount(),
                req.getLeadSource(), req.getTagIds(), null, req.getRemark(),
                empId, orgCode
        );
        return ResponseWrapper.success(lead.getId());
    }

    /**
     * 为已有客户创建删除版本线索。
     *
     * @param req 创建删除版本请求 DTO
     * @return 新版本线索 ID
     */
    @PostMapping("/delete-version")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.WRITE)
    @Operation(summary = "创建删除版本线索")
    public ResponseWrapper<String> createDeleteVersion(@Valid @RequestBody LeadDeleteVersionReqDTO req) {
        log.info("[LeadController.createDeleteVersion] sourceCustId={}", req.getSourceCustId());
        String empId = currentUserApi.getCurrentEmpId();
        String orgCode = currentUserApi.getCurrentOrgCode();
        CustLead lead = leadVersionService.createDeleteVersion(req.getSourceCustId(), empId, orgCode);
        return ResponseWrapper.success(lead.getId());
    }

    /**
     * 查询线索版本链（按 version_no 升序）。
     * <p>
     * 返回同一客户线索的所有历史版本记录，通过 sourceCustId 关联，
     * 按 version_no 升序排列，可追溯线索完整变更历史。
     * </p>
     *
     * @param id 线索ID（任意版本均可，系统自动查找关联的全部版本）
     * @return 版本链 DTO 列表，按 version_no 升序；查不到时返回空列表
     */
    @GetMapping("/{id}/versions")
    @BizAuth(bizType = BizType.LEAD, action = BizAction.READ)
    @Operation(summary = "查询线索版本链")
    public ResponseWrapper<List<LeadDTO>> versions(@PathVariable String id) {
        log.info("[LeadController.versions] id={}", id);
        List<LeadDTO> chain = leadApi.getLeadVersionChain(id);
        return ResponseWrapper.success(chain);
    }
}
