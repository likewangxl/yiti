# Screen-scope：隔离恢复与 SQL 验证实施方案

**状态：仅方案，尚未执行。三份 SQL 的目标身份 guard 已编码；第十轮结构检查已通过 3/3 真实 SQL、mutation 已 fail-close 拒绝累计 28/28 fixture，唯一 runner 的 36/36 纯 mock 契约测试均已通过，且第八轮 Sol 已人工审查 guard 的实际 fail-close 控制流。当前仓库尚未安装 root 受信发布包、systemd unit、签名密钥/公钥 pin、降权 service account、在线 claim service 或 A2 proof；没有实际阶段 B 的签名 manifest、A0/A1/A2 授权与隔离库实证。因此阶段 B 必须 fail-close，验证证据不等同部署通过；阶段 C 的专用隔离 profile 也尚未实施。**

本文规定以下三份手工 SQL 的隔离验证准备、恢复点和执行顺序：

1. docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql
2. docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql
3. docs/superpowers/sql/2026-08-11-screen-scope-map-seed.sql

本文不包含真实密码、令牌、端点、回调地址、业务行明细或人员/客户明细。所有审批均须在受控工单或运维记录中完成；本文本身不是任何阶段的执行授权。

## 1. 不可突破的边界与关键订正

### 1.1 当前实例、源库和生产库

- 当前活跃的 yiti_test 仅允许已批准的只读盘点和获准的 A0 源端备份；不得覆盖、清空、重建、克隆回写或就地净化。
- 不得连接、读取、备份、克隆、执行或改动生产 yiti。本文唯一可能的数据源是当前 yiti_test，生产执行属于阶段 D 的独立后续方案。
- 恢复目标必须是网络、实例身份和运维边界均独立的新 MySQL 实例。目标逻辑库名由 A1/B 的审批 manifest 精确指定，可以与源库同名，也可以不同名；不能只用逻辑库名推断隔离性。

### 1.2 已编码的目标身份 guard：控制流已审查，但只防误投

不得再声称“不同逻辑库名会自动构成授权”或“只靠 SQL guard 即证明审批”。三份 SQL 现已编码同一会话的目标身份 guard，并明确不预设固定逻辑库名；`DATABASE()` 只是四元组中的一个实际值，不能单独证明隔离性或审批。

现有检查器为 `docs/superpowers/scripts/2026-08-12-verify-sql-target-identity-guard.sh` 与其 Python 词法/结构实现；它排除字符串和 `--`/`#`/块注释后，检查身份 `CASE`、hard-stop `IF`、`PREPARE`/`EXECUTE` 与 DDL/DML/`CALL` 顺序，不是早期的纯 `grep` 文本命中检查。它不连接数据库。权威静态证据位于 `docs/superpowers/evidence/2026-08-12-sql-target-identity-guard/` 的 `mutation-green/` 和 `runner-round10-green/`，每份记录含 UTC 起止时间、实际命令、退出码和输入 SHA-256；根目录旧 `RED.raw.txt`/`GREEN.raw.txt` 仅为加固前历史证据，不可作为本轮结论。第八轮 Sol 的人工控制流审查已确认下列路径均在首个 DDL 前将失败条件送入不存在对象的 `PREPARE`/`EXECUTE` 硬停止链：

| SQL | 已审查的 fail-close 路径 |
|---|---|
| auth | 文件中的身份 `CASE` → 同文件 preflight guard 的 `PREPARE`/`EXECUTE` → 首个 DDL。 |
| align | 文件中的身份 `CASE` → 同文件 preflight guard 的 `PREPARE`/`EXECUTE` → 首个 DDL。 |
| seed | 文件中的身份 `CASE` → 同文件 preflight guard 的 `PREPARE`/`EXECUTE` → 首个 DDL。 |

三份 guard 均检查六个会话变量非空、`approved_manifest_sha256` 的 64 位十六进制格式、实际会话身份非空，以及四项实际值的二进制精确匹配（port 以 `@@port` 的十进制文本比较）。身份异常会进入既有 preflight error，再由不存在对象的 `PREPARE`/`EXECUTE` 停止链中断。它们绑定的字段如下：

统一 guard 至少绑定下列不可替代的身份字段：

| 绑定项 | 约束 |
|---|---|
| server_uuid | 必须与批准 manifest 中的新隔离 MySQL 实例 UUID 精确匹配。 |
| hostname | 必须与批准 manifest 记录的实例主机身份精确匹配；不得以 DNS 别名或客户端显示名替代。 |
| port | 必须与批准 manifest 的监听端口精确匹配。 |
| schema | 必须与当前 DATABASE() 及批准 manifest 的目标逻辑库精确匹配；不是硬编码 yiti_test。 |
| change ticket | SQL 内仅验证 `approved_change_ticket` 非空；工单存在、状态和批准人须由签发流程核验并绑定进签名 manifest，runner 再验证该 manifest 的签名及绑定工单标识。 |
| manifest hash | SQL 内仅验证 `approved_manifest_sha256` 非空且为 64 位十六进制文本；签名、不可变性及其内容绑定必须由 SQL 之外的受控 runner 验证。该 manifest 应绑定实例身份、目标 schema、三份 SQL 哈希、预检 checker 工件哈希和允许的净化后恢复点标识。 |

这六变量的 SQL guard **只降低误投到错误实例/端口/schema 的风险，不证明审批已存在**：`change ticket` 在 SQL 内仅为非空，`manifest hash` 在 SQL 内仅为格式合法；任何持有写会话的人若能自行填入变量，仍可伪造“相互一致”的值。因此变量不得由操作者手填、复制或以裸客户端注入。

