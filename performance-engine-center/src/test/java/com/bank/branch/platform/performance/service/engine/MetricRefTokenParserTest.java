package com.bank.branch.platform.performance.service.engine;

import com.bank.branch.platform.performance.enums.MetricValueTimeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricRefTokenParserTest {

    @Test
    void parse_plainCode_isToday() {
        List<MetricRefTokenParser.RefToken> t = MetricRefTokenParser.parse("M_A + 1");
        assertThat(t).hasSize(1);
        assertThat(t.get(0).token()).isEqualTo("M_A");
        assertThat(t.get(0).baseCode()).isEqualTo("M_A");
        assertThat(t.get(0).timePoint()).isEqualTo(MetricValueTimeEnum.TODAY);
    }

    @Test
    void parse_suffixedCode_splitsBaseAndTimePoint() {
        List<MetricRefTokenParser.RefToken> t = MetricRefTokenParser.parse("M_A - M_A__D1 + M_B__PME");
        assertThat(t).extracting("token").containsExactly("M_A", "M_A__D1", "M_B__PME");
        assertThat(t).extracting("baseCode").containsExactly("M_A", "M_A", "M_B");
        assertThat(t).extracting("timePoint").containsExactly(
                MetricValueTimeEnum.TODAY, MetricValueTimeEnum.D1, MetricValueTimeEnum.PME);
    }

    @Test
    void parse_underscoreCode_notMisSplit() {
        List<MetricRefTokenParser.RefToken> t = MetricRefTokenParser.parse("M_AUM_TOTAL + M_AUM_TOTAL__PYE");
        assertThat(t.get(0).baseCode()).isEqualTo("M_AUM_TOTAL");
        assertThat(t.get(0).timePoint()).isEqualTo(MetricValueTimeEnum.TODAY);
        assertThat(t.get(1).baseCode()).isEqualTo("M_AUM_TOTAL");
        assertThat(t.get(1).timePoint()).isEqualTo(MetricValueTimeEnum.PYE);
    }

    @Test
    void parse_deDupsKeepingOrder() {
        List<MetricRefTokenParser.RefToken> t = MetricRefTokenParser.parse("M_A + M_A - M_A__D1");
        assertThat(t).extracting("token").containsExactly("M_A", "M_A__D1");
    }

    @Test
    void parse_blankOrNull_empty() {
        assertThat(MetricRefTokenParser.parse(null)).isEmpty();
        assertThat(MetricRefTokenParser.parse("  ")).isEmpty();
    }
}
