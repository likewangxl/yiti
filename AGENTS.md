# AGENTS.md

本文件是仓库级 AI 开发指导。后续所有回答使用中文，读取和编辑文本统一使用 UTF-8。

`AGENTS.md` 是唯一权威的代理指导文件：进入子目录工作时，必须继续读取该目录及其父级适用的 `AGENTS.md`；不再创建或维护其他代理指导副本。

## 项目与权威来源

Branch Platform（分行业务平台）采用 Spring Boot 模块化单体后端和 Vue/Vite 前端：

- 根 `pom.xml` 是 Maven 聚合模块、依赖与版本的权威来源；`bootstrap` 是唯一 Spring Boot 启动入口。
- `bootstrap/src/main/resources/application*.yml` 是后端端口、数据源、Session、Flowable、Quartz、MyBatis-Plus、OBS 等运行配置的权威来源。
- `xanzc_frontend/vite.config.js` 及其环境变量是前端开发端口和代理目标的权威来源。
- 实现行为以源码和测试为准；接口契约见 `docs/modules/<模块>/03-接口设计*`、`04-对外API契约*`，表结构以目标库实际 schema 及获准的权威文档为准。
- 不在本文件固化依赖版本、端口、口令、机器进程或类/端点数量。执行命令前现场读取配置，禁止擅自修改或回退本地端口、代理、数据库连接和外联配置。

## 模块边界

根 POM 聚合以下后端工程：

| 工程 | 职责 |
|---|---|
| `common` | web、trace、安全、AOP、数据库公共基础设施 |
| `auth-permission-center` | 认证、RBAC、数据范围、组织与角色配置 |
| `system-governance-center` | 字典、配置、审计、通知、文件、Quartz 调度 |
| `workflow-center` | Flowable 工作流能力 |
| `portal-content-center` | 门户与内容 |
| `customer-marketing-center` | 客户营销 |
| `business-application-center` | 业务申请 |
| `performance-engine-center` | 绩效计算与评价 |
| `report-analytics-center` | 报表、导出、自由报表和大屏 |
| `red-engine-center` | 红色引擎党建管理 |
| `soap-gateway-center` | SOAP/callpu 外部渠道网关 |
| `bootstrap` | 聚合全部后端模块并启动 |

`xanzc_frontend/` 是独立 npm 工程，不在 Maven 聚合内。

### 必须遵守的依赖规则

1. 跨模块只依赖对方 `api/` 中的 `*Api`、`*QueryApi` 及 DTO；禁止引用其他模块的 `mapper`、`entity`、内部 `service` 或实现类，禁止跨模块直接 join 对方私有表。
2. `auth-permission-center` 只依赖 common，不反向依赖业务模块。
3. `system-governance-center` 是平台 Quartz 集成点；`workflow-center` 是唯一可直接调用 Flowable API 的模块。
4. `report-analytics-center` 对上游业务域只读，但可维护自身的查询定义、导出任务、自由报表、大屏配置和发布状态；“只读”不等于本模块数据库无写操作。业务模块不得依赖 report，report 也不作为跨模块查询中转层。
5. `red-engine-center` 自管审核状态机，不接 Flowable；`soap-gateway-center` 是外部渠道适配层。实际 Maven 依赖始终以各模块 `pom.xml` 为准，不在本文复制完整依赖图。
6. 文件对象实际由 `system-governance-center` 的 `ObsStorageClient` 存入华为云 OBS。源码中残留的 “MinIO” 名称或注释不代表当前实现；不得据此重新引入 MinIO。

## 认证、权限与数据边界

- 认证/RBAC 白名单以 `AuthenticationFilter.WHITELIST` 和 `WebMvcAuthConfig` 为准，两处必须保持一致。白名单只表示跳过相应认证或 RBAC 环节，不代表任意未标注 `@BizAuth` 的接口都是公开接口。
- 新增非白名单 REST 端点必须登记 `PT_RESOURCE`，业务 Controller 公共方法必须声明匹配的 `@BizAuth`。已有认证自服务等例外按源码、资源配置和测试处理，不得扩展成通用豁免。
- `SYS_ADMIN` 只在明确实现的 RBAC 环节享有跳过能力，不自动绕过屏级角色白名单、命名机构组、机构画像、数据范围或其他业务专属门禁；未知、空值和查询异常一律 Fail Close。
- 所有写操作必须在 Service 层基于目标实体做二次权限校验；所有读、详情和导出接口都必须应用统一数据范围，不能只在前端隐藏入口。
- 高危操作必须使用独立 URL、独立资源授权并独立审计。日志中的手机号、证件号、账号、金额和凭据必须脱敏。
- Session 使用 Spring Session JDBC；共享锁使用 `LockManager`/`PT_LOCK`。不要为这两类能力重新引入 Redis。

