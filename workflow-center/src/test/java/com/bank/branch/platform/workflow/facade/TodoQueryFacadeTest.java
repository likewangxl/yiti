package com.bank.branch.platform.workflow.facade;

import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import com.bank.branch.platform.workflow.service.TodoQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * TodoQueryFacade 单测：验证 thin wrapper 委托 + Map 构造逻辑。
 * Service 内部 Flowable 查询逻辑由集成测试覆盖，这里只测形态转换。
 */
@ExtendWith(MockitoExtension.class)
class TodoQueryFacadeTest {

    @Mock TodoQueryService todoQueryService;

    @InjectMocks TodoQueryFacade facade;

    @Test
    void w1_listMyTodoBusinessKeys_delegatesToService() {
        when(todoQueryService.listMyTodoBusinessKeys("E001", "ALLOC_ADJUST"))
                .thenReturn(Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2"));

        List<String> keys = facade.listMyTodoBusinessKeys("E001", "ALLOC_ADJUST");

        assertThat(keys).containsExactly("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2");
    }

    @Test
    void w2_findTaskRespByBusinessKeys_buildsMapKeyedByBusinessKey_partialMatch() {
        TaskRespDTO dto1 = new TaskRespDTO();
        dto1.setTaskId("T1");
        dto1.setBusinessKey("ALLOC_ADJUST:A1");
        TaskRespDTO dto2 = new TaskRespDTO();
        dto2.setTaskId("T2");
        dto2.setBusinessKey("ALLOC_ADJUST:A2");
        when(todoQueryService.findMyTaskRespByBusinessKeys("E001",
                Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2", "ALLOC_ADJUST:A3")))
                .thenReturn(Arrays.asList(dto1, dto2));

        Map<String, TaskRespDTO> map = facade.findTaskRespByBusinessKeys("E001",
                Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2", "ALLOC_ADJUST:A3"));

        assertThat(map).hasSize(2);
        assertThat(map.get("ALLOC_ADJUST:A1").getTaskId()).isEqualTo("T1");
        assertThat(map.get("ALLOC_ADJUST:A2").getTaskId()).isEqualTo("T2");
        assertThat(map).doesNotContainKey("ALLOC_ADJUST:A3");
    }

    @Test
    void w3_emptyInputs_returnEmpty() {
        assertThat(facade.findTaskRespByBusinessKeys(null, Arrays.asList("k1"))).isEmpty();
        assertThat(facade.findTaskRespByBusinessKeys("E001", null)).isEmpty();
        assertThat(facade.findTaskRespByBusinessKeys("E001", Collections.emptyList())).isEmpty();
    }

    @Test
    void w4_listMyDoneBusinessKeys_delegatesToService() {
        when(todoQueryService.listMyDoneBusinessKeys("E001", "ALLOC_ADJUST"))
                .thenReturn(Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2"));

        List<String> keys = facade.listMyDoneBusinessKeys("E001", "ALLOC_ADJUST");

        assertThat(keys).containsExactly("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2");
    }

    @Test
    void w5_findDoneTaskRespByBusinessKeys_buildsMap() {
        TaskRespDTO dto1 = new TaskRespDTO();
        dto1.setTaskId("T1");
        dto1.setBusinessKey("ALLOC_ADJUST:A1");
        when(todoQueryService.findDoneTaskRespByBusinessKeys("E001",
                Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2")))
                .thenReturn(Arrays.asList(dto1));

        Map<String, TaskRespDTO> map = facade.findDoneTaskRespByBusinessKeys("E001",
                Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2"));

        assertThat(map).hasSize(1);
        assertThat(map.get("ALLOC_ADJUST:A1").getTaskId()).isEqualTo("T1");
        assertThat(map).doesNotContainKey("ALLOC_ADJUST:A2");
    }
}
