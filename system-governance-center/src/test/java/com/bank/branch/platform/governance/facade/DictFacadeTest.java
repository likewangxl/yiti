package com.bank.branch.platform.governance.facade;

import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.governance.entity.SysDict;
import com.bank.branch.platform.governance.service.DictService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * DictFacade 单元测试
 * 验证 Facade 层对 DictService 的委托与实体到 DTO 的转换逻辑
 */
@ExtendWith(MockitoExtension.class)
class DictFacadeTest {

    @Mock
    private DictService dictService;

    @InjectMocks
    private DictFacade dictFacade;

    // ── 辅助方法 ──────────────────────────────────────────────────

    /**
     * 构造测试用 SysDict 实体
     */
    private SysDict buildSysDict(String id, String dictType, String dictCode,
                                 String dictLabel, String dictValue, Integer sortOrder) {
        SysDict dict = new SysDict();
        dict.setId(id);
        dict.setDictType(dictType);
        dict.setDictCode(dictCode);
        dict.setDictLabel(dictLabel);
        dict.setDictValue(dictValue);
        dict.setSortOrder(sortOrder);
        dict.setStatus("ACTIVE");
        return dict;
    }

    // ── 测试用例 ──────────────────────────────────────────────────

    @Test
    @DisplayName("getDictItems - 正常返回时应将 SysDict 转换为 DictItemDTO 列表")
    void getDictItems_returnsDTOs() {
        // given
        SysDict dict1 = buildSysDict("D_001", "INDUSTRY", "IT", "信息技术", "IT", 1);
        SysDict dict2 = buildSysDict("D_002", "INDUSTRY", "FIN", "金融", "FIN", 2);
        when(dictService.getDictItems("INDUSTRY")).thenReturn(List.of(dict1, dict2));

        // when
        List<DictItemDTO> result = dictFacade.getDictItems("INDUSTRY");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("D_001");
        assertThat(result.get(0).getDictType()).isEqualTo("INDUSTRY");
        assertThat(result.get(0).getDictCode()).isEqualTo("IT");
        assertThat(result.get(0).getDictLabel()).isEqualTo("信息技术");
        assertThat(result.get(0).getDictValue()).isEqualTo("IT");
        assertThat(result.get(0).getSortOrder()).isEqualTo(1);

        assertThat(result.get(1).getDictCode()).isEqualTo("FIN");
        verify(dictService, times(1)).getDictItems("INDUSTRY");
    }

    @Test
    @DisplayName("getDictItems - 不存在的字典类型应返回空列表而非抛异常")
    void getDictItems_unknownType_returnsEmptyList() {
        // given
        when(dictService.getDictItems("NON_EXISTENT")).thenReturn(Collections.emptyList());

        // when
        List<DictItemDTO> result = dictFacade.getDictItems("NON_EXISTENT");

        // then
        assertThat(result).isNotNull().isEmpty();
        verify(dictService).getDictItems("NON_EXISTENT");
    }

    @Test
    @DisplayName("getDictLabel - 应委托给 DictService 并返回结果")
    void getDictLabel_delegatesToService() {
        // given
        when(dictService.getDictLabel("INDUSTRY", "IT")).thenReturn("信息技术");

        // when
        String label = dictFacade.getDictLabel("INDUSTRY", "IT");

        // then
        assertThat(label).isEqualTo("信息技术");
        verify(dictService).getDictLabel("INDUSTRY", "IT");
    }

    @Test
    @DisplayName("getDictLabel - 不存在的编码应返回 dictCode 本身")
    void getDictLabel_unknownCode_returnsDictCodeItself() {
        // given — DictService 对于不存在的编码返回 null
        when(dictService.getDictLabel("INDUSTRY", "UNKNOWN")).thenReturn(null);

        // when
        String label = dictFacade.getDictLabel("INDUSTRY", "UNKNOWN");

        // then — Facade 应将 null 转换为 dictCode 本身
        assertThat(label).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("isValidDictValue - 应委托给 DictService 并返回结果")
    void isValidDictValue_delegatesToService() {
        // given
        when(dictService.isValidDictValue("INDUSTRY", "IT")).thenReturn(true);
        when(dictService.isValidDictValue("INDUSTRY", "UNKNOWN")).thenReturn(false);

        // when & then
        assertThat(dictFacade.isValidDictValue("INDUSTRY", "IT")).isTrue();
        assertThat(dictFacade.isValidDictValue("INDUSTRY", "UNKNOWN")).isFalse();
        verify(dictService).isValidDictValue("INDUSTRY", "IT");
        verify(dictService).isValidDictValue("INDUSTRY", "UNKNOWN");
    }

    @Test
    @DisplayName("batchGetDictItems - 应合并多个字典类型的结果并转换为 DTO")
    void batchGetDictItems_mergesResults() {
        // given
        SysDict industry1 = buildSysDict("D_001", "INDUSTRY", "IT", "信息技术", "IT", 1);
        SysDict custType1 = buildSysDict("D_010", "CUSTOMER_TYPE", "CORP", "企业客户", "CORP", 1);
        SysDict custType2 = buildSysDict("D_011", "CUSTOMER_TYPE", "RETAIL", "个人客户", "RETAIL", 2);

        Map<String, List<SysDict>> serviceResult = new HashMap<>();
        serviceResult.put("INDUSTRY", List.of(industry1));
        serviceResult.put("CUSTOMER_TYPE", List.of(custType1, custType2));

        Set<String> dictTypes = Set.of("INDUSTRY", "CUSTOMER_TYPE");
        when(dictService.batchGetDictItems(dictTypes)).thenReturn(serviceResult);

        // when
        Map<String, List<DictItemDTO>> result = dictFacade.batchGetDictItems(dictTypes);

        // then
        assertThat(result).containsKeys("INDUSTRY", "CUSTOMER_TYPE");
        assertThat(result.get("INDUSTRY")).hasSize(1);
        assertThat(result.get("INDUSTRY").get(0).getDictCode()).isEqualTo("IT");
        assertThat(result.get("CUSTOMER_TYPE")).hasSize(2);
        assertThat(result.get("CUSTOMER_TYPE").get(0).getDictCode()).isEqualTo("CORP");
        verify(dictService).batchGetDictItems(dictTypes);
    }

    @Test
    @DisplayName("getDictItem - 存在时应返回包含 DTO 的 Optional")
    void getDictItem_existing_returnsOptionalWithDTO() {
        // given
        SysDict dict = buildSysDict("D_001", "INDUSTRY", "IT", "信息技术", "IT", 1);
        when(dictService.getDictItems("INDUSTRY")).thenReturn(List.of(dict));

        // when
        Optional<DictItemDTO> result = dictFacade.getDictItem("INDUSTRY", "IT");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getDictCode()).isEqualTo("IT");
        assertThat(result.get().getDictLabel()).isEqualTo("信息技术");
    }

    @Test
    @DisplayName("getDictItem - 不存在时应返回 Optional.empty()")
    void getDictItem_nonExisting_returnsEmpty() {
        // given
        when(dictService.getDictItems("INDUSTRY")).thenReturn(Collections.emptyList());

        // when
        Optional<DictItemDTO> result = dictFacade.getDictItem("INDUSTRY", "UNKNOWN");

        // then
        assertThat(result).isEmpty();
    }
}
