package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricRef;
import com.bank.branch.platform.performance.mapper.PerfMetricRefMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetricRefService 单元测试.
 */
@ExtendWith(MockitoExtension.class)
class MetricRefServiceTest {

    @Mock
    private PerfMetricRefMapper mapper;

    @InjectMocks
    private MetricRefService service;

    @Test
    @DisplayName("setRefs 会先清旧引用，再批量插入新引用")
    void setRefs_clearsOldAndInsertsNew() {
        service.setRefs("METRIC_A", List.of("REF_1", "REF_2"));

        verify(mapper).deleteByMetricCode("METRIC_A");
        ArgumentCaptor<List<PerfMetricRef>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).extracting(PerfMetricRef::getRefMetricCode)
                .containsExactly("REF_1", "REF_2");
    }

    @Test
    @DisplayName("空引用列表时只删不插")
    void setRefs_whenEmpty_onlyDeletes() {
        service.setRefs("METRIC_A", List.of());

        verify(mapper).deleteByMetricCode("METRIC_A");
        verify(mapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("loadFullGraph 按上层指标分组返回完整图")
    void loadFullGraph_groupsByMetricCode() {
        PerfMetricRef first = new PerfMetricRef();
        first.setId("1");
        first.setMetricCode("METRIC_A");
        first.setRefMetricCode("REF_1");
        first.setCreatedTime(LocalDateTime.now());

        PerfMetricRef second = new PerfMetricRef();
        second.setId("2");
        second.setMetricCode("METRIC_A");
        second.setRefMetricCode("REF_2");
        second.setCreatedTime(LocalDateTime.now());

        PerfMetricRef third = new PerfMetricRef();
        third.setId("3");
        third.setMetricCode("METRIC_B");
        third.setRefMetricCode("REF_3");
        third.setCreatedTime(LocalDateTime.now());

        when(mapper.selectAll()).thenReturn(List.of(first, second, third));

        Map<String, List<String>> graph = service.loadFullGraph();

        assertThat(graph).containsOnlyKeys("METRIC_A", "METRIC_B");
        assertThat(graph.get("METRIC_A")).containsExactly("REF_1", "REF_2");
        assertThat(graph.get("METRIC_B")).containsExactly("REF_3");
    }
}
