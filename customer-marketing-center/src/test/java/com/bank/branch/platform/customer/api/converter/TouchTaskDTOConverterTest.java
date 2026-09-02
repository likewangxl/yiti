package com.bank.branch.platform.customer.api.converter;

import com.bank.branch.platform.customer.api.dto.TouchTaskDTO;
import com.bank.branch.platform.customer.entity.TouchTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TouchTaskDTOConverter 单元测试（TDD Red 阶段）
 * 测试类先于实现类存在，用于驱动实现。
 */
class TouchTaskDTOConverterTest {

    // ==================== toDTO ====================

    @Test
    void toDTO_mapsAllScalarFields() {
        // given
        TouchTask entity = new TouchTask();
        entity.setId(1L);
        entity.setTaskNo("T20240001");
        entity.setCustId(101L);
        entity.setOrgId("ORG_001");
        entity.setAssigneeEmpId("EMP_001");
        entity.setTaskType("FIRST_TOUCH");
        entity.setTaskStatus("PENDING");
        entity.setSlaStatus("GREEN");
        LocalDateTime now = LocalDateTime.of(2024, 4, 1, 8, 0, 0);
        entity.setPlanFinishTime(now.plusDays(7));
        entity.setWarningTime(now.plusDays(5));
        entity.setSuccessTime(null);
        entity.setCancelTime(null);
        entity.setCreatedTime(now);
        entity.setUpdatedTime(now.plusHours(1));
        entity.setCustName("测试客户");
        entity.setLogCount(2L);
        entity.setParticipantEmpIds(List.of("EMP_001", "EMP_002"));

        // when
        TouchTaskDTO dto = TouchTaskDTOConverter.toDTO(entity);

        // then
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo("1");
        assertThat(dto.getCustId()).isEqualTo("101");
        assertThat(dto.getOrgId()).isEqualTo("ORG_001");
        assertThat(dto.getAssigneeEmpId()).isEqualTo("EMP_001");
        assertThat(dto.getTaskType()).isEqualTo("FIRST_TOUCH");
        assertThat(dto.getTaskStatus()).isEqualTo("PENDING");
        assertThat(dto.getBusinessKey()).isEqualTo("TOUCH:1");
        assertThat(dto.getWorklogId()).isNull();
        assertThat(dto.getExpectedFinishAt()).isEqualTo(now.plusDays(7));
        assertThat(dto.getCreatedAt()).isEqualTo(now);
        assertThat(dto.getCustName()).isEqualTo("测试客户");
        assertThat(dto.getAssigneeEmpName()).isNull();
        assertThat(dto.getLogCount()).isEqualTo(2);
        assertThat(dto.getParticipantEmpIds()).containsExactly("EMP_001", "EMP_002");
        // PENDING 状态 finishResult 为 null
        assertThat(dto.getFinishResult()).isNull();
    }

    @Test
    void toDTO_finishResultIsSuccessWhenTaskStatusSuccess() {
        // given
        TouchTask entity = new TouchTask();
        entity.setId(2L);
        entity.setCustId(102L);
        entity.setTaskStatus("SUCCESS");
        entity.setSuccessTime(LocalDateTime.of(2024, 4, 8, 16, 0, 0));

        // when
        TouchTaskDTO dto = TouchTaskDTOConverter.toDTO(entity);

        // then: SUCCESS 状态，finishResult 填入 taskStatus 值
        assertThat(dto.getFinishResult()).isEqualTo("SUCCESS");
        assertThat(dto.getActualFinishAt()).isEqualTo(LocalDateTime.of(2024, 4, 8, 16, 0, 0));
    }

    @Test
    void toDTO_finishResultIsCancelledWhenTaskStatusCancelled() {
        // given
        TouchTask entity = new TouchTask();
        entity.setId(3L);
        entity.setCustId(103L);
        entity.setTaskStatus("CANCELLED");
        entity.setCancelTime(LocalDateTime.of(2024, 4, 5, 12, 0, 0));

        // when
        TouchTaskDTO dto = TouchTaskDTOConverter.toDTO(entity);

        // then: CANCELLED 状态，finishResult 填入 taskStatus 值
        assertThat(dto.getFinishResult()).isEqualTo("CANCELLED");
    }

