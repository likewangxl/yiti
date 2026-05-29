# 审批流程设计器 P1（后端引擎）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> **子代理红线**：派遣任何 subagent 时 model 必须 ≥ sonnet（禁用 haiku）。

**Goal:** 在 workflow-center 建成"DB 流程模型 → 生成 BPMN → 部署到影子 key"的后端引擎与管理 API，默认零影响现有审批。

**Architecture:** 新增 4 张 `WF_FLOW_*` 表存流程模型；`FlowBpmnGenerator` 用 Flowable `BpmnModel` Java API 把模型编程式生成 BPMN，发布时部署到**影子 processDefinitionKey**（与线上 key 不同），并把审批人规则写入现有 `WF_NODE_CANDIDATE_CONF` 以复用现有 `TaskAssignmentListener`。现有 BPMN/业务/监听器零改动。

**Tech Stack:** Spring Boot 3.2.3 + Flowable 7.0.1（`BpmnModel`/`RepositoryService`）+ MyBatis + MySQL(yiti) + JUnit5 + Mockito。

**依据 spec:** `docs/superpowers/specs/2026-05-29-workflow-flow-designer-design.md`

**前置约定:**
- 库表大写（Linux MySQL 大小写敏感）。MySQL root/`djdev`，dev 连 `yiti`。
- 多行 commit 信息用 bash heredoc（`cat <<'EOF' > /tmp/m.txt; git commit -F /tmp/m.txt`），禁用 PowerShell `@'...'@`。
- 跨模块 IT 改了上游需 `mvn install -pl workflow-center -DskipTests` 再跑。
- 影子 key 前缀常量：`DSN_`（如 `DSN_alloc_adjust_corp`）。

---

## 文件结构

```
workflow-center/src/main/java/com/bank/branch/platform/workflow/
├── entity/        WfFlowDef / WfFlowNode / WfFlowNodeApprover / WfFlowEdge
├── mapper/        WfFlowDefMapper / WfFlowNodeMapper / WfFlowNodeApproverMapper / WfFlowEdgeMapper (+ XML)
├── api/dto/flow/  FlowDefDTO / FlowNodeDTO / FlowApproverDTO / FlowEdgeDTO / FlowConditionDTO / FlowGraphDTO / FlowVariableDTO
├── service/flow/
│   ├── FlowConditionExpressionBuilder.java   条件模型→EL
│   ├── FlowBpmnGenerator.java                模型→BpmnModel
│   ├── FlowValidator.java                    发布校验
│   ├── FlowDefService.java                   CRUD/草稿
│   ├── FlowPublishService.java               校验→生成→部署→写候选配置→版本+1
│   └── FlowVariableCatalog.java              bizType→白名单变量
├── listener/      MultiInstanceApproverResolver.java   会签 MI 集合解析
└── controller/    FlowDesignController.java   /api/admin/workflow/flows
workflow-center/src/main/resources/mapper/   WfFlow*Mapper.xml
docs/superpowers/sql/2026-05-29-wf-flow-designer-ddl.sql
```

---

## Task 1: 建 4 张模型表 DDL

**Files:**
- Create: `docs/superpowers/sql/2026-05-29-wf-flow-designer-ddl.sql`

- [ ] **Step 1: 写 DDL 脚本（幂等 IF NOT EXISTS）**

```sql
-- 审批流程设计器模型表（workflow-center）。库表大写。
CREATE TABLE IF NOT EXISTS WF_FLOW_DEF (
  id varchar(32) NOT NULL PRIMARY KEY,
  flow_key varchar(64) NOT NULL,
  biz_type varchar(32) NOT NULL,
  name varchar(128) NOT NULL,
  description varchar(512) NULL,
  status varchar(16) NOT NULL DEFAULT 'DRAFT',
  version int NOT NULL DEFAULT 0,
  deployed_proc_def_key varchar(64) NULL,
  deployed_proc_def_id varchar(64) NULL,
  source_proc_def_key varchar(64) NULL,
  is_readonly_import tinyint NOT NULL DEFAULT 0,
  created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by varchar(32) NULL,
  updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by varchar(32) NULL,
  UNIQUE KEY uk_flow_key (flow_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程设计器-流程定义';

CREATE TABLE IF NOT EXISTS WF_FLOW_NODE (
  id varchar(32) NOT NULL PRIMARY KEY,
  flow_def_id varchar(32) NOT NULL,
  node_key varchar(64) NOT NULL,
  node_type varchar(16) NOT NULL,
  name varchar(128) NULL,
  approve_mode varchar(8) NULL,
  sort_no int NOT NULL DEFAULT 0,
  pos_x int NULL, pos_y int NULL,
  created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_node_flow (flow_def_id),
  UNIQUE KEY uk_flow_node (flow_def_id, node_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程设计器-节点';

CREATE TABLE IF NOT EXISTS WF_FLOW_NODE_APPROVER (
  id varchar(32) NOT NULL PRIMARY KEY,
  node_id varchar(32) NOT NULL,
  approver_type varchar(8) NOT NULL,
  approver_value varchar(64) NOT NULL,
  sort_no int NOT NULL DEFAULT 0,
  created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_approver_node (node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程设计器-节点审批人规则';

CREATE TABLE IF NOT EXISTS WF_FLOW_EDGE (
  id varchar(32) NOT NULL PRIMARY KEY,
  flow_def_id varchar(32) NOT NULL,
  from_node_id varchar(32) NOT NULL,
  to_node_id varchar(32) NOT NULL,
  name varchar(128) NULL,
  is_default tinyint NOT NULL DEFAULT 0,
  condition_json text NULL,
  sort_no int NOT NULL DEFAULT 0,
  created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_edge_flow (flow_def_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程设计器-连线/分支';
```

