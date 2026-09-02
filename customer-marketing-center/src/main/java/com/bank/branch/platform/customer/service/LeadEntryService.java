package com.bank.branch.platform.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.dto.req.LeadCreateReqDTO;
import com.bank.branch.platform.customer.dto.req.LeadUpdateReqDTO;
import com.bank.branch.platform.customer.dto.resp.LeadManagerScopeRespDTO;
import com.bank.branch.platform.customer.dto.resp.LeadRespDTO;
import com.bank.branch.platform.customer.dto.resp.LeadTagRespDTO;
import com.bank.branch.platform.customer.dto.resp.MainManagerLookupRespDTO;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.entity.CustLeadManagerScope;
import com.bank.branch.platform.customer.entity.CustLeadTagRel;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.CustomerRoleCode;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.enums.TagStatus;
import com.bank.branch.platform.customer.mapper.CustLeadManagerScopeMapper;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.CustLeadTagRelMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 一期线索录入服务。
 *
 * <p>负责录入表单、分配规则、标签/附件快照和审批详情的统一口径；旧版
 * {@link LeadService} 继续承载删除和提交工作流，避免破坏既有调用。</p>
 */
@Service
@RequiredArgsConstructor
public class LeadEntryService {

    private static final String PUBLIC = "PUBLIC";
    private static final String SCOPE = "SCOPE";
    private static final String OWNER = "OWNER";

    private final CustLeadMapper leadMapper;
    private final CustLeadManagerScopeMapper managerScopeMapper;
    private final CustLeadTagRelMapper leadTagRelMapper;
    private final CustTagMapper tagMapper;
    private final CustMasterMapper masterMapper;
    private final OrgApi orgApi;
    private final BizScopeApi bizScopeApi;
    private final UserApi userApi;
    private final FileApi fileApi;

