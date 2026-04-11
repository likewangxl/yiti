# portal-content-center V1 首版切片设计（工作台聚合 + 产品资料库）

**创建日期**：2026-04-11
**作者**：Claude Code (与 leid 协作)
**模块**：`portal-content-center`
**版本**：V1 首版（仅实现 10 个接口的子集）
**前置文档**：
- `docs/modules/portal-content-center/01-功能规格.md` ~ `09-依赖契约摘要.md`（完整模块设计）
- `docs/common-dev-guide.md`（共享开发规范）
- `docs/schema/ddl-portal.sql`（DDL 源）
- `docs/schema/seed-v1.sql §3.2 / §4.x`（PT_RESOURCE + 角色资源绑定基线）

---

## 1. 背景与目标

### 1.1 背景

项目 `branch-platform`（银行分行业务平台）已完成 5 个核心模块（common / auth / governance / workflow / bootstrap）并通过 100 条接口的全量测试。下一阶段进入 5 个待开发业务模块（portal / customer / bizapp / performance / report）的实现。

5 个模块的完整设计文档已在 `docs/modules/<module>/01-09` 齐备，并通过 2026-04-10 的维度补齐（导出规范 / 错误码 / DATA_SCOPE / 字段溯源等）达到"字段级可执行"精度。

### 1.2 本切片目标

选 **portal-content-center** 作为第一个进入开发的业务模块，但**不实现全部 30+ 接口**，而是选一个**最小可验证子集**（10 个接口），用于：

1. 从零建立 portal 模块的 Maven 骨架 + 包结构，成为后续 customer/bizapp/performance 开发的参考蓝本
2. 跑通"TDD（Service 单测 + Mapper Testcontainers 集成测试 + Controller MockMvc 集成测试）+ subagent-driven-development + code review"的完整工具链
3. 验证跨模块 QueryApi 的 stub 模式（为 customer/performance 未完成时的先行集成铺路）
4. 落地 `@BizAuth + DataScope` 的真实测试闭环（通过自定义 `@WithMockEmpContext` JUnit 扩展）

### 1.3 为什么选这 10 个接口

这 10 个接口的选取覆盖了 3 个关键维度：

| 维度 | 接口 | 价值 |
|---|---|---|
| **只读 CRUD** | D.3 / D.1 / D.2 | 最简单的 TDD 起手式，验证 Mapper + DATA_SCOPE + 字典翻译 |
| **写操作 + 事务** | D.4 / D.5 / D.6 | 验证悲观锁 + 事件发布 + 双向同步 + 附件绑定 |
| **导出 + 聚合** | D.7 / A.1 / A.2 / A.3 | 验证导出规范附录 H + CompletableFuture 并行聚合 + 降级策略 |

### 1.4 范围外（V1 不做）