- [ ] **Step 2: 在 yiti 库执行并验证 4 表存在**

Run: `mysql -uroot -pdjdev -h127.0.0.1 yiti < docs/superpowers/sql/2026-05-29-wf-flow-designer-ddl.sql`
然后：`mysql -uroot -pdjdev -h127.0.0.1 yiti -e "SHOW TABLES LIKE 'WF_FLOW%'"`
Expected: 4 行（WF_FLOW_DEF / WF_FLOW_NODE / WF_FLOW_NODE_APPROVER / WF_FLOW_EDGE）。

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/sql/2026-05-29-wf-flow-designer-ddl.sql
git commit -m "feat(workflow): 审批流程设计器 4 张模型表 DDL"
```

---

## Task 2: 实体 + Mapper（4 套，贫血模型）

**Files:**
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/entity/WfFlowDef.java` (+ WfFlowNode/WfFlowNodeApprover/WfFlowEdge)
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/mapper/WfFlowDefMapper.java` (+ 其余 3 个)
- Create: `workflow-center/src/main/resources/mapper/WfFlowDefMapper.xml` (+ 其余 3 个)
- Test: `workflow-center/src/test/java/com/bank/branch/platform/workflow/mapper/WfFlowDefMapperIT.java`

- [ ] **Step 1: 写实体类**（参照现有 `WfNodeCandidateConf` 风格，lombok `@Data`）

`WfFlowDef` 字段：`id, flowKey, bizType, name, description, status, version, deployedProcDefKey, deployedProcDefId, sourceProcDefKey, isReadonlyImport(Integer), createdTime, createdBy, updatedTime, updatedBy`。
`WfFlowNode`：`id, flowDefId, nodeKey, nodeType, name, approveMode, sortNo, posX, posY, createdTime, updatedTime`。
`WfFlowNodeApprover`：`id, nodeId, approverType, approverValue, sortNo, createdTime`。
`WfFlowEdge`：`id, flowDefId, fromNodeId, toNodeId, name, isDefault(Integer), conditionJson, sortNo, createdTime`。

- [ ] **Step 2: 写 Mapper 接口**（继承 `BaseMapper<T>`，参照 `NodeCandidateConfMapper`）

每个 Mapper：`int insert`(BaseMapper 提供)；自定义 `List<T> selectByFlowDefId(@Param("flowDefId") String)`（node/edge）、`List<WfFlowNodeApprover> selectByNodeIds(@Param("nodeIds") List<String>)`、`WfFlowDef selectByFlowKey(@Param("flowKey") String)`、`int deleteByFlowDefId(@Param("flowDefId") String)`（node/edge 整图替换用）、`int deleteApproverByNodeIds(@Param("nodeIds") List<String>)`。

- [ ] **Step 3: 写 Mapper XML**（`resultMap` 字段↔列映射；表名大写）。

- [ ] **Step 4: 写 Mapper IT（继承现有 workflow Mapper IT 基类）**

参照 workflow-center 现有 `*MapperIT` 的基类与 `@Sql`/事务回滚约定（先 `grep -rl "MapperTestBase\|@MybatisTest\|@SpringBootTest" workflow-center/src/test` 确认基类）。测试：插入 1 个 WfFlowDef + 2 节点 + 2 审批人 + 1 边，按 flowDefId 查回，断言条数与字段；再 `deleteByFlowDefId` 后查回为空。

```java
@Test
void insertAndSelectGraph_roundTrips() {
    WfFlowDef def = new WfFlowDef();
    def.setId("FD_T1"); def.setFlowKey("t1"); def.setBizType("ALLOC_ADJUST");
    def.setName("测试流程"); def.setStatus("DRAFT"); def.setVersion(0); def.setIsReadonlyImport(0);
    flowDefMapper.insert(def);
    // node ×2, approver ×2, edge ×1 ... insert
    assertThat(flowNodeMapper.selectByFlowDefId("FD_T1")).hasSize(2);
    assertThat(flowEdgeMapper.selectByFlowDefId("FD_T1")).hasSize(1);
}
```

- [ ] **Step 5: Run IT 验证通过**

Run: `mvn -q -pl workflow-center test -Dtest=WfFlowDefMapperIT`
Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/entity workflow-center/src/main/java/com/bank/branch/platform/workflow/mapper workflow-center/src/main/resources/mapper workflow-center/src/test/java/com/bank/branch/platform/workflow/mapper/WfFlowDefMapperIT.java
git commit -m "feat(workflow): WF_FLOW_* 实体+Mapper+IT"
```

