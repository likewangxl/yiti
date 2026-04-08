package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.web.GlobalExceptionHandler;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FileController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileService fileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void downloadFile_should302Redirect() throws Exception {
        // given
        when(fileService.getDownloadUrl(anyString()))
                .thenReturn("https://minio.local/bucket/path/file.pdf");

        // when & then：302重定向到预签名URL
        mockMvc.perform(get("/api/files/F_001/download"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("https://minio.local/bucket/path/file.pdf"));
    }

    @Test
    void listBizFiles_shouldReturn200() throws Exception {
        // given
        FileObjectDTO dto = new FileObjectDTO();
        dto.setId("F_001");
        dto.setFileName("test.pdf");
        when(fileService.listBizFiles(anyString(), anyString())).thenReturn(List.of(dto));

        // when & then（Query参数方式）
        mockMvc.perform(get("/api/files")
                .param("bizType", "LOAN")
                .param("bizId", "BIZ_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void upload_shouldReturn200() throws Exception {
        // given
        FileObjectDTO dto = new FileObjectDTO();
        dto.setId("F_NEW");
        dto.setFileName("test.pdf");
        when(fileService.upload(any(), anyString())).thenReturn(dto);

        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "test.pdf", MediaType.APPLICATION_PDF_VALUE, "pdf content".getBytes());

        // when & then
        mockMvc.perform(multipart("/api/files/upload")
                .file(mockFile)
                .param("uploadedBy", "emp001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    @Test
    void deleteFile_shouldReturn200() throws Exception {
        // given
        doNothing().when(fileService).deleteFile(anyString());

        // when & then
        mockMvc.perform(delete("/api/files/F_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    // ── L2 错误路径测试 ──────────────────────────────────────────

    @Test
    void downloadFile_fileNotFound_returnsBizError() throws Exception {
        when(fileService.getDownloadUrl(anyString()))
                .thenThrow(new BizException("GOV-40404", "文件不存在"));

        mockMvc.perform(get("/api/files/NOT_EXIST/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-40404"));
    }

    @Test
    void upload_invalidFormat_returnsBizError() throws Exception {
        when(fileService.upload(any(), anyString()))
                .thenThrow(new BizException("GOV-42203", "文件格式不合法"));

        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", "data".getBytes());

        mockMvc.perform(multipart("/api/files/upload")
                .file(mockFile)
                .param("uploadedBy", "emp001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GOV-42203"));
    }
}