当前审查快照中，结构检查已通过 3/3 真实 SQL；mutation suite 已对累计 28/28 fixture fail-close 拒绝。该证据只验证本地代码与临时变异文本，仍不等于隔离库实证或生产部署。每次 A2/B 批准前都必须在拟签名的精确工件上重新运行结构检查和 mutation suite，并归档原始命令、起止时间、退出码、checker/fixture/三份 SQL SHA-256；任一变化均须重新审查，不能沿用本快照。

### 1.3 审批变量来源、唯一受控 runner 与第十轮审查快照

实施契约必须同时满足：

1. 验证独立批准人先在受控工单中核验状态、批准人和范围，再签发只读、不可变的签名 manifest。六变量的 SQL guard 只防误投：`change ticket` 在 SQL 中只验非空，`manifest hash` 只验 64 位十六进制，二者都不证明审批。操作者不得改写工单状态、manifest、签名、公钥或自行填充/覆盖六变量；仅由受控 runner 从已批准的签名 manifest 注入。
2. 常规 shell 入口 `docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner.sh` 永久只允许校验：无论 `--execute` 或 `--execute=...` 均以退出码 64 拒绝，绝不启动 MySQL。它不得作为生产执行入口，以免受 shell 启动环境（包括 `BASH_ENV` 和动态加载器环境）影响。
3. 唯一生产执行链必须是 root 所有的 systemd unit 固定调用 `/usr/bin/python3.10 -I /opt/bank/controlled-sql-batch-runner/release/2026-08-12-controlled-sql-root-managed-launcher.py --manifest-id %i`。unit 在解释器前清空环境、无 shell；root launcher 仅为读取 root-only 私钥而运行，随后对 manifest SHA-256、当前 PID、`/proc/self/stat` starttime、目标 service UID、runner/launcher/unit/claim helper 哈希生成短时签名 proof，封入不可重开、同一文件描述符的 memfd（FD 198/199）。launcher 必须先 sealed proof 再 `setgroups`/`setgid`/`setuid` 降权至无登录权限、root 所有空 home 的 `controlled-sql-runner` service account；runner 只在该账户下运行。
4. runner 在连接前必须以同一次打开的文件描述符严格校验签名 manifest、root launcher proof、独立 A2 recovery proof 及各自 schema、签名、SHA-256、有效期、撤销状态和绑定字段。A2 proof 使用不同公钥/pin，必须绑定 manifest SHA-256、claim ID、原始 claim-token SHA-256、目标四元组（`server_uuid`、`server_hostname`、`connect_port`、`schema`）、恢复点引用、状态 `VERIFIED`、恢复点 SHA-256 和短时有效期；任何文件可替换、签名/绑定/时效不符都不得创建客户端。
5. manifest 所引用的批准回执只能经 root 所有、绝对路径的在线原子 claim helper `/usr/local/libexec/bank-controlled-sql-receipt-claim-helper --protocol=v1 --claim-once` 消费。helper 必须一次性在线核验并消费回执/nonce，返回独立签名、短时 claim token；离线、超时、已撤销、已消费、响应无效或 token 校验失败一律 fail-close，绝无本地回执或离线回退。
6. MySQL 客户端必须由独立签名的 release contract 放行：固定 `/usr/bin/mysql` 文件 SHA-256、版本、SBOM、manpage 与 `fixed-defaults-file-tcp-verify-identity` option contract；runner 只散列固定文件，不以 `mysql --version` 或 `--help` 动态推断。凭据只存在于 root 管理的固定 defaults 文件，且其 `[client]` 仅为 `user`、`password`、`ssl-mode=VERIFY_IDENTITY`、`ssl-ca`；固定 `--defaults-file`、`--protocol=TCP`、`--ssl-mode=VERIFY_IDENTITY`、CA，且 host/port/schema 仅来自已验证 manifest。凭据、令牌和端点均不得写入 manifest、CLI、日志或仓库。
7. runner 只可启动一次 **`mysql --batch --skip-reconnect`**，由这一进程建立唯一写连接。其 stdin 必须精确由六个 `SET` 加上已批准 auth → align → seed 原始字节组成，并无条件在同一 stdin 内固定重复两轮；不得使用客户端载入命令、可选轮次开关、裸 `mysql`、交互式客户端、`mysql -e`、GUI、`--force`、吞错包装器、分连接预检/执行或断线后续跑。任何断线、非零退出、guard/预检/哈希/签名失败都立即停止，只能从已验证的 A2 净化后恢复点重新开始。

以下为第十轮**本地审查快照**，不是 B 的批准值，也不声明下列源文件已被部署。完整哈希原始证据位于 `docs/superpowers/evidence/2026-08-12-sql-target-identity-guard/runner-round10-green/08-artifact-hashes.raw.txt`；路径引用的是工件区段而非易漂移行号：