- **B 章节 网址导航**（B.1-B.5）
- **C 章节 通讯录 REST 接口**（C.1-C.4；但内部 Repository 仍需建立，D.2 产品详情的负责人回显依赖它）
- **E 章节 文档管理**（E.1-E.5）
- **异步导出任务**（D.7 > 5000 行分支，依赖 governance `sys_async_task` 表，本次返回 TODO 错误码 `PORTAL-42299`）
- **A.1 的 recentDocuments / recentProducts 两路**（V2）
- **BPMN 文件补齐**
- **Kafka / RocketMQ**（事件只在进程内用 `@TransactionalEventListener` 发布）
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
    │   │   ├── stub/                       # ★ 临时接口（待 customer/performance 模块创建后迁移）
    │   │   │   ├── CustomerQueryApi.java
    │   │   │   ├── MetricApi.java
    │   │   │   ├── dto/
    │   │   │   └── impl/                   # @Primary @ConditionalOnMissingBean 空实现
    │   │   ├── controller/                 # 3 个 Controller
    │   │   ├── facade/                     # ProductFacade (ProductApi 实现)
    │   │   ├── service/                    # WorkspaceService / ShortcutService / ProductService / AddrbookService / ProductExportService
    │   │   ├── mapper/                     # ProductInfoMapper / PortalShortcutMapper / AddrbookEmployeeMapper
    │   │   ├── entity/                     # ProductInfo / PortalShortcut / AddrbookEmployee
    │   │   ├── enums/                      # ProductStatus / ShortcutType
    │   │   ├── convert/                    # ProductConverter / ShortcutConverter
    │   │   ├── config/                     # PortalMyBatisConfig / PortalAsyncConfig
    │   │   └── typehandler/                # JsonStringListTypeHandler
    │   └── resources/
    │       ├── mapper/                     # MyBatis XML
    │       │   ├── ProductInfoMapper.xml
    │       │   ├── PortalShortcutMapper.xml
    │       │   └── AddrbookEmployeeMapper.xml
    │       └── application-portal.yml      # 模块级配置片段（可选）
    └── test/
        ├── java/com/bank/branch/platform/portal/
        │   ├── support/                    # 测试支持类
        │   │   ├── WithMockEmpContext.java # JUnit 5 扩展
        │   │   ├── AbstractMapperIntegrationTest.java  # Testcontainers 基类
        │   │   └── AbstractControllerIntegrationTest.java  # MockMvc 基类
        │   ├── service/                    # Service 单测（Mockito）
        │   ├── mapper/                     # Mapper 集成测试（Testcontainers MySQL）
        │   └── controller/                 # Controller 集成测试（MockMvc）
        └── resources/
            └── sql/
                └── portal-test-data.sql    # 测试 fixture
```

### 2.2 父 pom.xml 与 bootstrap 的修改

- 父 pom.xml 的 `<modules>` 增加 `portal-content-center`（放在 workflow-center 之后）
- 父 pom.xml 的 `<dependencyManagement>` 增加 portal-content-center 的 artifact 声明
- bootstrap/pom.xml 增加 portal-content-center 依赖（让最终可执行 jar 包含它）

### 2.3 portal/pom.xml 依赖清单

**运行时依赖**：
- common-web / common-trace / common-security / common-aop / common-db
- auth-permission-center（`@BizAuth` + `DataScopeContext` + `CurrentUserApi`）
- system-governance-center（`FileApi` + `NotifyApi` + `AuditApi` + `DictApi`）
- workflow-center（`WorkflowQueryApi`）
- spring-boot-starter-web
- mybatis-spring-boot-starter
- spring-boot-starter-data-redis
- spring-session-data-redis

**测试依赖**：
- spring-boot-starter-test
- mybatis-spring-boot-starter-test
- testcontainers-mysql（新引入：`org.testcontainers:mysql:1.19.7`）
- testcontainers-junit-jupiter
- spring-security-test（用于鉴权测试支持）

### 2.4 基础包路径

`com.bank.branch.platform.portal`

### 2.5 关键架构决策（与已有模块一致）

1. **贫血模型**：Entity 不含业务逻辑，Service + DAO + Entity 分层
2. **跨模块只通过 `*Api`**：严禁直接依赖其他模块的 mapper/entity/serviceImpl
3. **MyBatis XML 优先**：所有 SQL（含简单 CRUD）写在 XML，保持一致
4. **错误码前缀**：`PORTAL-{HTTP_STATUS}{SEQ}`
5. **事件发布**：`@TransactionalEventListener(AFTER_COMMIT)` + `ApplicationEventPublisher`（进程内）

---

## 3. 数据模型

### 3.1 使用的表（3 张，全部为 portal 自有）

| 表 | 主键 | 本次作用 | DDL 源 |
|---|---|---|---|
| `product_info` | `id` VARCHAR(64) | 产品资料库的主表 | `ddl-portal.sql §4` |
| `portal_shortcut` | `id` VARCHAR(32) | 快捷入口 | `ddl-portal.sql §2` |
| `addrbook_employee` | `emp_id` VARCHAR(32) | 通讯录（内部用，不开 REST） | `ddl-portal.sql §3` |

**不涉及的表**：`portal_nav`（导航） / `doc_info`（文档）

### 3.2 JSON 字段处理（关键设计）

`product_info.responsible_emp_ids` 和 `addrbook_employee.responsible_product_ids` 都是 `TEXT` 类型存 JSON 数组字符串。

**处理方式**：
- **存储**：Jackson 序列化为 `["E10001","E10002"]` 字符串
- **TypeHandler**：自定义 `JsonStringListTypeHandler implements TypeHandler<List<String>>`
- **查询**：**应用层反序列化 + IN 查询**，不使用 MySQL `JSON_CONTAINS`（避免方言耦合）
  - 查产品的负责人：先读 product_info → 拿到 empIds → `SELECT * FROM addrbook_employee WHERE emp_id IN (...)`
  - 查员工负责的产品：先读 addrbook_employee → 拿到 productIds → `SELECT * FROM product_info WHERE id IN (...)`
- **约束**：`responsibleEmpIds` 最多 10 个（应用层校验）
- **TypeHandler 测试**：必须覆盖 `null` / `""` / `"[]"` / `"[\"E001\"]"` 四种情况

### 3.3 DATA_SCOPE 过滤

`product_info` 按 `product_dept_org_code` 过滤。参考 `docs/modules/portal-content-center/09-依赖契约摘要.md §10` 已具化的 MyBatis XML 片段：

```xml
<sql id="dataScopeFilter">
    <choose>
        <when test="dataScope.type == 'ALL'"/>
        <when test="dataScope.type == 'ORG_SUBTREE'">
            AND product_dept_org_code IN
            <foreach collection="dataScope.orgSubtree" item="o" open="(" close=")" separator=",">#{o}</foreach>
        </when>
        <when test="dataScope.type == 'ORG_SELF'">
            AND product_dept_org_code = #{dataScope.orgCode}
        </when>
        <when test="dataScope.type == 'SELF_CREATED'">
            AND created_by = #{dataScope.empId}
        </when>
        <otherwise>AND 1 = 0</otherwise>  <!-- Fail Close -->
    </choose>
