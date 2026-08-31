package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 报送员提交或重新提交任务填报内容的请求 DTO。 */
@Data
@Schema(description = "任务提交请求")
public class ReTaskSubmissionReqDTO {

    /** 党支部任务分配 ID。 */
    @NotNull(message = "任务分配不能为空")
    @Schema(description = "任务分配 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long assignmentId;

    /** 普通/临时任务正文；四维任务可为空，由 RE_SUBMIT 保存明细。 */
    @Schema(description = "填报内容")
    private String content;

    /** governance FileApi 返回的文件对象 ID。 */
    @Schema(description = "附件文件对象 ID")
    private List<String> fileObjectIds;

    /** 客户端幂等请求号。 */
    @NotBlank(message = "请求幂等号不能为空")
    @Schema(description = "请求幂等号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String clientRequestId;

    /** 四维任务关联既有 RE_SUBMIT 时使用的维度编码。 */
    private String dimensionCode;

    /** 四维任务关联既有 RE_SUBMIT 时使用的明细项编码。 */
    private String itemCode;

    /** 四维任务详情展示名称；普通任务无需填写。 */
    private String itemName;

    /** 四维任务兼容旧材料上报的上限分值。 */
    private BigDecimal maxScore;

    /** 四维任务兼容旧材料上报的项目名称。 */
    private String projectName;

    /** 四维任务兼容旧材料上报的上报类型。 */
    private Integer submitType;

    /** 四维任务兼容旧材料上报的上报日期。 */
    private LocalDate submitDate;

    /** 四维任务结构化材料数据。 */
    private String formData;
}
