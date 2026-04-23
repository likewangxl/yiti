package com.bank.branch.platform.performance.service.importer;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.entity.PerfImportBatch;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.service.importer.impl.AllocRelationImportStrategy;
import com.bank.branch.platform.performance.service.importer.model.AllocRelationImportRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AllocRelationImportStrategy 单元测试（Task P5.4 Red）.
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>importType 返回 ALLOC</li>
 *   <li>5 行全合法 → 全部成功，Mapper.insert 调 5 次</li>
 *   <li>必填空 / ACCOUNT 类型缺 accountNo → errorSummary，不抛</li>
 *   <li>重复主键 DuplicateKeyException → errorSummary 记录</li>
 *   <li>列头完全不匹配 → 抛 IMPORT_COLUMN_MAPPING_INVALID</li>
 * </ul>
 */
class AllocRelationImportStrategyTest {

    private CustAllocRelationMapper allocMapper;
    private AllocRelationImportStrategy strategy;
    private PerfImportBatch batch;

    @BeforeEach
    void setUp() {
        allocMapper = mock(CustAllocRelationMapper.class);
        when(allocMapper.insert(any(CustAllocRelation.class))).thenReturn(1);
        strategy = new AllocRelationImportStrategy(allocMapper);

        batch = new PerfImportBatch();
        batch.setId("BATCH_ALLOC");
        batch.setBatchNo("IMP_ALLOC_001");
        batch.setImportType("ALLOC");
        batch.setCreatedBy("admin");
    }

    @Test
    @DisplayName("importType 返回 ALLOC")
    void importType_returnsAlloc() {
        assertThat(strategy.importType()).isEqualTo("ALLOC");
    }

    @Test
    @DisplayName("execute：5 行全合法 RULE 类型 → insert 被调 5 次")
    void execute_5ValidRule_allInserted() {
        List<AllocRelationImportRow> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            rows.add(row("C" + i, "E" + i, null, "2026-04-01", "RULE",
                    "DEP", null, new BigDecimal("100.00")));
        }
        MultipartFile file = writeExcel(rows);

        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getTotalRows()).isEqualTo(5);
        assertThat(result.getSuccessRows()).isEqualTo(5);
        assertThat(result.getErrorRows()).isEqualTo(0);
        assertThat(result.getErrorSummary()).isNullOrEmpty();

        ArgumentCaptor<CustAllocRelation> cap = ArgumentCaptor.forClass(CustAllocRelation.class);
        verify(allocMapper, times(5)).insert(cap.capture());
        CustAllocRelation first = cap.getAllValues().get(0);
        assertThat(first.getCustId()).isEqualTo("C0");
        assertThat(first.getEmpId()).isEqualTo("E0");
        assertThat(first.getAllocDim()).isEqualTo("RULE");
        assertThat(first.getSourceBatchId()).isEqualTo("BATCH_ALLOC");
        assertThat(first.getId()).isNotBlank();
    }

    @Test
    @DisplayName("execute：必填空 / ACCOUNT 缺 accountNo → errorSummary 记录，insert 不被调")
    void execute_invalidRows_recordedInErrorSummary() {
        List<AllocRelationImportRow> rows = new ArrayList<>();
        rows.add(row("", "E001", null, "2026-04-01", "RULE", null, null, new BigDecimal("100"))); // custId 空
        rows.add(row("C001", "", null, "2026-04-01", "RULE", null, null, new BigDecimal("100"))); // empId 空
        rows.add(row("C001", "E001", null, null, "RULE", null, null, new BigDecimal("100")));     // effectiveDate 空
        rows.add(row("C001", "E001", null, "2026-04-01", "ACCOUNT", "DEP", null,
                new BigDecimal("100"))); // ACCOUNT 缺 accountNo

        MultipartFile file = writeExcel(rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getTotalRows()).isEqualTo(4);
        assertThat(result.getSuccessRows()).isEqualTo(0);
        assertThat(result.getErrorRows()).isEqualTo(4);
        assertThat(result.getErrorSummary()).isNotBlank();
        verify(allocMapper, never()).insert(any());
    }

    @Test
    @DisplayName("execute：主键冲突 DuplicateKeyException → errorSummary 记录，不整批回滚")
    void execute_duplicateKey_recordedInErrorSummary() {
        List<AllocRelationImportRow> rows = new ArrayList<>();
        rows.add(row("C001", "E001", null, "2026-04-01", "RULE", "DEP", null, new BigDecimal("100")));
        rows.add(row("C002", "E002", null, "2026-04-01", "RULE", "DEP", null, new BigDecimal("100")));

        // 第一行成功、第二行主键冲突
        when(allocMapper.insert(any(CustAllocRelation.class)))
                .thenReturn(1)
                .thenThrow(new DuplicateKeyException("Duplicate entry"));

        MultipartFile file = writeExcel(rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getSuccessRows()).isEqualTo(1);
        assertThat(result.getErrorRows()).isEqualTo(1);
        assertThat(result.getErrorSummary()).contains("第");
        verify(allocMapper, atLeastOnce()).insert(any());
    }

    @Test
    @DisplayName("execute：ratio 为空 → 默认 100.00 插入")
    void execute_nullRatio_usesDefault100() {
        List<AllocRelationImportRow> rows = new ArrayList<>();
        rows.add(row("C001", "E001", null, "2026-04-01", "RULE", null, null, null));

        MultipartFile file = writeExcel(rows);
        ImportResult result = strategy.execute(batch, file);

        assertThat(result.getSuccessRows()).isEqualTo(1);
        ArgumentCaptor<CustAllocRelation> cap = ArgumentCaptor.forClass(CustAllocRelation.class);
        verify(allocMapper).insert(cap.capture());
        assertThat(cap.getValue().getRatio()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("execute：列头完全不匹配 → 抛 IMPORT_COLUMN_MAPPING_INVALID")
    void execute_columnMismatch_throwsImportColumnMappingInvalid() {
        MultipartFile file = writeExcelWithWrongHead();

        assertThatThrownBy(() -> strategy.execute(batch, file))
                .isInstanceOf(PerfException.class)
                .satisfies(ex -> assertThat(((PerfException) ex).getErrorCode())
                        .isEqualTo(PerfErrorCode.IMPORT_COLUMN_MAPPING_INVALID));
        verify(allocMapper, never()).insert(any());
    }

    // ================ helpers ================

    private static AllocRelationImportRow row(String custId, String empId, String orgCode,
                                              String effectiveDate, String allocType,
                                              String bizKind, String accountNo, BigDecimal ratio) {
        AllocRelationImportRow r = new AllocRelationImportRow();
        r.setCustId(custId);
        r.setEmpId(empId);
        r.setOrgCode(orgCode);
        r.setEffectiveDate(effectiveDate);
        r.setAllocType(allocType);
        r.setBizKind(bizKind);
        r.setAccountNo(accountNo);
        r.setRatio(ratio);
        return r;
    }

    private static MultipartFile writeExcel(List<AllocRelationImportRow> rows) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EasyExcel.write(bos, AllocRelationImportRow.class).sheet("分配关系").doWrite(rows);
        return new MockMultipartFile("file", "alloc.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
    }

    private static MultipartFile writeExcelWithWrongHead() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        List<List<String>> head = new ArrayList<>();
        head.add(List.of("A"));
        head.add(List.of("B"));
        List<List<Object>> data = new ArrayList<>();
        data.add(List.of("x", "y"));
        EasyExcel.write(bos).head(head).sheet("分配关系").doWrite(data);
        return new MockMultipartFile("file", "wrong.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
    }
}
