package com.bank.branch.platform.report.dto.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

/** 单个代码化大屏绑定槽位的数据可用性说明。 */
@Data
public class SourceAvailabilityDTO {
    private SourceAvailabilityStatus status;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String dataDate;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, SourceAvailabilityFieldDTO> fields;
}