| 工件 | 当前路径 | 当前 SHA-256 |
|---|---|---|
| shell 校验入口 | `docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner.sh` | `9e53b6c5b4b475f10d5fb591d416855504599780d8a9d2e1556b38e13d4dcb6a` |
| 降权 runner | `docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner.py` | `1365dc9f43588b5cb2409e809fb204ad130107c285f17dc4b10e2b514c5fde8a` |
| root launcher | `docs/superpowers/scripts/2026-08-12-controlled-sql-root-managed-launcher.py` | `3b628704322d876704dda503645b6cddd05b5ca049e12abaa6254eb76b535099` |
| root systemd unit 模板 | `docs/superpowers/scripts/2026-08-12-controlled-sql-batch-runner@.service.template` | `d9a3562bd89185a8627a085887fbe8c944de9f7d403a1f30de3f63d906c323b5` |
| runner 纯 mock 测试 | `docs/superpowers/scripts/tests/test_controlled_sql_batch_runner.py` | `4ba10944aaa93a3f7a647aad0c40f8384d19d9cf95742fded8c57b38347469c2` |
| 结构 checker 入口 | `docs/superpowers/scripts/2026-08-12-verify-sql-target-identity-guard.sh` | `73701fdfdf25da4861aaf031c1f8891aca09e6d684583cad68b461e40735c760` |
| 结构 checker 实现 | `docs/superpowers/scripts/2026-08-12-verify-sql-target-identity-guard.py` | `7044a687516b5a587eb546186a13c6b67123b7523ac56a9333b51d0dac8bd362` |
| mutation 测试 | `docs/superpowers/scripts/tests/sql-target-identity-guard/test_mutations.py` | `9cdda5c67bddcd7ca602d70dc71862c4ede8ed25733e3d57aa639f872493783f` |
| mutation fixture | `docs/superpowers/scripts/tests/sql-target-identity-guard/mutation-fixtures.json` | `09b69c382092f5da9dfb050add062ffce7b3a57becfc20ee2e5a512342820ae7` |
| auth SQL | `docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql` | `adc6485f27b7889947c67c58f0aa49a6701e73fb114fbb08155f3b4f004f4ed8` |
| align SQL | `docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql` | `81310dbb0232f23e5374207c93d427512429dad7012ce08ea39d6e4a1dc33349` |
| seed SQL | `docs/superpowers/sql/2026-08-11-screen-scope-map-seed.sql` | `5c1b22ecdd8c74480e0607237d58316a71e762383cab1dead4c7cfe20798d500` |
| B manifest schema | `docs/superpowers/sql/2026-08-12-controlled-sql-batch-manifest.schema.json` | `9cbe4e7bbfecadb834b261e8989d3834e3816ddbf501b9468cc2db170184df42` |
| B manifest 示例（不可执行） | `docs/superpowers/sql/2026-08-12-controlled-sql-batch-manifest.example.json` | `ceffbe0a02d1a8d34930160f4ce8134b20f4d33da99028a24d1ac19c611d146b` |
| receipt claim-token schema | `docs/superpowers/sql/2026-08-12-controlled-sql-receipt-claim-token.schema.json` | `9447dd6d5deb78cdcb2c7dac62c22f5f781e9ba19c5a7510b6b98cef21c3bb16` |
| 独立 A2 proof schema | `docs/superpowers/sql/2026-08-12-controlled-sql-a2-recovery-proof.schema.json` | `02330328a408d48df7c4b3adc341a510afcbf158963321e1ddc928bfe3979506` |
| root launcher proof schema | `docs/superpowers/sql/2026-08-12-controlled-sql-root-launcher-proof.schema.json` | `a00eb826cda7212d100dac09af627ed17ed57765c676a4f6a193d21550acf579` |
| MySQL release contract schema | `docs/superpowers/sql/2026-08-12-controlled-sql-mysql-release-contract.schema.json` | `d497c48b3f892b26315843c407df0c3230b557d74097eae1812b7afecbd23ff8` |
| MySQL release contract 示例 | `docs/superpowers/sql/2026-08-12-controlled-sql-mysql-release-contract.json` | `4f9c567856da362e84873f4d8fd6eddd42b2aae7bd76911ffd3b074eba3bb24c` |
| 外部部署信任边界说明 | `docs/superpowers/sql/controlled-batch-manifest-trust/README.md` | `d9906ab3f129b5e99aff3b5c9784703025bdf110ec064516165188e683d22ca3` |
| 证据索引 | `docs/superpowers/evidence/2026-08-12-sql-target-identity-guard/README.md` | `087834181a32bcda24e9e5317c1feb67ed91201e4190138a515222ac5a34eeae` |

第十轮证据为：`mutation-green/05-round10-real-sql-client-command-grammar.raw.txt` 记录 3/3 真实 SQL 通过，`mutation-green/06-round10-all-28-mutations-rejected.raw.txt` 记录累计 28/28 拒绝；`runner-round10-green/01-runner-unit-tests.raw.txt` 记录 36/36 纯 mock 通过。其测试不连接数据库、不开 socket、不执行 MySQL、不启动 systemd 或网络服务。**A2/B 批准时必须重新计算上述及实际部署工件的哈希、重新运行证据，并由签名 manifest、claim token、A2 proof 和 release contract 冻结；仓库当前哈希绝不可当作实际批准。**

当前仓库没有 `/opt` 下受信发布根、已安装的 root unit、root-only 私钥、签名公钥/pin、`controlled-sql-runner` 账号、在线 claim helper 或独立 A2 proof。直接运行 Python 缺少 launcher sealed proof，常规 shell execute 固定退出 64；因此 B 现在硬性 fail-close。静态/纯 mock 验证只证明代码契约，并不证明这些外部信任边界已部署或可以执行。

因此，阶段 B 仍在实际签名 manifest、受信部署根、在线 claim、独立 A2 proof、A0/A1/A2 授权与隔离库实证均完成前一律延期；已编码 guard、mutation checker 和 runner 都只能作为其后的 fail-close 层，不是执行授权。

### 1.4 全局执行禁令

