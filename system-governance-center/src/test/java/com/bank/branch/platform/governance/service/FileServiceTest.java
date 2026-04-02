package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.entity.BizFileRel;
import com.bank.branch.platform.governance.entity.FileObject;
import com.bank.branch.platform.governance.mapper.BizFileRelMapper;
import com.bank.branch.platform.governance.mapper.FileObjectMapper;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 文件服务单元测试
 * <p>
 * 使用 Mockito 模拟 MinioClient，不依赖真实 MinIO 服务。
 * 覆盖场景：格式校验、大小校验、MD5 去重、正常上传、文件不存在、绑定幂等、列表查询。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    MinioClient minioClient;
    @Mock
    FileObjectMapper fileObjectMapper;
    @Mock
    BizFileRelMapper bizFileRelMapper;

    FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(minioClient, "branch-platform", fileObjectMapper, bizFileRelMapper);
    }

    /**
     * 上传文件格式不在白名单时，应抛出 GOV-42203 异常
     */
    @Test
    void upload_invalidFormat_throwsGov42203() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", new byte[100]);

        assertThatThrownBy(() -> fileService.upload(file, "EMP001"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("GOV-42203");
    }

    /**
     * 上传文件大小超过 50MB 时，应抛出 GOV-42204 异常
     */
    @Test
    void upload_exceedsMaxSize_throwsGov42204() {
        // 创建一个超过 50MB 的文件（实际不分配大数组，使用 Mock 控制 getSize）
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", new byte[100]);
        // 由于 MockMultipartFile 的 getSize() 返回字节数组长度，需要使用 spy 来覆盖
        MockMultipartFile spyFile = spy(file);
        when(spyFile.getSize()).thenReturn(51L * 1024 * 1024);

        assertThatThrownBy(() -> fileService.upload(spyFile, "EMP001"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("GOV-42204");
    }

    /**
     * 上传文件 MD5 已存在时，应直接返回已有记录，不调用 MinIO 上传
     */
    @Test
    void upload_duplicateMd5_returnsExisting() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "hello".getBytes());

        FileObject existing = new FileObject();
        existing.setId("F_EXISTING");
        existing.setFileName("test.pdf");
        existing.setFileSize(5L);
        existing.setFileType("application/pdf");
        existing.setMd5Hash("5d41402abc4b2a76b9719d911017c592");
        existing.setUploadedBy("EMP001");

        when(fileObjectMapper.selectByMd5Hash(anyString())).thenReturn(existing);

        FileObjectDTO result = fileService.upload(file, "EMP001");

        assertThat(result.getId()).isEqualTo("F_EXISTING");
        // MinIO 不应被调用
        verify(minioClient, never()).putObject(any());
        // Mapper insert 不应被调用
        verify(fileObjectMapper, never()).insert(any());
    }

    /**
     * 正常上传文件时，应调用 MinIO putObject 并插入数据库记录
     */
    @Test
    void upload_success_uploadsToMinioAndInsertsRecord() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "data".getBytes());

        when(fileObjectMapper.selectByMd5Hash(anyString())).thenReturn(null);
        when(minioClient.putObject(any())).thenReturn(
                new ObjectWriteResponse(null, "branch-platform", null, "test-path", null, null));

        FileObjectDTO result = fileService.upload(file, "EMP002");

        assertThat(result).isNotNull();
        assertThat(result.getFileName()).isEqualTo("report.xlsx");
        assertThat(result.getUploadedBy()).isEqualTo("EMP002");
        verify(minioClient, times(1)).putObject(any());
        verify(fileObjectMapper, times(1)).insert(any());
    }

    /**
     * 获取下载 URL 时，文件不存在应抛出 GOV-40005 异常
     */
    @Test
    void getDownloadUrl_fileNotFound_throwsGov40005() {
        when(fileObjectMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> fileService.getDownloadUrl("NOT_EXIST"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("GOV-40005");
    }

    /**
     * 绑定文件时，若关联已存在则跳过插入（幂等）
     */
    @Test
    void bindFile_idempotent_skipsIfExists() {
        FileObject fo = new FileObject();
        fo.setId("F_001");
        when(fileObjectMapper.selectById("F_001")).thenReturn(fo);
        when(bizFileRelMapper.existsByBizTypeAndBizIdAndFileObjectId("LEAD", "L001", "F_001"))
                .thenReturn(true);

        fileService.bindFile("LEAD", "L001", "F_001", "ATTACHMENT");

        verify(bizFileRelMapper, never()).insert(any());
    }

    /**
     * 查询业务关联文件列表，应正确委托并返回结果
     */
    @Test
    void listBizFiles_returnsFiles() {
        BizFileRel rel = new BizFileRel();
        rel.setFileObjectId("F_001");
        rel.setFileRole("ATTACHMENT");

        FileObject fo = new FileObject();
        fo.setId("F_001");
        fo.setFileName("doc.pdf");
        fo.setFileSize(1024L);
        fo.setFileType("application/pdf");
        fo.setMd5Hash("abc123");
        fo.setUploadedBy("EMP001");

        when(bizFileRelMapper.selectByBizTypeAndBizId("CUSTOMER", "C001"))
                .thenReturn(List.of(rel));
        when(fileObjectMapper.selectById("F_001")).thenReturn(fo);

        List<FileObjectDTO> result = fileService.listBizFiles("CUSTOMER", "C001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFileName()).isEqualTo("doc.pdf");
        assertThat(result.get(0).getFileRole()).isEqualTo("ATTACHMENT");
    }
}