## 代码与契约规范

- 包结构遵循 `api`、`controller`、`facade`、`service`、`mapper`、`entity`、`config` 分层；跨模块实现放在 `facade`，内部实现使用 `*ServiceImpl`。
- Service 类和 public 方法必须有注释；复杂逻辑说明“为什么”。禁止用 `Object` 等通用容器规避清晰类型契约。
- API 入参、出参、traceId 与耗时由统一基础设施记录；新增能力优先复用 common 自动配置，不得另建平行实现。
- 改动 `controller/`、`api/`、DTO、错误码或 `@BizAuth` 时，同步更新对应模块接口文档和 `PT_RESOURCE` 数据，并运行 `scripts/check-contract-docs.sh`。
- 规范实现位置统一登记在 `docs/code-examples.md`；共通响应、分页、鉴权、数据范围、审计、事件与日志规范见 `docs/common-dev-guide.md`。

## 构建与测试

```bash
mvn clean install -DskipTests
mvn test
mvn verify
mvn clean package
cd bootstrap && mvn spring-boot:run
```

- 跨模块改动或 bootstrap 测试依赖上游最新类时，先执行 `mvn clean install -DskipTests` 刷新本地 SNAPSHOT，再运行目标测试，避免 stale jar 造成假故障。
- `*Test.java`/`*Tests.java` 由 Surefire 执行；`*IT.java` 由 Failsafe 在 `verify` 阶段执行。新集成测试使用 `*IT.java`。
- 测试编码已由 Maven 统一为 UTF-8，不要在单个 `@Sql` 重复配置编码。
- 运行任何真库测试前必须读取测试 profile 的实际数据源。配置指向 `yiti` 或其他非隔离库时，不得因“测试会回滚”而默认执行。

### TDD（绝对红线）

- 一切特性开发和 Bug 修复必须完成 Red-Green-Refactor：先添加能稳定失败的测试，再写最小实现使其通过，最后重构。
- 禁止先堆实现、事后补测试凑覆盖率。

## 数据库与 SQL（绝对红线）

### Flyway 禁令

- 禁止引入 `flyway-core`/`flyway-mysql`，禁止添加 `spring.flyway.*`，禁止新增 `V*__*.sql`/`U*__*.sql`，禁止编写 `*FlywayIT`/`*FlywayTestBase`。
- schema 变更由 DBA 按审批结果在目标库直接实施，不生成或提交 DDL `.sql` 文件，不依赖自动 migrate 框架。
- 当前目标库 schema 是事实来源；fresh deploy baseline 只能从获准来源导出并按安全流程处理。

### 可执行 SQL 文件

- 新增或修改的可执行 `.sql` 只能包含 `START TRANSACTION`、`COMMIT`、`ROLLBACK` 以及 `INSERT`、`UPDATE`；禁止 `DELETE`、DDL 和其他独立语句。
- 禁止混入独立 `SELECT`、`SHOW`、`DESCRIBE`、`EXPLAIN`、`CHECKSUM`；`SELECT` 只能作为 `INSERT`/`UPDATE` 的组成部分。
- 禁止定义或调用存储过程、函数、触发器、事件，禁止引用 `INFORMATION_SCHEMA`。
- MySQL upsert 禁止使用已弃用的 `VALUES(col)`，使用行别名或显式更新表达式。
- 执行前盘点、执行后验收、幂等检查通过只读命令或测试脚本完成，原始证据外置归档，不得塞入交付 SQL。

### MyBatis-Plus

- 新增数据库访问统一使用 MyBatis-Plus：Mapper `extends BaseMapper<T>`；单条 CRUD 使用 BaseMapper；动态简单条件优先 `LambdaQueryWrapper`/`LambdaUpdateWrapper`。
- XML 只用于 BaseMapper 覆盖不到的自定义 SQL，例如批量写入、跨表 JOIN、聚合或带乐观条件的批更新。
- 新实体使用适配真实表结构的 `@TableName`、`@TableId` 等注解。公共分页与 MyBatis-Plus 配置位于 `common-db`；不要在各业务模块重复创建平行配置。
- 既有纯 MyBatis/XML 可保留，不以“统一”为由无关重写；新功能不得沿用遗留写法。

## Sol 主代理与 Luna 子代理协作规范（绝对红线）

非简单开发任务必须采用 **Sol 主代理分析规划、Luna 子代理执行开发、Sol 主代理最终验收** 的协作方式：