- 除已签名、已哈希并经批准的唯一 runner 外，禁止裸 `mysql`、交互式 `mysql`、`mysql -e`、GUI 客户端、`mysql --force`、吞错包装器、自动重连、REPLACE 式冲突掩盖、自动迁移框架和 Flyway。
- 禁止并行运行三份 SQL；一个获批 manifest 对应一个受控操作者、一条写会话和一个隔离目标。
- 禁止在命令行、Shell 历史、CI 日志、截图、工单或仓库文件中写入凭据、令牌、真实端点、回调值、对象键或业务明细。
- 所有备份、恢复、传输和恢复点产物均禁止产生临时明文文件。源端、跨实例和受控存储之间必须使用 TLS 或等效的受控加密传输通道；解密数据只可在获准恢复端的受控进程内短暂流式处理，绝不可落盘为明文。
- 三份 SQL 的外置预检、统一 guard 和过程内 SIGNAL 都是 fail-close 条件；任一失败即停止，不得继续下一条语句。

## 2. 已有证据与当前硬停止

已阅读的脱敏只读证据位于 docs/superpowers/evidence/2026-08-11-screen-scope-db-inventory/。它们表明：

- 当前 yiti_test 仍含 SYS_JOB_CONF、Quartz、会话、对象元数据和未验证配置，不能作为已隔离的执行环境。
- SPRING_SESSION、SPRING_SESSION_ATTRIBUTES、FILE_OBJECT、SYS_CONFIG_KV 等对象存在；证据没有读取配置值、会话属性、对象键或凭据，因而不能证明这些数据可安全带入恢复点。
- 前两份脚本的大部分基础对象可被盘点到，但 seed 的前置对象必须先按 auth → align 建立，不能跳过。
- 现有证据以元数据、聚合和脚本哈希为主。后续所有证据仍应最小化、脱敏且不导出行样本。

三份脚本均是手工 SQL，ALTER TABLE、过程创建/删除等 DDL 可能隐式提交。失败后不得假设事务 ROLLBACK 可恢复，必须回到经 A2 验证的净化后恢复点。

## 3. A0、A1、A2：独立授权的备份、恢复、净化与演练

### 3.1 阶段总规则

A0、A1、A2 是三个独立的高风险动作，必须分别取得明确、可撤销、目标精确的书面授权。前一阶段完成或留有证据，不构成下一阶段的授权；每个阶段开始前均核验前序产物哈希和批准范围。

| 阶段 | 目的 | 仅在本阶段内允许的动作 | 明确不包含 |
|---|---|---|---|
| A0 | 从当前 yiti_test 取得最小化、流式加密的源端备份 | 依据 A0 范围 manifest 进行一次受控只读备份并保存密文产物 | 新建实例、恢复、净化、SQL 验证、启动服务、访问 yiti。 |
| A1 | 在新独立实例恢复 A0 密文产物 | 创建获批隔离目标、恢复、只读核验恢复完整性 | 源端再次备份、净化、三份 SQL、启动应用/调度/外联。 |
| A2 | 将 A1 副本变成可用于 B 的净化后恢复点，并演练其可恢复性 | 按获审净化 manifest 执行精确净化、复核、制作加密净化后恢复点、在一次性目标恢复演练 | 使用原始未净化 A0 产物作为 B 的回退点，启动应用或执行三份 SQL。 |

任何授权缺少限定实例、时间窗、责任人、批准 manifest 哈希或明确禁止项时，均停在只读规划状态。

### 3.2 A0：最小化流式加密备份

A0 只针对当前 yiti_test，且仅在其身份经受控运维流程确认后进行。A0 授权必须把源端 `server_uuid`、`hostname`、`port`、`schema` 作为不可替代的四元组写入，不能仅写“yiti_test”或一个逻辑库名。备份范围必须预先固化为逐表 A0 schema/data allow-list manifest，先获批准、再计算 SHA-256；不得使用全库通配、默认全量数据、未审查的表列表或事后补充范围。

A0 批准记录、范围 manifest 与获审 runbook 至少共同绑定下列项目；任一项目为空、不可核验、与实际不符或在窗口内被替换，均不启动备份：

| 绑定项 | A0 的最低要求 |
|---|---|
| 源端身份 | 当前 yiti_test 的 `server_uuid`、`hostname`、`port`、`schema` 四元组，以及受控运维的只读核验记录。 |
| 备份账号 | 只读备份账号的标识、最小权限范围、授权责任人和失效时间；不记录密码、令牌或连接串。 |
| 工具与 runbook | 已批准备份工具名称、精确版本、经审查的参数策略，以及获审 runbook 的 SHA-256。 |
| 逐表范围 | 逐表 schema/data allow-list manifest 名称及 SHA-256；每张表的数据模式、必要性、敏感性和完整复制例外必须可追溯。 |
| 传输与保管 | TLS/等效受控加密通道、KMS/密钥流程、受控存储 ACL、留存期限、销毁责任人和销毁核验方式。 |

执行要求如下：

1. 使用适合 InnoDB 一致性快照的受控备份能力；若存在非事务表、事件、触发器、存储例程或复制设置会影响一致性，则停止并要求 DBA 给出新的专项方案。
2. 源端到备份器、备份器到受控存储均使用 TLS 或组织批准的等效加密通道。备份明文不得写入工作区、临时目录、容器卷、CI 工作区、日志或截图。
3. 密文以流式方式直接进入受控存储；密钥由组织批准的 KMS/密钥流程管理，密钥、明文、对象键和真实路径不得出现在本方案或普通日志中。
4. 产物只包括已批准的逐表 schema/data allow-list，以及不含行明细的表名、结构摘要、行数、密文文件 SHA-256 和受控访问审计。校验清单本身也受控保存。
5. A0 结束时只封存密文源产物；不得创建/连接恢复目标、不得净化、不得运行三份 SQL 或任何 SQL 验证、不得启动服务。

