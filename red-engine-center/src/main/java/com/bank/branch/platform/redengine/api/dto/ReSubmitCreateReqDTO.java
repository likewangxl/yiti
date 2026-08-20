package com.bank.branch.platform.redengine.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 材料上报-新建请求 DTO。
 * <p>对应源 redengine {@code SubmitController.SubmitRequest}（内嵌 {@code BizSubmit} + fileUrls），
 * 拍平为单一请求体；{@code orgId}/{@code submitterId} 不由前端传入，由 {@code ReSubmitService.createSubmit}
 * 依据当前登录人 userId（PT_USER.USER_ID）通过 {@code ReUserPartyMapService.getRequiredPartyOrgId}
 * 解析（防止越权指定他人组织）。
 * 附件不再传旧版 {@code fileUrls}，改传 {@link #fileObjectIds}——前端先调用 governance
 * {@code POST /api/files/upload} 直传拿到平台文件ID，再随本请求提交，由服务端逐个写
 * {@code RE_SUBMIT_FILE} 并调用 {@code FileApi.bindFile} 完成业务关联登记。</p>
 */
@Data
@Schema(description = "材料上报-新建请求")
public class ReSubmitCreateReqDTO {

    /** 考核维度(dim1~dim4) */
    @Schema(description = "考核维度(dim1~dim4)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "dimension 不能为空")
    private String dimension;

    /** 考核项编码(如1.1) */
    @Schema(description = "考核项编码(如1.1)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "itemCode 不能为空")
    private String itemCode;

    /** 考核项名称 */
    @Schema(description = "考核项名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "itemName 不能为空")
    private String itemName;

    /** 该考核项满分上限（可为空，交由 Task 9 评分校验兜底默认 100） */
    @Schema(description = "该考核项满分上限")
    private BigDecimal maxScore;

    /** 项目名称 */
    @Schema(description = "项目名称")
    private String projectName;

    /** 上报类型(1月度 2季度 3年度) */
    @Schema(description = "上报类型(1月度 2季度 3年度)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "submitType 不能为空")
    private Integer submitType;

    /** 上报日期 */
    @Schema(description = "上报日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "submitDate 不能为空")
    private LocalDate submitDate;

    /** 表单数据(JSON) */
    @Schema(description = "表单数据(JSON)")
    private String formData;

    /** 附件文件对象ID列表（governance FileApi 上传后返回的 fileObjectId，可为空表示无附件） */
    @Schema(description = "附件文件对象ID列表")
    private List<String> fileObjectIds;
}
