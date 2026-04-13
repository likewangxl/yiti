package com.bank.branch.platform.portal.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.portal.api.dto.ShortcutDTO;
import com.bank.branch.platform.portal.controller.dto.shortcut.ShortcutItemDTO;
import com.bank.branch.platform.portal.controller.dto.shortcut.ShortcutSaveReqDTO;
import com.bank.branch.platform.portal.entity.PortalShortcut;
import com.bank.branch.platform.portal.mapper.PortalShortcutMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * ShortcutService 单元测试
 * <p>TDD RED-GREEN 闭环：先写测试，再实现 Service</p>
 */
@ExtendWith(MockitoExtension.class)
class ShortcutServiceTest {

    @Mock
    PortalShortcutMapper shortcutMapper;

    @Mock
    CurrentUserApi currentUserApi;

    @InjectMocks
    ShortcutService shortcutService;

    @Captor
    ArgumentCaptor<List<PortalShortcut>> batchCaptor;

    // ────────── 辅助方法 ──────────

    private PortalShortcut buildShortcut(String id, String name, String type) {
        PortalShortcut s = new PortalShortcut();
        s.setId(id);
        s.setShortcutName(name);
        s.setShortcutUrl("https://" + id + ".bank.com");
        s.setShortcutIcon("icon-" + id);
        s.setShortcutType(type);
        s.setTargetType("INTERNAL");
        s.setSortOrder(1);
        return s;
    }

    // ────────── listShortcuts(empId, shortcutType) 测试 ──────────

