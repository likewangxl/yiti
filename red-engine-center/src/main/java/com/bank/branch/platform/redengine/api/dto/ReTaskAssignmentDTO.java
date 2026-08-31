package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 任务详情中支部填报汇总行 DTO。 */
@Data
@Schema(description = "任务支部分配汇总")
public class ReTaskAssignmentDTO {

    private Long taskId;
    private Long taskInstanceId;
    private Long assignmentId;
    private Long branchId;
    private String branchName;
    private ReTaskAssignmentStatus status;
    private Boolean isUnreported;
    private String submitterId;
    private String submitterName;
    private LocalDateTime submittedAt;
    private String content;
    private String formData;
    private List<ReTaskAttachmentDTO> files;
}
