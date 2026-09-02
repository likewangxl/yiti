package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 任务提交附件展示 DTO。 */
@Data
@Schema(description = "任务附件")
public class ReTaskAttachmentDTO {

    private Long id;
    private String fileId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private Integer sortNo;
}
