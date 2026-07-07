# portal-content-center V1 首版切片设计（工作台聚合 + 产品资料库）

**创建日期**：2026-04-11
**修订日期**：2026-04-11（r3，根据 spec-document-reviewer 第二轮反馈修订）
**模块**：`portal-content-center`
**版本**：V1 首版（仅实现 10 个接口的子集）

**权威前置文档（按优先级）**：

1. `docs/common-dev-guide.md`（共享开发规范，全局权威）
2. `docs/modules/portal-content-center/03-接口设计与报文.md`（接口契约权威）
3. `docs/modules/portal-content-center/06-并发与事务策略.md`（并发/事务策略权威）
4. `docs/modules/portal-content-center/09-依赖契约摘要.md`（跨模块依赖权威）
5. `docs/modules/portal-content-center/05-表结构DDL.md`（数据模型权威）
6. `docs/schema/ddl-portal.sql`（DDL 源，已与 05 对齐）
7. `docs/schema/seed-v1.sql`（PT_RESOURCE 和角色绑定基线）

**本 spec 的定位**：**切片契约**，明确本次开发的范围、偏离原 03/06/09 文档的决策及理由。当 spec 与前置文档冲突时，**前置文档为权威**，本 spec 必须修订以对齐；当前置文档未覆盖或与现状有冲突时，由本 spec 显式声明决策。

---

## 0. 修订历史

### 0.1 r3 修订（第二轮 reviewer 反馈 → 全部事实校验后落地）

reviewer r2 轮指出 2 个新 P0（`ORG_SELF` 枚举错、`WorkflowQueryApi` 不存在）+ 4 个新 P1（FileApi 方法名错、MetricCardDTO 两文档冲突、DDL 合并技术债、Spring MVC 路由顺序）+ 首轮 P0 的第 6 项（PT_RESOURCE）只部分修复。r3 经源码核对全部确认为事实问题，逐项修正：

| 类别 | r2 问题 | r3 修订 |
|---|---|---|
| **DataScopeType 枚举** | `ORG_SELF`（不存在） | 改为 `ORG`（`common-dev-guide.md §5.1` 第 865 行权威定义：`t.{ownerOrgCol} = #{orgCode}`） |
| **WorkflowQueryApi 不存在** | r2 假设存在 `countPendingTasks / listRecentPendingTasks` | 源码核实 `workflow-center/src/main/java/com/bank/branch/platform/workflow/api/WorkflowApi.java` **仅有 `startProcess / getProcessByBusinessKey / getProcessByBizTypeAndBizId` 3 个方法**，没有 Query 方法。**V1 决策**：不修改 workflow-center（已完成模块），A.1 的 `todoCount / recentTodos` 走"**未实现降级**"策略，通过 `aggregateErrors` 返回 `{"todos": "WorkflowQueryApi not available in V1"}`（与 MetricAdapter 模式一致） |
| **FileApi 方法签名错** | 调用 `getFileInfo / attachBizRelation / detachBizRelation`（不存在） | 源码核实 `FileApi` 实际方法：`upload / getDownloadUrl / bindFile(bizType,bizId,fileObjectId,fileRole) / listBizFiles / deleteFile`。r3 改为：①校验 fileObjectId 存在用 `getDownloadUrl` 的异常捕获（不存在抛 `GOV-40005`）或直接 `bindFile` 失败兜底；②新旧附件切换用 `listBizFiles + bindFile`（新 bindFile 会幂等覆盖同 biz；旧关联在 V1 不主动清理，作为技术债登记） |
| **MetricCardDTO 字段冲突** | 03 §A.1 是 7 字段 `String currentValue/targetValue + completionRate`，09 §4.1 是 11 字段 `BigDecimal currentValue/targetValue + achievementRate` | r3 **以 03 §A.1 为响应契约权威**（前端集成的唯一契约）。MetricAdapter 内部可返回 09 §4.1 的 11 字段，但 Facade 层投影为 03 §A.1 的 7 字段对外。附录 B7 登记 |
| **DDL 合并技术债** | 要求合并 3 份 DDL 去掉 CREATE DATABASE/USE，人工维护 | r3 采用 Testcontainers `.withInitScripts(script1, script2, script3)` 多脚本方案（Testcontainers 1.19+ 支持），顺序加载 ddl-auth → ddl-governance → ddl-portal，不需要人工合并；若 CREATE DATABASE/USE 语句存在，则写一个 Phase 0.4.1 子任务"启动前加载脚本时通过 `.withConfigurationOverride` 或 wrapper SQL 包装"来处理 |
| **Spring MVC 路由顺序** | D.3 `/api/products/support-available` 可能被 D.2 `/api/products/{id}` 的 `{id}` 匹配 | r3 §4.1 明示 Controller 方法声明顺序：`/support-available` 先于 `/{id}`；同时建议 D.2 的路径变量加约束 `@GetMapping("/{id:[A-Za-z0-9_-]{1,64}}")`（兜底） |
| **PT_RESOURCE 废弃资源** | r2 只补 3 条新增，未处理 4 条已废弃的 dashboard 旧资源 | r3 的 `2026-04-11-portal-resources-align.sql` 额外包含"4 条旧 dashboard 资源标记为 deleted=1"（`RES_PORTAL_TODOS / RES_PORTAL_NOTIFY / RES_PORTAL_NOTIFY_READ / RES_PORTAL_CARDS`），避免管理台混乱 |

### 0.2 r2 修订（首轮 reviewer 反馈）

r1 经 spec-document-reviewer 指出 6 处 P0 + 4 处 P1 的对齐缺口。r2 按 **"以前置文档为权威，不凭空发明"** 原则全面修订：

| 类别 | r1 问题 | r2 修订 |
|---|---|---|
| A.1 WorkspaceDTO 字段 | 凭空发明 `touchTasks / kpiCards / degraded / failedSources` | 严格按 03 §A.1 的 7 字段 |
| A.1/A.2/A.3 鉴权 | 凭空添加 `@BizAuth(PORTAL_WORKSPACE/PORTAL_SHORTCUT)` | **无需 @BizAuth**（遵循 03 §A.1/A.2/A.3） |
| D.4/D.5 事务方案 | `SELECT FOR UPDATE` + 同事务双向同步 | 采用 `@TransactionalEventListener(AFTER_COMMIT)`（理由见 §4a） |
| D.6 前置检查 | 缺失 | 补 `countEmployeesReferringProduct`，新增 `PORTAL-40905` |
| 错误码映射 | r1 编号与 03 §I 冲突 | 按 03 §I 权威清单对齐 |
| Stub 设计 | 凭空发明 CustomerQueryApi.getTouchTaskSummary 等 | 改为 09 §4.1 的 MetricAdapter 模式 |
| PT_RESOURCE | 放在"如有差异"模糊条款 | Phase 0 显式产出对齐 SQL |
| D.4/D.5 缺校验 | 缺机构归属校验 | 补 `PORTAL-40302` 校验 |
| D.5 不可变字段 | 未说明 | 明示 `productCode` / `productDeptOrgCode` 不可改 |
| D.7 > 5000 TODO 码 | `PORTAL-42299`（凭空发明） | 改为复用 `PORTAL-42207` |

---

## 1. 背景与目标

### 1.1 背景

项目 `branch-platform`（银行分行业务平台）已完成 5 个模块（common / auth / governance / workflow / bootstrap）并通过 100 条接口的全量测试。下一阶段进入 5 个待开发业务模块的实现。

5 个模块的完整设计文档已在 `docs/modules/<module>/01-09` 齐备，并通过 2026-04-10 的维度补齐达到"字段级可执行"精度。

### 1.2 本切片目标

选 **portal-content-center** 作为第一个进入开发的业务模块，但**不实现全部 30+ 接口**，而是选一个**最小可验证子集**（10 个接口），用于：

1. 从零建立 portal 模块的 Maven 骨架 + 包结构，成为后续 customer/bizapp/performance 开发的参考蓝本
2. 跑通"TDD（Service 单测 + Mapper Testcontainers 集成测试 + Controller MockMvc 集成测试）+ subagent-driven-development + code review"的完整工具链
3. 验证跨模块 Api 的 **Adapter + `@Autowired(required=false)`** 降级模式（为 `MetricApi` **和** `WorkflowQueryApi` 未实现时的先行集成铺路，参照 09 §4.1 的 MetricAdapter 示例）
4. 落地 `@BizAuth + DataScope` 的真实测试闭环（通过自定义 `@WithMockEmpContext` JUnit 扩展）

### 1.3 为什么选这 10 个接口

这 10 个接口的选取覆盖了 3 个关键维度：

| 维度 | 接口 | 价值 |
|---|---|---|
| **只读 CRUD** | D.3 / D.1 / D.2 | 最简单的 TDD 起手式，验证 Mapper + DATA_SCOPE + 字典翻译 |
| **写操作 + 事件** | D.4 / D.5 / D.6 | 验证单表事务 + 前置校验 + AFTER_COMMIT 事件发布 + 消费者 |
| **导出 + 聚合** | D.7 / A.1 / A.2 / A.3 | 验证导出规范附录 H + CompletableFuture 并行聚合 + 降级策略 |

