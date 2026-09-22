package com.bank.branch.platform.report.dto.req.presentation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
        if (payload == null) {
            return new ArrayList<>();
        }
        return validatePayload(payload.getComponents());
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
