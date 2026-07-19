package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.redengine.service.ReExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 红色引擎-数据导出端点。
 * <p>移植自源 redengine {@code ExportController}（{@code business.controller}）。URL 与 Task 4
 * 权限种子对照表 {@code P_RE_EXPORT}（{@code /api/re/export/*}，GET）逐字匹配，仅支持
 * {@code type=submit|score} 两种导出类型。</p>
 */
@Slf4j
@Tag(name = "红色引擎-数据导出")
@RestController
@RequestMapping("/api/re/export")
@RequiredArgsConstructor
public class ReExportController {

    private static final String CONTENT_TYPE_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReExportService reExportService;

    /**
     * 按类型导出 Excel（xlsx）。
     *
     * @param type 导出类型，仅支持 {@code submit}（材料上报）/ {@code score}（评分）
     * @return xlsx 文件字节流
     */
    @Operation(summary = "数据导出(submit/score)")
    @GetMapping("/{type}")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.EXPORT)
    @AuditLog(action = "RE_EXPORT", resourceType = "RE_SUBMIT")
    public ResponseEntity<byte[]> exportData(@PathVariable String type) {
        byte[] bytes = reExportService.exportData(type);
        String filename = "export_" + type + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));

        log.info("[ReExportController.exportData] type={}, bytes={}", type, bytes.length);
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(CONTENT_TYPE_XLSX))
                .body(bytes);
    }
}