---

## Task 3: 条件模型 DTO + EL 生成器（纯单元，TDD）

**Files:**
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/api/dto/flow/FlowConditionDTO.java`
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/service/flow/FlowConditionExpressionBuilder.java`
- Test: `workflow-center/src/test/java/com/bank/branch/platform/workflow/service/flow/FlowConditionExpressionBuilderTest.java`

`FlowConditionDTO`：`String logic`（AND/OR）+ `List<Cond> conditions`；内部类 `Cond{String field; String op; String value;}`。

- [ ] **Step 1: 写失败测试**

```java
class FlowConditionExpressionBuilderTest {
    private final FlowConditionExpressionBuilder b = new FlowConditionExpressionBuilder();

    @Test void single_EQ() {
        FlowConditionDTO c = cond("AND", List.of(new Cond("bizKind","EQ","CORP")));
        assertThat(b.toEl(c)).isEqualTo("${bizKind == 'CORP'}");
    }
    @Test void multi_AND() {
        FlowConditionDTO c = cond("AND", List.of(new Cond("bizKind","EQ","CORP"), new Cond("custType","NE","RETAIL")));
        assertThat(b.toEl(c)).isEqualTo("${bizKind == 'CORP' && custType != 'RETAIL'}");
    }
    @Test void in_op() {
        FlowConditionDTO c = cond("AND", List.of(new Cond("bizKind","IN","CORP,PER")));
        assertThat(b.toEl(c)).isEqualTo("${(bizKind == 'CORP' || bizKind == 'PER')}");
    }
    @Test void numeric_GT_unquoted() {
        FlowConditionDTO c = cond("AND", List.of(new Cond("amount","GT","100")));
        assertThat(b.toEl(c)).isEqualTo("${amount > 100}");
    }
    @Test void blank_returnsNull() { assertThat(b.toEl(null)).isNull(); }
    @Test void rejects_unknown_op() {
        FlowConditionDTO c = cond("AND", List.of(new Cond("x","BAD","1")));
        assertThatThrownBy(() -> b.toEl(c)).isInstanceOf(IllegalArgumentException.class);
    }
}
```
（`cond(...)`/`Cond` 为测试辅助构造，映射到 `FlowConditionDTO`。）

- [ ] **Step 2: Run 验证失败**

Run: `mvn -q -pl workflow-center test -Dtest=FlowConditionExpressionBuilderTest`
Expected: FAIL（类不存在/编译失败）。

- [ ] **Step 3: 写实现**

```java
@Component
public class FlowConditionExpressionBuilder {
    private static final Set<String> NUMERIC_OPS = Set.of("GT","GE","LT","LE");
    public String toEl(FlowConditionDTO c) {
        if (c == null || c.getConditions() == null || c.getConditions().isEmpty()) return null;
        String joiner = "OR".equalsIgnoreCase(c.getLogic()) ? " || " : " && ";
        String body = c.getConditions().stream().map(this::one).collect(Collectors.joining(joiner));
        return "${" + body + "}";
    }
    private String one(FlowConditionDTO.Cond x) {
        String f = x.getField(), v = x.getValue();
        switch (x.getOp()) {
            case "EQ": return f + " == " + lit(f, v);
            case "NE": return f + " != " + lit(f, v);
            case "GT": return f + " > " + v;
            case "GE": return f + " >= " + v;
            case "LT": return f + " < " + v;
            case "LE": return f + " <= " + v;
            case "CONTAINS": return f + ".contains('" + esc(v) + "')";
            case "IN":  return inExpr(f, v, "==", " || ");
            case "NOT_IN": return inExpr(f, v, "!=", " && ");
            default: throw new IllegalArgumentException("不支持的运算符: " + x.getOp());
        }
    }
    private String inExpr(String f, String csv, String eq, String join) {
        String body = Arrays.stream(csv.split(",")).map(String::trim)
            .map(s -> f + " " + eq + " '" + esc(s) + "'").collect(Collectors.joining(join));
        return "(" + body + ")";
    }
    private String lit(String f, String v) { // 数字字面量不加引号
        return v != null && v.matches("-?\\d+(\\.\\d+)?") ? v : "'" + esc(v) + "'";
    }
    private String esc(String v) { return v == null ? "" : v.replace("'", "\\'"); }
}
```

- [ ] **Step 4: Run 验证通过**

