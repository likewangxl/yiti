package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.ResourceTreeNodeDTO;
import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.mapper.ResourceMapper;
import com.bank.branch.platform.auth.mapper.RoleResourceMapper;
import com.bank.branch.platform.common.web.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock ResourceMapper resourceMapper;
    @Mock RoleResourceMapper roleResourceMapper;
    @Mock PermissionCacheService cacheService;
    @InjectMocks ResourceService resourceService;

    @Test
    void getById_shouldThrowWhenNotFound() {
        when(resourceMapper.selectByResourceId("NONE")).thenReturn(null);
        assertThatThrownBy(() -> resourceService.getById("NONE"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40402"));
    }

    @Test
    void createResource_shouldThrowOnDuplicate() {
        when(resourceMapper.selectByUrlAndMethod("/api/test", "GET", "PLATFORM"))
            .thenReturn(new PtResource());
        assertThatThrownBy(() -> resourceService.createResource("/api/test", "GET", "测试资源", 1, null, 0, null, "PLATFORM"))
            .isInstanceOf(BizException.class)
            .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo("AUTH-40902"));
    }

    @Test
    void createResource_shouldInsertAndEvictCache() {
        when(resourceMapper.selectByUrlAndMethod("/api/new", "POST", "PLATFORM")).thenReturn(null);
        when(resourceMapper.insert(any())).thenReturn(1);
        ResourceTreeNodeDTO dto = resourceService.createResource("/api/new", "POST", "新资源", 1, null, 0, null, "PLATFORM");
        assertThat(dto.getResourceUrl()).isEqualTo("/api/new");
        verify(cacheService).evictAllResourceCache();
    }

    @Test
    void deleteResource_shouldThrowWhenHasChildren() {
        PtResource r = makeResource("RES_001", "/api/parent", "GET");
        when(resourceMapper.selectByResourceId("RES_001")).thenReturn(r);
        when(resourceMapper.countChildren("RES_001")).thenReturn(2L);
        assertThatThrownBy(() -> resourceService.deleteResource("RES_001", "删除原因"))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("子资源");
    }

    @Test
    void deleteResource_shouldSetStatusOneAndCleanBindings() {
        PtResource r = makeResource("RES_001", "/api/leaf", "GET");
        when(resourceMapper.selectByResourceId("RES_001")).thenReturn(r);
        when(resourceMapper.countChildren("RES_001")).thenReturn(0L);
        when(resourceMapper.updateById(any())).thenReturn(1);
        resourceService.deleteResource("RES_001", "删除原因");
        verify(resourceMapper).updateById(argThat(res -> res.getStatus() == 1));
        verify(roleResourceMapper).deleteByResourceId("RES_001");
        verify(cacheService).evictAllResourceCache();
    }

    @Test
    void getResourceTree_shouldBuildHierarchy() {
        PtResource parent = makeResource("RES_P", "/api/module", "*");
        parent.setIsMenu(0);
        parent.setParentResourceId(null);

        PtResource child = makeResource("RES_C", "/api/module/action", "POST");
        child.setIsMenu(1);
        child.setParentResourceId("RES_P");

        when(resourceMapper.selectAll(any(), any())).thenReturn(List.of(parent, child));
        List<ResourceTreeNodeDTO> tree = resourceService.getResourceTree(null, null);
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getResourceId()).isEqualTo("RES_C");
    }

    private PtResource makeResource(String id, String url, String method) {
        PtResource r = new PtResource();
        r.setResourceId(id);
        r.setResourceUrl(url);
        r.setResourceMethod(method);
        r.setMenuName("测试资源");
        r.setStatus(0);
        r.setSysCode("PLATFORM");
        r.setIsMenu(1);
        r.setMenuEndflag("1");
        return r;
    }
}
