# 业绩调整审批改走「设计器动态流程」+ 经办人分支选择 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 全程遵守项目 TDD 红线（红-绿-重构，每步提交）。派遣 subagent 时 model 必须 ≥ sonnet。中文回答，UTF-8。

**Goal:** 让业绩调整审批的「审批环节 + 流转分支」完全由审批流程设计器中的「对公分配关系调整审批（设计器）/零售分配关系调整审批（设计器）」动态驱动；申请按客户类型自动走对公/零售流程；当某审批节点有 ≥2 条出边时，把出边「输出名称（流转连线 name）」反显到审批页面供经办人选择走向。原静态 BPMN 审批代码全部保留以便回退（不加运行时开关）。

**Architecture:**
- 设计器流程发布后部署为影子流程定义 `DSN_alloc_corp_designer` / `DSN_alloc_retail_designer`（`deployed_proc_def_key`）。
- perf 的 `AllocAdjustService.resolveProcessKey` 由「返回静态 key」改为「按 custType 解析已发布设计器流程的 `deployedProcDefKey`」；旧静态逻辑改名保留为 `resolveStaticProcessKey`（不调用）。
- 分支选择走**已存在**的链路：`TaskController POST /{taskId}/approve` 已收 `ApproveReqDTO.formData` → `approveByEmp(...,formData)` → 写流程变量 → 排他网关按 `corpRouteTo`/`finRouteTo` 路由。本计划只补「把当前节点命名出边返给前端」这一段：`TaskDetailRespDTO` 新增 `outgoingBranches`。
- 网关不产生 UserTask，故当前待办必为审批节点；`startOrgLevel` 这类网关分支不会出现在待办里，无需在 UI 处理，只需在起流程时把 `startOrgLevel` 作为启动变量种入。

**Tech Stack:** Spring Boot 3.2.3 / JDK17 / MyBatis-Plus / Flowable 7.0.1（仅 workflow-center 直连）/ MySQL8（库名 yiti，root/djdev，ALTER 需 yiti+onepl 双跑——本计划无 DDL）/ 前端 Vue3 + Element Plus（在 `/home/djdev/wangyq/yiti/xanzc_frontend`）。

**构建/测试约定（项目既有坑）：**
- 跨模块改动后先 `mvn clean install -DskipTests`（portal 协作者 WIP 测试编译坏，用 `-Dmaven.test.skip=true` 跳过测试编译做 install/package）。
- 单模块单测：`mvn -pl workflow-center test -Dtest=XxxTest -Dmaven.test.skip=false`（perf 同理 `-pl performance-engine-center`）。
- 已知 2 个 EXPR/Groovy 预存失败测试（MetricCalc/MetricTrial）与本特性无关，忽略。
- 后端跑在 18080，登录 admin/123456；curl 加 `--noproxy '*'`。

---

## 文件结构（创建/修改清单）

**workflow-center（暴露出边 + 解析已发布设计器 procDefKey）**
- 修改 `api/dto/TaskDetailRespDTO.java` — 新增字段 `List<BranchOptionDTO> outgoingBranches`。
- 创建 `api/dto/BranchOptionDTO.java` — 出边选项 DTO（outputName / toNodeKey / isDefault / routeVariables）。
- 修改 `mapper/WfFlowDefMapper.java` — 新增 `selectByDeployedProcDefKey(key)`（MyBatis-Plus LambdaQuery，无需 XML）。
- 修改 `service/TodoQueryService.java` — `getTaskDetail` 末尾计算并填充 `outgoingBranches`；注入 `FlowDefService` + `WfFlowDefMapper`。
- 修改 `api/WorkflowApi.java` — 新增 `String resolveDesignerProcDefKey(String flowKey)`。
- 修改 `facade/WorkflowFacade.java` — 实现该方法（校验 PUBLISHED + deployedProcDefKey 非空）。
- 测试 `src/test/java/.../service/TodoQueryServiceBranchTest.java`、`.../facade/WorkflowFacadeResolveDesignerTest.java`。

**performance-engine-center（切换到设计器流程 + 种入路由起始变量）**
- 修改 `service/adjust/AllocAdjustService.java` — `resolveProcessKey` 改解析设计器流程；旧逻辑改名 `resolveStaticProcessKey` 保留；`startApprovalWorkflow` 种入 `startOrgLevel`；新增常量 `FLOW_KEY_CORP_DESIGNER` / `FLOW_KEY_RETAIL_DESIGNER`。
- 测试 `src/test/java/.../service/adjust/AllocAdjustServiceDesignerRouteTest.java`。

**前端（审批页分支选择器，在 wangyq）**
- 修改业绩调整审批详情/审批组件 — 当 `outgoingBranches.length>=2` 渲染单选（label=outputName），提交时把所选项 `routeVariables` 并入 approve 的 `formData`。

**运维（一次性，每环境执行一次）**
- 通过 `POST /api/admin/workflow/flows/{FDEF_ALLOC_CORP|FDEF_ALLOC_RETAIL}/publish?operator=admin` 发布两条流程；验证 `deployed_proc_def_key` 落为 `DSN_alloc_corp_designer` / `DSN_alloc_retail_designer`。

---

## Task 0：发布两条设计器流程并捕获其所需启动变量（前置经验性验证）

**目的**：发布必须先于一切代码改动——后续 perf 解析 `deployedProcDefKey` 依赖它；同时通过生成的 BPMN 确认运行期需要哪些**启动变量**（重点 `startOrgLevel`）与**审批人候选**是否解析正确。

**Files:** 无代码改动（数据 + 验证）。

- [ ] **Step 1: 启动后端（若未运行）**

Run:
```bash
cd /home/djdev/lf/yiti && pgrep -f bootstrap-1.0.0-SNAPSHOT.jar || (mvn -q clean install -Dmaven.test.skip=true && nohup java -jar bootstrap/target/bootstrap-1.0.0-SNAPSHOT.jar > /tmp/boot.log 2>&1 &)
```
Expected: 18080 端口监听（`ss -ltnp | grep 18080`）。

- [ ] **Step 2: 登录拿 Cookie 并发布对公流程**