</sql>
```

**Fail Close 原则**：未知 DataScope 类型时 `AND 1=0`，宁可查不到数据也不开放越权。

### 3.4 Entity 字段（关键字段）

#### ProductInfo

| 字段 | 类型 | 说明 |
|---|---|---|
| id | String | 产品 ID（UUID） |
| productCode | String | 产品代码（DB UK） |
| productName | String | 产品名称 |
| productCategory | String | 字典 PRODUCT_CATEGORY |
| description | String | 产品描述（TEXT） |
| supportForSupportRequest | Boolean | 是否支持中场支持 |
| ownerOrgId | String | 归属机构 |
| productDeptOrgCode | String | 产品部门 ORG_CODE（DATA_SCOPE 字段） |
| fileObjectId | String | 主附件 ID |
| responsibleEmpIds | List\<String\> | 负责人 ID 列表（JSON TypeHandler） |
| status | String | `ACTIVE` / `DISABLED` |
| createdBy / createdTime / updatedBy / updatedTime / deleted | 审计字段 | — |

#### PortalShortcut

| 字段 | 类型 | 说明 |
|---|---|---|
| id | String | UUID |
| shortcutName / shortcutUrl / shortcutIcon | String | 快捷入口基本属性 |
| shortcutType | String | `SYSTEM` / `CUSTOM` |
| targetType | String | `INTERNAL` / `EXTERNAL` |
| empId | String | 所属用户（CUSTOM 必填，SYSTEM 为 null） |
| sortOrder / status / 审计字段 | — | — |

#### AddrbookEmployee

| 字段 | 类型 | 说明 |
|---|---|---|
| empId | String（主键） | 员工工号 |
| empName / mobile / email | String | 基本信息 |
| orgCode / orgName / position | String | 组织 / 岗位 |
| selfDesc | String | 自我描述 |
| responsibleProductIds | List\<String\> | 负责产品 ID 列表（JSON TypeHandler） |
| status | String | `ACTIVE` / `RESIGNED` |
| maintainerEmpId / createdTime / updatedTime / deleted | — | — |

---

## 4. 接口契约详情

### 4.1 D.3 `GET /api/products/support-available` — 支持中场支持的产品

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="READ")`
- **请求**：无参数
- **响应**：`CommonResult<List<ProductSimpleDTO>>`
- **SQL**：`WHERE support_for_support_request = 1 AND status = 'ACTIVE' AND deleted = 0 ORDER BY product_name`
- **ProductSimpleDTO 字段**：`id / productCode / productName / productCategory / productDeptOrgCode`
- **错误码**：仅通用 `AUTH-40301`

