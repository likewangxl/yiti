# 审批流程设计器 P3（现有流程只读导入）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: subagent-driven-development。逐任务执行。
> **子代理红线**：model 须为高能力模型。后端在 **lf**（/home/djdev/lf/yiti，分支 feature/wf-flow-designer-p1）。

**Goal:** 把现有 3 条已部署流程（perf_target_adjust_v1 / perf_alloc_adjust_corp_v1 / perf_alloc_adjust_retail_v1）反向导入成 WF_FLOW_* **只读模型**（is_readonly_import=1），使设计器「查看所有流程」展示现状、可克隆；零影响线上。

**Architecture:** 新增 FlowImportService：用 `RepositoryService.getBpmnModel(最新 procDefId)` 读结构 + `WF_NODE_CANDIDATE_CONF` 读审批人，映射成 FlowGraphDTO，经 FlowDefService 新增的 `createImported` 落库（isReadonlyImport=1, sourceProcDefKey=procKey）。提供 admin 导入端点触发；幂等（按 sourceProcDefKey 去重）。

**Tech Stack:** Flowable `RepositoryService`/`BpmnModel` + 既有 FlowDefService/Mapper + JUnit5/Mockito。

**依据 spec:** `docs/superpowers/specs/2026-05-29-workflow-flow-designer-design.md` §10。

## 既有可复用
- FlowDefService（service.flow）：含 4 Mapper + ObjectMapper + nodeKey→id 落库逻辑（saveGraph）。
- FlowGraphDTO/FlowNodeDTO/FlowEdgeDTO/FlowApproverDTO（api.dto.flow）。
- WfFlowDef 含 isReadonlyImport / sourceProcDefKey 字段；WfFlowDefMapper.selectByFlowKey。
- NodeCandidateConfMapper（按 procDefKey+nodeKey 查候选）；WfNodeCandidateConf{candidateType,candidateValue(JSON 数组)}。
- 前端（P2）已支持只读展示：FlowList 显示「只读导入」tag，FlowEdit 对 readonly 禁编辑+提示克隆。

## 设计要点 / 取舍
- 节点映射：StartEvent→START、UserTask→APPROVAL（MI 多实例→approveMode=ALL，否则 ANY）、ExclusiveGateway→GATEWAY、EndEvent→END；nodeKey=元素 id，name=元素 name。
- 审批人：APPROVAL 节点按 (procKey,nodeKey) 读 WF_NODE_CANDIDATE_CONF，每行 candidateValue JSON 数组展开为多个 FlowApproverDTO{candidateType,value}。
- 连线：SequenceFlow→edge(fromNodeKey=sourceRef,toNodeKey=targetRef)。**条件取舍**：现有 BPMN 网关条件用 `${corpRouteTo==...}` 等非白名单变量，无法表示为 FlowConditionDTO；故 **condition_json 置 null**，把原始 conditionExpression 文本拼到 edge.name（如 `[条件] ${corpRouteTo=='LEADER'}`）仅供只读展示。isDefault：网关 defaultFlow 指向的边置 1。
- bizType 推导：key 含 `alloc`→ALLOC_ADJUST，含 `target`→TARGET_ADJUST，否则 UNKNOWN。
- 只读：status=PUBLISHED、version=0、isReadonlyImport=1、sourceProcDefKey=procKey、deployedProcDefKey=null。
- 幂等：已存在 sourceProcDefKey 相同的 WfFlowDef 则跳过（除非 force）。

---

## Task 1: FlowDefService.createImported + FlowImportService（TDD）

**Files（lf）:**
- Modify: `workflow-center/.../service/flow/FlowDefService.java`（加 createImported）
- Create: `workflow-center/.../service/flow/FlowImportService.java`
- Test: `workflow-center/.../service/flow/FlowImportServiceTest.java`

- [ ] **Step 1: 失败测试 FlowImportServiceTest**（@ExtendWith(MockitoExtension.class)；mock RepositoryService、NodeCandidateConfMapper、FlowDefService、ProcessDefinition、BpmnModel）

构造一个真 `org.flowable.bpmn.model.BpmnModel`（用 API new Process + StartEvent("start") + UserTask("a1") + EndEvent("end") + 2 SequenceFlow），mock：
- `repositoryService.createProcessDefinitionQuery()...latestVersion().singleResult()` 返回 procDef（getId/getKey/getName）。
- `repositoryService.getBpmnModel(procDefId)` 返回上面的 BpmnModel。
- `nodeCandidateConfMapper.selectByProcessDefKeyAndNodeKey("perf_alloc_adjust_corp_v1","a1")` 返回 1 行 ROLE candidateValue=`["CORP_DEPT"]`。
- `flowDefMapper.selectByFlowKey(...)` 返回 null（未导入过）。

