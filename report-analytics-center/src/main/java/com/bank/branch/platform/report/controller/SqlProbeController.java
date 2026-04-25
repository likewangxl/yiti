package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.SqlProbeExecuteReqDTO;
import com.bank.branch.platform.report.dto.resp.SchemaWhitelistRespDTO;
import com.bank.branch.platform.report.dto.resp.SqlProbeExecuteRespDTO;
import com.bank.branch.platform.report.dto.resp.SqlProbeHistoryRespDTO;
import com.bank.branch.platform.report.service.SqlProbeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SQL 探查 REST 控制器（D 章 4 接口，M4.2 / M4.3 阶段）.
 *
 * <p>所有接口都注册到 PT_RESOURCE（M4.4 V1_0_4__rpt_sql_probe_pt_resources.sql）：
 * <ul>
 *   <li>D.1 POST /api/reports/sql-probe/execute        → R_RPT_SQL_EXEC</li>
 *   <li>D.2 GET  /api/reports/sql-probe/history        → R_RPT_SQL_HIST</li>
 *   <li>D.3 GET  /api/reports/sql-probe/history/{id}   → R_RPT_SQL_HIST_DTL</li>
 *   <li>D.4 GET  /api/reports/sql-probe/schema-whitelist → R_RPT_SQL_WL</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/reports/sql-probe")
@Tag(name = "报表-SQL 探查", description = "D 章 4 接口（execute / history / detail / whitelist）")
@Validated
@RequiredArgsConstructor
public class SqlProbeController {

    private final SqlProbeService sqlProbeService;

    /**
     * D.1 执行 SQL 探查（高危：仅 R_BACK_TECH 角色 + reason 必填 + 双写审计）.
     */
    @PostMapping("/execute")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.EXECUTE_SQL)
    @Operation(summary = "D.1 执行 SQL 探查")
    public ResponseWrapper<SqlProbeExecuteRespDTO> execute(@Valid @RequestBody SqlProbeExecuteReqDTO req) {
        // bizType 走 REPORT 是 report 模块单档策略（架构守护 RptBizAuthConsistencyArchTest）；
        // 实际"高危 SQL 执行"语义通过 BizAction.EXECUTE_SQL + R_BACK_TECH 角色双层约束保证.
        return ResponseWrapper.success(sqlProbeService.execute(req));
    }

    /**
     * D.2 分页查询本人 SQL 探查历史.
     */
    @GetMapping("/history")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "D.2 分页查询本人 SQL 探查历史")
    public ResponseWrapper<SqlProbeHistoryRespDTO> queryHistory(@Valid PageRequest page) {
        PageResult<SqlProbeHistoryRespDTO> result = sqlProbeService.queryHistory(page);
        return ResponseWrapper.page(result);
    }

    /**
     * D.3 单条历史详情（仅本人）.
     */
    @GetMapping("/history/{id}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "D.3 SQL 探查历史详情")
    public ResponseWrapper<SqlProbeHistoryRespDTO> getHistoryDetail(
            @PathVariable @NotBlank(message = "id 不能为空") String id) {
        return ResponseWrapper.success(sqlProbeService.getHistoryDetail(id));
    }

    /**
     * D.4 SQL 探查白名单展示（前端 SQL 编辑器侧栏使用）.
     */
    @GetMapping("/schema-whitelist")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "D.4 SQL 探查白名单展示")
    public ResponseWrapper<SchemaWhitelistRespDTO> getSchemaWhitelist() {
        return ResponseWrapper.success(sqlProbeService.getSchemaWhitelist());
    }
}