### 3.3 A1：新独立实例恢复

仅在 A0 密文文件、A0 manifest 哈希和密文校验均通过后，才可申请 A1 授权。A1 授权必须精确给出新实例的 server_uuid、hostname、port、网络隔离证明、管理员、目标 schema 和允许的恢复时间窗；这些值也将成为后续 B 执行 manifest 的候选身份，但 A1 通过不等于 B 已获批准。

执行要求如下：

1. 由运维创建或指定一个与当前活跃 yiti_test 不同的 MySQL 实例，并通过实例身份和网络路径证明独立；不得只以不同逻辑库名作证明。
2. 密文备份的转运和恢复连接都使用 TLS/受控加密通道；不得产生临时明文。恢复端的解密仅可在受控流中完成。
3. 恢复完成后不启动应用、浏览器、调度器、消息客户端、对象存储客户端或边车。仅对 A1 目标做只读结构、逐表聚合行数、批准的表/数据范围和密文关联校验。
4. A1 的输出是“未净化恢复副本”的只读证明，不能作为 B 恢复点，也不能自动进入 A2。

### 3.4 A2：按预哈希 manifest 精确净化与恢复演练

仅在 A1 的只读恢复核验通过后，才可申请 A2 授权。A2 授权必须附带已审阅并 SHA-256 固化的“净化与恢复 manifest”；任何临时 SQL、临时顺序、未知表或未写明影响行数的操作都不被允许。

净化与恢复 manifest 至少包含：

| 条目 | 必填内容 |
|---|---|
| schema/data allow-list | 每张表分别列出 schema 是否允许、数据模式、用途/依赖、数据责任人和敏感性类别。数据模式只能是“不带数据”“最小化筛选数据”“合成数据”或“经逐表说明必要性的完整复制”。 |
| 完整复制例外 | 每张完整复制表必须说明业务必要性、无法最小化/合成的原因、批准人、预期行数、保留期限和后续净化方式；没有逐表理由即不复制数据。 |
| 净化 SQL | 每一条 SQL 文件/语句的 SHA-256、精确顺序、目标表/条件、预计影响行数、操作者、前置条件和失败停止规则。不得以通配或“按现场判断”替代。 |
| 复核 | 每个净化步骤的只读复核查询文本 SHA-256、预期聚合结果、前后行数/结构差异和允许差异说明。复核输出不得含行明细或敏感值。 |
| 恢复点 | 净化完成后密文恢复点的哈希、创建时刻、保管 ACL、留存/销毁规则，以及用于阶段 B 的唯一性标识。 |

默认策略是最小数据或合成数据；schema 和数据均逐表 allow-list。未明确列入 allow-list 的对象和数据都不得进入 A2 的净化后恢复点。特别是：

- SYS_CONFIG_KV 及其他未知运行时配置的数据默认不复制；不得读取或记录 value，也不得原样进入 B 恢复点。若后续 C 必需某项配置，只能在独立审批下写入合成、隔离、安全的值，并重新核验。
- SPRING_SESSION、SPRING_SESSION_ATTRIBUTES、PT_LOCK、SYS_JOB_CONF、SYS_JOB_RUN_LOG 和所有 QRTZ_ 运行态数据不得进入 B 恢复点。
- FILE_OBJECT、OBS/遗留 MinIO 元数据、消息端点、缓存命名空间、回调、边车和外联相关配置的数据默认排除；保留 schema 不等于允许保留数据。

A2 的固定顺序为：

1. 在 A1 恢复目标执行已哈希批准的净化 SQL，并逐步比对预计影响行数和复核查询结果。
2. 完成所有隔离复核后，创建密文的“净化后恢复点”，记录其哈希和最小化聚合证明。
3. 将该**净化后恢复点**恢复到另一个一次性、独立的演练目标，重复只读复核，证明恢复后的对象仍满足净化约束。
4. 演练目标在验证后封存或按获批规则销毁；当前 yiti_test 永不参与演练。

若没有第二个一次性目标，必须在同一隔离目标重建后重新执行全部 A2 净化和复核，才可形成新的净化后恢复点。绝不可“从 A0 原始密封备份恢复后直接继续 B”。

## 4. 隔离内容与恢复点的强制复核

以下控制既适用于 A2 净化，也适用于进入 B/C 前的复核。任何未知项按未隔离处理，停止推进。

