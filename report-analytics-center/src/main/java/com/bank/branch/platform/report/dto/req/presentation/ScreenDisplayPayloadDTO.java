package com.bank.branch.platform.report.dto.req.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** 按展示配置键保存的显式比较来源；缺省表示沿用历史比较路径。 */
    @Valid
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
    private Map<String, ScreenDisplayComparisonDTO> comparisons = new LinkedHashMap<>();
}