    /**
     * listShortcuts(empId, null) 即 ALL 模式：应返回 SYSTEM + 当前用户 CUSTOM
     */
    @Test
    void listShortcuts_returnsSystemAndCustomWhenTypeNull() {
        // given
        PortalShortcut sys = buildShortcut("sys-001", "核心系统", "SYSTEM");
        PortalShortcut cust = buildShortcut("cust-001", "我的常用", "CUSTOM");
        when(shortcutMapper.listByEmpIdOrSystem("E10001"))
                .thenReturn(Arrays.asList(sys, cust));

        // when
        List<PortalShortcut> result = shortcutService.listShortcuts("E10001", null);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getShortcutType()).isEqualTo("SYSTEM");
        assertThat(result.get(1).getShortcutType()).isEqualTo("CUSTOM");
        verify(shortcutMapper).listByEmpIdOrSystem("E10001");
    }

    /**
     * listShortcuts(empId, "ALL") 模式：应返回 SYSTEM + CUSTOM
     */
    @Test
    void listShortcuts_returnsSystemAndCustomWhenTypeAll() {
        // given
        PortalShortcut sys = buildShortcut("sys-001", "核心系统", "SYSTEM");
        when(shortcutMapper.listByEmpIdOrSystem("E10001"))
                .thenReturn(Collections.singletonList(sys));

        // when
        List<PortalShortcut> result = shortcutService.listShortcuts("E10001", "ALL");

        // then
        assertThat(result).hasSize(1);
        verify(shortcutMapper).listByEmpIdOrSystem("E10001");
    }

    /**
     * listShortcuts(empId, "SYSTEM") 模式：仅返回 SYSTEM 类型
     */
    @Test
    void listShortcuts_systemOnly() {
        // given
        PortalShortcut sys = buildShortcut("sys-001", "核心系统", "SYSTEM");
        when(shortcutMapper.listSystemOnly())
                .thenReturn(Collections.singletonList(sys));

        // when
        List<PortalShortcut> result = shortcutService.listShortcuts("E10001", "SYSTEM");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getShortcutType()).isEqualTo("SYSTEM");
        verify(shortcutMapper).listSystemOnly();
        verify(shortcutMapper, never()).listByEmpIdOrSystem(any());
    }

    /**
     * listShortcuts(empId, "CUSTOM") 模式：仅返回该用户的 CUSTOM 快捷入口
     */
    @Test
    void listShortcuts_customOnly() {
        // given
        PortalShortcut cust = buildShortcut("cust-001", "我的常用", "CUSTOM");
        when(shortcutMapper.listByEmpId("E10001"))
                .thenReturn(Collections.singletonList(cust));

        // when
        List<PortalShortcut> result = shortcutService.listShortcuts("E10001", "CUSTOM");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getShortcutType()).isEqualTo("CUSTOM");
        verify(shortcutMapper).listByEmpId("E10001");
        verify(shortcutMapper, never()).listByEmpIdOrSystem(any());
    }

    // ────────── listMyShortcuts 兼容测试（委托给 listShortcuts） ──────────

    /**
     * listMyShortcuts 应返回 SYSTEM + 当前用户 CUSTOM 快捷入口合并列表
     */
    @Test
    void listMyShortcutsShouldReturnSystemAndOwnCustomMerged() {
        // given
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");

        PortalShortcut systemShortcut = buildShortcut("sys-001", "核心系统", "SYSTEM");
        PortalShortcut customShortcut = buildShortcut("cust-001", "我的常用", "CUSTOM");
        customShortcut.setTargetType("EXTERNAL");

        when(shortcutMapper.listByEmpIdOrSystem("E10001"))
                .thenReturn(Arrays.asList(systemShortcut, customShortcut));

        // when
        List<ShortcutDTO> result = shortcutService.listMyShortcuts();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getShortcutName()).isEqualTo("核心系统");
        assertThat(result.get(0).getShortcutType()).isEqualTo("SYSTEM");
        assertThat(result.get(1).getShortcutName()).isEqualTo("我的常用");
        assertThat(result.get(1).getShortcutType()).isEqualTo("CUSTOM");
        verify(shortcutMapper).listByEmpIdOrSystem("E10001");
    }

    /**
     * listMyShortcuts 无数据时应返回空列表
     */
    @Test
    void listMyShortcutsShouldReturnEmptyListWhenNoShortcuts() {
        // given
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");
        when(shortcutMapper.listByEmpIdOrSystem("E10001"))
                .thenReturn(Collections.emptyList());

        // when
        List<ShortcutDTO> result = shortcutService.listMyShortcuts();

        // then
        assertThat(result).isEmpty();
    }

    // ────────── saveCustomShortcuts(empId, items) 测试 ──────────

    /**
     * saveCustomShortcuts 应先删除旧数据再批量插入新数据
     */
    @Test
    void saveCustomShortcuts_deletesOldAndInsertsNew() {
        // given
        ShortcutItemDTO item1 = new ShortcutItemDTO();
        item1.setShortcutName("常用入口1");
        item1.setShortcutUrl("https://url1.bank.com");
        item1.setShortcutIcon("icon-1");
        item1.setTargetType("INTERNAL");
        item1.setSortOrder(1);

        ShortcutItemDTO item2 = new ShortcutItemDTO();
        item2.setShortcutName("常用入口2");
        item2.setShortcutUrl("https://url2.bank.com");
        item2.setShortcutIcon("icon-2");
        item2.setTargetType("EXTERNAL");
        item2.setSortOrder(2);

        List<ShortcutItemDTO> items = Arrays.asList(item1, item2);

        // when
        shortcutService.saveCustomShortcuts("E10001", items);

        // then
        verify(shortcutMapper).deleteCustomByEmpId("E10001");
        verify(shortcutMapper).insertBatch(batchCaptor.capture());
        List<PortalShortcut> inserted = batchCaptor.getValue();
        assertThat(inserted).hasSize(2);
        assertThat(inserted.get(0).getShortcutName()).isEqualTo("常用入口1");
        assertThat(inserted.get(1).getShortcutName()).isEqualTo("常用入口2");
    }

    /**
     * saveCustomShortcuts 应正确设置实体字段（shortcutType=CUSTOM, status=ACTIVE, empId 等）
     */
    @Test
    void saveCustomShortcuts_setsCorrectFieldsOnEntities() {
        // given
        ShortcutItemDTO item = new ShortcutItemDTO();
        item.setShortcutName("测试入口");
        item.setShortcutUrl("https://test.bank.com");
        item.setShortcutIcon("icon-test");
        item.setTargetType("INTERNAL");
        item.setSortOrder(5);

        // when
        shortcutService.saveCustomShortcuts("E20002", Collections.singletonList(item));

        // then
        verify(shortcutMapper).insertBatch(batchCaptor.capture());
        PortalShortcut entity = batchCaptor.getValue().get(0);

        assertThat(entity.getId()).isNotNull().hasSize(32); // UUID 去掉横线后 32 位
        assertThat(entity.getShortcutName()).isEqualTo("测试入口");
        assertThat(entity.getShortcutUrl()).isEqualTo("https://test.bank.com");
        assertThat(entity.getShortcutIcon()).isEqualTo("icon-test");
        assertThat(entity.getShortcutType()).isEqualTo("CUSTOM");
        assertThat(entity.getTargetType()).isEqualTo("INTERNAL");
        assertThat(entity.getEmpId()).isEqualTo("E20002");
        assertThat(entity.getSortOrder()).isEqualTo(5);
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getCreatedBy()).isEqualTo("E20002");
        assertThat(entity.getUpdatedBy()).isEqualTo("E20002");
    }

    // ────────── saveMyCustomShortcuts 兼容测试（委托给 saveCustomShortcuts） ──────────

    /**
     * saveMyCustomShortcuts 应从 currentUserApi 获取 empId 后委托
     */
    @Test
    void saveMyCustomShortcutsShouldDelegateToSaveCustomShortcuts() {
        // given
        when(currentUserApi.getCurrentEmpId()).thenReturn("E10001");

        ShortcutItemDTO item = new ShortcutItemDTO();
        item.setShortcutName("常用入口");
        item.setShortcutUrl("https://url.bank.com");
        item.setShortcutIcon("icon-1");
        item.setTargetType("INTERNAL");
        item.setSortOrder(1);

        ShortcutSaveReqDTO req = new ShortcutSaveReqDTO();
        req.setShortcuts(Collections.singletonList(item));

        // when
        shortcutService.saveMyCustomShortcuts(req);

        // then
        verify(shortcutMapper).deleteCustomByEmpId("E10001");
        verify(shortcutMapper).insertBatch(batchCaptor.capture());
        assertThat(batchCaptor.getValue()).hasSize(1);
        assertThat(batchCaptor.getValue().get(0).getEmpId()).isEqualTo("E10001");
    }
}
