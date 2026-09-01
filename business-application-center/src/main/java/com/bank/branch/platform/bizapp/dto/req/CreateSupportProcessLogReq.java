package com.bank.branch.platform.bizapp.dto.req;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 承接人员新增中台支持过程记录请求。 */
@Data
public class CreateSupportProcessLogReq {
    /** 移动端幂等键。 */
    @NotBlank(message = "clientUuid不能为空")
    @Size(max = 128, message = "clientUuid长度不能超过128")
    private String clientUuid;
    /** PROCESS / RESULT。未传时按 PROCESS 处理。 */
    @Pattern(regexp = "(?i)(PROCESS|RESULT)", message = "logType仅支持PROCESS或RESULT")
    private String logType;
    @NotBlank(message = "content不能为空")
    @Size(max = 2000, message = "content长度不能超过2000")
    private String content;
    private LocalDateTime checkinTime;
    @DecimalMin(value = "-180", message = "longitude超出范围")
    @DecimalMax(value = "180", message = "longitude超出范围")
    private BigDecimal longitude;
    @DecimalMin(value = "-90", message = "latitude超出范围")
    @DecimalMax(value = "90", message = "latitude超出范围")
    private BigDecimal latitude;
    @Size(max = 255, message = "locationAddress长度不能超过255")
    private String locationAddress;
    /** 图片/附件文件对象 ID，绑定为 SUPPORT_LOG。 */
    @JsonAlias({"photoFileIds", "attachmentIds"})
    @Size(max = 20, message = "fileIds数量不能超过20")
    private List<String> fileIds;
}
