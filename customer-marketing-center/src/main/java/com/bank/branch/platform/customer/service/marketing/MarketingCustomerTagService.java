package com.bank.branch.platform.customer.service.marketing;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.marketing.tag.TagCreateRequest;
import com.bank.branch.platform.customer.dto.marketing.tag.TagUpdateRequest;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTag;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTagRel;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagRelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * 营销客户标签主数据服务。
 *
 * <p>该服务只维护正式标签字典和正式客户群查询；标签客户导入及审批分别由
 * {@link MarketingCustomerTagImportService} 和 {@link MarketingCustomerTagApprovalService} 负责。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingCustomerTagService {

    private static final Set<String> VIEW_STATUSES = Set.of(
            "ACTIVE", "DISABLED", "PENDING", "REJECTED", "EXCEPTION");

    private final MarketingCustomerTagMapper tagMapper;
    private final MarketingCustomerTagRelMapper relationMapper;

    /** 新增标签，固定以 PENDING + DISABLED 进入审批。 */
    @Transactional
    public MarketingCustomerTag create(TagCreateRequest request, String operatorEmpId, String ownerOrgId) {
        if (request == null || !StringUtils.hasText(request.getTagName())) {
            throw new BizException("CUST-40000", "标签名称不能为空");
        }
        String tagName = normalizeName(request.getTagName());
        if (tagMapper.selectByTagName(tagName) != null) {
            throw new BizException("CUST-40002", "标签名称已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        MarketingCustomerTag tag = new MarketingCustomerTag();
        tag.setTagName(tagName);
        tag.setTagCategory(trimToNull(request.getTagCategory()));
        tag.setTagType(trimToNull(request.getTagType()));
        tag.setTagPriority(request.getTagPriority() == null ? 0 : request.getTagPriority());
        tag.setDescription(trimToNull(request.getDescription()));
        tag.setExpiresAt(request.getExpiresAt());
        tag.setStatus("DISABLED");
        tag.setApprovalStatus("PENDING");
        tag.setRecordStatus("ACTIVE");
        tag.setOwnerOrgId(ownerOrgId);
        tag.setCreatedBy(operatorEmpId);
        tag.setCreatedTime(now);
        tag.setUpdatedBy(operatorEmpId);
        tag.setUpdatedTime(now);
        tag.setLockVersion(0);
        try {
            tagMapper.insert(tag);
        } catch (DuplicateKeyException ex) {
            throw new BizException("CUST-40002", "标签名称已存在", ex);
        }
        return tag;
    }

    /** 兼容服务层直接传字段的调用方式。 */
    public MarketingCustomerTag create(String tagName, String tagCategory, String tagType,
                                       Integer tagPriority, String description, LocalDate expiresAt,
                                       String operatorEmpId, String ownerOrgId) {
        TagCreateRequest request = new TagCreateRequest();
        request.setTagName(tagName);
        request.setTagCategory(tagCategory);
        request.setTagType(tagType);
        request.setTagPriority(tagPriority);
        request.setDescription(description);
        request.setExpiresAt(expiresAt);
        return create(request, operatorEmpId, ownerOrgId);
    }

    /** 查询标签分页，客户数量由 Mapper 从正式有效关系中统计。 */
    public PageResult<MarketingCustomerTag> list(String keyword, String category, String status,
                                                  String approvalStatus, int pageNo, int pageSize) {
        return list(keyword, category, null, status, approvalStatus, null, pageNo, pageSize);
    }

    /**
     * 查询标签分页，支持标签类型精确筛选和页面级组合状态筛选；旧状态参数继续按 AND 语义生效。
     */
    public PageResult<MarketingCustomerTag> list(String keyword, String category, String tagType,
                                                  String status, String approvalStatus,
                                                  String viewStatus, int pageNo, int pageSize) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.min(Math.max(1, pageSize), 100);
        int offset = (safePageNo - 1) * safePageSize;
        String normalizedViewStatus = normalizeViewStatus(viewStatus);
        return PageResult.of(safePageNo, safePageSize,
                tagMapper.countPage(trimToNull(keyword), trimToNull(category), trimToNull(tagType),
                        trimToNull(status), trimToNull(approvalStatus), normalizedViewStatus),
                tagMapper.selectPage(trimToNull(keyword), trimToNull(category), trimToNull(tagType),
                        trimToNull(status), trimToNull(approvalStatus), normalizedViewStatus,
                        offset, safePageSize));
    }

    /** 兼容常见的 listPage 命名。 */
    public PageResult<MarketingCustomerTag> listPage(String keyword, String category, String status,
                                                      String approvalStatus, int pageNo, int pageSize) {
        return list(keyword, category, status, approvalStatus, pageNo, pageSize);
    }

    /** 查询标签详情。 */
    public MarketingCustomerTag get(Long tagId) {
        MarketingCustomerTag tag = tagMapper.selectById(tagId);
        if (tag == null || !"ACTIVE".equalsIgnoreCase(tag.getRecordStatus())) {
            throw new BizException("CUST-40401", "标签不存在");
        }
        return tag;
    }

    /** 兼容 getById 命名。 */
    public MarketingCustomerTag getById(Long tagId) {
        return get(tagId);
    }

    /** 查询正式有效客户群，导入审批中的客户不会提前出现在此列表。 */
    public PageResult<MarketingCustomerTagRel> listCustomers(Long tagId, String keyword,
                                                              int pageNo, int pageSize) {
        requireTag(tagId);
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.min(Math.max(1, pageSize), 100);
        int offset = (safePageNo - 1) * safePageSize;
        return PageResult.of(safePageNo, safePageSize,
                relationMapper.countActiveByTagId(tagId, trimToNull(keyword)),
                relationMapper.selectActivePage(tagId, trimToNull(keyword), offset, safePageSize));
    }

    /** 修改标签属性；正式关系和审批状态不由普通编辑接口改变。 */
    @Transactional
    public MarketingCustomerTag update(Long tagId, TagUpdateRequest request, String operatorEmpId) {
        MarketingCustomerTag current = get(tagId);
        if (request == null || !StringUtils.hasText(request.getTagName())) {
            throw new BizException("CUST-40000", "标签名称不能为空");
        }
        String normalizedName = normalizeName(request.getTagName());
        MarketingCustomerTag sameName = tagMapper.selectByTagName(normalizedName);
        if (sameName != null && !tagId.equals(sameName.getId())) {
            throw new BizException("CUST-40002", "标签名称已存在");
        }
        if (request.getLockVersion() != null && current.getLockVersion() != null
                && !request.getLockVersion().equals(current.getLockVersion())) {
            throw new BizException("CUST-40903", "标签已被其他人员修改，请刷新后重试");
        }
        current.setTagName(normalizedName);
        current.setTagCategory(trimToNull(request.getTagCategory()));
        current.setTagType(trimToNull(request.getTagType()));
        if (request.getTagPriority() != null) current.setTagPriority(request.getTagPriority());
        current.setDescription(trimToNull(request.getDescription()));
        current.setExpiresAt(request.getExpiresAt());
        current.setUpdatedBy(operatorEmpId);
        current.setUpdatedTime(LocalDateTime.now());
        if (tagMapper.updateById(current) == 0) {
            throw new BizException("CUST-40903", "标签已被其他人员修改，请刷新后重试");
        }
        return current;
    }

    /** 标签审批通过，并在通过时启用标签。 */
    @Transactional
    public MarketingCustomerTag approveTag(Long tagId, String reviewerEmpId) {
        return approveTag(tagId, reviewerEmpId, true);
    }

    /** 标签审批通过；非公司部审核人不得审批他人创建的标签。 */
    @Transactional
    public MarketingCustomerTag approveTag(Long tagId, String reviewerEmpId, boolean reviewerAllowed) {
        MarketingCustomerTag tag = requireTag(tagId);
        assertReviewer(tag, reviewerEmpId, reviewerAllowed);
        if ("APPROVED".equalsIgnoreCase(tag.getApprovalStatus())) return tag;
        if (!"PENDING".equalsIgnoreCase(tag.getApprovalStatus())) {
            throw new BizException("CUST-40907", "标签当前状态不允许审批");
        }
        LocalDateTime now = LocalDateTime.now();
        tag.setApprovalStatus("APPROVED");
        tag.setStatus("ENABLED");
        tag.setReviewedBy(reviewerEmpId);
        tag.setReviewedTime(now);
        tag.setRejectReason(null);
        tag.setUpdatedBy(reviewerEmpId);
        tag.setUpdatedTime(now);
        if (tagMapper.updateById(tag) == 0) {
            throw new BizException("CUST-40903", "标签审批状态已变化，请刷新后重试");
        }
        return tag;
    }

    /** 标签审批驳回，必须保存原因。 */
    @Transactional
    public MarketingCustomerTag rejectTag(Long tagId, String reason, String reviewerEmpId) {
        return rejectTag(tagId, reason, reviewerEmpId, true);
    }

    /** 标签审批驳回；已驳回标签不得参与正式关系导入。 */
    @Transactional
    public MarketingCustomerTag rejectTag(Long tagId, String reason, String reviewerEmpId,
                                          boolean reviewerAllowed) {
        MarketingCustomerTag tag = requireTag(tagId);
        assertReviewer(tag, reviewerEmpId, reviewerAllowed);
        if (!StringUtils.hasText(reason)) {
            throw new BizException("CUST-40000", "标签驳回原因不能为空");
        }
        if (!"PENDING".equalsIgnoreCase(tag.getApprovalStatus())) {
            throw new BizException("CUST-40907", "标签当前状态不允许驳回");
        }
        LocalDateTime now = LocalDateTime.now();
        tag.setApprovalStatus("REJECTED");
        tag.setStatus("DISABLED");
        tag.setReviewedBy(reviewerEmpId);
        tag.setReviewedTime(now);
        tag.setRejectReason(reason.trim());
        tag.setUpdatedBy(reviewerEmpId);
        tag.setUpdatedTime(now);
        if (tagMapper.updateById(tag) == 0) {
            throw new BizException("CUST-40903", "标签审批状态已变化，请刷新后重试");
        }
        return tag;
    }

    /** 检查标签可接收客户导入。 */
    public MarketingCustomerTag requireImportable(Long tagId) {
        MarketingCustomerTag tag = requireTag(tagId);
        if (!"APPROVED".equalsIgnoreCase(tag.getApprovalStatus())
                || !"ENABLED".equalsIgnoreCase(tag.getStatus())) {
            throw new BizException("CUST-40902", "标签尚未审批通过或已停用");
        }
        if (tag.getExpiresAt() != null && tag.getExpiresAt().isBefore(LocalDate.now())) {
            throw new BizException("CUST-40902", "标签已失效");
        }
        return tag;
    }

    /** 只返回存在且为有效记录的标签。 */
    public MarketingCustomerTag requireTag(Long tagId) {
        if (tagId == null) throw new BizException("CUST-40401", "标签不存在");
        MarketingCustomerTag tag = tagMapper.selectById(tagId);
        if (tag == null || (tag.getRecordStatus() != null
                && !"ACTIVE".equalsIgnoreCase(tag.getRecordStatus()))) {
            throw new BizException("CUST-40401", "标签不存在");
        }
        return tag;
    }

    private void assertReviewer(MarketingCustomerTag tag, String reviewerEmpId, boolean reviewerAllowed) {
        if (!reviewerAllowed && !reviewerEmpId.equals(tag.getCreatedBy())) {
            throw new BizException("CUST-40301", "无权审批该标签");
        }
    }

    private String normalizeName(String value) {
        return value == null ? null : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private String normalizeViewStatus(String viewStatus) {
        String normalized = trimToNull(viewStatus);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!VIEW_STATUSES.contains(normalized)) {
            throw new BizException("CUST-40000", "标签页面状态不合法");
        }
        return normalized;
    }
}
