package com.bank.branch.platform.report.dto.req.presentation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 展示子协议的跨字段校验；保存/发布接线由 S03 完成。 */
public final class ScreenDisplayContractValidator {

    public static final int VERSION = 1;
    private static final Pattern ID = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{1,63}");
    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");
    private static final Pattern TARGET = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Set<ScreenComponentType> SINGLE_VALUE_TYPES = Set.of(
            ScreenComponentType.METRIC_CARD, ScreenComponentType.COMPLETION,
            ScreenComponentType.COMPOSITION_TABS, ScreenComponentType.RANKING);

    private ScreenDisplayContractValidator() {
    }

    /**
     * 兼容 S02 聚合类型的校验入口。实际 JSON 使用
     * {@link ScreenDisplayPayloadDTO}，版本不再嵌套在 display 节点。
     */
    public static List<String> validate(ScreenDisplayConfigDTO config) {
        List<String> issues = new ArrayList<>();
        if (config == null) {
            return issues;
        }
        if (config.getDisplaySchemaVersion() == null || config.getDisplaySchemaVersion() != VERSION) {
            issues.add("展示协议版本不受支持");
        }
        issues.addAll(validatePayload(config.getComponents()));
        return issues;
    }

    /** 缺省配置表示旧展示路径，返回空问题清单。 */
    public static List<String> validateDisplayPayload(ScreenDisplayPayloadDTO payload) {
        return validateDisplayPayload(null, payload);
    }

    /**
     * 校验带模板语义的展示负载。比较配置必须在这里和普通展示组件一起校验，
     * 不能由前端目录或运行时兜底决定可用键。
     */
    public static List<String> validateDisplayPayload(String template, ScreenDisplayPayloadDTO payload) {
        if (payload == null) {
            return new ArrayList<>();
        }
        List<String> issues = validatePayload(payload.getComponents());
        validateComparisons(template, payload.getComponents(), payload.getComparisons(), issues);
        return issues;
    }

    private static List<String> validatePayload(List<ScreenDisplayComponentDTO> components) {
        List<String> issues = new ArrayList<>();
        if (components == null || components.isEmpty()) {
            issues.add("components至少配置一项");
            return issues;
        }
        Set<String> componentIds = new HashSet<>();
        for (int index = 0; index < components.size(); index++) {
            ScreenDisplayComponentDTO component = components.get(index);
            String prefix = "components[" + index + "] ";
            if (component == null) {
                issues.add(prefix + "组件不能为空");
                continue;
            }
            String componentId = trim(component.getComponentId());
            if (!ID.matcher(componentId).matches()) {
                issues.add(prefix + "组件ID不合法");
            } else if (!componentIds.add(componentId)) {
                issues.add("组件ID重复: " + componentId);
            }
            if (component.getComponentType() == null) issues.add(prefix + "组件类型不能为空");
            if (component.getLayoutRegion() == null) issues.add(prefix + "布局区域不能为空");
            if (component.getOrder() == null || component.getOrder() < 0) issues.add(prefix + "顺序必须是非负整数");
            if (component.getVisible() == null) issues.add(prefix + "visible不能为空");
            validateText(component.getText(), prefix, issues);
            if (component.getFormat() == null) issues.add(prefix + "format不能为空");
            validateContent(component, prefix, issues);
            validateInteraction(component.getInteraction(), prefix, issues);
            validateDataRefs(component, prefix, issues);
        }
        return issues;
    }

