package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.AuditLogDTO;
import com.bank.branch.platform.governance.service.SqlProbeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * SQL探针控制器
 * 提供受限的SQL查询执行和执行历史查询接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/sql-probe")
@Tag(name = "SQL探针", description = "受限SQL查询执行与历史记录")
public class SqlProbeController {

    private final SqlProbeService sqlProbeService;

    /**
     * 执行SQL查询（仅SELECT）
     *
     * @param sql           待执行的SQL语句
     * @param operatorEmpId 操作人工号
     * @param reason        执行原因
     * @return 查询结果列表
     */
    @PostMapping("/execute")
    @Operation(summary = "执行SQL查询")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.EXECUTE_SQL)
    public ResponseWrapper<List<Map<String, Object>>> executeSql(
            @RequestParam(value = "sql") String sql,
            @RequestParam(value = "operatorEmpId") String operatorEmpId,
            @RequestParam(value = "reason") String reason) {
        log.info("[SqlProbeController.executeSql] operatorEmpId={}, reason={}", operatorEmpId, reason);
        List<Map<String, Object>> results = sqlProbeService.executeSql(sql, operatorEmpId, reason);
        return ResponseWrapper.success(results);
    }

    /**
     * 查询SQL探针执行历史
     *
     * @param operatorEmpId 操作人工号
     * @param pageNo        页码，默认 1
     * @param pageSize      每页条数，默认 20
     * @return 分页结果
     */
    @GetMapping("/history")
    @Operation(summary = "查询执行历史")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<AuditLogDTO> listHistory(
            @RequestParam(value = "operatorEmpId") String operatorEmpId,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[SqlProbeController.listHistory] operatorEmpId={}, pageNo={}, pageSize={}",
                operatorEmpId, pageNo, pageSize);
        PageResult<AuditLogDTO> result = sqlProbeService.listHistory(operatorEmpId, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }
}
