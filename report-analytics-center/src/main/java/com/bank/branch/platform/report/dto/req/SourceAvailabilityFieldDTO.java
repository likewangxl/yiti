package com.bank.branch.platform.report.dto.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** 单个绑定语义字段的数据可用性说明。 */
@Data
public class SourceAvailabilityFieldDTO {
    private SourceAvailabilityStatus status;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String dataDate;
}