Run: `mvn -q -pl workflow-center test -Dtest=FlowConditionExpressionBuilderTest`
Expected: PASS（6 用例）。
> 注：`numeric_GT_unquoted` 用 GT 走 `v` 直出；`single_EQ` 的 'CORP' 非数字加引号。确认 `lit` 仅 EQ/NE 用，GT 等直接拼 v。

- [ ] **Step 5: Commit**

```bash
git add workflow-center/src/main/java/com/bank/branch/platform/workflow/api/dto/flow/FlowConditionDTO.java workflow-center/src/main/java/com/bank/branch/platform/workflow/service/flow/FlowConditionExpressionBuilder.java workflow-center/src/test/java/com/bank/branch/platform/workflow/service/flow/FlowConditionExpressionBuilderTest.java
git commit -m "feat(workflow): 条件模型 DTO + EL 生成器（TDD）"
```

---

## Task 4: 流程图 DTO + 发布校验器（TDD）

**Files:**
- Create: `.../api/dto/flow/FlowNodeDTO.java`, `FlowApproverDTO.java`, `FlowEdgeDTO.java`, `FlowGraphDTO.java`
- Create: `.../service/flow/FlowValidator.java`
- Test: `.../service/flow/FlowValidatorTest.java`

DTO：
- `FlowNodeDTO{String nodeKey; String nodeType; String name; String approveMode; Integer sortNo; List<FlowApproverDTO> approvers;}`
- `FlowApproverDTO{String approverType; String approverValue;}`
- `FlowEdgeDTO{String fromNodeKey; String toNodeKey; Boolean isDefault; FlowConditionDTO condition;}`
- `FlowGraphDTO{String name; String bizType; List<FlowNodeDTO> nodes; List<FlowEdgeDTO> edges;}`（编辑/保存以 nodeKey 关联，避免依赖 DB id）

- [ ] **Step 1: 写失败测试**

```java
class FlowValidatorTest {
    private final FlowValidator v = new FlowValidator(new FlowVariableCatalog());

    @Test void valid_linear_passes() {
        FlowGraphDTO g = graph(
          node("start","START"), approvalNode("a1","ANY","ROLE:CORP_DEPT"), node("end","END"));
        g.setEdges(List.of(edge("start","a1"), edge("a1","end")));
        assertThat(v.validate(g, "ALLOC_ADJUST").isOk()).isTrue();
    }
    @Test void missing_start_fails() { /* 无 START → errors 含 "必须恰好 1 个 START" */ }
    @Test void approval_without_approver_fails() { /* APPROVAL 无 approver → error */ }
    @Test void orphan_node_fails() { /* 某节点无入边 → error */ }
    @Test void condition_unknown_field_fails() {
        // edge 条件用 field=notInWhitelist → error
    }
}
```

- [ ] **Step 2: Run 验证失败** — `mvn -q -pl workflow-center test -Dtest=FlowValidatorTest` → FAIL。

- [ ] **Step 3: 写 FlowValidator + FlowVariableCatalog（见 Task 8 提前建 catalog）**

`FlowValidator.validate(FlowGraphDTO, bizType) → ValidationResult{boolean ok; List<String> errors}`。规则（spec §7）：
- 恰好 1 个 nodeType=START；≥1 个 END。
- 除 START 外每节点有入边；除 END 外每节点有出边（按 edges 的 from/to nodeKey 统计）。
- 每个 APPROVAL 节点 `approvers` 非空。
- 每条 edge 的 `condition` 若非空：每个 `cond.field` ∈ `FlowVariableCatalog.fields(bizType)`，且 `op` 合法（复用 builder 的支持集）。

- [ ] **Step 4: Run 验证通过** — PASS。

- [ ] **Step 5: Commit** — `feat(workflow): 流程图 DTO + 发布校验器（TDD）`。

---

## Task 5: 变量白名单 catalog（TDD）

**Files:**
- Create: `.../service/flow/FlowVariableCatalog.java`
- Create: `.../api/dto/flow/FlowVariableDTO.java`（`{String field; String label; String type;}`）
- Test: `.../service/flow/FlowVariableCatalogTest.java`

- [ ] **Step 1: 失败测试**

```java
class FlowVariableCatalogTest {
    private final FlowVariableCatalog c = new FlowVariableCatalog();
    @Test void allocAdjust_hasBizKind() {
        assertThat(c.variables("ALLOC_ADJUST")).extracting(FlowVariableDTO::getField)
            .contains("bizKind","custType","allocDim");
    }
    @Test void unknownBiz_empty() { assertThat(c.variables("NOPE")).isEmpty(); }
    @Test void fields_setForValidator() {
        assertThat(c.fields("ALLOC_ADJUST")).contains("bizKind");
    }
}
```

- [ ] **Step 2: Run → FAIL。**

- [ ] **Step 3: 实现**（代码侧维护 map；首期 ALLOC_ADJUST/TARGET_ADJUST）

