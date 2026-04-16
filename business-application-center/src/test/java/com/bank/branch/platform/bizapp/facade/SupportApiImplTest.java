package com.bank.branch.platform.bizapp.facade;

import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link SupportApiImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class SupportApiImplTest {

    @Mock
    private SupportRequestMapper supportRequestMapper;

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

    // ------------------------------------------------------------------
    // 测试用例
    // ------------------------------------------------------------------

    @Test
    void getSupportRequest_exists_shouldReturnPresent() {
        // Arrange
        String requestId = "req001";
        SupportRequest entity = buildSupportRequest(requestId);
        when(supportRequestMapper.selectById(requestId)).thenReturn(entity);

        // Act
        Optional<SupportRequestDTO> result = supportApiImpl.getSupportRequest(requestId);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(requestId);
        assertThat(result.get().getRequestNo()).isEqualTo("SR202401010001");
        assertThat(result.get().getCustId()).isEqualTo("CUST001");
        assertThat(result.get().getStatus()).isEqualTo("COMPLETED");
        assertThat(result.get().getSubmitGroupId()).isEqualTo("GROUP001");
    }

    @Test
    void getBySubmitGroup_shouldReturnGroupedList() {
        // Arrange
        String groupId = "GROUP001";
        SupportRequest e1 = buildSupportRequest("req001");
        SupportRequest e2 = buildSupportRequest("req002");
        e2.setProductId("PROD002");
        when(supportRequestMapper.selectBySubmitGroupId(groupId)).thenReturn(List.of(e1, e2));

        // Act
        List<SupportRequestDTO> result = supportApiImpl.getBySubmitGroup(groupId);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(SupportRequestDTO::getSubmitGroupId)
                .containsOnly("GROUP001");
        assertThat(result.get(1).getProductId()).isEqualTo("PROD002");
    }
}
