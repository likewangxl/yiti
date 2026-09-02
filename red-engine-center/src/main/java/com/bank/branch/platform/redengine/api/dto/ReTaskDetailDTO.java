package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 任务详情 DTO，包含任务对象和文件限制快照。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务详情")
public class ReTaskDetailDTO extends ReTaskListItemDTO {

    private List<ReTaskTargetDTO> targets;
    private List<ReTaskFileTypeDTO> fileTypes;
    private Long currentInstanceId;
}
