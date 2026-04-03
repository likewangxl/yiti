package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.entity.SysDict;
import com.bank.branch.platform.governance.mapper.DictMapper;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 字典服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class DictServiceTest {

    @Mock
    RedisTemplate<String, Object> redisTemplate;
    @Mock
    ValueOperations<String, Object> valueOperations;
    @Mock
    DictMapper dictMapper;
    @InjectMocks
    DictService dictService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * 测试缓存命中时直接返回缓存数据，不查询数据库
     */
    @Test
    void getDictItems_cacheHit_returnsCachedItems() {
        List<SysDict> cached = List.of(makeDict("D_001", "INDUSTRY", "IT", "信息技术", "IT"));
        when(valueOperations.get("gov:dict:INDUSTRY")).thenReturn(cached);

        List<SysDict> result = dictService.getDictItems("INDUSTRY");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDictCode()).isEqualTo("IT");
        verify(dictMapper, never()).selectByDictType(anyString());
    }

    /**
     * 测试缓存未命中时从数据库加载并写入缓存
     */
    @Test
    void getDictItems_cacheMiss_loadsFromDb() {
        when(valueOperations.get("gov:dict:INDUSTRY")).thenReturn(null);
        List<SysDict> dbItems = List.of(makeDict("D_001", "INDUSTRY", "IT", "信息技术", "IT"));
        when(dictMapper.selectByDictType("INDUSTRY")).thenReturn(dbItems);

        List<SysDict> result = dictService.getDictItems("INDUSTRY");

        assertThat(result).hasSize(1);
        verify(valueOperations).set(eq("gov:dict:INDUSTRY"), eq(dbItems), any());
    }

    /**
     * 测试根据字典类型和编码获取标签
     */
    @Test
    void getDictLabel_returnsLabelForExistingCode() {
        List<SysDict> cached = List.of(
                makeDict("D_001", "INDUSTRY", "IT", "信息技术", "IT"),
                makeDict("D_002", "INDUSTRY", "FIN", "金融", "FIN")
        );
        when(valueOperations.get("gov:dict:INDUSTRY")).thenReturn(cached);

        String label = dictService.getDictLabel("INDUSTRY", "FIN");

        assertThat(label).isEqualTo("金融");
    }

    /**
     * 测试创建字典时编码重复抛出 GOV-40901
     */
    @Test
    void createDict_duplicateCode_throwsGov40901() {
        when(dictMapper.existsByDictTypeAndDictCode("INDUSTRY", "IT")).thenReturn(true);

        assertThatThrownBy(() -> dictService.createDict("INDUSTRY", "IT", "信息技术", "IT", 1, null))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40901"));
    }

    /**
     * 测试创建字典成功后插入数据库并清除缓存
     */
    @Test
    void createDict_success_insertsAndEvictsCache() {
        when(dictMapper.existsByDictTypeAndDictCode("INDUSTRY", "IT")).thenReturn(false);
        when(dictMapper.insert(any())).thenReturn(1);

        SysDict result = dictService.createDict("INDUSTRY", "IT", "信息技术", "IT", 1, "行业类型");

        assertThat(result).isNotNull();
        assertThat(result.getDictType()).isEqualTo("INDUSTRY");
        assertThat(result.getDictCode()).isEqualTo("IT");
        verify(dictMapper).insert(any(SysDict.class));
        verify(redisTemplate).delete("gov:dict:INDUSTRY");
    }

    /**
     * 测试删除字典时将状态设为 DISABLED 并清除缓存
     */
    @Test
    void deleteDict_setsStatusToDisabled() {
        SysDict existing = makeDict("D_001", "INDUSTRY", "IT", "信息技术", "IT");
        when(dictMapper.selectById("D_001")).thenReturn(existing);
        when(dictMapper.updateById(any())).thenReturn(1);

        dictService.deleteDict("D_001");

        verify(dictMapper).updateById(argThat(dict -> "DISABLED".equals(dict.getStatus())));
        verify(redisTemplate).delete("gov:dict:INDUSTRY");
    }

    /**
     * 测试分页查询返回 PageResult
     */
    @Test
    void listByPage_returnsPageResult() {
        List<SysDict> records = List.of(makeDict("D_001", "INDUSTRY", "IT", "信息技术", "IT"));
        when(dictMapper.countByPage("INDUSTRY", null)).thenReturn(1L);
        when(dictMapper.selectByPage(eq("INDUSTRY"), isNull(), eq(0), eq(20))).thenReturn(records);

        PageResult<SysDict> page = dictService.listByPage("INDUSTRY", null, 1, 20);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getPageNo()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(20);
    }

    // ── L1 补全测试 ──────────────────────────────────────────────

    /**
     * 测试 getDictLabel：编码不存在时返回 null
     */
    @Test
    void getDictLabel_unknownCode_returnsNull() {
        List<SysDict> cached = List.of(makeDict("D_001", "INDUSTRY", "IT", "信息技术", "IT"));
        when(valueOperations.get("gov:dict:INDUSTRY")).thenReturn(cached);

        String label = dictService.getDictLabel("INDUSTRY", "NON_EXIST");

        assertThat(label).isNull();
    }

    /**
     * 测试 isValidDictValue：编码存在时返回 true
     */
    @Test
    void isValidDictValue_existingCode_returnsTrue() {
        List<SysDict> cached = List.of(makeDict("D_001", "INDUSTRY", "IT", "信息技术", "IT"));
        when(valueOperations.get("gov:dict:INDUSTRY")).thenReturn(cached);

        boolean valid = dictService.isValidDictValue("INDUSTRY", "IT");

        assertThat(valid).isTrue();
    }

    /**
     * 测试 isValidDictValue：编码不存在时返回 false
     */
    @Test
    void isValidDictValue_nonExistingCode_returnsFalse() {
        List<SysDict> cached = List.of(makeDict("D_001", "INDUSTRY", "IT", "信息技术", "IT"));
        when(valueOperations.get("gov:dict:INDUSTRY")).thenReturn(cached);

        boolean valid = dictService.isValidDictValue("INDUSTRY", "UNKNOWN");

        assertThat(valid).isFalse();
    }

    /**
     * 测试 batchGetDictItems：批量获取多类型字典数据
     */
    @Test
    void batchGetDictItems_mergesMultipleTypes() {
        List<SysDict> industryItems = List.of(makeDict("D_001", "INDUSTRY", "IT", "信息技术", "IT"));
        List<SysDict> statusItems = List.of(makeDict("D_002", "STATUS", "ACT", "激活", "ACTIVE"));
        when(valueOperations.get("gov:dict:INDUSTRY")).thenReturn(industryItems);
        when(valueOperations.get("gov:dict:STATUS")).thenReturn(statusItems);

        var result = dictService.batchGetDictItems(Set.of("INDUSTRY", "STATUS"));

        assertThat(result).hasSize(2);
        assertThat(result.get("INDUSTRY")).hasSize(1);
        assertThat(result.get("STATUS")).hasSize(1);
    }

    /**
     * 测试 updateDict：正常更新成功
     */
    @Test
    void updateDict_success_updatesAndEvictsCache() {
        SysDict existing = makeDict("D_001", "INDUSTRY", "IT", "信息技术", "IT");
        when(dictMapper.selectById("D_001")).thenReturn(existing);
        when(dictMapper.updateById(any())).thenReturn(1);

        dictService.updateDict("D_001", "新标签", "新值", 5, "更新备注");

        verify(dictMapper).updateById(argThat(dict ->
                "新标签".equals(dict.getDictLabel())
                        && "新值".equals(dict.getDictValue())
                        && dict.getSortOrder() == 5
        ));
        verify(redisTemplate).delete("gov:dict:INDUSTRY");
    }

    /**
     * 测试 updateDict：字典不存在时抛出 GOV-40001
     */
    @Test
    void updateDict_notFound_throwsGov40001() {
        when(dictMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> dictService.updateDict("NOT_EXIST", "标签", null, null, null))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40001"));
    }

    /**
     * 测试 toggleStatus：ACTIVE → DISABLED
     */
    @Test
    void toggleStatus_activeToDisabled() {
        SysDict existing = makeDict("D_001", "INDUSTRY", "IT", "信息技术", "IT");
        existing.setStatus("ACTIVE");
        when(dictMapper.selectById("D_001")).thenReturn(existing);
        when(dictMapper.updateById(any())).thenReturn(1);

        SysDict result = dictService.toggleStatus("D_001");

        verify(dictMapper).updateById(argThat(dict -> "DISABLED".equals(dict.getStatus())));
        verify(redisTemplate).delete("gov:dict:INDUSTRY");
    }

    /**
     * 测试 toggleStatus：DISABLED → ACTIVE
     */
    @Test
    void toggleStatus_disabledToActive() {
        SysDict existing = makeDict("D_001", "INDUSTRY", "IT", "信息技术", "IT");
        existing.setStatus("DISABLED");
        when(dictMapper.selectById("D_001")).thenReturn(existing);
        when(dictMapper.updateById(any())).thenReturn(1);

        SysDict result = dictService.toggleStatus("D_001");

        verify(dictMapper).updateById(argThat(dict -> "ACTIVE".equals(dict.getStatus())));
    }

    /**
     * 测试 toggleStatus：字典不存在时抛出 GOV-40001
     */
    @Test
    void toggleStatus_notFound_throwsGov40001() {
        when(dictMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> dictService.toggleStatus("NOT_EXIST"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40001"));
    }

    /**
     * 测试 deleteDict：字典不存在时抛出 GOV-40001
     */
    @Test
    void deleteDict_notFound_throwsGov40001() {
        when(dictMapper.selectById("NOT_EXIST")).thenReturn(null);

        assertThatThrownBy(() -> dictService.deleteDict("NOT_EXIST"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("GOV-40001"));
    }

    /**
     * 测试 getDictItems：DB 返回空列表时应写入空缓存
     */
    @Test
    void getDictItems_emptyDb_returnsEmptyList() {
        when(valueOperations.get("gov:dict:EMPTY_TYPE")).thenReturn(null);
        when(dictMapper.selectByDictType("EMPTY_TYPE")).thenReturn(List.of());

        List<SysDict> result = dictService.getDictItems("EMPTY_TYPE");

        assertThat(result).isEmpty();
    }

    private SysDict makeDict(String id, String dictType, String dictCode, String dictLabel, String dictValue) {
        SysDict dict = new SysDict();
        dict.setId(id);
        dict.setDictType(dictType);
        dict.setDictCode(dictCode);
        dict.setDictLabel(dictLabel);
        dict.setDictValue(dictValue);
        dict.setSortOrder(0);
        dict.setStatus("ACTIVE");
        dict.setCreatedTime(LocalDateTime.now());
        dict.setUpdatedTime(LocalDateTime.now());
        return dict;
    }
}