    /**
     * 查询当前员工本人录入的线索台账。
     *
     * <p>录入页属于个人工作台，无系统管理员或机构数据范围例外。</p>
     */
    public PageResult<CustLead> listCreatedPage(String keyword, String status, String ownerOrgId,
                                                int pageNo, int pageSize, String operatorEmpId) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePageNo - 1) * safePageSize;
        List<CustLead> records = leadMapper.selectCreatedPage(
                keyword, status, ownerOrgId, operatorEmpId, offset, safePageSize);
        long total = leadMapper.countCreatedPage(keyword, status, ownerOrgId, operatorEmpId);
        return PageResult.of(safePageNo, safePageSize, total, records);
    }

    /**
     * 查询录入台账可见线索：全行公开始终可见，指定范围/主办线索仅相关人员可见，
     * ORG/ORG_SUBTREE/ALL 角色再叠加其机构数据范围。
     */
    public PageResult<CustLead> listVisiblePage(String keyword, String status, String ownerOrgId,
                                                int pageNo, int pageSize, String operatorEmpId,
                                                boolean systemAdmin) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        DataScopeType scopeType = DataScopeType.ALL;
        Set<String> orgCodes = Set.of();
        if (!systemAdmin) {
            DataScopeContext context = bizScopeApi.buildScopeContext(
                    operatorEmpId, BizType.LEAD, BizAction.LIST);
            scopeType = context == null || context.scopeType() == null
                    ? DataScopeType.SELF_CREATED : context.scopeType();
            if (scopeType == DataScopeType.ORG && StringUtils.hasText(context.orgCode())) {
                orgCodes = Set.of(context.orgCode());
            } else if (scopeType == DataScopeType.ORG_SUBTREE
                    && context.orgSubtreeCodes() != null) {
                orgCodes = context.orgSubtreeCodes();
            }
        }
        int offset = (safePageNo - 1) * safePageSize;
        List<CustLead> records = leadMapper.selectVisiblePage(keyword, status, ownerOrgId,
                scopeType.getCode(), operatorEmpId, orgCodes, offset, safePageSize);
        long total = leadMapper.countVisiblePage(keyword, status, ownerOrgId,
                scopeType.getCode(), operatorEmpId, orgCodes);
        return PageResult.of(safePageNo, safePageSize, total, records);
    }

    /** 新建线索草稿，并按录入人层级固化可见范围。 */
    @Transactional
    public CustLead createDraft(LeadCreateReqDTO req, String operatorEmpId,
                                String orgCode, boolean systemAdmin) {
        LocalDateTime now = LocalDateTime.now();
        CustLead lead = new CustLead();
        lead.setId(uuid());
        lead.setLeadNo("LEAD_" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        lead.setLeadOp(LeadOp.CREATE.getCode());
        lead.setVersionNo(1);
        lead.setIsLatest(1);
        lead.setLeadStatus(LeadStatus.DRAFT.getCode());
        lead.setOwnerOrgId(orgCode);
        lead.setCreatedBy(operatorEmpId);
        lead.setCreatedTime(now);
        lead.setUpdatedBy(operatorEmpId);
        lead.setUpdatedTime(now);
        lead.setDeleted(0);
        lead.setLockVersion(0);
        applyCreateFields(lead, req);

        Distribution distribution = resolveDistribution(req.getDistributionMode(), req.getMainManagerId(),
                req.getManagerScopeIds(), operatorEmpId, orgCode, systemAdmin,
                req.getUnifiedCreditCode(), req.getCustName());
        applyDistribution(lead, distribution);
        lead.setTagIds(toTagJson(req.getTagIdList(), req.getTagIds()));

        try {
            leadMapper.insert(lead);
        } catch (DuplicateKeyException ex) {
            throw new BizException(CustomerErrorCode.LEAD_CREDIT_CODE_DUPLICATE.getCode(),
                    CustomerErrorCode.LEAD_CREDIT_CODE_DUPLICATE.getMessage());
        }
        saveManagerScopes(lead.getId(), distribution, operatorEmpId, now);
        saveTagSnapshots(lead.getId(), req.getTagIdList(), operatorEmpId, now);
        bindAttachments(lead.getId(), req.getAttachmentIds());
        return lead;
    }

    /** 编辑草稿；null 字段保持原值，显式提交的关系集合会全量替换。 */
    @Transactional
    public CustLead updateDraft(String id, LeadUpdateReqDTO req, String operatorEmpId,
                                String orgCode, boolean systemAdmin) {
        CustLead current = requireDraft(id);
        if (!systemAdmin && !bizScopeApi.checkWritePermission(operatorEmpId, BizType.LEAD,
                current.getOwnerOrgId(), current.getCreatedBy())) {
            throw error(CustomerErrorCode.LEAD_WRITE_FORBIDDEN);
        }
        copyNonNull(req, current);
        current.setUpdatedBy(operatorEmpId);
        current.setUpdatedTime(LocalDateTime.now());

        boolean distributionChanged = req.getDistributionMode() != null
                || req.getMainManagerId() != null || req.getManagerScopeIds() != null;
        if (distributionChanged) {
            String mode = req.getDistributionMode() == null ? current.getDistributionMode() : req.getDistributionMode();
            String owner = req.getMainManagerId() == null ? current.getMainManagerId() : req.getMainManagerId();
            Distribution distribution = resolveDistribution(mode, owner, req.getManagerScopeIds(),
                    operatorEmpId, orgCode, systemAdmin, current.getUnifiedCreditCode(), current.getCustName());
            applyDistribution(current, distribution);
            managerScopeMapper.delete(new LambdaQueryWrapper<CustLeadManagerScope>()
                    .eq(CustLeadManagerScope::getLeadId, id));
            saveManagerScopes(id, distribution, operatorEmpId, LocalDateTime.now());
        }
        if (req.getTagIdList() != null) {
            current.setTagIds(toTagJson(req.getTagIdList(), null));
            leadTagRelMapper.delete(new LambdaQueryWrapper<CustLeadTagRel>()
                    .eq(CustLeadTagRel::getLeadId, id));
            saveTagSnapshots(id, req.getTagIdList(), operatorEmpId, LocalDateTime.now());
        } else if (req.getTagIds() != null) {
            current.setTagIds(req.getTagIds());
        }
        leadMapper.updateById(current);
        bindAttachments(id, req.getAttachmentIds());
        return current;
    }

    /** 返回录入页和审批页共用的完整详情。 */
    public LeadRespDTO getDetail(String id) {
        CustLead lead = leadMapper.selectById(id);
        if (lead == null) {
            throw error(CustomerErrorCode.LEAD_NOT_FOUND);
        }
        return toDetail(lead);
    }

    /** 当前员工仅可从线索录入页读取本人创建的线索详情。 */
    public LeadRespDTO getCreatedDetail(String id, String operatorEmpId) {
        CustLead lead = leadMapper.selectCreatedById(id, operatorEmpId);
        if (lead == null) {
            throw error(CustomerErrorCode.LEAD_NOT_FOUND);
        }
        return toDetail(lead);
    }

    /** 按录入分配关系和 LEAD 数据范围读取详情，范围外统一按不存在处理。 */
    public LeadRespDTO getVisibleDetail(String id, String operatorEmpId, boolean systemAdmin) {
        DataScopeType scopeType = DataScopeType.ALL;
        Set<String> orgCodes = Set.of();
        if (!systemAdmin) {
            DataScopeContext context = bizScopeApi.buildScopeContext(
                    operatorEmpId, BizType.LEAD, BizAction.READ);
            scopeType = context == null || context.scopeType() == null
                    ? DataScopeType.SELF_CREATED : context.scopeType();
            if (scopeType == DataScopeType.ORG && context != null
                    && StringUtils.hasText(context.orgCode())) {
                orgCodes = Set.of(context.orgCode());
            } else if (scopeType == DataScopeType.ORG_SUBTREE && context != null
                    && context.orgSubtreeCodes() != null) {
                orgCodes = context.orgSubtreeCodes();
            }
        }
        CustLead lead = leadMapper.selectVisibleById(
                id, scopeType.getCode(), operatorEmpId, orgCodes);
        if (lead == null) {
            throw error(CustomerErrorCode.LEAD_NOT_FOUND);
        }
        return toDetail(lead);
    }

    private LeadRespDTO toDetail(CustLead lead) {
        String id = lead.getId();
        LeadRespDTO dto = new LeadRespDTO();
        BeanUtils.copyProperties(lead, dto);
        if (StringUtils.hasText(lead.getCreatedBy())) {
            dto.setCreatedByName(userApi.getUserName(lead.getCreatedBy()));
        }
        if (StringUtils.hasText(lead.getReviewedBy())) {
            dto.setReviewedByName(userApi.getUserName(lead.getReviewedBy()));
        }

        List<CustLeadManagerScope> scopes = managerScopeMapper.selectList(
                new LambdaQueryWrapper<CustLeadManagerScope>()
                        .eq(CustLeadManagerScope::getLeadId, id)
                        .orderByDesc(CustLeadManagerScope::getIsPrimary)
                        .orderByAsc(CustLeadManagerScope::getCreatedTime));
        List<LeadManagerScopeRespDTO> scopeDtos = new ArrayList<>();
        for (CustLeadManagerScope scope : scopes) {
            LeadManagerScopeRespDTO item = new LeadManagerScopeRespDTO();
            item.setManagerEmpId(scope.getManagerEmpId());
            item.setManagerName(userApi.getUserName(scope.getManagerEmpId()));
            item.setManagerOrgId(scope.getManagerOrgId());
            OrgDTO org = orgApi.getOrg(scope.getManagerOrgId());
            item.setManagerOrgName(org == null ? null : org.getOrgName());
            item.setAssignmentType(scope.getAssignmentType());
            item.setPrimary(Objects.equals(scope.getIsPrimary(), 1));
            scopeDtos.add(item);
        }
        dto.setManagerScopes(scopeDtos);

        List<CustLeadTagRel> tagRels = leadTagRelMapper.selectList(
                new LambdaQueryWrapper<CustLeadTagRel>()
                        .eq(CustLeadTagRel::getLeadId, id)
                        .orderByAsc(CustLeadTagRel::getCreatedTime));
        dto.setTags(tagRels.stream()
                .map(rel -> new LeadTagRespDTO(rel.getTagId(), rel.getTagNameSnapshot())).toList());
        List<FileObjectDTO> files = fileApi.listBizFiles("LEAD", id);
        dto.setAttachments(files == null ? List.of() : files);

        if (StringUtils.hasText(lead.getMainManagerId())) {
            dto.setMainManagerName(userApi.getUserName(lead.getMainManagerId()));
        }
        if (StringUtils.hasText(lead.getMainManagerOrgId())) {
            OrgDTO org = orgApi.getOrg(lead.getMainManagerOrgId());
            dto.setMainManagerOrgName(org == null ? null : org.getOrgName());
        }
        return dto;
    }

    /** 按统一社会信用代码优先、客户名称其次查询存量客户和主办权。 */
    public MainManagerLookupRespDTO lookupMainManager(String unifiedCreditCode, String custName) {
        LambdaQueryWrapper<CustMaster> query = new LambdaQueryWrapper<CustMaster>()
                .eq(CustMaster::getDeleted, 0);
        if (StringUtils.hasText(unifiedCreditCode)) {
            query.eq(CustMaster::getUnifiedCreditCode, unifiedCreditCode.trim());
        } else if (StringUtils.hasText(custName)) {
            query.eq(CustMaster::getCustName, custName.trim());
        } else {
            MainManagerLookupRespDTO empty = new MainManagerLookupRespDTO();
            empty.setExistingCustomer(false);
            empty.setHasMainOwnership(false);
            return empty;
        }
        CustMaster master = masterMapper.selectOne(query.last("LIMIT 1"));
        return toLookupResult(master);
    }

    private MainManagerLookupRespDTO toLookupResult(CustMaster master) {
        MainManagerLookupRespDTO dto = new MainManagerLookupRespDTO();
        dto.setExistingCustomer(master != null);
        dto.setHasMainOwnership(master != null && StringUtils.hasText(master.getMainManagerId()));
        if (master == null) {
            return dto;
        }
        dto.setCustomerId(master.getId());
        dto.setCustNo(master.getCustNo());
        dto.setCustName(master.getCustName());
        dto.setUnifiedCreditCode(master.getUnifiedCreditCode());
        dto.setMainManagerId(master.getMainManagerId());
        dto.setMainOrgId(master.getMainOrgId());
        if (StringUtils.hasText(master.getMainManagerId())) {
            dto.setMainManagerName(userApi.getUserName(master.getMainManagerId()));
        }
        if (StringUtils.hasText(master.getMainOrgId())) {
            OrgDTO org = orgApi.getOrg(master.getMainOrgId());
            dto.setMainOrgName(org == null ? null : org.getOrgName());
        }
        return dto;
    }

    private Distribution resolveDistribution(String requestedMode, String requestedOwner,
                                             List<String> requestedManagers, String operatorEmpId,
                                             String orgCode, boolean systemAdmin,
                                             String creditCode, String custName) {
        OrgDTO operatorOrg = orgApi.getOrg(orgCode);
        if (!systemAdmin && (operatorOrg == null || operatorOrg.getOrgLevel() == null
                || operatorOrg.getOrgLevel() >= 3)) {
            return new Distribution(OWNER, operatorEmpId, orgCode,
                    List.of(new Manager(operatorEmpId, orgCode, true)));
        }

        String mode = StringUtils.hasText(requestedMode) ? requestedMode.trim().toUpperCase() : PUBLIC;
        if (PUBLIC.equals(mode)) {
            return new Distribution(PUBLIC, null, null, List.of());
        }
        if (SCOPE.equals(mode)) {
            Set<String> uniqueIds = requestedManagers == null ? Set.of() : new LinkedHashSet<>(requestedManagers);
            if (uniqueIds.isEmpty()) {
                throw error(CustomerErrorCode.LEAD_DISTRIBUTION_INVALID);
            }
            List<Manager> managers = uniqueIds.stream().map(this::requireCustomerManager).toList();
            return new Distribution(SCOPE, null, null, managers);
        }
        if (OWNER.equals(mode) && StringUtils.hasText(requestedOwner)) {
            MainManagerLookupRespDTO lookup = lookupMainManager(creditCode, custName);
            if (!Boolean.TRUE.equals(lookup.getHasMainOwnership())
                    || !Objects.equals(requestedOwner, lookup.getMainManagerId())) {
                throw error(CustomerErrorCode.LEAD_OWNER_MISMATCH);
            }
            Manager owner = requireCustomerManager(requestedOwner);
            return new Distribution(OWNER, owner.empId(), owner.orgId(),
                    List.of(new Manager(owner.empId(), owner.orgId(), true)));
        }
        throw error(CustomerErrorCode.LEAD_DISTRIBUTION_INVALID);
    }

    private Manager requireCustomerManager(String empId) {
        UserDTO user = userApi.getUserByEmpId(empId);
        Set<String> roles = userApi.getUserRoleCodes(empId);
        if (user == null || Boolean.FALSE.equals(user.getEnabled()) || !StringUtils.hasText(user.getMainOrgCode())
                || !CustomerRoleCode.isCustomerManager(roles)) {
            throw error(CustomerErrorCode.LEAD_MANAGER_INVALID);
        }
        return new Manager(empId, user.getMainOrgCode(), false);
    }

    private void saveManagerScopes(String leadId, Distribution distribution,
                                   String operatorEmpId, LocalDateTime now) {
        for (Manager manager : distribution.managers()) {
            CustLeadManagerScope scope = new CustLeadManagerScope();
            scope.setId(uuid());
            scope.setLeadId(leadId);
            scope.setManagerEmpId(manager.empId());
            scope.setManagerOrgId(manager.orgId());
            scope.setAssignmentType(distribution.mode());
            scope.setIsPrimary(manager.primary() ? 1 : 0);
            scope.setCreatedBy(operatorEmpId);
            scope.setCreatedTime(now);
            managerScopeMapper.insert(scope);
        }
    }

    private void saveTagSnapshots(String leadId, List<String> tagIds,
                                  String operatorEmpId, LocalDateTime now) {
        if (tagIds == null) {
            return;
        }
        for (String tagId : new LinkedHashSet<>(tagIds)) {
            CustTag tag = tagMapper.selectById(tagId);
            if (tag == null || Objects.equals(tag.getDeleted(), 1)
                    || !TagStatus.ACTIVE.getCode().equals(tag.getStatus())) {
                throw error(CustomerErrorCode.TAG_NOT_FOUND);
            }
            CustLeadTagRel rel = new CustLeadTagRel();
            rel.setId(uuid());
            rel.setLeadId(leadId);
            rel.setTagId(tagId);
            rel.setTagNameSnapshot(tag.getTagName());
            rel.setCreatedBy(operatorEmpId);
            rel.setCreatedTime(now);
            leadTagRelMapper.insert(rel);
        }
    }

    private void bindAttachments(String leadId, List<String> attachmentIds) {
        if (attachmentIds == null) {
            return;
        }
        new LinkedHashSet<>(attachmentIds).stream().filter(StringUtils::hasText)
                .forEach(id -> fileApi.bindFile("LEAD", leadId, id, "ATTACHMENT"));
    }

    private void applyCreateFields(CustLead lead, LeadCreateReqDTO req) {
        lead.setLeadType(defaultValue(req.getLeadType(), "NEW_ACCOUNT"));
        lead.setCustNo(req.getCustNo());
        lead.setCustName(req.getCustName());
        lead.setUnifiedCreditCode(req.getUnifiedCreditCode());
        lead.setContactPerson(req.getContactPerson());
        lead.setContactMobile(req.getContactMobile());
        lead.setIndustry(req.getIndustry());
        lead.setGroupType(req.getGroupType());
        lead.setCustomerType(req.getCustomerType());
        lead.setIsKeystone(req.getIsKeystone());
        lead.setEnterpriseType(req.getEnterpriseType());
        lead.setGroupName(req.getGroupName());
        lead.setIsAccountOpened(req.getIsAccountOpened());
        lead.setTouchRestricted(req.getTouchRestricted() == null ? 1 : req.getTouchRestricted());
        lead.setCustomerDesc(req.getCustomerDesc());
        lead.setCreditAmount(req.getCreditAmount());
        lead.setCreditExposureAmount(req.getCreditExposureAmount());
        lead.setLeadSource(req.getLeadSource());
        lead.setRemark(req.getRemark());
    }

    private void copyNonNull(LeadUpdateReqDTO req, CustLead lead) {
        if (req.getLeadType() != null) lead.setLeadType(req.getLeadType());
        if (req.getCustNo() != null) lead.setCustNo(req.getCustNo());
        if (req.getCustName() != null) lead.setCustName(req.getCustName());
        if (req.getUnifiedCreditCode() != null) lead.setUnifiedCreditCode(req.getUnifiedCreditCode());
        if (req.getContactPerson() != null) lead.setContactPerson(req.getContactPerson());
        if (req.getContactMobile() != null) lead.setContactMobile(req.getContactMobile());
        if (req.getIndustry() != null) lead.setIndustry(req.getIndustry());
        if (req.getGroupType() != null) lead.setGroupType(req.getGroupType());
        if (req.getCustomerType() != null) lead.setCustomerType(req.getCustomerType());
        if (req.getIsKeystone() != null) lead.setIsKeystone(req.getIsKeystone());
        if (req.getEnterpriseType() != null) lead.setEnterpriseType(req.getEnterpriseType());
        if (req.getGroupName() != null) lead.setGroupName(req.getGroupName());
        if (req.getIsAccountOpened() != null) lead.setIsAccountOpened(req.getIsAccountOpened());
        if (req.getTouchRestricted() != null) lead.setTouchRestricted(req.getTouchRestricted());
        if (req.getCustomerDesc() != null) lead.setCustomerDesc(req.getCustomerDesc());
        if (req.getCreditAmount() != null) lead.setCreditAmount(req.getCreditAmount());
        if (req.getCreditExposureAmount() != null) lead.setCreditExposureAmount(req.getCreditExposureAmount());
        if (req.getLeadSource() != null) lead.setLeadSource(req.getLeadSource());
        if (req.getRemark() != null) lead.setRemark(req.getRemark());
    }

    private CustLead requireDraft(String id) {
        CustLead lead = leadMapper.selectById(id);
        if (lead == null) throw error(CustomerErrorCode.LEAD_NOT_FOUND);
        if (!LeadStatus.DRAFT.getCode().equals(lead.getLeadStatus())) {
            throw error(CustomerErrorCode.LEAD_EDIT_FORBIDDEN);
        }
        return lead;
    }

    private void applyDistribution(CustLead lead, Distribution distribution) {
        lead.setDistributionMode(distribution.mode());
        lead.setMainManagerId(distribution.ownerEmpId());
        lead.setMainManagerOrgId(distribution.ownerOrgId());
        lead.setAssignedTo(distribution.ownerEmpId());
    }

    private String toTagJson(List<String> ids, String legacy) {
        if (ids == null) return legacy;
        return ids.stream().filter(StringUtils::hasText).distinct()
                .map(id -> "\"" + id.replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private BizException error(CustomerErrorCode code) {
        return new BizException(code.getCode(), code.getMessage());
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record Manager(String empId, String orgId, boolean primary) { }
    private record Distribution(String mode, String ownerEmpId, String ownerOrgId,
                                List<Manager> managers) { }
}
