package com.bank.branch.platform.customer.service.marketing;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadImportDetailResponse;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadImportPreviewResponse;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadImportQuery;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadImportBatch;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadImportDetail;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadImportBatchMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadImportDetailMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 页面三线索批量导入服务。
 *
 * <p>批次处理和线索审批是两条独立状态机：本服务只负责 OBS 文件、逐行校验、
 * 待确认和生成线索，不会把导入批次误当成一个整批审批单。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingLeadImportService {

    static final String WAITING_CONFIRM = "WAITING_CONFIRM";
    static final String COMPLETED = "COMPLETED";
    static final String ALL_FAILED = "ALL_FAILED";
    static final String ABANDONED = "ABANDONED";
    static final String PROCESS_VALID = "PROCESS_VALID";
    static final String ABANDON_REIMPORT = "ABANDON_REIMPORT";

    private static final int MAX_ROWS = 5000;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> FAILURE_STATUSES = Set.of("REJECTED", "ERROR", "WARNING");

    private final MarketingLeadImportBatchMapper batchMapper;
    private final MarketingLeadImportDetailMapper detailMapper;
    private final MarketingLeadInfoMapper leadMapper;
    private final MarketingCustomerInfoMapper customerMapper;
    private final FileApi fileApi;

    /** 查询导入批次；客户经理固定只能查看本人批次，管理员可查看全量。 */
    public PageResult<MarketingLeadImportBatch> list(LeadImportQuery query,
                                                     String operatorEmpId,
                                                     boolean allScope) {
        LeadImportQuery safe = query == null ? new LeadImportQuery() : query;
        int pageNo = safePageNo(safe.getPageNo());
        int pageSize = safePageSize(safe.getPageSize());
        int offset = (pageNo - 1) * pageSize;
        List<MarketingLeadImportBatch> records = batchMapper.selectPage(
                safe.getKeyword(), safe.getStatus(), operatorEmpId, allScope, offset, pageSize);
        long total = batchMapper.countPage(safe.getKeyword(), safe.getStatus(), operatorEmpId, allScope);
        return PageResult.of(pageNo, pageSize, total, records == null ? List.of() : records);
    }

    /** 获取批次汇总并校验数据范围。 */
    public MarketingLeadImportBatch getBatch(Long batchId, String operatorEmpId, boolean allScope) {
        MarketingLeadImportBatch batch = batchMapper.selectActiveById(batchId);
        requireBatchVisible(batch, operatorEmpId, allScope);
        return batch;
    }

    /** 分页获取批次明细；排序由 Mapper 固定为失败、异常、警告、有效。 */
    public PageResult<MarketingLeadImportDetail> listDetails(Long batchId, int pageNo, int pageSize) {
        int safePageNo = safePageNo(pageNo);
        int safePageSize = safePageSize(pageSize);
        int offset = (safePageNo - 1) * safePageSize;
        List<MarketingLeadImportDetail> records = detailMapper.selectByBatchIdOrderByFailure(
                batchId, offset, safePageSize);
        long total = detailMapper.countByBatchId(batchId);
        return PageResult.of(safePageNo, safePageSize, total, records == null ? List.of() : records);
    }

    /** 带数据范围检查的明细查询，供 Controller 使用。 */
    public PageResult<MarketingLeadImportDetail> listDetails(Long batchId, int pageNo, int pageSize,
                                                              String operatorEmpId, boolean allScope) {
        getBatch(batchId, operatorEmpId, allScope);
        return listDetails(batchId, pageNo, pageSize);
    }

    /**
     * 预览文件并持久化批次及逐行明细。此动作不生成线索，返回 WAITING_CONFIRM 时尤其如此。
     */
    @Transactional
    public LeadImportPreviewResponse preview(MultipartFile file, String operatorEmpId, String operatorOrgId) {
        return prepareBatch(file, operatorEmpId, operatorOrgId);
    }

    /** 创建并处理批次：无 WARNING 的有效行立即生成线索；有 WARNING 则只停在待确认。 */
    @Transactional
    public LeadImportPreviewResponse create(MultipartFile file, String operatorEmpId, String operatorOrgId) {
        LeadImportPreviewResponse response = prepareBatch(file, operatorEmpId, operatorOrgId);
        MarketingLeadImportBatch batch = response.getBatch();
        if (!WAITING_CONFIRM.equals(batch.getImportStatus())) {
            processValidRows(batch, operatorEmpId);
            batch.setImportStatus(batch.getGeneratedLeadCount() != null && batch.getGeneratedLeadCount() > 0
                    ? COMPLETED : ALL_FAILED);
            batchMapper.updateById(batch);
        }
        return response;
    }

    /**
     * 处理待确认批次。PROCESS_VALID 仅二次校验并处理 VALID 行，其余行标记 SKIPPED；
     * ABANDON_REIMPORT 只结束当前批次，不删除原始文件和明细证据。
     */
    @Transactional
    public MarketingLeadImportBatch confirm(Long batchId, String action,
                                            String operatorEmpId, String remark) {
        return confirm(batchId, action, operatorEmpId, remark, false);
    }

    /** 带管理员数据范围标志的待确认操作；非管理员不能确认他人的批次。 */
    @Transactional
    public MarketingLeadImportBatch confirm(Long batchId, String action,
                                            String operatorEmpId, String remark, boolean allScope) {
        MarketingLeadImportBatch batch = batchMapper.selectForUpdate(batchId);
        requireBatchVisible(batch, operatorEmpId, allScope);
        if (!WAITING_CONFIRM.equals(batch.getImportStatus())) {
            throw error("MARKETING_LEAD_IMPORT_STATE_INVALID", "当前批次不处于待确认状态");
        }
        String normalizedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if (!PROCESS_VALID.equals(normalizedAction) && !ABANDON_REIMPORT.equals(normalizedAction)) {
            throw error("MARKETING_LEAD_IMPORT_ACTION_INVALID", "不支持的导入确认动作");
        }
        if (PROCESS_VALID.equals(normalizedAction)) {
            processValidRows(batch, operatorEmpId);
            skipNonValidRows(batch.getId());
            batch.setImportStatus(batch.getGeneratedLeadCount() != null && batch.getGeneratedLeadCount() > 0
                    ? COMPLETED : ALL_FAILED);
        } else {
            skipAllPendingRows(batch.getId());
            batch.setImportStatus(ABANDONED);
        }
        batch.setConfirmAction(normalizedAction);
        batch.setConfirmRemark(remark);
        batch.setConfirmedBy(operatorEmpId);
        batch.setConfirmedTime(LocalDateTime.now());
        batch.setUpdatedBy(operatorEmpId);
        batch.setUpdatedTime(LocalDateTime.now());
        batchMapper.updateById(batch);
        return batch;
    }

    /** 下载原始导入文件；调用方已在 service 层校验批次权限。 */
    public byte[] sourceFile(Long batchId, String operatorEmpId, boolean allScope) {
        MarketingLeadImportBatch batch = getBatch(batchId, operatorEmpId, allScope);
        if (!StringUtils.hasText(batch.getSourceFileId())) {
            throw error("MARKETING_LEAD_IMPORT_FILE_NOT_FOUND", "原始导入文件不存在");
        }
        return fileApi.getFileContent(batch.getSourceFileId());
    }

    /** 下载失败明细；没有已生成文件时由服务端生成并通过 FileApi 保存到 OBS。 */
    @Transactional
    public byte[] errorFile(Long batchId, String operatorEmpId, boolean allScope) {
        MarketingLeadImportBatch batch = getBatch(batchId, operatorEmpId, allScope);
        if (StringUtils.hasText(batch.getErrorFileId())) {
            return fileApi.getFileContent(batch.getErrorFileId());
        }
        List<MarketingLeadImportDetail> details = detailMapper.selectByBatchIdOrderByFailure(
                batchId, 0, Integer.MAX_VALUE);
        List<MarketingLeadImportDetail> failures = (details == null ? List.<MarketingLeadImportDetail>of() : details)
                .stream().filter(d -> FAILURE_STATUSES.contains(d.getValidationStatus())).toList();
        if (failures.isEmpty()) {
            throw error("MARKETING_LEAD_IMPORT_NO_ERROR_FILE", "当前批次没有失败明细");
        }
        byte[] bytes = toErrorCsv(failures);
        FileObjectDTO uploaded = fileApi.upload(bytes,
                safeFilename(batch.getSourceFileName()) + ".error.csv",
                "text/csv", operatorEmpId, "LEAD_IMPORT_ERROR");
        if (uploaded != null && StringUtils.hasText(uploaded.getId())) {
            batch.setErrorFileId(uploaded.getId());
            batch.setUpdatedBy(operatorEmpId);
            batch.setUpdatedTime(LocalDateTime.now());
            batchMapper.updateById(batch);
        }
        return bytes;
    }

    private LeadImportPreviewResponse prepareBatch(MultipartFile file, String operatorEmpId,
                                                   String operatorOrgId) {
        validateFile(file);
        LocalDateTime now = LocalDateTime.now();
        MarketingLeadImportBatch batch = new MarketingLeadImportBatch();
        batch.setBatchNo("MLB_" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT));
        batch.setSourceFileName(file.getOriginalFilename());
        batch.setImportEmpId(operatorEmpId);
        batch.setImportOrgId(operatorOrgId);
        batch.setImportTime(now);
        batch.setCreatedBy(operatorEmpId);
        batch.setCreatedTime(now);
        batch.setUpdatedBy(operatorEmpId);
        batch.setUpdatedTime(now);
        batch.setRecordStatus("ACTIVE");
        batch.setImportStatus("IMPORTING");
        batch.setApprovalSummaryStatus("NOT_SUBMITTED");
        batch.setLockVersion(0);

        FileObjectDTO source = fileApi.upload(file, operatorEmpId, "LEAD_IMPORT");
        if (source != null) {
            batch.setSourceFileId(source.getId());
            batch.setFileChecksum(source.getMd5Hash());
        }
        batchMapper.insert(batch);

        List<MarketingLeadImportDetail> details;
        try {
            details = parseAndValidate(file, batch.getId());
        } catch (Exception ex) {
            log.warn("线索导入文件解析失败: {}", ex.getMessage());
            details = List.of(errorDetail(batch.getId(), 2, "文件解析失败，请检查模板和文件内容"));
        }
        if (details.size() > MAX_ROWS) {
            throw error("MARKETING_LEAD_IMPORT_ROWS_TOO_MANY", "导入行数不能超过" + MAX_ROWS + "行");
        }
        for (MarketingLeadImportDetail detail : details) {
            detailMapper.insert(detail);
        }
        refreshBatchCounters(batch, details);
        if (batch.getWarningCount() != null && batch.getWarningCount() > 0) {
            batch.setImportStatus(WAITING_CONFIRM);
        } else if (batch.getValidCount() == null || batch.getValidCount() == 0) {
            batch.setImportStatus(ALL_FAILED);
        }
        batchMapper.updateById(batch);

        LeadImportPreviewResponse response = new LeadImportPreviewResponse();
        response.setBatch(batch);
        response.setDetails(details);
        return response;
    }

    private List<MarketingLeadImportDetail> parseAndValidate(MultipartFile file, Long batchId)
            throws IOException {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".csv")) {
            return parseCsv(file.getInputStream(), batchId);
        }
        if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            return parseWorkbook(file.getInputStream(), batchId);
        }
        throw error("MARKETING_LEAD_IMPORT_FORMAT_INVALID", "仅支持CSV、XLSX或XLS文件");
    }

    private List<MarketingLeadImportDetail> parseCsv(InputStream input, Long batchId) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return List.of(errorDetail(batchId, 2, "文件缺少表头"));
            List<String> headers = splitCsv(headerLine);
            List<MarketingLeadImportDetail> details = new ArrayList<>();
            String line;
            int rowNo = 2;
            while ((line = reader.readLine()) != null) {
                List<String> values = splitCsv(line);
                if (values.stream().allMatch(v -> v == null || v.isBlank())) continue;
                details.add(validateRow(toMap(headers, values), batchId, rowNo++));
            }
            return details;
        }
    }

    private List<MarketingLeadImportDetail> parseWorkbook(InputStream input, Long batchId) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) return List.of(errorDetail(batchId, 2, "文件缺少工作表"));
            DataFormatter formatter = new DataFormatter();
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) return List.of(errorDetail(batchId, 2, "文件缺少表头"));
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < header.getLastCellNum(); i++) headers.add(value(header.getCell(i), formatter));
            List<MarketingLeadImportDetail> details = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                List<String> values = new ArrayList<>();
                boolean blank = true;
                for (int i = 0; i < headers.size(); i++) {
                    String cell = value(row.getCell(i), formatter);
                    values.add(cell);
                    if (!cell.isBlank()) blank = false;
                }
                if (!blank) details.add(validateRow(toMap(headers, values), batchId, rowIndex + 1));
            }
            return details;
        } catch (Exception ex) {
            if (ex instanceof IOException io) throw io;
            throw new IOException("Workbook parse failed", ex);
        }
    }

    private MarketingLeadImportDetail validateRow(Map<String, String> row, Long batchId, int rowNo) {
        MarketingLeadImportDetail detail = new MarketingLeadImportDetail();
        detail.setBatchId(batchId);
        detail.setRowNo(rowNo);
        detail.setCustNo(first(row, "custNo", "客户号", "客户编号"));
        detail.setCustName(first(row, "custName", "企业名称", "客户名称"));
        String code = MarketingLeadEntryService.normalizeCreditCode(
                first(row, "unifiedCreditCode", "统一社会信用代码", "统一信用代码"));
        detail.setUnifiedCreditCode(code);
        detail.setContactPerson(first(row, "contactPerson", "联系人"));
        detail.setContactMobile(first(row, "contactMobile", "联系电话", "联系人电话"));
        detail.setRegisteredAddress(first(row, "registeredAddress", "注册地址"));
        detail.setBusinessAddress(first(row, "businessAddress", "经营地址"));
        detail.setIndustry(first(row, "industry", "行业"));
        detail.setCustomerType(first(row, "customerType", "客户类型"));
        detail.setCreditAmount(decimal(first(row, "creditAmount", "授信金额")));
        detail.setCreditExposureAmount(decimal(first(row, "creditExposureAmount", "授信敞口金额", "敞口金额")));
        detail.setRequestedManagerId(first(row, "requestedManagerId", "主办客户经理", "客户经理工号", "主办人工号"));
        detail.setRawRowJson(toJson(row));
        detail.setHandlingStatus("PENDING");

        if (!StringUtils.hasText(detail.getCustName())) {
            return markError(detail, "REQUIRED_CUST_NAME", "企业名称不能为空");
        }
        if (!StringUtils.hasText(code) || !code.matches("[0-9A-Z]{18}")) {
            return markError(detail, "INVALID_CREDIT_CODE", "统一社会信用代码必须为18位大写字母或数字");
        }
        MarketingCustomerInfo customer = findCustomer(code);
        if (customer != null) {
            detail.setMatchedCustomerId(customer.getId());
            detail.setCustomerMatchStatus(customer.getIsAccountOpened() != null && customer.getIsAccountOpened() == 1
                    ? "MATCHED_EXISTING_OPENED" : "MATCHED_EXISTING_UNOPENED");
            if (StringUtils.hasText(detail.getRequestedManagerId())
                    && !Objects.equals(detail.getRequestedManagerId(), customer.getMainManagerId())) {
                detail.setValidationStatus("WARNING");
                detail.setWarningCode("OWNER_CONFLICT");
                detail.setWarningMessage("文件主办人与客户主档当前主办人不一致，需人工确认");
                detail.setRequestedManagerOrgId(customer.getMainOrgId());
                return detail;
            }
        } else {
            detail.setCustomerMatchStatus("NEW_CUSTOMER");
        }
        MarketingLeadInfo pending = leadMapper.selectActiveByCreditCode(code);
        if (pending != null) {
            detail.setValidationStatus("REJECTED");
            detail.setErrorCode("PENDING_LEAD_DUPLICATE");
            detail.setErrorMessage("已有在途线索 " + pending.getLeadNo() + "，录入人="
                    + pending.getEntryEmpId() + "，时间=" + pending.getEntryTime());
            detail.setMatchedLeadId(pending.getId());
            detail.setMatchedEntryEmpId(pending.getEntryEmpId());
            detail.setMatchedEntryOrgId(pending.getEntryOrgId());
            detail.setMatchedEntryTime(pending.getEntryTime());
            return detail;
        }
        detail.setValidationStatus("VALID");
        return detail;
    }

    private void processValidRows(MarketingLeadImportBatch batch, String operatorEmpId) {
        List<MarketingLeadImportDetail> validRows = detailMapper.selectByBatchAndValidationStatus(
                batch.getId(), "VALID");
        int generated = 0;
        if (validRows != null) {
            for (MarketingLeadImportDetail detail : validRows) {
                if (!"PENDING".equals(detail.getHandlingStatus())) continue;
                String code = MarketingLeadEntryService.normalizeCreditCode(detail.getUnifiedCreditCode());
                MarketingLeadInfo pending = leadMapper.selectActiveByCreditCode(code);
                if (pending != null) {
                    detail.setValidationStatus("REJECTED");
                    detail.setErrorCode("PENDING_LEAD_DUPLICATE");
                    detail.setErrorMessage("二次校验发现在途线索 " + pending.getLeadNo());
                    detail.setHandlingStatus("SKIPPED");
                    detailMapper.updateById(detail);
                    continue;
                }
                MarketingLeadInfo lead = toLead(detail, batch, operatorEmpId);
                try {
                    leadMapper.insert(lead);
                    detailMapper.updateHandlingIf(detail.getId(), "PENDING", "GENERATED", lead.getId());
                    generated++;
                } catch (DuplicateKeyException ex) {
                    detail.setValidationStatus("REJECTED");
                    detail.setErrorCode("PENDING_LEAD_DUPLICATE");
                    detail.setErrorMessage("生成线索时检测到统一社会信用代码已有在途线索");
                    detail.setHandlingStatus("SKIPPED");
                    detailMapper.updateById(detail);
                }
            }
        }
        batch.setGeneratedLeadCount((batch.getGeneratedLeadCount() == null ? 0 : batch.getGeneratedLeadCount()) + generated);
        batch.setUpdatedBy(operatorEmpId);
        batch.setUpdatedTime(LocalDateTime.now());
    }

    private MarketingLeadInfo toLead(MarketingLeadImportDetail detail, MarketingLeadImportBatch batch,
                                     String operatorEmpId) {
        MarketingLeadInfo lead = new MarketingLeadInfo();
        lead.setLeadNo("MLEAD_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT));
        lead.setCustId(detail.getMatchedCustomerId());
        lead.setLeadType("NEW_CUSTOMER".equals(detail.getCustomerMatchStatus())
                ? "NEW_ACCOUNT" : "EXISTING_MARKETING");
        lead.setCustomerMatchStatus(detail.getCustomerMatchStatus());
        lead.setCustNoSnapshot(detail.getCustNo());
        lead.setCustName(detail.getCustName());
        lead.setUnifiedCreditCode(detail.getUnifiedCreditCode());
        lead.setContactPerson(detail.getContactPerson());
        lead.setContactMobile(detail.getContactMobile());
        lead.setRegisteredAddress(detail.getRegisteredAddress());
        lead.setBusinessAddress(detail.getBusinessAddress());
        lead.setIndustry(detail.getIndustry());
        lead.setCustomerType(detail.getCustomerType());
        lead.setCreditAmount(detail.getCreditAmount());
        lead.setCreditExposureAmount(detail.getCreditExposureAmount());
        lead.setMainManagerIdSnapshot(detail.getRequestedManagerId());
        lead.setMainOrgIdSnapshot(detail.getRequestedManagerOrgId());
        lead.setLeadSource("LEAD_IMPORT");
        lead.setDistributionMode(StringUtils.hasText(detail.getRequestedManagerId()) ? "OWNER" : "PUBLIC");
        lead.setPoolStatus("NOT_READY");
        lead.setActiveDedupKey(detail.getUnifiedCreditCode());
        lead.setLeadStatus("DRAFT");
        lead.setEntryEmpId(batch.getImportEmpId());
        lead.setEntryOrgId(batch.getImportOrgId());
        lead.setEntryTime(batch.getImportTime());
        lead.setImportBatchId(batch.getId());
        lead.setBatchRowNo(detail.getRowNo());
        lead.setRecordStatus("ACTIVE");
        lead.setCreatedBy(operatorEmpId);
        lead.setCreatedTime(LocalDateTime.now());
        lead.setUpdatedBy(operatorEmpId);
        lead.setUpdatedTime(LocalDateTime.now());
        lead.setLockVersion(0);
        return lead;
    }

    private void skipNonValidRows(Long batchId) {
        List<MarketingLeadImportDetail> all = detailMapper.selectByBatchIdOrderByFailure(
                batchId, 0, Integer.MAX_VALUE);
        if (all == null) return;
        for (MarketingLeadImportDetail detail : all) {
            if (!"VALID".equals(detail.getValidationStatus()) && "PENDING".equals(detail.getHandlingStatus())) {
                detailMapper.updateHandlingIf(detail.getId(), "PENDING", "SKIPPED", null);
            }
        }
    }

    private void skipAllPendingRows(Long batchId) {
        List<MarketingLeadImportDetail> all = detailMapper.selectByBatchIdOrderByFailure(
                batchId, 0, Integer.MAX_VALUE);
        if (all == null) return;
        for (MarketingLeadImportDetail detail : all) {
            if ("PENDING".equals(detail.getHandlingStatus())) {
                detailMapper.updateHandlingIf(detail.getId(), "PENDING", "SKIPPED", null);
            }
        }
    }

    private void refreshBatchCounters(MarketingLeadImportBatch batch, List<MarketingLeadImportDetail> details) {
        int valid = 0, warning = 0, rejected = 0, error = 0;
        for (MarketingLeadImportDetail detail : details) {
            switch (detail.getValidationStatus()) {
                case "VALID" -> valid++;
                case "WARNING" -> warning++;
                case "REJECTED" -> rejected++;
                default -> error++;
            }
        }
        batch.setTotalCount(details.size());
        batch.setValidCount(valid);
        batch.setWarningCount(warning);
        batch.setRejectedCount(rejected);
        batch.setErrorCount(error);
        batch.setGeneratedLeadCount(0);
    }

    private MarketingCustomerInfo findCustomer(String code) {
        return customerMapper.selectOne(new QueryWrapper<MarketingCustomerInfo>()
                .eq("unified_credit_code", code)
                .eq("record_status", "ACTIVE")
                .last("LIMIT 1"));
    }

    private void requireBatchVisible(MarketingLeadImportBatch batch, String operatorEmpId, boolean allScope) {
        if (batch == null || !"ACTIVE".equals(batch.getRecordStatus())
                || (!allScope && !Objects.equals(batch.getImportEmpId(), operatorEmpId))) {
            throw error("MARKETING_LEAD_IMPORT_NOT_FOUND", "导入批次不存在");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw error("MARKETING_LEAD_IMPORT_FILE_EMPTY", "导入文件不能为空");
        String name = file.getOriginalFilename();
        if (name == null || !(name.toLowerCase(Locale.ROOT).endsWith(".csv")
                || name.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || name.toLowerCase(Locale.ROOT).endsWith(".xls"))) {
            throw error("MARKETING_LEAD_IMPORT_FORMAT_INVALID", "仅支持CSV、XLSX或XLS文件");
        }
    }

    private MarketingLeadImportDetail markError(MarketingLeadImportDetail detail, String code, String message) {
        detail.setValidationStatus("ERROR");
        detail.setErrorCode(code);
        detail.setErrorMessage(message);
        return detail;
    }

    private MarketingLeadImportDetail errorDetail(Long batchId, int rowNo, String message) {
        MarketingLeadImportDetail detail = new MarketingLeadImportDetail();
        detail.setBatchId(batchId);
        detail.setRowNo(rowNo);
        detail.setValidationStatus("ERROR");
        detail.setErrorCode("FILE_PARSE_ERROR");
        detail.setErrorMessage(message);
        detail.setHandlingStatus("PENDING");
        return detail;
    }

    private String first(Map<String, String> row, String... names) {
        for (String name : names) {
            String value = row.get(normalizeHeader(name));
            if (StringUtils.hasText(value)) return value.trim();
        }
        return null;
    }

    private Map<String, String> toMap(List<String> headers, List<String> values) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            result.put(normalizeHeader(headers.get(i)), i < values.size() ? values.get(i) : null);
        }
        return result;
    }

    private String normalizeHeader(String header) {
        return header == null ? "" : header.replace("\uFEFF", "").trim()
                .replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private List<String> splitCsv(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else quote = !quote;
            } else if (ch == ',' && !quote) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else current.append(ch);
        }
        result.add(current.toString().trim());
        return result;
    }

    private String value(Cell cell, DataFormatter formatter) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String toJson(Map<String, String> row) {
        return row.toString();
    }

    private byte[] toErrorCsv(List<MarketingLeadImportDetail> failures) {
        StringBuilder csv = new StringBuilder("行号,企业名称,统一社会信用代码,校验状态,错误编码,失败原因\n");
        for (MarketingLeadImportDetail detail : failures) {
            csv.append(detail.getRowNo()).append(',')
                    .append(csvValue(detail.getCustName())).append(',')
                    .append(csvValue(detail.getUnifiedCreditCode())).append(',')
                    .append(csvValue(detail.getValidationStatus())).append(',')
                    .append(csvValue(detail.getErrorCode() == null ? detail.getWarningCode() : detail.getErrorCode())).append(',')
                    .append(csvValue(detail.getErrorMessage() == null ? detail.getWarningMessage() : detail.getErrorMessage()))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvValue(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String safeFilename(String filename) {
        if (!StringUtils.hasText(filename)) return "lead-import";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private BizException error(String code, String message) {
        return new BizException(code, message);
    }

    private int safePageNo(Integer pageNo) { return pageNo == null || pageNo < 1 ? 1 : pageNo; }

    private int safePageNo(int pageNo) { return Math.max(1, pageNo); }

    private int safePageSize(Integer pageSize) {
        return pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }

    private int safePageSize(int pageSize) { return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE); }
}
