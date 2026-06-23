package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeSaveReqDTO;
import com.bank.branch.platform.portal.entity.ZhGuaranteeInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.mapper.ZhGuaranteeInfoMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GuaranteeService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class GuaranteeServiceTest {

    @Mock
    private ZhGuaranteeInfoMapper guaranteeMapper;

    @InjectMocks
    private GuaranteeService guaranteeService;

    private GuaranteeSaveReqDTO sampleReq() {
        GuaranteeSaveReqDTO req = new GuaranteeSaveReqDTO();
        req.setClientName("某某公司");
        req.setAmountManage("1000");
        req.setUsableExposureSum("300");
        req.setExposureAmount("700");
        req.setLastExpire("2027-12-31");
        req.setOperator("finance_zhou");
        return req;
    }

    @Test
    void create_setsFieldsCreatorAndCreateTime_returnsGeneratedId() {
        // insert 时模拟 MyBatis-Plus 回填自增主键
        doAnswer(inv -> {
            ZhGuaranteeInfo e = inv.getArgument(0);
            e.setId(99L);
            return 1;
        }).when(guaranteeMapper).insert(any(ZhGuaranteeInfo.class));

        Long id = guaranteeService.create(sampleReq(), "admin");

        assertThat(id).isEqualTo(99L);
        ArgumentCaptor<ZhGuaranteeInfo> captor = ArgumentCaptor.forClass(ZhGuaranteeInfo.class);
        verify(guaranteeMapper).insert(captor.capture());
        ZhGuaranteeInfo saved = captor.getValue();
        assertThat(saved.getClientName()).isEqualTo("某某公司");
        assertThat(saved.getAmountManage()).isEqualTo("1000");
        assertThat(saved.getUsableExposureSum()).isEqualTo("300");
        assertThat(saved.getExposureAmount()).isEqualTo("700");
        assertThat(saved.getLastExpire()).isEqualTo("2027-12-31");
        assertThat(saved.getOperator()).isEqualTo("finance_zhou");
        assertThat(saved.getCreateUser()).isEqualTo("admin");
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getType()).isEqualTo("1");
    }

    @Test
    void update_existing_appliesFieldsAndStampsUpdateTime() {
        ZhGuaranteeInfo existing = new ZhGuaranteeInfo();
        existing.setId(5L);
        existing.setClientName("旧名");
        when(guaranteeMapper.selectById(5L)).thenReturn(existing);

        guaranteeService.update(5L, sampleReq(), "admin");

        ArgumentCaptor<ZhGuaranteeInfo> captor = ArgumentCaptor.forClass(ZhGuaranteeInfo.class);
        verify(guaranteeMapper).updateById(captor.capture());
        ZhGuaranteeInfo saved = captor.getValue();
        assertThat(saved.getClientName()).isEqualTo("某某公司");
        assertThat(saved.getUpdateTime()).isNotBlank();
    }

    @Test
    void update_notFound_throwsGuaranteeNotFound() {
        when(guaranteeMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> guaranteeService.update(404L, sampleReq(), "admin"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", PortalErrorCode.GUARANTEE_NOT_FOUND.getCode());
    }

    @Test
    void getById_notFound_throwsGuaranteeNotFound() {
        when(guaranteeMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> guaranteeService.getById(404L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", PortalErrorCode.GUARANTEE_NOT_FOUND.getCode());
    }

    @Test
    void batchDelete_delegatesToMapper() {
        when(guaranteeMapper.deleteBatchIds(List.of(1L, 2L))).thenReturn(2);

        int deleted = guaranteeService.batchDelete(List.of(1L, 2L));

        assertThat(deleted).isEqualTo(2);
        verify(guaranteeMapper).deleteBatchIds(List.of(1L, 2L));
    }

    @Test
    void batchDelete_emptyIds_shortCircuitsWithoutMapperCall() {
        assertThat(guaranteeService.batchDelete(List.of())).isZero();
        verify(guaranteeMapper, org.mockito.Mockito.never()).deleteBatchIds(any());
    }

    @Test
    void listGuarantees_returnsMapperPage() {
        GuaranteeQueryReqDTO req = new GuaranteeQueryReqDTO();
        req.setClientName("公司");
        IPage<ZhGuaranteeInfo> page = new Page<>(1, 20);
        page.setRecords(List.of(new ZhGuaranteeInfo()));
        when(guaranteeMapper.selectPage(any(), any())).thenReturn(page);

        IPage<ZhGuaranteeInfo> result = guaranteeService.listGuarantees(req);

        assertThat(result.getRecords()).hasSize(1);
    }

    // 引入 SFunction 仅为确保 lambda 列引用编译期可用（防止裸 lambda 推断歧义）
    @SuppressWarnings("unused")
    private static final SFunction<ZhGuaranteeInfo, ?> COLUMN_REF = ZhGuaranteeInfo::getClientName;
}
