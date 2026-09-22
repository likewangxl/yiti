package com.bank.branch.platform.report.dto.req.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 一项受控模板展示实例。 */
@Data
public class ScreenDisplayComponentDTO {
    @NotBlank
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{1,63}")
    private String componentId;
    @NotNull
    private ScreenComponentType componentType;
    @NotNull
    private ScreenLayoutRegion layoutRegion;
    @NotNull
    @Min(0)
    private Integer order;
    @NotNull
    private Boolean visible;
    @Valid
    @NotNull
    private ScreenDisplayTextDTO text;
    @Valid
    @NotNull
    private ScreenDisplayFormatDTO format;
    @Valid
    @NotNull
    private ScreenDisplayContentDTO content;
    @Valid
    @NotNull
    private ScreenDisplayInteractionDTO interaction;
    @Valid
    @NotEmpty
    private List<ScreenDisplayDataRefDTO> dataRefs = new ArrayList<>();
}
