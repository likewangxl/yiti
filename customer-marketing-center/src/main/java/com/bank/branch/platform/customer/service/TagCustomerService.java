package com.bank.branch.platform.customer.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.req.TagCustomerImportRow;
import com.bank.branch.platform.customer.dto.resp.TagCustomerImportResultDTO;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.entity.CustTagRel;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import com.bank.branch.platform.customer.mapper.CustTagRelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 标签-客户关联业务服务。
 * <p>
 * 负责标签客户的覆盖式导入（先删后批量插入）和标签关联客户列表查询。
 * importCustomers 采用覆盖式策略：先删除该标签下所有旧关联，再批量插入新关联。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagCustomerService {

    private static final long MAX_IMPORT_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_IMPORT_ROWS = 5000;
    /** 每条批量 SELECT/INSERT/UPDATE SQL 最多处理的关系行数。 */
    private static final int RELATION_BATCH_SIZE = 500;

    private final CustTagMapper tagMapper;
    private final CustTagRelMapper tagRelMapper;
    private final CustMasterMapper masterMapper;

    /**
     * 覆盖式导入标签客户列表。
     * <p>
     * 策略：先 deleteByTagId 删除该标签所有旧关联，再 insertBatch 批量插入新关联。
     * 批量写入在同一事务内执行，任一分批失败时整体回滚。
     * </p>
     *
     * @param tagId         标签 ID
     * @param custIds       客户 ID 列表
     * @param operatorEmpId 操作人员工工号
     */
    @Transactional(rollbackFor = Exception.class)
    public void importCustomers(String tagId, List<String> custIds, String operatorEmpId) {
        log.info("[TagCustomerService.importCustomers] tagId={}, custCount={}, operator={}",
                tagId, custIds.size(), operatorEmpId);

        // 先确认标签存在
        CustTag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BizException(CustomerErrorCode.TAG_NOT_FOUND.getCode(),
                    CustomerErrorCode.TAG_NOT_FOUND.getMessage());
        }

        // 客户ID 有效性校验（CUST-42202）：空列表跳过；非空必须全部存在于营销主档，否则整批回滚
        if (custIds != null && !custIds.isEmpty()) {
            assertCustIdsValid(custIds);
        }

        // 覆盖式：先删除该标签所有旧的客户关联
        int deleted = tagRelMapper.deleteByTagId(tagId);
        log.debug("[TagCustomerService.importCustomers] deleted {} old relations for tagId={}", deleted, tagId);

        if (custIds == null || custIds.isEmpty()) {
            log.info("[TagCustomerService.importCustomers] custIds is empty, skip insertBatch");
            return;
        }

        // 批量构建关联实体
        LocalDateTime now = LocalDateTime.now();
        List<CustTagRel> relList = custIds.stream().map(custId -> {
            CustTagRel rel = new CustTagRel();
            rel.setId(UUID.randomUUID().toString().replace("-", ""));
            rel.setCustId(custId);
            rel.setTagId(tagId);
            rel.setCreatedBy(operatorEmpId);
            rel.setCreatedTime(now);
            rel.setActive(1);
            rel.setEffectiveTime(now);
            rel.setUpdatedBy(operatorEmpId);
            rel.setUpdatedTime(now);
            return rel;
        }).collect(Collectors.toList());

        insertInBatches(relList);
        log.info("[TagCustomerService.importCustomers] inserted {} relations for tagId={}", relList.size(), tagId);
    }

    /**
     * 按 V2 模式导入标签关系。APPEND 保留未出现在本次名单中的关系；REPLACE 将其置为历史失效。
     */
    @Transactional
    public void importCustomers(String tagId, List<String> custIds, String mode, String operatorEmpId) {
        requireImportableTag(tagId);
        List<String> distinctIds = custIds == null ? List.of() : custIds.stream().distinct().toList();
        if (!distinctIds.isEmpty()) assertCustIdsValid(distinctIds);
        applyImport(tagId, distinctIds, normalizeMode(mode), operatorEmpId);
    }

    /**
     * 解析并导入客户标签 Excel。文件内任一行校验失败时不会修改任何标签关联。
     */
    @Transactional(rollbackFor = Exception.class)
    public TagCustomerImportResultDTO importCustomersFile(String tagId, MultipartFile file, String mode,
                                                           String operatorEmpId) {
        CustTag tag = requireImportableTag(tagId);
        String normalizedMode = normalizeMode(mode);
        List<TagCustomerImportRow> rows = parseImportFile(file);

        TagCustomerImportResultDTO result = new TagCustomerImportResultDTO();
        result.setMode(normalizedMode);
        result.setTotalRows(rows.size());

        List<TagCustomerImportResultDTO.RowError> errors = new ArrayList<>();
        List<ValidatedRow> formattedRows = new ArrayList<>();
        Map<String, ValidatedRow> firstByCode = new LinkedHashMap<>();
        int duplicateRows = 0;
        for (int i = 0; i < rows.size(); i++) {
            int excelRow = i + 2;
            TagCustomerImportRow row = rows.get(i);
            String custName = trim(row.getCustName());
            String creditCode = trim(row.getUnifiedCreditCode());
            String rowTagName = trim(row.getTagName());
            boolean valid = true;
            if (custName.isEmpty()) {
                errors.add(rowError(excelRow, creditCode, "客户名称不能为空"));
                valid = false;
            }
            if (creditCode.isEmpty()) {
                errors.add(rowError(excelRow, creditCode, "统一社会信用代码不能为空"));
                valid = false;
            }
            if (rowTagName.isEmpty()) {
                errors.add(rowError(excelRow, creditCode, "标签名称不能为空"));
                valid = false;
            } else if (!tag.getTagName().equals(rowTagName)) {
                errors.add(rowError(excelRow, creditCode,
                        "标签名称必须与当前标签“" + tag.getTagName() + "”一致"));
                valid = false;
            }
            if (!valid) {
                continue;
            }
            ValidatedRow validated = new ValidatedRow(excelRow, custName, creditCode);
            formattedRows.add(validated);
            if (firstByCode.putIfAbsent(creditCode, validated) != null) {
                duplicateRows++;
            }
        }

        Map<String, CustMaster> customerByCode = new LinkedHashMap<>();
        if (!firstByCode.isEmpty()) {
            List<CustMaster> customers = masterMapper.selectByUnifiedCreditCodes(
                    new ArrayList<>(firstByCode.keySet()));
            if (customers != null) {
                for (CustMaster customer : customers) {
                    if (customer != null && customer.getUnifiedCreditCode() != null) {
                        customerByCode.putIfAbsent(trim(customer.getUnifiedCreditCode()), customer);
                    }
                }
            }
        }
        for (ValidatedRow row : formattedRows) {
            CustMaster customer = customerByCode.get(row.creditCode());
            if (customer == null) {
                errors.add(rowError(row.excelRow(), row.creditCode(), "统一社会信用代码在客户主档中不存在"));
            } else if (!row.custName().equals(trim(customer.getCustName()))) {
                errors.add(rowError(row.excelRow(), row.creditCode(),
                        "客户名称与系统不一致，系统名称为“" + trim(customer.getCustName()) + "”"));
            }
        }

        result.setSkippedCount(duplicateRows);
        if (!errors.isEmpty()) {
            result.setSuccess(false);
            result.setErrors(errors);
            return result;
        }

        List<String> customerIds = firstByCode.keySet().stream()
                .map(customerByCode::get)
                .map(CustMaster::getId)
                .toList();
        applyImport(tagId, customerIds, normalizedMode, operatorEmpId);
        result.setSuccess(true);
        result.setImportedCount(customerIds.size());
        log.info("[TagCustomerService.importCustomersFile] tagId={}, mode={}, rows={}, imported={}, skipped={}",
                tagId, normalizedMode, rows.size(), customerIds.size(), duplicateRows);
        return result;
    }

    /** 生成当前标签的导入模板首行，标签信息预先填充以减少误录。 */
    public TagCustomerImportRow getImportTemplateRow(String tagId) {
        CustTag tag = requireImportableTag(tagId);
        TagCustomerImportRow row = new TagCustomerImportRow();
        row.setTagName(tag.getTagName());
        row.setTagDescription(tag.getDescription());
        return row;
    }

    private void applyImport(String tagId, List<String> custIds, String mode, String operatorEmpId) {
        LocalDateTime now = LocalDateTime.now();
        List<CustTagRel> existingRelations = selectExistingRelationsInBatches(tagId, custIds);
        Map<String, CustTagRel> existingByCustomerId = existingRelations.stream()
                .collect(Collectors.toMap(CustTagRel::getCustId, item -> item, (first, ignored) -> first));

        if ("REPLACE".equals(mode)) {
            tagRelMapper.expireActiveByTagId(tagId, operatorEmpId, now);
        }

        List<String> relationIdsToReactivate = new ArrayList<>();
        List<CustTagRel> relationsToInsert = new ArrayList<>();
        for (String custId : custIds) {
            CustTagRel existing = existingByCustomerId.get(custId);
            if (existing != null) {
                if ("REPLACE".equals(mode) || Integer.valueOf(0).equals(existing.getActive())) {
                    relationIdsToReactivate.add(existing.getId());
                }
            } else {
                CustTagRel rel = new CustTagRel();
                rel.setId(UUID.randomUUID().toString().replace("-", ""));
                rel.setCustId(custId);
                rel.setTagId(tagId);
                rel.setCreatedBy(operatorEmpId);
                rel.setCreatedTime(now);
                rel.setActive(1);
                rel.setEffectiveTime(now);
                rel.setUpdatedBy(operatorEmpId);
                rel.setUpdatedTime(now);
                relationsToInsert.add(rel);
            }
        }

        reactivateInBatches(relationIdsToReactivate, operatorEmpId, now);
        insertInBatches(relationsToInsert);
        log.info("[TagCustomerService.applyImport] tagId={}, mode={}, requested={}, existing={}, reactivated={}, inserted={}",
                tagId, mode, custIds.size(), existingRelations.size(),
                relationIdsToReactivate.size(), relationsToInsert.size());
    }

    private List<CustTagRel> selectExistingRelationsInBatches(String tagId, List<String> custIds) {
        if (custIds == null || custIds.isEmpty()) {
            return List.of();
        }
        List<CustTagRel> result = new ArrayList<>();
        for (int from = 0; from < custIds.size(); from += RELATION_BATCH_SIZE) {
            int to = Math.min(from + RELATION_BATCH_SIZE, custIds.size());
            List<CustTagRel> batch = tagRelMapper.selectByTagIdAndCustIds(
                    tagId, new ArrayList<>(custIds.subList(from, to)));
            if (batch != null) {
                result.addAll(batch);
            }
        }
        return result;
    }

    private void reactivateInBatches(List<String> relationIds, String operatorEmpId, LocalDateTime now) {
        for (int from = 0; from < relationIds.size(); from += RELATION_BATCH_SIZE) {
            int to = Math.min(from + RELATION_BATCH_SIZE, relationIds.size());
            tagRelMapper.reactivateBatch(new ArrayList<>(relationIds.subList(from, to)), operatorEmpId, now);
        }
    }

    private void insertInBatches(List<CustTagRel> relations) {
        for (int from = 0; from < relations.size(); from += RELATION_BATCH_SIZE) {
            int to = Math.min(from + RELATION_BATCH_SIZE, relations.size());
            tagRelMapper.insertBatch(new ArrayList<>(relations.subList(from, to)));
        }
    }

    private CustTag requireImportableTag(String tagId) {
        CustTag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BizException(CustomerErrorCode.TAG_NOT_FOUND.getCode(), CustomerErrorCode.TAG_NOT_FOUND.getMessage());
        }
        if (tag.getApprovalStatus() != null
                && (!"APPROVED".equals(tag.getApprovalStatus()) || !"ACTIVE".equals(tag.getStatus()))) {
            throw new BizException(CustomerErrorCode.TAG_NOT_APPROVED.getCode(),
                    CustomerErrorCode.TAG_NOT_APPROVED.getMessage());
        }
        return tag;
    }

    private String normalizeMode(String mode) {
        String normalized = trim(mode).toUpperCase(Locale.ROOT);
        if (!"APPEND".equals(normalized) && !"REPLACE".equals(normalized)) {
            throw new BizException(CustomerErrorCode.TAG_IMPORT_VALIDATION_FAILED.getCode(),
                    "标签导入模式仅支持 APPEND 或 REPLACE");
        }
        return normalized;
    }

    private List<TagCustomerImportRow> parseImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(CustomerErrorCode.IMPORT_FILE_EMPTY.getCode(),
                    CustomerErrorCode.IMPORT_FILE_EMPTY.getMessage());
        }
        if (file.getSize() > MAX_IMPORT_FILE_SIZE) {
            throw new BizException(CustomerErrorCode.IMPORT_FILE_TOO_LARGE.getCode(),
                    CustomerErrorCode.IMPORT_FILE_TOO_LARGE.getMessage());
        }
        String fileName = file.getOriginalFilename();
        String lowerName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".xlsx") && !lowerName.endsWith(".xls")) {
            throw new BizException(CustomerErrorCode.IMPORT_FILE_FORMAT_INVALID.getCode(),
                    "导入文件格式错误，仅支持 xlsx/xls");
        }
        List<TagCustomerImportRow> rows;
        try {
            rows = EasyExcel.read(file.getInputStream())
                    .head(TagCustomerImportRow.class).sheet().doReadSync();
        } catch (Exception ex) {
            log.warn("[TagCustomerService.parseImportFile] fileName={}, 解析失败: {}", fileName, ex.getMessage());
            throw new BizException(CustomerErrorCode.IMPORT_FILE_FORMAT_INVALID.getCode(),
                    "导入文件无法解析，请使用系统提供的模板");
        }
        if (rows == null || rows.isEmpty()) {
            throw new BizException(CustomerErrorCode.IMPORT_FILE_EMPTY.getCode(),
                    "导入文件中没有客户数据");
        }
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new BizException(CustomerErrorCode.IMPORT_ROWS_TOO_MANY.getCode(),
                    CustomerErrorCode.IMPORT_ROWS_TOO_MANY.getMessage());
        }
        return rows;
    }

    private static TagCustomerImportResultDTO.RowError rowError(int row, String creditCode, String message) {
        return new TagCustomerImportResultDTO.RowError(row, creditCode, message);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record ValidatedRow(int excelRow, String custName, String creditCode) {
    }

    /**
     * 查询标签关联的客户主档列表，用于导出。
     * <p>
     * 先通过 tag_rel 查出 custId 列表，再批量查询客户主档。
     * 标签下无客户时返回空列表，不调用 masterMapper。
     * </p>
     *
     * @param tagId 标签 ID
     * @return 该标签关联的客户主档列表
     */
    public List<CustMaster> listCustomersForExport(String tagId) {
        log.info("[TagCustomerService.listCustomersForExport] tagId={}", tagId);
        List<String> custIds = tagRelMapper.selectCustIdsByTagId(tagId);
        if (custIds == null || custIds.isEmpty()) {
            log.debug("[TagCustomerService.listCustomersForExport] tagId={} 无关联客户", tagId);
            return List.of();
        }
        return masterMapper.selectByIds(custIds);
    }

    /**
     * 查询标签关联的客户列表。
     *
     * @param tagId 标签 ID
     * @return 该标签关联的客户关联记录列表
     */
    public List<CustTagRel> listCustomersByTag(String tagId) {
        log.debug("[TagCustomerService.listCustomersByTag] tagId={}", tagId);
        List<CustTagRel> relations = tagRelMapper.selectByTagId(tagId);
        if (relations == null || relations.isEmpty()) return List.of();
        List<CustMaster> customers = masterMapper.selectByIds(relations.stream()
                .map(CustTagRel::getCustId).distinct().toList());
        if (customers == null || customers.isEmpty()) return relations;
        java.util.Map<String, CustMaster> customerMap = customers.stream()
                .collect(Collectors.toMap(CustMaster::getId, item -> item, (a, b) -> a));
        relations.forEach(rel -> {
            CustMaster customer = customerMap.get(rel.getCustId());
            if (customer != null) {
                rel.setCustNo(customer.getCustNo());
                rel.setCustName(customer.getCustName());
                rel.setUnifiedCreditCode(customer.getUnifiedCreditCode());
            }
        });
        return relations;
    }

    /**
     * 给客户追加标签（幂等：已存在的标签关联跳过，不覆盖）。
     * <p>
     * 逐个判断标签关联是否存在，存在则跳过，不存在则插入新关联。
     * 返回实际新增的标签数量，调用方可通过返回值判断本次操作是否有实际写入。
     * </p>
     *
     * @param custId        客户 ID
     * @param tagIds        待追加的标签 ID 列表
     * @param operatorEmpId 操作人员工工号
     * @return 实际新增的标签关联数量（已存在的不计入）
     */
    public int addTagsToCustomer(String custId, List<String> tagIds, String operatorEmpId) {
        log.info("[TagCustomerService.addTagsToCustomer] custId={}, tagCount={}, operator={}",
                custId, tagIds.size(), operatorEmpId);
        int added = 0;
        LocalDateTime now = LocalDateTime.now();
        for (String tagId : tagIds) {
            // 幂等判断：已存在则跳过，避免破坏唯一索引 uk_cust_tag(cust_id, tag_id)
            CustTagRel existing = tagRelMapper.selectByCustIdAndTagId(custId, tagId);
            if (existing != null) {
                log.debug("[TagCustomerService.addTagsToCustomer] tagId={} already exists for custId={}, skip",
                        tagId, custId);
                continue;
            }
            CustTagRel rel = new CustTagRel();
            rel.setId(UUID.randomUUID().toString().replace("-", ""));
            rel.setTagId(tagId);
            rel.setCustId(custId);
            rel.setCreatedBy(operatorEmpId);
            rel.setCreatedTime(now);
            rel.setActive(1);
            rel.setEffectiveTime(now);
            rel.setUpdatedBy(operatorEmpId);
            rel.setUpdatedTime(now);
            tagRelMapper.insert(rel);
            added++;
        }
        log.info("[TagCustomerService.addTagsToCustomer] custId={}, added={}", custId, added);
        return added;
    }

    /**
     * 取消客户某个标签（物理删除关联记录）。
     *
     * @param custId 客户 ID
     * @param tagId  标签 ID
     * @return true 表示删除成功，false 表示关联不存在
     */
    public boolean removeTagFromCustomer(String custId, String tagId) {
        log.info("[TagCustomerService.removeTagFromCustomer] custId={}, tagId={}", custId, tagId);
        int rows = tagRelMapper.deleteByCustIdAndTagId(custId, tagId);
        return rows > 0;
    }

    /**
     * 校验客户 ID 列表中每一个都存在于 CUSTOMER_MARKET_CUSTOMER，缺失任一抛 CUST-42202。
     * 整批回滚而非逐行剔除，保证导入语义"全有或全无"。
     * 输入先去重再比对，避免重复 ID（如 ["C001","C001","C002"]）触发 IN(...) 去重后 size 不等的假阳性。
     *
     * @param custIds 待校验的客户 ID 列表（非空、非 null）
     */
    private void assertCustIdsValid(List<String> custIds) {
        java.util.Set<String> distinct = new java.util.LinkedHashSet<>(custIds);
        List<CustMaster> existing = masterMapper.selectByIds(new java.util.ArrayList<>(distinct));
        int found = existing == null ? 0 : existing.size();
        if (found != distinct.size()) {
            log.warn("[TagCustomerService.assertCustIdsValid] requested={}, distinct={}, found={}, 校验失败 CUST-42202",
                    custIds.size(), distinct.size(), found);
            throw new BizException(CustomerErrorCode.TAG_IMPORT_VALIDATION_FAILED.getCode(),
                    CustomerErrorCode.TAG_IMPORT_VALIDATION_FAILED.getMessage());
        }
    }
}
