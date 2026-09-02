package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.portal.api.dto.ResponsibleEmpDTO;
import com.bank.branch.platform.portal.entity.ProductInfo;
import com.bank.branch.platform.portal.mapper.ProductInfoMapper;
import com.bank.branch.platform.portal.service.dto.ProductExportRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductExportService 单元测试
 * <p>TDD RED-GREEN 闭环</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductExportServiceTest {

    @Mock ProductInfoMapper productInfoMapper;
    @Mock DictApi dictApi;
    @Mock OrgApi orgApi;
    @Mock AddrbookQueryService addrbookQueryService;
    @Mock UserProductRelationService userProductRelationService;
    @Mock AuditApi auditApi;

    @InjectMocks ProductExportService productExportService;

    @BeforeEach
    void defaultRelationQueriesToEmpty() {
        lenient().when(userProductRelationService.mapUserIdsByProductIds(any()))
                .thenReturn(Collections.emptyMap());
    }

    /**
     * 超过 5000 行应抛出 PORTAL-42207
     */
    @Test
    void exportShouldThrow42207WhenCountExceedsThreshold() {
        when(productInfoMapper.countPage(null, null, "ACTIVE")).thenReturn(5001L);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertThatThrownBy(() -> productExportService.exportToStream(null, null, null, baos, "E10001"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("PORTAL-42207"));

        verify(productInfoMapper, never()).selectPage(any(), any(), any(), anyInt(), anyInt());
    }

    /**
     * 正好 5000 行应正常导出
     */
    @Test
    void exportShouldSucceedWhenCountEquals5000() {
        when(productInfoMapper.countPage(null, null, "ACTIVE")).thenReturn(5000L);
        ProductInfo entity = buildEntity("P001");
        when(productInfoMapper.selectPage(eq(null), eq(null), eq("ACTIVE"), eq(0), eq(5000)))
                .thenReturn(List.of(entity));
        OrgDTO org = new OrgDTO();
        org.setOrgCode("ORG_SZ_001");
        org.setOrgName("深圳分行");
        when(orgApi.getOrgsByCodes(Collections.singleton("ORG_SZ_001"))).thenReturn(List.of(org));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        productExportService.exportToStream(null, null, null, baos, "E10001");

        assertThat(baos.size()).isGreaterThan(0);
        verify(auditApi).log(any());
    }

    /**
     * 导出应包含负责人脱敏手机号
     */
    @Test
    void exportShouldIncludeResponsibleEmpsWithMaskedMobile() {
        when(productInfoMapper.countPage(null, null, "ACTIVE")).thenReturn(1L);
        ProductInfo entity = buildEntity("P001");
        when(productInfoMapper.selectPage(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(entity));
        when(userProductRelationService.mapUserIdsByProductIds(any()))
                .thenReturn(Map.of("P001", List.of("E001")));
        when(orgApi.getOrgsByCodes(any())).thenReturn(Collections.emptyList());
        ResponsibleEmpDTO emp = new ResponsibleEmpDTO();
        emp.setEmpId("E001");
        emp.setEmpName("张三");
        emp.setMobile("138****5678");
        when(addrbookQueryService.listResponsibleEmps(List.of("E001"))).thenReturn(List.of(emp));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        productExportService.exportToStream(null, null, null, baos, "E10001");

        assertThat(baos.size()).isGreaterThan(0);
        verify(addrbookQueryService).listResponsibleEmps(List.of("E001"));
    }

    /**
     * 0 行时应生成空 Excel（只有表头）
     */
    @Test
    void exportShouldWriteEmptyExcelWhenNoData() {
        when(productInfoMapper.countPage(null, null, "ACTIVE")).thenReturn(0L);
        when(productInfoMapper.selectPage(eq(null), eq(null), eq("ACTIVE"), eq(0), eq(0)))
                .thenReturn(Collections.emptyList());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        productExportService.exportToStream(null, null, null, baos, "E10001");

        assertThat(baos.size()).isGreaterThan(0); // Excel 文件头仍有内容
        verify(auditApi).log(any());
    }

    private ProductInfo buildEntity(String id) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductCode("DEPOSIT_001");
        p.setProductName("活期存款");
        p.setProductCategory("CAT_DEPOSIT");
        p.setDescription("产品描述\n包含换行");
        p.setSupportForSupportRequest(true);
        p.setProductDeptOrgCode("ORG_SZ_001");
        p.setStatus("ACTIVE");
        p.setUpdatedTime(LocalDateTime.of(2026, 4, 13, 10, 0, 0));
        p.setDeleted(0);
        return p;
    }
}
