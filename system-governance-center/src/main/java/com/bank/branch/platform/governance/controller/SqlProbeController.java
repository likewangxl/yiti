package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.AuditLogDTO;
import com.bank.branch.platform.governance.api.dto.SqlProbeReqDTO;
import com.bank.branch.platform.governance.api.dto.SqlProbeRespDTO;
import com.bank.branch.platform.governance.service.SqlProbeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     * @param req 请求体，包含 SQL 语句和备注
     * @return SQL探查结果
     */
    @PostMapping("/execute")
    @Operation(summary = "执行SQL查询")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.EXECUTE_SQL)
    public ResponseWrapper<SqlProbeRespDTO> executeSql(@Valid @RequestBody SqlProbeReqDTO req) {
        String operatorEmpId = DataScopeContext.current().getEmpId();
        log.info("[SqlProbeController.executeSql] operatorEmpId={}, sqlLength={}",
                operatorEmpId, req.getSql().length());
        SqlProbeRespDTO result = sqlProbeService.executeSql(req, operatorEmpId);
        return ResponseWrapper.success(result);
    }

    /**
     * 查询当前用户的SQL探针执行历史
     *
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 20
     * @return 分页结果
     */
    @GetMapping("/history")
    @Operation(summary = "查询执行历史")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<AuditLogDTO> listHistory(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        // 从上下文获取当前用户ID，查询自己的历史
        String empId = DataScopeContext.current().getEmpId();
        log.debug("[SqlProbeController.listHistory] empId={}, pageNo={}, pageSize={}",
                empId, pageNo, pageSize);
        PageResult<AuditLogDTO> result = sqlProbeService.listHistory(empId, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }
}
