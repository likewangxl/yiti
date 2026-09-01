package com.bank.branch.platform.bizapp.api.dto;

import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 中台支持过程记录对外 DTO。 */
@Data
public class SupportProcessLogDTO {
    private String id;
    private String supportRequestId;
    private String clientUuid;
    private String logType;
    private String content;
    private LocalDateTime checkinTime;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String locationAddress;
    private String createdBy;
    private LocalDateTime createdTime;
    private List<FileObjectDTO> files;
}
