package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.TagStatus;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 标签管理业务服务。
 * <p>
 * 负责标签的 CRUD、启禁用及列表查询，是标签管理域的核心逻辑入口。
 * 所有数据库操作委托给 {@link CustTagMapper}，不直接调用其他模块的 Mapper。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    private final CustTagMapper tagMapper;
    private final UserApi userApi;

    /**
     * 创建新标签。
     * <p>
     * 先做名称唯一性预检，通过后插入，主键由本方法生成（UUID 32位）。
     * 新标签默认状态为 ACTIVE。
     * </p>
     *
     * @param tagName       标签名称（唯一）
     * @param description   标签描述（可为 null）
     * @param tagCategory   标签分类（可为 null）
     * @param tagPriority   排序优先级（可为 null，越大越靠前）
     * @param operatorEmpId 操作人员工工号
     * @return 插入成功后完整的标签实体
     */
    public CustTag createTag(String tagName, String description,
                              String tagCategory, Integer tagPriority, String operatorEmpId) {
        return createTag(tagName, description, tagCategory, tagPriority,
                null, null, null, operatorEmpId);
    }

    /** 创建需要审核的 V2 客户标签。 */
    public CustTag createTag(String tagName, String description,
                             String tagCategory, Integer tagPriority, String tagType,
                             java.time.LocalDate expiresAt, String ownerOrgId, String operatorEmpId) {
        log.info("[TagService.createTag] tagName={}, operator={}", tagName, operatorEmpId);

        // 名称唯一性校验
        if (tagMapper.selectByTagName(tagName) != null) {
            throw new BizException(CustomerErrorCode.TAG_NAME_DUPLICATE.getCode(),
                    CustomerErrorCode.TAG_NAME_DUPLICATE.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        CustTag entity = new CustTag();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setTagName(tagName);
        entity.setDescription(description);
        entity.setTagCategory(tagCategory);
        entity.setTagPriority(tagPriority);
        entity.setTagType(tagType);
        entity.setExpiresAt(expiresAt);
        entity.setOwnerOrgId(ownerOrgId);
        // 新建标签必须先审核，通过前不可用于打标。
        entity.setStatus(TagStatus.DISABLED.getCode());
        entity.setApprovalStatus("PENDING");
        entity.setCreatedBy(operatorEmpId);
        entity.setCreatedTime(now);
        entity.setUpdatedBy(operatorEmpId);
        entity.setUpdatedTime(now);
        entity.setDeleted(0);

        tagMapper.insert(entity);
        log.info("[TagService.createTag] created tagId={}", entity.getId());
        return entity;
    }

    /** 审核通过待审核标签并启用。 */
    public void approve(String id, String reviewerEmpId) {
        approve(id, reviewerEmpId, true);
    }

    /** 创建人可自审，非本人标签只允许公司部审核人员办理。 */
    public void approve(String id, String reviewerEmpId, boolean companyReviewer) {
        CustTag existing = requireExists(id);
        assertReviewPermission(existing, reviewerEmpId, companyReviewer);
        assertPending(existing);
        CustTag update = new CustTag();
        update.setId(id);
        update.setApprovalStatus("APPROVED");
        update.setStatus(TagStatus.ACTIVE.getCode());
        update.setReviewedBy(reviewerEmpId);
        update.setReviewedTime(LocalDateTime.now());
        update.setRejectReason(null);
        update.setUpdatedBy(reviewerEmpId);
        update.setUpdatedTime(LocalDateTime.now());
        tagMapper.updateById(update);
    }

    /** 退回待审核标签，退回原因必须明确记录。 */
    public void reject(String id, String reason, String reviewerEmpId) {
        reject(id, reason, reviewerEmpId, true);
    }

    /** 按审核权限退回待审核标签。 */
    public void reject(String id, String reason, String reviewerEmpId, boolean companyReviewer) {
        CustTag existing = requireExists(id);
        assertReviewPermission(existing, reviewerEmpId, companyReviewer);
        assertPending(existing);
        if (reason == null || reason.isBlank()) {
            throw new BizException(CustomerErrorCode.TAG_REJECT_REASON_REQUIRED.getCode(),
                    CustomerErrorCode.TAG_REJECT_REASON_REQUIRED.getMessage());
        }
        CustTag update = new CustTag();
        update.setId(id);
        update.setApprovalStatus("REJECTED");
        update.setStatus(TagStatus.DISABLED.getCode());
        update.setReviewedBy(reviewerEmpId);
        update.setReviewedTime(LocalDateTime.now());
        update.setRejectReason(reason.trim());
        update.setUpdatedBy(reviewerEmpId);
        update.setUpdatedTime(LocalDateTime.now());
        tagMapper.updateById(update);
    }

    /**
     * 更新标签信息。
     * <p>
     * tagName 仍需唯一性检查（排除自身）。
     * </p>
     *
     * @param id            标签 ID
     * @param tagName       新标签名称
     * @param description   新描述
     * @param tagCategory   新分类
     * @param tagPriority   新优先级
     * @param operatorEmpId 操作人
     * @return 更新后的标签实体
     */
    public CustTag updateTag(String id, String tagName, String description,
                              String tagCategory, Integer tagPriority, String operatorEmpId) {
        log.info("[TagService.updateTag] id={}, tagName={}, operator={}", id, tagName, operatorEmpId);

        CustTag existing = requireExists(id);

        // 名称唯一性校验（排除自身）
        if (tagName != null && !tagName.equals(existing.getTagName())) {
            CustTag nameConflict = tagMapper.selectByTagName(tagName);
            if (nameConflict != null && !nameConflict.getId().equals(id)) {
                throw new BizException(CustomerErrorCode.TAG_NAME_DUPLICATE.getCode(),
                        CustomerErrorCode.TAG_NAME_DUPLICATE.getMessage());
            }
        }

        CustTag updateEntity = new CustTag();
        updateEntity.setId(id);
        updateEntity.setTagName(tagName);
        updateEntity.setDescription(description);
        updateEntity.setTagCategory(tagCategory);
        updateEntity.setTagPriority(tagPriority);
        updateEntity.setUpdatedBy(operatorEmpId);
        updateEntity.setUpdatedTime(LocalDateTime.now());

        tagMapper.updateById(updateEntity);

        // 返回更新后的完整对象（合并变更到原实体）
        existing.setTagName(tagName != null ? tagName : existing.getTagName());
        existing.setDescription(description != null ? description : existing.getDescription());
        existing.setTagCategory(tagCategory != null ? tagCategory : existing.getTagCategory());
        existing.setTagPriority(tagPriority != null ? tagPriority : existing.getTagPriority());
        existing.setUpdatedBy(operatorEmpId);
        existing.setUpdatedTime(updateEntity.getUpdatedTime());
        log.info("[TagService.updateTag] updated tagId={}", id);
        return existing;
    }

    /**
     * 切换标签的启用/停用状态。
     *
     * @param id            标签 ID
     * @param status        目标状态（ACTIVE / DISABLED）
     * @param operatorEmpId 操作人
     */
    public void toggleStatus(String id, String status, String operatorEmpId) {
        log.info("[TagService.toggleStatus] id={}, status={}, operator={}", id, status, operatorEmpId);

        CustTag existing = requireExists(id);
        if (TagStatus.ACTIVE.getCode().equals(status)
                && existing.getApprovalStatus() != null
                && !"APPROVED".equals(existing.getApprovalStatus())) {
            throw new BizException(CustomerErrorCode.TAG_NOT_APPROVED.getCode(),
                    CustomerErrorCode.TAG_NOT_APPROVED.getMessage());
        }

        CustTag updateEntity = new CustTag();
        updateEntity.setId(id);
        updateEntity.setStatus(status);
        updateEntity.setUpdatedBy(operatorEmpId);
        updateEntity.setUpdatedTime(LocalDateTime.now());

        tagMapper.updateById(updateEntity);
    }

    /** 批量禁用标签；先校验全部标签存在，再在同一事务中更新。 */
    @Transactional
    public void batchDisable(List<String> ids, String operatorEmpId) {
        List<String> normalizedIds = normalizeTagIds(ids);
        normalizedIds.forEach(this::requireExists);
        LocalDateTime now = LocalDateTime.now();
        normalizedIds.forEach(id -> {
            CustTag update = new CustTag();
            update.setId(id);
            update.setStatus(TagStatus.DISABLED.getCode());
            update.setUpdatedBy(operatorEmpId);
            update.setUpdatedTime(now);
            tagMapper.updateById(update);
        });
    }

    /** 批量软删除标签，保留客户关联历史记录。 */
    @Transactional
    public void batchDelete(List<String> ids, String operatorEmpId) {
        List<String> normalizedIds = normalizeTagIds(ids);
        normalizedIds.forEach(this::requireExists);
        LocalDateTime now = LocalDateTime.now();
        normalizedIds.forEach(id -> {
            CustTag update = new CustTag();
            update.setId(id);
            update.setStatus(TagStatus.DISABLED.getCode());
            update.setDeleted(1);
            update.setUpdatedBy(operatorEmpId);
            update.setUpdatedTime(now);
            tagMapper.updateById(update);
        });
    }

    /**
     * 按 ID 查询标签，不存在时抛出 BizException。
     *
     * @param id 标签 ID
     * @return 标签实体
     */
    public CustTag getById(String id) {
        log.debug("[TagService.getById] id={}", id);
        return requireExists(id);
    }

    /**
     * 分页查询标签列表。
     * <p>
     * offset = (pageNo - 1) * pageSize
     * </p>
     *
     * @param keyword  关键词（模糊匹配 tagName），可为 null
     * @param status   状态过滤，可为 null
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<CustTag> listPage(String keyword, String status, int pageNo, int pageSize) {
        log.debug("[TagService.listPage] keyword={}, status={}, pageNo={}, pageSize={}", keyword, status, pageNo, pageSize);

        int offset = (pageNo - 1) * pageSize;
        List<CustTag> records = tagMapper.selectPage(keyword, status, offset, pageSize);
        long total = tagMapper.countPage(keyword, status);
        fillOperatorNames(records);

        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 查询标签审核页数据。
     *
     * <p>待审核页返回所有待办；审核记录页由 Mapper 强制限定为当前审核人。</p>
     */
    public PageResult<CustTag> listReviewPage(String keyword, String reviewTab,
                                              int pageNo, int pageSize, String reviewerEmpId) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePageNo - 1) * safePageSize;
        List<CustTag> records = tagMapper.selectReviewPage(
                keyword, reviewTab, reviewerEmpId, offset, safePageSize);
        long total = tagMapper.countReviewPage(keyword, reviewTab, reviewerEmpId);
        fillOperatorNames(records);
        return PageResult.of(safePageNo, safePageSize, total, records);
    }

    /**
     * 查询所有启用状态的标签（用于打标下拉选择）。
     *
     * @return 启用的标签列表，按 tagPriority 降序
     */
    public List<CustTag> listEnabled() {
        log.debug("[TagService.listEnabled] called");
        return tagMapper.selectEnabled();
    }

    // ============================= 私有辅助方法 =============================

    /** 批量补充创建人、审核人姓名，避免标签审核列表逐条查询用户。 */
    private void fillOperatorNames(List<CustTag> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<String> empIds = records.stream()
                .flatMap(tag -> java.util.stream.Stream.of(tag.getCreatedBy(), tag.getReviewedBy()))
                .filter(empId -> empId != null && !empId.isBlank())
                .distinct()
                .toList();
        if (empIds.isEmpty()) {
            return;
        }
        List<UserDTO> users = userApi.getUserByEmpIds(empIds);
        Map<String, String> names = new HashMap<>();
        if (users != null) {
            users.stream().filter(Objects::nonNull)
                    .filter(user -> user.getEmpId() != null)
                    .forEach(user -> names.put(user.getEmpId(), user.getDisplayName()));
        }
        records.forEach(tag -> {
            tag.setCreatedByName(names.get(tag.getCreatedBy()));
            tag.setReviewedByName(names.get(tag.getReviewedBy()));
        });
    }

    private List<String> normalizeTagIds(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    /**
     * 断言标签存在，不存在时抛出 BizException。
     * <p>
     * 避免在多个方法中重复查询+判空逻辑。
     * </p>
     *
     * @param id 标签 ID
     * @return 已存在的标签实体
     */
    private CustTag requireExists(String id) {
        CustTag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BizException(CustomerErrorCode.TAG_NOT_FOUND.getCode(),
                    CustomerErrorCode.TAG_NOT_FOUND.getMessage());
        }
        return tag;
    }

    private void assertPending(CustTag tag) {
        if (!"PENDING".equals(tag.getApprovalStatus())) {
            throw new BizException(CustomerErrorCode.TAG_REVIEW_STATUS_CONFLICT.getCode(),
                    CustomerErrorCode.TAG_REVIEW_STATUS_CONFLICT.getMessage());
        }
    }

    private void assertReviewPermission(CustTag tag, String reviewerEmpId, boolean companyReviewer) {
        if (!companyReviewer && !java.util.Objects.equals(tag.getCreatedBy(), reviewerEmpId)) {
            throw new BizException(CustomerErrorCode.TAG_REVIEW_FORBIDDEN.getCode(),
                    CustomerErrorCode.TAG_REVIEW_FORBIDDEN.getMessage());
        }
    }
}
