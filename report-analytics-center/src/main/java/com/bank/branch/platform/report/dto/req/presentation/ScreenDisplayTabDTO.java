package com.bank.branch.platform.report.dto.req.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 公司/零售结构页签。 */
@Data
public class ScreenDisplayTabDTO {
    @NotBlank
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{0,63}")
    private String tabKey;
    @NotBlank
    @Size(max = 100)
    private String label;
    @NotBlank
    @Size(max = 100)
    private String corporateField;
    @NotBlank
    @Size(max = 100)
    private String retailField;
    @Size(max = 100)
    private String totalField;
    @NotNull
    private ScreenDisplayUnit unit;
}
