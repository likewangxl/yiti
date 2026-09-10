package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.report.dto.req.ScreenCreateReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenMetadataUpdateReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenEntryRespDTO;
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

/** 当前用户可见的代码化大屏目录服务契约。 */
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
    void catalog_returnsOnlyFixedCodeRegistrationsAndDoesNotRequirePublishedCanvas() {
        RptScreen province = screen("SCR_PROVINCE", "数据库旧名称", "PROVINCE", "COMMON", "ACTIVE", 0, null);
        RptScreen retail = screen("SCR_RETAIL_OVERVIEW", "数据库零售名称", "BRANCH", "RETAIL", "ACTIVE", 0, null);
        RptScreen old = screen("SCR_OLD", "旧发布屏", "BRANCH", "COMMON", "ACTIVE", 1,
                "{\"schemaVersion\":1,\"components\":[]}");
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(old, retail, province));
        when(scopeAuthorizationService.authorize(any(RptScreen.class))).thenReturn(Set.of());

        List<ScreenEntryRespDTO> result = service.listAuthorizedCodeScreens();

        assertThat(result).extracting(ScreenEntryRespDTO::getScreenCode)
                .containsExactly("SCR_PROVINCE", "SCR_RETAIL_OVERVIEW");
        assertThat(result).extracting(ScreenEntryRespDTO::getScreenName)
                .containsExactly("分行经营总览", "零售经营总览");
        assertThat(result).extracting(ScreenEntryRespDTO::getTemplate)
                .containsExactly("branch-overview-v1", "retail-overview-v1");
        assertThat(result).extracting(ScreenEntryRespDTO::getBizLine)
                .containsExactly("COMMON", "RETAIL");
        assertThat(result).allSatisfy(entry -> assertThat(entry.getDataMode()).isEqualTo("DEMO"));
    }

    @Test
    void catalog_excludesInactiveUnknownAndInvalidScopeWithoutLeakingMetadata() {
        RptScreen inactive = screen("SCR_PROVINCE", "停用屏", "PROVINCE", "COMMON", "DISABLED", 0, null);
        RptScreen unknown = screen("SCR_OLD", "旧屏敏感名称", "BRANCH", "COMMON", "ACTIVE", 0, null);
        RptScreen invalid = screen("SCR_RETAIL_OVERVIEW", "非法范围名称", "BRANCH", "BAD_LINE", "ACTIVE", 0, null);
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(inactive, unknown, invalid));

        assertThat(service.listAuthorizedCodeScreens()).isEmpty();
        verify(scopeAuthorizationService, never()).authorize(any(RptScreen.class));
    }

    @Test
    void catalog_excludesDeniedRegisteredScreenWithoutLeakingMetadata() {
        RptScreen denied = screen("SCR_RETAIL_OVERVIEW", "敏感零售名称", "BRANCH", "RETAIL", "ACTIVE", 0, null);
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(denied));
        when(scopeAuthorizationService.authorize(denied)).thenThrow(
                new com.bank.branch.platform.report.exception.RptException(RptErrorCode.SCREEN_ACCESS_DENIED));

        assertThat(service.listAuthorizedCodeScreens()).isEmpty();
    }

    @Test
    void catalog_namedGroupRequiresNonEmptyAuthorizedScope() {
        RptScreen retail = screen("SCR_RETAIL_OVERVIEW", "零售经营总览", "BRANCH", "RETAIL", "ACTIVE", 0, null);
        retail.setOrgScopeMode("NAMED_GROUP");
        retail.setOrgGroupCode("GROUP_RETAIL");
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(retail));
        when(scopeAuthorizationService.authorize(retail)).thenReturn(Set.of());

        assertThat(service.listAuthorizedCodeScreens()).isEmpty();
    }

    @Test
    void catalog_infrastructureFailureIsPropagatedInsteadOfBecomingEmpty() {
        when(screenMapper.selectList(any(Wrapper.class))).thenThrow(new IllegalStateException("database offline"));

        assertThatThrownBy(() -> service.listAuthorizedCodeScreens())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database offline");
    }

    @Test
    void catalog_authorizationInfrastructureFailureWithKnownCodeIsPropagated() {
        RptScreen retail = screen("SCR_RETAIL_OVERVIEW", "零售经营总览", "BRANCH", "RETAIL", "ACTIVE", 0, null);
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(retail));
        when(scopeAuthorizationService.authorize(retail)).thenThrow(
                new com.bank.branch.platform.report.exception.RptException(
                        RptErrorCode.SCREEN_ACCESS_DENIED, new IllegalStateException("role store offline")));

        assertThatThrownBy(() -> service.listAuthorizedCodeScreens())
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", RptErrorCode.SCREEN_ACCESS_DENIED.getCode())
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void catalog_doesNotExposeSensitiveEntityFieldsInDto() {
        Set<String> fields = Arrays.stream(ScreenEntryRespDTO.class.getDeclaredFields())
                .map(Field::getName).collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder(
                "screenCode", "screenName", "viewLevel", "bizLine", "template", "dataMode");
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
        RptScreen existing = screen("EXISTING", "既有屏", "BRANCH", "COMMON", "ACTIVE", 0, null);
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

    private RptScreen screen(String code, String name, String viewLevel, String bizLine,
                             String status, int publishStatus, String packageJson) {
        RptScreen screen = new RptScreen();
        screen.setId((long) Math.abs(code.hashCode()));
        screen.setScreenCode(code);
        screen.setScreenName(name);
        screen.setViewLevel(viewLevel);
        screen.setBizLine(bizLine);
        screen.setOrgScopeMode("LEGACY_CONTEXT");
        screen.setStatus(status);
        screen.setPublishStatus(publishStatus);
        screen.setCanvasPublishedJson(packageJson);
        return screen;
    }
}
