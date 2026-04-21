package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.api.dto.LeadDTO;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * LeadApiImpl 单元测试（TDD Red → Green）
 * 验证新 5 方法契约的委托调用路径正确。
 */
@ExtendWith(MockitoExtension.class)
class LeadApiImplTest {

    @Mock
    private CustLeadMapper leadMapper;

    @InjectMocks
    private LeadApiImpl leadApiImpl;

    // ===================== getLead =====================

    @Test
    void getLead_returnsEmptyWhenNotFound() {
        when(leadMapper.selectById("not-exist")).thenReturn(null);

        Optional<LeadDTO> result = leadApiImpl.getLead("not-exist");

        assertThat(result).isEmpty();
    }

    @Test
    void getLead_returnsMappedDTO() {
        CustLead e = new CustLead();
        e.setId("L1");
        e.setCustName("X");
        when(leadMapper.selectById("L1")).thenReturn(e);

        Optional<LeadDTO> dto = leadApiImpl.getLead("L1");

        assertThat(dto).isPresent();
        assertThat(dto.get().getId()).isEqualTo("L1");
        assertThat(dto.get().getCustName()).isEqualTo("X");
    }

    // ===================== getLeadByBusinessKey =====================

    @Test
    void getLeadByBusinessKey_singleLead() {
        CustLead e = new CustLead();
        e.setId("L1");
        e.setBusinessKey("LEAD:L1");
        when(leadMapper.selectByBusinessKey("LEAD:L1")).thenReturn(e);

        Optional<LeadDTO> dto = leadApiImpl.getLeadByBusinessKey("LEAD:L1");

        assertThat(dto).isPresent();
        assertThat(dto.get().getBusinessKey()).isEqualTo("LEAD:L1");
    }

    @Test
    void getLeadByBusinessKey_returnsEmpty() {
        when(leadMapper.selectByBusinessKey("LEAD:NONE")).thenReturn(null);

        Optional<LeadDTO> dto = leadApiImpl.getLeadByBusinessKey("LEAD:NONE");

        assertThat(dto).isEmpty();
    }

    // ===================== getLeadsByBatch =====================

    @Test
    void getLeadsByBatch_returnsAllInBatch() {
        CustLead a = new CustLead();
        a.setId("L1");
        CustLead b = new CustLead();
        b.setId("L2");
        when(leadMapper.selectByImportBatchId("B1")).thenReturn(List.of(a, b));

        List<LeadDTO> list = leadApiImpl.getLeadsByBatch("B1");

        assertThat(list).hasSize(2);
    }

    @Test
    void getLeadsByBatch_emptyResult() {
        when(leadMapper.selectByImportBatchId("B_NONE")).thenReturn(Collections.emptyList());

        List<LeadDTO> list = leadApiImpl.getLeadsByBatch("B_NONE");

        assertThat(list).isEmpty();
    }

    @Test
    void getLeadsByBatch_nullResultReturnsEmptyList() {
        when(leadMapper.selectByImportBatchId("B_NULL")).thenReturn(null);

        List<LeadDTO> list = leadApiImpl.getLeadsByBatch("B_NULL");

        assertThat(list).isEmpty();
    }

    // ===================== getLeadVersionChain =====================

    @Test
    void getLeadVersionChain_returnsEmptyForUnknown() {
        when(leadMapper.selectById("UNKNOWN")).thenReturn(null);

        List<LeadDTO> chain = leadApiImpl.getLeadVersionChain("UNKNOWN");

        assertThat(chain).isEmpty();
    }

    @Test
    void getLeadVersionChain_singleVersionWhenNoSourceCustId() {
        CustLead current = new CustLead();
        current.setId("L1");
        current.setVersionNo(1);
        current.setSourceCustId(null);
        when(leadMapper.selectById("L1")).thenReturn(current);

        List<LeadDTO> chain = leadApiImpl.getLeadVersionChain("L1");

        assertThat(chain).hasSize(1);
        assertThat(chain.get(0).getId()).isEqualTo("L1");
    }

    @Test
    void getLeadVersionChain_returnsFullChainOrderedByVersion() {
        CustLead current = new CustLead();
        current.setId("L2");
        current.setVersionNo(2);
        current.setSourceCustId("C1");
        when(leadMapper.selectById("L2")).thenReturn(current);

        CustLead v1 = new CustLead();
        v1.setId("L1");
        v1.setVersionNo(1);
        CustLead v2 = new CustLead();
        v2.setId("L2");
        v2.setVersionNo(2);
        when(leadMapper.selectBySourceCustIdOrderByVersion("C1")).thenReturn(List.of(v1, v2));

        List<LeadDTO> chain = leadApiImpl.getLeadVersionChain("L2");

        assertThat(chain).hasSize(2);
        assertThat(chain.get(0).getVersionNo()).isEqualTo(1);
        assertThat(chain.get(1).getVersionNo()).isEqualTo(2);
    }

    // ===================== isLeadCustNameAvailable =====================

    @Test
    void isLeadCustNameAvailable_trueWhenNoCollision() {
        when(leadMapper.countActiveByCustName("新客户", null)).thenReturn(0L);

        assertThat(leadApiImpl.isLeadCustNameAvailable("新客户", null)).isTrue();
    }

    @Test
    void isLeadCustNameAvailable_falseWhenCollision() {
        when(leadMapper.countActiveByCustName("已有客户", null)).thenReturn(1L);

        assertThat(leadApiImpl.isLeadCustNameAvailable("已有客户", null)).isFalse();
    }

    @Test
    void isLeadCustNameAvailable_excludesGivenLeadId() {
        when(leadMapper.countActiveByCustName("X", "L1")).thenReturn(0L);

        assertThat(leadApiImpl.isLeadCustNameAvailable("X", "L1")).isTrue();
    }

    @Test
    void isLeadCustNameAvailable_falseWhenBlankCustName() {
        assertThat(leadApiImpl.isLeadCustNameAvailable("", null)).isFalse();
        assertThat(leadApiImpl.isLeadCustNameAvailable("   ", null)).isFalse();
        assertThat(leadApiImpl.isLeadCustNameAvailable(null, null)).isFalse();
    }
}
