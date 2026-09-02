package com.bank.branch.platform.customer.service.marketing;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.marketing.tag.TagApprovalResult;
import com.bank.branch.platform.customer.dto.marketing.tag.TagApprovalSummary;
import com.bank.branch.platform.customer.dto.marketing.tag.TagCustomerBatchApprovalRequest;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTag;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTagImportBatch;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTagImportDetail;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTagRel;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagImportBatchMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagImportDetailMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagRelMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 页面六标签和标签所属客户的审批状态机。 */
@Service
@RequiredArgsConstructor
public class MarketingCustomerTagApprovalService {

    private final MarketingCustomerTagMapper tagMapper;
    private final MarketingCustomerTagImportBatchMapper batchMapper;
    private final MarketingCustomerTagImportDetailMapper detailMapper;
    private final MarketingCustomerTagRelMapper relationMapper;
    private final MarketingCustomerInfoMapper customerMapper;
    private final MarketingLeadInfoMapper leadMapper;
    private final MarketingCustomerTagService tagService;

    /** 按标签聚合待审批；只暴露存在标签待审或客户待审的标签。 */
    public PageResult<TagApprovalSummary> pending(String keyword, int pageNo, int pageSize) {
        List<MarketingCustomerTag> tags = tagMapper.selectList(new QueryWrapper<MarketingCustomerTag>()
                .eq("record_status", "ACTIVE")
                .and(w -> w.eq("approval_status", "PENDING").or().inSql("id",
                        "SELECT b.tag_id FROM MARKETING_CUSTOMER_TAG_IMPORT_BATCH b "
                                + "WHERE b.record_status='ACTIVE' AND b.pending_approval_count > 0"))
                .like(StringUtils.hasText(keyword), "tag_name", keyword)
                .orderByDesc("created_time"));
        List<TagApprovalSummary> summaries = new ArrayList<>();
        for (MarketingCustomerTag tag : tags) {
            TagApprovalSummary summary = new TagApprovalSummary();
            summary.setTag(tag);
            List<MarketingCustomerTagImportBatch> batches = batchMapper.selectList(
                    new QueryWrapper<MarketingCustomerTagImportBatch>()
                            .eq("tag_id", tag.getId()).eq("record_status", "ACTIVE")
                            .notIn("status", "COMPLETED", "CANCELLED").orderByDesc("import_time"));
            summary.setBatches(batches);
            summary.setPendingCustomerCount(batches.stream().mapToInt(b -> value(b.getPendingApprovalCount())).sum());
            summary.setApprovedCustomerCount(batches.stream().mapToInt(b -> value(b.getApprovedCount())).sum());
            summary.setRejectedCustomerCount(batches.stream().mapToInt(b -> value(b.getRejectedCount())).sum());
            summaries.add(summary);
        }
        int pn = Math.max(1, pageNo);
        int ps = Math.min(Math.max(1, pageSize), 100);
        int from = Math.min((pn - 1) * ps, summaries.size());
        int to = Math.min(from + ps, summaries.size());
        return PageResult.of(pn, ps, summaries.size(), summaries.subList(from, to));
    }

    public PageResult<MarketingCustomerTagImportDetail> pendingCustomers(Long tagId, String keyword,
                                                                          int pageNo, int pageSize) {
        tagService.requireTag(tagId);
        int pn = Math.max(1, pageNo);
        int ps = Math.min(Math.max(1, pageSize), 100);
        List<MarketingCustomerTagImportDetail> records = detailMapper.selectPendingByTagId(
                tagId, keyword, (pn - 1) * ps, ps);
        long total = countPending(tagId, keyword);
        return PageResult.of(pn, ps, total, records);
    }

