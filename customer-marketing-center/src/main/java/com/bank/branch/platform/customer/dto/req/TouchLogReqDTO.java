package com.bank.branch.platform.customer.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 新增触达日志请求 DTO
 */
@Data
@Schema(description = "新增触达日志请求")
public class TouchLogReqDTO {

    /**
     * 客户端幂等键（由移动端生成，防重复提交），必填
     */
    @NotBlank(message = "客户端幂等键不能为空")
    @Schema(description = "客户端幂等键（UUID），用于防重复提交", required = true)
    private String clientUuid;

    /**
     * 触达内容描述，必填
     */
    @NotBlank(message = "触达内容不能为空")
    @Schema(description = "触达内容文字描述", required = true)
    private String logContent;

    /**
     * 照片 URL 列表（MinIO 上传后的 URL），最多 9 张，可为空
     */
    @Schema(description = "照片 URL 列表（MinIO URL），最多 9 张")
    private List<String> photoUrls;
}
