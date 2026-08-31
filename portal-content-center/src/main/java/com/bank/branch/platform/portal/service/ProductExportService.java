package com.bank.branch.platform.portal.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.trace.MdcUtils;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.portal.api.dto.ResponsibleEmpDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import com.bank.branch.platform.portal.service.dto.ProductExportRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 产品导出服务 (D.7)
 * <p>V1 仅支持同步导出 (≤5000 行)，超过阈值返回 PORTAL-42207</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductExportService {

    private final ProductInfoMapper productInfoMapper;
    private final DictApi dictApi;
    private final OrgApi orgApi;
    private final AddrbookQueryService addrbookQueryService;
    private final UserProductRelationService userProductRelationService;
    private final AuditApi auditApi;

    private static final long SYNC_EXPORT_THRESHOLD = 5000L;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 导出产品列表到输出流
     *
     * @param keyword  关键词筛选（可为 null）
     * @param category 类别筛选（可为 null）
     * @param status   状态筛选（可为 null，默认 ACTIVE）
     * @param output   输出流
     * @param operatorEmpId 操作人工号（审计用）
     */
    public void exportToStream(String keyword, String category, String status,
                               OutputStream output, String operatorEmpId) {
        String effectiveStatus = status != null ? status : "ACTIVE";

        // 1. 计数检查
        long count = productInfoMapper.countPage(keyword, category, effectiveStatus);
        if (count > SYNC_EXPORT_THRESHOLD) {
            throw new BizException(PortalErrorCode.EXPORT_ROWS_LIMIT_EXCEEDED.getCode(),
                    "V1 暂不支持异步导出，当前命中 " + count + " 行超过同步阈值 " + SYNC_EXPORT_THRESHOLD + "，请增加过滤条件");
        }

        // 2. 查询全量数据（导出不分页）
        List<ProductInfo> entities = productInfoMapper.selectPage(keyword, category, effectiveStatus, 0, (int) count);

        // 3. 统一批量回填导出所需的跨表信息，避免按产品逐行查询关系、人员和机构。
        Map<String, List<String>> responsibleMap = userProductRelationService.mapUserIdsByProductIds(
                entities.stream().map(ProductInfo::getId).collect(Collectors.toList()));
        Map<String, ResponsibleEmpDTO> employeeMap = resolveResponsibleEmps(responsibleMap);
        Map<String, String> orgNameMap = resolveOrgNames(entities);
        Map<String, String> categoryLabelMap = resolveCategoryLabels();

        // 4. 转换为导出行
        List<ProductExportRow> rows = entities.stream()
                .map(entity -> toExportRow(entity, responsibleMap, employeeMap, orgNameMap, categoryLabelMap))
                .collect(Collectors.toList());

        // 5. 写入 Excel（autoCloseStream=false 避免关闭外部 OutputStream）
        EasyExcel.write(output, ProductExportRow.class)
                .autoCloseStream(false)
                .sheet("产品资料")
                .doWrite(rows);

        // 6. 审计日志（导出为高危操作）
        safeAuditLog(AuditLogCmd.builder()
                .traceId(MdcUtils.getTraceId())
                .empId(operatorEmpId)
                .bizType("PRODUCT")
                .bizAction("EXPORT")
                .resourceUrl("/api/products/export")
                .requestMethod("GET")
                .requestParams("keyword=" + keyword + "&category=" + category + "&status=" + effectiveStatus + "&rowCount=" + rows.size())
                .responseStatus(200)
                .build());

        log.info("[ProductExport] operator={} exported {} rows", operatorEmpId, rows.size());
    }

    /**
     * 将实体转为导出行（含字典翻译、机构名、负责人脱敏手机）
     */
    private ProductExportRow toExportRow(ProductInfo entity,
                                         Map<String, List<String>> responsibleMap,
                                         Map<String, ResponsibleEmpDTO> employeeMap,
                                         Map<String, String> orgNameMap,
                                         Map<String, String> categoryLabelMap) {
        ProductExportRow row = new ProductExportRow();
        row.setProductCode(entity.getProductCode());
        row.setProductName(entity.getProductName());

        // 字典翻译
        row.setProductCategoryDesc(categoryLabelMap.getOrDefault(entity.getProductCategory(), entity.getProductCategory()));

        // 产品说明（去换行符）
        row.setDescription(entity.getDescription() != null
                ? entity.getDescription().replaceAll("[\\r\\n]+", " ") : "");

        // 是否支持中场支持
        row.setSupportForSupportRequest(Boolean.TRUE.equals(entity.getSupportForSupportRequest()) ? "是" : "否");

        // 机构名
        row.setProductDeptOrgName(orgNameMap.getOrDefault(entity.getProductDeptOrgCode(), ""));

        // 负责人（含脱敏手机）
        List<String> empIds = responsibleMap.getOrDefault(entity.getId(), Collections.emptyList());
        if (!empIds.isEmpty()) {
            row.setResponsibleEmpNames(empIds.stream()
                    .map(id -> employeeMap.containsKey(id) ? employeeMap.get(id).getEmpName() : id)
                    .collect(Collectors.joining("、")));
            row.setResponsibleEmpMobiles(empIds.stream()
                    .map(id -> employeeMap.containsKey(id) ? employeeMap.get(id).getMobile() : "")
                    .collect(Collectors.joining("、")));
        } else {
            row.setResponsibleEmpNames("");
            row.setResponsibleEmpMobiles("");
        }

        // 状态
        row.setStatusDesc("ACTIVE".equals(entity.getStatus()) ? "启用" : "停用");

        // 更新时间
        row.setUpdatedTime(entity.getUpdatedTime() != null ? entity.getUpdatedTime().format(DT_FMT) : "");

        return row;
    }

    private Map<String, ResponsibleEmpDTO> resolveResponsibleEmps(Map<String, List<String>> responsibleMap) {
        Set<String> empIds = responsibleMap.values().stream()
                .flatMap(List::stream)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        if (empIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ResponsibleEmpDTO> employees = addrbookQueryService.listResponsibleEmps(List.copyOf(empIds));
        if (employees == null || employees.isEmpty()) {
            return Collections.emptyMap();
        }
        return employees.stream().collect(Collectors.toMap(
                ResponsibleEmpDTO::getEmpId, employee -> employee, (a, b) -> a));
    }

    private Map<String, String> resolveOrgNames(List<ProductInfo> entities) {
        Set<String> orgCodes = entities.stream()
                .map(ProductInfo::getProductDeptOrgCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
        if (orgCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<OrgDTO> orgs = orgApi.getOrgsByCodes(orgCodes);
            if (orgs == null) {
                return Collections.emptyMap();
            }
            return orgs.stream().collect(Collectors.toMap(
                    OrgDTO::getOrgCode, OrgDTO::getOrgName, (a, b) -> a));
        } catch (Exception e) {
            log.warn("[ProductExport] batch organization lookup failed", e);
            return Collections.emptyMap();
        }
    }

    private Map<String, String> resolveCategoryLabels() {
        try {
            Map<String, List<DictItemDTO>> dicts = dictApi.batchGetDictItems(Collections.singleton("PRODUCT_CATEGORY"));
            List<DictItemDTO> items = dicts == null ? Collections.emptyList()
                    : dicts.getOrDefault("PRODUCT_CATEGORY", Collections.emptyList());
            return items.stream().collect(Collectors.toMap(
                    DictItemDTO::getDictCode, DictItemDTO::getDictLabel, (a, b) -> a));
        } catch (Exception e) {
            log.warn("[ProductExport] batch category lookup failed", e);
            return Collections.emptyMap();
        }
    }

    private void safeAuditLog(AuditLogCmd cmd) {
        try {
            auditApi.log(cmd);
        } catch (Exception ex) {
            log.warn("[ProductExportService] audit log failed, action={}", cmd.getBizAction(), ex);
        }
    }
}
