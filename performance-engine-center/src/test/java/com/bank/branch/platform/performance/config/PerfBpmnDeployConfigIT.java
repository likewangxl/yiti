package com.bank.branch.platform.performance.config;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1.2 Phase Q0.3 BPMN 部署占位守护测试.
 *
 * <p>本测试验证 performance-engine-center 自有的 3 个 BPMN 流程文件：
 * <ul>
 *     <li>{@code bpmn/perf_alloc_adjust_corp_v1.bpmn20.xml} —— 对公分配调整</li>
 *     <li>{@code bpmn/perf_alloc_adjust_retail_v1.bpmn20.xml} —— 零售分配调整</li>
 *     <li>{@code bpmn/perf_target_adjust_v1.bpmn20.xml} —— 目标修正</li>
 * </ul>
 *
 * <p>验证方式：
 * <ol>
 *     <li>3 个 BPMN XML 文件均存在于 classpath:bpmn/ 目录下（会被 bootstrap 的
 *         {@code flowable.process-definition-location-prefix=classpath*:/bpmn/} 自动扫描部署）；</li>
 *     <li>使用 Flowable 官方 {@link BpmnXMLConverter} 解析 XML，断言 processDefinitionKey 正确；</li>
 *     <li>3 个流程均 isExecutable=true 且至少包含 Start / 2 个 UserTask / End 节点.</li>
 * </ol>
 *
 * <p>设计说明：
 * 本 IT 不启动 Spring Context，也不初始化 Flowable ProcessEngine（performance 模块测试
 * 未建 ACT_* 表，启动 ProcessEngine 会失败）. 实际部署验证由 bootstrap 集成测试负责：
 * Flowable starter 会扫描 classpath*:/bpmn/**.bpmn20.xml 并自动部署.
 *
 * <p>Red 阶段：3 个 BPMN 资源均不存在，ClassLoader.getResource 返回 null，断言失败.
 * <p>Green 阶段：3 份 BPMN XML 文件创建后通过.
 */
class PerfBpmnDeployConfigIT {

    /** 对公分配调整：processDefinitionKey. */
    private static final String KEY_CORP = "perf_alloc_adjust_corp_v1";

    /** 零售分配调整：processDefinitionKey. */
    private static final String KEY_RETAIL = "perf_alloc_adjust_retail_v1";

    /** 目标修正：processDefinitionKey. */
    private static final String KEY_TARGET = "perf_target_adjust_v1";

    /**
     * 3 个 BPMN 资源文件必须存在于 classpath:bpmn/ 目录.
     */
    @Test
    void allThreeBpmnFiles_exist_onClasspath() {
        assertThat(resource(KEY_CORP))
            .as("对公分配调整 BPMN 应在 classpath:bpmn/%s.bpmn20.xml", KEY_CORP)
            .isNotNull();
        assertThat(resource(KEY_RETAIL))
            .as("零售分配调整 BPMN 应在 classpath:bpmn/%s.bpmn20.xml", KEY_RETAIL)
            .isNotNull();
        assertThat(resource(KEY_TARGET))
            .as("目标修正 BPMN 应在 classpath:bpmn/%s.bpmn20.xml", KEY_TARGET)
            .isNotNull();
    }

    /**
     * 对公分配调整 BPMN 必须解析出 processDefinitionKey=perf_alloc_adjust_corp_v1，
     * 且 isExecutable=true，含至少 2 个 UserTask.
     */
    @Test
    void corpAdjustBpmn_parsedCorrectly() {
        Process process = loadProcess(KEY_CORP);
        assertThat(process.getId()).isEqualTo(KEY_CORP);
        assertThat(process.isExecutable()).isTrue();
        long userTasks = process.getFlowElements().stream()
            .filter(e -> e instanceof org.flowable.bpmn.model.UserTask)
            .count();
        assertThat(userTasks).isGreaterThanOrEqualTo(2L);
    }

    /**
     * 零售分配调整 BPMN 必须解析出 processDefinitionKey=perf_alloc_adjust_retail_v1，
     * 且 isExecutable=true，含至少 2 个 UserTask.
     */
    @Test
    void retailAdjustBpmn_parsedCorrectly() {
        Process process = loadProcess(KEY_RETAIL);
        assertThat(process.getId()).isEqualTo(KEY_RETAIL);
        assertThat(process.isExecutable()).isTrue();
        long userTasks = process.getFlowElements().stream()
            .filter(e -> e instanceof org.flowable.bpmn.model.UserTask)
            .count();
        assertThat(userTasks).isGreaterThanOrEqualTo(2L);
    }

    /**
     * 目标修正 BPMN 必须解析出 processDefinitionKey=perf_target_adjust_v1，
     * 且 isExecutable=true，含至少 2 个 UserTask.
     */
    @Test
    void targetAdjustBpmn_parsedCorrectly() {
        Process process = loadProcess(KEY_TARGET);
        assertThat(process.getId()).isEqualTo(KEY_TARGET);
        assertThat(process.isExecutable()).isTrue();
        long userTasks = process.getFlowElements().stream()
            .filter(e -> e instanceof org.flowable.bpmn.model.UserTask)
            .count();
        assertThat(userTasks).isGreaterThanOrEqualTo(2L);
    }

    /**
     * PerfBpmnDeployConfig 配置类存在，为 V1.2 显式部署入口（Green 阶段创建）.
     *
     * <p>bootstrap 启动时 Flowable starter 会自动扫描 classpath*:/bpmn/，
     * 本配置类作为显式占位标识：V1.2 正式实现时可在此注入 RepositoryService
     * 做条件化部署（如按版本号禁用、分环境策略等）.
     */
    @Test
    void perfBpmnDeployConfig_classExists() throws ClassNotFoundException {
        Class<?> clazz = Class.forName("com.bank.branch.platform.performance.config.PerfBpmnDeployConfig");
        assertThat(clazz).isNotNull();
    }

    // ----------------------- helpers -----------------------

    /** 从 classpath 读 bpmn/<key>.bpmn20.xml 资源. */
    private static URL resource(String processKey) {
        return PerfBpmnDeployConfigIT.class.getClassLoader()
            .getResource("bpmn/" + processKey + ".bpmn20.xml");
    }

    /** 用 Flowable BpmnXMLConverter 解析出第一个 Process 定义. */
    private static Process loadProcess(String processKey) {
        URL url = resource(processKey);
        assertThat(url).as("BPMN %s 资源必须存在", processKey).isNotNull();
        try (InputStream is = url.openStream()) {
            XMLInputFactory xif = XMLInputFactory.newInstance();
            xif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
            xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            XMLStreamReader xsr = xif.createXMLStreamReader(is);
            BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(xsr);
            assertThat(model.getProcesses()).as("BPMN 必须至少含 1 个 Process 定义").isNotEmpty();
            return model.getProcesses().get(0);
        } catch (Exception ex) {
            throw new RuntimeException("解析 BPMN " + processKey + " 失败: " + ex.getMessage(), ex);
        }
    }
}