    private static void validateComparisons(String template, List<ScreenDisplayComponentDTO> components,
                                            Map<String, ScreenDisplayComparisonDTO> comparisons,
                                            List<String> issues) {
        if (comparisons == null) {
            issues.add("comparisons不能为空");
            return;
        }
        Map<String, ScreenDisplayComponentDTO> cardComponents = new LinkedHashMap<>();
        if (components != null) {
            for (ScreenDisplayComponentDTO component : components) {
                if (component != null && component.getComponentType() != null
                        && (component.getComponentType() == ScreenComponentType.METRIC_CARD
                        || component.getComponentType() == ScreenComponentType.COMPLETION)
                        && !trim(component.getComponentId()).isEmpty()) {
                    cardComponents.put(trim(component.getComponentId()), component);
                }
            }
        }
        for (Map.Entry<String, ScreenDisplayComparisonDTO> entry : comparisons.entrySet()) {
            String key = entry.getKey();
            ScreenDisplayComparisonDTO comparison = entry.getValue();
            boolean branchOverview = key != null && "branch-overview-v1".equals(template)
                    && Set.of("overview-deposit", "overview-loan", "overview-settlementDeposit").contains(key);
            ScreenDisplayComponentDTO sourceComponent = cardComponents.get(key);
            if ((!branchOverview && sourceComponent == null) || key == null || key.isBlank()) {
                issues.add("comparisons键不属于当前METRIC_CARD/COMPLETION或分行总览: " + key);
                continue;
            }
            if (comparison == null) {
                issues.add("comparisons[" + key + "]不能为空");
                continue;
            }
            validateComparison(key, branchOverview, comparison, sourceComponent, issues);
        }
    }

    private static void validateComparison(String key, boolean overviewKey,
                                           ScreenDisplayComparisonDTO comparison,
                                           ScreenDisplayComponentDTO sourceComponent,
                                           List<String> issues) {
        String prefix = "comparisons[" + key + "] ";
        if (comparison.getEnabled() == null) {
            issues.add(prefix + "enabled不能为空");
            return;
        }
        if (!comparison.getEnabled()) {
            if (comparison.getHistoryBlockId() != null || comparison.getValueFields() != null
                    || comparison.getDateField() != null || comparison.getSourceUnit() != null) {
                issues.add(prefix + "enabled=false时只能配置enabled");
            }
            return;
        }
        if (comparison.getHistoryBlockId() == null || comparison.getHistoryBlockId() <= 0) {
            issues.add(prefix + "historyBlockId必须是正整数");
        }
        List<String> valueFields = comparison.getValueFields();
        if (valueFields == null || valueFields.isEmpty() || valueFields.size() > 8) {
            issues.add(prefix + "valueFields必须是1到8个字段");
        } else {
            Set<String> seen = new HashSet<>();
            for (String field : valueFields) {
                String normalized = trim(field);
                if (field == null || normalized.isEmpty() || normalized.length() > 100
                        || !normalized.equals(field) || !seen.add(normalized)) {
                    issues.add(prefix + "valueFields必须是1到8个不重复的非空字段");
                    break;
                }
            }
        }
        String dateField = comparison.getDateField();
        if (dateField == null || trim(dateField).isEmpty() || trim(dateField).length() > 100
                || !trim(dateField).equals(dateField)) {
            issues.add(prefix + "dateField必须是1到100个字符");
        }
        ScreenDisplayUnit sourceUnit = comparison.getSourceUnit();
        if (sourceUnit == null || sourceUnit == ScreenDisplayUnit.AUTO || sourceUnit.kind() == null) {
            issues.add(prefix + "sourceUnit必须是明确的金融单位");
        }
        if (sourceComponent != null && sourceUnit != null && sourceUnit.kind() != null) {
            Set<String> refKinds = new HashSet<>();
            if (sourceComponent.getDataRefs() != null) {
                for (ScreenDisplayDataRefDTO ref : sourceComponent.getDataRefs()) {
                    if (ref != null && ref.getUnit() != null && ref.getUnit().kind() != null) {
                        refKinds.add(ref.getUnit().kind());
                    }
                }
            }
            if (refKinds.size() == 1 && !refKinds.contains(sourceUnit.kind())) {
                issues.add(prefix + "sourceUnit与卡片来源单位类型冲突");
            }
        }
        if (valueFields != null && valueFields.size() > 1
                && (!overviewKey || sourceUnit == null || !"amount".equals(sourceUnit.kind()))) {
            issues.add(prefix + "普通卡或非金额比较不能配置多个valueFields");
        }
    }

    private static void validateText(ScreenDisplayTextDTO text, String prefix, List<String> issues) {
        if (text == null) {
            issues.add(prefix + "text不能为空");
            return;
        }
        ScreenTitleMode mode = text.getTitleMode() == null ? ScreenTitleMode.AUTO : text.getTitleMode();
        if (mode == ScreenTitleMode.CUSTOM && trim(text.getTitle()).isEmpty()) {
            issues.add(prefix + "自定义标题不能为空");
        }
    }