```java
@Component
public class FlowVariableCatalog {
    private static final Map<String, List<FlowVariableDTO>> CATALOG = Map.of(
        "ALLOC_ADJUST", List.of(
            new FlowVariableDTO("bizKind","业务种类","string"),
            new FlowVariableDTO("custType","客户类型","string"),
            new FlowVariableDTO("allocDim","分配维度","string")),
        "TARGET_ADJUST", List.of(
            new FlowVariableDTO("bizKind","业务种类","string"),
            new FlowVariableDTO("custType","客户类型","string"))
    );
    public List<FlowVariableDTO> variables(String bizType) { return CATALOG.getOrDefault(bizType, List.of()); }
    public Set<String> fields(String bizType) {
        return variables(bizType).stream().map(FlowVariableDTO::getField).collect(Collectors.toSet());
    }
}
```
> 校验：TARGET_ADJUST 实际写入的流程变量须核对 `TargetAdjustService` 的 `startCmd` variables，按实情增减。

- [ ] **Step 4: Run → PASS。**
- [ ] **Step 5: Commit** — `feat(workflow): 流程变量白名单 catalog（TDD）`。

---

## Task 6: BPMN 生成器（核心，TDD）

**Files:**
- Create: `.../service/flow/FlowBpmnGenerator.java`
- Test: `.../service/flow/FlowBpmnGeneratorTest.java`

职责：`BpmnModel generate(FlowGraphDTO graph, String shadowProcessKey)`，用 `org.flowable.bpmn.model.*` 编程构建：
- `Process` id=shadowProcessKey。
- START→`StartEvent`，END→`EndEvent`，每个额外 REJECTED 汇聚到一个 `EndEvent id="rejectedEnd"`。
- APPROVAL→`UserTask`；挂 `taskAssignmentListener`（TaskListener，event=create，delegateExpression `${taskAssignmentListener}`）。ANY=普通 userTask；ALL=`MultiInstanceLoopCharacteristics`（sequential=false，inputDataItem 集合变量 `approverEmpIds`，elementVariable `approver`，`flowable:assignee=${approver}`，completionCondition `${rejected == true || nrOfCompletedInstances >= nrOfInstances}`），并挂 `${multiInstanceApproverResolver}`（execution start listener，Task 7）。
- 审批节点后隐式 `ExclusiveGateway`：`approved==true`→后继边，否则→rejectedEnd。
- GATEWAY 节点→`ExclusiveGateway`，出边 `SequenceFlow` 带 `conditionExpression`（用 Task 3 builder），`is_default`→gateway.defaultFlow。
- 流程级 `processCompletedListener`（ExecutionListener end，`${processCompletedListener}`）。

- [ ] **Step 1: 失败测试**（断言生成的 BpmnModel 结构）

```java
class FlowBpmnGeneratorTest {
    private final FlowBpmnGenerator g = new FlowBpmnGenerator(new FlowConditionExpressionBuilder());

    @Test void any_node_generates_userTask_with_listener() {
        FlowGraphDTO graph = linear("ALLOC_ADJUST"); // start -> a1(ANY,ROLE:CORP_DEPT) -> end
        BpmnModel m = g.generate(graph, "DSN_t1");
        Process p = m.getProcessById("DSN_t1");
        UserTask ut = (UserTask) p.getFlowElement("a1");
        assertThat(ut).isNotNull();
        assertThat(ut.getTaskListeners()).anyMatch(l -> "${taskAssignmentListener}".equals(l.getImplementation()));
        assertThat(ut.getLoopCharacteristics()).isNull(); // 或签非多实例
    }

    @Test void all_node_generates_multiInstance() {
        FlowGraphDTO graph = linearAll("ALLOC_ADJUST"); // a1 approveMode=ALL
        BpmnModel m = g.generate(graph, "DSN_t2");
        UserTask ut = (UserTask) m.getProcessById("DSN_t2").getFlowElement("a1");
        assertThat(ut.getLoopCharacteristics()).isNotNull();
        assertThat(ut.getLoopCharacteristics().getInputDataItem()).contains("approverEmpIds");
    }

    @Test void approval_node_has_reject_gateway_to_rejectedEnd() {
        BpmnModel m = g.generate(linear("ALLOC_ADJUST"), "DSN_t3");
        Process p = m.getProcessById("DSN_t3");
        assertThat(p.getFlowElements()).anyMatch(fe -> fe instanceof EndEvent && "rejectedEnd".equals(fe.getId()));
        assertThat(p.getFlowElements()).anyMatch(fe -> fe instanceof ExclusiveGateway);
    }

    @Test void gateway_edge_condition_rendered() {
        FlowGraphDTO graph = branching("ALLOC_ADJUST"); // gw -> a(cond bizKind EQ CORP) / -> b(default)
        BpmnModel m = g.generate(graph, "DSN_t4");
        SequenceFlow cond = findFlowTo(m, "DSN_t4", "a");
        assertThat(cond.getConditionExpression()).isEqualTo("${bizKind == 'CORP'}");
    }

    @Test void model_is_deployable() {
        BpmnModel m = g.generate(linear("ALLOC_ADJUST"), "DSN_t5");
        assertThat(new BpmnXMLConverter().convertToXML(m)).isNotEmpty(); // 可序列化
    }
}
```

