package com.bank.branch.platform.report.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.report.dto.req.NoticeQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.NoticeVO;
import com.bank.branch.platform.report.service.NoticeQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 公告查询 REST 控制器（只读，历史数据查询 - 公告查询，归属报表分析中心）.
 *
 * <p>资源登记见 2026-06-25-history-data-query-resources.sql：
 * <ul>
 *   <li>GET /api/reports/notices         → R_RPT_NOTICE_LIST（列表，SEQ_NO 倒序）</li>
 *   <li>GET /api/reports/notices/{id}    → R_RPT_NOTICE_DET（详情：含正文）</li>
 * </ul></p>
 */
@Slf4j
@RestController
@RequestMapping("/api/reports/notices")
@Tag(name = "历史数据查询-公告查询", description = "sys_notice 公告查询（列表 + 详情）")
@Validated
@RequiredArgsConstructor
public class NoticeQueryController {

    private final NoticeQueryService noticeQueryService;
    private final FileApi fileApi;

    @GetMapping
    @BizAuth(bizType = BizType.REPORT, action = BizAction.LIST)
    @Operation(summary = "公告查询列表")
    public ResponseWrapper<NoticeVO> list(
            @Valid @ModelAttribute NoticeQueryReqDTO req,
            @Valid @ModelAttribute PageRequest page) {
        PageResult<NoticeVO> result = noticeQueryService.pageList(req, page);
        return ResponseWrapper.page(result);
    }

    @GetMapping("/{noticId}")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "公告查询详情")
    public ResponseWrapper<NoticeVO> detail(@PathVariable("noticId") String noticId) {
        return ResponseWrapper.success(noticeQueryService.detail(noticId));
    }

    /**
     * 下载公告附件：EXTEND 作为文件标识从对象存储（OBS）读取并流式下载。
     * <p>要求 EXTEND 为有效文件 ID/Key；无附件返回 404。</p>
     */
    @GetMapping("/{noticId}/attachment")
    @BizAuth(bizType = BizType.REPORT, action = BizAction.READ)
    @Operation(summary = "下载公告附件")
    public void attachment(@PathVariable("noticId") String noticId, HttpServletResponse resp) throws IOException {
        NoticeVO n = noticeQueryService.detail(noticId);
        if (!StringUtils.hasText(n.getExtend())) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "该公告无附件");
            return;
        }
        String fileKey = n.getExtend().trim();
        byte[] bytes;
        try {
            bytes = fileApi.getFileContent(fileKey);
        } catch (Exception ex) {
            log.warn("[Notice.attachment] 读取附件失败 noticId={} key={} err={}", noticId, fileKey, ex.getMessage());
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "附件不存在或无法读取");
            return;
        }
        String fileName;
        try {
            fileName = fileApi.getFileName(fileKey);
        } catch (Exception ex) {
            fileName = fileKey;
        }
        if (!StringUtils.hasText(fileName)) {
            fileName = fileKey;
        }
        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Disposition",
                "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
        resp.getOutputStream().write(bytes);
        resp.flushBuffer();
    }
}
