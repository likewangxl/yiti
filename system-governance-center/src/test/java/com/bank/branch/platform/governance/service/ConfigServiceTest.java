package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.ConfigDTO;
import com.bank.branch.platform.governance.config.MemoryCacheService;
import com.bank.branch.platform.governance.entity.SysConfigKv;
import com.bank.branch.platform.governance.mapper.ConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 配置服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    @Mock
    MemoryCacheService memoryCacheService;
    @Mock
    ConfigMapper configMapper;
    @Mock
    ObjectMapper objectMapper;
    @InjectMocks
    ConfigService configService;

    @Test
    void getConfigValue_cacheHit_returnsCachedValue() {
        when(memoryCacheService.get("gov:config:app.name")).thenReturn("BranchPlatform");

        String result = configService.getConfigValue("app.name");

        assertThat(result).isEqualTo("BranchPlatform");
        verify(configMapper, never()).selectByConfigKey(anyString());
    }

    @Test
    void getConfigValue_cacheMiss_loadsFromDb() {
        when(memoryCacheService.get("gov:config:app.name")).thenReturn(null);
        SysConfigKv config = makeConfig("C_001", "app.name", "BranchPlatform", "STRING");
        when(configMapper.selectByConfigKey("app.name")).thenReturn(config);

        String result = configService.getConfigValue("app.name");

        assertThat(result).isEqualTo("BranchPlatform");
        verify(memoryCacheService).put(eq("gov:config:app.name"), eq("BranchPlatform"), any());
    }

    @Test
    void getConfigValue_withDefault_returnsDefaultWhenMissing() {
        when(memoryCacheService.get("gov:config:missing.key")).thenReturn(null);
        when(configMapper.selectByConfigKey("missing.key")).thenReturn(null);

        String result = configService.getConfigValue("missing.key", "defaultVal");

        assertThat(result).isEqualTo("defaultVal");
    }

    @Test
    void getConfigValue_numberType_convertsToLong() {
        when(memoryCacheService.get("gov:config:max.retry")).thenReturn(null);
        SysConfigKv config = makeConfig("C_002", "max.retry", "42", "NUMBER");
        when(configMapper.selectByConfigKey("max.retry")).thenReturn(config);

        Long result = configService.getConfigValue("max.retry", Long.class);

        assertThat(result).isEqualTo(42L);
    }

    @Test
    void getConfigValue_boolType_convertsToBoolean() {
        when(memoryCacheService.get("gov:config:feature.enabled")).thenReturn(null);
        SysConfigKv config = makeConfig("C_003", "feature.enabled", "true", "BOOL");
        when(configMapper.selectByConfigKey("feature.enabled")).thenReturn(config);

        Boolean result = configService.getConfigValue("feature.enabled", Boolean.class);

        assertThat(result).isTrue();
    }

    @Test
    void updateConfig_evictsCache() {
        SysConfigKv config = makeConfig("C_001", "app.name", "OldValue", "STRING");
        when(configMapper.selectByConfigKey("app.name")).thenReturn(config);
        when(configMapper.updateById((SysConfigKv) any())).thenReturn(1);

        configService.updateConfig("app.name", "NewValue", "test reason");

        verify(configMapper).updateById(argThat((SysConfigKv c) -> "NewValue".equals(c.getConfigValue())));
        verify(memoryCacheService).evict("gov:config:app.name");
    }

    @Test
    void updateConfig_keyNotFound_throwsGov40002() {
        when(configMapper.selectByConfigKey("nonexistent.key")).thenReturn(null);

        assertThatThrownBy(() -> configService.updateConfig("nonexistent.key", "value", "test reason"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40002"));
    }

    @Test
    void listConfigs_returnsPageResult() {
        List<SysConfigKv> records = List.of(makeConfig("C_001", "app.name", "BP", "STRING"));
        when(configMapper.countAll(null)).thenReturn(1L);
        when(configMapper.selectAll(isNull(), eq(0), eq(20))).thenReturn(records);

        PageResult<ConfigDTO> page = configService.listConfigs(null, 1, 20);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getConfigKey()).isEqualTo("app.name");
    }

    @Test
    void listConfigs_withStatusFilter_delegatesToMapper() {
        when(configMapper.countAll("ACTIVE")).thenReturn(0L);
        when(configMapper.selectAll(eq("ACTIVE"), eq(0), eq(10))).thenReturn(List.of());

        PageResult<ConfigDTO> page = configService.listConfigs("ACTIVE", 1, 10);

        assertThat(page.getTotal()).isEqualTo(0L);
        assertThat(page.getRecords()).isEmpty();
        verify(configMapper).countAll("ACTIVE");
    }

    @Test
    void getConfigValue_typedMethod_keyNotFound_throwsGov40002() {
        when(memoryCacheService.get("gov:config:missing")).thenReturn(null);
        when(configMapper.selectByConfigKey("missing")).thenReturn(null);

        assertThatThrownBy(() -> configService.getConfigValue("missing", Long.class))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40002"));
    }

    @Test
    void getConfigValue_dbMissAndNoDefault_returnsNull() {
        when(memoryCacheService.get("gov:config:missing")).thenReturn(null);
        when(configMapper.selectByConfigKey("missing")).thenReturn(null);

        String result = configService.getConfigValue("missing");

        assertThat(result).isNull();
    }

    // ── 辅助方法 ──────────────────────────────────────────────────

    private SysConfigKv makeConfig(String id, String configKey, String configValue, String valueType) {
        SysConfigKv config = new SysConfigKv();
        config.setId(id);
        config.setConfigKey(configKey);
        config.setConfigValue(configValue);
        config.setValueType(valueType);
        config.setStatus("ACTIVE");
        config.setCreatedTime(LocalDateTime.now());
        config.setUpdatedTime(LocalDateTime.now());
        return config;
    }
}
