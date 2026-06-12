package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.governance.storage.FileCategory;
import com.bank.branch.platform.portal.controller.dto.announcement.AnnouncementFileDTO;
import com.bank.branch.platform.portal.entity.AnnouncementFile;
import com.bank.branch.platform.portal.mapper.AnnouncementFileMapper;
import com.bank.branch.platform.portal.mapper.AnnouncementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AnnouncementService 单测：公告附件上传走 OBS（FileApi）。
 */
@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock AnnouncementMapper announcementMapper;
    @Mock AnnouncementFileMapper announcementFileMapper;
    @Mock CurrentUserApi currentUserApi;
    @Mock FileApi fileApi;

    AnnouncementService service;

    @BeforeEach
    void setUp() {
        service = new AnnouncementService(announcementMapper, announcementFileMapper, currentUserApi, fileApi);
    }

    @Test
    void uploadFile_putsToObsViaFileApi_andStoresFileId() {
        MockMultipartFile f = new MockMultipartFile("file", "gg.pdf", "application/pdf", "x".getBytes());
        when(currentUserApi.getCurrentEmpId()).thenReturn("E1");
        FileObjectDTO dto = new FileObjectDTO();
        dto.setId("F_GG_1");
        when(fileApi.upload(any(byte[].class), eq("gg.pdf"), anyString(), eq("E1"),
                eq(FileCategory.ANNOUNCEMENT))).thenReturn(dto);

        AnnouncementFileDTO r = service.uploadFile("ANN1", f);

        assertThat(r).isNotNull();
        verify(fileApi).upload(any(byte[].class), eq("gg.pdf"), anyString(), eq("E1"),
                eq(FileCategory.ANNOUNCEMENT));
        ArgumentCaptor<AnnouncementFile> cap = ArgumentCaptor.forClass(AnnouncementFile.class);
        verify(announcementFileMapper).insert(cap.capture());
        // filePath 列复用，存 OBS fileId
        assertThat(cap.getValue().getFilePath()).isEqualTo("F_GG_1");
        assertThat(cap.getValue().getFileName()).isEqualTo("gg.pdf");
    }
}
