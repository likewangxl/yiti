package com.bank.branch.platform.customer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.entity.TouchWorklog;
import com.bank.branch.platform.customer.entity.TouchWorklogParticipant;
import com.bank.branch.platform.customer.entity.TouchWorklogPictureRecord;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.dto.resp.TouchWorklogVO;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import com.bank.branch.platform.customer.mapper.TouchWorklogMapper;
import com.bank.branch.platform.customer.mapper.TouchWorklogParticipantMapper;
import com.bank.branch.platform.customer.mapper.TouchWorklogPictureRecordMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 触达日志协同与终态补录契约。
 *
 * <p>这些测试刻意覆盖会议纪要中“多人联合触达”和需求书 CD-04 的完成后补录规则。
 * 先以当前实现运行应稳定失败，再由 TouchLogService 的最小改动使其通过。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TouchLogServiceCollaborationTest {

    @Mock
    private TouchTaskMapper taskMapper;
    @Mock
    private TouchWorklogMapper worklogMapper;
    @Mock
    private TouchWorklogPictureRecordMapper pictureMapper;
    @Mock
    private TouchWorklogParticipantMapper participantMapper;
    @Mock
    private MarketingCustomerInfoMapper customerMapper;

    private TouchLogService service;

    @BeforeEach
    void setUp() {
        service = new TouchLogService(taskMapper, worklogMapper, pictureMapper,
                participantMapper, customerMapper, new ObjectMapper());
        when(pictureMapper.selectActiveByWorklogId(anyLong())).thenReturn(List.of());
        when(participantMapper.selectByWorklogId(anyLong())).thenReturn(List.of());
    }

    @Test
    void successTask_allowsAssigneeToAppendLog_withoutChangingTerminalStatus() {
        TouchTask task = task("1", "SUCCESS", "E001", "ORG-1");
        givenWritableDependencies(task, 10L);

        TouchWorklogVO result = service.addLog(
                "1", "uuid-success-append", "完成后补充客户反馈",
                "[\"/api/files/F-1/download#feedback.jpg\"]",
                LocalDateTime.of(2026, 8, 11, 10, 30), "PHONE",
                null, null, null, "E001", "ORG-1", false);

        assertThat(result).isNotNull();
        verify(worklogMapper).insert(any(TouchWorklog.class));
        verify(taskMapper, never()).markInProgressIfPending(anyLong(), any(), any());
    }

    @Test
    void successTask_allowsRegisteredCollaboratorToAppendLog() {
        TouchTask task = task("2", "SUCCESS", "E001", "ORG-1");
        givenWritableDependencies(task, 20L);

        TouchWorklog firstLog = worklog(20L, 2L, "E001");
        TouchWorklogParticipant collaborator = participant(200L, 20L, "E002", "ORG-1", "COLLABORATOR");
        when(worklogMapper.selectValidByTaskId(2L)).thenReturn(List.of(firstLog));
        when(participantMapper.selectByWorklogId(20L)).thenReturn(List.of(collaborator));

        TouchWorklogVO result = service.addLog(
                "2", "uuid-collaborator-append", "协同人员补充跟进结果",
                "[\"/api/files/F-2/download#follow-up.jpg\"]",
                LocalDateTime.of(2026, 8, 11, 11, 30), "WECHAT",
                null, null, null, "E002", "ORG-1", false);

        assertThat(result).isNotNull();
        verify(worklogMapper).insert(any(TouchWorklog.class));
        verify(taskMapper, never()).markInProgressIfPending(anyLong(), any(), any());
    }

    @Test
    void collaborator_registeredByFirstAssigneeLog_canWriteAfterFirstLog() {
        TouchTask task = task("3", "PENDING", "E001", "ORG-1");
        MarketingCustomerInfo customer = customer(3L);
        when(customerMapper.selectActiveById(3L)).thenReturn(customer);

        List<TouchWorklog> saved = new ArrayList<>();
        when(taskMapper.selectByIdForUpdate("3")).thenAnswer(invocation -> {
            task.setTaskStatus(saved.isEmpty() ? "PENDING" : "IN_PROGRESS");
            return task;
        });
        when(worklogMapper.selectByTaskAndClientUuid(eq(3L), any())).thenReturn(null);
        when(worklogMapper.countValidByTaskId(3L)).thenReturn(0L);
        when(worklogMapper.selectValidByTaskId(3L)).thenAnswer(invocation -> List.copyOf(saved));
        when(worklogMapper.insert(any(TouchWorklog.class))).thenAnswer(invocation -> {
            TouchWorklog row = invocation.getArgument(0);
            row.setId(30L + saved.size());
            saved.add(row);
            return 1;
        });
        TouchWorklogParticipant collaborator = participant(301L, 30L, "E002", "ORG-1", "COLLABORATOR");
        when(participantMapper.selectByWorklogId(anyLong())).thenReturn(List.of(collaborator));

        service.addLog("3", "uuid-first", "主执行人首次记录",
                "[\"/api/files/F-3/download#first.jpg\"]",
                LocalDateTime.of(2026, 8, 11, 12, 0), "VISIT",
                "[\"E002\"]", null, null, "E001", "ORG-1", false);

        TouchWorklogVO collaboratorLog = service.addLog("3", "uuid-second", "协同人员补充记录",
                "[\"/api/files/F-4/download#second.jpg\"]",
                LocalDateTime.of(2026, 8, 11, 13, 0), "PHONE",
                null, null, null, "E002", "ORG-1", false);

        assertThat(collaboratorLog).isNotNull();
        assertThat(saved).hasSize(2);
        verify(taskMapper).markInProgressIfPending(eq(3L), eq("E001"), any());
    }

    @Test
    void systemAdmin_cannotWriteLog_whenNotAssigneeOrRegisteredCollaborator() {
        TouchTask task = task("4", "SUCCESS", "E001", "ORG-1");
        givenWritableDependencies(task, 40L);

        assertThatThrownBy(() -> service.addLog(
                "4", "uuid-admin", "管理员代录",
                "[\"/api/files/F-4/download#admin.jpg\"]",
                LocalDateTime.of(2026, 8, 11, 14, 0), "PHONE",
                null, null, null, "ADMIN", "ORG-1", true))
                .isInstanceOf(com.bank.branch.platform.common.web.exception.BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TOUCH_TASK_ACCESS_FORBIDDEN.getCode());
    }

    @Test
    void assigneeFromAnotherOrg_cannotWriteTaskLog() {
        TouchTask task = task("41", "IN_PROGRESS", "E001", "ORG-1");
        givenWritableDependencies(task, 410L);

        assertThatThrownBy(() -> service.addLog(
                "41", "uuid-cross-org", "跨机构代录",
                "[\"/api/files/FILE-OBJECT-41/download#cross-org.jpg\"]",
                LocalDateTime.of(2026, 8, 11, 14, 30), "PHONE",
                null, null, null, "E001", "ORG-2", false))
                .isInstanceOf(com.bank.branch.platform.common.web.exception.BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TOUCH_TASK_ACCESS_FORBIDDEN.getCode());
    }

    @Test
    void platformDownloadUrl_storesFileObjectId_andReturnsCanonicalDownloadUrl() {
        TouchTask task = task("5", "IN_PROGRESS", "E001", "ORG-1");
        givenWritableDependencies(task, 50L);

        List<TouchWorklogPictureRecord> savedPictures = new ArrayList<>();
        when(pictureMapper.insert(any(TouchWorklogPictureRecord.class))).thenAnswer(invocation -> {
            TouchWorklogPictureRecord row = invocation.getArgument(0);
            row.setId(501L);
            savedPictures.add(row);
            return 1;
        });
        when(pictureMapper.selectActiveByWorklogId(anyLong())).thenAnswer(invocation -> savedPictures);

        TouchWorklogVO result = service.addLog(
                "5", "uuid-image", "图片引用测试",
                "[\"/api/files/FILE-OBJECT-5/download#现场照片.jpg\"]",
                LocalDateTime.of(2026, 8, 11, 15, 0), "VISIT",
                null, null, null, "E001", "ORG-1", false);

        ArgumentCaptor<TouchWorklogPictureRecord> picture =
                ArgumentCaptor.forClass(TouchWorklogPictureRecord.class);
        verify(pictureMapper).insert(picture.capture());
        assertThat(picture.getValue().getFileObjectId()).isEqualTo("FILE-OBJECT-5");
        assertThat(result.getPhotoUrls()).containsExactly("/api/files/FILE-OBJECT-5/download");
    }

    @Test
    void nonImageDownloadUrl_remainsRejected() {
        TouchTask task = task("6", "IN_PROGRESS", "E001", "ORG-1");
        givenWritableDependencies(task, 60L);

        assertThatThrownBy(() -> service.addLog(
                "6", "uuid-pdf", "非图片文件",
                "[\"/api/files/FILE-OBJECT-6/download#evidence.pdf\"]",
                LocalDateTime.of(2026, 8, 11, 16, 0), "PHONE",
                null, null, null, "E001", "ORG-1", false))
                .isInstanceOf(com.bank.branch.platform.common.web.exception.BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TOUCH_LOG_PHOTO_FORMAT_INVALID.getCode());
    }

    @Test
    void existingPhotoUrl_isKeptWhenBuildingResponse() {
        TouchTask task = task("7", "IN_PROGRESS", "E001", "ORG-1");
        givenWritableDependencies(task, 70L);

        List<TouchWorklogPictureRecord> savedPictures = new ArrayList<>();
        when(pictureMapper.insert(any(TouchWorklogPictureRecord.class))).thenAnswer(invocation -> {
            TouchWorklogPictureRecord row = invocation.getArgument(0);
            savedPictures.add(row);
            return 1;
        });
        when(pictureMapper.selectActiveByWorklogId(anyLong())).thenAnswer(invocation -> savedPictures);

        TouchWorklogVO result = service.addLog(
                "7", "uuid-existing-url", "已有 URL",
                "[\"https://cdn.example.com/photo.jpg?token=abc\"]",
                LocalDateTime.of(2026, 8, 11, 17, 0), "VISIT",
                null, null, null, "E001", "ORG-1", false);

        assertThat(savedPictures).singleElement().extracting(TouchWorklogPictureRecord::getFileObjectId)
                .isEqualTo("https://cdn.example.com/photo.jpg?token=abc");
        assertThat(result.getPhotoUrls()).containsExactly("https://cdn.example.com/photo.jpg?token=abc");
    }

    private void givenWritableDependencies(TouchTask task, long worklogId) {
        MarketingCustomerInfo customer = customer(task.getId());
        when(taskMapper.selectByIdForUpdate(String.valueOf(task.getId()))).thenReturn(task);
        when(worklogMapper.selectByTaskAndClientUuid(task.getId(), "uuid-success-append"))
                .thenReturn(null);
        when(worklogMapper.selectByTaskAndClientUuid(task.getId(), "uuid-collaborator-append"))
                .thenReturn(null);
        when(worklogMapper.selectByTaskAndClientUuid(task.getId(), "uuid-admin"))
                .thenReturn(null);
        when(worklogMapper.selectByTaskAndClientUuid(task.getId(), "uuid-image"))
                .thenReturn(null);
        when(worklogMapper.selectByTaskAndClientUuid(task.getId(), "uuid-pdf"))
                .thenReturn(null);
        when(customerMapper.selectActiveById(task.getCustId())).thenReturn(customer);
        when(worklogMapper.countValidByTaskId(task.getId())).thenReturn(1L);
        when(worklogMapper.insert(any(TouchWorklog.class))).thenAnswer(invocation -> {
            TouchWorklog row = invocation.getArgument(0);
            row.setId(worklogId);
            return 1;
        });
    }

    private TouchTask task(String id, String status, String assignee, String orgId) {
        TouchTask task = new TouchTask();
        task.setId(Long.valueOf(id));
        task.setCustId(Long.valueOf(id));
        task.setTaskStatus(status);
        task.setTaskType("FIRST_TOUCH");
        task.setAssigneeEmpId(assignee);
        task.setOrgId(orgId);
        task.setTaskNo("TOUCH-" + id);
        return task;
    }

    private MarketingCustomerInfo customer(long id) {
        MarketingCustomerInfo customer = new MarketingCustomerInfo();
        customer.setId(id);
        customer.setCustName("测试客户" + id);
        customer.setUnifiedCreditCode("9135000000000000" + String.format("%02d", id));
        return customer;
    }

    private TouchWorklog worklog(long id, long taskId, String operator) {
        TouchWorklog row = new TouchWorklog();
        row.setId(id);
        row.setTaskId(taskId);
        row.setOperatorEmpId(operator);
        row.setRecordStatus("VALID");
        return row;
    }

    private TouchWorklogParticipant participant(long id, long worklogId, String empId,
                                               String orgId, String role) {
        TouchWorklogParticipant row = new TouchWorklogParticipant();
        row.setId(id);
        row.setWorklogId(worklogId);
        row.setParticipantEmpId(empId);
        row.setParticipantOrgId(orgId);
        row.setParticipantRole(role);
        return row;
    }
}