- [ ] **Step 2: Run → FAIL。**

- [ ] **Step 3: 实现 FlowBpmnGenerator**

按上面职责构建 `BpmnModel`/`Process`，逐 node 转 FlowElement，逐 approval 追加 reject 网关 + rejectedEnd（rejectedEnd 单例），逐 edge 转 SequenceFlow（条件用 builder）。MI 块设置 `flowable:collection=approverEmpIds`、`elementVariable=approver`、assignee `${approver}`、completionCondition。给 userTask addTaskListener(create,`${taskAssignmentListener}`)。给 process addExecutionListener(end,`${processCompletedListener}`)。
> 实现参考：现有静态 BPMN（`perf_alloc_adjust_corp_v1.bpmn20.xml`）的 userTask/listener/gateway 写法，照其属性命名生成。

- [ ] **Step 4: Run → PASS（5 用例）。**
- [ ] **Step 5: Commit** — `feat(workflow): BPMN 生成器 BpmnModel API（TDD）`。

---

## Task 7: 会签 MI 审批人解析监听器（TDD）

**Files:**
- Create: `.../listener/MultiInstanceApproverResolver.java`
- Test: `.../listener/MultiInstanceApproverResolverTest.java`

职责：实现 `ExecutionListener`（bean 名 `multiInstanceApproverResolver`），在 MI 审批节点进入前，按当前节点的审批人规则展开成 empId 集合，set 到流程变量 `approverEmpIds`（供 MI collection 用）。复用现有把 ROLE/ORG/USER 展开为 empId 的逻辑（参照 `TaskAssignmentListener.expandCandidatesToEmpIds` + `UserApi`），按 shadow key + nodeKey 读 `WF_NODE_CANDIDATE_CONF`（Task 9 发布时已写入）。

- [ ] **Step 1: 失败测试（Mockito mock UserApi + NodeCandidateConfMapper + DelegateExecution）**

```java
@ExtendWith(MockitoExtension.class)
class MultiInstanceApproverResolverTest {
    @Mock UserApi userApi; @Mock NodeCandidateConfMapper confMapper; @Mock DelegateExecution exec;
    @InjectMocks MultiInstanceApproverResolver resolver;

    @Test void resolves_role_to_empIds_and_sets_collection() {
        when(exec.getProcessDefinitionId()).thenReturn("DSN_x:1:9");
        when(exec.getCurrentActivityId()).thenReturn("a1");
        // confMapper 返回 ROLE:CORP_DEPT；userApi.getEmpIdsByRoleCode -> [E1,E2]
        resolver.notify(exec);
        verify(exec).setVariable(eq("approverEmpIds"), argThat(v -> ((Collection<?>) v).containsAll(List.of("E1","E2"))));
    }
    @Test void empty_resolves_to_empty_collection_not_npe() { /* 无规则 → set 空集合，不抛 */ }
}
```

- [ ] **Step 2: Run → FAIL。**
- [ ] **Step 3: 实现**（提取 processDefinitionKey 走 RepositoryService 或按 id 前缀；读候选配置；展开 empId；`exec.setVariable("approverEmpIds", set)`）。
- [ ] **Step 4: Run → PASS。**
- [ ] **Step 5: Commit** — `feat(workflow): 会签 MI 审批人解析监听器（TDD）`。

---

## Task 8: FlowDefService（草稿 CRUD，TDD）

**Files:**
- Create: `.../service/flow/FlowDefService.java`
- Create: `.../api/dto/flow/FlowDefDTO.java`（列表/详情用：`id, flowKey, bizType, name, status, version, deployedProcDefKey, updatedTime`）
- Test: `.../service/flow/FlowDefServiceTest.java`

方法：
- `List<FlowDefDTO> listAll()`
- `FlowGraphDTO getGraph(String flowDefId)`（组装 nodes+approvers+edges，edge 用 nodeKey）
- `String create(FlowGraphDTO graph, String operator)`（status=DRAFT, version=0；分配 flowKey/id）
- `void saveGraph(String flowDefId, FlowGraphDTO graph, String operator)`（**整图替换**：del nodes/approvers/edges by flowDefId → 重插；`is_readonly_import=1` 的禁止保存，抛 BizException）
- `void deleteDraft(String flowDefId)`（仅 DRAFT/非 readonly）

- [ ] **Step 1: 失败测试（Mockito mock 4 Mapper）**

