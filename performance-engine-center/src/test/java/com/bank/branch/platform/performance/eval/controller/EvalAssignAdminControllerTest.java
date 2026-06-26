package com.bank.branch.platform.performance.eval.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportAcceptedDTO;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportRow;
import com.bank.branch.platform.performance.eval.service.EvalAssignImportService;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EvalAssignAdminController} 单元测试（异步化重写）。
 *
 * <p>导入接口由「同步返回结果 / 错误 CSV 文件流」改为「异步受理」：解析 → 建 IMPORTING 批次 →
 * 触发异步处理 → 立即返回 {@code {batchId, status:3}}。</p>
 */
@ExtendWith(MockitoExtension.class)
class EvalAssignAdminControllerTest {

    @Mock EvalAssignImportService evalAssignImportService;
    @Mock CurrentUserApi currentUserApi;
    EvalAssignAdminController controller;

    private final LocalDateTime deadline = LocalDateTime.now().plusDays(7);
    private final MockMultipartFile file = new MockMultipartFile("file", "t.xlsx", null, new byte[]{1});

    @BeforeEach
    void setUp() {
        controller = new EvalAssignAdminController(evalAssignImportService, currentUserApi);
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("ADMIN");
    }

    @Test
    @DisplayName("受理：解析 → 建 IMPORTING 批次 → 触发异步 → 返回 {batchId, status:3}")
    void importExcel_returnsAcceptedBatchIdAndStatus3() {
        List<EvalAssignImportRow> rows = List.of(new EvalAssignImportRow());
        when(evalAssignImportService.parseRows(any())).thenReturn(rows);
        when(evalAssignImportService.createImportingBatch("EVAL", "测试任务", deadline, "ADMIN"))
                .thenReturn(99L);

        ResponseWrapper<EvalAssignImportAcceptedDTO> ret =
                controller.importExcel(file, "EVAL", "测试任务", deadline);

        assertThat(ret).isNotNull();
        assertThat(ret.getData().getBatchId()).isEqualTo(99L);
        assertThat(ret.getData().getStatus()).isEqualTo(3);
        // 异步处理被触发（同 batchId + 解析行集）
        verify(evalAssignImportService)
                .processImport(eq(99L), eq(rows), eq("EVAL"), eq("测试任务"), eq(deadline), eq("ADMIN"));
    }

    @Test
    @DisplayName("解析失败（空文件/格式错）→ 同步快速反馈原错误码，不建批次、不触发异步")
    void importExcel_parseFails_propagatesAndShortCircuits() {
        when(evalAssignImportService.parseRows(any()))
                .thenThrow(new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_EMPTY));

        assertThatThrownBy(() -> controller.importExcel(file, "EVAL", "测试任务", deadline))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("导入文件为空");

        verify(evalAssignImportService, never()).createImportingBatch(any(), any(), any(), any());
        verify(evalAssignImportService, never())
                .processImport(any(), anyList(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("@BizAuth(EVAL, IMPORT) 不变")
    void importExcel_declaresBizAuthEvalImport() throws NoSuchMethodException {
        Method m = EvalAssignAdminController.class.getMethod("importExcel",
                MultipartFile.class, String.class, String.class, LocalDateTime.class);
        BizAuth biz = m.getAnnotation(BizAuth.class);
        assertThat(biz).isNotNull();
        assertThat(biz.bizType()).isEqualTo(BizType.EVAL);
        assertThat(biz.action()).isEqualTo(BizAction.IMPORT);
    }
}