    private static void validateContent(ScreenDisplayComponentDTO component, String prefix, List<String> issues) {
        ScreenDisplayContentDTO content = component.getContent();
        if (content == null) {
            issues.add(prefix + "展示内容不能为空");
            return;
        }
        ScreenComponentType type = component.getComponentType();
        if ((type == ScreenComponentType.METRIC_CARD || type == ScreenComponentType.COMPLETION
                || type == ScreenComponentType.MAP) && trim(content.getMainField()).isEmpty()) {
            issues.add(prefix + "mainField不能为空");
        }
        if (type == ScreenComponentType.TREND && empty(content.getSeries())) issues.add(prefix + "series至少配置一项");
        if (type == ScreenComponentType.COMPOSITION_TABS && empty(content.getTabs())) issues.add(prefix + "tabs至少配置一项");
        if (type == ScreenComponentType.RANKING && empty(content.getRankingMetrics())) issues.add(prefix + "rankingMetrics至少配置一项");
        if (type == ScreenComponentType.DETAIL_TABLE && empty(content.getColumns())) issues.add(prefix + "columns至少配置一项");
        validateSeries(content.getSeries(), prefix, issues);
        validateColumns(content.getColumns(), prefix, issues);
        validateTabs(content.getTabs(), prefix, issues);
        validateRankingMetrics(content.getRankingMetrics(), prefix, issues);
        validateIncomeRatio(content.getIncomeRatio(), prefix, issues);
    }

    private static void validateIncomeRatio(ScreenDisplayIncomeRatioDTO ratio, String prefix,
                                            List<String> issues) {
        if (ratio == null) {
            return;
        }
        String numerator = trim(ratio.getNumeratorField());
        String denominator = trim(ratio.getDenominatorField());
        if (ratio.getUnit() == null || !Set.of(ScreenDisplayUnit.YUAN,
                ScreenDisplayUnit.TEN_THOUSAND, ScreenDisplayUnit.HUNDRED_MILLION)
                .contains(ratio.getUnit())) {
            issues.add(prefix + "incomeRatio单位必须是金额单位");
        }
        if (numerator.isEmpty() != denominator.isEmpty()) {
            issues.add(prefix + "incomeRatio分子和分母必须同时配置或同时为空");
        }
    }

    private static void validateInteraction(ScreenDisplayInteractionDTO interaction, String prefix, List<String> issues) {
        if (interaction == null) {
            issues.add(prefix + "interaction不能为空");
            return;
        }
        ScreenInteractionAction action = interaction.getAction() == null ? ScreenInteractionAction.NONE : interaction.getAction();
        String target = trim(interaction.getTarget());
        if (!target.isEmpty() && !TARGET.matcher(target).matches()) issues.add(prefix + "交互目标不合法");
        if (action == ScreenInteractionAction.NONE && !target.isEmpty()) issues.add(prefix + "NONE交互不能配置目标");
        if (action == ScreenInteractionAction.OPEN_BUSINESS_LINE
                && !Set.of("CORP", "RETAIL", "COMMON").contains(target)) {
            issues.add(prefix + "业务条线目标不合法");
        }
    }

    private static void validateDataRefs(ScreenDisplayComponentDTO component, String prefix, List<String> issues) {
        List<ScreenDisplayDataRefDTO> refs = component.getDataRefs();
        if (refs == null || refs.isEmpty()) {
            issues.add(prefix + "至少引用一个blockId");
            return;
        }
        Set<Long> blocks = new HashSet<>();
        ScreenDisplayUnit displayUnit = component.getFormat() == null
                ? ScreenDisplayUnit.AUTO : component.getFormat().getDisplayUnit();
        if (component.getFormat() != null && component.getFormat().getDecimals() != null
                && (component.getFormat().getDecimals() < 0 || component.getFormat().getDecimals() > 8)) {
            issues.add(prefix + "小数位必须是0到8");
        }
        for (ScreenDisplayDataRefDTO ref : refs) {
            if (ref == null || ref.getBlockId() == null || ref.getBlockId() <= 0) {
                issues.add(prefix + "blockId不合法");
                continue;
            }
            if (!blocks.add(ref.getBlockId())) issues.add(prefix + "blockId重复: " + ref.getBlockId());
            if (ref.getRole() == null) issues.add(prefix + "数据引用角色不能为空");
            if (ref.getUnit() == null) issues.add(prefix + "来源单位不能为空");
            if (ref.getUnit() == ScreenDisplayUnit.AUTO) issues.add(prefix + "来源原始单位不能为AUTO");
            if (component.getComponentType() != null && SINGLE_VALUE_TYPES.contains(component.getComponentType())
                    && displayUnit != null && displayUnit.kind() != null && ref.getUnit() != null
                    && ref.getUnit().kind() != null && !displayUnit.kind().equals(ref.getUnit().kind())) {
                issues.add(prefix + "单位类型冲突: " + displayUnit + "/" + ref.getUnit());
            }
        }
    }