### 1.4 范围外（V1 不做）

- **B 章节 网址导航**（B.1-B.5）
- **C 章节 通讯录 REST 接口**（C.1-C.4；但内部 Repository 仍需建立，D.2 产品详情的负责人回显和 D.4/D.5 的 empId 存在性校验依赖它）
- **E 章节 文档管理**（E.1-E.5）
- **异步导出任务**（D.7 > 5000 行分支，V1 返回 `PORTAL-42207` 拒绝）
- **BPMN 文件补齐**
- **Kafka / RocketMQ / sys_event_outbox 表**（V1 事件只在进程内用 `@TransactionalEventListener(AFTER_COMMIT)` 发布）
- **`common-test` 抽出**（下一个模块开发时再考虑）
- **前端集成**

---

## 2. 整体架构

### 2.1 新建 portal-content-center Maven 模块

```
portal-content-center/
├── pom.xml                                 # 声明依赖 common/auth/governance/workflow 等
├── CLAUDE.md                               # 模块级上下文
└── src/
    ├── main/
    │   ├── java/com/bank/branch/platform/portal/
    │   │   ├── api/                        # 对外 API（ProductApi + dto）
    │   │   ├── adapter/                    # ★ 未实现模块的 Adapter 包装（替代原 stub 方案）
    │   │   │   ├── MetricAdapter.java      # 包装 @Autowired(required=false) MetricApi
    │   │   │   └── dto/
    │   │   │       └── MetricCardDTO.java  # 本地投影，与 09 §4.1 字段一致
    │   │   ├── controller/                 # 3 个 Controller
    │   │   │   ├── WorkspaceController.java # A.1
    │   │   │   ├── ShortcutController.java  # A.2/A.3
    │   │   │   └── ProductController.java   # D.1-D.7
    │   │   ├── facade/                     # ProductFacade (ProductApi 实现)
    │   │   ├── service/                    # 业务逻辑
    │   │   │   ├── WorkspaceService.java
    │   │   │   ├── ShortcutService.java
    │   │   │   ├── ProductService.java
    │   │   │   ├── AddrbookService.java    # ★ 内部用，不对外
    │   │   │   └── ProductExportService.java
    │   │   ├── listener/                   # ★ AFTER_COMMIT 事件消费者
    │   │   │   └── ProductResponsibleSyncListener.java
    │   │   ├── mapper/
    │   │   ├── entity/
    │   │   ├── enums/
    │   │   ├── convert/
    │   │   ├── config/
    │   │   │   ├── PortalMyBatisConfig.java
    │   │   │   └── PortalAsyncConfig.java  # 工作台聚合线程池
    │   │   └── typehandler/
    │   │       └── JsonStringListTypeHandler.java
    │   └── resources/
    │       └── mapper/                     # MyBatis XML
    └── test/
        ├── java/com/bank/branch/platform/portal/
        │   ├── support/
        │   │   ├── WithMockEmpContext.java
        │   │   ├── MockEmpContextExtension.java
        │   │   ├── AbstractMapperIntegrationTest.java
        │   │   └── AbstractControllerIntegrationTest.java
        │   ├── service/
        │   ├── mapper/
        │   ├── listener/                   # 事件消费者测试
        │   └── controller/
        └── resources/
            └── sql/
                ├── ddl-combined-for-portal.sql  # 由 Phase 0 产出
                └── portal-test-data.sql
```

### 2.2 父 pom.xml 与 bootstrap 的修改

- 父 pom.xml 的 `<modules>` 增加 `portal-content-center`（放在 workflow-center 之后）
- 父 pom.xml 的 `<dependencyManagement>` 增加 portal-content-center 的 artifact 声明
- bootstrap/pom.xml 增加 portal-content-center 依赖

### 2.3 portal/pom.xml 依赖清单

**运行时依赖**：
- common-web / common-trace / common-security / common-aop / common-db
- auth-permission-center（`@BizAuth`、`DataScopeContext`、`CurrentUserApi`、`OrgApi`、`BizScopeApi`）
- system-governance-center（`FileApi`、`NotifyApi`、`AuditApi`、`DictApi`）
- workflow-center（`WorkflowQueryApi`）
- spring-boot-starter-web
- mybatis-spring-boot-starter
- spring-boot-starter-data-redis
- easyexcel（Alibaba）—— D.7 同步导出

**测试依赖**：
- spring-boot-starter-test
- mybatis-spring-boot-starter-test
- testcontainers-mysql
- testcontainers-junit-jupiter
- spring-security-test

### 2.4 基础包路径

`com.bank.branch.platform.portal`

### 2.5 关键架构决策（与已有模块一致）

1. **贫血模型**：Entity 不含业务逻辑，Service + DAO + Entity 分层
2. **跨模块只通过 `*Api`**：严禁直接依赖其他模块的 mapper/entity/serviceImpl
3. **MyBatis XML 优先**：所有 SQL 写在 XML
4. **错误码前缀**：`PORTAL-{HTTP_STATUS}{SEQ}`
5. **事件发布**：**V1 使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` + `ApplicationEventPublisher`**（进程内），详见 §4a
6. **禁用测试并行**：`junit.jupiter.execution.parallel.enabled = false`（避免 `@WithMockEmpContext` 的 ThreadLocal 串数据）

---

## 3. 数据模型

### 3.1 使用的表（3 张，全部为 portal 自有）

| 表 | 主键 | 本次作用 | DDL 源 |
|---|---|---|---|
| `product_info` | `id` VARCHAR(64) | 产品资料库的主表 | `ddl-portal.sql §4` |
| `portal_shortcut` | `id` VARCHAR(32) | 快捷入口 | `ddl-portal.sql §2` |
| `addrbook_employee` | `emp_id` VARCHAR(32) | 通讯录（内部用，不开 REST） | `ddl-portal.sql §3` |

**不涉及的表**：`portal_nav`、`doc_info`

### 3.2 JSON 字段处理

`product_info.responsible_emp_ids` 和 `addrbook_employee.responsible_product_ids` 都是 `TEXT` 类型存 JSON 数组字符串。

**处理方式**：
- **存储**：Jackson 序列化为 `["E10001","E10002"]`
- **TypeHandler**：自定义 `JsonStringListTypeHandler implements TypeHandler<List<String>>`
- **查询**：**应用层反序列化 + IN 查询**，不使用 MySQL `JSON_CONTAINS`（避免方言耦合）
- **约束**：`responsibleEmpIds` 最多 10 个（应用层校验，03 §D.4 未明示上限时采用 spec 决定的 10）
- **TypeHandler 测试**：必须覆盖 `null` / `""` / `"[]"` / `"[\"E001\"]"`

### 3.3 DATA_SCOPE 过滤

`product_info` 按 `product_dept_org_code` 过滤。参考 `docs/common-dev-guide.md §5.1` 的 **7 种 `DataScopeType` 完整枚举**（第 862-867 行权威定义）：

| DataScopeType | 谓词模板 | portal 是否使用 | 备注 |
|---|---|---|---|
| `ALL` | `1=1` | ✅ | 管理员视图 |
| `ORG_SUBTREE` | `t.{ownerOrgCol} IN (SELECT ...)` | ✅ | 本机构及下级 |
| **`ORG`** | `t.{ownerOrgCol} = #{orgCode}` | ✅ | **本机构（不是 r2 的 `ORG_SELF`，该枚举不存在）** |
| `SELF_CREATED` | `t.created_by = #{empId}` | ✅ | 本人创建的数据 |
| `SELF` | `t.{selfCol} = #{empId}` | ❌ | portal 不涉及 |
| `SELF_ASSIGNED` | `t.{assigneeCol} = #{empId}` | ❌ | portal 不涉及 |
| `WORKFLOW_PARTICIPANT` | 流程参与人 | ❌ | portal 不涉及 |

**重要更正（r3）**：r2 错用的 `ORG_SELF` 不是合法枚举值。`common-dev-guide.md §5.1` 权威定义是 **`ORG`**（第 865 行：`ORG | t.{ownerOrgCol} = #{orgCode} | CurrentUserContext.mainOrgCode | 本机构数据`）。r2 的 XML 模板按 `ORG_SELF` 判定会走 `<otherwise>AND 1=0</otherwise>` 造成线上 0 数据。r3 全部更正为 `ORG`。

MyBatis XML 过滤片段（r3 修正版）：

