package com.bank.branch.platform.auth.location;

import com.bank.branch.platform.auth.api.dto.OrgProfileDTO;
import com.bank.branch.platform.auth.api.dto.OrgLocationCapabilitiesDTO;
import com.bank.branch.platform.auth.api.dto.OrgLocationDTO;
import com.bank.branch.platform.auth.api.dto.OrgLocationGeocodeCandidateDTO;
import com.bank.branch.platform.auth.api.dto.OrgLocationGeocodePreviewReqDTO;
import com.bank.branch.platform.auth.api.dto.OrgLocationUpdateReqDTO;
import com.bank.branch.platform.auth.entity.ExtOrgInfo;
import com.bank.branch.platform.auth.entity.PtOrgProfile;
import com.bank.branch.platform.auth.location.audit.OrgLocationAuditService;
import com.bank.branch.platform.auth.location.geocode.OrgLocationGeocoder;
import com.bank.branch.platform.auth.location.geocode.OrgLocationGeocodeCandidate;
import com.bank.branch.platform.auth.location.persistence.OrgLocationStore;
import com.bank.branch.platform.auth.location.persistence.PtOrgLocation;
import com.bank.branch.platform.auth.location.security.OrgLocationCandidateTokenService;
import com.bank.branch.platform.auth.location.service.OrgLocationService;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 机构位置能力纯单元测试。
 *
 * <p>这些用例刻意只使用 mock store/外部机构 mapper，不连接任何真实数据库，固定位置存储
 * 默认关闭、数据范围、CAS、审计前置和运行时坐标隔离契约。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrgLocationServiceTest {

    @Mock
    private OrgLocationStore store;
    @Mock
    private OrgLocationGeocoder geocoder;
    @Mock
    private OrgLocationAuditService auditService;
    @Mock
    private com.bank.branch.platform.auth.mapper.OrgMapper orgMapper;
    @Mock
    private com.bank.branch.platform.auth.mapper.OrgProfileMapper orgProfileMapper;

    private OrgLocationProperties properties;
    private OrgLocationCandidateTokenService tokenService;
    private OrgLocationService service;

    @BeforeEach
    void setUp() {
        properties = new OrgLocationProperties();
        properties.setStorageEnabled(true);
        properties.setGeocodingEnabled(true);
        properties.setApiKey("unit-test-amap-key");
        properties.setSigningSecret("unit-test-location-signing-secret-012345");
        properties.setCandidateTtlSeconds(300);
        tokenService = new OrgLocationCandidateTokenService(properties,
                Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC));
        service = new OrgLocationService(store, geocoder, auditService, orgMapper,
                orgProfileMapper, properties, tokenService);

        DataScopeContext context = new DataScopeContext();
        context.setEmpId("E10001");
        context.setOrgCode("ORG001");
        context.setScope(DataScopeType.ALL);
        DataScopeContext.set(context);
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    void disabledStorageDoesNotTouchStoreAndReportsUnavailable() {
        properties.setStorageEnabled(false);
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(activeOrg("ORG001"));

        assertThat(service.capabilities().isStorageEnabled()).isFalse();
        assertThat(service.capabilities().isStorageAvailable()).isFalse();
        assertThatThrownBy(() -> service.getLocation("ORG001"))
                .hasMessageContaining("位置存储");

        verify(store, never()).isAvailable();
        verify(store, never()).findByOrgCode(any());
    }

    @Test
    void capabilityReportsMissingStorageSchemaAndDoesNotProbeGeocoder() {
        when(store.isAvailable()).thenReturn(false);

        OrgLocationCapabilitiesDTO result = service.capabilities();

        assertThat(result.isStorageEnabled()).isTrue();
        assertThat(result.isStorageAvailable()).isFalse();
        assertThat(result.isGeocodingEnabled()).isTrue();
        assertThat(result.isGeocodingAvailable()).isFalse();
        assertThat(result.getStorageReason()).contains("PT_ORG_LOCATION");
        assertThat(result.getGeocodingReason()).contains("存储");
        verify(geocoder, never()).isAvailable();
    }

    @Test
    void capabilityReportsMissingApiKeyWithoutExternalProbe() {
        properties.setApiKey(null);
        when(store.isAvailable()).thenReturn(true);

        OrgLocationCapabilitiesDTO result = service.capabilities();

        assertThat(result.isStorageAvailable()).isTrue();
        assertThat(result.isGeocodingEnabled()).isTrue();
        assertThat(result.isGeocodingAvailable()).isFalse();
        assertThat(result.getGeocodingReason()).contains("Key");
        verify(geocoder, never()).isAvailable();
    }

    @Test
    void missingExternalOrganizationIsRejectedBeforeStorageWrite() {
        when(orgMapper.selectByOrgCode("ORG404")).thenReturn(null);

        OrgLocationUpdateReqDTO request = manualRequest(0);

        assertThatThrownBy(() -> service.updateLocation("ORG404", request))
                .hasMessageContaining("机构不存在或已停用");
        verify(store, never()).insert(any());
        verify(store, never()).updateWithVersion(any(), any());
    }

    @Test
    void scopeMustContainCurrentOrganizationOrSubtree() {
        DataScopeContext.current().setScope(DataScopeType.ORG);
        DataScopeContext.current().setOrgCode("ORG001");

        assertThatThrownBy(() -> service.updateLocation("ORG999", manualRequest(0)))
                .hasMessageContaining("数据范围");
        verify(store, never()).insert(any());
        verifyNoInteractions(orgMapper);
    }

    @Test
    void manualCoordinatesCanBeSavedOnlyAfterExplicitConfirmation() {
        when(store.isAvailable()).thenReturn(true);
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(activeOrg("ORG001"));
        when(orgProfileMapper.selectById("ORG001")).thenReturn(profileWithCity("ORG001", "610100"));
        when(store.findByOrgCode("ORG001")).thenReturn(null);

        OrgLocationUpdateReqDTO request = manualRequest(0);
        request.setLng(new BigDecimal("108.9000000"));
        request.setLat(new BigDecimal("34.2000000"));
        request.setManualConfirmed(true);

        OrgLocationDTO result = service.updateLocation("ORG001", request);

        assertThat(result.getStatus()).isEqualTo(OrgLocationService.STATUS_VERIFIED);
        assertThat(result.getLocationSource()).isEqualTo(OrgLocationService.SOURCE_MANUAL);
        assertThat(result.getProvider()).isEqualTo(OrgLocationService.PROVIDER_MANUAL);
        assertThat(result.getCoordSys()).isEqualTo(OrgLocationService.COORD_SYS_GCJ02);
        verify(store).insert(any(PtOrgLocation.class));
        verify(auditService).locationChanged(any(), any(), eq(request.getReason()));
    }

    @Test
    void addressOnlyChangeRequiresMatchingActiveProfileCity() {
        when(store.isAvailable()).thenReturn(true);
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(activeOrg("ORG001"));
        when(orgProfileMapper.selectById("ORG001")).thenReturn(profileWithCity("ORG001", "610100"));
        when(store.findByOrgCode("ORG001")).thenReturn(location("ORG001", 2));
        OrgLocationUpdateReqDTO request = manualRequest(2);
        request.setCityCode("610300");

        assertThatThrownBy(() -> service.updateLocation("ORG001", request))
                .hasMessageContaining("画像一致");
        verify(store, never()).updateWithVersion(any(), any());
    }

    @Test
    void addressChangeClearsPreviousManualCoordinatesUntilReconfirmed() {
        when(store.isAvailable()).thenReturn(true);
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(activeOrg("ORG001"));
        when(orgProfileMapper.selectById("ORG001")).thenReturn(profileWithCity("ORG001", "610100"));
        PtOrgLocation existing = location("ORG001", 2);
        existing.setAddress("陕西省西安市雁塔区旧地址");
        existing.setStatus(OrgLocationService.STATUS_VERIFIED);
        existing.setLocationSource(OrgLocationService.SOURCE_MANUAL);
        existing.setAddressSource("MANUAL");
        existing.setProvider("MANUAL");
        existing.setMatchLevel("MANUAL");
        existing.setLng(new BigDecimal("108.9000000"));
        existing.setLat(new BigDecimal("34.2000000"));
        existing.setCoordSys("GCJ02");
        when(store.findByOrgCode("ORG001")).thenReturn(existing);
        when(store.updateWithVersion(any(PtOrgLocation.class), eq(2))).thenReturn(true);

        OrgLocationUpdateReqDTO request = manualRequest(2);
        request.setAddress("陕西省西安市雁塔区新地址");
        ArgumentCaptor<PtOrgLocation> changed = ArgumentCaptor.forClass(PtOrgLocation.class);

        service.updateLocation("ORG001", request);

        verify(store).updateWithVersion(changed.capture(), eq(2));
        assertThat(changed.getValue().getLng()).isNull();
        assertThat(changed.getValue().getLat()).isNull();
        assertThat(changed.getValue().getProvider()).isNull();
        assertThat(changed.getValue().getMatchLevel()).isNull();
        assertThat(changed.getValue().getLocationSource()).isNull();
        assertThat(changed.getValue().getStatus()).isEqualTo(OrgLocationService.STATUS_UNLOCATED);
    }

    @Test
    void storageWriteFailureDoesNotAudit() {
        when(store.isAvailable()).thenReturn(true);
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(activeOrg("ORG001"));
        when(orgProfileMapper.selectById("ORG001")).thenReturn(profileWithCity("ORG001", "610100"));
        when(store.findByOrgCode("ORG001")).thenReturn(null);
        when(store.insert(any(PtOrgLocation.class))).thenThrow(new IllegalStateException("db down"));
        OrgLocationUpdateReqDTO request = manualRequest(0);
        request.setLng(new BigDecimal("108.9000000"));
        request.setLat(new BigDecimal("34.2000000"));
        request.setManualConfirmed(true);

        assertThatThrownBy(() -> service.updateLocation("ORG001", request))
                .hasMessageContaining("存储能力");
        verifyNoInteractions(auditService);
    }

    @Test
    void concurrentFirstInsertDuplicateKeyIsVersionConflictAndDoesNotAudit() {
        when(store.isAvailable()).thenReturn(true);
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(activeOrg("ORG001"));
        when(orgProfileMapper.selectById("ORG001")).thenReturn(profileWithCity("ORG001", "610100"));
        when(store.findByOrgCode("ORG001")).thenReturn(null);
        when(store.insert(any(PtOrgLocation.class)))
                .thenThrow(new DuplicateKeyException("duplicate org location"));

        OrgLocationUpdateReqDTO request = manualRequest(0);
        request.setLng(new BigDecimal("108.9000000"));
        request.setLat(new BigDecimal("34.2000000"));
        request.setManualConfirmed(true);

        assertThatThrownBy(() -> service.updateLocation("ORG001", request))
                .hasMessageContaining("其他管理员修改");
        verify(auditService, never()).locationChanged(any(), any(), any());
        verify(store, never()).updateWithVersion(any(), any());
    }

    @Test
    void compareAndSetConflictPerformsNoAudit() {
        when(store.isAvailable()).thenReturn(true);
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(activeOrg("ORG001"));
        when(orgProfileMapper.selectById("ORG001")).thenReturn(profileWithCity("ORG001", "610100"));
        PtOrgLocation existing = location("ORG001", 4);
        when(store.findByOrgCode("ORG001")).thenReturn(existing);
        when(store.updateWithVersion(any(PtOrgLocation.class), eq(4))).thenReturn(false);

        OrgLocationUpdateReqDTO request = manualRequest(4);
        request.setLng(new BigDecimal("108.9000000"));
        request.setLat(new BigDecimal("34.2000000"));
        request.setManualConfirmed(true);

        assertThatThrownBy(() -> service.updateLocation("ORG001", request))
                .hasMessageContaining("其他管理员修改");
        verify(auditService, never()).locationChanged(any(), any(), any());
    }

    @Test
    void auditFailureIsPropagatedAfterStorageMutationForTransactionRollback() {
        when(store.isAvailable()).thenReturn(true);
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(activeOrg("ORG001"));
        when(orgProfileMapper.selectById("ORG001")).thenReturn(profileWithCity("ORG001", "610100"));
        when(store.findByOrgCode("ORG001")).thenReturn(null);
        PtOrgLocation saved = location("ORG001", 1);
        when(store.insert(any(PtOrgLocation.class))).thenReturn(saved);
        org.mockito.Mockito.doThrow(new RuntimeException("audit down"))
                .when(auditService).locationChanged(any(), any(), any());

        OrgLocationUpdateReqDTO request = manualRequest(0);
        request.setLng(new BigDecimal("108.9000000"));
        request.setLat(new BigDecimal("34.2000000"));
        request.setManualConfirmed(true);

        assertThatThrownBy(() -> service.updateLocation("ORG001", request))
                .hasMessageContaining("审计");
        verify(store).insert(any(PtOrgLocation.class));
    }

    @Test
    void candidateTokenCannotCrossOrganizationOrAddressOrExpire() {
        OrgLocationGeocodeCandidate candidate = candidate("ORG001", "610100");
        String token = tokenService.issue("E10001", "ORG001", "陕西省西安市雁塔区1号",
                "610100", candidate);

        assertThat(tokenService.verify(token, "E10001", "ORG001", "陕西省西安市雁塔区1号",
                "610100", candidate)).isPresent();
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");
        assertThat(tokenService.verify(tampered, "E10001", "ORG001", "陕西省西安市雁塔区1号",
                "610100", candidate)).isEmpty();
        assertThat(tokenService.verify(token, "E10001", "ORG002", "陕西省西安市雁塔区1号",
                "610100", candidate)).isEmpty();
        assertThat(tokenService.verify(token, "E10001", "ORG001", "陕西省西安市雁塔区2号",
                "610100", candidate)).isEmpty();

        OrgLocationProperties expired = new OrgLocationProperties();
        expired.setSigningSecret("unit-test-location-signing-secret-012345");
        expired.setCandidateTtlSeconds(1);
        OrgLocationCandidateTokenService issueService = new OrgLocationCandidateTokenService(expired,
                Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC));
        String expiredToken = issueService.issue("E10001", "ORG001", "陕西省西安市雁塔区1号",
                "610100", candidate);
        OrgLocationCandidateTokenService expiredService = new OrgLocationCandidateTokenService(expired,
                Clock.fixed(Instant.parse("2026-09-08T00:00:02Z"), ZoneOffset.UTC));
        assertThat(expiredService.verify(expiredToken, "E10001", "ORG001", "陕西省西安市雁塔区1号",
                "610100", candidate)).isEmpty();
    }

    @Test
    void shortSigningSecretOrLongTtlCannotIssueCandidateToken() {
        OrgLocationProperties unsafe = new OrgLocationProperties();
        unsafe.setSigningSecret("too-short");
        unsafe.setCandidateTtlSeconds(300);
        OrgLocationCandidateTokenService shortSecret = new OrgLocationCandidateTokenService(unsafe);
        assertThat(shortSecret.isAvailable()).isFalse();

        unsafe.setSigningSecret("unit-test-location-signing-secret-012345");
        unsafe.setCandidateTtlSeconds(901);
        OrgLocationCandidateTokenService longTtl = new OrgLocationCandidateTokenService(unsafe);
        assertThat(longTtl.isAvailable()).isFalse();
    }

    @Test
    void lowPrecisionOrCrossCityCandidatesCannotBeConfirmed() {
        when(store.isAvailable()).thenReturn(true);
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(activeOrg("ORG001"));
        when(orgProfileMapper.selectById("ORG001")).thenReturn(profileWithCity("ORG001", "610100"));
        when(geocoder.isAvailable()).thenReturn(true);
        when(geocoder.geocode(eq("陕西省西安市"), eq("610100"))).thenReturn(List.of(
                candidate("ORG001", "610100").withMatchLevel("城市"),
                candidate("ORG001", "610300").withMatchLevel("门牌号")));

        OrgLocationGeocodePreviewReqDTO request = new OrgLocationGeocodePreviewReqDTO();
        request.setAddress("陕西省西安市");
        request.setCityCode("610100");
        request.setReason("核对机构地址");
        org.mockito.Mockito.doNothing().when(auditService)
                .geocodePreviewRequested(any(), any(), any(), any());

        List<OrgLocationGeocodeCandidateDTO> result = service.previewGeocode("ORG001", request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isVerificationAllowed()).isFalse();
        assertThat(result.get(0).getCandidateToken()).isNull();
        verify(geocoder).geocode("陕西省西安市", "610100");
    }

    @Test
    void geocodePreviewAuditsBeforeExternalCall() {
        when(store.isAvailable()).thenReturn(true);
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(activeOrg("ORG001"));
        when(orgProfileMapper.selectById("ORG001")).thenReturn(profileWithCity("ORG001", "610100"));
        when(geocoder.isAvailable()).thenReturn(true);
        when(geocoder.geocode(any(), any())).thenReturn(List.of());

        OrgLocationGeocodePreviewReqDTO request = new OrgLocationGeocodePreviewReqDTO();
        request.setAddress("陕西省西安市雁塔区1号");
        request.setCityCode("610100");
        request.setReason("核对机构地址");

        service.previewGeocode("ORG001", request);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(auditService, geocoder);
        order.verify(auditService).geocodePreviewRequested("ORG001", request.getAddress(),
                request.getCityCode(), request.getReason());
        order.verify(geocoder).geocode(request.getAddress(), request.getCityCode());
    }

    @Test
    void geocodeOnWithoutStorageStopsBeforeAuditOrExternalCall() {
        properties.setStorageEnabled(false);
        when(orgMapper.selectByOrgCode("ORG001")).thenReturn(activeOrg("ORG001"));
        when(orgProfileMapper.selectById("ORG001")).thenReturn(profileWithCity("ORG001", "610100"));
        OrgLocationGeocodePreviewReqDTO request = new OrgLocationGeocodePreviewReqDTO();
        request.setAddress("陕西省西安市雁塔区1号");
        request.setCityCode("610100");
        request.setReason("核对机构地址");

        assertThatThrownBy(() -> service.previewGeocode("ORG001", request))
                .hasMessageContaining("位置存储");
        verifyNoInteractions(auditService, geocoder, store);
    }

    @Test
    void pollutedProfileCityCodeRemainsUnmaintainedForRuntimeSupplement() {
        OrgProfileDTO profile = new OrgProfileDTO();
        profile.setOrgCode("ORG001");
        profile.setStatus("ACTIVE");
        profile.setCityCode("610100\u200c");

        assertThat(service.findRuntimeLocations(Set.of("ORG001"), Map.of("ORG001", profile))).isEmpty();
        verify(store, never()).findByOrgCodes(any());
    }

    @Test
    void runtimeSupplementUsesBatchStoreAndNeverLeaksOtherOrganizationOrAddress() {
        when(store.isAvailable()).thenReturn(true);
        PtOrgLocation own = location("ORG001", 2);
        own.setCityCode("610100");
        own.setStatus("VERIFIED");
        own.setLocationSource("GEOCODE_VERIFIED");
        own.setLng(new BigDecimal("108.9000000"));
        own.setLat(new BigDecimal("34.2000000"));
        own.setCoordSys("GCJ02");
        PtOrgLocation other = location("ORG002", 2);
        other.setCityCode("610100");
        other.setStatus("VERIFIED");
        other.setLocationSource("GEOCODE_VERIFIED");
        other.setLng(new BigDecimal("109.0000000"));
        other.setLat(new BigDecimal("34.3000000"));
        other.setCoordSys("GCJ02");
        when(store.findByOrgCodes(Set.of("ORG001"))).thenReturn(List.of(own, other));

        OrgProfileDTO profile = new OrgProfileDTO();
        profile.setOrgCode("ORG001");
        profile.setStatus("ACTIVE");
        profile.setCityCode("610100");

        Map<String, PtOrgLocation> result = service.findRuntimeLocations(Set.of("ORG001"),
                Map.of("ORG001", profile));

        assertThat(result).containsKey("ORG001").doesNotContainKey("ORG002");
        assertThat(result.get("ORG001").getAddress()).isNull();
        assertThat(result.get("ORG001").getAddressSource()).isNull();
        assertThat(result.get("ORG001").getProvider()).isNull();
        assertThat(result.get("ORG001").getMatchLevel()).isNull();
        assertThat(result.get("ORG001").getCreatedBy()).isNull();
        assertThat(result.get("ORG001").getUpdatedBy()).isNull();
        verify(store).findByOrgCodes(Set.of("ORG001"));
    }

    private OrgLocationUpdateReqDTO manualRequest(int version) {
        OrgLocationUpdateReqDTO request = new OrgLocationUpdateReqDTO();
        request.setAddress("陕西省西安市雁塔区1号");
        request.setCityCode("610100");
        request.setVersion(version);
        request.setReason("人工校准机构位置");
        request.setCoordSys("GCJ02");
        return request;
    }

    private ExtOrgInfo activeOrg(String code) {
        ExtOrgInfo org = new ExtOrgInfo();
        org.setOrgCode(code);
        org.setOrgName("测试机构");
        org.setOrganState(0);
        return org;
    }

    private PtOrgProfile profileWithCity(String code, String cityCode) {
        PtOrgProfile profile = new PtOrgProfile();
        profile.setOrgCode(code);
        profile.setStatus("ACTIVE");
        profile.setCityCode(cityCode);
        return profile;
    }

    private PtOrgLocation location(String code, int version) {
        PtOrgLocation location = new PtOrgLocation();
        location.setOrgCode(code);
        location.setVersion(version);
        location.setAddress("陕西省西安市雁塔区1号");
        location.setCityCode("610100");
        location.setStatus("UNLOCATED");
        return location;
    }

    private OrgLocationGeocodeCandidate candidate(String code, String cityCode) {
        return new OrgLocationGeocodeCandidate("AMAP", "陕西省西安市雁塔区1号", cityCode,
                new BigDecimal("108.9000000"), new BigDecimal("34.2000000"), "GCJ02", "门牌号");
    }
}