    @Test
    void toDTO_finishResultIsNullWhenTaskStatusPending() {
        // given: PENDING 状态不设 finishResult
        TouchTask entity = new TouchTask();
        entity.setId(4L);
        entity.setCustId(104L);
        entity.setTaskStatus("PENDING");

        // when
        TouchTaskDTO dto = TouchTaskDTOConverter.toDTO(entity);

        // then
        assertThat(dto.getFinishResult()).isNull();
    }

    @Test
    void toDTO_finishResultIsNullWhenTaskStatusIsNull() {
        // given
        TouchTask entity = new TouchTask();
        entity.setId(5L);
        entity.setCustId(105L);
        entity.setTaskStatus(null);

        // when
        TouchTaskDTO dto = TouchTaskDTOConverter.toDTO(entity);

        // then
        assertThat(dto.getFinishResult()).isNull();
    }

    @Test
    void toDTO_slaWarningMappedFromSlaStatus() {
        // given: YELLOW 表示触发预警
        TouchTask yellowEntity = new TouchTask();
        yellowEntity.setId(6L);
        yellowEntity.setSlaStatus("YELLOW");

        // given: RED 也表示触发预警（更严重）
        TouchTask redEntity = new TouchTask();
        redEntity.setId(7L);
        redEntity.setSlaStatus("RED");

        // given: GREEN 不触发预警
        TouchTask greenEntity = new TouchTask();
        greenEntity.setId(8L);
        greenEntity.setSlaStatus("GREEN");

        // when + then
        assertThat(TouchTaskDTOConverter.toDTO(yellowEntity).getSlaWarning()).isTrue();
        assertThat(TouchTaskDTOConverter.toDTO(redEntity).getSlaWarning()).isTrue();
        assertThat(TouchTaskDTOConverter.toDTO(greenEntity).getSlaWarning()).isFalse();
    }

    @Test
    void toDTO_slaDeadlineMappedFromPlanFinishTime() {
        // given
        TouchTask entity = new TouchTask();
        entity.setId(9L);
        LocalDateTime deadline = LocalDateTime.of(2024, 5, 1, 23, 59, 59);
        entity.setPlanFinishTime(deadline);

        // when
        TouchTaskDTO dto = TouchTaskDTOConverter.toDTO(entity);

        // then: slaDeadline 来自 planFinishTime
        assertThat(dto.getSlaDeadline()).isEqualTo(deadline);
    }

    @Test
    void toDTO_returnsNullForNullEntity() {
        assertThat(TouchTaskDTOConverter.toDTO(null)).isNull();
    }

    @Test
    void toDTOList_mapsEachAndFiltersNulls() {
        // given
        TouchTask e1 = new TouchTask();
        e1.setId(1L);
        e1.setTaskStatus("SUCCESS");
        e1.setSuccessTime(LocalDateTime.now());

        TouchTask e2 = new TouchTask();
        e2.setId(2L);
        e2.setTaskStatus("PENDING");

        List<TouchTask> list = Arrays.asList(e1, null, e2);

        // when
        List<TouchTaskDTO> dtos = TouchTaskDTOConverter.toDTOList(list);

        // then
        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getId()).isEqualTo("1");
        assertThat(dtos.get(0).getFinishResult()).isEqualTo("SUCCESS");
        assertThat(dtos.get(1).getId()).isEqualTo("2");
        assertThat(dtos.get(1).getFinishResult()).isNull();
    }

    @Test
    void toDTOList_returnsEmptyForNullInput() {
        assertThat(TouchTaskDTOConverter.toDTOList(null)).isEmpty();
    }

    @Test
    void toDTOList_returnsEmptyForEmptyList() {
        assertThat(TouchTaskDTOConverter.toDTOList(Collections.emptyList())).isEmpty();
    }
}
