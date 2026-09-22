package com.bank.branch.platform.report.dto.req.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * displaySchemaVersion=1 的展示负载。
 *
 * <p>版本属于 presentation 节点，不属于 display 节点；该类型只承载
 * {@code presentation.display.components}，避免把协议版本重复序列化到错误层级。</p>
 */
@Data
public class ScreenDisplayPayloadDTO {

    @Valid
    @NotEmpty
    private List<ScreenDisplayComponentDTO> components = new ArrayList<>();
}
