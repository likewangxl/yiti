package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeCreateResultDTO;
import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeQueryReqDTO;
import com.bank.branch.platform.portal.controller.dto.guarantee.GuaranteeSaveReqDTO;
import com.bank.branch.platform.portal.entity.CcmsBusinessContract;
import com.bank.branch.platform.portal.entity.ZhGuaranteeInfo;
import com.bank.branch.platform.portal.enums.PortalErrorCode;
import com.bank.branch.platform.portal.mapper.CcmsBusinessContractMapper;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GuaranteeService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class GuaranteeServiceTest {

    @Mock
    private ZhGuaranteeInfoMapper guaranteeMapper;

    @Mock
    private CcmsBusinessContractMapper contractMapper;

    @InjectMocks
    private GuaranteeService guaranteeService;

    private GuaranteeSaveReqDTO sampleReq() {
        GuaranteeSaveReqDTO req = new GuaranteeSaveReqDTO();
        req.setClientNo("C0001");
        req.setClientName("某某公司");
        // 故意带货币符号与千分位，验证落库前被清洗（front_insert_replace.jpg 口径）
        req.setNotionalAmount("¥1,000.00");
        req.setOccupyNotionalAmount("300");
        req.setUsableNominalSum("700");
        req.setLastExpire("2027-12-31");
        req.setUserName("finance_zhou");
        return req;
    }

    /** 模拟客户在担保表不存在（重复校验通过）。 */
    private void mockClientNotExists() {
        when(guaranteeMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    void create_clientExists_throwsClientExists() {
        when(guaranteeMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> guaranteeService.create(sampleReq(), "admin"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", PortalErrorCode.GUARANTEE_CLIENT_EXISTS.getCode());

        // 已存在直接拒绝，不查合同、不落库
        verify(contractMapper, never()).selectList(any());
        verify(guaranteeMapper, never()).insert(any(ZhGuaranteeInfo.class));
        verify(guaranteeMapper, never()).batchInsert(any());
    }

    @Test
    void create_noContract_insertsManualRecord_returnsGeneratedId() {
        mockClientNotExists();
        // 合同表无记录
        when(contractMapper.selectList(any())).thenReturn(List.of());
        // insert 时模拟 MyBatis-Plus 回填自增主键
        doAnswer(inv -> {
            ZhGuaranteeInfo e = inv.getArgument(0);
            e.setId(99L);
            return 1;
        }).when(guaranteeMapper).insert(any(ZhGuaranteeInfo.class));

        GuaranteeCreateResultDTO result = guaranteeService.create(sampleReq(), "admin");

        assertThat(result.getSource()).isEqualTo("MANUAL");
        assertThat(result.getCount()).isEqualTo(1);
        assertThat(result.getId()).isEqualTo(99L);
        verify(guaranteeMapper, never()).batchInsert(any());
        ArgumentCaptor<ZhGuaranteeInfo> captor = ArgumentCaptor.forClass(ZhGuaranteeInfo.class);
        verify(guaranteeMapper).insert(captor.capture());
        ZhGuaranteeInfo saved = captor.getValue();
        assertThat(saved.getClientNo()).isEqualTo("C0001");
        assertThat(saved.getClientName()).isEqualTo("某某公司");
        // 金额按「万元」录入：先清洗 ¥ 与千分位 ，再 ×10000 转「元」落库
        assertThat(saved.getNotionalAmount()).isEqualTo("10000000.00");
        assertThat(saved.getOccupyNotionalAmount()).isEqualTo("3000000.00");
        assertThat(saved.getUsableNominalSum()).isEqualTo("7000000.00");
        assertThat(saved.getLastExpire()).isEqualTo("2027-12-31");
        assertThat(saved.getUserName()).isEqualTo("finance_zhou");
        assertThat(saved.getCreateUser()).isEqualTo("admin");
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getType()).isEqualTo("1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void create_contractHit_batchInsertsMappedRows_ignoresFormData() {
        mockClientNotExists();
        when(contractMapper.selectList(any())).thenReturn(List.of(contract("LINE-A"), contract("LINE-B")));
        when(guaranteeMapper.batchInsert(any())).thenReturn(2);

        GuaranteeCreateResultDTO result = guaranteeService.create(sampleReq(), "admin");

        assertThat(result.getSource()).isEqualTo("CONTRACT");
        assertThat(result.getCount()).isEqualTo(2);
        assertThat(result.getId()).isNull();
        // 命中合同：走批量插入，绝不落前端单条
        verify(guaranteeMapper, never()).insert(any(ZhGuaranteeInfo.class));
        ArgumentCaptor<List<ZhGuaranteeInfo>> captor = ArgumentCaptor.forClass(List.class);
        verify(guaranteeMapper).batchInsert(captor.capture());
        List<ZhGuaranteeInfo> rows = captor.getValue();
        assertThat(rows).hasSize(2);
        ZhGuaranteeInfo r = rows.get(0);
        assertThat(r.getClientNo()).isEqualTo("C0001");
        assertThat(r.getClientName()).isEqualTo("某某公司");
        assertThat(r.getAmountType()).isEqualTo("LINE-A");
        // 合同金额按「元」原值落库（不做 ×10000），尾零去除
        assertThat(r.getNotionalAmount()).isEqualTo("8888");
        assertThat(r.getUsableNominalSum()).isEqualTo("3000");
        assertThat(r.getOccupyExposureAmount()).isEqualTo("1500");
        assertThat(r.getExpired()).isEqualTo("2030-01-01");
        assertThat(r.getStart()).isEqualTo("2029-05-01");        // 额度生效日 → start
        assertThat(r.getBasicId()).isNull();                     // 合同表无 basic_id，恒 null
        assertThat(r.getLastExpire()).isEqualTo("2031-06-30");
        assertThat(r.getOrgan()).isEqualTo("ORG88");
        assertThat(r.getUserName()).isEqualTo("op_li");
        assertThat(r.getCreateUser()).isEqualTo("admin");
        assertThat(r.getCreateTime()).isNotNull();
        assertThat(r.getType()).isEqualTo("1");
    }

    private CcmsBusinessContract contract(String creditType) {
        CcmsBusinessContract c = new CcmsBusinessContract();
        c.setCustomerId("C0001");
        c.setCustomerName("某某公司");
        c.setCreditTypeFlag(creditType);
        c.setBusinessSum2(new BigDecimal("8888.000000"));
        c.setUsableNominalSum(new BigDecimal("3000.000000"));
        c.setExposureBalance(new BigDecimal("1500.000000"));
        c.setMaturity("2030-01-01");
        c.setPutoutDate("2029-05-01");
        c.setTermDate3("2031-06-30");
        c.setOperateOrgId("ORG88");
        c.setOperateUserId("op_li");
        return c;
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