### 4.2 D.1 `GET /api/products` — 产品列表

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="LIST")`
- **请求参数**：
  | 字段 | 类型 | 必填 | 校验 |
  |---|---|---|---|
  | `keyword` | String | 否 | `@Size(max=100)` |
  | `category` | String | 否 | — |
  | `status` | String | 否 | `ACTIVE` / `DISABLED` / `ALL`（默认 `ACTIVE`） |
  | `pageNo` | Integer | 否 | `@Min(1)`（默认 1） |
  | `pageSize` | Integer | 否 | `@Min(1) @Max(100)`（默认 20） |
- **响应**：`CommonResult<PageResult<ProductDTO>>`
- **DATA_SCOPE**：按 `product_dept_org_code` 过滤
- **错误码**：`PORTAL-42200` 参数校验失败

### 4.3 D.2 `GET /api/products/{id}` — 产品详情

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="READ")`
- **响应 `CommonResult<ProductDetailDTO>`**
- **ProductDetailDTO** 含 14 个字段（见第 3 段接口契约详情），关键字段：
  - `productCategoryDesc`（调 `DictApi.getDictLabel("PRODUCT_CATEGORY", productCategory)`）
  - `attachment`（FileRefDTO，调 `FileApi.getDownloadUrl(fileObjectId)`）
  - `responsibleEmployees`（List\<ResponsibleEmpDTO\>，本地 addrbook join）
- **ResponsibleEmpDTO** 含 `empId / empName / mobile(脱敏 `138****5678`) / position`
- **错误码**：`PORTAL-40003` 产品不存在、`PORTAL-40304` 数据范围越权

### 4.4 D.4 `POST /api/products` — 新增产品

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="WRITE")`
- **请求体 `ProductCreateReqDTO`**：参考 03 文档 §4.4 的字段定义
- **业务逻辑**：
  1. 校验 productCode 全局唯一（依赖 DB UK），否则 `PORTAL-40901`
  2. 校验 productCategory 为字典有效值（`DictApi.isValidDictValue`），否则 `PORTAL-42201`
  3. 校验 productDeptOrgCode 存在（调用 OrgApi），否则 `PORTAL-42202`
  4. 校验 responsibleEmpIds 每个在 addrbook_employee 中存在，否则 `PORTAL-42203`
  5. INSERT product_info（事务内）
  6. 若有 fileObjectId，调 `FileApi.bindFile(bizType=PRODUCT, bizId=newId, fileObjectId, fileRole=MAIN)`
  7. **双向同步**：`SELECT ... FOR UPDATE` 锁定每个 responsibleEmp，追加 productId 到 responsible_product_ids
  8. 发布事件 `portal.product.created.v1`（AFTER_COMMIT）

### 4.5 D.5 `PUT /api/products/{id}` — 编辑产品

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="WRITE")`
- **请求体**：同 D.4，所有字段可选（部分更新语义）
- **业务逻辑**：
  1. `SELECT ... FOR UPDATE` 锁定 product_info 行
  2. 字段校验（同 D.4）
  3. 若 responsibleEmpIds 有变更：计算 diff（新增的 / 移除的），分别更新 addrbook_employee
  4. UPDATE product_info
  5. 若 fileObjectId 变更：`FileApi.unbindFile` + `FileApi.bindFile`
  6. 发布事件 `portal.product.updated.v1`

