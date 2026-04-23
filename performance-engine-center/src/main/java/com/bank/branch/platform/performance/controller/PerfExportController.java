package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.service.export.PerfExportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 绩效异步导出 REST 控制器（V1.2 Task Q6.1 骨架）.
 *
 * <p>Q6.1 先交付空骨架，4 个导出端点由 Task Q6.4 正式接入。
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/export")
@Tag(name = "Performance Export", description = "绩效异步导出（V1.2）")
@Validated
@RequiredArgsConstructor
public class PerfExportController {

    @SuppressWarnings("unused")
    private final PerfExportService perfExportService;

    /**
     * 占位健康检查（Q6.1 骨架）.
     */
    ResponseWrapper<String> ping() {
        return ResponseWrapper.success("ok");
    }
}