    /** 审批记录仅按当前审批人工号查询，不接收任意 reviewer 参数。 */
    public PageResult<Object> history(String reviewerEmpId, String keyword, int pageNo, int pageSize) {
        int pn = Math.max(1, pageNo);
        int ps = Math.min(Math.max(1, pageSize), 100);
        List<Object> records = new ArrayList<>();
        records.addAll(tagMapper.selectList(new QueryWrapper<MarketingCustomerTag>()
                .eq("reviewed_by", reviewerEmpId).eq("record_status", "ACTIVE")
                .like(StringUtils.hasText(keyword), "tag_name", keyword)
                .orderByDesc("reviewed_time").last("LIMIT " + ps)));
        if (records.size() < ps) {
            records.addAll(detailMapper.selectHistoryByReviewer(reviewerEmpId, keyword, 0, ps - records.size()));
        }
        long tagCount = tagMapper.selectCount(new QueryWrapper<MarketingCustomerTag>()
                .eq("reviewed_by", reviewerEmpId).eq("record_status", "ACTIVE"));
        return PageResult.of(pn, ps, tagCount + detailMapper.countHistoryByReviewer(reviewerEmpId, keyword), records);
    }

    public MarketingCustomerTag tagDetail(Long tagId) { return tagService.requireTag(tagId); }

    @Transactional
    public MarketingCustomerTag approveTag(Long tagId, String reviewerEmpId) {
        return tagService.approveTag(tagId, reviewerEmpId, true);
    }

    @Transactional
    public MarketingCustomerTag rejectTag(Long tagId, String reason, String reviewerEmpId) {
        return tagService.rejectTag(tagId, reason, reviewerEmpId, true);
    }

    @Transactional
    public TagApprovalResult approve(TagCustomerBatchApprovalRequest request, String reviewerEmpId) {
        Selection selection = selection(request);
        MarketingCustomerTag tag = tagService.requireTag(selection.batch().getTagId());
        if (!"APPROVED".equals(tag.getApprovalStatus())) {
            if (!request.isApproveTag()) {
                throw new TagApprovalRequiredException(tag.getId(), tag.getTagName(),
                        selection.batch().getId(), selection.details().stream().map(MarketingCustomerTagImportDetail::getId).toList());
            }
            tag = tagService.approveTag(tag.getId(), reviewerEmpId, true);
        }
        TagApprovalResult result = new TagApprovalResult();
        for (MarketingCustomerTagImportDetail detail : selection.details()) {
            if (!"VALID".equals(detail.getValidationStatus()) || !"PENDING".equals(detail.getApprovalStatus())) {
                result.setSkippedCount(result.getSkippedCount() + 1);
                result.getItems().add(new TagApprovalResult.Item(detail.getId(), "SKIPPED", "明细已处理或不可审批"));
                continue;
            }
            approveDetail(detail, selection.batch(), tag, reviewerEmpId);
            result.setSuccessCount(result.getSuccessCount() + 1);
            result.getItems().add(new TagApprovalResult.Item(detail.getId(), "APPROVED", "审批通过"));
        }
        refreshBatch(selection.batch(), reviewerEmpId);
        return result;
    }

    public TagApprovalResult approveOne(Long detailId, boolean approveTag, String opinion, String reviewerEmpId) {
        MarketingCustomerTagImportDetail detail = detailMapper.selectById(detailId);
        if (detail == null) throw error("CUST-40403", "标签客户审批明细不存在");
        TagCustomerBatchApprovalRequest request = new TagCustomerBatchApprovalRequest();
        request.setBatchId(detail.getBatchId());
        request.setDetailIds(List.of(detailId));
        request.setApproveTag(approveTag);
        request.setOpinion(opinion);
        return approve(request, reviewerEmpId);
    }

