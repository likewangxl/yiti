package com.bank.branch.platform.workflow.facade;

import com.bank.branch.platform.workflow.api.TodoQueryApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import com.bank.branch.platform.workflow.service.TodoQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TodoQueryApi 默认实现：thin wrapper，委托 TodoQueryService 拿数据 + 构造 Map 形态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoQueryFacade implements TodoQueryApi {

    private final TodoQueryService todoQueryService;

    @Override
    public List<String> listMyTodoBusinessKeys(String empId, String bizType) {
        log.debug("[TodoQueryFacade.listMyTodoBusinessKeys] empId={}, bizType={}", empId, bizType);
        return todoQueryService.listMyTodoBusinessKeys(empId, bizType);
    }

    @Override
    public Map<String, TaskRespDTO> findTaskRespByBusinessKeys(String empId, List<String> businessKeys) {
        log.debug("[TodoQueryFacade.findTaskRespByBusinessKeys] empId={}, keys={}",
                empId, businessKeys == null ? 0 : businessKeys.size());
        if (empId == null || businessKeys == null || businessKeys.isEmpty()) {
            return Collections.emptyMap();
        }
        return todoQueryService.findMyTaskRespByBusinessKeys(empId, businessKeys).stream()
                .filter(d -> d.getBusinessKey() != null)
                .collect(Collectors.toMap(TaskRespDTO::getBusinessKey, d -> d, (a, b) -> a));
    }

    @Override
    public List<String> listMyDoneBusinessKeys(String empId, String bizType) {
        log.debug("[TodoQueryFacade.listMyDoneBusinessKeys] empId={}, bizType={}", empId, bizType);
        return todoQueryService.listMyDoneBusinessKeys(empId, bizType);
    }

    @Override
    public Map<String, TaskRespDTO> findDoneTaskRespByBusinessKeys(String empId, List<String> businessKeys) {
        log.debug("[TodoQueryFacade.findDoneTaskRespByBusinessKeys] empId={}, keys={}",
                empId, businessKeys == null ? 0 : businessKeys.size());
        if (empId == null || businessKeys == null || businessKeys.isEmpty()) {
            return Collections.emptyMap();
        }
        return todoQueryService.findDoneTaskRespByBusinessKeys(empId, businessKeys).stream()
                .filter(d -> d.getBusinessKey() != null)
                .collect(Collectors.toMap(TaskRespDTO::getBusinessKey, d -> d, (a, b) -> a));
    }
}
