package com.bank.branch.platform.report.dto.resp;

import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 大屏详情响应（列表场景 blocks=null）.
 */
@Data
public class ScreenDetailRespDTO {

    private Long id;

    private String screenCode;

    private String screenName;

    private String viewLevel;

    private String bizLine;

    private String orgScopeMode;

    private String orgGroupCode;

    private List<String> allowedRoleCodes;

    private String themeJson;

    private String status;

    private LocalDateTime createdTime;

    private List<ScreenBlockDTO> blocks;
}
