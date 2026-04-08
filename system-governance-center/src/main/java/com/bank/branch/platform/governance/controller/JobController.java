package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.JobConfDTO;
import com.bank.branch.platform.governance.api.dto.JobRunLogDTO;
import com.bank.branch.platform.governance.api.dto.JobTriggerReqDTO;
import com.bank.branch.platform.governance.api.dto.JobTriggerRespDTO;
import com.bank.branch.platform.governance.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务调度控制器
 * 提供任务配置查询、执行日志查询、手动触发、暂停和恢复接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/sys/jobs")
@Tag(name = "任务调度", description = "定时任务管理与监控")
public class JobController {

    private final JobService jobService;

    /**
     * 分页查询任务配置列表
     *
     * @param keyword  关键词，可为 null
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 20
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询任务列表")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<JobConfDTO> listJobs(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[JobController.listJobs] keyword={}, pageNo={}, pageSize={}", keyword, pageNo, pageSize);
        PageResult<JobConfDTO> result = jobService.listJobs(keyword, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 分页查询任务执行日志
     *
     * @param jobId    任务ID（路径参数）
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 20
     * @return 分页结果
     */
    @GetMapping("/{jobId}/logs")
    @Operation(summary = "查询任务执行日志")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.READ)
    public ResponseWrapper<JobRunLogDTO> listRunLogs(
            @PathVariable(value = "jobId") String jobId,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[JobController.listRunLogs] jobId={}, pageNo={}, pageSize={}", jobId, pageNo, pageSize);
        PageResult<JobRunLogDTO> result = jobService.listRunLogs(jobId, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 手动触发任务执行（C.3）
     *
     * @param jobId   任务ID（路径参数）
     * @param reqDTO  触发请求（包含原因）
     * @return 执行结果
     */
    @PostMapping("/{jobId}/trigger")
    @Operation(summary = "手动触发任务")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.JOB_TRIGGER)
    public ResponseWrapper<JobTriggerRespDTO> triggerJob(
            @PathVariable(value = "jobId") String jobId,
            @Valid @RequestBody JobTriggerReqDTO reqDTO) {
        log.info("[JobController.triggerJob] jobId={}, reason={}", jobId, reqDTO.getReason());
        JobTriggerRespDTO resp = jobService.triggerJob(jobId, reqDTO.getReason());
        return ResponseWrapper.success(resp);
    }

    /**
     * 暂停任务
     *
     * @param jobId 任务ID（路径参数）
     * @return 成功响应
     */
    @PutMapping("/{jobId}/pause")
    @Operation(summary = "暂停任务")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> pauseJob(@PathVariable(value = "jobId") String jobId) {
        log.info("[JobController.pauseJob] jobId={}", jobId);
        jobService.pauseJob(jobId);
        return ResponseWrapper.success();
    }

    /**
     * 恢复任务
     *
     * @param jobId 任务ID（路径参数）
     * @return 成功响应
     */
    @PutMapping("/{jobId}/resume")
    @Operation(summary = "恢复任务")
    @BizAuth(bizType = BizType.SYS_CONFIG, action = BizAction.CONFIG)
    public ResponseWrapper<Void> resumeJob(@PathVariable(value = "jobId") String jobId) {
        log.info("[JobController.resumeJob] jobId={}", jobId);
        jobService.resumeJob(jobId);
        return ResponseWrapper.success();
    }
}
