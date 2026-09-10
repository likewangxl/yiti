package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.report.dto.resp.ScreenEntryRespDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 新代码化大屏目录的固定注册表契约。 */
@ExtendWith(MockitoExtension.class)
class ScreenCatalogFixedRegistryTest {

    @Mock private RptScreenMapper screenMapper;
    @Mock private RptScreenBlockMapper blockMapper;
    @Mock private RptScreenDatasourceMapper datasourceMapper;
    @Mock private RptScreenMapPointMapper mapPointMapper;
    @Mock private CurrentUserApi currentUserApi;
    @Mock private ScreenScopeAuthorizationService scopeAuthorizationService;

    private ScreenConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScreenConfigServiceImpl(screenMapper, blockMapper, datasourceMapper,
                mapPointMapper, currentUserApi);
        ReflectionTestUtils.setField(service, "scopeAuthorizationService", scopeAuthorizationService);
    }

    @Test
    void catalog_usesOnlyRegisteredCodeTemplatesAndDoesNotRequirePublishedCanvas() {
        RptScreen province = screen("SCR_PROVINCE", "数据库旧名称", "PROVINCE", "COMMON", "ACTIVE", 0, null);
        RptScreen retail = screen("SCR_RETAIL_OVERVIEW", "数据库零售名称", "BRANCH", "RETAIL", "ACTIVE", 0, null);
        RptScreen oldPublished = screen("SCR_OLD_PUBLISHED", "旧发布屏", "BRANCH", "COMMON", "ACTIVE", 1,
                "{\"schemaVersion\":1,\"components\":[]}");
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(oldPublished, retail, province));
        when(scopeAuthorizationService.authorize(any(RptScreen.class))).thenReturn(Set.of());

        List<ScreenEntryRespDTO> entries = service.listAuthorizedCodeScreens();

        assertThat(entries).extracting(ScreenEntryRespDTO::getScreenCode)
                .containsExactly("SCR_PROVINCE", "SCR_RETAIL_OVERVIEW");
        assertThat(entries).extracting(ScreenEntryRespDTO::getTemplate)
                .containsExactly("branch-overview-v1", "retail-overview-v1");
        assertThat(entries).extracting(ScreenEntryRespDTO::getScreenName)
                .containsExactly("分行经营总览", "零售经营总览");
        assertThat(entries).extracting(ScreenEntryRespDTO::getBizLine)
                .containsExactly("COMMON", "RETAIL");
        assertThat(entries).extracting(ScreenEntryRespDTO::getDataMode)
                .containsExactly("TEST", "DEMO");
    }

    @Test
    void catalog_excludesInactiveUnknownAndDeniedRegisteredScreensWithoutLeakingMetadata() {
        RptScreen inactive = screen("SCR_PROVINCE", "已停用", "PROVINCE", "COMMON", "DISABLED", 0, null);
        RptScreen unknown = screen("SCR_OTHER", "不应展示的旧屏", "BRANCH", "COMMON", "ACTIVE", 0, null);
        RptScreen denied = screen("SCR_RETAIL_OVERVIEW", "不应泄漏名称", "BRANCH", "RETAIL", "ACTIVE", 0, null);
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(inactive, unknown, denied));
        when(scopeAuthorizationService.authorize(denied)).thenThrow(
                new com.bank.branch.platform.report.exception.RptException(
                        com.bank.branch.platform.report.enums.RptErrorCode.SCREEN_ACCESS_DENIED));

        assertThat(service.listAuthorizedCodeScreens()).isEmpty();
    }

    @Test
    void catalog_propagatesAuthorizationInfrastructureFailure() {
        RptScreen retail = screen("SCR_RETAIL_OVERVIEW", "零售经营总览", "BRANCH", "RETAIL", "ACTIVE", 0, null);
        when(screenMapper.selectList(any(Wrapper.class))).thenReturn(List.of(retail));
        when(scopeAuthorizationService.authorize(retail)).thenThrow(
                new com.bank.branch.platform.report.exception.RptException(
                        com.bank.branch.platform.report.enums.RptErrorCode.SCREEN_ACCESS_DENIED,
                        new IllegalStateException("role store offline")));

        assertThatThrownBy(() -> service.listAuthorizedCodeScreens())
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private RptScreen screen(String code, String name, String viewLevel, String bizLine,
                             String status, int publishStatus, String publishedJson) {
        RptScreen screen = new RptScreen();
        screen.setId((long) Math.abs(code.hashCode()));
        screen.setScreenCode(code);
        screen.setScreenName(name);
        screen.setViewLevel(viewLevel);
        screen.setBizLine(bizLine);
        screen.setOrgScopeMode("LEGACY_CONTEXT");
        screen.setStatus(status);
        screen.setPublishStatus(publishStatus);
        screen.setCanvasPublishedJson(publishedJson);
        return screen;
    }
}
