# docs/schema 数据库文档指导

本文件约束 `docs/schema/` 下的历史结构资料、种子快照和 schema 可视化产物，继承 `../AGENTS.md` 与仓库根 `AGENTS.md`。

## 当前定位

- 当前数据库的真实结构以获准目标库的只读盘点为准，不以本目录任一 SQL、Excel、ER 图或 Markdown 为准。
- 现有 `ddl-*.sql`、seed 文件、`migrations/` 和生产基线导出均为历史快照或档案，只用于追溯和理解，不能冒充当前迁移流程、fresh deploy 入口或执行授权。
- 当前 `ddl-customer.sql` 快照记录 16 张相关表：15 张客户营销表（含 `CUSTOMER_MARKET_CUSTOMER`、线索、标签、跨机构营销、转交、认领和触达）以及 1 张仅承接 M98 T-1 同步的存量 `CUST_MASTER`；该数量和用途仍不替代目标库只读核实。
- `yiti-schema.xlsx`、ER Markdown/DOT/图片等生成物反映其输入快照，不代表实时数据库；引用时说明快照属性。
- `docs/exports/` 是另一套历史可视化产物链，和本目录生成脚本互不构成当前 schema 权威。

## 结构变更红线

- 项目已经废弃 Flyway：禁止新增 Flyway 依赖、配置、迁移命名或测试。
- 任何结构变更由 DBA 按审批结果直接在目标库实施；开发侧不得新增、生成或提交 DDL `.sql`，也不得通过修改历史 `ddl-*.sql`/`migrations/` 伪装成当前流程。
- DBA 实施后，先以只读方式核实表、列、索引、约束和关键查询，再同步实体、Mapper、模块 `05-表结构DDL.md` 及必要的结构说明。
- 物理主键策略决定 MyBatis-Plus `@TableId` 类型；`INPUT`、`AUTO` 或外部表逻辑映射必须逐表核实，不能使用统一模板推断。

## 可执行 SQL 白名单

- 新增或修改的可执行 `.sql` 只能包含 `START TRANSACTION`、`COMMIT`、`ROLLBACK` 以及 `INSERT`、`UPDATE`。
- 禁止 DDL、`DELETE`、独立 `SELECT`/`SHOW`/`DESCRIBE`/`EXPLAIN`/`CHECKSUM`、数据库对象定义或调用，以及 `INFORMATION_SCHEMA` 引用。
- `SELECT` 只能作为 `INSERT`/`UPDATE` 的组成部分；MySQL upsert 禁止 `VALUES(col)`，使用行别名或显式表达式。
- 执行前盘点、执行后验收、幂等复跑和 diff 必须使用独立只读命令或测试，原始证据不得混入可执行 SQL。
- 历史 SQL 即使含现行白名单之外的语句也只作为档案保留，不得据此复制、新建或直接重跑。

## 数据库安全门禁

- 对 `yiti_test` 的写入、覆盖、清空或克隆必须先取得明确确认，并先完成只读盘点；测试成功不构成写入 `yiti` 的授权。
- 获准克隆或验证时，隔离 Session、锁、Quartz/调度、消息、缓存、对象存储和外联凭据，并保存受控备份、清单、checksum、恢复演练和前后 diff 证据。
- `yiti_test` 验证失败、隔离不完整或证据缺失时停止；验证通过后仍需再次取得 `yiti` 执行确认。
- 输出、日志、备份和图表不得泄露敏感数据，采用最小化、脱敏和受控留存。

## 文档维护

- 模块 `05-表结构DDL.md` 是数据模型说明，不是实施脚本；只同步已核实的最终结构。
- 初始化数据清单描述业务所需配置，不代表可以执行旧 seed 文件。资源、角色、字典等现状必须查询目标环境。
- 跨模块业务查询通过公开 `*QueryApi`，不能因文档中存在表关系图就直接 JOIN 其他模块私表。
- 修改生成器或可视化产物时，不得顺带改历史 SQL；明确输入快照、生成命令和验证范围。
