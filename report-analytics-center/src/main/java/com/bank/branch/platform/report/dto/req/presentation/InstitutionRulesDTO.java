package com.bank.branch.platform.report.dto.req.presentation;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * 代码化经营大屏的机构展示规则。
 *
 * <p>规则是发布包的一部分，服务端按精确集合过滤已授权机构；不配置规则时不能由
 * 客户端按名称、编码或角色猜测机构集合。</p>
 */
@Data
public class InstitutionRulesDTO {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> allowedOperatingLevels;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> allowedOrgNatures;

    /**
     * 临时按机构画像名称排除的关键词；缺省时不启用名称排除。
     * 关键词只影响展示目录，不改变授权机构集合或画像经营值。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> excludedOrgNameKeywords;
}
