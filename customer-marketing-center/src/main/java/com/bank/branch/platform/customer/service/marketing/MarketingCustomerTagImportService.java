package com.bank.branch.platform.customer.service.marketing;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.marketing.tag.TagCreateRequest;
import com.bank.branch.platform.customer.dto.marketing.tag.TagImportCreateRequest;
import com.bank.branch.platform.customer.dto.marketing.tag.TagImportPreviewResponse;
import com.bank.branch.platform.customer.dto.marketing.tag.TagImportRow;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTag;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTagImportBatch;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTagImportDetail;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagImportBatchMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagImportDetailMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 页面五标签客户导入服务。上传阶段只形成批次、明细和待审事项，不提前写正式标签关系。 */
@Service
@RequiredArgsConstructor
public class MarketingCustomerTagImportService {

    private static final Set<String> MODES = Set.of("APPEND", "REPLACE");
    private static final int MAX_ROWS = 5000;

    private final MarketingCustomerTagImportBatchMapper batchMapper;
    private final MarketingCustomerTagImportDetailMapper detailMapper;
    private final MarketingCustomerInfoMapper customerMapper;
    private final MarketingLeadInfoMapper leadMapper;
    private final MarketingCustomerTagService tagService;
    private final FileApi fileApi;

    /** 无副作用预览；最终创建批次时会重新解析和校验。 */
    public TagImportPreviewResponse preview(MultipartFile file, Long tagId, String importMode) {
        String mode = requireMode(importMode);
        validateFile(file);
        MarketingCustomerTag tag = tagService.requireTag(tagId);
        List<TagImportRow> rows = parse(file);
        TagImportPreviewResponse response = new TagImportPreviewResponse();
        response.setTagId(tag.getId());
        response.setTagName(tag.getTagName());
        response.setImportMode(mode);
        response.setSourceFileName(file.getOriginalFilename());
        response.setTotalCount(rows.size());
        int valid = (int) rows.stream().filter(this::validRow).count();
        response.setValidCount(valid);
        response.setErrorCount(rows.size() - valid);
        response.setRows(rows);
        return response;
    }

    /** 创建导入批次；新标签先保存为 PENDING，所有有效客户均进入页面六审批。 */
    @Transactional
    public MarketingCustomerTagImportBatch create(MultipartFile file, TagImportCreateRequest request,
                                                    String operatorEmpId, String operatorOrgId) {
        if (request == null) throw error("CUST-40000", "导入参数不能为空");
        String mode = requireMode(request.getImportMode());
        validateFile(file);
        MarketingCustomerTag tag = resolveTag(request, operatorEmpId, operatorOrgId);
        if ("REPLACE".equals(mode) && batchMapper.countInFlightReplace(tag.getId(), null) > 0) {
            throw error("CUST-40908", "同一标签已有未结束的全量替换批次");
        }
        FileObjectDTO source = fileApi.upload(file, operatorEmpId, "CUSTOMER_TAG_IMPORT");
        if (source == null || !StringUtils.hasText(source.getId())) {
            throw error("CUST-50302", "标签导入文件上传失败");
        }
        LocalDateTime now = LocalDateTime.now();
        MarketingCustomerTagImportBatch batch = new MarketingCustomerTagImportBatch();
        batch.setBatchNo("MTB_" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "_"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT));
        batch.setTagId(tag.getId());
        batch.setTagNameSnapshot(tag.getTagName());
        batch.setImportMode(mode);
        batch.setSourceFileName(file.getOriginalFilename());
        batch.setSourceFileId(source.getId());
        batch.setFileChecksum(source.getMd5Hash());
        batch.setTagApprovalRequired("APPROVED".equals(tag.getApprovalStatus()) ? 0 : 1);
        batch.setCustomerApprovalStatus("IN_APPROVAL");
        batch.setStatus("IMPORTING");
        batch.setImportEmpId(operatorEmpId);
        batch.setImportOrgId(operatorOrgId);
        batch.setImportTime(now);
        batch.setRecordStatus("ACTIVE");
        batch.setCreatedBy(operatorEmpId);
        batch.setCreatedTime(now);
        batch.setUpdatedBy(operatorEmpId);
        batch.setUpdatedTime(now);
        batch.setLockVersion(0);
        batchMapper.insert(batch);

