package com.bank.branch.platform.customer.service.marketing;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadCreateRequest;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 页面三线索批量导入服务。
 *
 * <p>批次处理和线索审批是两条独立状态机：本服务只负责本地暂存文件、逐行校验、
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
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> FAILURE_STATUSES = Set.of("REJECTED", "ERROR", "WARNING");
    private static final List<RequiredHeader> REQUIRED_HEADERS = List.of(
            new RequiredHeader("客户名称", Set.of("custname", "客户名称", "企业名称")),
            new RequiredHeader("统一社会信用代码", Set.of("unifiedcreditcode", "统一社会信用代码", "统一信用代码")),
            new RequiredHeader("是否触达限制", Set.of("touchrestricted", "是否触达限制"))
    );
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final BigDecimal WAN_TO_YUAN = BigDecimal.TEN.pow(4);

    private final MarketingLeadImportBatchMapper batchMapper;
    private final MarketingLeadImportDetailMapper detailMapper;
    private final MarketingLeadInfoMapper leadMapper;
    private final MarketingCustomerInfoMapper customerMapper;
    private final MarketingLeadEntryService leadEntryService;
    private final PlatformTransactionManager transactionManager;
    private final MarketingLeadImportLocalStorage localStorage;
    /** 仅用于兼容读取切换前已经落库的 OBS 文件 ID，不再用于新导入文件上传。 */
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
        return readStoredFile(batch.getSourceFileId());
    }

    private byte[] readStoredFile(String storageId) {
        if (localStorage.supports(storageId)) {
            return localStorage.read(storageId);
        }
        return fileApi.getFileContent(storageId);
    }

    /** 下载失败明细；没有已生成文件时由服务端生成并保存到本地目录。 */
    @Transactional
    public byte[] errorFile(Long batchId, String operatorEmpId, boolean allScope) {
        MarketingLeadImportBatch batch = getBatch(batchId, operatorEmpId, allScope);
        if (StringUtils.hasText(batch.getErrorFileId())) {
            return readStoredFile(batch.getErrorFileId());
        }
        List<MarketingLeadImportDetail> details = detailMapper.selectByBatchIdOrderByFailure(
                batchId, 0, Integer.MAX_VALUE);
        List<MarketingLeadImportDetail> failures = (details == null ? List.<MarketingLeadImportDetail>of() : details)
                .stream().filter(d -> FAILURE_STATUSES.contains(d.getValidationStatus())).toList();
        if (failures.isEmpty()) {
            throw error("MARKETING_LEAD_IMPORT_NO_ERROR_FILE", "当前批次没有失败明细");
        }
        byte[] bytes = toErrorCsv(failures);
        batch.setErrorFileId(localStorage.saveErrorCsv(bytes));
        batch.setUpdatedBy(operatorEmpId);
        batch.setUpdatedTime(LocalDateTime.now());
        batchMapper.updateById(batch);
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

        MarketingLeadImportLocalStorage.StoredFile source = localStorage.saveSource(file);
        batch.setSourceFileId(source.key());
        batch.setFileChecksum(source.md5Hash());
        batchMapper.insert(batch);

        List<MarketingLeadImportDetail> details;
        try {
            details = parseAndValidate(file, batch.getId());
        } catch (Exception ex) {
            String message = parseFailureMessage(file);
            log.warn("线索导入文件解析失败: {}", message, ex);
            details = List.of(errorDetail(batch.getId(), 1, parseFailureCode(file), message));
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
            if (headerLine == null || headerLine.isBlank()) {
                return List.of(errorDetail(batchId, 1, "FILE_HEADER_MISSING",
                        "文件缺少表头，请下载最新线索导入模板"));
            }
            List<String> headers = splitCsv(headerLine);
            if (headers.stream().noneMatch(StringUtils::hasText)) {
                return List.of(errorDetail(batchId, 1, "FILE_HEADER_MISSING",
                        "文件缺少表头，请下载最新线索导入模板"));
            }
            List<String> missingHeaders = missingRequiredHeaders(headers);
            if (!missingHeaders.isEmpty()) {
                return List.of(errorDetail(batchId, 1, "FILE_REQUIRED_HEADER_MISSING",
                        "文件缺少关键表头：" + String.join("、", missingHeaders)
                                + "，请下载最新线索导入模板"));
            }
            List<MarketingLeadImportDetail> details = new ArrayList<>();
            String line;
            int rowNo = 2;
            while ((line = reader.readLine()) != null) {
                List<String> values = splitCsv(line);
                int physicalRowNo = rowNo++;
                if (values.stream().allMatch(v -> v == null || v.isBlank())) continue;
                details.add(validateRow(toMap(headers, values), batchId, physicalRowNo));
            }
            return details.isEmpty()
                    ? List.of(errorDetail(batchId, 2, "FILE_NO_DATA",
                    "文件没有可导入的数据行，请至少填写1条线索"))
                    : details;
        }
    }

    private List<MarketingLeadImportDetail> parseWorkbook(InputStream input, Long batchId) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) return List.of(errorDetail(batchId, 2, "文件缺少工作表"));
            DataFormatter formatter = new DataFormatter();
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null || header.getLastCellNum() <= 0) {
                return List.of(errorDetail(batchId, 1, "FILE_HEADER_MISSING",
                        "文件缺少表头，请下载最新线索导入模板"));
            }
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < header.getLastCellNum(); i++) headers.add(value(header.getCell(i), formatter));
            if (headers.stream().noneMatch(StringUtils::hasText)) {
                return List.of(errorDetail(batchId, 1, "FILE_HEADER_MISSING",
                        "文件缺少表头，请下载最新线索导入模板"));
            }
            List<String> missingHeaders = missingRequiredHeaders(headers);
            if (!missingHeaders.isEmpty()) {
                return List.of(errorDetail(batchId, 1, "FILE_REQUIRED_HEADER_MISSING",
                        "文件缺少关键表头：" + String.join("、", missingHeaders)
                                + "，请下载最新线索导入模板"));
            }
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
            return details.isEmpty()
                    ? List.of(errorDetail(batchId, 2, "FILE_NO_DATA",
                    "文件没有可导入的数据行，请至少填写1条线索"))
                    : details;
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
        detail.setRawRowJson(toJson(row));
        detail.setHandlingStatus("PENDING");

        if (!StringUtils.hasText(detail.getCustName())) {
            return markError(detail, "REQUIRED_CUST_NAME", "企业名称不能为空");
        }
        if (!StringUtils.hasText(code) || !code.matches("[0-9A-Z]{18}")) {
            return markError(detail, "INVALID_CREDIT_CODE", "统一社会信用代码必须为18位大写字母或数字");
        }
        LeadCreateRequest request;
        try {
            request = toLeadCreateRequest(detail);
        } catch (ImportValidationException ex) {
            return markError(detail, ex.code, ex.getMessage());
        }
        detail.setCreditAmount(request.getCreditAmount());
        detail.setCreditExposureAmount(request.getCreditExposureAmount());
        detail.setRequestedManagerId(request.getManagerEmpIds().isEmpty()
                ? null : request.getManagerEmpIds().get(0));

        MarketingCustomerInfo customer = findCustomer(code);
        if (customer != null) {
            detail.setMatchedCustomerId(customer.getId());
            detail.setCustomerMatchStatus(customer.getIsAccountOpened() != null && customer.getIsAccountOpened() == 1
                    ? "MATCHED_EXISTING_OPENED" : "MATCHED_EXISTING_UNOPENED");
            if ("OWNER".equals(request.getDistributionMode())
                    && StringUtils.hasText(customer.getMainManagerId())
                    && !request.getManagerEmpIds().isEmpty()
                    && !request.getManagerEmpIds().contains(customer.getMainManagerId())) {
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
                    markProcessingRejected(batch, detail, "二次校验发现在途线索 " + pending.getLeadNo());
                    continue;
                }
                try {
                    MarketingLeadInfo lead = createImportedLead(batch, detail, operatorEmpId);
                    generated++;
                } catch (DuplicateKeyException ex) {
                    markProcessingRejected(batch, detail, "生成线索时检测到统一社会信用代码已有在途线索");
                } catch (ImportValidationException ex) {
                    markProcessingError(batch, detail, ex.code, ex.getMessage());
                } catch (BizException ex) {
                    markProcessingError(batch, detail, ex.getCode(), ex.getMessage());
                }
            }
        }
        batch.setGeneratedLeadCount((batch.getGeneratedLeadCount() == null ? 0 : batch.getGeneratedLeadCount()) + generated);
        batch.setUpdatedBy(operatorEmpId);
        batch.setUpdatedTime(LocalDateTime.now());
    }

    private MarketingLeadInfo createImportedLead(MarketingLeadImportBatch batch,
                                                  MarketingLeadImportDetail detail,
                                                  String operatorEmpId) {
        return inNewTransaction(() -> {
            LeadCreateRequest request = toLeadCreateRequest(detail);
            MarketingLeadInfo lead = leadEntryService.createDraft(
                    request, batch.getImportEmpId(), batch.getImportOrgId());
            if (lead == null || lead.getId() == null) {
                throw error("MARKETING_LEAD_IMPORT_CREATE_FAILED", "线索服务未返回有效线索");
            }
            patchImportedLead(lead, batch, detail, operatorEmpId);
            leadMapper.updateById(lead);
            detailMapper.updateHandlingIf(detail.getId(), "PENDING", "GENERATED", lead.getId());
            return lead;
        });
    }

    private <T> T inNewTransaction(Supplier<T> operation) {
        // 明细尚在外层批次事务中，使用保存点隔离行级回滚，避免新连接看不到未提交明细。
        // Mockito 单测及少量离线装配没有事务管理器时仍直接执行。
        if (transactionManager == null) return operation.get();
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        return template.execute(status -> operation.get());
    }

    private void patchImportedLead(MarketingLeadInfo lead, MarketingLeadImportBatch batch,
                                   MarketingLeadImportDetail detail, String operatorEmpId) {
        lead.setLeadSource("LEAD_IMPORT");
        lead.setImportBatchId(batch.getId());
        lead.setBatchRowNo(detail.getRowNo());
        lead.setUpdatedBy(operatorEmpId);
        lead.setUpdatedTime(LocalDateTime.now());
    }

    private void markProcessingRejected(MarketingLeadImportBatch batch,
                                        MarketingLeadImportDetail detail, String message) {
        detail.setValidationStatus("REJECTED");
        detail.setErrorCode("PENDING_LEAD_DUPLICATE");
        detail.setErrorMessage(message);
        detail.setHandlingStatus("SKIPPED");
        moveValidCounter(batch, "REJECTED");
        detailMapper.updateById(detail);
    }

    private void markProcessingError(MarketingLeadImportBatch batch,
                                     MarketingLeadImportDetail detail, String code, String message) {
        detail.setValidationStatus("ERROR");
        detail.setErrorCode(StringUtils.hasText(code) ? code : "MARKETING_LEAD_IMPORT_ROW_FAILED");
        detail.setErrorMessage(StringUtils.hasText(message) ? message : "该行处理失败，请修正后重新导入");
        detail.setHandlingStatus("SKIPPED");
        moveValidCounter(batch, "ERROR");
        detailMapper.updateById(detail);
    }

    private void moveValidCounter(MarketingLeadImportBatch batch, String targetStatus) {
        batch.setValidCount(Math.max(0, (batch.getValidCount() == null ? 0 : batch.getValidCount()) - 1));
        if ("REJECTED".equals(targetStatus)) {
            batch.setRejectedCount((batch.getRejectedCount() == null ? 0 : batch.getRejectedCount()) + 1);
        } else {
            batch.setErrorCount((batch.getErrorCount() == null ? 0 : batch.getErrorCount()) + 1);
        }
    }

    /**
     * 将导入行还原成手工录入使用的同一份请求对象。
     * 明细表只保留历史兼容字段，新增字段全部从 raw_row_json 读取，避免为模板扩展修改 DDL。
     */
    private LeadCreateRequest toLeadCreateRequest(MarketingLeadImportDetail detail) {
        Map<String, String> row = readRawRow(detail);
        boolean legacyRow = "true".equalsIgnoreCase(row.get("__legacyrawrow"));
        LeadCreateRequest request = new LeadCreateRequest();
        request.setLeadType(normalizeLeadType(first(row, "leadType", "线索类型")));
        request.setCustName(firstOr(row, detail.getCustName(), "custName", "企业名称", "客户名称"));
        request.setUnifiedCreditCode(MarketingLeadEntryService.normalizeCreditCode(
                firstOr(row, detail.getUnifiedCreditCode(), "unifiedCreditCode", "统一社会信用代码", "统一信用代码")));
        request.setCustNo(firstOr(row, detail.getCustNo(), "custNo", "客户号", "客户编号"));
        request.setContactPerson(firstOr(row, detail.getContactPerson(), "contactPerson", "联系人"));
        request.setContactMobile(firstOr(row, detail.getContactMobile(), "contactMobile", "联系电话", "联系人电话"));
        request.setRegisteredAddress(firstOr(row, detail.getRegisteredAddress(), "registeredAddress", "注册地址"));
        request.setBusinessAddress(firstOr(row, detail.getBusinessAddress(), "businessAddress", "经营地址"));
        request.setIndustry(firstOr(row, detail.getIndustry(), "industry", "所属行业", "行业"));
        request.setGroupType(first(row, "groupType", "所属集团类型", "集团类型"));
        request.setGroupName(first(row, "groupName", "所属集团名称", "集团名称"));
        request.setCustomerType(firstOr(row, detail.getCustomerType(), "customerType", "客户类型"));
        request.setEnterpriseType(first(row, "enterpriseType", "企业类型"));
        request.setIsKeystone(parseBoolean(first(row, "isKeystone", "是否基石客户"),
                "isKeystone", null));
        request.setIsAccountOpenedSnapshot(parseBoolean(
                first(row, "isAccountOpenedSnapshot", "isAccountOpened", "是否开户"),
                "isAccountOpenedSnapshot", 0));
        String touchRestricted = first(row, "touchRestricted", "是否触达限制");
        if (!StringUtils.hasText(touchRestricted)) {
            if (legacyRow) {
                // 历史明细没有该列，沿用手工录入默认的“是”，不阻断历史批次确认。
                request.setTouchRestricted(1);
            } else {
                throw invalid("REQUIRED_TOUCH_RESTRICTED", "是否触达限制不能为空");
            }
        } else {
            request.setTouchRestricted(parseBoolean(touchRestricted, "touchRestricted", null));
        }
        request.setCustomerDesc(first(row, "customerDesc", "客户说明"));
        String creditValue = first(row, "creditAmount", "授信金额（万元）", "授信金额");
        String exposureValue = first(row, "creditExposureAmount", "授信敞口金额（万元）",
                "授信敞口金额", "敞口金额");
        if (legacyRow) {
            creditValue = firstOr(row, detail.getCreditAmount() == null ? null
                    : detail.getCreditAmount().toPlainString(), "creditAmount", "授信金额（万元）", "授信金额");
            exposureValue = firstOr(row, detail.getCreditExposureAmount() == null ? null
                    : detail.getCreditExposureAmount().toPlainString(), "creditExposureAmount", "授信敞口金额（万元）",
                    "授信敞口金额", "敞口金额");
        }
        request.setCreditAmount(legacyRow ? amountYuan(creditValue, "creditAmount")
                : amountWan(creditValue, "creditAmount"));
        request.setCreditExposureAmount(legacyRow ? amountYuan(exposureValue, "creditExposureAmount")
                : amountWan(exposureValue, "creditExposureAmount"));

        String managerValue = first(row, "managerEmpIds", "指定客户经理范围", "requestedManagerId",
                "主办客户经理", "客户经理工号", "主办人工号");
        List<String> managerEmpIds = splitDelimited(managerValue);
        request.setManagerEmpIds(managerEmpIds);
        String distributionValue = first(row, "distributionMode", "分配方式");
        String distributionMode = normalizeDistributionMode(distributionValue);
        // 旧模板只有“主办客户经理”列，携带该列时保持旧模板的主办分配语义。
        if (!StringUtils.hasText(distributionValue) && !managerEmpIds.isEmpty()) {
            distributionMode = "OWNER";
        }
        request.setDistributionMode(distributionMode);
        if ("OWNER".equals(distributionMode) && !managerEmpIds.isEmpty()) {
            request.setMainManagerId(managerEmpIds.get(0));
        }
        request.setTagIds(parseTagIds(first(row, "tagIds", "客户标签")));
        return request;
    }

    private Map<String, String> readRawRow(MarketingLeadImportDetail detail) {
        if (detail != null && StringUtils.hasText(detail.getRawRowJson())) {
            try {
                Map<String, String> parsed = JSON.readValue(detail.getRawRowJson(),
                        new TypeReference<Map<String, String>>() { });
                if (parsed != null && !parsed.isEmpty()) {
                    return parsed;
                }
            } catch (IOException ex) {
                // 旧批次曾保存 Map.toString()，下面使用明细列兜底，保证历史批次仍可确认处理。
                log.debug("旧版线索导入明细 rawRowJson 非 JSON，使用兼容字段: rowNo={}",
                        detail.getRowNo());
            }
        }
        Map<String, String> fallback = new HashMap<>();
        if (detail == null) return fallback;
        fallback.put("__legacyrawrow", "true");
        fallback.put("custno", detail.getCustNo());
        fallback.put("custname", detail.getCustName());
        fallback.put("unifiedcreditcode", detail.getUnifiedCreditCode());
        fallback.put("contactperson", detail.getContactPerson());
        fallback.put("contactmobile", detail.getContactMobile());
        fallback.put("registeredaddress", detail.getRegisteredAddress());
        fallback.put("businessaddress", detail.getBusinessAddress());
        fallback.put("industry", detail.getIndustry());
        fallback.put("customertype", detail.getCustomerType());
        fallback.put("creditamount", detail.getCreditAmount() == null
                ? null : detail.getCreditAmount().toPlainString());
        fallback.put("creditexposureamount", detail.getCreditExposureAmount() == null
                ? null : detail.getCreditExposureAmount().toPlainString());
        fallback.put("requestedmanagerid", detail.getRequestedManagerId());
        return fallback;
    }

    private String firstOr(Map<String, String> row, String fallback, String... names) {
        String value = first(row, names);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalizeLeadType(String value) {
        if (!StringUtils.hasText(value)) return "NEW_ACCOUNT";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "NEW_ACCOUNT", "NEW", "新客户", "新客户开户", "新客户开户线索" -> "NEW_ACCOUNT";
            case "EXISTING_MARKETING", "EXISTING", "存量客户营销", "存量客户营销线索" -> "EXISTING_MARKETING";
            default -> throw invalid("INVALID_LEAD_TYPE", "线索类型只能填写新客户开户线索/存量客户营销线索或对应代码");
        };
    }

    private String normalizeDistributionMode(String value) {
        if (!StringUtils.hasText(value)) return "PUBLIC";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PUBLIC", "全行公开认领", "全行公开", "公开认领" -> "PUBLIC";
            case "SCOPE", "指定客户经理范围", "指定范围" -> "SCOPE";
            case "OWNER", "主办专属", "主办人专属", "主办分配" -> "OWNER";
            default -> throw invalid("INVALID_DISTRIBUTION_MODE", "分配方式只能填写全行公开认领/指定客户经理范围/主办专属或对应代码");
        };
    }

    private Integer parseBoolean(String value, String field, Integer defaultValue) {
        if (!StringUtils.hasText(value)) return defaultValue;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "是", "YES", "Y", "TRUE", "1" -> 1;
            case "否", "NO", "N", "FALSE", "0" -> 0;
            default -> throw invalid("INVALID_" + field.replaceAll("([a-z])([A-Z])", "$1_$2")
                    .toUpperCase(Locale.ROOT), "字段值只能填写是/否或对应代码：" + value);
        };
    }

    private List<Long> parseTagIds(String value) {
        List<String> tokens = splitDelimited(value);
        if (tokens.isEmpty()) return List.of();
        List<Long> ids = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            try {
                ids.add(Long.valueOf(token));
            } catch (NumberFormatException ex) {
                throw invalid("INVALID_TAG_IDS", "客户标签必须填写标签ID，当前值：" + token);
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private List<String> splitDelimited(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        List<String> result = new ArrayList<>();
        for (String token : value.trim().split("[,;；，、|\\s]+")) {
            if (StringUtils.hasText(token)) result.add(token.trim());
        }
        return new ArrayList<>(new LinkedHashSet<>(result));
    }

    private BigDecimal amountWan(String value, String field) {
        if (!StringUtils.hasText(value)) return null;
        try {
            BigDecimal amount = new BigDecimal(value.replace(",", "").trim());
            if (amount.signum() < 0) {
                throw invalid("INVALID_" + field.replaceAll("([a-z])([A-Z])", "$1_$2")
                        .toUpperCase(Locale.ROOT), "金额不能为负数");
            }
            return amount.multiply(WAN_TO_YUAN);
        } catch (NumberFormatException ex) {
            throw invalid("INVALID_" + field.replaceAll("([a-z])([A-Z])", "$1_$2")
                    .toUpperCase(Locale.ROOT), "金额必须为数字：" + value);
        }
    }

    private BigDecimal amountYuan(String value, String field) {
        if (!StringUtils.hasText(value)) return null;
        try {
            BigDecimal amount = new BigDecimal(value.replace(",", "").trim());
            if (amount.signum() < 0) {
                throw invalid("INVALID_" + field.replaceAll("([a-z])([A-Z])", "$1_$2")
                        .toUpperCase(Locale.ROOT), "金额不能为负数");
            }
            return amount;
        } catch (NumberFormatException ex) {
            throw invalid("INVALID_" + field.replaceAll("([a-z])([A-Z])", "$1_$2")
                    .toUpperCase(Locale.ROOT), "金额必须为数字：" + value);
        }
    }

    private ImportValidationException invalid(String code, String message) {
        return new ImportValidationException(code, message);
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
        if (file.getSize() > MAX_FILE_SIZE) {
            throw error("MARKETING_LEAD_IMPORT_FILE_TOO_LARGE", "导入文件过大：单个文件不能超过10MB");
        }
    }

    private MarketingLeadImportDetail markError(MarketingLeadImportDetail detail, String code, String message) {
        detail.setValidationStatus("ERROR");
        detail.setErrorCode(code);
        detail.setErrorMessage(message);
        return detail;
    }

    private MarketingLeadImportDetail errorDetail(Long batchId, int rowNo, String message) {
        return errorDetail(batchId, rowNo, "FILE_PARSE_ERROR", message);
    }

    private MarketingLeadImportDetail errorDetail(Long batchId, int rowNo, String code, String message) {
        MarketingLeadImportDetail detail = new MarketingLeadImportDetail();
        detail.setBatchId(batchId);
        detail.setRowNo(rowNo);
        detail.setValidationStatus("ERROR");
        detail.setErrorCode(code);
        detail.setErrorMessage(message);
        detail.setHandlingStatus("PENDING");
        return detail;
    }

    private List<String> missingRequiredHeaders(List<String> headers) {
        Set<String> normalizedHeaders = new LinkedHashSet<>();
        for (String header : headers) {
            String normalized = normalizeHeader(header);
            if (StringUtils.hasText(normalized)) normalizedHeaders.add(normalized);
        }
        List<String> missing = new ArrayList<>();
        for (RequiredHeader required : REQUIRED_HEADERS) {
            if (required.aliases().stream().noneMatch(normalizedHeaders::contains)) {
                missing.add(required.displayName());
            }
        }
        return missing;
    }

    private String parseFailureCode(MultipartFile file) {
        String filename = file == null || file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        return filename.endsWith(".xlsx") || filename.endsWith(".xls")
                ? "FILE_EXCEL_CORRUPTED" : "FILE_PARSE_ERROR";
    }

    private String parseFailureMessage(MultipartFile file) {
        String filename = file == null || file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            return "Excel文件损坏或格式无法读取，请重新保存为.xlsx后重试";
        }
        return "CSV文件无法读取，请确认文件使用UTF-8编码且内容格式正确";
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

    private String toJson(Map<String, String> row) {
        try {
            return JSON.writeValueAsString(row);
        } catch (JsonProcessingException ex) {
            throw error("MARKETING_LEAD_IMPORT_JSON_FAILED", "导入行快照生成失败");
        }
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

    private BizException error(String code, String message) {
        return new BizException(code, message);
    }

    private int safePageNo(Integer pageNo) { return pageNo == null || pageNo < 1 ? 1 : pageNo; }

    private int safePageNo(int pageNo) { return Math.max(1, pageNo); }

    private int safePageSize(Integer pageSize) {
        return pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }

    private int safePageSize(int pageSize) { return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE); }

    private static final class ImportValidationException extends IllegalArgumentException {
        private final String code;

        private ImportValidationException(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    private record RequiredHeader(String displayName, Set<String> aliases) {
    }
}