Run:
```bash
cd /tmp
curl -s --noproxy '*' -c cj.txt -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' http://localhost:18080/api/auth/login >/dev/null
curl -s --noproxy '*' -b cj.txt -X POST 'http://localhost:18080/api/admin/workflow/flows/FDEF_ALLOC_CORP/publish?operator=admin'
```
Expected: `{"code":"0"...}` 成功；若返回 `FLOW_PUBLISH_VALIDATION_FAILED`，记录 message 里的结构错误明细——**此时停止**并把校验失败明细反馈给用户（DRAFT 流程图不完整，需先在设计器补全，非本计划代码问题）。

- [ ] **Step 3: 发布零售流程**

Run:
```bash
curl -s --noproxy '*' -b cj.txt -X POST 'http://localhost:18080/api/admin/workflow/flows/FDEF_ALLOC_RETAIL/publish?operator=admin'
```
Expected: 成功。

- [ ] **Step 4: 验证 deployed_proc_def_key 落库**

Run:
```bash
mysql -uroot -pdjdev yiti -t -e "SELECT flow_key,status,deployed_proc_def_key FROM WF_FLOW_DEF WHERE id IN ('FDEF_ALLOC_CORP','FDEF_ALLOC_RETAIL');"
```
Expected: 两行 status=PUBLISHED，deployed_proc_def_key 分别为 `DSN_alloc_corp_designer`、`DSN_alloc_retail_designer`。

- [ ] **Step 5: 捕获对公流程所需启动变量**

Run:
```bash
mysql -uroot -pdjdev yiti -t -e "SELECT DISTINCT condition_json FROM WF_FLOW_EDGE WHERE flow_def_id='FDEF_ALLOC_CORP' AND condition_json IS NOT NULL;"
```
Expected: 列出全部条件字段。把出现的字段分两类记录到本任务备注：
  - **审批人选择型**（节点出边、由审批人填）：`corpRouteTo`、`finRouteTo` —— 走 Task 6 的 formData/前端分支选择，不在启动时种。
  - **启动决定型**（网关分支、起流程即定）：`startOrgLevel` —— 必须在 Task 7 的 `startApprovalWorkflow` 启动变量里种入。
  零售流程同样跑一遍（`flow_def_id='FDEF_ALLOC_RETAIL'`）。**此清单是 Task 7 种变量的依据**。

- [ ] **Step 6: 提交（仅文档记录，无代码）**

把 Step 5 的变量清单追加到本计划文件 Task 7 备注处并提交：
```bash
cd /home/djdev/lf/yiti
git add docs/superpowers/plans/2026-06-18-alloc-adjust-designer-flow.md
git commit -F - <<'EOF'
docs(perf/alloc): 记录设计器流程启动变量清单（startOrgLevel 等）

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

## Task 1：BranchOptionDTO（出边选项 DTO）

**Files:**
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/api/dto/BranchOptionDTO.java`

- [ ] **Step 1: 创建 DTO（无逻辑，下一 Task 的测试会用到它）**

```java
package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 审批节点「下一步走向」分支选项 DTO。
 * <p>
 * 当当前审批节点在设计器图中存在 ≥2 条命名出边（流转连线 name）时，
 * 每条命名出边转为一个本 DTO 返给前端，供经办人选择走哪条分支；
 * 选中后前端把 {@link #routeVariables} 并入审批 formData，
 * 驱动发布后 BPMN 排他网关（如 {@code corpRouteTo}/{@code finRouteTo}）路由。
 * </p>
 */
@Data
public class BranchOptionDTO {

    /** 分支输出名称（WF_FLOW_EDGE.name，页面展示标签，如「部门负责人审批」） */
    private String outputName;

    /** 目标节点 nodeKey（展示/调试用） */
    private String toNodeKey;

    /** 是否默认分支（无条件，选它仅靠 BPMN 默认流转，routeVariables 为空） */
    private Boolean isDefault;

    /**
     * 选中该分支须写入审批 formData 的流程变量。
     * 由该出边 condition 中的 EQ 条件解析（field→value），如 {@code {"corpRouteTo":"LEADER"}}。
     */
    private Map<String, Object> routeVariables;
}
```

- [ ] **Step 2: 编译通过**

Run: `cd /home/djdev/lf/yiti && mvn -q -pl workflow-center -am compile -Dmaven.test.skip=true`
Expected: BUILD SUCCESS。

- [ ] **Step 3: 提交**

```bash
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/api/dto/BranchOptionDTO.java
git commit -F - <<'EOF'
feat(wf/flow): 新增 BranchOptionDTO 承载审批节点出边选项

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

## Task 2：WfFlowDefMapper.selectByDeployedProcDefKey

**Files:**
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/mapper/WfFlowDefMapper.java`
- Test: `workflow-center/src/test/java/com/bank/branch/platform/workflow/mapper/WfFlowDefMapperTest.java`（如已有同名测试类则追加方法）

- [ ] **Step 1: 写失败测试**

新建/追加（若已有该测试类，仅加本方法）：
```java
package com.bank.branch.platform.workflow.mapper;

import com.bank.branch.platform.workflow.entity.WfFlowDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WfFlowDefMapper.selectByDeployedProcDefKey 行为契约测试（纯 mock，不连库）。
 * 仅验证「按 deployedProcDefKey 精确返回单条 / 未命中返 null」契约，
 * 真实 SQL 由 default 方法的 LambdaQueryWrapper 保证，集成层另测。
 */
class WfFlowDefMapperTest {

    @Test
    void selectByDeployedProcDefKey_hit_returnsDef() {
        WfFlowDefMapper mapper = mock(WfFlowDefMapper.class);
        WfFlowDef def = new WfFlowDef();
        def.setId("FDEF_ALLOC_CORP");
        def.setDeployedProcDefKey("DSN_alloc_corp_designer");
        when(mapper.selectByDeployedProcDefKey("DSN_alloc_corp_designer")).thenReturn(def);

        WfFlowDef got = mapper.selectByDeployedProcDefKey("DSN_alloc_corp_designer");
        assertEquals("FDEF_ALLOC_CORP", got.getId());
    }

    @Test
    void selectByDeployedProcDefKey_miss_returnsNull() {
        WfFlowDefMapper mapper = mock(WfFlowDefMapper.class);
        when(mapper.selectByDeployedProcDefKey("DSN_none")).thenReturn(null);
        assertNull(mapper.selectByDeployedProcDefKey("DSN_none"));
    }
}
```
（注：mock 测试用于锁定方法签名存在；真实查询语义由 default 方法实现保证。若仓库已有 mapper 集成测试基建[H2]，改为集成断言更佳。）

