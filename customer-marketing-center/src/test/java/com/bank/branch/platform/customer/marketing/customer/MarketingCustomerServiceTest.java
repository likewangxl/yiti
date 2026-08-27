package com.bank.branch.platform.customer.marketing.customer;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerProfileUpdateRequest;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerQuery;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerVO;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTag;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTagRel;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagRelMapper;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerService;
import com.bank.branch.platform.customer.service.marketing.MarketingCustomerTagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingCustomerServiceTest {

    @Mock
    MarketingCustomerInfoMapper customerMapper;
    @Mock
    UserApi userApi;
    @Mock
    OrgApi orgApi;
    @Mock
    BizScopeApi bizScopeApi;
    @Mock
    MarketingCustomerTagRelMapper relationMapper;
    @Mock
    MarketingCustomerTagMapper tagMapper;
    @Mock
    MarketingCustomerTagService tagService;

    @Test
    void listMine_shouldAlwaysConstrainToCurrentMainManager() {
        MarketingCustomerQuery query = new MarketingCustomerQuery();
        query.setKeyword("测试");
        MarketingCustomerInfo customer = customer(1L, "E10001", "ORG001");
        when(customerMapper.selectPage(any(), eq(DataScopeType.SELF.getCode()), any(), eq("E10001"),
                eq(0), eq(20))).thenReturn(List.of(customer));
        when(customerMapper.countPage(any(), eq(DataScopeType.SELF.getCode()), any(), eq("E10001")))
                .thenReturn(1L);

        MarketingCustomerService service = service();
        PageResult<MarketingCustomerVO> result = service.listMine(query, "E10001", "ORG001");

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(1L);
            assertThat(item.getMainManagerId()).isEqualTo("E10001");
        });
        ArgumentCaptor<MarketingCustomerQuery> captor = ArgumentCaptor.forClass(MarketingCustomerQuery.class);
        verify(customerMapper).selectPage(captor.capture(), eq(DataScopeType.SELF.getCode()), any(), eq("E10001"),
                eq(0), eq(20));
        assertThat(captor.getValue().getOwnershipStatus()).isEqualTo("ASSIGNED");
        assertThat(captor.getValue().getRecordStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void listAll_shouldReturnUnassignedCustomerWhenManagerAndOrgAreMissing() {
        MarketingCustomerInfo customer = customer(5L, null, null);
        customer.setOwnershipStatus("UNASSIGNED");
        when(customerMapper.selectPage(any(), eq(DataScopeType.ALL.getCode()), any(), isNull(),
                eq(0), eq(20))).thenReturn(List.of(customer));
        when(customerMapper.countPage(any(), eq(DataScopeType.ALL.getCode()), any(), isNull()))
                .thenReturn(1L);

        PageResult<MarketingCustomerVO> result = service().listAll(new MarketingCustomerQuery(),
                "ADMIN", "ORG001", DataScopeType.ALL);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getOwnershipStatus()).isEqualTo("UNASSIGNED");
            assertThat(item.getMainManagerId()).isNull();
            assertThat(item.getMainManagerName()).isNull();
            assertThat(item.getMainOrgId()).isNull();
            assertThat(item.getMainOrgName()).isNull();
        });
    }

    @Test
    void getDetail_shouldRejectCustomerOutsideScope() {
        MarketingCustomerInfo customer = customer(2L, "OTHER", "ORG002");
        when(customerMapper.selectActiveById(2L)).thenReturn(customer);
        when(orgApi.getOrgSubtreeCodes("ORG001")).thenReturn(Set.of("ORG001"));

        MarketingCustomerService service = service();

        assertThatThrownBy(() -> service.getDetail(2L, "E10001", "ORG001",
                DataScopeType.ORG_SUBTREE, false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void updateProfile_shouldUseProfileAndLockVersionCasAndOnlyPersistAllowedFields() {
        MarketingCustomerInfo customer = customer(3L, "E10001", "ORG001");
        customer.setProfileVersion(4);
        customer.setLockVersion(7);
        when(customerMapper.selectActiveById(3L)).thenReturn(customer);
        when(customerMapper.updateProfileByVersions(eq(3L), eq(4), eq(7), any(), eq("ADMIN"), any()))
                .thenReturn(1);
        when(customerMapper.selectActiveById(3L)).thenReturn(customer, customer);

        MarketingCustomerProfileUpdateRequest request = new MarketingCustomerProfileUpdateRequest();
        request.setCustName("新名称");
        request.setCreditExposureAmount(new BigDecimal("120.50"));
        request.setProfileVersion(4);
        request.setLockVersion(7);
        request.setReason("管理员修订客户资料");

        MarketingCustomerService service = service();
        MarketingCustomerVO result = service.updateProfile(3L, request, "ADMIN", "ORG001", true);

        assertThat(result.getId()).isEqualTo(3L);
        ArgumentCaptor<MarketingCustomerProfileUpdateRequest> captor =
                ArgumentCaptor.forClass(MarketingCustomerProfileUpdateRequest.class);
        verify(customerMapper).updateProfileByVersions(eq(3L), eq(4), eq(7), captor.capture(), eq("ADMIN"), any());
        assertThat(captor.getValue().getCustName()).isEqualTo("新名称");
        assertThat(captor.getValue().getCreditExposureAmount()).isEqualByComparingTo("120.50");
    }

    @Test
    void updateProfile_shouldRejectStaleVersions() {
        MarketingCustomerInfo customer = customer(4L, "E10001", "ORG001");
        customer.setProfileVersion(5);
        customer.setLockVersion(8);
        when(customerMapper.selectActiveById(4L)).thenReturn(customer);

        MarketingCustomerProfileUpdateRequest request = new MarketingCustomerProfileUpdateRequest();
        request.setCustName("新名称");
        request.setProfileVersion(4);
        request.setLockVersion(8);
        request.setReason("管理员修订客户资料");

        assertThatThrownBy(() -> service().updateProfile(4L, request, "ADMIN", "ORG001", true))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("版本");
    }

    @Test
    void updateProfile_shouldValidateDistinctTagsAndSynchronizeRelations() {
        MarketingCustomerInfo customer = customer(6L, "E10001", "ORG001");
        customer.setProfileVersion(1);
        customer.setLockVersion(2);
        when(customerMapper.selectActiveById(6L)).thenReturn(customer, customer);
        when(customerMapper.updateProfileByVersions(eq(6L), eq(1), eq(2), any(), eq("ADMIN"), any()))
                .thenReturn(1);

        MarketingCustomerTagRel active = relation(601L, 6L, 10L, 1);
        MarketingCustomerTagRel inactive = relation(602L, 6L, 20L, 0);
        MarketingCustomerTagRel removed = relation(604L, 6L, 40L, 1);
        when(relationMapper.selectByCustId(6L)).thenReturn(List.of(active, inactive, removed));
        when(relationMapper.selectActiveByCustIds(List.of(6L))).thenReturn(List.of());
        when(tagService.requireImportable(10L)).thenReturn(tag(10L, "重点客户"));
        when(tagService.requireImportable(20L)).thenReturn(tag(20L, "项目客户"));
        when(tagService.requireImportable(30L)).thenReturn(tag(30L, "认证客户"));
        when(relationMapper.reactivateWithSourceType(eq(602L), eq("MANUAL"), eq("ADMIN"), any()))
                .thenReturn(1);
        when(relationMapper.insert(any(MarketingCustomerTagRel.class))).thenAnswer(invocation -> {
            MarketingCustomerTagRel relation = invocation.getArgument(0);
            relation.setId(603L);
            return 1;
        });

        MarketingCustomerProfileUpdateRequest request = profileRequest(1, 2);
        request.setTagIds(List.of(10L, 20L, 20L, 30L));

        MarketingCustomerVO result = service().updateProfile(6L, request, "ADMIN", "ORG001", true);

        assertThat(result.getId()).isEqualTo(6L);
        verify(tagService).requireImportable(10L);
        verify(tagService).requireImportable(20L);
        verify(tagService).requireImportable(30L);
        verify(relationMapper).expireNotInTagIds(eq(6L), eq(List.of(10L, 20L, 30L)), eq("ADMIN"), any());
        verify(relationMapper).reactivateWithSourceType(eq(602L), eq("MANUAL"), eq("ADMIN"), any());
        ArgumentCaptor<MarketingCustomerTagRel> captor = ArgumentCaptor.forClass(MarketingCustomerTagRel.class);
        verify(relationMapper).insert(captor.capture());
        assertThat(captor.getValue().getCustId()).isEqualTo(6L);
        assertThat(captor.getValue().getTagId()).isEqualTo(30L);
        assertThat(captor.getValue().getSourceType()).isEqualTo("MANUAL");
    }

    @Test
    void updateProfile_emptyTagIds_shouldExpireAllRelationsAndNullShouldLeaveTagsUntouched() {
        MarketingCustomerInfo customer = customer(7L, "E10001", "ORG001");
        customer.setProfileVersion(1);
        customer.setLockVersion(2);
        when(customerMapper.selectActiveById(7L)).thenReturn(customer);
        when(customerMapper.updateProfileByVersions(eq(7L), eq(1), eq(2), any(), eq("ADMIN"), any()))
                .thenReturn(1);
        when(relationMapper.selectByCustId(7L)).thenReturn(List.of(relation(701L, 7L, 10L, 1)));
        when(relationMapper.selectActiveByCustIds(List.of(7L))).thenReturn(List.of());

        MarketingCustomerProfileUpdateRequest clearRequest = profileRequest(1, 2);
        clearRequest.setTagIds(List.of());
        service().updateProfile(7L, clearRequest, "ADMIN", "ORG001", true);

        verify(relationMapper, times(1)).expireNotInTagIds(eq(7L), eq(List.of()), eq("ADMIN"), any());

        MarketingCustomerProfileUpdateRequest unchangedRequest = profileRequest(1, 2);
        unchangedRequest.setTagIds(null);
        unchangedRequest.setCustName("资料字段变更");
        service().updateProfile(7L, unchangedRequest, "ADMIN", "ORG001", true);
        verify(relationMapper, times(1)).expireNotInTagIds(eq(7L), eq(List.of()), eq("ADMIN"), any());
    }

    @Test
    void updateProfile_shouldRejectNullOrTooManyTagIds() {
        MarketingCustomerProfileUpdateRequest nullIdRequest = profileRequest(1, 2);
        nullIdRequest.setTagIds(Arrays.asList(10L, null, 10L));
        assertThatThrownBy(() -> service().updateProfile(7L, nullIdRequest,
                "ADMIN", "ORG001", true))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标签 ID 不能为空");

        MarketingCustomerProfileUpdateRequest tooManyRequest = profileRequest(1, 2);
        tooManyRequest.setTagIds(Collections.nCopies(101, 10L));
        assertThatThrownBy(() -> service().updateProfile(7L, tooManyRequest,
                "ADMIN", "ORG001", true))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("最多选择100个");
    }

    @Test
    void listAll_shouldBatchLoadActiveTagIdsAndNames() {
        MarketingCustomerInfo first = customer(8L, null, null);
        MarketingCustomerInfo second = customer(9L, null, null);
        when(customerMapper.selectPage(any(), eq(DataScopeType.ALL.getCode()), any(), eq((String) null), eq(0), eq(20)))
                .thenReturn(List.of(first, second));
        when(customerMapper.countPage(any(), eq(DataScopeType.ALL.getCode()), any(), eq((String) null)))
                .thenReturn(2L);
        when(relationMapper.selectActiveByCustIds(List.of(8L, 9L))).thenReturn(List.of(
                relation(801L, 8L, 100L, 1), relation(802L, 8L, 200L, 1), relation(803L, 9L, 100L, 1)));
        when(tagMapper.selectBatchIds(List.of(100L, 200L))).thenReturn(List.of(
                tag(100L, "战略客户"), tag(200L, "项目客户")));

        PageResult<MarketingCustomerVO> result = service().listAll(new MarketingCustomerQuery(),
                "ADMIN", "ORG001", DataScopeType.ALL);

        assertThat(result.getRecords()).extracting(MarketingCustomerVO::getTagIds)
                .containsExactly(List.of(100L, 200L), List.of(100L));
        assertThat(result.getRecords()).extracting(MarketingCustomerVO::getTagNames)
                .containsExactly(List.of("战略客户", "项目客户"), List.of("战略客户"));
        verify(relationMapper).selectActiveByCustIds(List.of(8L, 9L));
        verify(tagMapper).selectBatchIds(List.of(100L, 200L));
    }

    private MarketingCustomerService service() {
        return new MarketingCustomerService(customerMapper, userApi, orgApi, bizScopeApi,
                relationMapper, tagMapper, tagService);
    }

    private MarketingCustomerProfileUpdateRequest profileRequest(int profileVersion, int lockVersion) {
        MarketingCustomerProfileUpdateRequest request = new MarketingCustomerProfileUpdateRequest();
        request.setProfileVersion(profileVersion);
        request.setLockVersion(lockVersion);
        request.setReason("管理员修订客户标签");
        return request;
    }

    private MarketingCustomerTag tag(Long id, String name) {
        MarketingCustomerTag tag = new MarketingCustomerTag();
        tag.setId(id);
        tag.setTagName(name);
        tag.setRecordStatus("ACTIVE");
        return tag;
    }

    private MarketingCustomerTagRel relation(Long id, Long custId, Long tagId, int active) {
        MarketingCustomerTagRel relation = new MarketingCustomerTagRel();
        relation.setId(id);
        relation.setCustId(custId);
        relation.setTagId(tagId);
        relation.setActive(active);
        return relation;
    }

    private MarketingCustomerInfo customer(Long id, String managerId, String orgId) {
        MarketingCustomerInfo customer = new MarketingCustomerInfo();
        customer.setId(id);
        customer.setCustNo("C" + id);
        customer.setCustName("测试客户" + id);
        customer.setUnifiedCreditCode("91310000000000000" + id);
        customer.setMainManagerId(managerId);
        customer.setMainOrgId(orgId);
        customer.setOwnershipStatus("ASSIGNED");
        customer.setOwnershipMaintainMode("AUTO");
        customer.setRecordStatus("ACTIVE");
        customer.setProfileVersion(1);
        customer.setLockVersion(1);
        return customer;
    }
}
