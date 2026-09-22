package com.bank.branch.platform.report.dto.req.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** displaySchemaVersion=1 的展示子协议。 */
@Data
public class ScreenDisplayConfigDTO {
    @NotNull
    private Integer displaySchemaVersion;
    @Valid
    @NotEmpty
    private List<ScreenDisplayComponentDTO> components = new ArrayList<>();
}