### 4.6 D.6 `DELETE /api/products/{id}` — 删除产品（逻辑删除）

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="WRITE")`
- **业务逻辑**：
  1. `UPDATE product_info SET deleted = 1 WHERE id = ?`
  2. 从所有 addrbook_employee.responsible_product_ids 中移除该 productId
  3. `FileApi.unbindFile`（解除附件关联）
  4. 发布事件 `portal.product.deleted.v1`

### 4.7 D.7 `GET /api/products/export` — 产品导出

- **鉴权**：`@BizAuth(bizType="PRODUCT", action="EXPORT")`（高危）
- **行为**（参考 03 文档附录 H）：
  1. `SELECT count(*)` 取精确命中行数
  2. **N ≤ 5000**：同步流式返回 Excel（EasyExcel）
  3. **5000 < N ≤ 100000**：**V1 返回 `PORTAL-42299` TODO**（异步分支待 governance sys_async_task 就绪）
  4. **N > 100000**：拒绝 `PORTAL-42207`
- **列定义**：严格按 03 文档 §H.7.1 的 10 列清单
- **脱敏**：负责人手机号列按 `138****5678`
- **审计**：`AuditApi.log(EXPORT, bizType=PRODUCT, filter=..., rowCount=..., result=...)`

### 4.8 A.2 `GET /api/portal/shortcuts` — 查询快捷入口

- **鉴权**：`@BizAuth(bizType="PORTAL_SHORTCUT", action="READ")`
- **响应**：`CommonResult<List<ShortcutDTO>>`
- **SQL**：`WHERE (shortcut_type='SYSTEM') OR (shortcut_type='CUSTOM' AND emp_id=#{currentEmpId}) ORDER BY shortcut_type DESC, sort_order ASC`

### 4.9 A.3 `PUT /api/portal/shortcuts` — 保存个性化快捷入口

- **鉴权**：`@BizAuth(bizType="PORTAL_SHORTCUT", action="WRITE")`
- **请求体**：`customShortcuts` 列表（最多 20 个）
- **业务逻辑**：事务内 DELETE 当前用户的所有 CUSTOM → 批量 INSERT 新的 → 返回合并后的完整列表

### 4.10 A.1 `GET /api/portal/workspace` — 工作台聚合

- **鉴权**：`@BizAuth(bizType="PORTAL_WORKSPACE", action="READ")`
- **响应 WorkspaceDTO 字段**：
  | 字段 | 来源 | 超时 |
  |---|---|---|
  | `shortcuts` | 内部调 ShortcutService | 100 ms |
  | `pendingTasks` | `WorkflowQueryApi.countPendingTasks` | 500 ms |
  | `unreadNotifications` | `NotifyApi.countUnread` | 500 ms |
  | `touchTasks` | **stub** `CustomerQueryApi.getTouchTaskSummary` | 500 ms |
  | `kpiCards` | **stub** `MetricApi.getPersonalKpiCards` | 800 ms |
  | `recentDocuments` | **V2 不实现** | — |
  | `recentProducts` | **V2 不实现** | — |
- **并发编排**：`CompletableFuture.allOf(...)` + `orTimeout(2, SECONDS)` + 独立线程池 `portalAggregateExecutor`
- **降级**：任一路失败/超时 → 该字段返回默认值（0 或空列表） + 响应体增加 `degraded: true` + `failedSources: ["kpiCards"]`，整体仍返回 200 OK

### 4.11 错误码完整清单

| code | HTTP | 消息 |
|---|---|---|
| `PORTAL-40003` | 400 | 产品不存在 |
| `PORTAL-40301` | 403 | 未授权（由 AUTH 链路抛，继承错误码） |
| `PORTAL-40304` | 403 | 数据范围越权 |
| `PORTAL-40901` | 409 | 产品代码已存在 |
| `PORTAL-42200` | 422 | 参数校验失败（通用） |
| `PORTAL-42201` | 422 | 产品类别字典值无效 |
| `PORTAL-42202` | 422 | 产品部门不存在 |
| `PORTAL-42203` | 422 | 负责人 empId 不存在或超过 10 个 |
| `PORTAL-42207` | 422 | 导出行数超上限（100000） |
| `PORTAL-42299` | 422 | V1 暂不支持异步导出（N > 5000） |
| `PORTAL-50002` | 500 | 导出文件生成失败 |

### 4.12 事件发布（本模块 V1）

| 事件名 | 发布时机 | 消费方 |
|---|---|---|
| `portal.product.created.v1` | D.4 事务提交后 | 预留 |
| `portal.product.updated.v1` | D.5 事务提交后 | 预留 |
| `portal.product.deleted.v1` | D.6 事务提交后 | 预留 |

---

## 5. 跨模块依赖策略

### 5.1 已存在模块的 API（直接调用）

| API | 模块 | 用途 |
|---|---|---|
| `CurrentUserApi` | auth-permission-center | 获取当前用户 empId / orgCode |
| `DataScopeContext` | auth-permission-center | 获取当前 BizType 的 DataScope |
| `OrgApi` | auth-permission-center | 校验 orgCode 存在 / 获取机构树 |
| `DictApi` | system-governance-center | 字典翻译 / 校验 |
| `FileApi` | system-governance-center | 附件 upload / bindFile / getDownloadUrl |
| `NotifyApi` | system-governance-center | 未读通知 / 通知推送 |
| `AuditApi` | system-governance-center | 审计日志写入 |
| `WorkflowQueryApi` | workflow-center | 查询待办任务 |

### 5.2 Stub 接口（未实现模块的临时占位）

| Stub API | 目标模块 | 本次定位 |
|---|---|---|
| `stub.CustomerQueryApi` | customer-marketing-center | 仅定义 `getTouchTaskSummary(empId)` 方法，签名来自 customer 04 契约 |
| `stub.MetricApi` | performance-engine-center | 仅定义 `getPersonalKpiCards(empId)` 方法，签名来自 performance 04 契约 |

**Stub 实现规则**：
- 位于 `portal.stub.impl` 包
- 使用 `@Primary @ConditionalOnMissingBean(name="customerQueryApi")` 注册 bean
- 返回空对象 / 空列表（`TouchTaskSummaryDTO.empty()` / `List.of()`）
- 类级 javadoc 明确标注 `@deprecated V1 临时实现，customer-marketing-center 模块创建后迁移到正式位置`
- 未来 customer/performance 模块创建真实 bean 时，Spring 自动选择真实 bean（`@ConditionalOnMissingBean` 不命中时 stub 不注册）

### 5.3 迁移路径

当 customer/performance 模块创建时：
1. customer 模块在 `com.bank.branch.platform.customer.api.CustomerQueryApi` 定义正式接口
2. customer-marketing-center 依赖 portal（反向依赖不可取）— 不行
3. 正确做法：portal 的 stub 接口**迁移**到 customer 模块的 api 包，portal 改为依赖 customer 的 api 包
4. 迁移时只改 import 路径，接口方法签名保持兼容

---

## 6. 测试策略

### 6.1 三层测试分工

| 层级 | 工具 | 数量预估 | 单次耗时 | 作用 |
|---|---|---|---|---|
| **Service 单测** | Mockito + JUnit 5 | 60-80 | 10-50 ms | 业务逻辑 / 状态机 / 字段校验 / 错误码 / 事件发布时机 |
| **Mapper 集成测试** | Testcontainers MySQL 8.0 | 20-30 | 50-200 ms | SQL 正确性 / JSON TypeHandler 往返 / DATA_SCOPE 过滤 / 索引命中 |
| **Controller 集成测试** | MockMvc + `@WithMockEmpContext` | 15-25 | 100-500 ms | HTTP 报文契约 / @BizAuth 注解生效 / 异常映射 / 统一响应包装 |

**合计**：95-135 个测试，首次 ~45 秒（含容器启动），后续 ~30 秒。

### 6.2 Testcontainers 基类设计

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
            .withReuse(true)
            .withInitScript("sql/ddl-combined-for-portal.sql");  // 合并 auth + governance + portal DDL

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", MYSQL::getJdbcUrl);
        reg.add("spring.datasource.username", MYSQL::getUsername);
        reg.add("spring.datasource.password", MYSQL::getPassword);
    }
}
```

**关键点**：
- `withReuse(true)` 让容器跨测试类复用，降低启动开销
- `withInitScript` 只建表，不插种子数据
- 测试 fixture 通过 `@Sql` 按方法重置
- 合并的 DDL 文件位于 `src/test/resources/sql/ddl-combined-for-portal.sql`（Phase 0 任务产出）

### 6.3 `@WithMockEmpContext` 自定义扩展

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MockEmpContextExtension.class)
public @interface WithMockEmpContext {
    String empId() default "E10001";
    String orgCode() default "ORG_SZ_001";
    String[] roleCodes() default {"R_RM"};
    String dataScope() default "ALL";
    String[] orgSubtree() default {};
}
```