    @Transactional
    public TagApprovalResult reject(TagCustomerBatchApprovalRequest request, String reviewerEmpId) {
        if (request == null || !StringUtils.hasText(request.getOpinion())) {
            throw error("CUST-40000", "客户驳回原因不能为空");
        }
        Selection selection = selection(request);
        TagApprovalResult result = new TagApprovalResult();
        for (MarketingCustomerTagImportDetail detail : selection.details()) {
            if (!"PENDING".equals(detail.getApprovalStatus())) {
                result.setSkippedCount(result.getSkippedCount() + 1);
                result.getItems().add(new TagApprovalResult.Item(detail.getId(), "SKIPPED", "明细已处理"));
                continue;
            }
            detail.setApprovalStatus("REJECTED");
            detail.setReviewedBy(reviewerEmpId);
            detail.setReviewedTime(LocalDateTime.now());
            detail.setRejectReason(request.getOpinion().trim());
            detailMapper.updateById(detail);
            if (detail.getGeneratedLeadId() != null) {
                leadMapper.updateStatusIf(detail.getGeneratedLeadId(), "IN_APPROVAL", "REJECTED",
                        reviewerEmpId, request.getOpinion().trim());
            }
            result.setSuccessCount(result.getSuccessCount() + 1);
            result.getItems().add(new TagApprovalResult.Item(detail.getId(), "REJECTED", "审批驳回"));
        }
        refreshBatch(selection.batch(), reviewerEmpId);
        return result;
    }

    public TagApprovalResult rejectOne(Long detailId, String reason, String reviewerEmpId) {
        MarketingCustomerTagImportDetail detail = detailMapper.selectById(detailId);
        if (detail == null) throw error("CUST-40403", "标签客户审批明细不存在");
        TagCustomerBatchApprovalRequest request = new TagCustomerBatchApprovalRequest();
        request.setBatchId(detail.getBatchId());
        request.setDetailIds(List.of(detailId));
        request.setOpinion(reason);
        return reject(request, reviewerEmpId);
    }

    private Selection selection(TagCustomerBatchApprovalRequest request) {
        if (request == null) throw error("CUST-40000", "审批参数不能为空");
        MarketingCustomerTagImportBatch batch;
        List<MarketingCustomerTagImportDetail> details;
        if (request.getBatchId() != null) {
            batch = batchMapper.selectById(request.getBatchId());
            if (batch == null || "CANCELLED".equals(batch.getStatus())) throw error("CUST-40402", "标签导入批次不存在");
            if (request.isAllPending()) {
                details = detailMapper.selectByBatchId(batch.getId()).stream()
                        .filter(d -> "PENDING".equals(d.getApprovalStatus())).toList();
            } else {
                if (request.getDetailIds() == null || request.getDetailIds().isEmpty()) {
                    throw error("CUST-40000", "请选择待审批客户");
                }
                details = detailMapper.selectByBatchIdAndIds(batch.getId(), request.getDetailIds());
                if (details == null || details.size() != request.getDetailIds().stream().distinct().count()) {
                    throw error("CUST-40301", "审批明细不属于当前批次或已不可见");
                }
            }
        } else {
            if (request.getTagId() == null || !request.isAllPending()) {
                throw error("CUST-40000", "批量审批必须指定批次，或按标签选择全部待审批客户");
            }
            details = detailMapper.selectPendingByTagId(request.getTagId(), null, 0, 5000);
            if (details.isEmpty()) throw error("CUST-40402", "没有待审批客户");
            batch = batchMapper.selectById(details.get(0).getBatchId());
            // 全部通过跨批次时需要逐批状态收敛，接口层应分批调用；这里拒绝混批以免破坏 REPLACE 原子性。
            if (details.stream().anyMatch(d -> !Objects.equals(d.getBatchId(), batch.getId()))) {
                throw error("CUST-40907", "该标签存在多个待审批批次，请按批次处理");
            }
        }
        return new Selection(batch, details);
    }