| 范畴 | B 恢复点必须达到的状态 | 必须保留的最小化证据 |
|---|---|---|
| Session | SPRING_SESSION 和 SPRING_SESSION_ATTRIBUTES 无历史会话；后续 C 另设 cookie 名、作用域和隔离 session 表。 | 两表聚合行数、配置键名和隔离结论；不输出属性值。 |
| 分布式锁 | PT_LOCK 无遗留锁，且后续实例标识只属于隔离环境。 | 清空/排除影响行数及锁状态聚合。 |
| Quartz 与任务 | SYS_JOB_CONF、SYS_JOB_RUN_LOG、QRTZ_ 的运行态数据不进入恢复点；不能以空表替代 C 的 Bean 排除。 | 表级 allow-list、净化行数、无 trigger/heartbeat 的聚合复核。 |
| Spring 调度与启动补偿 | A/B 不启动应用；C 必须排除 @Scheduled、ApplicationReadyEvent/Runner 写入和启动补偿。 | C profile 的源码、测试和 bean 清单，不以配置截图替代。 |
| Flowable | A/B 不启动引擎；C 禁用 schema update 和 async executor，必要时排除相关自动配置。 | 有效配置键、启动测试和无异步执行器证明。 |
| 消息与缓存 | 不保留生产消费者/生产者、共享 Redis 或任何共享缓存 namespace；未知运行时绑定一律禁用。 | allow-list、有效配置来源和 egress 拒绝日志。 |
| 文件/对象存储 | FILE_OBJECT 及 OBS、遗留 MinIO 元数据默认排除；无独立端点、桶、短期凭据和网络策略即禁止文件功能。 | 不含对象键/凭据的类别清单与禁用证明。 |
| 边车、认证、回调 | Sidecar、统一认证、回调、重定向和外联凭据默认不进入恢复点、不装配或禁用。 | Bean/配置来源清单和无成功外联证据。 |
| 网络出口 | C 所在宿主机/容器/网络策略默认拒绝 egress，只放行隔离 MySQL、获准本地前后端通道和必要受控观测端点。DNS、HTTP(S)、消息协议和未知 TCP/UDP 默认拒绝。 | 脱敏策略、规则标识和流量日志摘要。 |

## 5. 阶段 B：同写会话 SQL 验证

### 5.1 独立批准与进入条件

阶段 B 只能在 A2 产生并演练通过的净化后恢复点基础上申请独立批准。A0、A1、A2 的任何授权均不继承到 B。

批准前必须同时满足：

1. 三份 SQL 已按第 1.2 节完成 guard 编码，当前结构 3/3、累计 mutation 28/28 审查快照已通过且 Sol 已人工审查真实 fail-close 控制流；A2/B 批准前仍必须对拟签名工件重新执行并归档静态与 mutation 的原始命令、起止时间、退出码、checker SHA-256 和三份 SQL SHA-256，缺一不可。
2. B 执行 manifest 已冻结、只读并由验证独立批准人签名；其 SHA-256 与签名链由受控 runner 复核，工单已签名批准状态由签发流程在签名之前核验。manifest 必须包含隔离目标 server_uuid、hostname、port、schema、变更工单标识、三份 SQL 哈希、预检 checker 工件哈希、A2 净化后恢复点哈希和批准编号。
3. 目标是由该 manifest 识别的独立实例，而不是当前活跃 yiti_test；逻辑库名是否为 yiti_test 不构成通过条件。
4. 恢复点、目标网络隔离、数据 allow-list 和第 4 节所有项目均已有 A2 证据。
5. 第 1.3 节的唯一 runner、结构 checker、manifest/A2 proof/release contract schema 与 36/36 纯 mock 契约测试快照均已复核；A2/B 批准时必须重新计算并由实际签名 manifest 固定其 SHA-256。实际 root 发布包、unit、密钥/pin、降权 service account、在线 claim helper、独立 A2 proof 及临时写账号授权都必须已由独立运维验收；任一项缺失均不得进入 B。

### 5.2 唯一写会话的执行顺序

1. 只能由 root 所有 systemd unit 固定调用 Python `-I` root launcher；普通 shell 和裸 Python 都不是 execute 入口。launcher 以 root 校验受信发布根并生成封存的 PID/starttime proof 后，降权为固定 service account；任一发布工件、proof、账号或权限不符时不创建数据库连接。
2. 降权 runner 在启动客户端前，以同一次打开的文件描述符复核签名 manifest、签名 release contract、在线一次性 claim token、独立签名 A2 recovery proof、三份 SQL、预检 checker 工件和 A2 恢复点的哈希与绑定。工单签名批准状态及临时写授权由签发/运维流程独立核验；任一条件失败时不创建数据库连接。
3. runner 只能启动一次 **`mysql --batch --skip-reconnect`**，由这一个客户端进程建立唯一目标写连接。stdin 只包含 runner 生成的六变量 `SET` 与 auth → align → seed 的精确批准字节，并固定连续两轮；不得摘取语句、以客户端命令载入脚本、以可选参数改变轮次，或由裸 `mysql`/GUI/第二进程插入语句。
4. 每轮均在同一未断开的连接内保留 guard 与脚本结果，并仅输出最小化的结构、行数和业务键聚合 diff。第二轮不得新增列、索引、资源、角色授权、回填或 seed 变化。
5. 保存两轮的脚本哈希、guard 结果、外置预检摘要、结构/data 聚合 diff、claim/A2 proof/恢复点引用、操作者和时间线；不得保存敏感行内容。

客户端断线、代理切换、事务/会话状态丢失或任一脚本报错时，`--skip-reconnect` 必须导致唯一客户端进程非零终止，runner 不得重连、忽略、转换为成功或续跑。不得尝试临时反向 SQL 或从 A0 原始备份继续；只能重建到 A2 已验证的净化后恢复点，重新执行完整同写会话预检和验证。

## 6. 阶段 C：真实隔离后端与官方 Playwright CLI 联调

A/B 阶段推荐完全不启动应用。**不启动应用只能说明尚未进行联调，绝不能称为“无 mock 联调”。** 若专用隔离 profile 未获批准、未编码、未按 TDD 测试或未通过 Sol 审查，阶段 C 必须延期。

### 6.1 专用隔离 profile 的强制设计门槛

阶段 C 前须另行实现一个专用 bootstrap 隔离 profile；不得直接使用 dev、remerge 或现有 test profile。该 profile 是新的代码/配置交付物，必须先写失败测试，再实现最小配置，最后完成重构和 Sol 审查。C 授权前应提交 profile 源码、测试、有效配置来源和 bean 清单。