```xml
<sql id="dataScopeFilter">
    <choose>
        <when test="dataScope.type == 'ALL'"/>
        <when test="dataScope.type == 'ORG_SUBTREE'">
            AND product_dept_org_code IN
            <foreach collection="dataScope.orgSubtree" item="o" open="(" close=")" separator=",">#{o}</foreach>
        </when>
        <when test="dataScope.type == 'ORG'">
            AND product_dept_org_code = #{dataScope.orgCode}
        </when>
        <when test="dataScope.type == 'SELF_CREATED'">
            AND created_by = #{dataScope.empId}
        </when>
        <otherwise>AND 1 = 0</otherwise>  <!-- Fail Close -->
    </choose>
</sql>
```

**Fail Close 原则**：未知 DataScope 类型时 `AND 1=0`。

### 3.4 Entity 字段映射

遵循 `docs/modules/portal-content-center/05-表结构DDL.md` 权威定义，本 spec 不重复字段清单。关键点：
- `product_info.status` 取值：`ACTIVE` / `DISABLED`
- `portal_shortcut.shortcut_type` 取值：`SYSTEM` / `CUSTOM`
- `addrbook_employee.status` 取值：`ACTIVE` / `RESIGNED`
- 所有 `deleted` 字段默认 `0`，逻辑删除更新为 `1`

---

## 4. 接口契约详情

**权威来源**：本章节的接口字段、URL、错误码严格对齐 `docs/modules/portal-content-center/03-接口设计与报文.md`。本 spec 只记录**范围决策**和**行为差异**，不重复字段清单。

### 4.1 D.3 `GET /api/products/support-available` — 支持中场支持的产品

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="READ")`
- **业务逻辑**：`WHERE support_for_support_request = 1 AND status = 'ACTIVE' AND deleted = 0 ORDER BY product_name`
- **V1 范围决策**：**不实现 03 §D.3 提到的 `portal:product:support-available:all` 缓存**，V1 每次查库
- **路由顺序约束（r3 新增）**：`ProductController` 的 `/support-available` 端点必须在 `/{id}` 端点**之前声明**，避免 Spring MVC 把 `support-available` 匹配为 `{id}`。推荐写法：
  ```java
  @GetMapping("/support-available")   // 先声明
  public CommonResult<List<...>> listSupportAvailable() { ... }

  @GetMapping("/{id:[A-Za-z0-9_-]{1,64}}")  // 后声明，并用正则约束
  public CommonResult<...> getProduct(@PathVariable String id) { ... }
  ```

### 4.2 D.1 `GET /api/products` — 产品列表

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="LIST")`
- **请求参数**：严格按 03 §D.1 的 `keyword / category / status / productDeptOrgCode / supportForSupportRequest / pageNo / pageSize`
- **响应**：`PageResult<ProductDTO>`
- **ProductDTO 字段**：严格按 03 §D.1 定义，包含 `productCategoryDesc`（字典翻译）、`productDeptOrgName`（机构名回填）、`responsibleEmps`（负责人简要列表）、`updatedByName`
- **DATA_SCOPE**：按 `product_dept_org_code`

### 4.3 D.2 `GET /api/products/{id}` — 产品详情

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="READ")`
- **响应 ProductDetailDTO**：扩展 D.1 的 ProductDTO，额外包含 `description`、`fileDownloadUrl`（调 `FileApi.getDownloadUrl`）、`canEdit`（当前用户 orgCode == productDeptOrgCode 或管理员）
- **责任链路**：负责人回显走本地 `addrbook_employee` 表 JOIN
- **脱敏**：负责人手机号 `138****5678`
- **错误码**：`PORTAL-40003` 产品不存在

### 4.4 D.4 `POST /api/products` — 新增产品

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="WRITE")`
- **请求体**：严格按 03 §D.4 的 `ProductCreateReqDTO`
- **业务流程**：
  1. **写前校验（事务外）**：
     - 当前用户的 `orgCode` == `productDeptOrgCode` 或为管理员，否则 `PORTAL-40302`
     - `productCategory` 在 `PRODUCT_CATEGORY` 字典中存在（调 `DictApi.isValidDictValue`），否则 `PORTAL-42200`（通用参数校验）
     - `productDeptOrgCode` 在 `EXT_ORG_INFO` 中存在（调 `OrgApi`）
     - `fileObjectId` 若提供，通过 `FileApi.getDownloadUrl(fileObjectId)` 探测存在性（捕获 `BizException(GOV-40005)`），不存在抛 `PORTAL-42203`（附件对象不存在）
     - `responsibleEmpIds`（若提供）中每个 empId 在 `addrbook_employee` 中存在且 `status=ACTIVE`，否则 `PORTAL-40902`（员工已离职）；列表长度 ≤ 10
  2. **事务内**：
     - INSERT `product_info`，依赖 `uk_product_code_deleted` 唯一约束兜底；捕获 `DuplicateKeyException` 转 `PORTAL-40901`
     - 若 `fileObjectId` 非空：`FileApi.bindFile(bizType="PRODUCT", bizId=newId, fileObjectId, fileRole="MAIN")`（幂等）
     - 写审计日志（`AuditApi`）
     - 若 `responsibleEmpIds` 非空：`ApplicationEventPublisher.publishEvent(new ProductResponsibleUpdatedEvent(productId, removed=[], added=responsibleEmpIds, source=PRODUCT_SIDE))`
     - 事务提交
  3. **事务提交后**（`@TransactionalEventListener(phase = AFTER_COMMIT)`）：
     - `ProductResponsibleSyncListener` 对每个 added empId 更新 `addrbook_employee.responsible_product_ids`（追加 productId）
     - 使用乐观锁 `WHERE emp_id=? AND updated_time=?` 更新，失败重试 3 次（间隔 100/500/2000ms）
     - 所有重试失败后记录 ERROR 日志（V1 无 DLQ，待 governance 的事件总线就绪后升级）
- **响应**：`ApiResult<String>`（返回 productId）
- **r3 注**：FileApi 不存在 `getFileInfo / attachBizRelation`，全部改为现有 `getDownloadUrl`（用于校验）和 `bindFile`（幂等绑定，同 bizType/bizId/fileObjectId 不会重复创建）

### 4.5 D.5 `PUT /api/products/{id}` — 编辑产品

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="WRITE")`
- **请求体**：严格按 03 §D.5 的 `ProductUpdateReqDTO`
- **不可变字段**：`productCode` 和 `productDeptOrgCode` **禁止**修改（请求体中根本没有这两个字段）
- **业务流程**：
  1. **写前校验**：
     - `SELECT * FROM product_info WHERE id=? AND deleted=0`；不存在抛 `PORTAL-40003`
     - 当前用户 orgCode == `productDeptOrgCode` 或为管理员，否则 `PORTAL-40302`
     - 若修改 `productCategory`：字典校验
     - 若修改 `fileObjectId`：通过 `FileApi.getDownloadUrl(新 fileObjectId)` 探测存在性；不存在抛 `PORTAL-42203`
     - 若修改 `responsibleEmpIds`：每个 empId 校验存在 + 在职，长度 ≤ 10
     - 若修改 `supportForSupportRequest` 为 `false` 且有员工正在负责该产品：记 WARN 日志（03 §D.5 未要求阻塞，仅提示）
  2. **事务内**：
     - `SELECT ... FOR UPDATE` 锁定 `product_info` 行（确保 responsible_emp_ids 的 diff 计算与 UPDATE 原子）
     - 计算 `responsibleEmpIds` 的 `removed` 和 `added` 列表
     - UPDATE `product_info`
     - 写审计日志
     - 若 `responsibleEmpIds` 有变更：发布 `ProductResponsibleUpdatedEvent(productId, removed, added, source=PRODUCT_SIDE)`
     - 若 `fileObjectId` 有变更：调 `FileApi.bindFile(bizType="PRODUCT", bizId=id, 新 fileObjectId, fileRole="MAIN")` 绑定新附件（bindFile 幂等，即使旧绑定仍存在也不会出错；**旧 fileObjectId 的关联在 V1 不主动解除**，作为技术债登记 —— 等 governance 补 `unbindFile(bizType,bizId,fileObjectId)` 方法后再实现，或等 FileApi 提供 `replaceBinding` 语义）
     - 清理 `portal:product:support-available:all` 缓存（若 V1 未实现缓存，则此步骤记 TODO 注释）
  3. **事务提交后**：同 D.4 的消费者逻辑，但对 removed 做删除、added 做追加
- **响应**：`ApiResult<Void>`
- **r3 技术债**：附件替换时的"旧绑定清理"在 V1 不做（FileApi 无 unbind 方法），登记到附录 B

### 4.6 D.6 `DELETE /api/products/{id}` — 删除产品（逻辑删除）

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="WRITE")`
- **业务流程**（严格按 06 §3.4 权威设计 + r3 修正 FileApi 调用）：
  1. `SELECT * FROM product_info WHERE id=?`；不存在抛 `PORTAL-40003`
  2. 权限校验：当前用户 orgCode == `productDeptOrgCode` 或为管理员，否则 `PORTAL-40302`
  3. **前置引用检查**（事务内）：
     ```java
     int refCount = addrbookMapper.countEmployeesReferringProduct(productId);
     if (refCount > 0) {
         throw new BizException("PORTAL-40905",
             "仍有 " + refCount + " 名员工将该产品列为负责产品，请先清理");
     }
     ```
  4. **软删**：`UPDATE product_info SET deleted=1, status='DISABLED', updated_by=?, updated_time=NOW() WHERE id=?`
  5. **附件处理（r3 修正）**：V1 **不主动解除** `biz_file_rel` 关联（FileApi 无 unbind 方法）。由于 product_info 已逻辑删除，后续 `listBizFiles` 不会命中（配合 product 的 deleted=1），不会造成业务问题。技术债登记到附录 B。**若** product 的附件关联需要严格清理，可通过 `FileApi.deleteFile(fileObjectId)` 彻底删除文件（但这会影响所有其他 bizId 的引用，不推荐），因此 V1 保持"软删不清理附件关联"
  6. 写高危审计日志（level=HIGH）
  7. 事务提交后发布 `ProductResponsibleUpdatedEvent(productId, removed=原responsibleEmpIds, added=[], source=PRODUCT_SIDE)`（让监听器清理 addrbook 的反向引用）

