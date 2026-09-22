package com.bank.branch.platform.report.dto.req.presentation;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 受控交互；target 是业务身份，不是 URL。 */
@Data
public class ScreenDisplayInteractionDTO {
    private ScreenInteractionAction action = ScreenInteractionAction.NONE;
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String target;
}