1. **Sol 先分析和制定计划**：主代理必须先阅读本文件、目标模块的 `AGENTS.md` 及相关代码，明确需求、影响范围、风险、验收标准和可执行计划，再开始派遣开发任务。
2. **拆分可验证子任务**：Sol 将计划拆成边界清晰、范围受控、可以独立完成并验证的开发子任务；架构决策、跨模块契约、需求歧义、冲突裁决及高风险操作仍由 Sol 负责。
3. **Luna 负责子任务开发**：清晰、窄范围、低歧义或重复性的编码与测试任务，必须优先派给 `luna_worker`（`gpt-5.6-luna`，`max`）执行。
4. **明确文件所有权**：每个 Luna 子代理必须获得明确的文件或模块所有权，并被告知其并非独自在代码库中工作；不得回退他人改动，发现并行修改时必须主动适配。
5. **谨慎并行写入**：仅当子任务互不依赖且修改文件不重叠时才允许并行派遣多个 Luna；存在共享文件、契约依赖、数据库迁移或集成顺序时必须顺序执行，不得为了并行而强行拆分。
6. **等待并审查结果**：Sol 必须等待所有 Luna 子代理返回，审查实际 diff，确认没有遗漏、越界或无关改动，并按本项目 TDD、前端及数据库验证门禁完成适用的集成验证。
7. **Sol 最终验收**：最终由 Sol 汇总计划完成情况、实际变更、测试结果、未验证项和剩余风险；不得直接转发 Luna 的结论作为最终验收结果。
8. **聚焦提交**：每完成一个可独立验证的小节点，应在适用测试通过后创建聚焦的 Git commit；提交不得夹带用户已有改动或无关文件。

## 前端与数据库验证门禁（绝对红线）

- 任何前端功能、交互、样式或 API 契约改动，除单元测试和构建外，必须使用官方 `playwright-cli` 对真实运行页面验证关键流程；不得用 Vitest、Playwright Test 或 MCP 替代 CLI 浏览器验收。
- 每次 CLI 验收必须归档真实命令、路由/拦截器注册清单、原始 console、原始 request/response 摘要和截图。无 mock 验收要证明未注册 mock route 且请求到达目标服务；开发态 mock 必须逐条列明路由和响应，并标注“仅开发态 mock，非联调”。
- 任何 DDL、DML 或数据库脚本开始前，必须先对现有 `yiti_test` 做只读盘点。覆盖、清空、重建或从 `yiti` 克隆到 `yiti_test` 均属破坏性操作，必须先取得明确确认；“测试验证”或“备份”不构成隐含授权，严禁直接改动 `yiti`。
- 备份只针对获准操作的目标库：保存受控访问的备份文件、表/行数清单和 checksum 清单，并在独立隔离实例完成恢复演练。备份、diff、日志、截图和工单必须脱敏、最小化并限制留存与访问。
- 获准克隆后，`yiti_test` 必须隔离 `SPRING_SESSION`、`PT_LOCK`、Quartz/`sys_job_conf`、消息收发、缓存命名空间、对象存储、外联凭据和回调；禁用调度与外发并使用独立实例标识。只在该隔离环境验证结构、数据、约束、真实查询、幂等复跑、执行前后 diff 和恢复。
- `yiti_test` 验证失败不得进入 `yiti`；验证通过后先报告完整证据，再次取得对 `yiti` 的明确执行确认后方可执行。未确认、隔离不完整或证据缺失均为停止条件。

## 子目录指导

开发前读取目标模块的 `AGENTS.md`：

- [common/AGENTS.md](common/AGENTS.md)
- [auth-permission-center/AGENTS.md](auth-permission-center/AGENTS.md)
- [system-governance-center/AGENTS.md](system-governance-center/AGENTS.md)
- [workflow-center/AGENTS.md](workflow-center/AGENTS.md)
- [portal-content-center/AGENTS.md](portal-content-center/AGENTS.md)
- [customer-marketing-center/AGENTS.md](customer-marketing-center/AGENTS.md)
- [business-application-center/AGENTS.md](business-application-center/AGENTS.md)
- [performance-engine-center/AGENTS.md](performance-engine-center/AGENTS.md)
- [report-analytics-center/AGENTS.md](report-analytics-center/AGENTS.md)
- [red-engine-center/AGENTS.md](red-engine-center/AGENTS.md)
- [soap-gateway-center/AGENTS.md](soap-gateway-center/AGENTS.md)
- [bootstrap/AGENTS.md](bootstrap/AGENTS.md)
- [xanzc_frontend/AGENTS.md](xanzc_frontend/AGENTS.md)
- [docs/AGENTS.md](docs/AGENTS.md)