**Extension 实现**：
- `BeforeEachCallback`：读取注解参数 → 设置 `SecurityContextHolder` + `DataScopeContextHolder`（或等效 ThreadLocal）
- `AfterEachCallback`：清理 ThreadLocal / SecurityContextHolder

**使用示例**：
```java
@Test
@WithMockEmpContext(empId = "E10001", dataScope = "ORG_SUBTREE", orgSubtree = {"ORG_SZ_001", "ORG_SZ_002"})
void shouldListProductsWithOrgSubtree() throws Exception {
    mockMvc.perform(get("/api/products"))
           .andExpect(status().isOk());
}
```

### 6.4 Service 单测命名规范

- `ProductServiceTest.shouldRejectDuplicateProductCode()`
- `ProductServiceTest.shouldSyncResponsibleEmpIdsOnUpdate()`
- `WorkspaceServiceTest.shouldReturnDegradedWhenKpiSourceTimeout()`

---

## 7. 开发计划（Phase 粒度）

按 `D.3 → D.1 → D.2 → D.4 → D.5 → D.6 → D.7 → A.2 → A.3 → A.1` 顺序分 10 个 Phase，加上 Phase 0/1/10 的基础设施共约 14-16 个 commit。

详细 Phase 任务列表见第 8 段（由后续 writing-plans 技能生成的实现计划负责落地）。