        List<TagImportRow> rows = parse(file);
        if (rows.size() > MAX_ROWS) throw error("CUST-40003", "单次导入不能超过5000行");
        int valid = 0;
        int errors = 0;
        for (TagImportRow row : rows) {
            MarketingCustomerTagImportDetail detail = toDetail(batch.getId(), row);
            validateDetail(detail);
            detailMapper.insert(detail);
            if ("VALID".equals(detail.getValidationStatus())) {
                if (createApprovalLead(detail, batch, operatorEmpId, operatorOrgId)) {
                    valid++;
                } else {
                    errors++;
                }
            } else {
                errors++;
            }
        }
        batch.setTotalCount(rows.size());
        batch.setValidCount(valid);
        batch.setErrorCount(errors);
        batch.setPendingApprovalCount(valid);
        batch.setApprovedCount(0);
        batch.setRejectedCount(0);
        batch.setLoadedCount(0);
        batch.setStatus(valid == 0 ? "REPLACE".equals(mode) ? "REPLACE_BLOCKED" : "COMPLETED" : "IN_APPROVAL");
        if (valid == 0 && "REPLACE".equals(mode)) batch.setReplaceBlockReason("没有可审批的有效客户");
        batchMapper.updateById(batch);
        fileApi.bindFile("CUSTOMER_TAG_IMPORT", String.valueOf(batch.getId()), source.getId(), "SOURCE");
        return batch;
    }

    public PageResult<MarketingCustomerTagImportBatch> list(String keyword, Long tagId, String status,
                                                             String importEmpId, int pageNo, int pageSize) {
        int pn = Math.max(1, pageNo);
        int ps = Math.min(Math.max(1, pageSize), 100);
        int offset = (pn - 1) * ps;
        return PageResult.of(pn, ps, batchMapper.countPage(keyword, tagId, status, importEmpId),
                batchMapper.selectPage(keyword, tagId, status, importEmpId, offset, ps));
    }

    public MarketingCustomerTagImportBatch get(Long batchId, String operatorEmpId, boolean allScope) {
        MarketingCustomerTagImportBatch batch = batchMapper.selectById(batchId);
        if (batch == null || !"ACTIVE".equals(batch.getRecordStatus())
                || (!allScope && !operatorEmpId.equals(batch.getImportEmpId()))) {
            throw error("CUST-40402", "标签导入批次不存在");
        }
        return batch;
    }

    public PageResult<MarketingCustomerTagImportDetail> details(Long batchId, String approvalStatus,
                                                                 String keyword, int pageNo, int pageSize,
                                                                 String operatorEmpId, boolean allScope) {
        get(batchId, operatorEmpId, allScope);
        int pn = Math.max(1, pageNo);
        int ps = Math.min(Math.max(1, pageSize), 100);
        return PageResult.of(pn, ps, detailMapper.countByBatchId(batchId, approvalStatus, keyword),
                detailMapper.selectPageByBatchId(batchId, approvalStatus, keyword, (pn - 1) * ps, ps));
    }

    @Transactional
    public void cancel(Long batchId, String operatorEmpId, boolean allScope) {
        MarketingCustomerTagImportBatch batch = get(batchId, operatorEmpId, allScope);
        if (Set.of("COMPLETED", "CANCELLED", "REPLACE_BLOCKED").contains(batch.getStatus())) {
            throw error("CUST-40907", "当前批次状态不允许取消");
        }
        batch.setStatus("CANCELLED");
        batch.setUpdatedBy(operatorEmpId);
        batch.setUpdatedTime(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    public byte[] sourceFile(Long batchId, String operatorEmpId, boolean allScope) {
        return fileApi.getFileContent(get(batchId, operatorEmpId, allScope).getSourceFileId());
    }

    private MarketingCustomerTag resolveTag(TagImportCreateRequest request, String empId, String orgId) {
        if (request.getTagId() != null) {
            MarketingCustomerTag existing = tagService.requireTag(request.getTagId());
            if ("REJECTED".equals(existing.getApprovalStatus())) {
                throw error("CUST-40902", "已驳回标签不能导入客户");
            }
            return existing;
        }
        if (!StringUtils.hasText(request.getTagName())) throw error("CUST-40000", "标签名称不能为空");
        TagCreateRequest create = new TagCreateRequest();
        create.setTagName(request.getTagName());
        create.setTagCategory(request.getTagCategory());
        create.setTagType(request.getTagType());
        create.setTagPriority(request.getTagPriority());
        create.setDescription(request.getTagDescription());
        return tagService.create(create, empId, orgId);
    }

    private boolean createApprovalLead(MarketingCustomerTagImportDetail detail,
                                       MarketingCustomerTagImportBatch batch,
                                       String empId, String orgId) {
        MarketingLeadInfo conflict = leadMapper.selectActiveByCreditCode(detail.getUnifiedCreditCode());
        if (conflict != null) {
            detail.setValidationStatus("REJECTED");
            detail.setApprovalStatus("NOT_REQUIRED");
            detail.setErrorCode("PENDING_LEAD_DUPLICATE");
            detail.setErrorMessage("该企业已有未结束线索，录入人：" + conflict.getEntryEmpId());
            detailMapper.updateById(detail);
            return false;
        }
        MarketingCustomerInfo customer = findCustomer(detail.getUnifiedCreditCode());
        LocalDateTime now = LocalDateTime.now();
        MarketingLeadInfo lead = new MarketingLeadInfo();
        lead.setLeadNo("TAG_" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "_" + detail.getId());
        lead.setCustId(customer == null ? null : customer.getId());
        lead.setLeadType(customer == null ? "NEW_ACCOUNT" : "EXISTING_MARKETING");
        lead.setCustomerMatchStatus(customer == null ? "NEW_CUSTOMER"
                : customer.getIsAccountOpened() != null && customer.getIsAccountOpened() == 1
                ? "MATCHED_EXISTING_OPENED" : "MATCHED_EXISTING_UNOPENED");
        lead.setBaseCustomerProfileVersion(customer == null ? null : customer.getProfileVersion());
        lead.setCustNoSnapshot(customer == null ? null : customer.getCustNo());
        lead.setCustName(detail.getCustName());
        lead.setUnifiedCreditCode(detail.getUnifiedCreditCode());
        lead.setContactPerson(detail.getContactPerson());
        lead.setContactMobile(detail.getContactMobile());
        lead.setRegisteredAddress(detail.getRegisteredAddress());
        lead.setBusinessAddress(detail.getBusinessAddress());
        lead.setLeadSource("TAG_IMPORT");
        lead.setDistributionMode(customer != null && StringUtils.hasText(customer.getMainManagerId()) ? "OWNER" : "PUBLIC");
        lead.setPoolStatus("NOT_READY");
        lead.setMainManagerIdSnapshot(customer == null ? null : customer.getMainManagerId());
        lead.setMainOrgIdSnapshot(customer == null ? null : customer.getMainOrgId());
        lead.setEntryEmpId(empId);
        lead.setEntryOrgId(orgId);
        lead.setEntryTime(now);
        lead.setLeadStatus("IN_APPROVAL");
        lead.setActiveDedupKey(detail.getUnifiedCreditCode());
        lead.setSubmittedBy(empId);
        lead.setSubmittedTime(now);
        lead.setBusinessKey("TAG_IMPORT:" + detail.getId());
        lead.setImportBatchId(batch.getId());
        lead.setBatchRowNo(detail.getRowNo());
        lead.setTagImportDetailId(detail.getId());
        lead.setRecordStatus("ACTIVE");
        lead.setCreatedBy(empId);
        lead.setCreatedTime(now);
        lead.setUpdatedBy(empId);
        lead.setUpdatedTime(now);
        lead.setLockVersion(0);
        leadMapper.insert(lead);
        detail.setGeneratedLeadId(lead.getId());
        detailMapper.updateById(detail);
        return true;
    }

    private MarketingCustomerTagImportDetail toDetail(Long batchId, TagImportRow row) {
        MarketingCustomerTagImportDetail detail = new MarketingCustomerTagImportDetail();
        detail.setBatchId(batchId);
        detail.setRowNo(row.getRowNo());
        detail.setCustName(trim(row.getCustName()));
        detail.setUnifiedCreditCode(MarketingLeadEntryService.normalizeCreditCode(row.getUnifiedCreditCode()));
        detail.setContactPerson(trim(row.getContactPerson()));
        detail.setContactMobile(trim(row.getContactMobile()));
        detail.setRegisteredAddress(trim(row.getRegisteredAddress()));
        detail.setBusinessAddress(trim(row.getBusinessAddress()));
        detail.setRawRowJson(row.getRawRowJson());
        detail.setLoadedFlag(0);
        detail.setCreatedTime(LocalDateTime.now());
        return detail;
    }

    private void validateDetail(MarketingCustomerTagImportDetail detail) {
        if (!StringUtils.hasText(detail.getCustName()) || !StringUtils.hasText(detail.getUnifiedCreditCode())
                || detail.getUnifiedCreditCode().length() != 18) {
            detail.setValidationStatus("ERROR");
            detail.setApprovalStatus("NOT_REQUIRED");
            detail.setCustomerChangeType("NEW_CUSTOMER");
            detail.setErrorCode("CUSTOMER_IDENTITY_INVALID");
            detail.setErrorMessage("企业名称和18位统一社会信用代码不能为空");
            return;
        }
        MarketingCustomerInfo customer = findCustomer(detail.getUnifiedCreditCode());
        detail.setMatchedCustomerId(customer == null ? null : customer.getId());
        detail.setCustomerChangeType(customer == null ? "NEW_CUSTOMER"
                : sameProfile(customer, detail) ? "EXISTING_NO_CHANGE" : "EXISTING_UPDATE");
        detail.setValidationStatus("VALID");
        detail.setApprovalStatus("PENDING");
    }

    private MarketingCustomerInfo findCustomer(String code) {
        return customerMapper.selectOne(new QueryWrapper<MarketingCustomerInfo>()
                .eq("unified_credit_code", code).eq("record_status", "ACTIVE").last("LIMIT 1"));
    }

    private boolean sameProfile(MarketingCustomerInfo customer, MarketingCustomerTagImportDetail detail) {
        return java.util.Objects.equals(customer.getCustName(), detail.getCustName())
                && java.util.Objects.equals(customer.getContactPerson(), detail.getContactPerson())
                && java.util.Objects.equals(customer.getContactMobile(), detail.getContactMobile())
                && java.util.Objects.equals(customer.getBusinessAddress(), detail.getBusinessAddress());
    }

    private List<TagImportRow> parse(MultipartFile file) {
        try {
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
            return name.endsWith(".csv") ? parseCsv(file) : parseWorkbook(file);
        } catch (IOException ex) {
            throw error("CUST-40003", "导入文件解析失败");
        }
    }

    private List<TagImportRow> parseCsv(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) return List.of();
            String[] headers = header.split(",", -1);
            List<TagImportRow> rows = new ArrayList<>();
            String line;
            int rowNo = 2;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1);
                Map<String, String> map = new HashMap<>();
                for (int i = 0; i < headers.length; i++) map.put(headers[i].trim(), i < values.length ? values[i].trim() : "");
                rows.add(toRow(map, rowNo++));
            }
            return rows;
        }
    }

    private List<TagImportRow> parseWorkbook(MultipartFile file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < header.getLastCellNum(); i++) headers.add(formatter.formatCellValue(header.getCell(i)).trim());
            List<TagImportRow> rows = new ArrayList<>();
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> map = new HashMap<>();
                for (int c = 0; c < headers.size(); c++) map.put(headers.get(c), formatter.formatCellValue(row.getCell(c)).trim());
                rows.add(toRow(map, i + 1));
            }
            return rows;
        } catch (RuntimeException ex) {
            throw new IOException(ex);
        }
    }

    private TagImportRow toRow(Map<String, String> map, int rowNo) {
        TagImportRow row = new TagImportRow();
        row.setRowNo(rowNo);
        row.setCustName(first(map, "企业名称", "客户名称", "custName"));
        row.setUnifiedCreditCode(first(map, "统一社会信用代码", "unifiedCreditCode"));
        row.setContactPerson(first(map, "联系人", "contactPerson"));
        row.setContactMobile(first(map, "联系电话", "联系方式", "contactMobile"));
        row.setRegisteredAddress(first(map, "注册地址", "registeredAddress"));
        row.setBusinessAddress(first(map, "经营地址", "businessAddress"));
        row.setRawRowJson(map.toString());
        return row;
    }

    private String first(Map<String, String> map, String... keys) {
        for (String key : keys) if (StringUtils.hasText(map.get(key))) return map.get(key);
        return null;
    }

    private boolean validRow(TagImportRow row) {
        String code = row == null ? null : MarketingLeadEntryService.normalizeCreditCode(row.getUnifiedCreditCode());
        return row != null && StringUtils.hasText(row.getCustName()) && code != null && code.length() == 18;
    }

    private String requireMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(normalized)) throw error("CUST-40000", "导入模式只能为 APPEND 或 REPLACE");
        return normalized;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw error("CUST-40000", "导入文件不能为空");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".csv") && !name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw error("CUST-40003", "仅支持 CSV、XLSX 或 XLS 文件");
        }
    }

    private String trim(String value) { return value == null ? null : value.trim(); }
    private BizException error(String code, String message) { return new BizException(code, message); }
}
