package com.bank.branch.platform.auth.security.matcher;

import com.bank.branch.platform.auth.entity.PtResource;
import com.bank.branch.platform.auth.service.PermissionCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceMatcherTest {

    @Mock PermissionCacheService cacheService;
    @InjectMocks ResourceMatcher resourceMatcher;

    @Test
    void match_exactUrl_returnsResource() {
        when(cacheService.getAllResources()).thenReturn(List.of(makeResource("RES_01", "/api/roles", "GET")));

        Optional<PtResource> result = resourceMatcher.match("/api/roles", "GET");

        assertThat(result).isPresent();
        assertThat(result.get().getResourceId()).isEqualTo("RES_01");
    }

    @Test
    void match_antPattern_matchesVariableSegment() {
        when(cacheService.getAllResources()).thenReturn(List.of(makeResource("RES_02", "/api/users/{id}", "GET")));

        Optional<PtResource> result = resourceMatcher.match("/api/users/E001", "GET");

        assertThat(result).isPresent();
        assertThat(result.get().getResourceId()).isEqualTo("RES_02");
    }

    @Test
    void match_wildcardPattern_matchesSubPaths() {
        when(cacheService.getAllResources()).thenReturn(List.of(makeResource("RES_03", "/api/admin/**", "POST")));

        Optional<PtResource> result = resourceMatcher.match("/api/admin/roles/bind", "POST");

        assertThat(result).isPresent();
    }

    @Test
    void match_methodWildcard_matchesAnyMethod() {
        when(cacheService.getAllResources()).thenReturn(List.of(makeResource("RES_04", "/api/health", "*")));

        assertThat(resourceMatcher.match("/api/health", "GET")).isPresent();
        assertThat(resourceMatcher.match("/api/health", "POST")).isPresent();
    }

    @Test
    void match_methodMismatch_returnsEmpty() {
        when(cacheService.getAllResources()).thenReturn(List.of(makeResource("RES_05", "/api/roles", "GET")));

        Optional<PtResource> result = resourceMatcher.match("/api/roles", "POST");

        assertThat(result).isEmpty();
    }

    @Test
    void match_noMatchingUrl_returnsEmpty() {
        when(cacheService.getAllResources()).thenReturn(List.of(makeResource("RES_06", "/api/roles", "GET")));

        Optional<PtResource> result = resourceMatcher.match("/api/other", "GET");

        assertThat(result).isEmpty();
    }

    @Test
    void match_disabledResource_isSkipped() {
        PtResource disabled = makeResource("RES_07", "/api/roles", "GET");
        disabled.setStatus(1); // 1 = disabled
        when(cacheService.getAllResources()).thenReturn(List.of(disabled));

        Optional<PtResource> result = resourceMatcher.match("/api/roles", "GET");

        assertThat(result).isEmpty();
    }

    private PtResource makeResource(String id, String url, String method) {
        PtResource r = new PtResource();
        r.setResourceId(id);
        r.setResourceUrl(url);
        r.setResourceMethod(method);
        r.setStatus(0); // enabled
        return r;
    }
}
