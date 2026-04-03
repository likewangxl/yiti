package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.ConfigDTO;
import com.bank.branch.platform.governance.entity.SysConfigKv;
import com.bank.branch.platform.governance.mapper.ConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 配置服务单元测试
 * 验证 ConfigService 的缓存策略、类型转换和更新逻辑
 */
@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    @Mock
    RedisTemplate<String, Object> redisTemplate;
    @Mock
    ValueOperations<String, Object> valueOperations;
    @Mock
    ConfigMapper configMapper;
    @Mock
    ObjectMapper objectMapper;
    @InjectMocks
    ConfigService configService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * 测试缓存命中时直接返回缓存数据，不查询数据库
     */
    @Test
    void getConfigValue_cacheHit_returnsCachedValue() {
        when(valueOperations.get("gov:config:app.name")).thenReturn("BranchPlatform");

        String result = configService.getConfigValue("app.name");

        assertThat(result).isEqualTo("BranchPlatform");
        verify(configMapper, never()).selectByConfigKey(anyString());
    }

    /**
     * 测试缓存未命中时从数据库加载并写入缓存
     */
    @Test
    void getConfigValue_cacheMiss_loadsFromDb() {
        when(valueOperations.get("gov:config:app.name")).thenReturn(null);
        SysConfigKv config = makeConfig("C_001", "app.name", "BranchPlatform", "STRING");
        when(configMapper.selectByConfigKey("app.name")).thenReturn(config);

        String result = configService.getConfigValue("app.name");

        assertThat(result).isEqualTo("BranchPlatform");
        verify(valueOperations).set(eq("gov:config:app.name"), eq("BranchPlatform"), any());
    }

    /**
     * 测试带默认值的方法：key 不在数据库中时返回默认值
     */
    @Test
    void getConfigValue_withDefault_returnsDefaultWhenMissing() {
        when(valueOperations.get("gov:config:missing.key")).thenReturn(null);
        when(configMapper.selectByConfigKey("missing.key")).thenReturn(null);

        String result = configService.getConfigValue("missing.key", "defaultVal");

        assertThat(result).isEqualTo("defaultVal");
    }

    /**
     * 测试 NUMBER 类型转换为 Long
     */
    @Test
    void getConfigValue_numberType_convertsToLong() {
        when(valueOperations.get("gov:config:max.retry")).thenReturn(null);
        SysConfigKv config = makeConfig("C_002", "max.retry", "42", "NUMBER");
        when(configMapper.selectByConfigKey("max.retry")).thenReturn(config);

        Long result = configService.getConfigValue("max.retry", Long.class);

        assertThat(result).isEqualTo(42L);
    }

    /**
     * 测试 BOOL 类型转换为 Boolean
     */
    @Test
    void getConfigValue_boolType_convertsToBoolean() {
        when(valueOperations.get("gov:config:feature.enabled")).thenReturn(null);
        SysConfigKv config = makeConfig("C_003", "feature.enabled", "true", "BOOL");
        when(configMapper.selectByConfigKey("feature.enabled")).thenReturn(config);

        Boolean result = configService.getConfigValue("feature.enabled", Boolean.class);

        assertThat(result).isTrue();
    }

    /**
     * 测试更新配置后缓存被清除
     */
    @Test
    void updateConfig_evictsCache() {
        SysConfigKv config = makeConfig("C_001", "app.name", "OldValue", "STRING");
        when(configMapper.selectByConfigKey("app.name")).thenReturn(config);
        when(configMapper.updateById(any())).thenReturn(1);

        configService.updateConfig("app.name", "NewValue");

        verify(configMapper).updateById(argThat(c -> "NewValue".equals(c.getConfigValue())));
        verify(redisTemplate).delete("gov:config:app.name");
    }

    /**
     * 测试更新不存在的配置时抛出 GOV-40002
     */
    @Test
    void updateConfig_keyNotFound_throwsGov40002() {
        when(configMapper.selectByConfigKey("nonexistent.key")).thenReturn(null);

        assertThatThrownBy(() -> configService.updateConfig("nonexistent.key", "value"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40002"));
    }

    // ── L1 补全测试 ──────────────────────────────────────────────

    /**
     * 测试 listConfigs：正常返回分页结果
     */
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

    /**
     * 测试 listConfigs：带状态过滤
     */
    @Test
    void listConfigs_withStatusFilter_delegatesToMapper() {
        when(configMapper.countAll("ACTIVE")).thenReturn(0L);
        when(configMapper.selectAll(eq("ACTIVE"), eq(0), eq(10))).thenReturn(List.of());

        PageResult<ConfigDTO> page = configService.listConfigs("ACTIVE", 1, 10);

        assertThat(page.getTotal()).isEqualTo(0L);
        assertThat(page.getRecords()).isEmpty();
        verify(configMapper).countAll("ACTIVE");
    }

    /**
     * 测试 getConfigValue(key, Class)：key 不存在时抛出 GOV-40002
     */
    @Test
    void getConfigValue_typedMethod_keyNotFound_throwsGov40002() {
        when(valueOperations.get("gov:config:missing")).thenReturn(null);
        when(configMapper.selectByConfigKey("missing")).thenReturn(null);

        assertThatThrownBy(() -> configService.getConfigValue("missing", Long.class))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40002"));
    }

    /**
     * 测试 getConfigValue：DB 中不存在且无默认值时返回 null
     */
    @Test
    void getConfigValue_dbMissAndNoDefault_returnsNull() {
        when(valueOperations.get("gov:config:missing")).thenReturn(null);
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