    private static void validateSeries(List<ScreenDisplaySeriesDTO> values, String prefix, List<String> issues) {
        Set<String> keys = new HashSet<>();
        if (values == null) return;
        for (ScreenDisplaySeriesDTO value : values) {
            if (value == null) {
                issues.add(prefix + "seriesKey缺失或重复");
                continue;
            }
            if (!validKey(value.getSeriesKey()) || !keys.add(trim(value.getSeriesKey()))) {
                issues.add(prefix + "seriesKey缺失或重复");
            }
            if (trim(value.getField()).isEmpty() || trim(value.getLabel()).isEmpty()) issues.add(prefix + "series字段和标签不能为空");
            if (value.getUnit() == null || value.getUnit() == ScreenDisplayUnit.AUTO) issues.add(prefix + "series单位不合法");
        }
    }

    private static void validateColumns(List<ScreenDisplayColumnDTO> values, String prefix, List<String> issues) {
        Set<String> keys = new HashSet<>();
        if (values == null) return;
        for (ScreenDisplayColumnDTO value : values) {
            if (value == null) {
                issues.add(prefix + "columnKey缺失或重复");
                continue;
            }
            if (!validKey(value.getColumnKey()) || !keys.add(trim(value.getColumnKey()))) {
                issues.add(prefix + "columnKey缺失或重复");
            }
            if (trim(value.getField()).isEmpty() || trim(value.getLabel()).isEmpty()) issues.add(prefix + "column字段和标签不能为空");
            if (value.getUnit() == null || value.getVisible() == null) issues.add(prefix + "column单位和visible不能为空");
        }
    }

    private static void validateTabs(List<ScreenDisplayTabDTO> values, String prefix, List<String> issues) {
        Set<String> keys = new HashSet<>();
        if (values == null) return;
        for (ScreenDisplayTabDTO value : values) {
            if (value == null) {
                issues.add(prefix + "tabKey缺失或重复");
                continue;
            }
            if (!validKey(value.getTabKey()) || !keys.add(trim(value.getTabKey()))) {
                issues.add(prefix + "tabKey缺失或重复");
            }
            if (trim(value.getLabel()).isEmpty() || trim(value.getCorporateField()).isEmpty()
                    || trim(value.getRetailField()).isEmpty()) issues.add(prefix + "tab标签和公司/零售字段不能为空");
            if (value.getUnit() == null || value.getUnit() == ScreenDisplayUnit.AUTO) issues.add(prefix + "tab单位不合法");
        }
    }

    private static void validateRankingMetrics(List<ScreenDisplayRankingMetricDTO> values,
                                               String prefix, List<String> issues) {
        Set<String> keys = new HashSet<>();
        if (values == null) return;
        for (ScreenDisplayRankingMetricDTO value : values) {
            if (value == null) {
                issues.add(prefix + "ranking metricKey缺失或重复");
                continue;
            }
            if (!validKey(value.getMetricKey()) || !keys.add(trim(value.getMetricKey()))) {
                issues.add(prefix + "ranking metricKey缺失或重复");
            }
            if (trim(value.getField()).isEmpty() || trim(value.getLabel()).isEmpty()) issues.add(prefix + "ranking字段和标签不能为空");
            if (value.getUnit() == null || value.getUnit() == ScreenDisplayUnit.AUTO
                    || value.getDirection() == null) issues.add(prefix + "ranking单位和方向不合法");
        }
    }

    private static boolean empty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private static boolean validKey(String value) {
        return KEY.matcher(trim(value)).matches();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
