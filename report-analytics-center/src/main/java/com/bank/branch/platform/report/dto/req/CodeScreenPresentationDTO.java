package com.bank.branch.platform.report.dto.req;

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

    /** 当前支持 branch-overview-v1 与 retail-overview-v1；未知模板拒绝。 */
    private String template;
}
