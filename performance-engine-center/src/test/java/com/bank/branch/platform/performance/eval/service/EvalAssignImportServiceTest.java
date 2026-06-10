package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportResultDTO;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportRow;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalAssignItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvalAssignImportServiceTest {

    @Mock UserApi userApi;
    @Mock DictApi dictApi;
    @Mock EvalAssignBatchMapper batchMapper;
    @Mock EvalAssignItemMapper itemMapper;
    EvalAssignImportService service;

    private final LocalDateTime deadline = LocalDateTime.now().plusDays(7);

    private DictItemDTO dict(String type, String code, String label) {
        DictItemDTO d = new DictItemDTO();
        d.setDictType(type);
        d.setDictCode(code);
        d.setDictLabel(label);
        d.setDictValue(code);
        return d;
    }

    private UserDTO user(String empId) {
        UserDTO u = new UserDTO();
        u.setEmpId(empId);
        return u;
    }

    /** 10 列 helper。 */
    private EvalAssignImportRow row(String beId, String beName, String beDept, String beTag,
                                    String evId, String evName, String evTag, String evDept,
                                    String weight, String scoreType) {
        EvalAssignImportRow r = new EvalAssignImportRow();
        r.setBeEvalUserId(beId);
        r.setBeEvalUserName(beName);
        r.setBeEvalDept(beDept);
        r.setBeEvalTag(beTag);
        r.setEvalUserId(evId);
        r.setEvalUserName(evName);
        r.setEvalUserTag(evTag);
        r.setEvalUserDept(evDept);
        r.setWeightTag(weight);
        r.setScoreTypeText(scoreType);
        return r;
    }

    @BeforeEach
    void setUp() {
        service = new EvalAssignImportService(userApi, dictApi, batchMapper, itemMapper);
        lenient().when(dictApi.getDictItems("EVAL_WEIGHT_TAG")).thenReturn(List.of(
                dict("EVAL_WEIGHT_TAG", "MAIN", "主要"),
                dict("EVAL_WEIGHT_TAG", "MINOR", "次要")));
        lenient().when(dictApi.getDictItems("EVAL_SCORE_TYPE")).thenReturn(List.of(
                dict("EVAL_SCORE_TYPE", "NUM", "数值打分"),
                dict("EVAL_SCORE_TYPE", "GRADE", "等级打分")));
        // 批次插入回填自增主键
        lenient().when(batchMapper.insert(any(EvalAssignBatch.class))).thenAnswer(inv -> {
            ((EvalAssignBatch) inv.getArgument(0)).setBatchId(99L);
            return 1;
        });
    }

    private void mockUsersExist(String... empIds) {
        List<UserDTO> users = new ArrayList<>();
        for (String e : empIds) {
            users.add(user(e));
        }
        when(userApi.getUserByEmpIds(anyList())).thenReturn(users);
    }

    @Test
    @DisplayName("全部合法 → 建批次 + 批量插明细，评价类型映射为编码")
    void importRows_allValid_savesBatchAndItems() {
        mockUsersExist("B1", "B2", "E1", "E2");
        List<EvalAssignImportRow> rows = List.of(
                row("B1", "被一", "信贷部", "客户经理", "E1", "评一", "支行长", "管理部", "主要", "数值打分"),
                row("B2", "被二", "信贷部", "客户经理", "E2", "评二", "支行长", "管理部", "次要", "等级打分"));

        EvalAssignImportResultDTO res = service.importRows(rows, "EVAL", deadline, "ADMIN");

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getImportedCount()).isEqualTo(2);
        assertThat(res.getErrors()).isEmpty();

        ArgumentCaptor<EvalAssignBatch> batchCap = ArgumentCaptor.forClass(EvalAssignBatch.class);
        verify(batchMapper).insert(batchCap.capture());
        assertThat(batchCap.getValue().getTaskType()).isEqualTo("EVAL");
        assertThat(batchCap.getValue().getSource()).isEqualTo("IMPORT");
        assertThat(batchCap.getValue().getDeadline()).isEqualTo(deadline);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EvalAssignItem>> itemCap = ArgumentCaptor.forClass(List.class);
        verify(itemMapper).batchInsert(itemCap.capture());
        List<EvalAssignItem> items = itemCap.getValue();
        assertThat(items).hasSize(2);
        assertThat(items).allMatch(i -> i.getBatchId().equals(99L));
        List<String> types = items.stream().map(EvalAssignItem::getScoreType).collect(Collectors.toList());
        assertThat(types).containsExactly("NUM", "GRADE");
        assertThat(items.get(0).getWeightTag()).isEqualTo("主要");
        assertThat(items.get(0).getBeEvalDept()).isEqualTo("信贷部");
        assertThat(items.get(0).getSubmitted()).isEqualTo(0);
    }

    @Test
    @DisplayName("任一行错误 → 整批不入库")
    void importRows_anyError_savesNothing() {
        mockUsersExist("B1", "E1"); // B2 不存在
        List<EvalAssignImportRow> rows = List.of(
                row("B1", "被一", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分"),
                row("B2", "被二", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分"));

        EvalAssignImportResultDTO res = service.importRows(rows, "EVAL", deadline, "ADMIN");

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getImportedCount()).isZero();
        assertThat(res.getErrors()).hasSize(1);
        assertThat(res.getErrors().get(0).getRow()).isEqualTo(2);
        assertThat(res.getErrors().get(0).getMessage()).contains("被打分员工工号不存在");
        verify(batchMapper, never()).insert(any(EvalAssignBatch.class));
        verify(itemMapper, never()).batchInsert(anyList());
    }

    @Test
    @DisplayName("打分人工号不存在 → 行错误")
    void importRows_scorerNotExist_rowError() {
        mockUsersExist("B1"); // E1 不存在
        EvalAssignImportResultDTO res = service.importRows(
                List.of(row("B1", "被一", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分")),
                "EVAL", deadline, "ADMIN");
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("打分员工工号不存在");
    }

    @Test
    @DisplayName("权重标签不在字典 → 行错误")
    void importRows_weightTagInvalid_rowError() {
        mockUsersExist("B1", "E1");
        EvalAssignImportResultDTO res = service.importRows(
                List.of(row("B1", "被一", "信贷部", "t", "E1", "评一", "t", "d", "不存在权重", "数值打分")),
                "EVAL", deadline, "ADMIN");
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("权重标签");
    }

    @Test
    @DisplayName("评价类型不在字典 → 行错误")
    void importRows_scoreTypeInvalid_rowError() {
        mockUsersExist("B1", "E1");
        EvalAssignImportResultDTO res = service.importRows(
                List.of(row("B1", "被一", "信贷部", "t", "E1", "评一", "t", "d", "主要", "星级")),
                "EVAL", deadline, "ADMIN");
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("评价类型");
    }

    @Test
    @DisplayName("文件内 (打分人,被打分人) 组合重复 → 行错误")
    void importRows_duplicatePair_rowError() {
        mockUsersExist("B1", "E1");
        EvalAssignImportResultDTO res = service.importRows(List.of(
                row("B1", "被一", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分"),
                row("B1", "被一", "信贷部", "t", "E1", "评一", "t", "d", "次要", "等级打分")),
                "EVAL", deadline, "ADMIN");
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().stream().anyMatch(e -> e.getMessage().contains("重复"))).isTrue();
    }

    @Test
    @DisplayName("打分人/被打分人编号为空 → 行错误")
    void importRows_emptyIds_rowError() {
        EvalAssignImportResultDTO res = service.importRows(List.of(
                row("", "被一", "信贷部", "t", "E1", "评一", "t", "d", "主要", "数值打分")),
                "EVAL", deadline, "ADMIN");
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getErrors().get(0).getMessage()).contains("被打分员工编号");
        verify(batchMapper, never()).insert(any(EvalAssignBatch.class));
    }
}
