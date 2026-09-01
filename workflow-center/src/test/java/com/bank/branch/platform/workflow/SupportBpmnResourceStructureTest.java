package com.bank.branch.platform.workflow;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 中台支持 V1 固定流程资源结构合同测试。
 *
 * <p>驳回不在 BPMN 中建条件网关，由 workflow-center 的任务服务在驳回时强制终止流程；
 * 因此两份资源都必须保持「开始 → 一个用户任务 → 结束」的最小线性结构。</p>
 */
class SupportBpmnResourceStructureTest {

    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";

    @Test
    void simple_support_process_has_expected_linear_shape_and_listeners() throws Exception {
        assertSupportProcess("/bpmn/support_simple_v1.bpmn20.xml", "support_simple_v1",
                "end", "product_owner_handle");
    }

    @Test
    void complex_support_process_has_expected_linear_shape_and_listeners() throws Exception {
        assertSupportProcess("/bpmn/support_complex_v1.bpmn20.xml", "support_complex_v1",
                "end", "dept_secretary_dispatch", "support_staff_handle");
    }

    private void assertSupportProcess(String resource, String processKey,
                                      String endKey, String... taskKeys) throws Exception {
        Document document;
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, "缺少 BPMN 资源: " + resource);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            document = factory.newDocumentBuilder().parse(input);
        }

        Element process = firstElement(document.getElementsByTagNameNS(BPMN_NS, "process"));
        assertNotNull(process, "BPMN 必须声明 process");
        assertEquals(processKey, process.getAttribute("id"));
        assertEquals("true", process.getAttribute("isExecutable"));

        List<Element> flowNodes = elements(process, "startEvent", "userTask", "endEvent", "exclusiveGateway",
                "parallelGateway", "inclusiveGateway", "complexGateway", "callActivity", "serviceTask",
                "scriptTask", "manualTask", "receiveTask", "sendTask", "subProcess");
        assertEquals(taskKeys.length + 2, flowNodes.size(),
                "中台支持固定流程只能包含开始、规定的用户任务和结束节点");

        Element start = childById(process, "startEvent", "start");
        Element end = childById(process, "endEvent", endKey);
        assertNotNull(start);
        assertNotNull(end);

        for (String taskKey : taskKeys) {
            Element task = childById(process, "userTask", taskKey);
            assertNotNull(task, "缺少用户任务: " + taskKey);
            List<Element> taskListeners = descendants(task, FLOWABLE_NS, "taskListener");
            assertEquals(1, taskListeners.size(), "支持任务应只挂一个任务分配监听器: " + taskKey);
            assertEquals("create", taskListeners.get(0).getAttribute("event"));
            assertEquals("${taskAssignmentListener}", taskListeners.get(0).getAttribute("delegateExpression"));
        }

        List<Element> processListeners = descendants(process, FLOWABLE_NS, "executionListener");
        assertEquals(1, processListeners.size(), "支持流程应只挂一个完成监听器");
        assertEquals("end", processListeners.get(0).getAttribute("event"));
        assertEquals("${processCompletedListener}", processListeners.get(0).getAttribute("delegateExpression"));

        List<Element> flows = directChildren(process, "sequenceFlow");
        assertEquals(taskKeys.length + 1, flows.size(), "线性流程顺序流数量应与节点链一致");
        assertFlow(flows, "start", taskKeys[0]);
        for (int i = 0; i < taskKeys.length - 1; i++) {
            assertFlow(flows, taskKeys[i], taskKeys[i + 1]);
        }
        assertFlow(flows, taskKeys[taskKeys.length - 1], endKey);
        assertTrue(flows.stream().allMatch(flow -> !flow.hasAttribute("conditionExpression")),
                "驳回由任务服务强制终止，BPMN 顺序流不应包含审批条件");
    }

    private void assertFlow(List<Element> flows, String source, String target) {
        assertTrue(flows.stream().anyMatch(flow -> source.equals(flow.getAttribute("sourceRef"))
                        && target.equals(flow.getAttribute("targetRef"))),
                "缺少顺序流 " + source + " -> " + target);
    }

    private Element childById(Element parent, String localName, String id) {
        for (Element element : directChildren(parent, localName)) {
            if (id.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        return null;
    }

    private List<Element> elements(Element parent, String... localNames) {
        List<Element> result = new ArrayList<>();
        for (String localName : localNames) {
            result.addAll(directChildren(parent, localName));
        }
        return result;
    }

    private List<Element> directChildren(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && BPMN_NS.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) {
                result.add(element);
            }
        }
        return result;
    }

    private List<Element> descendants(Element parent, String namespace, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getElementsByTagNameNS(namespace, localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add((Element) nodes.item(i));
        }
        return result;
    }

    private Element firstElement(NodeList nodes) {
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }
}
