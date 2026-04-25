package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.SavedQuerySaveReqDTO;
import com.bank.branch.platform.report.dto.req.SavedQueryUpdateReqDTO;
import com.bank.branch.platform.report.dto.resp.SavedQueryDetailRespDTO;
import com.bank.branch.platform.report.dto.resp.SavedQuerySummaryDTO;
import com.bank.branch.platform.report.service.SavedQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 查询方案保存 REST 控制器（B 章 4 端点）.
 *
 * <p>路径映射：
 * <ul>
 *   <li>GET    /api/reports/saved-queries           → B.1.1 列表（M1.4）</li>
 *   <li>GET    /api/reports/saved-queries/:id       → B.1.2 详情（M1.4）</li>
 *   <li>POST   /api/reports/saved-queries           → B.2 保存（M1.5）</li>
 *   <li>PUT    /api/reports/saved-queries/:id       → B.3 更新（M1.5）</li>
 *   <li>DELETE /api/reports/saved-queries/:id       → B.4 删除（M1.5）</li>
 * </ul>
 *
 * <p>M1.4 阶段先落地 GET 端点；M1.5 在本类追加 POST/PUT/DELETE.
 */
@Slf4j
@RestController
@RequestMapping("/api/reports/saved-queries")
@Tag(name = "报表-查询方案", description = "查询方案 CRUD（每用户最多 10 条）")
@Validated
@RequiredArgsConstructor
public class SavedQueryController {

    private final SavedQueryService savedQueryService;

    @GetMapping
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "B.1.1 查询本人方案列表（可按 dim 过滤）")
    public ResponseWrapper<List<SavedQuerySummaryDTO>> listMine(
            @RequestParam(required = false) String dim) {
        return ResponseWrapper.success(savedQueryService.listMine(dim));
    }

    @GetMapping("/{id}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "B.1.2 获取方案详情（仅本人）")
    public ResponseWrapper<SavedQueryDetailRespDTO> getDetail(@PathVariable String id) {
        return ResponseWrapper.success(savedQueryService.getDetail(id));
    }

    @PostMapping
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "B.2 保存方案（每用户最多 10 条，超限自动删最旧）")
    public ResponseWrapper<Map<String, String>> saveQuery(@Valid @RequestBody SavedQuerySaveReqDTO req) {
        String id = savedQueryService.saveQuery(req);
        return ResponseWrapper.success(Map.of("id", id));
    }

    @PutMapping("/{id}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "B.3 更新方案（乐观锁）")
    public ResponseWrapper<Void> updateQuery(@PathVariable String id,
                                             @Valid @RequestBody SavedQueryUpdateReqDTO req) {
        savedQueryService.updateQuery(id, req);
        return ResponseWrapper.success();
    }

    @DeleteMapping("/{id}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.DELETE)
    @Operation(summary = "B.4 删除方案（仅本人）")
    public ResponseWrapper<Void> deleteQuery(@PathVariable String id) {
        savedQueryService.deleteQuery(id);
        return ResponseWrapper.success();
    }
}