    private void approveDetail(MarketingCustomerTagImportDetail detail,
                               MarketingCustomerTagImportBatch batch,
                               MarketingCustomerTag tag,
                               String reviewerEmpId) {
        MarketingLeadInfo lead = detail.getGeneratedLeadId() == null ? null
                : leadMapper.selectActiveById(detail.getGeneratedLeadId());
        MarketingCustomerInfo current = detail.getMatchedCustomerId() == null ? null
                : customerMapper.selectActiveById(detail.getMatchedCustomerId());
        if (lead != null && current != null && lead.getBaseCustomerProfileVersion() != null
                && !Objects.equals(lead.getBaseCustomerProfileVersion(), current.getProfileVersion())) {
            throw error("CUST-40903", "客户主档已发生变化，请刷新后重新确认");
        }
        MarketingCustomerInfo customer = assembleCustomer(detail, reviewerEmpId);
        detail.setMatchedCustomerId(customer.getId());
        detail.setApprovalStatus("APPROVED");
        detail.setReviewedBy(reviewerEmpId);
        detail.setReviewedTime(LocalDateTime.now());
        detail.setRejectReason(null);
        if ("APPEND".equals(batch.getImportMode())) {
            MarketingCustomerTagRel relation = activateRelation(customer.getId(), tag.getId(), batch, reviewerEmpId);
            detail.setLoadedFlag(1);
            detail.setLoadedRelId(relation.getId());
            detail.setLoadedTime(LocalDateTime.now());
        }
        detailMapper.updateById(detail);
        if (detail.getGeneratedLeadId() != null) {
            leadMapper.updateStatusIf(detail.getGeneratedLeadId(), "IN_APPROVAL", "APPROVED", reviewerEmpId, null);
        }
    }

    private MarketingCustomerInfo assembleCustomer(MarketingCustomerTagImportDetail detail, String operatorEmpId) {
        MarketingCustomerInfo customer = detail.getMatchedCustomerId() == null ? null
                : customerMapper.selectActiveById(detail.getMatchedCustomerId());
        if (customer == null) {
            customer = customerMapper.selectOne(new QueryWrapper<MarketingCustomerInfo>()
                    .eq("unified_credit_code", detail.getUnifiedCreditCode())
                    .eq("record_status", "ACTIVE").last("LIMIT 1"));
        }
        LocalDateTime now = LocalDateTime.now();
        if (customer == null) {
            customer = new MarketingCustomerInfo();
            customer.setUnifiedCreditCode(detail.getUnifiedCreditCode());
            customer.setCustName(detail.getCustName());
            customer.setContactPerson(detail.getContactPerson());
            customer.setContactMobile(detail.getContactMobile());
            customer.setRegisteredAddress(detail.getRegisteredAddress());
            customer.setBusinessAddress(detail.getBusinessAddress());
            customer.setIsAccountOpened(0);
            customer.setOwnershipStatus("UNASSIGNED");
            customer.setOwnershipSource("TAG_IMPORT");
            customer.setOwnershipMaintainMode("AUTO");
            customer.setProfileVersion(1);
            customer.setRecordStatus("ACTIVE");
            customer.setCreatedBy(operatorEmpId);
            customer.setCreatedTime(now);
            customer.setUpdatedBy(operatorEmpId);
            customer.setUpdatedTime(now);
            customer.setLockVersion(0);
            customerMapper.insert(customer);
        } else if ("EXISTING_UPDATE".equals(detail.getCustomerChangeType())) {
            customer.setCustName(detail.getCustName());
            customer.setContactPerson(detail.getContactPerson());
            customer.setContactMobile(detail.getContactMobile());
            customer.setRegisteredAddress(detail.getRegisteredAddress());
            customer.setBusinessAddress(detail.getBusinessAddress());
            customer.setProfileVersion(value(customer.getProfileVersion()) + 1);
            customer.setUpdatedBy(operatorEmpId);
            customer.setUpdatedTime(now);
            customerMapper.updateById(customer);
        }
        return customer;
    }

    private MarketingCustomerTagRel activateRelation(Long customerId, Long tagId,
                                                      MarketingCustomerTagImportBatch batch,
                                                      String operatorEmpId) {
        MarketingCustomerTagRel relation = relationMapper.selectByCustIdAndTagId(customerId, tagId);
        LocalDateTime now = LocalDateTime.now();
        if (relation != null) {
            if (!Integer.valueOf(1).equals(relation.getActive())) relationMapper.reactivate(relation.getId(), operatorEmpId, now);
            return relation;
        }
        relation = new MarketingCustomerTagRel();
        relation.setCustId(customerId);
        relation.setTagId(tagId);
        relation.setActive(1);
        relation.setSourceType("IMPORT");
        relation.setSourceRefId(String.valueOf(batch.getId()));
        relation.setEffectiveTime(now);
        relation.setCreatedBy(operatorEmpId);
        relation.setCreatedTime(now);
        relation.setUpdatedBy(operatorEmpId);
        relation.setUpdatedTime(now);
        relationMapper.insert(relation);
        return relation;
    }

