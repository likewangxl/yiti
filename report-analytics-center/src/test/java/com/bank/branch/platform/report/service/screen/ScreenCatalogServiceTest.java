package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.resp.ScreenEntryRespDTO;
import com.bank.branch.platform.report.dto.req.ScreenCreateReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenMetadataUpdateReqDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenCanvasMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapPointMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 当前用户可见的大屏运行时目录服务契约。 */
@ExtendWith(MockitoExtension.class)
class ScreenCatalogServiceTest {

    @Mock private RptScreenMapper screenMapper;
    @Mock private RptScreenBlockMapper blockMapper;
    @Mock private RptScreenDatasourceMapper datasourceMapper;
    @Mock private RptScreenMapPointMapper mapPointMapper;
    @Mock private RptScreenCanvasMapper canvasMapper;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private ScreenScopeAuthorizationService scopeAuthorizationService;

    private ScreenConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScreenConfigServiceImpl(screenMapper, blockMapper, datasourceMapper,
                mapPointMapper, currentUserApi);
        ReflectionTestUtils.setField(service, "scopeAuthorizationService", scopeAuthorizationService);
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    }

    @Test
    void catalog_filtersActivePublishedPackagesAndSortsByScreenCode() {
        RptScreen b = screen("B", "B屏", "ACTIVE", 2, validPackage());
        RptScreen a = screen("A", "A屏", "ACTIVE", 1, validPackage());
        RptScreen draft = screen("DRAFT", "草稿", "ACTIVE", 0, validPackage());
        RptScreen disabled = screen("DISABLED", "禁用", "DISABLED", 1, validPackage());
        RptScreen blank = screen("BLANK", "空包", "ACTIVE", 1, " \t\r\n");
        RptScreen missing = screen("MISSING", "无包", "ACTIVE", 1, null);
        RptScreen nonObject = screen("NON_OBJECT", "非对象包", "ACTIVE", 1, "[]");
        when(screenMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(b, a, draft, disabled, blank, missing, nonObject));
        when(scopeAuthorizationService.authorize(any(RptScreen.class))).thenReturn(Set.of());

        List<ScreenEntryRespDTO> result = service.listAuthorizedPublishedScreens();

        assertThat(result).extracting(ScreenEntryRespDTO::getScreenCode)
                .containsExactly("A", "B");
        assertThat(result).allSatisfy(entry -> {
            assertThat(entry.getScreenName()).isNotBlank();
            assertThat(entry.getViewLevel()).isEqualTo("BRANCH");
            assertThat(entry.getBizLine()).isEqualTo("COMMON");
        });
        assertThat(result.toString()).doesNotContain("非对象包", "NON_OBJECT");
    }

    @Test
    void catalog_excludesDeniedOrInvalidScreensWithoutLeakingTheirMetadata() {
        RptScreen allowed = screen("ALLOWED", "允许屏", "ACTIVE", 1, validPackage());
        RptScreen denied = screen("SECRET", "敏感屏名称", "ACTIVE", 1, validPackage());
        RptScreen invalid = screen("BROKEN", "非法屏名称", "ACTIVE", 1, "not-json");
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(denied, invalid, allowed));
        when(scopeAuthorizationService.authorize(denied))
                .thenThrow(new com.bank.branch.platform.report.exception.RptException(
                        RptErrorCode.SCREEN_ACCESS_DENIED));
        when(scopeAuthorizationService.authorize(allowed)).thenReturn(Set.of());

        List<ScreenEntryRespDTO> result = service.listAuthorizedPublishedScreens();

        assertThat(result).extracting(ScreenEntryRespDTO::getScreenCode)
                .containsExactly("ALLOWED");
        assertThat(result.toString()).doesNotContain("SECRET", "敏感屏名称", "BROKEN", "非法屏名称");
    }

    @Test
    void catalog_excludesNamedGroupScopeDenialAndRangeDenial() {
        RptScreen roleDenied = screen("ROLE_DENIED", "角色拒绝", "ACTIVE", 1, validPackage());
        RptScreen rangeDenied = screen("RANGE_DENIED", "范围拒绝", "ACTIVE", 1, validPackage());
        roleDenied.setOrgScopeMode("NAMED_GROUP");
        rangeDenied.setOrgScopeMode("NAMED_GROUP");
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(roleDenied, rangeDenied));
        when(scopeAuthorizationService.authorize(roleDenied))
                .thenThrow(new com.bank.branch.platform.report.exception.RptException(
                        RptErrorCode.SCREEN_ACCESS_DENIED));
        when(scopeAuthorizationService.authorize(rangeDenied))
                .thenThrow(new com.bank.branch.platform.report.exception.RptException(
                        RptErrorCode.SCREEN_SCOPE_INVALID));

        assertThat(service.listAuthorizedPublishedScreens()).isEmpty();
    }

    @Test
    void catalog_infrastructureFailureIsPropagatedInsteadOfBecomingEmpty() {
        when(screenMapper.selectList(any(Wrapper.class))).thenThrow(new IllegalStateException("database offline"));

        assertThatThrownBy(() -> service.listAuthorizedPublishedScreens())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database offline");
    }

    @Test
    void catalog_authorizationInfrastructureFailureWithKnownCodeIsPropagated() {
        RptScreen screen = screen("AUTH_INFRA", "授权基础设施故障", "ACTIVE", 1, validPackage());
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(screen));
        when(scopeAuthorizationService.authorize(screen))
                .thenThrow(new com.bank.branch.platform.report.exception.RptException(
                        RptErrorCode.SCREEN_ACCESS_DENIED, new IllegalStateException("role store offline")));

        assertThatThrownBy(() -> service.listAuthorizedPublishedScreens())
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_ACCESS_DENIED.getCode())
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void catalog_preservesPublishedScreenWhenDraftExists() {
        RptScreen publishedWithDraft = screen("PUBLISHED_DRAFT", "已发布有草稿", "ACTIVE", 2, validPackage());
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(publishedWithDraft));
        when(scopeAuthorizationService.authorize(publishedWithDraft)).thenReturn(Set.of());

        assertThat(service.listAuthorizedPublishedScreens())
                .extracting(ScreenEntryRespDTO::getScreenCode)
                .containsExactly("PUBLISHED_DRAFT");
    }

    @Test
    void catalog_acceptsHistoricalObjectPackageWithoutSchemaVersionOrBindSnapshots() {
        RptScreen historical = screen("HISTORICAL", "历史旧包", "ACTIVE", 1, "{\"components\":[]}");
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(historical));
        when(scopeAuthorizationService.authorize(historical)).thenReturn(Set.of());

        assertThat(service.listAuthorizedPublishedScreens())
                .extracting(ScreenEntryRespDTO::getScreenCode)
                .containsExactly("HISTORICAL");
    }

    @Test
    void catalog_doesNotExposeSensitiveEntityFieldsInDto() {
        Set<String> fields = Arrays.stream(ScreenEntryRespDTO.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder("screenCode", "screenName", "viewLevel", "bizLine");
    }

    @Test
    void createScreen_rejectsExactCatalogRouteCode() {
        ScreenCreateReqDTO req = new ScreenCreateReqDTO();
        req.setScreenCode("catalog");
        req.setScreenName("冲突目录路由");
        req.setViewLevel("BRANCH");
        req.setBizLine("COMMON");
        req.setOrgScopeMode("LEGACY_CONTEXT");

        assertThatThrownBy(() -> service.createScreen(req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
        verify(screenMapper, never()).insert(any(RptScreen.class));
    }

    @Test
    void updateScreenMetadata_rejectsExactCatalogRouteCode() {
        RptScreen existing = screen("EXISTING", "既有屏", "ACTIVE", 1, validPackage());
        existing.setId(11L);
        when(screenMapper.selectById(11L)).thenReturn(existing);
        ScreenMetadataUpdateReqDTO req = new ScreenMetadataUpdateReqDTO();
        req.setScreenCode("catalog");
        req.setScreenName("冲突目录路由");
        req.setViewLevel("BRANCH");
        req.setBizLine("COMMON");
        req.setOrgScopeMode("LEGACY_CONTEXT");
        req.setExpectedVersion(1);
        req.setReason("测试保留路由");

        assertThatThrownBy(() -> service.updateScreenMetadata(11L, req))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_LAYOUT_INVALID.getCode());
        verify(canvasMapper, never()).updateMetadataCas(any(RptScreen.class), anyInt(), any(String.class));
    }

    private RptScreen screen(String code, String name, String status, int publishStatus, String packageJson) {
        RptScreen screen = new RptScreen();
        screen.setId((long) Math.abs(code.hashCode()));
        screen.setScreenCode(code);
        screen.setScreenName(name);
        screen.setViewLevel("BRANCH");
        screen.setBizLine("COMMON");
        screen.setOrgScopeMode("LEGACY_CONTEXT");
        screen.setStatus(status);
        screen.setPublishStatus(publishStatus);
        screen.setCanvasPublishedJson(packageJson);
        return screen;
    }

    private String validPackage() {
        return "{\"schemaVersion\":1,\"components\":[],\"bindSnapshots\":{}}";
    }
}
