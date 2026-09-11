package com.bank.branch.platform.performance.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 最新批次尝试的脱敏状态，用于提示页面是否回退到了旧成功快照。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDashboardBatchAttemptDTO {

    private String attemptId;
    private String status;
    private LocalDate dataDate;
    private LocalDateTime startedAt;
    private String message;
}
