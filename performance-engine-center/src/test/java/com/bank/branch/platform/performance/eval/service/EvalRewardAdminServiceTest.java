package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalRewardItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRewardItemMapper;
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
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link EvalRewardAdminService} 单元测试（奖励分配管理端：批次列表/详情/导出）。
 */
@ExtendWith(MockitoExtension.class)
class EvalRewardAdminServiceTest {

    @Mock EvalAssignBatchMapper batchMapper;
    @Mock EvalRewardItemMapper itemMapper;
    @Mock UserApi userApi;
    EvalRewardAdminService service;

    @BeforeEach
    void setUp() {
        service = new EvalRewardAdminService(batchMapper, itemMapper, userApi);
    }

    private EvalRewardItem item(long id, String be, String total) {
        EvalRewardItem it = new EvalRewardItem();
        it.setItemId(id);
        it.setAssignUserId("ID_A1");
        it.setBeAssignedUserId(be);
        it.setBeAssignedUserName("被" + id);
        it.setDeptName("信贷部");
        it.setOriginalValue(new BigDecimal("76.5"));
        it.setCashValue(new BigDecimal("80"));
        it.setAssignTotal(new BigDecimal(total));
        it.setSubmitted(0);
        return it;
    }

    @Test
    @DisplayName("导出：分配人工号列输出 PT_USER.username（经 UserApi 反查）；被分配人原样")
    void exportItems_assignerUsernameAndValues() throws Exception {
        EvalAssignBatch batch = new EvalAssignBatch();
        batch.setBatchId(9L);
        when(batchMapper.selectById(9L)).thenReturn(batch);
        when(itemMapper.selectByBatchId(eq(9L), anyInt(), anyInt())).thenReturn(List.of(item(1L, "B1", "100")));
        when(itemMapper.selectDistinctAssignerIdsByBatch(9L)).thenReturn(List.of("ID_A1"));
        when(userApi.mapEmpIdsToUsername(anyList())).thenReturn(java.util.Map.of("ID_A1", "assigner01"));

        byte[] data = service.exportItems(9L);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("被分配人工号");
            assertThat(sheet.getRow(0).getCell(6).getStringCellValue()).isEqualTo("分配人工号");
            Row r1 = sheet.getRow(1);
            assertThat(r1.getCell(0).getStringCellValue()).isEqualTo("B1");
            assertThat(r1.getCell(6).getStringCellValue()).isEqualTo("assigner01");
        }
    }

    @Test
    @DisplayName("批次详情：分配人工号(USER_ID)经 UserApi 翻译为 username")
    void getBatchDetail_fillsAssignerUsername() {
        EvalAssignBatch batch = new EvalAssignBatch();
        batch.setBatchId(9L);
        when(batchMapper.selectById(9L)).thenReturn(batch);
        when(itemMapper.selectByBatchId(eq(9L), anyInt(), anyInt())).thenReturn(List.of(item(1L, "B1", "100")));
        when(itemMapper.countByBatchId(9L)).thenReturn(1L);
        when(userApi.mapEmpIdsToUsername(anyList())).thenReturn(java.util.Map.of("ID_A1", "assigner01"));

        @SuppressWarnings("unchecked")
        PageResult<EvalRewardItem> page =
                (PageResult<EvalRewardItem>) service.getBatchDetail(9L, 1, 50).get("items");
        assertThat(page.getRecords().get(0).getAssignUserUsername()).isEqualTo("assigner01");
    }

    @Test
    @DisplayName("批次列表：委托 mapper REWARD 过滤查询")
    void pageBatches_delegates() {
        when(itemMapper.selectRewardBatchesByCondition(eq(2), eq("k"), anyInt(), anyInt()))
                .thenReturn(List.of(new EvalAssignBatch()));
        when(itemMapper.countRewardBatchesByCondition(2, "k")).thenReturn(1L);
        PageResult<EvalAssignBatch> page = service.pageBatches(2, "k", 1, 20);
        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getRecords()).hasSize(1);
    }
}
