package com.bank.branch.platform.report.dto.req.presentation;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 展示文字；AUTO 空标题按发布时的名称快照回退。 */
@Data
public class ScreenDisplayTextDTO {
    private ScreenTitleMode titleMode = ScreenTitleMode.AUTO;
    @Size(max = 100)
    private String title;
    @Size(max = 200)
    private String subtitle;
    @Size(max = 500)
    private String description;
}
