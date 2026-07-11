package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalAssignItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignItemMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalAssignAdminServiceTest {

    @Mock EvalAssignBatchMapper batchMapper;
    @Mock EvalAssignItemMapper itemMapper;
    @Mock DictApi dictApi;
    @Mock UserApi userApi;
    EvalAssignAdminService service;

    @BeforeEach
    void setUp() {
        service = new EvalAssignAdminService(batchMapper, itemMapper, dictApi, userApi);
    }

    private EvalAssignItem item(String scoreType) {
        EvalAssignItem it = new EvalAssignItem();
        it.setEvalUserId("ID_E1");
        it.setEvalUserName("评一");
        it.setBeEvalUserId("ID_B1");
        it.setBeEvalUserName("被一");
        it.setBeEvalDept("信贷部");
        it.setGroupDept("零售条线");
        it.setWeightTag("主要");
        it.setScoreType(scoreType);
        it.setSubmitted(0);
        return it;
    }

    @Test
    @DisplayName("导出评价类型列输出字典中文名称而非编码")
    void exportItems_scoreTypeColumn_showsDictLabel() throws Exception {
        EvalAssignBatch batch = new EvalAssignBatch();
        batch.setBatchId(2L);
        when(batchMapper.selectById(2L)).thenReturn(batch);
        when(itemMapper.selectByBatchId(eq(2L), anyInt(), anyInt()))
                .thenReturn(List.of(item("NUM"), item("GRADE")));
        lenient().when(itemMapper.selectDistinctUserIdsByBatch(2L))
                .thenReturn(List.of("ID_E1", "ID_B1"));
        lenient().when(userApi.mapEmpIdsToUsername(anyList())).thenReturn(java.util.Map.of());
        // 字典编码 → 中文名称 反查
        lenient().when(dictApi.getDictLabel("EVAL_SCORE_TYPE", "NUM")).thenReturn("数值打分");
        lenient().when(dictApi.getDictLabel("EVAL_SCORE_TYPE", "GRADE")).thenReturn("等级打分");

        byte[] data = service.exportItems(2L);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = wb.getSheetAt(0);
            // 第 8 列(0-based)=分组部门（新插入，被打分人部门之后）
            assertThat(sheet.getRow(0).getCell(8).getStringCellValue()).isEqualTo("分组部门");
            assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).isEqualTo("零售条线");
            // 第 10 列=评价类型（因分组部门插入右移 1）
            assertThat(sheet.getRow(0).getCell(10).getStringCellValue()).isEqualTo("评价类型");
            Row r1 = sheet.getRow(1);
            Row r2 = sheet.getRow(2);
            assertThat(r1.getCell(10).getStringCellValue()).isEqualTo("数值打分");
            assertThat(r2.getCell(10).getStringCellValue()).isEqualTo("等级打分");
        }
    }

    @Test
    @DisplayName("导出：打分人/被打分人工号列输出 PT_USER.username（经 UserApi 批量反查）")
    void exportItems_userIdColumns_showUsername() throws Exception {
        EvalAssignBatch batch = new EvalAssignBatch();
        batch.setBatchId(2L);
        when(batchMapper.selectById(2L)).thenReturn(batch);
        when(itemMapper.selectByBatchId(eq(2L), anyInt(), anyInt()))
                .thenReturn(List.of(item("NUM")));
        when(itemMapper.selectDistinctUserIdsByBatch(2L))
                .thenReturn(List.of("ID_E1", "ID_B1"));
        // 去重 USER_ID 一次性反查工号
        when(userApi.mapEmpIdsToUsername(anyList()))
                .thenReturn(java.util.Map.of("ID_E1", "scorer01", "ID_B1", "target01"));
        lenient().when(dictApi.getDictLabel(eq("EVAL_SCORE_TYPE"), anyString())).thenReturn("数值打分");

        byte[] data = service.exportItems(2L);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = wb.getSheetAt(0);
            Row r1 = sheet.getRow(1);
            // 第 0 列=打分人工号、第 4 列=被打分人工号，均显示 username
            assertThat(r1.getCell(0).getStringCellValue()).isEqualTo("scorer01");
            assertThat(r1.getCell(4).getStringCellValue()).isEqualTo("target01");
        }
    }

    @Test
    @DisplayName("批次详情：打分人/被打分人工号(USER_ID)经 UserApi 翻译为 PT_USER.username")
    void getBatchDetail_fillsUsernameFromUserApi() {
        EvalAssignBatch batch = new EvalAssignBatch();
        batch.setBatchId(3L);
        when(batchMapper.selectById(3L)).thenReturn(batch);
        when(itemMapper.selectByBatchId(eq(3L), anyInt(), anyInt()))
                .thenReturn(List.of(item("NUM")));
        when(itemMapper.countByBatchId(3L)).thenReturn(1L);
        // ID_E1 / ID_B1 是入库的 USER_ID，UserApi 批量反查得到登录名(工号)
        when(userApi.mapEmpIdsToUsername(anyList()))
                .thenReturn(java.util.Map.of("ID_E1", "scorer01", "ID_B1", "target01"));

        @SuppressWarnings("unchecked")
        com.bank.branch.platform.common.web.PageResult<EvalAssignItem> page =
                (com.bank.branch.platform.common.web.PageResult<EvalAssignItem>)
                        service.getBatchDetail(3L, 1, 50).get("items");
        EvalAssignItem it = page.getRecords().get(0);
        assertThat(it.getEvalUserUsername()).isEqualTo("scorer01");
        assertThat(it.getBeEvalUserUsername()).isEqualTo("target01");
    }
}