断言：`importFromDeployed("perf_alloc_adjust_corp_v1")` 调用 `flowDefService.createImported(graphCaptor, eq("perf_alloc_adjust_corp_v1"))`，捕获的 FlowGraphDTO：nodes 含 start(START)/a1(APPROVAL,approvers=[ROLE:CORP_DEPT])/end(END)，edges 2 条，bizType=ALLOC_ADJUST。
再加 `idempotent_skipsIfAlreadyImported`：flowDefMapper.selectByFlowKey 返回已存在 → 不调 createImported（返回已存在 id 或跳过标志）。

- [ ] **Step 2: Run 红** `mvn -q -pl workflow-center test -Dtest=FlowImportServiceTest -Dsurefire.failIfNoSpecifiedTests=false`。
- [ ] **Step 3: 实现**
  - FlowDefService 新增 `String createImported(FlowGraphDTO graph, String sourceProcKey)`：复用其私有落库逻辑（nodeKey→id 插 nodes/approvers/edges），但 def 设 isReadonlyImport=1、sourceProcDefKey=sourceProcKey、status="PUBLISHED"、version=0、flowKey="imported_"+sourceProcKey。返回 flowDefId。
  - FlowImportService（@Service，注入 RepositoryService、NodeCandidateConfMapper、ObjectMapper、FlowDefService、WfFlowDefMapper）：`importFromDeployed(procKey)`：幂等检查（selectByFlowKey("imported_"+procKey) 非空→return 其 id）；查最新 procDef→getBpmnModel→遍历 flowElements 映射 nodes（含 MI 判定）+edges（条件文本拼 name、default 边）→APPROVAL 节点查候选配置组装 approvers→bizType 推导→build FlowGraphDTO→flowDefService.createImported。
- [ ] **Step 4: Run 绿。**
- [ ] **Step 5: Commit** `feat(workflow): 现有流程反向导入只读模型 service（TDD）`。

---

## Task 2: 导入端点 + 资源登记

**Files（lf）:**
- Modify: `workflow-center/.../controller/FlowDesignController.java`（加导入端点）
- Test: 扩展 `FlowDesignControllerTest`
- Modify/Create: 资源 SQL（追加 1 条）

- [ ] **Step 1: 失败测试**：MockMvc `POST /api/admin/workflow/flows/import-existing` → 200，verify flowImportService.importFromDeployed 对 3 个已知 key 各调一次（控制器内置 3 个 key 列表）。
- [ ] **Step 2: 实现**：FlowDesignController 注入 FlowImportService，加
  `POST /api/admin/workflow/flows/import-existing`（@BizAuth SYS_CONFIG/CONFIG）：对内置 3 个 key 逐个 importFromDeployed，返回导入/跳过的 flowKey 列表（ResponseWrapper<List<String>>）。
- [ ] **Step 3: Run 绿。**
- [ ] **Step 4: 资源 SQL**：在 docs/superpowers/sql/2026-05-29-wf-flow-designer-resources.sql 追加 1 条 PT_RESOURCE（W_FLOW_IMP，URL /api/admin/workflow/flows/import-existing，POST，SYS_CODE=WF）+ 绑定到与其它 W_FLOW_* 相同的 20 角色（NOT EXISTS 幂等）。在 yiti 执行。
- [ ] **Step 5: Commit** `feat(workflow): 现有流程导入端点 + 资源登记`。

---

## Task 3: 执行导入 + 验证

- [ ] **Step 1: install + 重启**：`mvn install -pl workflow-center -DskipTests`，停旧 bootstrap，后台起 `mvn -f bootstrap/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev`，确认 Started + 18080。
- [ ] **Step 2: 触发导入**：登录管理员后（或临时放行）调 `POST /api/admin/workflow/flows/import-existing`。若无法自动登录，则改为临时用一段 SQL 校验路径或交用户手工调用；优先尝试用已知管理员账号 curl 带 session。
- [ ] **Step 3: 验证 DB**：
```sql
SELECT flow_key, biz_type, status, is_readonly_import, source_proc_def_key FROM WF_FLOW_DEF WHERE is_readonly_import=1;
-- 期望 3 行（imported_perf_target_adjust_v1 / _corp_v1 / _retail_v1）
SELECT d.flow_key, COUNT(n.id) nodes FROM WF_FLOW_DEF d LEFT JOIN WF_FLOW_NODE n ON n.flow_def_id=d.id WHERE d.is_readonly_import=1 GROUP BY d.flow_key;
```
- [ ] **Step 4: 验证幂等**：再调一次 import-existing，DB 行数不变（跳过）。
- [ ] **Step 5: 报告** + Commit（若有收尾）`chore(workflow): P3 现有流程只读导入验证`。

---

## Self-Review
- spec §10 覆盖：只读导入 service（T1）+ 触发端点（T2）+ 执行验证（T3）。前端只读展示 P2 已做。
- 零影响：只新增 service/端点 + 插入 is_readonly_import=1 的模型行；不动现有 BPMN/业务/线上流程。
- 取舍已记：网关条件因非白名单变量不结构化（condition=null + 文本入 name 供展示），只读流程不可编辑发布故可接受。
- 类型一致：FlowGraphDTO/FlowApproverDTO/condition 形与 P1 一致；createImported 复用 FlowDefService 落库逻辑（DRY）。