- [ ] **Step 2: 跑测试，确认编译失败（方法不存在）**

Run: `mvn -q -pl workflow-center test -Dtest=WfFlowDefMapperTest -Dmaven.test.skip=false`
Expected: 编译失败 `cannot find symbol: method selectByDeployedProcDefKey`。

- [ ] **Step 3: 用 default 方法 + LambdaQuery 实现（MyBatis-Plus 规范，无 XML）**

在 `WfFlowDefMapper` 接口内、`selectByFlowKey` 下方追加：
```java
    /**
     * 根据已部署的 Flowable 流程定义 KEY（deployed_proc_def_key，如
     * {@code DSN_alloc_corp_designer}）反查设计器流程定义。
     * <p>供运行期由「当前任务的 processDefinitionKey」反查其设计器图，
     * 计算审批节点的命名出边选项。未命中返回 null。</p>
     *
     * @param deployedProcDefKey 已部署流程定义 KEY
     * @return 流程定义，未命中返回 null
     */
    default WfFlowDef selectByDeployedProcDefKey(String deployedProcDefKey) {
        return selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<WfFlowDef>lambdaQuery()
                .eq(WfFlowDef::getDeployedProcDefKey, deployedProcDefKey)
                .last("LIMIT 1"));
    }
```

- [ ] **Step 4: 跑测试通过**

Run: `mvn -q -pl workflow-center test -Dtest=WfFlowDefMapperTest -Dmaven.test.skip=false`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/mapper/WfFlowDefMapper.java \
        workflow-center/src/test/java/com/bank/branch/platform/workflow/mapper/WfFlowDefMapperTest.java
git commit -F - <<'EOF'
feat(wf/flow): WfFlowDefMapper 新增 selectByDeployedProcDefKey 反查

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

## Task 3：TaskDetailRespDTO 新增 outgoingBranches 字段

**Files:**
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/api/dto/TaskDetailRespDTO.java`

- [ ] **Step 1: 加字段**

在 `approvalLogs` 字段下方追加：
```java
    /**
     * 当前审批节点的「下一步走向」分支选项（来自设计器图当前节点的命名出边）。
     * <p>仅设计器动态流程（procDefKey 以 DSN_ 前缀）任务会填充；静态 BPMN 任务为空列表。
     * 前端当 size≥2 时渲染分支单选，选中项的 routeVariables 并入审批 formData。</p>
     */
    private List<BranchOptionDTO> outgoingBranches;
```
（`BranchOptionDTO` 与本类同包 `...api.dto`，无需 import；`List` 已 import。）

- [ ] **Step 2: 编译通过**

Run: `mvn -q -pl workflow-center -am compile -Dmaven.test.skip=true`
Expected: BUILD SUCCESS。

- [ ] **Step 3: 提交**

```bash
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/api/dto/TaskDetailRespDTO.java
git commit -F - <<'EOF'
feat(wf/task): TaskDetailRespDTO 新增 outgoingBranches 出边选项字段

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

## Task 4：TodoQueryService 计算 outgoingBranches

**Files:**
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/service/TodoQueryService.java`
- Test: `workflow-center/src/test/java/com/bank/branch/platform/workflow/service/TodoQueryServiceBranchTest.java`

> 设计：从 `task` 取 `procDefKey=extractProcessDefinitionKey(task.getProcessDefinitionId())` 与 `nodeKey=task.getTaskDefinitionKey()`。仅当 procDefKey 以 `DSN_` 开头才计算（静态流程返空，向后兼容）。`flowDef=flowDefMapper.selectByDeployedProcDefKey(procDefKey)`；`graph=flowDefService.getGraph(flowDef.getId())`；筛 `fromNodeKey==nodeKey && outputName!=null` 的边，每条转 BranchOptionDTO，routeVariables 取该边 condition 内 op=EQ 的 (field→value)。

- [ ] **Step 1: 写失败测试（抽出纯函数便于单测）**

```java
package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.workflow.api.dto.BranchOptionDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowConditionDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO;
import com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TodoQueryService.computeOutgoingBranches 纯函数单测：
 * 给定流程图 + 当前 nodeKey，正确产出命名出边选项与路由变量。
 */
class TodoQueryServiceBranchTest {

    private FlowEdgeDTO edge(String from, String to, String name, String field, String val) {
        FlowEdgeDTO e = new FlowEdgeDTO();
        e.setFromNodeKey(from);
        e.setToNodeKey(to);
        e.setOutputName(name);
        e.setIsDefault(false);
        if (field != null) {
            FlowConditionDTO c = new FlowConditionDTO();
            c.setLogic("AND");
            c.setConditions(List.of(new FlowConditionDTO.Cond(field, "EQ", val)));
            e.setCondition(c);
        }
        return e;
    }

    @Test
    void computeOutgoingBranches_twoNamedEdges_returnsBothWithRouteVars() {
        FlowGraphDTO g = new FlowGraphDTO();
        g.setEdges(List.of(
                edge("biz_dept_review", "biz_dept_leader_approve", "部门负责人审批", "corpRouteTo", "LEADER"),
                edge("biz_dept_review", "original_owner_approve", "原业绩所属人会签", "corpRouteTo", "OWNER"),
                // 结构边（无名）不应出现
                edge("biz_dept_review", "someGw", null, null, null)
        ));

        List<BranchOptionDTO> branches = TodoQueryService.computeOutgoingBranches(g, "biz_dept_review");

        assertEquals(2, branches.size());
        assertEquals("部门负责人审批", branches.get(0).getOutputName());
        assertEquals("LEADER", branches.get(0).getRouteVariables().get("corpRouteTo"));
        assertEquals("OWNER", branches.get(1).getRouteVariables().get("corpRouteTo"));
    }

