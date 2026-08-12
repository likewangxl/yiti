package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasDiscardReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasRollbackReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasEditorRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasSaveRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenPublishLogRespDTO;
import com.bank.branch.platform.report.service.screen.ScreenCanvasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 大屏画布设计器(管理端)——双态加载/保存/发布/回滚/放弃草稿.
 *
 * <p>发布/回滚为高危操作:独立 URL、独立 PT_RESOURCE、服务层手工审计。
 */
@Slf4j
@RestController
@RequestMapping("/api/screen/admin/canvas")
@Tag(name = "大屏-画布设计器", description = "画布双态:草稿保存(乐观锁) + 发布/回滚(高危) + 放弃草稿")
@Validated
@RequiredArgsConstructor
public class ScreenCanvasAdminController {

    private final ScreenCanvasService canvasService;

    @GetMapping("/{id}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "编辑器加载(style+draft+blocks+版本)")
    public ResponseWrapper<ScreenCanvasEditorRespDTO> load(@PathVariable Long id) {
        return ResponseWrapper.success(canvasService.loadCanvas(id));
    }

    @PostMapping("/save")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "保存草稿(乐观锁,冲突 RPT-43012)")
    public ResponseWrapper<ScreenCanvasSaveRespDTO> save(@Valid @RequestBody ScreenCanvasSaveReqDTO req) {
        return ResponseWrapper.success(canvasService.saveCanvas(req));
    }

    @PostMapping("/publish")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "发布(高危:合成渲染包+归档+审计)")
    public ResponseWrapper<Void> publish(@Valid @RequestBody ScreenCanvasPublishReqDTO req) {
        canvasService.publishCanvas(req);
        return ResponseWrapper.success();
    }

    @PostMapping("/rollback")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "回滚(高危:从归档恢复发布态)")
    public ResponseWrapper<Void> rollback(@Valid @RequestBody ScreenCanvasRollbackReqDTO req) {
        canvasService.rollbackCanvas(req);
        return ResponseWrapper.success();
    }

    @PostMapping("/discard")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.WRITE)
    @Operation(summary = "放弃草稿(发布态覆盖草稿)")
    public ResponseWrapper<Void> discard(@Valid @RequestBody ScreenCanvasDiscardReqDTO req) {
        canvasService.discardDraft(req);
        return ResponseWrapper.success();
    }

    @GetMapping("/{id}/publish-logs")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "发布归档列表(回滚选择用)")
    public ResponseWrapper<List<ScreenPublishLogRespDTO>> publishLogs(@PathVariable Long id) {
        return ResponseWrapper.success(canvasService.listPublishLogs(id));
    }
}