    private void refreshBatch(MarketingCustomerTagImportBatch batch, String operatorEmpId) {
        List<MarketingCustomerTagImportDetail> all = detailMapper.selectByBatchId(batch.getId());
        int pending = (int) all.stream().filter(d -> "PENDING".equals(d.getApprovalStatus())).count();
        int approved = (int) all.stream().filter(d -> "APPROVED".equals(d.getApprovalStatus())).count();
        int rejected = (int) all.stream().filter(d -> "REJECTED".equals(d.getApprovalStatus())).count();
        int loaded = (int) all.stream().filter(d -> Integer.valueOf(1).equals(d.getLoadedFlag())).count();
        String approvalStatus = pending > 0 ? (approved + rejected > 0 ? "PARTIAL_FINISHED" : "IN_APPROVAL")
                : rejected > 0 ? "HAS_REJECTED" : "ALL_APPROVED";
        String status = "IN_APPROVAL";
        String blockReason = null;
        LocalDateTime completed = null;
        if ("REPLACE".equals(batch.getImportMode())) {
            if (rejected > 0 || value(batch.getErrorCount()) > 0) {
                status = "REPLACE_BLOCKED";
                blockReason = rejected > 0 ? "存在被驳回客户，正式关系未变更" : "导入存在校验失败，正式关系未变更";
            } else if (pending == 0) {
                status = "READY_TO_LOAD";
                loaded = applyReplace(batch, all, operatorEmpId);
                status = "COMPLETED";
                completed = LocalDateTime.now();
            }
        } else if (pending == 0) {
            status = "COMPLETED";
            completed = LocalDateTime.now();
        }
        batchMapper.updateSummary(batch.getId(), approvalStatus, status, pending, approved, rejected,
                loaded, completed, blockReason, operatorEmpId);
    }

    /** REPLACE 只在全部有效客户通过后一次性计算并切换关系。 */
    private int applyReplace(MarketingCustomerTagImportBatch batch,
                             List<MarketingCustomerTagImportDetail> details,
                             String operatorEmpId) {
        List<MarketingCustomerTagImportDetail> approved = details.stream()
                .filter(d -> "APPROVED".equals(d.getApprovalStatus())).toList();
        List<Long> customerIds = approved.stream().map(MarketingCustomerTagImportDetail::getMatchedCustomerId)
                .filter(Objects::nonNull).distinct().toList();
        relationMapper.expireNotInCustomerIds(batch.getTagId(), customerIds, operatorEmpId, LocalDateTime.now());
        for (MarketingCustomerTagImportDetail detail : approved) {
            MarketingCustomerTagRel relation = activateRelation(detail.getMatchedCustomerId(), batch.getTagId(), batch, operatorEmpId);
            detail.setLoadedFlag(1);
            detail.setLoadedRelId(relation.getId());
            detail.setLoadedTime(LocalDateTime.now());
            detailMapper.updateById(detail);
        }
        return approved.size();
    }

    private long countPending(Long tagId, String keyword) {
        QueryWrapper<MarketingCustomerTagImportDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("approval_status", "PENDING").eq("validation_status", "VALID")
                .inSql("batch_id", "SELECT id FROM MARKETING_CUSTOMER_TAG_IMPORT_BATCH WHERE tag_id=" + tagId
                        + " AND record_status='ACTIVE'");
        if (StringUtils.hasText(keyword)) wrapper.and(w -> w.like("cust_name", keyword).or().eq("unified_credit_code", keyword));
        return detailMapper.selectCount(wrapper);
    }

    private int value(Integer value) { return value == null ? 0 : value; }
    private BizException error(String code, String message) { return new BizException(code, message); }
    private record Selection(MarketingCustomerTagImportBatch batch,
                             List<MarketingCustomerTagImportDetail> details) { }
}