    @Test
    void computeOutgoingBranches_otherNode_returnsEmpty() {
        FlowGraphDTO g = new FlowGraphDTO();
        g.setEdges(List.of(edge("finance_review", "x", "资财部负责人审批", "finRouteTo", "LEADER")));
        assertTrue(TodoQueryService.computeOutgoingBranches(g, "biz_dept_review").isEmpty());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -q -pl workflow-center test -Dtest=TodoQueryServiceBranchTest -Dmaven.test.skip=false`
Expected: 编译失败 `cannot find symbol: method computeOutgoingBranches`。

- [ ] **Step 3: 实现纯函数 + 在 getTaskDetail 接线**

3a. 在 `TodoQueryService` 顶部注入两个协作者——把字段区改为：
```java
    private final ObjectMapper objectMapper;
    private final CurrentUserApi currentUserApi;
    private final UserApi userApi;
    private final OrgApi orgApi;
    private final com.bank.branch.platform.workflow.service.flow.FlowDefService flowDefService;
    private final com.bank.branch.platform.workflow.mapper.WfFlowDefMapper flowDefMapper;
```
（类已 `@RequiredArgsConstructor`，加 final 字段即自动进构造器；`FlowDefService`/`WfFlowDefMapper` 同模块 Spring Bean，可注入。）

3b. 新增静态纯函数（放在类内 `extractProcessDefinitionKey` 附近）：
```java
    /**
     * 从设计器流程图中提取「当前节点的命名出边」作为审批分支选项。
     * <p>只取 fromNodeKey==当前 nodeKey 且 outputName 非空的边；routeVariables 取该边
     * condition 内 op=EQ 的 field→value（驱动发布后排他网关路由）。无图/无命中返回空列表。</p>
     *
     * @param graph   设计器流程图（FlowDefService.getGraph 结果）
     * @param nodeKey 当前任务节点 key（task.taskDefinitionKey）
     * @return 分支选项列表（可能为空，不为 null）
     */
    static List<com.bank.branch.platform.workflow.api.dto.BranchOptionDTO> computeOutgoingBranches(
            com.bank.branch.platform.workflow.api.dto.flow.FlowGraphDTO graph, String nodeKey) {
        List<com.bank.branch.platform.workflow.api.dto.BranchOptionDTO> result = new ArrayList<>();
        if (graph == null || graph.getEdges() == null || nodeKey == null) {
            return result;
        }
        for (com.bank.branch.platform.workflow.api.dto.flow.FlowEdgeDTO e : graph.getEdges()) {
            if (!nodeKey.equals(e.getFromNodeKey()) || e.getOutputName() == null) {
                continue; // 只暴露当前节点的「命名」出边（结构边 name 为 null 跳过）
            }
            com.bank.branch.platform.workflow.api.dto.BranchOptionDTO b =
                    new com.bank.branch.platform.workflow.api.dto.BranchOptionDTO();
            b.setOutputName(e.getOutputName());
            b.setToNodeKey(e.getToNodeKey());
            b.setIsDefault(Boolean.TRUE.equals(e.getIsDefault()));
            Map<String, Object> vars = new LinkedHashMap<>();
            com.bank.branch.platform.workflow.api.dto.flow.FlowConditionDTO cond = e.getCondition();
            if (cond != null && cond.getConditions() != null) {
                for (com.bank.branch.platform.workflow.api.dto.flow.FlowConditionDTO.Cond c : cond.getConditions()) {
                    if ("EQ".equals(c.getOp()) && c.getField() != null) {
                        vars.put(c.getField(), c.getValue()); // 选中该分支须写入的路由变量
                    }
                }
            }
            b.setRouteVariables(vars);
            result.add(b);
        }
        return result;
    }
```

3c. 在 `getTaskDetail` 的 `return detail;` 之前接线：
```java
        // 8. 设计器动态流程：填充当前节点命名出边作为「下一步走向」分支选项（静态 BPMN 任务为空）
        detail.setOutgoingBranches(resolveOutgoingBranches(processDefinitionKey, task.getTaskDefinitionKey()));
```
并新增私有方法：
```java
    /**
     * 设计器动态流程任务的出边分支解析：仅 procDefKey 以 DSN_ 前缀的流程才反查设计器图。
     * 任何异常/未命中均返回空列表，绝不阻断任务详情主流程。
     */
    private List<com.bank.branch.platform.workflow.api.dto.BranchOptionDTO> resolveOutgoingBranches(
            String processDefinitionKey, String nodeKey) {
        if (processDefinitionKey == null || !processDefinitionKey.startsWith("DSN_")) {
            return new ArrayList<>();
        }
        try {
            com.bank.branch.platform.workflow.entity.WfFlowDef def =
                    flowDefMapper.selectByDeployedProcDefKey(processDefinitionKey);
            if (def == null) {
                return new ArrayList<>();
            }
            return computeOutgoingBranches(flowDefService.getGraph(def.getId()), nodeKey);
        } catch (Exception e) {
            log.warn("[TodoQueryService.resolveOutgoingBranches] 出边解析失败 procDefKey={}, nodeKey={}",
                    processDefinitionKey, nodeKey, e);
            return new ArrayList<>();
        }
    }
```
（`LinkedHashMap` 已 import；`WfFlowDef` 实体全限定即可。）

- [ ] **Step 4: 跑测试通过**

Run: `mvn -q -pl workflow-center test -Dtest=TodoQueryServiceBranchTest -Dmaven.test.skip=false`
Expected: PASS。

- [ ] **Step 5: 回归编译整模块**

Run: `mvn -q -pl workflow-center -am compile -Dmaven.test.skip=true`
Expected: BUILD SUCCESS。

- [ ] **Step 6: 提交**

```bash
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/service/TodoQueryService.java \
        workflow-center/src/test/java/com/bank/branch/platform/workflow/service/TodoQueryServiceBranchTest.java
git commit -F - <<'EOF'
feat(wf/task): 任务详情填充设计器流程当前节点命名出边作为分支选项

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

## Task 5：WorkflowApi.resolveDesignerProcDefKey + Facade 实现

**Files:**
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/api/WorkflowApi.java`
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/facade/WorkflowFacade.java`
- Test: `workflow-center/src/test/java/com/bank/branch/platform/workflow/facade/WorkflowFacadeResolveDesignerTest.java`

- [ ] **Step 1: 接口加方法**

在 `WorkflowApi` 末尾 `rejectByEmp` 之后追加：
```java
    /**
     * 按设计器流程 flowKey 解析其「已发布」的 Flowable 流程定义 KEY（deployed_proc_def_key）。
     * <p>供 perf 等业务模块在起流程时按 custType 选对公/零售设计器流程；
     * 流程未发布（status≠PUBLISHED 或 deployed_proc_def_key 为空）时抛 WF-40401，
     * 调用方据此 fail-fast 提示「请先发布对应审批流程」。</p>
     *
     * @param flowKey 设计器流程唯一键（如 alloc_corp_designer）
     * @return 已部署流程定义 KEY（如 DSN_alloc_corp_designer）
     * @throws com.bank.branch.platform.common.web.exception.BizException WF-40401 流程不存在或未发布
     */
    String resolveDesignerProcDefKey(String flowKey);
```

- [ ] **Step 2: 写失败测试**

```java
package com.bank.branch.platform.workflow.facade;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.entity.WfFlowDef;
import com.bank.branch.platform.workflow.mapper.WfFlowDefMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WorkflowFacade.resolveDesignerProcDefKey：已发布→返 key；未发布/不存在→抛 BizException。
 */
class WorkflowFacadeResolveDesignerTest {

    private WfFlowDef def(String status, String key) {
        WfFlowDef d = new WfFlowDef();
        d.setFlowKey("alloc_corp_designer");
        d.setStatus(status);
        d.setDeployedProcDefKey(key);
        return d;
    }

    @Test
    void resolve_published_returnsKey() {
        WfFlowDefMapper mapper = mock(WfFlowDefMapper.class);
        when(mapper.selectByFlowKey("alloc_corp_designer"))
                .thenReturn(def("PUBLISHED", "DSN_alloc_corp_designer"));
        WorkflowFacade facade = WorkflowFacade.forResolveTest(mapper);
        assertEquals("DSN_alloc_corp_designer", facade.resolveDesignerProcDefKey("alloc_corp_designer"));
    }

    @Test
    void resolve_draft_throws() {
        WfFlowDefMapper mapper = mock(WfFlowDefMapper.class);
        when(mapper.selectByFlowKey("alloc_corp_designer")).thenReturn(def("DRAFT", null));
        WorkflowFacade facade = WorkflowFacade.forResolveTest(mapper);
        assertThrows(BizException.class, () -> facade.resolveDesignerProcDefKey("alloc_corp_designer"));
    }

    @Test
    void resolve_missing_throws() {
        WfFlowDefMapper mapper = mock(WfFlowDefMapper.class);
        when(mapper.selectByFlowKey("none")).thenReturn(null);
        WorkflowFacade facade = WorkflowFacade.forResolveTest(mapper);
        assertThrows(BizException.class, () -> facade.resolveDesignerProcDefKey("none"));
    }
}
```
> 注：`WorkflowFacade.forResolveTest(mapper)` 是为隔离测试新增的包级工厂；若 `WorkflowFacade` 现有构造器协作者不多，可直接 new 真实构造器并把无关依赖传 null/mock。实现 Step 时按 facade 实际构造器选其一，保证测试只依赖 `WfFlowDefMapper`。

- [ ] **Step 3: 跑测试确认失败**

Run: `mvn -q -pl workflow-center test -Dtest=WorkflowFacadeResolveDesignerTest -Dmaven.test.skip=false`
Expected: 编译失败（方法/工厂不存在）。

- [ ] **Step 4: Facade 实现**

4a. 确保 `WorkflowFacade` 注入了 `WfFlowDefMapper`（若未注入，在其字段区加 `private final WfFlowDefMapper flowDefMapper;`，`@RequiredArgsConstructor` 自动接线；若为手写构造器则补参）。

4b. 实现方法（`implements WorkflowApi`）：
```java
    @Override
    public String resolveDesignerProcDefKey(String flowKey) {
        com.bank.branch.platform.workflow.entity.WfFlowDef def = flowDefMapper.selectByFlowKey(flowKey);
        if (def == null || !"PUBLISHED".equals(def.getStatus())
                || def.getDeployedProcDefKey() == null || def.getDeployedProcDefKey().isBlank()) {
            throw new com.bank.branch.platform.common.web.exception.BizException(
                    com.bank.branch.platform.workflow.enums.WfErrorCode.PROCESS_DEFINITION_NOT_FOUND.getCode(),
                    "设计器审批流程未发布或不存在，flowKey=" + flowKey);
        }
        return def.getDeployedProcDefKey();
    }
```
（`WfErrorCode` 里 WF-40401 对应的枚举名按实际为准——前面 startProcess 文档写 WF-40401「流程定义不存在」，用同一枚举。若枚举名不同，grep `40401` 取准确名。）

4c. 加包级测试工厂（仅当 Step 2 选了 `forResolveTest` 路线）：
```java
    /** 仅供单测隔离 resolveDesignerProcDefKey：其余协作者置 null。 */
    static WorkflowFacade forResolveTest(WfFlowDefMapper flowDefMapper) {
        WorkflowFacade f = new WorkflowFacade(/* 其余构造参数按实际填 null */);
        // 若 flowDefMapper 为 final 字段无法二次赋值，则改用反射或在 Step2 直接走真实构造器
        return f;
    }
```
> 实操建议：若 `WorkflowFacade` 构造参数过多导致 `forResolveTest` 笨重，**优先**把 `resolveDesignerProcDefKey` 的核心逻辑下沉到一个轻量 `@Service`（如 `DesignerFlowLookupService`，仅依赖 `WfFlowDefMapper`），Facade 委托它；测试直接测该 service。二选一，保持测试只依赖 mapper。

- [ ] **Step 5: 跑测试通过**

Run: `mvn -q -pl workflow-center test -Dtest=WorkflowFacadeResolveDesignerTest -Dmaven.test.skip=false`
Expected: PASS。

- [ ] **Step 6: install workflow-center 到本地 .m2（perf 下游测试要用新接口）**

Run: `mvn -q -pl workflow-center -am install -Dmaven.test.skip=true`
Expected: BUILD SUCCESS（避免 perf 编译时撞旧 jar，见 CLAUDE.md stale jar 章节）。

- [ ] **Step 7: 提交**

```bash
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/api/WorkflowApi.java \
        workflow-center/src/main/java/com/bank/branch/platform/workflow/facade/WorkflowFacade.java \
        workflow-center/src/test/java/com/bank/branch/platform/workflow/facade/WorkflowFacadeResolveDesignerTest.java
git commit -F - <<'EOF'
feat(wf/api): 新增 resolveDesignerProcDefKey 按 flowKey 解析已发布设计器流程

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

## Task 6：AllocAdjustService 切换到设计器流程（保留旧静态逻辑）

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustService.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustServiceDesignerRouteTest.java`

- [ ] **Step 1: 写失败测试（对公→corp designer key，零售→retail designer key）**

```java
package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.workflow.api.WorkflowApi;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AllocAdjustService 路由改造：custType=CORP→对公设计器流程，RETAIL→零售设计器流程，
 * 经 WorkflowApi.resolveDesignerProcDefKey(flowKey) 解析 deployedProcDefKey。
 */
class AllocAdjustServiceDesignerRouteTest {

    private String invokeResolve(AllocAdjustService svc, String custType, String bizKind) throws Exception {
        Method m = AllocAdjustService.class.getDeclaredMethod("resolveProcessKey", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(svc, custType, bizKind);
    }

    @Test
    void resolveProcessKey_corp_usesCorpDesignerFlow() throws Exception {
        WorkflowApi workflowApi = mock(WorkflowApi.class);
        when(workflowApi.resolveDesignerProcDefKey("alloc_corp_designer")).thenReturn("DSN_alloc_corp_designer");
        AllocAdjustService svc = AllocAdjustService.forRouteTest(workflowApi);
        assertEquals("DSN_alloc_corp_designer", invokeResolve(svc, "CORP", "CORP_LOAN"));
    }

    @Test
    void resolveProcessKey_retail_usesRetailDesignerFlow() throws Exception {
        WorkflowApi workflowApi = mock(WorkflowApi.class);
        when(workflowApi.resolveDesignerProcDefKey("alloc_retail_designer")).thenReturn("DSN_alloc_retail_designer");
        AllocAdjustService svc = AllocAdjustService.forRouteTest(workflowApi);
        assertEquals("DSN_alloc_retail_designer", invokeResolve(svc, "RETAIL", "RETAIL_CARD"));
    }
}
```
> `forRouteTest(workflowApi)` 为隔离工厂（其余依赖 null）。若 `@RequiredArgsConstructor` 生成的全参构造器过宽，则在 service 内加：
> ```java
> static AllocAdjustService forRouteTest(WorkflowApi workflowApi) {
>     return new AllocAdjustService(null,null,null,workflowApi,null,null,null,null,null);
> }
> ```
> 参数顺序须与字段声明顺序一致（applyMapper,itemMapper,allocRelationMapper,workflowApi,currentUserApi,userApi,orgApi,perfScopeHelper,allocAdjustPreviewService）。

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=AllocAdjustServiceDesignerRouteTest -Dmaven.test.skip=false`
Expected: 失败（resolveProcessKey 仍返回静态 key / 工厂不存在）。

- [ ] **Step 3: 改 resolveProcessKey，旧逻辑改名保留**

3a. 在常量区（`WORKFLOW_PREFIX` 附近）新增：
```java
    /** 对公分配关系调整审批（设计器）flowKey. */
    public static final String FLOW_KEY_CORP_DESIGNER = "alloc_corp_designer";

    /** 零售分配关系调整审批（设计器）flowKey. */
    public static final String FLOW_KEY_RETAIL_DESIGNER = "alloc_retail_designer";
```

3b. 把现有 `resolveProcessKey` 整体改名为 `resolveStaticProcessKey` 并加废弃注记（保留以便回退，不删）：
```java
    /**
     * 【已停用·保留回退】原静态 BPMN 路由：对公→perf_alloc_adjust_corp_v1，零售→perf_alloc_adjust_retail_v1。
     * V1.x 起业绩调整审批改走设计器动态流程（{@link #resolveProcessKey}）；本方法保留以便快速回退，
     * 当前不被调用。回退方式：把 {@link #resolveProcessKey} 内部改回 {@code return resolveStaticProcessKey(...)}。
     */
    @Deprecated
    private String resolveStaticProcessKey(String custType, String bizKind) {
        // ……（原方法体原样保留，不改一行）……
    }
```

3c. 新增 active 的 `resolveProcessKey`（替换原签名位置）：
```java
    /**
     * 按客户类型解析「已发布设计器审批流程」的 Flowable 流程定义 KEY：
     * 对公（CORP）→ {@link #FLOW_KEY_CORP_DESIGNER}，零售/个人/中间业务 → {@link #FLOW_KEY_RETAIL_DESIGNER}，
     * 再经 {@link WorkflowApi#resolveDesignerProcDefKey} 取已部署 procDefKey（未发布则 fail-fast）。
     *
     * <p>custType 为空时回退按 bizKind 前缀判定（兼容旧数据），判定口径与
     * {@link #resolveStaticProcessKey} 一致。原静态 BPMN 逻辑保留于该方法以便回退。</p>
     *
     * @param custType 客户类型 CORP/RETAIL（优先）
     * @param bizKind  业务种类（custType 为空时回退依据）
     * @return 已发布设计器流程的 deployedProcDefKey
     * @throws PerfException BIZ_KIND_INVALID 当 bizKind 不属于任何已知族
     */
    private String resolveProcessKey(String custType, String bizKind) {
        String flowKey = resolveDesignerFlowKey(custType, bizKind);
        return workflowApi.resolveDesignerProcDefKey(flowKey);
    }

    /**
     * 客户类型/业务种类 → 设计器 flowKey（对公 vs 零售族）。判定口径同原静态路由。
     */
    private String resolveDesignerFlowKey(String custType, String bizKind) {
        if (custType != null && !custType.isBlank()) {
            String ct = custType.toUpperCase();
            if ("CORP".equals(ct)) return FLOW_KEY_CORP_DESIGNER;
            if ("RETAIL".equals(ct)) return FLOW_KEY_RETAIL_DESIGNER;
        }
        String upper = bizKind == null ? "" : bizKind.toUpperCase();
        if (upper.startsWith("CORP_") || upper.equals("CORP")) {
            return FLOW_KEY_CORP_DESIGNER;
        }
        if (upper.startsWith("RETAIL_") || upper.equals("RETAIL")
                || upper.startsWith("PER_") || upper.equals("PER")
                || upper.startsWith("FEE_") || upper.equals("FEE_BIZ")) {
            return FLOW_KEY_RETAIL_DESIGNER;
        }
        throw new PerfException(PerfErrorCode.BIZ_KIND_INVALID, bizKind);
    }
```

3d. 加测试工厂（Step1 用）：
```java
    /** 仅供 resolveProcessKey 路由单测：除 workflowApi 外其余依赖置 null。 */
    static AllocAdjustService forRouteTest(WorkflowApi workflowApi) {
        return new AllocAdjustService(null, null, null, workflowApi, null, null, null, null, null);
    }
```

- [ ] **Step 4: 跑测试通过**

Run: `mvn -q -pl performance-engine-center test -Dtest=AllocAdjustServiceDesignerRouteTest -Dmaven.test.skip=false`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustServiceDesignerRouteTest.java
git commit -F - <<'EOF'
feat(perf/alloc): 业绩调整审批路由切换到已发布设计器流程（静态逻辑改名保留回退）

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

## Task 7：startApprovalWorkflow 种入设计器流程所需启动变量（startOrgLevel 等）

> **依据 Task 0 Step 5 捕获的启动变量清单填写本任务。** 已知至少需 `startOrgLevel`（对公流程按发起机构级别 2/3 分流的网关变量）。下方以 `startOrgLevel` 为例；若清单含更多启动型变量，按同法补齐。审批人选择型变量（corpRouteTo/finRouteTo）**不**在此种入。

**Files:**
- Modify: `performance-engine-center/.../service/adjust/AllocAdjustService.java`（`startApprovalWorkflow`）
- Test: `performance-engine-center/.../service/adjust/AllocAdjustServiceStartVarsTest.java`

- [ ] **Step 1: 写失败测试（断言 vars 含 startOrgLevel，取自 OrgApi.orgLevel）**

```java
package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * startApprovalWorkflow 须把发起机构级别种为启动变量 startOrgLevel（对公网关分流依据）。
 * 这里测抽出的纯函数 buildStartVariables(ownerOrgId)。
 */
class AllocAdjustServiceStartVarsTest {

    @Test
    void buildStartVariables_putsStartOrgLevelFromOrgApi() throws Exception {
        OrgApi orgApi = mock(OrgApi.class);
        OrgDTO org = new OrgDTO();
        org.setOrgLevel(2);
        when(orgApi.getOrgByCode("0201")).thenReturn(org);

        AllocAdjustService svc = AllocAdjustService.forStartVarsTest(orgApi);
        Method m = AllocAdjustService.class.getDeclaredMethod("buildStartVariables", String.class, Map.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> vars = (Map<String, Object>) m.invoke(svc, "0201", new java.util.HashMap<String,Object>());

        assertEquals("2", vars.get("startOrgLevel"));
    }
}
```
> `OrgApi` 取机构的方法名以实际为准（grep `OrgApi` 找按 orgCode 取 OrgDTO 的方法，如 `getOrgByCode`/`getOrg`）。`startOrgLevel` 存字符串以匹配 condition_json 的 `value:"2"`（FlowConditionExpressionBuilder 对纯数字 value 会按数字比较——核对 Task 0 的 condition：`{field:startOrgLevel,op:EQ,value:"2"}` → EL `startOrgLevel == 2`，数字比较；则应存 **Integer 2** 而非字符串。**以 Task 0 实测 EL 为准**择类型，并相应改断言）。

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=AllocAdjustServiceStartVarsTest -Dmaven.test.skip=false`
Expected: 失败。

- [ ] **Step 3: 抽出 buildStartVariables 并在 startApprovalWorkflow 调用**

3a. 把 `startApprovalWorkflow` 里组装 `vars` 的段落抽成：
```java
    /**
     * 组装流程启动变量。在原有 applyId/custId/... 基础上，补种设计器流程网关分流所需的
     * 「启动决定型」变量 startOrgLevel（发起机构行政级别，取 OrgApi.orgLevel）。
     * 审批人选择型变量（corpRouteTo/finRouteTo）不在此种入，由审批时 formData 提供。
     *
     * @param ownerOrgId 发起/归属机构编码
     * @param vars       已含基础变量的可变 Map（原地补充并返回）
     * @return 补充后的变量 Map
     */
    private Map<String, Object> buildStartVariables(String ownerOrgId, Map<String, Object> vars) {
        if (!isBlank(ownerOrgId)) {
            try {
                OrgDTO org = orgApi.getOrgByCode(ownerOrgId); // 方法名以 OrgApi 实际为准
                if (org != null && org.getOrgLevel() != null) {
                    // 类型（Integer vs String）以 Task 0 实测网关 EL 为准
                    vars.put("startOrgLevel", String.valueOf(org.getOrgLevel()));
                }
            } catch (Exception e) {
                log.warn("[AllocAdjustService.buildStartVariables] 取机构级别失败 ownerOrgId={}, err={}",
                        ownerOrgId, e.toString());
            }
        }
        return vars;
    }
```

3b. 在 `startApprovalWorkflow` 内，`startCmd.setVariables(vars);` 之前插入：
```java
        // 设计器流程网关分流：种入发起机构级别等启动变量
        buildStartVariables(cmd.getOwnerOrgId(), vars);
```

3c. 加测试工厂：
```java
    /** 仅供 buildStartVariables 单测：除 orgApi 外其余依赖置 null。 */
    static AllocAdjustService forStartVarsTest(OrgApi orgApi) {
        return new AllocAdjustService(null, null, null, null, null, null, orgApi, null, null);
    }
```

- [ ] **Step 4: 跑测试通过**

Run: `mvn -q -pl performance-engine-center test -Dtest=AllocAdjustServiceStartVarsTest -Dmaven.test.skip=false`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustServiceStartVarsTest.java
git commit -F - <<'EOF'
feat(perf/alloc): 起流程种入 startOrgLevel 等设计器网关分流启动变量

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

## Task 8：端到端集成验证（提交→对公流程→经办选分支→走对应节点）

**Files:** 无新代码（手工/脚本验证）。先 `mvn clean install -Dmaven.test.skip=true` 重启后端。

- [ ] **Step 1: 重新构建并重启后端**

```bash
cd /home/djdev/lf/yiti && mvn -q clean install -Dmaven.test.skip=true \
 && pkill -9 -f bootstrap-1.0.0-SNAPSHOT.jar; sleep 2 \
 && nohup java -jar bootstrap/target/bootstrap-1.0.0-SNAPSHOT.jar > /tmp/boot.log 2>&1 &
```
Expected: 18080 起来，日志无 ConflictingBeanDefinition。

- [ ] **Step 2: 提交一笔对公分配调整申请**

用既有对公调整提交接口（参考前端「业绩调整」提交报文，custType=CORP）提交一笔；记下返回 applyId。
Expected: 返回 status=IN_APPROVAL；`SELECT process_instance_id FROM perf_alloc_adjust_apply WHERE id='<applyId>'` 非空，且该 pid 在 `BIZ_PROCESS_MAP` 的 proc def key 为 `DSN_alloc_corp_designer`：
```bash
mysql -uroot -pdjdev yiti -t -e "SELECT m.business_key, m.process_status FROM BIZ_PROCESS_MAP m WHERE m.business_key='ALLOC_ADJUST:<applyId>';"
```

- [ ] **Step 3: 公司部经办取任务详情，断言 outgoingBranches 反显**

以公司部经办身份登录 → GET 待办 → 取该任务 taskId → `GET /api/workflow/tasks/{taskId}`。
Expected: 响应含 `outgoingBranches`，至少 2 项，outputName 含「部门负责人审批」「原业绩所属人会签」，各带 `routeVariables.corpRouteTo`。

- [ ] **Step 4: 选一条分支审批，断言流程走对应节点**

`POST /api/workflow/tasks/{taskId}/approve`，body：
```json
{"opinion":"同意-走部门负责人","formData":{"corpRouteTo":"LEADER"}}
```
Expected: 下一个待办落在「公司部负责人审批」节点（验证 `ACT_RU_TASK` 当前 task 的 name/taskDefinitionKey）。再用另一笔申请选 `corpRouteTo=OWNER`，验证走「原业绩所属人会签」。

- [ ] **Step 5: 零售流程冒烟**

custType=RETAIL 提交一笔，重复 Step 3-4，验证资财部经办节点 outgoingBranches（finRouteTo: LEADER/END），选 END 应直接走向结束。

- [ ] **Step 6: 回归——审批通过到底，确认 AllocAdjustCompletedListener 仍生效**

走完一条流程到 APPROVED，确认 `cust_alloc_relation` 新分配写入、原分配 end_date=今日（既有 listener 逻辑不受影响）。
Expected: 与改造前一致。

> 本任务为人工/脚本验证，不强制提交；若编写了可重复的集成测试（`*IT.java`），按 `mvn verify -pl bootstrap` 跑并提交。

---

## Task 9：前端审批页分支选择器（wangyq）

**Files（在 `/home/djdev/wangyq/yiti/xanzc_frontend`）:**
- Modify: 业绩调整审批的任务详情/审批弹窗组件（定位：搜 `outgoingBranches` 消费点 / `tasks/${taskId}/approve` 调用处；业绩调整审批 UI 属 perf 域，参考 reference_frontend_apps_and_workflow_ui）。

- [ ] **Step 1: 定位审批组件与 approve 调用**

Run: `cd /home/djdev/wangyq/yiti/xanzc_frontend && grep -rn "tasks/.*approve\|getTaskDetail\|outgoingBranches" src/`
Expected: 找到审批通过的方法与任务详情数据源。

- [ ] **Step 2: 渲染分支单选（仅当 outgoingBranches.length>=2）**

在审批弹窗「审批意见」上方加（Element Plus）：
```vue
<el-form-item
  v-if="taskDetail.outgoingBranches && taskDetail.outgoingBranches.length >= 2"
  label="下一步走向" required>
  <el-radio-group v-model="selectedBranchIndex">
    <el-radio
      v-for="(b, i) in taskDetail.outgoingBranches"
      :key="i" :label="i">{{ b.outputName }}</el-radio>
  </el-radio-group>
</el-form-item>
```
data 增 `selectedBranchIndex: null`。

- [ ] **Step 3: 提交审批时把所选分支 routeVariables 并入 formData**

在 approve 提交方法里：
```js
const branches = this.taskDetail.outgoingBranches || []
let formData = {}
if (branches.length >= 2) {
  if (this.selectedBranchIndex === null || this.selectedBranchIndex === undefined) {
    this.$message.warning('请选择下一步走向')
    return
  }
  formData = { ...(branches[this.selectedBranchIndex].routeVariables || {}) }
}
await approveTask(this.taskId, { opinion: this.opinion, formData })
```
（approveTask 的请求体已是 `{opinion, formData}`，与 ApproveReqDTO 对齐。）

- [ ] **Step 4: 前端联调验证**

启动前端（vite 在 wangyq 实跑），走 Task 8 同样的对公申请：公司部经办审批页应出现「下一步走向」单选，选「原业绩所属人会签」提交后流程走会签节点。
Expected: UI 正常、路由正确。

- [ ] **Step 5: 提交（前端 commit 留在 wangyq，按既有前后端目录约定）**

```bash
cd /home/djdev/wangyq/yiti/xanzc_frontend
git add -A
git commit -F - <<'EOF'
feat(perf/alloc): 审批页按设计器出边输出名称反显分支供经办人选择走向

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

## Self-Review（对照 spec 核查）

**Spec 覆盖：**
- 「整个审批环节和分支由设计器流程实现」→ Task 0（发布）+ Task 6（perf 起流程改用 deployedProcDefKey）。✓
- 「申请按客户类型自动走对公/零售」→ Task 6 `resolveDesignerFlowKey`（CORP→corp，RETAIL→retail）。✓
- 「≥2 分支时反显出边输出名称供选择」→ Task 1/3/4（后端暴露 outgoingBranches）+ Task 9（前端单选）。✓
- 「所选分支驱动流转」→ 复用既有 approve formData→流程变量→网关；Task 4 解析 routeVariables、Task 8/9 验证。✓
- 「原代码保留可回退、不加开关」→ Task 6 旧逻辑改名 `resolveStaticProcessKey` 保留、静态 BPMN xml 不删；回退仅需一行改回。✓

**类型/签名一致性：**
- `BranchOptionDTO.routeVariables: Map<String,Object>` 与前端 `formData` 一致、与 `ApproveReqDTO.formData: Map<String,Object>` 一致。✓
- `resolveDesignerProcDefKey(String): String` 在 Task5 定义、Task6 调用，签名一致。✓
- `computeOutgoingBranches(FlowGraphDTO,String)` Task4 定义并自测。✓

**已知风险/留待执行期确认（已在对应 Task 标注）：**
1. Task 0 发布可能因 DRAFT 图不完整触发 FlowValidator 失败 → 需用户在设计器补图（非代码）。
2. Task 7 `startOrgLevel` 的**值类型（Integer/String）**与是否还有其他启动变量，以 Task 0 实测 condition/EL 为准。
3. `OrgApi` 取机构方法名、`WfErrorCode` 的 WF-40401 枚举名 → 执行时 grep 取准。
4. Facade/Service 隔离测试工厂（forRouteTest 等）若构造器过宽笨重，按 Task5 建议下沉轻量 service 测之。