该 profile 至少必须做到：

1. 同时禁用项目 Quartz 配置并排除 QuartzAutoConfiguration；只设 spring.quartz.enabled=false 不足以阻止 Spring Boot Quartz 自动装配。
2. 启动测试必须证明 ApplicationContext 中没有任何 Scheduler bean；若存在 Scheduler，JobService 可能注册 ACTIVE 任务，C 不得启动。
3. 显式关闭 Flowable database schema update 和 async executor；若属性不足以证明未装配/未启动，则排除相应 Flowable 自动配置。不得允许默认 schema update 或异步执行器。
4. 在组件装配层排除 @EnableScheduling、所有 @Scheduled 入口、ApplicationReadyEvent/CommandLineRunner/ApplicationRunner 的写入路径、启动补偿器（包括 EvalImportCompensation）和其他自动写入 Bean。
5. 不装配 SidecarRegistrationChecker、SOAP Netty、消息消费者/生产者、外部认证客户端、回调客户端和对象存储客户端；仅把 endpoint 改为无效地址不合格。
6. 所有读写及报表只读数据源都只能指向 A2/B 的隔离实例；Session、PT_LOCK、cookie、缓存命名空间、对象存储和实例标识均为隔离专属。
7. 在宿主机/容器/网络层施加默认拒绝 egress，并以有效规则和流量摘要证明没有成功外联。

### 6.2 C 的验收与停止条件

只有真实隔离后端启动且以上门槛均有证据时，前端才可使用官方 playwright-cli 执行关键流程。前端不得注册 mock route；验收材料至少包括：

- 脱敏后的真实 CLI 命令；
- 前端路由、请求拦截器和 mock route 的完整清单，明确证明没有 mock route；
- 原始 console 输出、原始 request/response 摘要、请求实际到达隔离后端的证明和截图；
- 运行前后隔离库的最小化聚合 diff；
- Scheduler 缺失、Flowable 禁用、调度/补偿/边车/消息/对象存储/回调未装配，以及 egress deny 的原始受控证据。

发现 mock、Scheduler bean、Flowable schema update/async executor、任何未批准自动写入 Bean、对象存储/边车/回调/消息外联成功、请求未到隔离后端或敏感信息泄露时，C 立即停止。若 C 产生了 B 范围外写入，只能从 A2 已验证的净化后恢复点重建，不能回到 A0 原始备份。

## 7. 阶段 D：生产 yiti（不属于本文授权）

阶段 D 仅可在 A0、A1、A2、B、C 均通过且完整证据已提交后，由用户另行明确授权。该授权须独立定义生产源/目标、备份、恢复点、窗口、批准人和只读盘点。收到前，本文不得触及 yiti，也不把任何测试结果视为生产许可。

## 8. 精确停止条件、恢复与证据

| 阶段 | 停止条件 | 处置 | 必须归档的受控证据 |
|---|---|---|---|
| A0 | 未获 A0 授权；源四元组、只读账号标识、工具版本、runbook hash、逐表 allow-list manifest hash、加密/TLS/KMS/ACL/留存/销毁任一不明确；要求落地明文。 | 不触碰源，不生成或使用备份。 | A0 批准号、源四元组摘要、只读账号标识、工具版本、runbook/范围 manifest 哈希、密文 SHA-256、ACL/留存/销毁摘要。 |
| A1 | A0 产物或哈希不符；不能证明新实例独立；目标身份/网络/目标 schema 不明确；恢复需临时明文。 | 封存已产生的隔离目标；不得净化或执行 SQL。 | A1 批准号、目标身份摘要、恢复核验、结构/行数聚合和传输加密证明。 |
| A2 | allow-list、净化 SQL、顺序、预计影响行数或复核查询未哈希批准；未知配置将进入 B 恢复点；恢复演练失败。 | 停止，封存目标。需要重做时先按 A1/A2 授权恢复，再重新净化；不进入 B。 | A2 批准号、manifest 哈希、逐步影响行数、复核结果、净化后恢复点哈希、一次性目标演练结果。 |
| B | 当前审查快照与拟签名工件不一致，或未重新运行 mutation/runner 验证；未部署受信 root release、unit、key/pin、service account、在线 claim helper 或独立 A2 proof；签名 manifest/工单状态/身份/哈希不符；预检 violation；断线、换连接或非零退出；脚本报错、并发冲突或非预期 diff。 | 立即停止，不做临时逆转；只从 A2 已验证的净化后恢复点重建并重跑完整预检。 | B 批准号、部署验收摘要、mutation 结果、签名 runner/launcher/unit/claim helper/release contract 路径与哈希、manifest/claim/A2 proof 验证摘要、同写会话 guard 摘要、脚本/预检哈希、两轮输出、结构与聚合 diff、恢复点引用。 |
| C | profile 未批准/未实现/未测试/未过 Sol 审查；有 mock；Scheduler 或 Flowable 异步存在；任何自动写入或外联成功。 | 停止隔离进程、撤销短期凭据；必要时只从 A2 净化后恢复点重建。 | profile 代码/测试/审查结论、CLI 证据、bean 清单、egress 日志、前后聚合 diff。 |
| D | 未取得新的生产书面确认，或前序证据不完整/失败。 | 本方案到此停止。 | 由独立生产方案定义。 |

## 9. 分阶段待确认问题

下列问题按阶段划分。回答任一组只支持该阶段，绝不构成后续阶段、生产 yiti 或服务启动的模糊授权。

