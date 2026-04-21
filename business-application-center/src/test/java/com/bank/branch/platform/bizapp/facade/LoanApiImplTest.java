package com.bank.branch.platform.bizapp.facade;

import com.bank.branch.platform.bizapp.api.converter.LoanApplyDTOConverter;
import com.bank.branch.platform.bizapp.api.dto.LoanApplyDTO;
import com.bank.branch.platform.bizapp.entity.LoanApply;
import com.bank.branch.platform.bizapp.mapper.LoanApplyMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * {@link LoanApiImpl} 单元测试。
 * <p>
 * 测试重点：facade 正确委托 mapper 和 converter；
 * converter 的内部逻辑（custName 填充、deleted 移除）由 LoanApplyDTOConverterTest 单独覆盖。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class LoanApiImplTest {

    @Mock
    private LoanApplyMapper loanApplyMapper;

    @Mock
    private LoanApplyDTOConverter loanApplyDTOConverter;

    @InjectMocks
    private LoanApiImpl loanApiImpl;

    // ------------------------------------------------------------------
    // 测试辅助方法
    // ------------------------------------------------------------------

    /** 构造一个完整的 LoanApply 实体用于测试 */
    private LoanApply buildLoanApply(String id) {
        LoanApply entity = new LoanApply();
        entity.setId(id);
        entity.setApplyNo("LA202401010001");
        entity.setCustId("CUST001");
        entity.setSourceTouchTaskId("TASK001");
        entity.setProjectType("TYPE_A");
        entity.setBizType("BIZ_CORP");
        entity.setGuaranteeType("GUARANTEE_PLEDGE");
        entity.setCreditAmount(new BigDecimal("500000.0000"));
        entity.setCreditExposureAmount(new BigDecimal("300000.0000"));
        entity.setStatus("COMPLETED");
        entity.setBusinessKey("LOAN:" + id);
        entity.setProcessInstanceId("PROC001");
        entity.setOwnerOrgId("ORG001");
        entity.setCreatedBy("EMP001");
        entity.setCreatedTime(LocalDateTime.now());
        entity.setUpdatedBy("EMP001");
        entity.setUpdatedTime(LocalDateTime.now());
        entity.setDeleted(0);
        return entity;
    }

    /** 构造一个测试用 DTO */
    private LoanApplyDTO buildDTO(String id) {
        LoanApplyDTO dto = new LoanApplyDTO();
        dto.setId(id);
        dto.setApplyNo("LA202401010001");
        dto.setCustId("CUST001");
        dto.setCustName("腾讯云计算");
        dto.setCreditAmount(new BigDecimal("500000.0000"));
        dto.setStatus("COMPLETED");
        return dto;
    }

    // ------------------------------------------------------------------
    // 测试用例
    // ------------------------------------------------------------------

    @Test
    void getLoanApply_exists_shouldReturnPresent() {
        // Arrange
        String applyId = "abc123";
        LoanApply entity = buildLoanApply(applyId);
        LoanApplyDTO dto = buildDTO(applyId);
        when(loanApplyMapper.selectById(applyId)).thenReturn(entity);
        when(loanApplyDTOConverter.toDTO(entity)).thenReturn(dto);

        // Act
        Optional<LoanApplyDTO> result = loanApiImpl.getLoanApply(applyId);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(applyId);
        assertThat(result.get().getApplyNo()).isEqualTo("LA202401010001");
        assertThat(result.get().getCustId()).isEqualTo("CUST001");
        assertThat(result.get().getCustName()).isEqualTo("腾讯云计算");
        assertThat(result.get().getCreditAmount()).isEqualByComparingTo("500000.0000");
        assertThat(result.get().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void getLoanApply_notExists_shouldReturnEmpty() {
        // Arrange
        String applyId = "notExist";
        when(loanApplyMapper.selectById(applyId)).thenReturn(null);

        // Act
        Optional<LoanApplyDTO> result = loanApiImpl.getLoanApply(applyId);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void getCustomerLoanHistory_shouldReturnList() {
        // Arrange
        String custId = "CUST001";
        LoanApply e1 = buildLoanApply("id1");
        LoanApply e2 = buildLoanApply("id2");
        LoanApplyDTO d1 = buildDTO("id1");
        LoanApplyDTO d2 = buildDTO("id2");
        when(loanApplyMapper.selectByCustId(custId)).thenReturn(List.of(e1, e2));
        when(loanApplyDTOConverter.toDTOList(List.of(e1, e2))).thenReturn(List.of(d1, d2));

        // Act
        List<LoanApplyDTO> result = loanApiImpl.getCustomerLoanHistory(custId);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("id1");
        assertThat(result.get(1).getId()).isEqualTo("id2");
    }

    @Test
    void getLoanApplyBatch_shouldReturnList() {
        // Arrange
        List<String> ids = List.of("id1", "id2");
        LoanApply e1 = buildLoanApply("id1");
        LoanApply e2 = buildLoanApply("id2");
        LoanApplyDTO d1 = buildDTO("id1");
        LoanApplyDTO d2 = buildDTO("id2");
        when(loanApplyMapper.selectByIds(ids)).thenReturn(List.of(e1, e2));
        when(loanApplyDTOConverter.toDTOList(List.of(e1, e2))).thenReturn(List.of(d1, d2));

        // Act
        List<LoanApplyDTO> result = loanApiImpl.getLoanApplyBatch(ids);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(LoanApplyDTO::getId).containsExactlyInAnyOrder("id1", "id2");
    }
}
