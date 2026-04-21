package com.bank.branch.platform.bizapp.facade;

import com.bank.branch.platform.bizapp.api.converter.SupportRequestDTOConverter;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * {@link SupportApiImpl} 单元测试。
 * <p>
 * 测试重点：facade 正确委托 mapper 和 converter；
 * converter 的内部逻辑（冗余字段填充、deleted 移除）由 SupportRequestDTOConverterTest 单独覆盖。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SupportApiImplTest {

    @Mock
    private SupportRequestMapper supportRequestMapper;

    @Mock
    private SupportRequestDTOConverter supportRequestDTOConverter;

    @InjectMocks
    private SupportApiImpl supportApiImpl;

    // ------------------------------------------------------------------
    // 测试辅助方法
    // ------------------------------------------------------------------

    /** 构造一个完整的 SupportRequest 实体用于测试 */
    private SupportRequest buildSupportRequest(String id) {
        SupportRequest entity = new SupportRequest();
        entity.setId(id);
        entity.setRequestNo("SR202401010001");
        entity.setSubmitGroupId("GROUP001");
        entity.setCustId("CUST001");
        entity.setSourceTouchTaskId("TASK001");
        entity.setProductId("PROD001");
        entity.setSupportDeptId("DEPT001");
        entity.setOtherDemand("补充说明");
        entity.setDispatchEmpId(null);
        entity.setDispatchTime(null);
        entity.setAssignedEmpId("EMP002");
        entity.setStatus("COMPLETED");
        entity.setBusinessKey("SUPPORT:" + id);
        entity.setProcessInstanceId("PROC002");
        entity.setOwnerOrgId("ORG001");
        entity.setCreatedBy("EMP001");
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedBy("EMP001");
        entity.setUpdatedTime(LocalDateTime.now());
        entity.setDeleted(0);
        return entity;
    }

    /** 构造测试用 DTO */
    private SupportRequestDTO buildDTO(String id) {
        SupportRequestDTO dto = new SupportRequestDTO();
        dto.setId(id);
        dto.setRequestNo("SR202401010001");
        dto.setSubmitGroupId("GROUP001");
        dto.setCustId("CUST001");
        dto.setCustName("腾讯云计算");
        dto.setProductId("PROD001");
        dto.setProductName("数字化专项贷");
        dto.setSupportDeptId("DEPT001");
        dto.setSupportDeptName("公司金融部");
        dto.setStatus("COMPLETED");
        return dto;
    }

    // ------------------------------------------------------------------
    // 测试用例
    // ------------------------------------------------------------------

    @Test
    void getSupportRequest_exists_shouldReturnPresent() {
        // Arrange
        String requestId = "req001";
        SupportRequest entity = buildSupportRequest(requestId);
        SupportRequestDTO dto = buildDTO(requestId);
        when(supportRequestMapper.selectById(requestId)).thenReturn(entity);
        when(supportRequestDTOConverter.toDTO(entity)).thenReturn(dto);

        // Act
        Optional<SupportRequestDTO> result = supportApiImpl.getSupportRequest(requestId);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(requestId);
        assertThat(result.get().getRequestNo()).isEqualTo("SR202401010001");
        assertThat(result.get().getCustId()).isEqualTo("CUST001");
        assertThat(result.get().getCustName()).isEqualTo("腾讯云计算");
        assertThat(result.get().getProductName()).isEqualTo("数字化专项贷");
        assertThat(result.get().getSupportDeptName()).isEqualTo("公司金融部");
        assertThat(result.get().getStatus()).isEqualTo("COMPLETED");
        assertThat(result.get().getSubmitGroupId()).isEqualTo("GROUP001");
    }

    @Test
    void getSupportRequest_notExists_shouldReturnEmpty() {
        // Arrange
        when(supportRequestMapper.selectById("notExist")).thenReturn(null);

        // Act
        Optional<SupportRequestDTO> result = supportApiImpl.getSupportRequest("notExist");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void getBySubmitGroup_shouldReturnGroupedList() {
        // Arrange
        String groupId = "GROUP001";
        SupportRequest e1 = buildSupportRequest("req001");
        SupportRequest e2 = buildSupportRequest("req002");
        e2.setProductId("PROD002");
        SupportRequestDTO d1 = buildDTO("req001");
        SupportRequestDTO d2 = buildDTO("req002");
        d2.setProductId("PROD002");
        when(supportRequestMapper.selectBySubmitGroupId(groupId)).thenReturn(List.of(e1, e2));
        when(supportRequestDTOConverter.toDTOList(List.of(e1, e2))).thenReturn(List.of(d1, d2));

        // Act
        List<SupportRequestDTO> result = supportApiImpl.getBySubmitGroup(groupId);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(SupportRequestDTO::getSubmitGroupId)
                .containsOnly("GROUP001");
    }

    @Test
    @DisplayName("getSupportRequestBatch 入参为 null 应返回空列表")
    void getSupportRequestBatch_null_shouldReturnEmpty() {
        // Act
        List<SupportRequestDTO> result = supportApiImpl.getSupportRequestBatch(null);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getSupportRequestBatch 入参为空列表应返回空列表")
    void getSupportRequestBatch_empty_shouldReturnEmpty() {
        // Act
        List<SupportRequestDTO> result = supportApiImpl.getSupportRequestBatch(Collections.emptyList());

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getSupportRequestBatch 入参超过 500 条应抛 IllegalArgumentException")
    void getSupportRequestBatch_exceeds500_throws() {
        List<String> ids = Stream.generate(() -> UUID.randomUUID().toString())
                .limit(501).toList();
        assertThatThrownBy(() -> supportApiImpl.getSupportRequestBatch(ids))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("getSupportRequestBatch 入参恰好 500 条应正常返回")
    void getSupportRequestBatch_exactly500_succeeds() {
        List<String> ids = Stream.generate(() -> UUID.randomUUID().toString())
                .limit(500).toList();
        // mock mapper/converter 返回空列表，避免执行真实查询
        when(supportRequestMapper.selectByIds(anyList())).thenReturn(Collections.emptyList());

        assertThatCode(() -> supportApiImpl.getSupportRequestBatch(ids))
                .doesNotThrowAnyException();
    }
}
