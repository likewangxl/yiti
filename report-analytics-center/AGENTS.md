# report-analytics-center 开发指导

本文件是报表分析中心的模块级开发权威，与仓库根目录 `AGENTS.md` 同时适用；根文件的 TDD、鉴权、数据范围、SQL、MyBatis-Plus、日志和测试门禁不在此重复。发生冲突时以根文件为准。

## 模块职责与“只读”边界

- 基础包：`com.bank.branch.platform.report`
- Maven 坐标：`com.bank.branch.platform:report-analytics-center`
- 负责动态查询、保存查询、仪表盘、汇总报表、导出、自由报表、数据湖/AMAS 查询、数据范围选择器，以及 screen 大屏配置、设计和运行时渲染。
- “只读模块”是指**不得写入上游业务模块或外部系统的数据**，不是指模块内禁止一切写操作。
- 允许写入本模块自有状态，例如保存查询、导出任务、SQL 探查历史/导出任务、自由报表批次/行、大屏、画布块、数据源、访问角色和发布日志；写入必须走本模块 Service 事务、鉴权和审计。
- 客户、绩效、组织治理、AMAS、数据湖等上游/外部数据只能读取，不得借报表接口修改、补偿或回写。

## 依赖与边界

- 依赖 `auth-permission-center`、`system-governance-center`、`customer-marketing-center`、`performance-engine-center` 及 `common-*`，只通过其公开 `*Api`/`*QueryApi` 聚合。
- 本模块禁止被业务模块依赖；业务模块需要数据时直接调用数据所有者的公开 API，不经 report 中转。
- 当前 `api/` 仅有 `package-info.java`，不暴露 `*Api`/`*QueryApi`；`RptModuleStructureArchTest` 守护此边界。若架构决策发生变化，必须先更新依赖方向和架构测试。
- `AMAS_*`、`amas_dt_import_*`、`DATALAKE_XAN_*`、`sys_notice` 属外部系统或同步只读表，可由本模块 Mapper 直接读取；其 schema 不归本模块所有，禁止在本模块实施结构或数据写入。
- `PERF_ALLOC_ADJUST_*` 由 `performance-engine-center` 持有并写入；report 对这些表的直接读取只是存量跨模块例外和待收口技术债，不得在新代码中仿照。后续应由数据所有者提供公开 `*QueryApi`，再迁移现有读取路径。
- 新数据库访问使用 MyBatis-Plus。实体 `@TableId` 必须反映物理表实际策略；本模块同时有 `INPUT`、`AUTO`，且部分外部表无标准物理主键，不能机械套用自增或把逻辑映射列误称为唯一主键。

## 鉴权、数据范围与高风险查询

- Controller 的 HTTP 处理方法必须标注 `@BizAuth`，且本模块统一使用 `BizType.REPORT`；资源同时登记 `PT_RESOURCE`。
- 查询、详情、下载和导出必须复用同一后端数据范围，范围选择器只负责提供候选范围，不能替代 Service/Mapper 的最终过滤。
- Controller 方法签名和局部变量不得暴露 Entity，使用 DTO/VO；对应 ArchUnit 测试是强制门禁。
- SQL 探查属于高风险运维能力：只允许专用科技角色和独立资源，要求理由，并同时记录本模块业务历史与治理审计。不得放宽为普通报表角色或复用普通查询权限。
- 大屏草稿、发布、运行时访问和角色白名单分别校验；管理员身份不能替代已配置的屏幕访问范围，读取授权失败时保持 fail-close。

## 自有写事务与状态规则

### 保存查询与导出

- 保存查询归当前用户所有，更新必须带 version 做乐观锁；删除和读取均需校验所有者/授权范围。
- 导出任务状态由创建、执行、成功/失败路径显式推进；文件统一经治理中心 `FileApi` 上传和下载，不直接操作对象存储。
- 通用 `RptExportService` 当前仍可能在创建任务后内联执行，不能仅凭存在任务表就宣称是真异步；需要改异步时必须补线程池、任务可恢复性和失败状态测试。

### 大屏画布

- 画布保存使用 `canvas_version` 条件更新并自增，影响行数为 0 表示乐观锁冲突；不得用无条件 `updateById` 绕过。
- 草稿与已发布包是独立状态：编辑只改草稿，发布时校验结构/数据源并生成不可被后续草稿编辑影响的发布快照，同时写发布审计。
- 已发布包引用的数据源允许在原地编辑查询语义，但保存前仍须重新通过引用屏的业务条线/NAMED_GROUP 安全校验，并写入完整引用清单审计；草稿、当前发布或归档任一引用，以及引用证据无法可信解析时，删除均按 fail-close 处理。
- `ScreenKpiSchemeMapper` 必须保持独立类名，不能与 performance 的 `PerfKpiSchemeMapper` 合并，否则 bootstrap 聚合启动时默认 Mapper Bean 名冲突。

### 自由报表

- 同一操作人的同名重新导入会在事务内替换旧批次记录。
- 不删除旧对象存储文件：MD5 去重可能让新旧批次共享文件对象，删除会破坏仍在使用的文件；孤儿文件清理由独立治理策略处理。

## 运行时基础设施约束

- `ReportCacheConfig#rptCacheManager` 是 `@Primary` 的平台共享 CacheManager；调整 TTL、容量、动态 cache 名或主 Bean 会影响其他模块，不能按 report 私有配置处理。
- 大屏查询必须使用受控模板和参数绑定，遵守 SQL 安全校验；不得允许配置直接拼接任意 SQL、表名或排序表达式。
- 大屏运行时与设计器的前端契约位于 `xanzc_frontend`；涉及其交互或样式时还须遵守前端模块 AGENTS 及根目录 playwright-cli 验收门禁。

## 契约与实现入口

- REST Controller：`src/main/java/com/bank/branch/platform/report/controller/`
- 报表实现：`service/`、`facade/`；大屏：`service/screen/`；实体与映射：`entity/`、`mapper/`
- 接口文档：`docs/modules/report-analytics-center/03-接口设计与报文.md`、`04-对外API契约.md`
- 大屏指南：`docs/modules/report-analytics-center/10-大屏设计器操作指南.md`
- 上游/外部表与自有表均以当前数据库、实体和 Mapper 映射共同核实；旧文档或历史脚本不能单独作为当前结构真相。

## 测试入口

- 从仓库根目录运行 `mvn -pl report-analytics-center -am test`；包含 `*IT` 时运行 `mvn -pl report-analytics-center -am verify`。
- 测试数据使用稳定且可清理的 `TEST_RPT_*` 前缀；并发用例使用独立前缀，不依赖其他模块或历史测试遗留数据。
- `src/test/java/com/bank/branch/platform/report/arch/` 守护 BizAuth、模块不暴露 API、Controller 不泄漏 Entity 和 facade 测试不得保留占位实现。
- 大屏数据模板、安全校验和 schema 变更优先扩展 `support/ScreenSqlTemplateTest`、`ScreenConfigSchemaTest`、`SqlSafeValidatorTest` 等对应测试。
- 跨模块查询契约变更后按根目录 stale SNAPSHOT 指引刷新依赖，再运行 report 及 bootstrap 相关测试。
