package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.entity.BizFileRel;
import com.bank.branch.platform.governance.entity.FileObject;
import com.bank.branch.platform.governance.mapper.BizFileRelMapper;
import com.bank.branch.platform.governance.mapper.FileObjectMapper;
import com.bank.branch.platform.governance.storage.FileCategory;
import com.bank.branch.platform.governance.storage.ObsStorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 文件服务单元测试。
 * <p>Mock {@link ObsStorageClient}，不依赖真实 OBS。覆盖：格式/大小校验、MD5 去重、
 * 带类型前缀上传、下载预签名、内容读取、删除、绑定幂等、列表查询。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    ObsStorageClient obsStorageClient;
    @Mock
    FileObjectMapper fileObjectMapper;
    @Mock
    BizFileRelMapper bizFileRelMapper;

    FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(obsStorageClient, fileObjectMapper, bizFileRelMapper);
    }

    @Test
    void upload_invalidFormat_throwsGov42203() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", new byte[100]);

        assertThatThrownBy(() -> fileService.upload(file, "EMP001", null, null))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("GOV-42203");
    }

    @Test
    void upload_exceedsMaxSize_throwsGov42204() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", new byte[100]);
        MockMultipartFile spyFile = spy(file);
        when(spyFile.getSize()).thenReturn(51L * 1024 * 1024);

        assertThatThrownBy(() -> fileService.upload(spyFile, "EMP001", null, null))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("GOV-42204");
    }

    @Test
    void upload_duplicateMd5_returnsExisting() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "hello".getBytes());

        FileObject existing = new FileObject();
        existing.setId("F_EXISTING");
        existing.setFileName("test.pdf");
        existing.setMd5Hash("5d41402abc4b2a76b9719d911017c592");
        existing.setUploadedBy("EMP001");

        when(fileObjectMapper.selectByMd5Hash(anyString())).thenReturn(existing);

        FileObjectDTO result = fileService.upload(file, "EMP001", null, null);

        assertThat(result.getId()).isEqualTo("F_EXISTING");
        verify(obsStorageClient, never()).putObject(any(), anyString());
        verify(fileObjectMapper, never()).insert((FileObject) any());
    }

    @Test
    void upload_putsToObsWithCategoryPrefix_andStoresKey() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "r.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "x".getBytes());
        when(fileObjectMapper.selectByMd5Hash(anyString())).thenReturn(null);

        FileObjectDTO dto = fileService.upload(file, "U1", null, null, FileCategory.FREE_REPORT);

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        verify(obsStorageClient).putObject(any(byte[].class), keyCap.capture());
        assertThat(keyCap.getValue()).matches("\\d{4}/\\d{2}/\\d{2}/zybb_[0-9a-f]+\\.xlsx");

        ArgumentCaptor<FileObject> foCap = ArgumentCaptor.forClass(FileObject.class);
        verify(fileObjectMapper).insert(foCap.capture());
        assertThat(foCap.getValue().getStoragePath()).isEqualTo(keyCap.getValue());
        assertThat(foCap.getValue().getBucketName()).isEqualTo("obs");
        assertThat(dto.getFileName()).isEqualTo("r.xlsx");
    }

    @Test
    void uploadBytes_putsToObsWithCategory() {
        when(fileObjectMapper.selectByMd5Hash(anyString())).thenReturn(null);

        FileObjectDTO dto = fileService.upload("data".getBytes(), "exp.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "U2", FileCategory.EXPORT_KPI);

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        verify(obsStorageClient).putObject(any(byte[].class), keyCap.capture());
        assertThat(keyCap.getValue()).matches("\\d{4}/\\d{2}/\\d{2}/jxkpi_[0-9a-f]+\\.xlsx");
        assertThat(dto).isNotNull();
    }

    @Test
    void getDownloadUrl_fileNotFound_throwsGov40005() {
        when(fileObjectMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> fileService.getDownloadUrl("NOT_EXIST"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("GOV-40005");
    }

    @Test
    void getDownloadUrl_success_returnsPresignedUrl() {
        FileObject fo = new FileObject();
        fo.setId("F_001");
        fo.setStoragePath("2026/06/12/zybb_a.xlsx");
        when(fileObjectMapper.selectById("F_001")).thenReturn(fo);
        when(obsStorageClient.generatePresignedUrl("2026/06/12/zybb_a.xlsx"))
                .thenReturn("https://obs/presigned");

        assertThat(fileService.getDownloadUrl("F_001")).isEqualTo("https://obs/presigned");
    }

    @Test
    void getFileContent_readsFromObs() {
        FileObject fo = new FileObject();
        fo.setId("F_002");
        fo.setStoragePath("k");
        when(fileObjectMapper.selectById("F_002")).thenReturn(fo);
        when(obsStorageClient.getBytes("k")).thenReturn("bytes".getBytes());

        assertThat(fileService.getFileContent("F_002")).isEqualTo("bytes".getBytes());
    }

    @Test
    void getFileContent_notFound_throwsGov40005() {
        when(fileObjectMapper.selectById("NOPE")).thenReturn(null);
        assertThatThrownBy(() -> fileService.getFileContent("NOPE"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("GOV-40005");
    }

    @Test
    void bindFile_idempotent_skipsIfExists() {
        FileObject fo = new FileObject();
        fo.setId("F_001");
        when(fileObjectMapper.selectById("F_001")).thenReturn(fo);
        when(bizFileRelMapper.existsByBizTypeAndBizIdAndFileObjectId("LEAD", "L001", "F_001"))
                .thenReturn(true);

        fileService.bindFile("LEAD", "L001", "F_001", "ATTACHMENT");

        verify(bizFileRelMapper, never()).insert((BizFileRel) any());
    }

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

    @Test
    void deleteFile_notFound_throwsGov40005() {
        when(fileObjectMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> fileService.deleteFile("NOT_EXIST"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("GOV-40005");
    }

    @Test
    void deleteFile_success_deletesObsAndRecordAndRelations() {
        FileObject fo = new FileObject();
        fo.setId("F_001");
        fo.setStoragePath("2026/06/12/zybb_x.pdf");
        when(fileObjectMapper.selectById("F_001")).thenReturn(fo);
        when(fileObjectMapper.deleteById("F_001")).thenReturn(1);
        when(bizFileRelMapper.deleteByFileObjectId("F_001")).thenReturn(2);

        fileService.deleteFile("F_001");

        verify(obsStorageClient).deleteByKey("2026/06/12/zybb_x.pdf");
        verify(fileObjectMapper).deleteById("F_001");
        verify(bizFileRelMapper).deleteByFileObjectId("F_001");
    }

    @Test
    void bindFile_fileNotFound_throwsGov40005() {
        when(fileObjectMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> fileService.bindFile("LEAD", "L001", "NOT_EXIST", "ATTACHMENT"))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo("GOV-40005");
    }

    @Test
    void bindFile_newRelation_insertsRecord() {
        FileObject fo = new FileObject();
        fo.setId("F_001");
        fo.setUploadedBy("EMP001");
        when(fileObjectMapper.selectById("F_001")).thenReturn(fo);
        when(bizFileRelMapper.existsByBizTypeAndBizIdAndFileObjectId("LEAD", "L001", "F_001"))
                .thenReturn(false);
        when(bizFileRelMapper.insert((BizFileRel) any())).thenReturn(1);

        fileService.bindFile("LEAD", "L001", "F_001", "ATTACHMENT");

        verify(bizFileRelMapper).insert(argThat((BizFileRel rel) ->
                "LEAD".equals(rel.getBizType())
                        && "L001".equals(rel.getBizId())
                        && "F_001".equals(rel.getFileObjectId())
                        && "ATTACHMENT".equals(rel.getFileRole())
        ));
    }

    @Test
    void listBizFiles_noRelations_returnsEmptyList() {
        when(bizFileRelMapper.selectByBizTypeAndBizId("CUSTOMER", "C999"))
                .thenReturn(Collections.emptyList());

        List<FileObjectDTO> result = fileService.listBizFiles("CUSTOMER", "C999");

        assertThat(result).isEmpty();
    }

    @Test
    void upload_validFormatPdf_succeeds() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.PDF", "application/pdf", "data".getBytes());
        when(fileObjectMapper.selectByMd5Hash(anyString())).thenReturn(null);

        FileObjectDTO result = fileService.upload(file, "EMP001", null, null);

        assertThat(result).isNotNull();
        verify(obsStorageClient).putObject(any(byte[].class), anyString());
    }
}
