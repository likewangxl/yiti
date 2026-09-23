package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.report.dto.req.presentation.ScreenComponentType;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayComponentDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayConfigDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayContentDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayContractValidator;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayDataRefDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayFormatDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayInteractionDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayIncomeRatioDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplaySeriesDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayTextDTO;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDisplayUnit;
import com.bank.branch.platform.report.dto.req.presentation.ScreenInteractionAction;
import com.bank.branch.platform.report.dto.req.presentation.ScreenLayoutRegion;
import com.bank.branch.platform.report.dto.req.presentation.ScreenDataRefRole;
import com.bank.branch.platform.report.dto.req.presentation.ScreenSourceDimension;
import com.bank.branch.platform.report.dto.req.presentation.ScreenTitleMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenPresentationContractTest {

    @Test
    void legacyPresentationWithoutDisplayContractStaysValid() {
        assertThat(ScreenDisplayContractValidator.validate(null)).isEmpty();
    }

    @Test
    void validContractAllowsFalseVisibilityZeroDecimalsMultipleSeriesAndSharedBlocks() {
        ScreenDisplayConfigDTO config = config();
        config.getComponents().get(0).setVisible(false);
        config.getComponents().get(0).getFormat().setDecimals(0);
        config.getComponents().get(1).getDataRefs().get(0).setBlockId(11L);

        assertThat(ScreenDisplayContractValidator.validate(config)).isEmpty();
        assertThat(config.getComponents().get(1).getContent().getSeries()).hasSize(2);
    }

    @Test
    void duplicateIdsAndDuplicateBlockWithinOneComponentAreRejected() {
        ScreenDisplayConfigDTO config = config();
        config.getComponents().get(1).setComponentId("deposit-card");
        config.getComponents().get(0).setDataRefs(List.of(ref(11L), ref(11L)));

        assertThat(ScreenDisplayContractValidator.validate(config))
                .anyMatch(issue -> issue.contains("组件ID重复"))
                .anyMatch(issue -> issue.contains("blockId重复"));
    }

    @Test
    void unknownVersionBlankCustomTitleIllegalActionTargetAndUnitConflictAreRejected() {
        ScreenDisplayConfigDTO config = config();
        config.setDisplaySchemaVersion(9);
        config.getComponents().get(0).getText().setTitle("   ");
        config.getComponents().get(0).getInteraction().setAction(ScreenInteractionAction.OPEN_CITY);
        config.getComponents().get(0).getInteraction().setTarget("https://example.invalid");
        config.getComponents().get(0).getFormat().setDisplayUnit(ScreenDisplayUnit.PERCENT);

        assertThat(ScreenDisplayContractValidator.validate(config))
                .anyMatch(issue -> issue.contains("展示协议版本"))
                .anyMatch(issue -> issue.contains("自定义标题"))
                .anyMatch(issue -> issue.contains("交互目标"))
                .anyMatch(issue -> issue.contains("单位类型冲突"));
    }

    @Test
    void originalSourceUnitCannotUseAutoAndRequiredObjectsCannotDisappear() {
        ScreenDisplayConfigDTO config = config();
        config.getComponents().get(0).getDataRefs().get(0).setUnit(ScreenDisplayUnit.AUTO);
        config.getComponents().get(0).setFormat(null);
        config.getComponents().get(0).setInteraction(null);

        assertThat(ScreenDisplayContractValidator.validate(config))
                .anyMatch(issue -> issue.contains("来源原始单位不能为AUTO"))
                .anyMatch(issue -> issue.contains("format不能为空"))
                .anyMatch(issue -> issue.contains("interaction不能为空"));
    }

    @Test
    void duplicateSeriesIdentityAndIncompleteSeriesAreRejected() {
        ScreenDisplayConfigDTO config = config();
        ScreenDisplayComponentDTO trend = config.getComponents().get(1);
        ScreenDisplaySeriesDTO first = trend.getContent().getSeries().get(0);
        ScreenDisplaySeriesDTO duplicate = series("deposit", ScreenDisplayUnit.AUTO);
        duplicate.setLabel(" ");
        trend.getContent().setSeries(List.of(first, duplicate));

        assertThat(ScreenDisplayContractValidator.validate(config))
                .anyMatch(issue -> issue.contains("seriesKey缺失或重复"))
                .anyMatch(issue -> issue.contains("series单位不合法"));
    }

    @Test
    void incomeRatioAllowsPendingOrConfiguredAmountInputs() {
        ScreenDisplayContentDTO content = config().getComponents().get(0).getContent();
        ScreenDisplayIncomeRatioDTO pending = new ScreenDisplayIncomeRatioDTO();
        pending.setNumeratorField("");
        pending.setDenominatorField("");
        pending.setUnit(ScreenDisplayUnit.YUAN);
        content.setIncomeRatio(pending);
        assertThat(ScreenDisplayContractValidator.validate(config())).isEmpty();

        pending.setNumeratorField("intermediaryIncome");
        pending.setDenominatorField("operatingRevenue");
        assertThat(ScreenDisplayContractValidator.validate(config())).isEmpty();
    }

    @Test
    void incomeRatioRejectsPartialInputsAndRatioUnit() {
        ScreenDisplayConfigDTO config = config();
        ScreenDisplayContentDTO content = config.getComponents().get(0).getContent();
        ScreenDisplayIncomeRatioDTO ratio = new ScreenDisplayIncomeRatioDTO();
        ratio.setNumeratorField("intermediaryIncome");
        ratio.setDenominatorField("");
        ratio.setUnit(ScreenDisplayUnit.YUAN);
        content.setIncomeRatio(ratio);
        assertThat(ScreenDisplayContractValidator.validate(config))
                .anyMatch(issue -> issue.contains("incomeRatio"));

        ratio.setDenominatorField("operatingRevenue");
        ratio.setUnit(ScreenDisplayUnit.PERCENT);
        assertThat(ScreenDisplayContractValidator.validate(config))
                .anyMatch(issue -> issue.contains("incomeRatio"));
    }

    private ScreenDisplayConfigDTO config() {
        ScreenDisplayConfigDTO config = new ScreenDisplayConfigDTO();
        config.setDisplaySchemaVersion(1);
        List<ScreenDisplayComponentDTO> components = new ArrayList<>();
        components.add(component("deposit-card", ScreenComponentType.METRIC_CARD, 11L));
        ScreenDisplayComponentDTO trend = component("trend-main", ScreenComponentType.TREND, 12L);
        trend.getContent().setSeries(List.of(series("deposit", ScreenDisplayUnit.YUAN),
                series("loan", ScreenDisplayUnit.YUAN)));
        components.add(trend);
        config.setComponents(components);
        return config;
    }

    private ScreenDisplayComponentDTO component(String id, ScreenComponentType type, long blockId) {
        ScreenDisplayComponentDTO component = new ScreenDisplayComponentDTO();
        component.setComponentId(id);
        component.setComponentType(type);
        component.setLayoutRegion(ScreenLayoutRegion.LEFT);
        component.setOrder(0);
        component.setVisible(true);
        ScreenDisplayTextDTO text = new ScreenDisplayTextDTO();
        text.setTitleMode(ScreenTitleMode.CUSTOM);
        text.setTitle("自定义存款");
        component.setText(text);
        ScreenDisplayFormatDTO format = new ScreenDisplayFormatDTO();
        format.setDisplayUnit(ScreenDisplayUnit.YUAN);
        format.setDecimals(2);
        component.setFormat(format);
        ScreenDisplayInteractionDTO interaction = new ScreenDisplayInteractionDTO();
        interaction.setAction(ScreenInteractionAction.NONE);
        component.setInteraction(interaction);
        ScreenDisplayContentDTO content = new ScreenDisplayContentDTO();
        content.setMainField("value");
        component.setContent(content);
        component.setDataRefs(List.of(ref(blockId)));
        return component;
    }

    private ScreenDisplayDataRefDTO ref(long blockId) {
        ScreenDisplayDataRefDTO ref = new ScreenDisplayDataRefDTO();
        ref.setBlockId(blockId);
        ref.setRole(ScreenDataRefRole.PRIMARY);
        ref.setMetricCode("TEST_METRIC");
        ref.setMetricName("测试指标");
        ref.setUnit(ScreenDisplayUnit.YUAN);
        ref.setDimension(ScreenSourceDimension.ORG);
        return ref;
    }

    private ScreenDisplaySeriesDTO series(String field, ScreenDisplayUnit unit) {
        ScreenDisplaySeriesDTO series = new ScreenDisplaySeriesDTO();
        series.setSeriesKey(field);
        series.setField(field);
        series.setLabel(field);
        series.setUnit(unit);
        return series;
    }
}