### A0：仅支撑最小化流式加密备份

1. 谁在何一受控工单中授权对当前 yiti_test 的指定 `server_uuid`、`hostname`、`port`、`schema` 四元组做一次只读、最小化、流式加密备份？获权操作者、只读账号标识和时间窗是什么？
2. A0 范围 manifest 的逐表 schema/data allow-list、默认最小/合成数据规则、任何完整复制的逐表必要性说明及其 SHA-256 是什么？已审 runbook SHA-256、备份工具名称/精确版本和获准参数策略是什么？
3. 已批准的 TLS/受控加密通道、KMS/密钥流程、受控存储、最小 ACL、留存期限、销毁责任人和销毁核验方式分别是什么？

### A1：仅支撑新独立实例恢复

1. 新隔离 MySQL 实例的 server_uuid、hostname、port、网络边界、管理员和目标 schema 是什么，如何证明它不是当前活跃 yiti_test？
2. 哪个 A0 密文产物及哈希可被恢复，恢复端如何保持全程加密且不产生临时明文？
3. A1 只读恢复核验的表级聚合/结构指纹口径、批准人和受控证据位置是什么？

### A2：仅支撑精确净化与恢复演练

1. 净化与恢复 manifest 中每张表的数据模式、每条净化 SQL/顺序/预计影响行数/复核查询及其 SHA-256 是什么？
2. 哪个一次性独立目标用于恢复演练？若没有，谁批准“重建后重新净化”的替代路径？
3. 谁批准净化后恢复点的加密、ACL、留存、销毁和作为 B 唯一回退点的使用？

### B：仅支撑 SQL 验证

1. 三份 SQL 的同写会话 guard、当前结构 3/3、累计 mutation 28/28、runner 纯 mock 36/36 与 Sol 控制流审查已归档；拟签名工件重跑后的变异清单、实际退出码/时间/哈希和通过结论是什么？
2. 哪份只读签名 manifest 绑定已核验工单标识、四元组、六变量、三份脚本/预检 checker/A2 恢复点哈希？谁是验证独立批准人，在线 claim 如何证明操作者未自行填充变量？
3. 第 1.3 节当前 runner/launcher/unit/release contract 路径与哈希快照和拟批准 manifest 是否精确一致？实际 root 发布根、key/pin、降权 service account、在线 claim helper 与独立 A2 proof 是否已由独立运维验收？谁独立批准以 A2 净化后恢复点为基础的固定两轮执行？

### C：仅支撑真实隔离后端联调

1. 专用 bootstrap 隔离 profile 的源码、TDD 证据和 Sol 审查是否已通过，且是否证明排除 QuartzAutoConfiguration、无 Scheduler bean、关闭 Flowable schema update/async executor、排除调度/补偿/Sidecar/SOAP/消息/对象存储/回调？
2. 谁批准隔离数据源、Session/PT_LOCK/cookie/缓存/对象存储替代方案、egress deny 规则和短期凭据？
3. 测试账号、浏览器数据处理、官方 playwright-cli 验收范围和脱敏证据保管方式是什么？

## 10. A0 最小授权模板（供用户/运维逐字确认）

以下模板只可填写后作为 A0 授权，不能被解释为 A1、A2、B、C、D 或生产授权。方括号中的每项均为必须由批准人填写并独立核验的受控标识；不得在本方案、普通日志或聊天中填入密码、令牌、真实端点或业务明细：

> 我授权【获权操作者/团队】在【开始–结束时间窗】内，仅对当前 yiti_test 的已核验源端四元组【server_uuid】【hostname】【port】【schema】执行**一次**最小化、只读、流式加密备份。仅可使用只读备份账号标识【账号标识】及其获准最小权限；不得使用管理员、应用写账号或共享凭据。
>
> 备份范围严格限于【逐表 schema/data allow-list manifest 名称及 SHA-256】；逐表数据模式、最小化/合成数据规则和所有完整复制例外均以该 manifest 为准。仅可使用【备份工具名称】【精确版本】【获准参数策略标识】，并严格遵循【获审 runbook 名称及 SHA-256】。不得使用全库通配、未审查表列表或临时扩展范围。
>
> 源端至备份器、备份器至受控存储仅使用【TLS/受控加密通道标识】；密文仅写入【受控存储标识】，密钥仅依【KMS/密钥流程标识】管理，访问仅按【ACL 标识】授予，并按【留存期限】【销毁责任人】【销毁核验方式】处理。不得产生临时明文文件或在普通日志中记录敏感内容。
>
> 本授权**仅请求 A0**，明确**不包含 A1 及以后任何阶段**：不包含新建/指定实例、恢复或只读恢复核验（A1），净化、净化后恢复点或演练（A2），任何 SQL 脚本或 SQL 验证（B，包括三份 screen-scope SQL、外置预检、DDL、DML），应用/服务/容器/进程启动或网络联调（C），以及生产 yiti 的任何连接、读取、备份或改动（D）。本 A0 模板也**不授权**安装或配置 B 的 root 发布包、systemd unit、签名密钥/公钥 pin、降权 service account、在线 claim helper、A2 proof 或 MySQL release contract。备份工具为取得一致快照所必需的受控只读协议访问，不得扩展为 SQL 验证、部署或其他操作授权。

## 11. 本次方案编制的非执行声明

本次仅基于本地文件和已有脱敏证据修订方案。未连接数据库、未执行 SQL、未创建备份、未恢复/克隆实例、未启动应用/服务/容器/进程、未进行网络访问，也未改动当前 yiti_test 或生产 yiti。