**r2 新增错误码**：`PORTAL-40905` 产品仍被员工引用，不可删除。已计划在 §4.10 错误码清单登记。

**关于 06 §3.4 第 4 步"解除文件关联"**：06 文档写的 `fileApi.detachBizRelation(fileObjectId, "PRODUCT", productId)` 方法不存在。06 文档本身与实际 FileApi 不一致。r3 决策：**软删产品时不清理附件关联**，由附录 B8 记录该技术债。

### 4.7 D.7 `GET /api/products/export` — 产品导出

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="EXPORT")`（高危）
- **行为（V1 简化版本）**：
  1. `SELECT count(*)` 取精确命中行数
  2. **N ≤ 5000**：同步流式返回 Excel（EasyExcel）
  3. **N > 5000**：**V1 直接返回 `PORTAL-42207`**，错误消息："V1 暂不支持异步导出，当前命中 N 行超过同步阈值 5000，请增加过滤条件"
  4. **取消 r1 的 `PORTAL-42299` TODO 码**（凭空发明），全部走 03 §I 已有的 `PORTAL-42207`
- **列定义**：严格按 03 §H.7.1 的 10 列清单；**Phase 7 的 TDD 红用例必须按该清单生成 fixture**
- **脱敏**：负责人手机号列 `138****5678`
- **审计**：`AuditApi.log(EXPORT, bizType=PRODUCT, filter=..., rowCount=..., result=...)`

### 4.8 A.2 `GET /api/portal/shortcuts` — 查询快捷入口

- **鉴权**：**无需 `@BizAuth`**（遵循 03 §A.2）
- **请求参数**：`shortcutType` 可选（SYSTEM/CUSTOM/ALL，默认 ALL）
- **业务逻辑**：`WHERE (shortcut_type='SYSTEM') OR (shortcut_type='CUSTOM' AND emp_id=#{currentEmpId}) ORDER BY shortcut_type DESC, sort_order ASC`

### 4.9 A.3 `PUT /api/portal/shortcuts` — 保存个性化快捷入口

- **鉴权**：**无需 `@BizAuth`**（遵循 03 §A.3，仅操作本人记录）
- **请求体**：`ShortcutSaveReqDTO { shortcuts: List<ShortcutItemDTO> }`
- **ShortcutItemDTO 字段**（严格按 03 §A.3）：`shortcutName(@NotBlank @Size(max=100))` / `shortcutUrl(@NotBlank @Size(max=500))` / `shortcutIcon(@Size(max=100))` / `targetType(@NotBlank @Pattern("INTERNAL|EXTERNAL"))` / `sortOrder(@NotNull @Min(0))`
- **业务逻辑**：全量替换当前用户的 CUSTOM 类型记录（先 DELETE 后 INSERT，事务内）
- **响应**：`ApiResult<Void>`

### 4.10 A.1 `GET /api/portal/workspace` — 工作台数据聚合

- **鉴权**：**无需 `@BizAuth`**（遵循 03 §A.1，天然按当前登录用户）
- **请求参数**：无
- **响应 WorkspaceRespDTO 字段（严格按 03 §A.1 权威定义的 7 个字段）**：

| 字段 | 类型 | 来源 | V1 实现 |
|---|---|---|---|
| `todoCount` | int | **V1 未实现降级**（WorkflowQueryApi 不存在，见下 r3 说明） | ⚠️ 降级返回 0 + aggregateErrors |
| `unreadNotificationCount` | int | `NotifyApi.countUnread(empId)` | ✅ 实现 |
| `recentTodos` | `List<TodoItemDTO>` (最多 5 条) | **V1 未实现降级**（同上） | ⚠️ 降级返回空列表 + aggregateErrors |
| `recentNotifications` | `List<NotificationItemDTO>` (最多 5 条) | `NotifyApi.queryNotifications(empId, false, page(1,5))` | ✅ 实现（需 Phase 0 确认此方法在现有 NotifyApi 签名中存在；若只有 `queryNotifications(empId, isRead, pageNo, pageSize)` 则改用后者） |
| `metricCards` | `List<MetricCardDTO>` | **MetricAdapter**（9 §4.1 模式） | ⚠️ 降级返回空列表（V1 performance 未实现） |
| `shortcuts` | `List<ShortcutDTO>` | 内部调 `ShortcutService.listMy()` | ✅ 实现 |
| `aggregateErrors` | `Map<String,String>` | 每个子调用的失败错误码映射 | ✅ 实现 |

#### 4.10.1 跨模块依赖可用性矩阵（r3 新增，响应 reviewer P0-新-2）

| 数据源 | 需要的 API | V1 现状 | V1 降级行为 |
|---|---|---|---|
| `todoCount` | `WorkflowQueryApi.countPendingTasks(empId)` | ❌ **整个 WorkflowQueryApi 不存在**（workflow-center 只暴露 `WorkflowApi.startProcess / getProcessByBusinessKey / getProcessByBizTypeAndBizId` 3 个方法；待办查询只在内部 `TodoQueryService` + REST 端点 `GET /api/workflow/tasks/todo`） | 返回 `todoCount = 0`，`aggregateErrors` 放入 `{"todos": "WorkflowQueryApi not available in V1"}` |
| `recentTodos` | `WorkflowQueryApi.listRecentPendingTasks(empId, 5)` | ❌ 同上 | 返回空列表，`aggregateErrors` 同上 |
| `unreadNotificationCount` | `NotifyApi.countUnread(empId)` | ✅ governance CLAUDE.md 确认已有 | 正常调用；失败降级 |
| `recentNotifications` | `NotifyApi.queryNotifications(empId, isRead=false, page)` | ✅ governance CLAUDE.md 确认已有（注意方法签名需 Phase 0 验证） | 正常调用；失败降级 |
| `metricCards` | `MetricApi.getUserMetricCards(empId)` | ❌ performance 模块未创建 | 通过 `@Autowired(required=false)` 注入 null，MetricAdapter 返回空列表 |
| `shortcuts` | 本地 `ShortcutService.listMyShortcuts(empId)` | ✅ 本模块内部实现 | 正常调用 |

**V1 关键决策**：
- **不在 workflow-center 新建 `WorkflowQueryApi`**（避免修改已完成模块，符合"范围外"原则）
- portal 内部创建 `WorkflowQueryApi` **占位接口**（在 `portal.adapter` 包下），类头 javadoc 标注 "V1 临时占位，待 workflow-center 暴露正式接口后迁移 import"
- **WorkspaceService 通过 `@Autowired(required=false) WorkflowQueryApi`** 注入；bean 为 null 时降级
- V1 todo 卡片在前端显示为 "0 / 暂无待办"，用户需要通过 `GET /api/workflow/tasks/todo` 直接访问（本切片范围外的前端实现细节）

**V2 升级路径**：workflow-center 新建 `WorkflowQueryFacade implements WorkflowQueryApi`，包装内部 `TodoQueryService`；portal 的占位接口删除，import 切换到 `workflow.api.WorkflowQueryApi`

#### 4.10.2 并发编排

- **并发编排**：`CompletableFuture.allOf(...)` + `orTimeout(2, SECONDS)` + 独立线程池 `portalAggregateExecutor`
- **每路超时**：
  - shortcuts: 100 ms（本地查询）
  - notifications / todos / metricCards：500 ms 各自
- **降级策略**：
  - 任一路失败/超时 → 该字段返回默认值（0 或空列表）
  - **失败信息写入 `aggregateErrors` Map**：key 是区域名（`"todos"` / `"notifications"` / `"metrics"` / `"shortcuts"`），value 是错误码或异常消息
  - `aggregateErrors` 为空表示全部成功；任一失败则非空，整体仍返回 200 OK
  - **对于 V1 已知的未实现降级（todos / metrics），启动时即填充 `aggregateErrors`**，不需要每次调用再失败

#### 4.10.3 MetricCardDTO 字段取舍（r3 新增，响应 reviewer P1-新-2）

03 §A.1 定义的 `MetricCardDTO` 是 **7 字段**（`String currentValue/targetValue + BigDecimal completionRate + unit/trend/metricCode/metricName`）；09 §4.1 定义的 `MetricCardDTO` 是 **11 字段**（`BigDecimal currentValue/targetValue/achievementRate + period/dataTime/changeRate/colorHint + ...`）。两者字段名和类型都有冲突。

**V1 决策**：
- **对外响应契约以 03 §A.1 为权威**（前端集成的唯一契约）
- `MetricAdapter.fetch(empId)` **内部**按 09 §4.1 的 11 字段（如果 performance 模块实现）获取数据
- `WorkspaceFacade` 层做 **fieldset 投影**：把 11 字段 DTO 映射为 03 §A.1 的 7 字段 DTO 返回给前端
- **投影规则**：
  - `metricCode / metricName / unit / trend` 直接映射
  - `currentValue: BigDecimal → String`（按 unit 格式化，如 "1250.50 万元"）
  - `targetValue: BigDecimal → String`（同上，空值返回 ""）
  - `completionRate: BigDecimal`（09 的 `achievementRate` 改名为 03 的 `completionRate`）
- V1 由于 MetricAdapter 降级返回空列表，投影代码可以最简实现（空列表原样透传），但 `MetricCardProjection` 类必须存在，确保 performance 模块就绪后补 1 行就能工作

### 4.11 错误码清单（V1 使用的子集）

以 `docs/modules/portal-content-center/03-接口设计与报文.md §I` 为权威来源。本 spec 新增 1 个。

| code | HTTP | 消息（权威来源：03 §I） | 本切片触发 |
|---|---|---|---|
| `PORTAL-40003` | 400 | 产品不存在 | D.2 / D.5 / D.6 |
| `PORTAL-40302` | 403 | 无权维护非本机构产品 | D.4 / D.5 / D.6 |
| `PORTAL-40901` | 409 | 产品代码已存在 | D.4 |
| `PORTAL-40902` | 409 | 员工已离职 | D.4 / D.5（responsibleEmpIds 校验） |
| `PORTAL-40905` | 409 | **（r2 新增）** 产品仍被员工引用，不可删除 | D.6 |
| `PORTAL-42200` | 422 | 参数校验失败（通用） | 所有写接口 |
| `PORTAL-42203` | 422 | 附件对象不存在 | D.4 / D.5 |
| `PORTAL-42207` | 422 | 导出行数超过上限（V1 同步阈值 5000） | D.7 |
| `PORTAL-50002` | 500 | 导出文件生成失败 | D.7 |

**新增错误码 `PORTAL-40905` 的登记**：Phase 0/1 任务包含"在 `auth-permission-center` 的错误码枚举或 common-web 错误码配置中登记 `PORTAL-40905`"；若项目没有集中错误码枚举，则在 portal 模块的 `PortalErrorCodes` 常量类中声明。

### 4.12 事件发布（V1 本模块）

| 事件类 | 发布时机 | 消费者 | 备注 |
|---|---|---|---|
| `ProductResponsibleUpdatedEvent` | D.4 / D.5 / D.6 事务提交后 | `ProductResponsibleSyncListener`（本模块内） | V1 同进程，AFTER_COMMIT |

**事件字段**：`productId / removed(List<String>) / added(List<String>) / source(enum: PRODUCT_SIDE/ADDRBOOK_SIDE)`

**消费者行为**：
- 对每个 removed empId：从 `addrbook_employee.responsible_product_ids` 中移除 productId
- 对每个 added empId：追加 productId
- **乐观锁**：`WHERE emp_id=? AND updated_time=?`
- **重试策略**：失败重试 3 次（指数退避 100/500/2000 ms），全部失败记 ERROR 日志
- **防重入**：事件的 `source=PRODUCT_SIDE` 标记，消费者仅更新 addrbook，不发反向事件

---

## 4a. 关键偏离：V1 使用 AFTER_COMMIT 事件而非 outbox

### 4a.1 原设计（06 §2.4 / §3.3）

06 文档要求 **sys_event_outbox** 表驱动的事务消息模式：
1. 本地事务内 UPDATE 业务表 + INSERT outbox 记录
2. 异步 Worker 扫描 outbox 发布事件
3. 外部事件总线（Kafka / RocketMQ）分发
4. 事件消费者在独立事务内执行

### 4a.2 V1 偏离的理由

经核实：
- `common-dev-guide.md §7` 定义的是 **`@TransactionalEventListener(AFTER_COMMIT)`** 方案（进程内）
- **`sys_event_outbox` 表在 common 基础设施中尚未实现**（grep `sys_event_outbox` 在 common 模块零命中）
- **事件总线（Kafka / RocketMQ）在 V1 不引入**（本切片范围外）

### 4a.3 V1 决策

V1 采用 common-dev-guide §7 的 **`@TransactionalEventListener(AFTER_COMMIT)` + `ApplicationEventPublisher`** 进程内事件方案：

**优点**：
- 不依赖未就绪的 common 基础设施
- 与 governance 模块的事件实现模式一致（已在 governance-center 中运行）
- 事务边界清晰：主事务成功提交后才触发消费者
- 单进程内可靠性已足够 V1 场景

**局限（风险与补偿）**：
- 消费者失败时主事务无法回滚（因为已提交）
- 消费者自身重试 3 次后仍失败只能记 ERROR 日志
- **无持久化**：如果消费者执行期间进程崩溃，事件丢失

**V2 升级路径**：
- 当 common 的 `sys_event_outbox` 和 `OutboxWriter` 就绪后，切换到 outbox 模式
- 切换影响：Service 层把 `eventPublisher.publishEvent` 替换为 `outboxWriter.write`
- 消费者逻辑不变（依然是 Spring Bean 监听器）
- 测试代码需更新事务语义

### 4a.4 与 06 文档的冲突声明

**本 spec 的 §4.4 / §4.5 / §4.6 与 `06-并发与事务策略.md §2.4 / §3.3` 有冲突**。冲突部分由本 spec 的 V1 决策覆盖，理由如上。待 common 基础设施就绪后，**06 文档的 outbox 方案重新生效**，本 spec 的 §4a 应标记为 V2 升级的迁移点。

---

## 5. 跨模块依赖策略

### 5.1 已存在模块的 API（r3 已源码核对）

| API | 模块 | V1 可用方法（源码核对） | 用途 |
|---|---|---|---|
| `CurrentUserApi` | auth-permission-center | ✅ 获取当前用户 empId / orgCode | 全部接口 |
| `DataScopeContext` | auth-permission-center | ✅ 获取当前 BizType 的 DataScope | 产品列表 DATA_SCOPE |
| `OrgApi` | auth-permission-center | ✅ 校验 orgCode 存在 | D.4/D.5 写前校验 |
| `BizScopeApi` | auth-permission-center | ✅ `checkWritePermission(empId, bizType, ownerOrgId, createdBy)` | D.4/D.5/D.6 写权限校验 |
| `DictApi` | system-governance-center | ✅ `getDictLabel / isValidDictValue / getDictItems` | 产品类别翻译 |
| `FileApi` | system-governance-center | ✅ **实际方法清单**：`upload / getDownloadUrl / bindFile(bizType,bizId,fileObjectId,fileRole) / listBizFiles / deleteFile` — **没有 `getFileInfo / attachBizRelation / detachBizRelation`** | D.2 下载链接、D.4/D.5 绑定附件 |
| `NotifyApi` | system-governance-center | ✅ `countUnread(empId) / queryNotifications` —— 签名细节需 Phase 0 通过 `NotifyApi.java` 源码最终确认 | A.1 未读数、recentNotifications |
| `AuditApi` | system-governance-center | ✅ `log(cmd) / queryLogs(...)` | 所有写接口审计 |
| ~~`WorkflowQueryApi`~~ | ~~workflow-center~~ | ❌ **整个接口不存在**（只有 `WorkflowApi.startProcess / getProcessByBusinessKey / getProcessByBizTypeAndBizId`） | V1 走降级（见 §4.10.1） |

**FileApi 方法签名偏差处理**：
- **06 §3.4 中写的 `detachBizRelation` 和 03 §D.4 注释的 `getFileInfo` 都是文档层的期望，实际 `FileApi.java` 没实现**
- V1 决策：用 `getDownloadUrl` 的异常（`GOV-40005` 文件不存在）作为"探测文件存在"的替代；`bindFile` 是幂等的，不需要 "detach/attach" 序列
- 附件切换（D.5）和附件解绑（D.6）在 V1 **不主动清理旧 biz_file_rel**，作为附录 B8 技术债登记

### 5.2 未实现模块的 Adapter 模式（r3 扩展）

**Adapter 模式用于两类缺失依赖**：
- `MetricApi`（performance-engine-center 模块未创建）
- `WorkflowQueryApi`（workflow-center 模块未暴露该接口）

#### 5.2.1 MetricAdapter（09 §4.1 明示模式）

```java
@Service
public class MetricAdapter {
    @Autowired(required = false)
    private MetricApi metricApi;

    public List<PortalMetricCard> fetch(String empId) {  // 返回 portal 内部 DTO，投影后再对外
        if (metricApi == null) {
            return Collections.emptyList();
        }
        try {
            List<MetricCardDTO> raw = metricApi.getUserMetricCards(empId);
            return raw.stream().map(MetricCardProjection::toPortal).collect(Collectors.toList());
        } catch (Exception ex) {
            log.warn("metricApi.getUserMetricCards failed empId={}", empId, ex);
            return Collections.emptyList();
        }
    }
}
```

**MetricApi 占位接口**：
- 放在 `portal.adapter.MetricApi`，类头 javadoc 标注 `V1 临时占位，待 performance-engine-center 创建后迁移 import`
- 3 个方法严格对齐 09 §4.1：`getUserMetricCards / getMetricCard / getMetricTrend`
- 不提供 `@Service` 实现（bean 为 null，Adapter 降级）

**DTO 分层**：
- `portal.adapter.MetricCardDTO`（11 字段，对齐 09 §4.1 — 为未来 import 兼容性保留）
- `portal.api.dto.PortalMetricCard`（7 字段，对齐 03 §A.1 — 对外响应契约）
- `MetricCardProjection.toPortal(MetricCardDTO): PortalMetricCard`（字段映射 + 格式化）

#### 5.2.2 WorkflowQueryAdapter（r3 新增）

workflow-center 存在 `WorkflowApi`（已实现），但不包含 query 类方法。A.1 需要的 `countPendingTasks / listRecentPendingTasks` 只存在于 workflow-center **内部** `TodoQueryService`，不暴露为 Api。

**V1 决策**：创建 portal 内部的占位接口 `WorkflowQueryApi`（在 `portal.adapter` 包下），通过 `@Autowired(required=false)` 注入，bean 为 null 时走降级：

```java
// portal.adapter.WorkflowQueryApi — V1 临时占位
public interface WorkflowQueryApi {
    int countPendingTasks(String empId);
    List<PortalTodoItem> listRecentPendingTasks(String empId, int limit);
}

// portal.adapter.WorkflowQueryAdapter
@Service
public class WorkflowQueryAdapter {
    @Autowired(required = false)
    private WorkflowQueryApi workflowQueryApi;

    public int countPendingTasks(String empId) {
        if (workflowQueryApi == null) return 0;
        try { return workflowQueryApi.countPendingTasks(empId); }
        catch (Exception ex) { log.warn(...); return 0; }
    }

    public List<PortalTodoItem> listRecentPendingTasks(String empId, int limit) {
        if (workflowQueryApi == null) return Collections.emptyList();
        try { return workflowQueryApi.listRecentPendingTasks(empId, limit); }
        catch (Exception ex) { log.warn(...); return Collections.emptyList(); }
    }
}
```

`WorkspaceService` 通过 `WorkflowQueryAdapter`（而不是直接注入 `WorkflowQueryApi`）调用，实现降级。

**V2 升级**：workflow-center 创建 `WorkflowQueryFacade implements WorkflowQueryApi`，portal 的占位接口删除 → import `com.bank.branch.platform.workflow.api.WorkflowQueryApi`。

### 5.3 不需要 Stub 的确认

经核实 03 §A.1 的 WorkspaceRespDTO 字段，**A.1 工作台聚合不需要 `CustomerQueryApi`**。r1 凭空发明的 `touchTasks` 和 `getTouchTaskSummary` 在 03 文档中不存在，完全移除。

---

## 6. 测试策略

### 6.1 三层测试分工

| 层级 | 工具 | 数量预估 | 单次耗时 | 作用 |
|---|---|---|---|---|
| **Service 单测** | Mockito + JUnit 5 | 60-80 | 10-50 ms | 业务逻辑 / 状态机 / 字段校验 / 错误码 / 事件发布时机 |
| **Mapper 集成测试** | Testcontainers MySQL 8.0 | 20-30 | 50-200 ms | SQL 正确性 / JSON TypeHandler 往返 / DATA_SCOPE 过滤 |
| **Controller 集成测试** | MockMvc + `@WithMockEmpContext` | 15-25 | 100-500 ms | HTTP 报文契约 / @BizAuth 注解生效 / 异常映射 |
| **Listener 集成测试** | Spring + AFTER_COMMIT 验证 | 5-8 | 200-500 ms | `ProductResponsibleSyncListener` 在事务提交后被触发 |

**合计**：100-141 个测试，首次 ~45 秒（含容器启动），后续 ~30 秒。

### 6.2 Testcontainers 基类设计（r3 修订 —— 多脚本方案）

```java
@Testcontainers
@SpringBootTest
@Sql(scripts = "/sql/portal-test-data.sql", executionPhase = BEFORE_TEST_METHOD)
public abstract class AbstractMapperIntegrationTest {

    @Container
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("onepl")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    static {
        MYSQL.start();
        // 容器启动后按顺序执行 3 份 DDL（通过 spring boot 的 Flyway / 手动 JDBC 执行）
        // 不使用 .withInitScript 单脚本方案，避免人工合并 DDL 的技术债
    }

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", MYSQL::getJdbcUrl);
        reg.add("spring.datasource.username", MYSQL::getUsername);
        reg.add("spring.datasource.password", MYSQL::getPassword);
        // 通过 Spring Boot 的 schema 初始化机制按序加载 3 份 DDL
        reg.add("spring.sql.init.mode", () -> "always");
        reg.add("spring.sql.init.schema-locations", () -> String.join(",",
            "classpath:sql/test-ddl-auth-clean.sql",
            "classpath:sql/test-ddl-governance-clean.sql",
            "classpath:sql/test-ddl-portal-clean.sql"
        ));
    }
}
```

**r3 方案变更**（回应 reviewer P1-新-3）：
- **不再产出"合并 DDL"**（避免 auth/governance 改表时的人工维护）
- 使用 **Spring Boot `spring.sql.init.schema-locations` 多脚本方案**，按序加载 3 份
- Phase 0.4 任务改为"产出 3 份 `test-ddl-*-clean.sql`"：每份都是对应 `docs/schema/ddl-*.sql` 的拷贝 + 去掉 `CREATE DATABASE` / `USE` 语句
- 未来 auth/governance 改表时，只需重新执行 Phase 0.4 的脚本（`sed '/CREATE DATABASE\|^USE /d' docs/schema/ddl-xxx.sql > src/test/resources/sql/test-ddl-xxx-clean.sql`）
- `portal-test-data.sql` 是每个测试方法的 fixture 重置脚本
- `withReuse(true)` 让容器跨测试类复用

**替代方案（若 Spring Boot 多脚本方案在 Testcontainers 场景下启动冲突）**：
- 回退到 `.withInitScript("sql/combined-ddl.sql")` 单脚本
- 但 Phase 0.4 必须配一个 `scripts/build-combined-ddl.sh`（或 Java 代码）每次 `mvn test` 前自动从 3 份源 DDL 构建 combined，**禁止人工维护**

### 6.3 `@WithMockEmpContext` 自定义扩展

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MockEmpContextExtension.class)
public @interface WithMockEmpContext {
    String empId() default "E10001";
    String orgCode() default "ORG_SZ_001";
    String[] roleCodes() default {"R_RM"};
    String dataScope() default "ORG_SUBTREE";  // r2: 默认改为 ORG_SUBTREE 而非 ALL
    String[] orgSubtree() default {"ORG_SZ_001"};
}
```

**Extension 实现**：
- `BeforeEachCallback`：读取注解参数 → 设置 `SecurityContextHolder` + `DataScopeContextHolder`（ThreadLocal）
- `AfterEachCallback`：清理 ThreadLocal / SecurityContextHolder

**默认值 r2 修订理由**：reviewer 建议默认 `ALL` 会让测试漏掉 DATA_SCOPE 真实过滤分支，改为 `ORG_SUBTREE` 更能代表主要业务场景。

---

## 7. 开发计划（Phase 粒度）

按 `D.3 → D.1 → D.2 → D.4 → D.5 → D.6 → D.7 → A.2 → A.3 → A.1` 顺序分 10 个业务 Phase，加上 Phase 0/1/10 的基础设施共约 14-16 个 commit。详细任务列表由下一步 `writing-plans` 技能生成。

### 7.1 Phase 0 — 基础设施（r3 更新的任务清单）

| Task | 产出 |
|---|---|
| 0.1 创建 portal-content-center Maven 模块（pom.xml + 目录骨架） | 验证 `mvn compile` |
| 0.2 更新父 pom + bootstrap pom | |
| 0.3 创建 `portal.config.PortalMyBatisConfig` + `PortalAsyncConfig`（portalAggregateExecutor 线程池） | |
| 0.4 **产出 3 份 `test-ddl-*-clean.sql`（r3 方案）**：对 `docs/schema/ddl-auth.sql / ddl-governance.sql / ddl-portal.sql` 分别执行 `sed '/CREATE DATABASE\\|^USE /d'`，放到 `src/test/resources/sql/`。**禁止人工编辑**，每次源 DDL 改动时重新生成 | 3 份 clean DDL + 1 份生成脚本 `scripts/refresh-test-ddl.sh` |
| 0.5 Testcontainers 基类 `AbstractMapperIntegrationTest`（smoke test：验证 MySQL 启动 + 3 份 DDL 按序执行成功） | |
| 0.6 `@WithMockEmpContext` 扩展 + `MockEmpContextExtension`（最小 Controller + 测试验证 context 注入） | |
| 0.7 `JsonStringListTypeHandler` + 单测（覆盖 null / "" / "[]" / 正常 4 种边界） | |
| 0.8 `PortalErrorCodes` 常量类（V1 使用的 9 个错误码 + `PORTAL-40905` 新增） | |
| 0.9 **PT_RESOURCE 对齐 SQL**（r3 扩展）：产出 `docs/superpowers/sql/2026-04-11-portal-resources-align.sql`，包含两部分：| |
|    &nbsp;&nbsp;&nbsp;&nbsp; (a) **新增 3 条资源**：`RES_PORTAL_WORKSPACE`、`RES_PRODUCT_SUPPORT_AVAILABLE`、`RES_SHORTCUT_REPLACE_PUT` | |
|    &nbsp;&nbsp;&nbsp;&nbsp; (b) **标记 4 条废弃的 dashboard 旧资源为 deleted=1**：`RES_PORTAL_TODOS / RES_PORTAL_NOTIFY / RES_PORTAL_NOTIFY_READ / RES_PORTAL_CARDS`（URL 已被统一的 `/api/portal/workspace` 取代，reviewer P0-6 指出） | |
| 0.10 **确认 NotifyApi.queryNotifications 方法签名**：通过 Phase 0 读取 `NotifyApi.java` 确认 `queryNotifications(empId, isRead, page)` 的真实签名（参数顺序、分页参数形式），若签名与 spec 描述不一致，修订本 spec 的 §4.10.1 表格 | 修订建议 |

### 7.2 Phase 1 — Adapter 与占位接口（r3 扩展）

| Task | 产出 |
|---|---|
| 1.1 `portal.adapter.MetricApi` 接口定义（严格对齐 09 §4.1 的 3 个方法签名） | 类头 javadoc 标注 V1 临时占位 |
| 1.2 `portal.adapter.dto.MetricCardDTO`（对齐 09 §4.1 的 11 字段） | |
| 1.3 `portal.adapter.MetricAdapter`（`@Autowired(required=false)` + 降级逻辑） | MetricAdapter 单测验证 null bean 时返回空列表 |
| 1.4 **（r3 新增）** `portal.adapter.WorkflowQueryApi` 接口定义（`countPendingTasks / listRecentPendingTasks` 两个方法） | 类头 javadoc 标注 V1 临时占位，待 workflow-center 暴露后迁移 |
| 1.5 **（r3 新增）** `portal.adapter.dto.PortalTodoItem`（对齐 03 §A.1 的 TodoItemDTO 9 字段） | |
| 1.6 **（r3 新增）** `portal.adapter.WorkflowQueryAdapter`（`@Autowired(required=false)` + 降级逻辑） | Adapter 单测验证 null bean 时 count=0、list=空 |
| 1.7 **（r3 新增）** `portal.api.dto.PortalMetricCard`（03 §A.1 的 7 字段对外响应契约）+ `MetricCardProjection.toPortal` 字段投影 | 投影单测覆盖 BigDecimal→String 格式化 |

### 7.3 Phase 2 — Entity / Mapper 基础

| Task | 产出 |
|---|---|
| 2.1 `ProductInfo` Entity + `ProductInfoMapper` + `ProductInfoMapper.xml` 基础 CRUD（insertProduct / selectById / softDelete） | Mapper 集成测试先写失败再实现 |
| 2.2 `PortalShortcut` Entity + `PortalShortcutMapper` + XML | |
| 2.3 `AddrbookEmployee` Entity + `AddrbookEmployeeMapper` + XML（含 `countEmployeesReferringProduct(productId)`） | 后者用于 D.6 前置检查 |
| 2.4 `ProductConverter` / `ShortcutConverter` + 单测 | |

### 7.4 Phase 3-9 — 业务接口 TDD 循环

每个 Phase 独立启动一个 subagent，严格按 TDD 红-绿-重构：

| Phase | 接口 | 关键测试点 |
|---|---|---|
| 3 | D.3 `GET /api/products/support-available` | Service 单测 + Mapper 集成 + Controller 集成 = 首个闭环 |
| 4 | D.1 `GET /api/products` | 3 种 DATA_SCOPE 过滤分支 |
| 5 | D.2 `GET /api/products/{id}` | 字典翻译 + 附件下载链接 + 负责人回显 + 脱敏 |
| 6 | D.4 `POST /api/products` | productCode 唯一冲突 + AFTER_COMMIT 事件 + Listener 测试 |
| 7 | D.5 `PUT /api/products/{id}` | FOR UPDATE 锁 + responsibleEmpIds diff + 不可变字段验证 |
| 8 | D.6 `DELETE /api/products/{id}` | 前置引用检查（PORTAL-40905） + 反向同步 |
| 9 | D.7 `GET /api/products/export` | 同步流式 Excel + N>5000 拒绝 + 10 列清单严格对齐 |

**subagent 复杂度警告**：D.4 / D.5 / D.6 涉及事件发布和消费者，建议每个 subagent 的任务范围限定在单个接口内，Listener 的实现由 D.4 的 subagent 首次创建，D.5 / D.6 复用。

### 7.5 Phase 10-11 — 快捷入口 + 工作台聚合

| Phase | 接口 |
|---|---|
| 10 | A.2 `GET /api/portal/shortcuts` + A.3 `PUT /api/portal/shortcuts`（一个 subagent 做两个接口，依赖简单） |
| 11 | A.1 `GET /api/portal/workspace`（最复杂，5 路 CompletableFuture + 降级） |

### 7.6 Phase 12 — 收尾

| Task | 产出 |
|---|---|
| 12.1 执行 `docs/superpowers/sql/2026-04-11-portal-resources-align.sql` 到测试库，验证 3 条新资源就位 | |
| 12.2 `mvn -pl bootstrap spring-boot:run`，smoke test 真实 HTTP 请求每个接口 | |
| 12.3 全量测试绿灯：`mvn test`（所有模块） | |
| 12.4 最后 commit `chore(portal): V1 首版 10 个接口完成` | |

### 7.7 Commit 粒度

每个 Phase 一个 commit，共约 14-16 个 commit。commit message 格式：

```
feat(portal): D.3 支持中场支持的产品查询