```java
@ExtendWith(MockitoExtension.class)
class FlowDefServiceTest {
    @Mock WfFlowDefMapper defMapper; @Mock WfFlowNodeMapper nodeMapper;
    @Mock WfFlowNodeApproverMapper approverMapper; @Mock WfFlowEdgeMapper edgeMapper;
    @InjectMocks FlowDefService service;

    @Test void saveGraph_replacesNodesEdgesApprovers() {
        when(defMapper.selectById("FD1")).thenReturn(draftDef("FD1"));
        when(nodeMapper.selectByFlowDefId("FD1")).thenReturn(List.of()); // for old-id collection
        service.saveGraph("FD1", twoNodeGraph(), "admin");
        verify(edgeMapper).deleteByFlowDefId("FD1");
        verify(nodeMapper).deleteByFlowDefId("FD1");
        verify(nodeMapper, atLeastOnce()).insert(any(WfFlowNode.class));
        verify(edgeMapper, atLeastOnce()).insert(any(WfFlowEdge.class));
    }
    @Test void saveGraph_readonlyImport_throws() {
        when(defMapper.selectById("FD2")).thenReturn(readonlyDef("FD2"));
        assertThatThrownBy(() -> service.saveGraph("FD2", twoNodeGraph(), "admin"))
            .isInstanceOf(BizException.class);
    }
    @Test void getGraph_assemblesByNodeKey() { /* mock 返回 node/edge/approver，断言 edge.fromNodeKey 用 nodeKey 装配 */ }
}
```

- [ ] **Step 2: Run → FAIL。**
- [ ] **Step 3: 实现**（`@Transactional` 写方法；id 用 `UUID.replace("-","")`；edge 存储用 from/to 的 DB node id，需先插 node 建立 nodeKey→id 映射再插 edge）。
- [ ] **Step 4: Run → PASS。**
- [ ] **Step 5: Commit** — `feat(workflow): FlowDefService 草稿 CRUD（TDD）`。

---

## Task 9: FlowPublishService（校验→生成→部署→写候选配置→版本+1）

**Files:**
- Create: `.../service/flow/FlowPublishService.java`
- Test: `.../service/flow/FlowPublishServiceTest.java`（mock）
- Test: `.../service/flow/FlowPublishServiceIT.java`（真部署到内存 Flowable）

`publish(String flowDefId, String operator)`：
1. `getGraph` + `FlowValidator.validate`，失败抛 BizException（含 errors）。
2. shadowKey = `"DSN_" + flowDef.flowKey`。
3. `FlowBpmnGenerator.generate(graph, shadowKey)` → `BpmnXMLConverter.convertToXML` → `repositoryService.createDeployment().addBytes(shadowKey+".bpmn20.xml", xml).deploy()`。
4. 取部署后的 `processDefinition.getId()` 写回 `deployed_proc_def_key/id`，`version+1`，`status=PUBLISHED`。
5. **写候选配置**：按各 APPROVAL 节点的审批人规则，`delete + insert` `WF_NODE_CANDIDATE_CONF`（process_definition_key=shadowKey, node_key, candidate_type, candidate_value JSON 数组）——复用现有解析。

- [ ] **Step 1: 失败单元测试（mock RepositoryService/Deployment 链）**

```java
@Test void publish_invalid_throws_and_noDeploy() {
    when(flowDefService.getGraph("FD1")).thenReturn(invalidGraph());
    assertThatThrownBy(() -> publishService.publish("FD1","admin")).isInstanceOf(BizException.class);
    verifyNoInteractions(repositoryService);
}
@Test void publish_valid_deploys_and_bumpsVersion() {
    when(flowDefService.getGraph("FD1")).thenReturn(validGraph());
    // mock deployment chain returns proc def id
    publishService.publish("FD1","admin");
    verify(repositoryService).createDeployment();
    verify(flowDefMapper).updateById(argThat(d -> "PUBLISHED".equals(d.getStatus()) && d.getVersion()==1));
    verify(nodeCandidateConfMapper, atLeastOnce()).insert(any());
}
```

- [ ] **Step 2: Run → FAIL。**
- [ ] **Step 3: 实现 publish。**
- [ ] **Step 4: Run 单元 → PASS。**

- [ ] **Step 5: 写部署+流转集成测试 IT**（继承 workflow 现有 `@SpringBootTest` Flowable IT 基类；先 grep 确认基类）

```java
@Test void publishedFlow_runsAnyApprove_toEnd() {
    String fid = seedSimpleAnyFlow();        // start -> a1(ANY,ROLE:CORP_DEPT) -> end
    publishService.publish(fid, "admin");
    String key = "DSN_" + flowKeyOf(fid);
    ProcessInstance pi = runtimeService.startProcessInstanceByKey(key, vars("bizKind","CORP"));
    Task t = taskService.createTaskQuery().processInstanceId(pi.getId()).singleResult();
    assertThat(t.getTaskDefinitionKey()).isEqualTo("a1");
    taskService.setVariable(t.getId(), "approved", true);
    taskService.complete(t.getId());
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi.getId()).count()).isZero();
}
@Test void publishedFlow_reject_terminates() { /* approved=false → 实例结束、走 rejectedEnd */ }
@Test void publishedFlow_branch_routesByCondition() { /* gw 按 bizKind 路由到不同分支 */ }
@Test void publishedFlow_allNode_multiInstance_anyRejectTerminates() { /* ALL 节点两候选，其一 approved=false → 整单终止 */ }
```

