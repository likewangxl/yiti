package com.bank.branch.platform.performance.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分行经营大屏自动批次配置。
 *
 * <p>配置只描述批次编排所需的指标和目标方案，不承载业务数据。生产分类缺省为
 * {@code PROD}，测试 profile 必须显式写 {@code TEST}，以避免人工样本被误当作正式口径。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "perf.branch-dashboard")
public class BranchDashboardBatchProperties {

    /** 是否启用批次服务。 */
    private boolean enabled;

    /** 数据分类：TEST 或 PROD。 */
    private String dataClassification = "PROD";

    /** 命名机构组编码。 */
    private String groupCode;

    /** 目标方案编码。 */
    private String targetPlanCode;

    /** 实际值指标编码。 */
    private String actualMetricCode;

    /** 选择完整金融业务日时必须全部非空的指标编码。 */
    private List<String> upstreamMetricCodes = new ArrayList<>();

    /** 客户数输出指标编码。 */
    private String customerMetricCode;

    /** 待跟进数输出指标编码。 */
    private String attentionMetricCode;

    /** 目标值输出指标编码。 */
    private String targetMetricCode;

    /** 机构达成率输出指标编码。 */
    private String orgRateMetricCode;

    /** 集团贡献率输出指标编码。 */
    private String groupContributionMetricCode;

    /** 历史窗口天数；缺日不补零。 */
    private int historyDays = 30;

    /** 由 JobApi 注册的批次 cron；默认每 5 分钟，实际配置落入 SYS_JOB_CONF。 */
    private String cron = "0 */5 * * * ?";

    /** TEST 分类允许声明的指标预期单位，键为 metricCode。 */
    private Map<String, String> expectedUnits = new LinkedHashMap<>();
}
