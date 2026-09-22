package com.bank.branch.platform.report.dto.req;

import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayPayloadDTO;
import com.bank.branch.platform.report.dto.req.presentation.InstitutionRulesDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import lombok.Data;

/**
 * 代码化经营大屏的类型化声明。
 *
 * <p>声明存放在现有 {@code canvasStyle.presentation} JSON 节点中，不引入表字段或新表。</p>
 */
@Data
public class CodeScreenPresentationDTO {

    /** 当前仅支持 CODE。 */
    private String type;

    /** 当前支持 branch-overview-v1、retail-overview-v1 与 corporate-overview-v1；未知模板拒绝。 */
    private String template;

    /**
     * 新展示协议的机构过滤规则；发布态原样透传给运行时，不能由前端猜测。
     */
    @Valid
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private InstitutionRulesDTO institutionRules;

    /**
     * 可选的展示子协议版本。版本位于 presentation 根节点；缺省表示历史 CODE 展示路径。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer displaySchemaVersion;

    /** displaySchemaVersion=1 时的类型化展示负载。 */
    @Valid
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ScreenDisplayPayloadDTO display;
}
