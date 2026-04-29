package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.resp.LeadImportPreviewResp;
import com.bank.branch.platform.customer.entity.LeadImportBatch;
import com.bank.branch.platform.customer.enums.BatchStatus;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.LeadImportBatchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 线索批量导入服务（简化实现）。
 * <p>
 * 提供预览（preview）、执行（execute）、查询批次等功能。
 * 预览阶段：解析文件行数和错误统计，创建批次记录但不写入线索数据。
 * 执行阶段：基于已创建的批次记录，更新批次状态为 PENDING_APPROVAL。
 * 完整的行级别解析和校验在实际场景中需扩展。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadImportService {

    private final LeadImportBatchMapper batchMapper;
    private final CustLeadMapper leadMapper;

    private static final int MAX_IMPORT_ROWS = 5000;
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("csv", "xlsx", "xls");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 导入预览：解析文件行数和基本校验，创建批次记录（不写入线索数据）。
     * <p>
     * 以 CSV/XLSX 格式解析，统计总行数和错误行数。
     * 此方法不将数据持久化到 cust_lead 表，只创建 lead_import_batch 记录。
     * </p>
     *
     * @param file          上传的导入文件
     * @param operatorEmpId 操作人员工工号
     * @param orgCode       归属机构代码
     * @return 预览结果（批次ID + 行数统计）
     */
    @Transactional
    public LeadImportPreviewResp preview(MultipartFile file, String operatorEmpId, String orgCode) {
        log.info("[LeadImportService.preview] fileName={}, operator={}", file.getOriginalFilename(), operatorEmpId);

        // 文件格式校验（CUST-42203）：根据扩展名识别 csv/xlsx/xls
        assertFileFormatAllowed(file.getOriginalFilename());

        // 空文件校验（CUST-40005）
        if (file.isEmpty()) {
            throw new BizException(CustomerErrorCode.IMPORT_FILE_EMPTY.getCode(),
                    CustomerErrorCode.IMPORT_FILE_EMPTY.getMessage());
        }

        // 文件大小校验（CUST-42204）：限制 10MB 防止内存溢出
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BizException(CustomerErrorCode.IMPORT_FILE_TOO_LARGE.getCode(),
                    CustomerErrorCode.IMPORT_FILE_TOO_LARGE.getMessage());
        }

        // 解析文件行数（简化：按换行符计算，减去表头行）
        int totalRows = 0;
        int errorRows = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    // 跳过表头
                    firstLine = false;
                    continue;
                }
                if (!line.trim().isEmpty()) {
                    totalRows++;
                }
            }
        } catch (Exception e) {
            log.warn("[LeadImportService.preview] 文件解析异常: {}", e.getMessage());
            // 解析失败时设置行数为 0，交由前端决策
        }

        // 行数超限校验（CUST-42205）
        if (totalRows > MAX_IMPORT_ROWS) {
            throw new BizException(CustomerErrorCode.IMPORT_ROWS_TOO_MANY.getCode(),
                    CustomerErrorCode.IMPORT_ROWS_TOO_MANY.getMessage());
        }

        // 创建批次记录
        String batchId = UUID.randomUUID().toString().replace("-", "");
        String batchNo = generateBatchNo();
        LocalDateTime now = LocalDateTime.now();

        LeadImportBatch batch = new LeadImportBatch();
        batch.setId(batchId);
        batch.setBatchNo(batchNo);
        batch.setSourceFileName(file.getOriginalFilename());
        batch.setStatus(BatchStatus.CREATED.getCode());
        batch.setTotalRowCount(totalRows);
        batch.setErrorRowCount(errorRows);
        batch.setOwnerOrgId(orgCode);
        batch.setCreatedBy(operatorEmpId);
        batch.setCreatedTime(now);
        batch.setUpdatedBy(operatorEmpId);
        batch.setUpdatedTime(now);

        batchMapper.insert(batch);

        LeadImportPreviewResp resp = new LeadImportPreviewResp();
        resp.setBatchId(batchId);
        resp.setBatchNo(batchNo);
        resp.setTotalRows(totalRows);
        resp.setErrorRows(errorRows);
        // TODO: 行级校验逻辑将在后续补齐；当前简化实现下默认全部成功，无错误样本
        resp.setSuccessCount(totalRows);
        resp.setFailCount(0);
        resp.setErrorSamples(Collections.emptyList());

        log.info("[LeadImportService.preview] batchId={}, totalRows={}, successCount={}, failCount={}",
                batchId, totalRows, resp.getSuccessCount(), resp.getFailCount());
        return resp;
    }

    /**
     * 执行导入：基于预览创建的批次记录，更新批次状态为 PENDING_APPROVAL。
     * <p>
     * 简化实现：直接更新批次状态，实际生产中需要读取批次关联的原始文件并逐行写入 cust_lead 表。
     * </p>
     *
     * @param batchId       批次ID
     * @param operatorEmpId 操作人员工工号
     * @param orgCode       归属机构代码
     */
    @Transactional
    public void execute(String batchId, String operatorEmpId, String orgCode) {
        log.info("[LeadImportService.execute] batchId={}, operator={}", batchId, operatorEmpId);

        LeadImportBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(CustomerErrorCode.BATCH_NOT_FOUND.getCode(),
                    CustomerErrorCode.BATCH_NOT_FOUND.getMessage());
        }

        // 更新批次状态为待审批
        LeadImportBatch updateEntity = new LeadImportBatch();
        updateEntity.setId(batchId);
        updateEntity.setStatus(BatchStatus.PENDING_APPROVAL.getCode());
        updateEntity.setUpdatedBy(operatorEmpId);
        updateEntity.setUpdatedTime(LocalDateTime.now());

        batchMapper.updateById(updateEntity);
        log.info("[LeadImportService.execute] batchId={} status updated to PENDING_APPROVAL", batchId);
    }

    /**
     * 按 ID 查询导入批次，不存在时抛出业务异常。
     *
     * @param batchId 批次ID
     * @return 导入批次实体
     */
    public LeadImportBatch getBatchById(String batchId) {
        log.debug("[LeadImportService.getBatchById] batchId={}", batchId);
        LeadImportBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(CustomerErrorCode.BATCH_NOT_FOUND.getCode(),
                    CustomerErrorCode.BATCH_NOT_FOUND.getMessage());
        }
        return batch;
    }

    /**
     * 分页查询导入批次列表。
     * <p>
     * offset = (pageNo - 1) * pageSize
     * </p>
     *
     * @param keyword  关键词（模糊搜索批次号和文件名）
     * @param status   批次状态过滤
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<LeadImportBatch> listBatches(String keyword, String status, int pageNo, int pageSize) {
        log.debug("[LeadImportService.listBatches] keyword={}, status={}, pageNo={}, pageSize={}",
                keyword, status, pageNo, pageSize);

        int offset = (pageNo - 1) * pageSize;
        List<LeadImportBatch> records = batchMapper.selectPage(keyword, status, offset, pageSize);
        long total = batchMapper.countPage(keyword, status);

        return PageResult.of(pageNo, pageSize, total, records);
    }

    // ============================= 私有辅助方法 =============================

    /**
     * 校验上传文件扩展名是否在允许列表内（仅 csv/xlsx/xls）。
     * 文件名缺失或扩展名不符均抛 {@link CustomerErrorCode#IMPORT_FILE_FORMAT_INVALID} (CUST-42203)。
     *
     * @param filename 上传文件原始名称
     */
    private void assertFileFormatAllowed(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BizException(CustomerErrorCode.IMPORT_FILE_FORMAT_INVALID.getCode(),
                    CustomerErrorCode.IMPORT_FILE_FORMAT_INVALID.getMessage());
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new BizException(CustomerErrorCode.IMPORT_FILE_FORMAT_INVALID.getCode(),
                    CustomerErrorCode.IMPORT_FILE_FORMAT_INVALID.getMessage());
        }
        String ext = filename.substring(dot + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BizException(CustomerErrorCode.IMPORT_FILE_FORMAT_INVALID.getCode(),
                    CustomerErrorCode.IMPORT_FILE_FORMAT_INVALID.getMessage());
        }
    }

    /**
     * 生成唯一批次号，格式：BATCH_{yyyyMMdd}_{4位随机数}。
     *
     * @return 批次号
     */
    private String generateBatchNo() {
        String datePart = LocalDateTime.now().format(DATE_FMT);
        int rand = new Random().nextInt(10000);
        return String.format("BATCH_%s_%04d", datePart, rand);
    }
}
