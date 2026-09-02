package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 任务允许文件类型展示 DTO。 */
@Data
@Schema(description = "任务文件类型")
public class ReTaskFileTypeDTO {

    private Long id;
    private String fileTypeCode;
    private String fileTypeName;
    private String fileExtension;
    private String mimeType;
    private Long maxSizeBytes;
    private Integer sortNo;
    private Boolean enabled;
}
