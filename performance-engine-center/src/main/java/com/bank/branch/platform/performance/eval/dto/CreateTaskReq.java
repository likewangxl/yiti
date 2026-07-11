package com.bank.branch.platform.performance.eval.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发起评价任务请求体。
 */
@Data
public class CreateTaskReq {
    /** 任务名称（必填）. */
    @NotBlank
    private String taskName;
    /** 截止时间（必须晚于当前时间）. */
    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    /** 被评价人工号列表（必填）. */
    @NotNull
    private List<String> beEvalUserIds;
}
