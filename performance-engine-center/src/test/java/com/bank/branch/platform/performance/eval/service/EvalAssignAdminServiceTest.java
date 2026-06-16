package com.bank.branch.platform.performance.eval.service;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalAssignAdminServiceTest {

    @Mock EvalAssignBatchMapper batchMapper;
    @Mock EvalAssignItemMapper itemMapper;
    @Mock DictApi dictApi;
    EvalAssignAdminService service;

    @BeforeEach
    void setUp() {
        service = new EvalAssignAdminService(batchMapper, itemMapper, dictApi);
    }

    private EvalAssignItem item(String scoreType) {
        EvalAssignItem it = new EvalAssignItem();
        it.setEvalUserId("ID_E1");
        it.setEvalUserName("评一");
        it.setBeEvalUserId("ID_B1");
        it.setBeEvalUserName("被一");
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
        // 字典编码 → 中文名称 反查
        lenient().when(dictApi.getDictLabel("EVAL_SCORE_TYPE", "NUM")).thenReturn("数值打分");
        lenient().when(dictApi.getDictLabel("EVAL_SCORE_TYPE", "GRADE")).thenReturn("等级打分");

        byte[] data = service.exportItems(2L);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = wb.getSheetAt(0);
            // 第 9 列（0-based）= 评价类型
            assertThat(sheet.getRow(0).getCell(9).getStringCellValue()).isEqualTo("评价类型");
            Row r1 = sheet.getRow(1);
            Row r2 = sheet.getRow(2);
            assertThat(r1.getCell(9).getStringCellValue()).isEqualTo("数值打分");
            assertThat(r2.getCell(9).getStringCellValue()).isEqualTo("等级打分");
        }
    }
}