### 7.1 Commit 粒度

**每个接口一个 commit**（和用户 ① 的决策一致），commit message 格式：

```
feat(portal): D.3 支持中场支持的产品查询

- ProductInfoMapper.listSupportAvailable 实现
- ProductService.listSupportAvailable 单测通过
- ProductController GET /api/products/support-available
- Mapper 集成测试覆盖 status/deleted/support 过滤

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
```

### 7.2 subagent 使用策略

- **基础设施 Phase（0 / 1）**：主 Claude 直接执行（涉及 Maven 骨架创建，需要上下文连贯）
- **业务 Phase（3-9）**：每个 Phase 启动一个 subagent 处理 `TDD 循环 + 接口实现`
  - subagent 使用 `superpowers:test-driven-development` 和 `superpowers:subagent-driven-development` 的规范
  - 每个 subagent 的任务范围严格限定在 1 个接口的全部实现（Mapper + Service + Controller + 测试）
  - 完成后返回给主 Claude，主 Claude 验证并 commit
- **Review Phase（10）**：启动 `superpowers:code-reviewer` 审查整体实现

---

## 8. 风险与缓解

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R1 | Testcontainers 首次下载 MySQL 镜像（~500 MB） | 首次测试慢 | 提前 `docker pull mysql:8.0.36` + `withReuse(true)` |
| R2 | 父 pom 添加 portal 模块触发全量 reactor 编译 | 无关干扰 | 使用 `mvn -pl portal-content-center` 限定范围 |
| R3 | `@BizAuth` 依赖 auth 模块的 Filter/Interceptor | Controller 集成测试鉴权不生效 | 参考 governance-center 的 Controller 集成测试配置 |
| R4 | `JsonStringListTypeHandler` 处理 null / `""` 的边界 | NPE | TypeHandler 单测覆盖 4 种边界 |
| R5 | D.4/D.5 双向同步死锁 | 生产偶发事务失败 | 悲观锁顺序：product → employee，按 ID 升序；失败重试 3 次 |
| R6 | A.1 聚合线程池 queue 满 | 高并发响应慢 | queue=32 + `CallerRunsPolicy`（降级到调用线程） |
| R7 | stub 被替换时 import 迁移 | 小范围重构 | javadoc 明确 `@deprecated`，IDE 高亮 |
| R8 | workflow-center 的 `WorkflowQueryApi.countPendingTasks` 在无 BPMN 时可能返回 0 | A.1 待办卡始终为 0 | 单测 mock；真实启动时 degraded 正常 |
| R9 | DATA_SCOPE `orgSubtree` 为空 | 可能返回全表 | XML 强制 `<otherwise>AND 1=0</otherwise>` fail close |
| R10 | `@WithMockEmpContext` 并行测试串数据 | 偶发测试失败 | AfterEach 清理 ThreadLocal；禁用测试并行 |

