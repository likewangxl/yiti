package com.bank.branch.platform.performance.eval.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportResultDTO;
import com.bank.branch.platform.performance.eval.service.EvalAssignImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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

    private EvalAssignImportResultDTO resultWithErrors(int count) {
        EvalAssignImportResultDTO dto = new EvalAssignImportResultDTO();
        dto.setSuccess(false);
        dto.setImportedCount(0);
        List<EvalAssignImportResultDTO.RowError> errors = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            errors.add(new EvalAssignImportResultDTO.RowError(i, "第" + i + "行错误"));
        }
        dto.setErrors(errors);
        return dto;
    }

    @Test
    @DisplayName("错误数据超过 100 条 → 生成错误明细 CSV 文件，返回 null（响应已写为文件流）")
    void importExcel_errorsOver100_writesCsv() throws IOException {
        when(evalAssignImportService.importExcel(any(), any(), any(), any(), any()))
                .thenReturn(resultWithErrors(101));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseWrapper<EvalAssignImportResultDTO> ret =
                controller.importExcel(file, "EVAL", "测试任务", deadline, response);

        assertThat(ret).isNull();
        assertThat(response.getContentType()).contains("text/csv");
        assertThat(response.getHeader("Content-Disposition")).contains("attachment");
        String body = response.getContentAsString();
        // 含 UTF-8 BOM + 表头（行号/错误信息）
        assertThat(body).startsWith("﻿");
        assertThat(body).contains("行号");
        assertThat(body).contains("错误信息");
        // 含具体行号与错误信息
        assertThat(body).contains("1");
        assertThat(body).contains("第1行错误");
        assertThat(body).contains("101");
        assertThat(body).contains("第101行错误");
    }

    @Test
    @DisplayName("错误数据正好 100 条（未超过）→ 返回 JSON，不生成 CSV")
    void importExcel_errorsExactly100_returnsJson() throws IOException {
        when(evalAssignImportService.importExcel(any(), any(), any(), any(), any()))
                .thenReturn(resultWithErrors(100));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseWrapper<EvalAssignImportResultDTO> ret =
                controller.importExcel(file, "EVAL", "测试任务", deadline, response);

        assertThat(ret).isNotNull();
        assertThat(ret.getData().getErrors()).hasSize(100);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("导入成功 → 返回 JSON，不生成 CSV")
    void importExcel_success_returnsJson() throws IOException {
        EvalAssignImportResultDTO ok = new EvalAssignImportResultDTO();
        ok.setSuccess(true);
        ok.setImportedCount(3);
        when(evalAssignImportService.importExcel(any(), any(), any(), any(), any())).thenReturn(ok);
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseWrapper<EvalAssignImportResultDTO> ret =
                controller.importExcel(file, "EVAL", "测试任务", deadline, response);

        assertThat(ret).isNotNull();
        assertThat(ret.getData().isSuccess()).isTrue();
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("阈值可配置（非写死）：调低到 5，错误 6 条即生成 CSV")
    void importExcel_thresholdConfigurable_lowThresholdTriggersCsv() throws IOException {
        controller.setErrorCsvThreshold(5);
        when(evalAssignImportService.importExcel(any(), any(), any(), any(), any()))
                .thenReturn(resultWithErrors(6));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseWrapper<EvalAssignImportResultDTO> ret =
                controller.importExcel(file, "EVAL", "测试任务", deadline, response);

        assertThat(ret).isNull();
        assertThat(response.getContentType()).contains("text/csv");
        assertThat(response.getContentAsString()).contains("第6行错误");
    }

    @Test
    @DisplayName("错误信息含逗号/引号 → CSV 字段正确转义（包裹双引号并转义内部引号）")
    void importExcel_messageWithCommaAndQuote_csvEscaped() throws IOException {
        EvalAssignImportResultDTO dto = resultWithErrors(101);
        dto.getErrors().set(0, new EvalAssignImportResultDTO.RowError(1, "权重标签,不存在：\"主要\""));
        when(evalAssignImportService.importExcel(any(), any(), any(), any(), any())).thenReturn(dto);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.importExcel(file, "EVAL", "测试任务", deadline, response);

        String body = response.getContentAsString();
        // 含逗号/引号的字段必须整体加双引号，内部引号转义为两个双引号
        assertThat(body).contains("\"权重标签,不存在：\"\"主要\"\"\"");
    }
}