- ProductInfoMapper.listSupportAvailable 实现
- ProductService.listSupportAvailable 单测通过
- ProductController GET /api/products/support-available
- Mapper 集成测试覆盖 status/deleted/support 过滤

```

### 7.8 subagent 使用策略

- **Phase 0 / 1 / 2**：主执行者 直接执行（涉及 Maven 骨架和基础组件，需要上下文连贯）
- **Phase 3-11（业务接口）**：每个 Phase 启动一个 subagent，严格 TDD 红-绿-重构
- **Phase 12**：主执行者 收尾 + 启动 `code-reviewer` 审查整体实现

---

## 8. 风险与缓解

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R1 | Testcontainers 首次下载 MySQL 镜像（~500 MB） | 首次测试慢 | 提前 `docker pull mysql:8.0.36` + `withReuse(true)` |
| R2 | 父 pom 添加 portal 模块触发全量 reactor 编译 | 无关干扰 | 使用 `mvn -pl portal-content-center` 限定范围 |
| R3 | `@BizAuth` 依赖 auth 模块的 Filter/Interceptor | Controller 集成测试鉴权不生效 | 参考 governance-center 的 Controller 集成测试配置 |
| R4 | `JsonStringListTypeHandler` 处理 null / `""` 的边界 | NPE | 单测覆盖 4 种边界 |
| R5 | AFTER_COMMIT 事件消费者在进程崩溃时丢失事件 | 双向同步数据不一致 | 记 ERROR 日志 + V2 切换到 outbox |
| R6 | A.1 聚合线程池 queue 满 | 高并发响应慢 | queue=32 + `CallerRunsPolicy` |
| R7 | MetricApi 接口占位类被替换时 import 迁移 | 小范围重构 | javadoc 明确 V1 临时 |
| R8 | workflow-center 的 `WorkflowQueryApi.listRecentPendingTasks` 可能不存在 | A.1 recentTodos 无法填充 | Phase 0 确认；如缺失则 aggregateErrors 返回 TODO 标记 |
| R9 | DATA_SCOPE `orgSubtree` 为空 | 可能返回全表 | XML 强制 `<otherwise>AND 1=0</otherwise>` fail close |
| R10 | `@WithMockEmpContext` 并行测试串数据 | 偶发测试失败 | 禁用测试并行（`junit.jupiter.execution.parallel.enabled=false`） |
| R11 | AFTER_COMMIT 消费者的乐观锁更新频繁失败 | 双向同步延迟 / 数据不一致 | 指数退避重试 3 次；失败记 ERROR；对 responsibleEmpIds 频繁变更的场景做限流（V2） |
| R12 | `ddl-combined-for-portal.sql` 合并时遗漏 `USE` / `CREATE DATABASE` 导致 Testcontainers 启动失败 | 测试无法启动 | Phase 0.4 任务必须包含"grep 搜索 CREATE DATABASE / USE 并人工确认删除"的验证步骤 |

---

## 9. 验收标准

- ✅ `mvn -pl portal-content-center compile` 成功
- ✅ `mvn -pl portal-content-center test` 全部通过
- ✅ Service 单测覆盖率 ≥ 70%（Jacoco）
- ✅ `mvn -pl bootstrap spring-boot:run` 启动成功，无 error 日志
- ✅ Smoke test：每个接口至少 1 个成功的 MockMvc 测试命中并返回 200
- ✅ 10 个接口的 commit 历史（feat/chore/fix 规范）
- ✅ PT_RESOURCE 对齐脚本 `2026-04-11-portal-resources-align.sql` 已生成并提交
- ✅ 现有 auth/governance/workflow 模块测试不被破坏（`mvn test` 全量通过）
- ✅ code-reviewer subagent 出具的 review 报告无 P0 级问题

---

## 10. 后续步骤

1. 本 spec 由 spec-document-reviewer 审查并由用户最终确认
2. 进入 `writing-plans` 技能，基于本 spec 生成详细的任务级实现计划（每个 Phase 的 Task 列表、TDD 红-绿-重构顺序、验收 checkpoint）
3. 实现计划确认后，启动 `subagent-driven-development` 执行
4. 每个 Phase 完成后 commit
5. 全部完成后，启动 `code-reviewer` 进行最终审查
6. 根据 review 反馈修正，进入 `finishing-a-development-branch` 流程

---

## 附录 A：决策一览

| # | 决策项 | 选择 |
|---|---|---|
| ① | 起步模块 | portal 子域 10 接口 |
| ② | TDD 测试层级 | Service 单测 + Mapper Testcontainers + Controller MockMvc |
| ③ | BPMN 缺失处理 | Mock WorkflowQueryApi，不阻塞 |
| ④ | commit 粒度 | 每个接口一个 commit |
| 问题 1 | A.1 跨模块依赖缺失 | 定义 API 占位 + **MetricAdapter 降级包装模式**（r2 修订） |
| 问题 2 | 占位接口位置 | `portal.adapter` 子包（r2 重命名自 r1 的 `portal.stub`） |
| 问题 3 | 负责人回显 | addrbook_employee 内部 Repository |
| 问题 4 | Mapper 集成测试数据库 | Testcontainers MySQL 8.0 |
| 问题 5 | 10 个接口开发顺序 | D.3 → D.1 → D.2 → D.4 → D.5 → D.6 → D.7 → A.2 → A.3 → A.1 |
| 问题 6 | 鉴权集成点 | 真实 @BizAuth + `@WithMockEmpContext` 扩展（默认 dataScope=ORG_SUBTREE） |

---

## 附录 B：与前置文档的偏离清单

| # | 前置文档 | 原定义 | V1 本 spec 决策 | 理由 |
|---|---|---|---|---|
| B1 | 06 §2.4 / §3.3 | outbox 事务消息模式 | `@TransactionalEventListener(AFTER_COMMIT)` 进程内事件 | `sys_event_outbox` 基础设施未就绪，V2 升级 |
| B2 | 03 §D.3 | 5 分钟 Redis 缓存 | V1 不实现缓存 | 性能需求验证后 V2 补 |
| B3 | 03 §D.5 | "编辑后清理 `portal:product:support-available:all` 缓存" | V1 缓存未引入，此步骤 TODO | 与 B2 同步 |
| B4 | 03 §I | 错误码清单不含 `PORTAL-40905` | V1 新增 `PORTAL-40905` 产品仍被员工引用 | 06 §3.4 明确需要此场景错误码，03 §I 未登记是文档不一致，本 spec 顺手修补 |
| B5 | 09 §4.1 | MetricApi 来自 performance-engine-center | 在 `portal.adapter` 包定义**占位接口**（编译期满足） | performance 模块未实现，V1 用 `@Autowired(required=false)` 降级 |
| B6 | seed-v1.sql §3.2 | 缺 3 条 portal 资源 + 4 条已废弃 dashboard 资源 | Phase 0.9 产出 `2026-04-11-portal-resources-align.sql`：新增 3 条 + 标记 4 条废弃 | 与前置文档对齐的增量脚本 |
| **B7（r3 新增）** | 03 §A.1 vs 09 §4.1 | MetricCardDTO 字段清单不一致（7 字段 String vs 11 字段 BigDecimal） | 对外契约以 **03 §A.1 的 7 字段为准**；MetricAdapter 内部按 09 §4.1 取数据，Facade 层投影 | 03 是前端集成的唯一契约，必须优先；09 是 performance 的 internal DTO 设计 |
| **B8（r3 新增）** | 06 §3.4 / 03 §D.4/D.5/D.6 | 假设 `FileApi.getFileInfo / attachBizRelation / detachBizRelation` 存在 | 实际 FileApi 只有 `bindFile / getDownloadUrl / deleteFile / listBizFiles / upload`。V1 **不主动清理旧的 biz_file_rel 关联**；文件存在性校验用 `getDownloadUrl` 的异常 | FileApi 源码核实；待 governance 补 `unbindFile(bizType,bizId,fileObjectId)` 方法后升级 |
| **B9（r3 新增）** | 09 §1.1 | 示例代码用 `@BizAuth(resource = "PORTAL_WORKBENCH_AGGREGATE")` 形式 | V1 沿用 03 §A.1/A.2/A.3 的"**无需 @BizAuth**"，不引入 resource= 形式 | 03 是权威接口契约，09 的示例代码仅演示用途 |
| **B10（r3 新增）** | 09 §1.2 | 定义 portal BizType 枚举 `PRODUCT/EMPLOYEE/NAV/SHORTCUT/DOC/WORKBENCH` | V1 对 @BizAuth 注解使用 `bizType="PRODUCT"`（与 03 §D.1-D.7 一致） | 03 的 @BizAuth 示例用字符串 `"PRODUCT"`，与 09 的枚举项兼容（大写字符串） |
| **B11（r3 新增）** | workflow-center CLAUDE.md | 仅暴露 `WorkflowApi`（启动流程）；查询待办只有内部 `TodoQueryService` + REST 端点 | V1 在 `portal.adapter` 定义占位接口 `WorkflowQueryApi`，走 Adapter 降级模式 | 不修改已完成模块；V2 待 workflow-center 补 `WorkflowQueryFacade` 后迁移 |

---

## 附录 C：r3 源码核对事实清单

| 事实 | 源文件 | 行号 | 本 spec 的决策 |
|---|---|---|---|
| `DataScopeType` 枚举不含 `ORG_SELF` | `docs/common-dev-guide.md` | §5.1 第 865 行（`ORG`） | §3.3 全部改为 `ORG` |
| `WorkflowApi` 只有 3 个方法 | `workflow-center/src/main/java/.../workflow/api/WorkflowApi.java` | 18-48 行 | 创建 `portal.adapter.WorkflowQueryApi` 占位 |
| `FileApi` 没有 `getFileInfo / attachBizRelation / detachBizRelation` | `system-governance-center/src/main/java/.../governance/api/FileApi.java` | 15-67 行 | 用 `getDownloadUrl` 探测 + `bindFile` 幂等 |
| `sys_event_outbox` 表不存在 | `common` 模块 grep 零命中 | — | V1 用 `@TransactionalEventListener(AFTER_COMMIT)` |
| `MetricApi` 未实现 | performance-engine-center 模块不存在 | — | 用 `@Autowired(required=false)` 降级 |
| seed-v1.sql 缺 `/api/portal/workspace` 和 `/api/products/support-available` | `docs/schema/seed-v1.sql` | §3.2 第 347-382 行 | Phase 0.9 补齐 |
| seed-v1.sql 含 4 条废弃的 dashboard 资源 | 同上 | 第 351-354 行 | Phase 0.9 标记 deleted=1 |

---

**文档结束**
