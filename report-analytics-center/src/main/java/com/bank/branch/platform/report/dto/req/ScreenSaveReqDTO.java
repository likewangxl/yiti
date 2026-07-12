package com.bank.branch.platform.report.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 大屏整体保存请求（屏 + 区块一次提交）.
 */
@Data
public class ScreenSaveReqDTO {

    /** 空=新建；非空=更新 */
    private Long id;

    /** 空则服务端生成 SCR_XXXXXXXX */
    private String screenCode;

    @NotBlank
    private String screenName;

    /** PROVINCE / BRANCH / PERSON */
    @NotBlank
    private String viewLevel;

    private String themeJson;

    /** ACTIVE / DISABLED（空=ACTIVE） */
    private String status;

    @Valid
    private List<ScreenBlockDTO> blocks;
}