---

## 9. 验收标准

- ✅ `mvn -pl portal-content-center compile` 成功
- ✅ `mvn -pl portal-content-center test` 全部通过
- ✅ Service 单测覆盖率 ≥ 70%（Jacoco）
- ✅ `mvn -pl bootstrap spring-boot:run` 启动成功，无 error 日志
- ✅ Smoke test：每个接口至少 1 个成功的 MockMvc 测试命中并返回 200
- ✅ 10 个接口的 commit 历史（feat/chore/fix 规范）
- ✅ PT_RESOURCE 核对脚本已生成（如有差异）
- ✅ 现有 auth/governance/workflow 模块测试不被破坏（`mvn test` 全量通过）
- ✅ code-reviewer subagent 出具的 review 报告无 P0 级问题

---

## 10. 后续步骤

1. 本 spec 由 spec-document-reviewer 审查并由用户确认
2. 进入 `superpowers:writing-plans` 技能，基于本 spec 生成详细的任务级实现计划（每个 Phase 的 Task 列表、TDD 红-绿-重构顺序、验收 checkpoint）
3. 实现计划确认后，启动 `superpowers:subagent-driven-development` 执行计划
4. 每个 Phase 完成后 commit
5. 全部完成后，启动 `superpowers:code-reviewer` 进行最终审查
6. 根据 review 反馈修正，进入 `superpowers:finishing-a-development-branch` 流程

---

## 附录 A：开发决策一览

| # | 决策项 | 选择 |
|---|---|---|
| ① | 起步模块 | **D**：portal 子域 10 接口（工作台聚合 A.1-A.3 + 产品资料库 D.1-D.7） |
| ② | TDD 测试层级 | **a+b+c**：Service 单测 + Mapper 集成 + Controller 集成 |
| ③ | BPMN 缺失处理 | **A**：Mock WorkflowApi，不阻塞业务代码 |
| ④ | commit 粒度 | **B**：每个接口一个 commit |
| 问题 1 | A.1 跨模块依赖缺失 | **A**：定义 QueryApi 接口 + Stub 实现 |
| 问题 2 | QueryApi 接口位置 | **B**：portal.stub 子包 |
| 问题 3 | 负责人回显 | **A**：addrbook_employee 建表 + 内部 Repository，不开 REST |
| 问题 4 | Mapper 集成测试数据库 | **A**：Testcontainers MySQL 8.0 |
| 问题 5 | 10 个接口开发顺序 | **A**：D.3 → D.1 → D.2 → D.4-D.6 → D.7 → A.2-A.3 → A.1 |
| 问题 6 | 鉴权集成点 | **B**：真实 @BizAuth + `@WithMockEmpContext` 自定义扩展 |

---

**文档结束**