- [ ] **Step 6: Run IT** — `mvn -q -pl workflow-center test -Dtest=FlowPublishServiceIT`（必要时先 `mvn install -pl workflow-center -DskipTests`）。Expected: PASS。
- [ ] **Step 7: Commit** — `feat(workflow): FlowPublishService 生成+部署+候选配置（TDD+IT）`。

---

## Task 10: 管理 API（FlowDesignController）

**Files:**
- Create: `.../controller/FlowDesignController.java`
- Create: `.../api/dto/flow/FlowSaveReqDTO.java`（= FlowGraphDTO 包装）
- Test: `.../controller/FlowDesignControllerTest.java`（MockMvc + mock service）

端点（全部 `@BizAuth(bizType=BizType.SYS_CONFIG, action=BizAction.CONFIG)`，`ResponseWrapper<>`）：
- `GET /api/admin/workflow/flows` → `listAll()`
- `GET /api/admin/workflow/flows/{id}` → `getGraph(id)`
- `POST /api/admin/workflow/flows` → `create(graph)`
- `PUT /api/admin/workflow/flows/{id}` → `saveGraph(id, graph)`
- `POST /api/admin/workflow/flows/{id}/publish` → `publish(id)`
- `DELETE /api/admin/workflow/flows/{id}` → `deleteDraft(id)`
- `GET /api/admin/workflow/flows/meta/variables?bizType=` → `variableCatalog.variables(bizType)`

- [ ] **Step 1: 失败测试（@WebMvcTest 或现有 controller test 风格；先 grep 确认 workflow controller test 套路）**

```java
@Test void list_returnsFlows() throws Exception {
    when(flowDefService.listAll()).thenReturn(List.of(sampleDefDTO()));
    mockMvc.perform(get("/api/admin/workflow/flows"))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.data[0].flowKey").value("alloc_adjust_corp"));
}
@Test void publish_returnsOk() throws Exception {
    mockMvc.perform(post("/api/admin/workflow/flows/FD1/publish"))
       .andExpect(status().isOk());
    verify(flowPublishService).publish(eq("FD1"), anyString());
}
```

- [ ] **Step 2: Run → FAIL。**
- [ ] **Step 3: 实现 Controller**（operator 取 `currentUserApi.getCurrentEmpId()`）。
- [ ] **Step 4: Run → PASS。**
- [ ] **Step 5: 注册 PT_RESOURCE**：补 SQL（脚本追加到 Task 1 的 sql 或新增），为 7 个端点登记 `RESOURCE_URL/METHOD`，并绑定到管理员角色（SYS_ADMIN 等）。在 yiti 执行。
- [ ] **Step 6: Commit** — `feat(workflow): 审批流程设计器管理 API + 资源登记`。

---

## Task 11: 全量回归 + 构建部署

- [ ] **Step 1: 跑 workflow-center 全量单元+IT**

Run: `mvn -q -pl workflow-center test`
Expected: 全绿（含既有 `TodoQueryServiceTest` 等）。

- [ ] **Step 2: install + 重启验证启动**

Run: `mvn install -pl workflow-center -DskipTests`，停旧 bootstrap，后台起 `mvn -f bootstrap/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev`，grep 日志 `Started BranchPlatformApplication` + 18080 监听。
Expected: 正常启动，**现有审批流程行为不变**（抽查现有 alloc/target 发起与待办仍正常）。

- [ ] **Step 3: Commit（若有收尾改动）** — `chore(workflow): P1 引擎全量回归通过`。

---

## Self-Review 记录

- **Spec 覆盖**：①查看所有=Task10 GET /flows；②增删改环节=Task8 saveGraph 整图替换；③审批人多条件+或签会签=Task6/7/9（approvers 并集、ANY/ALL MI）；④条件分支=Task3/6（EL+gateway）。零影响=影子 key（Task9）+ 现有监听器复用不改 + readonly 导入禁改（Task8）。校验=Task4。版本=Task9。
- **占位扫描**：无 TBD；TARGET_ADJUST 变量与 readonly 导入(P3) 标注为需核对/后续。
- **类型一致**：`FlowGraphDTO`(nodeKey 关联)、`approverEmpIds`(MI 集合变量)、`approved`/`rejected`(网关变量)、shadowKey 前缀 `DSN_`、bean 名 `taskAssignmentListener`/`processCompletedListener`/`multiInstanceApproverResolver` 全计划统一。
- **缺口**：现有 workflow 测试基类名（Mapper IT / Flowable IT / controller test）需实现时先 grep 确认并对齐，已在相应 Task 注明。
