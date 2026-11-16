# 大屏配置与批次离线恢复资料包

这是从原电脑留存记录整理的**加密恢复资料**，不是已验证的最终数据库导出，也不是自动导库程序。原库当前无法连接，无法确认 2026-11-10 后是否又有编辑。禁止将 `configuration-before.private.json.gz` 直接当作最终状态覆盖目标库。

## 内容与可信边界

- `daily-screen-data-20261109/formal/configuration-before.private.json.gz`：正式来源改造前的屏定义、画布草稿、发布快照、区块绑定、数据源、指标定义/依赖、目标方案、既有任务等配置表记录及列信息；不含账号表、密码或建表 SQL。
- `definition-plan.private.json`：正式指标变更计划；`binding-plan.json`：来源、旧新指标和列名映射。
- `formal-snapshot.private.json`：已验收正式不可变批次，含当前 52 家机构、32 个日期的历史覆盖及 metricContracts；生成来源分类保留。
- `calculated.private.json.gz`、`source-rows.private.json.gz`、`source-plan.private.json`：已计算员工/机构指标、目标记录、生成基础明细与映射资料。
- `publish-*.js`、`labels-*.js`、`complete-display.js`、`baseline-comparisons.js`、`composition-display-unit.js`：后续发布、显示字段、比较期和单位调整的原始操作记录。
- `retail-goals-20261110/`：零售目标的前态盘点、变更计划、执行与发布配套资料。
- `observed-final/`：最后一次真实浏览器读取到的分行 display 组件/指标引用与城市数据源契约，供重建后比对；不是完整最终画布。

`manifest.json` 记录每个成员文件的长度与 SHA-256，以及整个密文的 SHA-256。包内有相同成员清单。**私有数据只有密文进入公开 GitHub；密钥不在仓库中。**

## 解密

先从原电脑单独复制 `key.json` 到另一台电脑的受控目录，不要放入 Git、公开聊天或日志。安装 Python 的 cryptography，然后在仓库根目录运行：

```powershell
python -m pip install cryptography
python scripts/open_screen_recovery_bundle.py --bundle docs/deployment/screen-recovery-20261116/recovery.bundle.enc --key-file C:/secure/key.json --output C:/secure/yiti-screen-recovery
```

`--output` 必须尚不存在。工具只解密和解压，不连接数据库、不执行包内脚本。解密文件也不得提交到 Git。

## 恢复顺序

1. 核对另一台电脑实际数据库、主数据源/报表只读库、分支与 API 响应；“布局相同但待接入”不等于数据已同步。
2. 阅读目标环境 `AGENTS.md`，只读盘点目标 schema、屏配置、发布状态、指标依赖、机构组/角色权限和已有批次。保存受控备份与 checksum，并按规则进行隔离恢复验证。
3. 把配置前态与后续变更分开。以 `formal` 正式指标计划为主，将旧 TEST 列名/指标映射到正式字段；结合发布/显示/比较期脚本及 `observed-final` 重建**候选最终配置**。旧的独立测试屏停用状态应保留，不回退为当前入口。
4. 查清目标环境机构编码、机构层级与授权组成员；不可硬套原主键或授予 SYS_ADMIN。映射不明确先列缺项。画布 blockId、dsId、bindSnapshots、指标代码/槽位、方案/目标、批次 groupCode/成员必须相互一致。
5. 生成可审查的配置 diff、数据清单与导入计划。已有有效配置不覆盖，已有完整批次不重复生成；有未发布草稿必须保留。不要把旧 `canvasVersion` 和发布时间直接当作新环境并发版本。
6. 验证指标定义/依赖后导入必要数据及不可变批次；保持生成来源和历史日期，不改日期冒充实时业务数据。优先使用既有管理/发布 API；必要 DML 遵守仓库 SQL 白名单。无 DDL/Flyway，不用临时表，不复制 Session/锁/任务调度状态。
7. 验证通过并取得目标环境所需执行授权后应用。通过官方 playwright-cli 验收已登录真实页面、分行和宝鸡下钻、目标与比较期、金额单位、跨城市隔离，并核对 `/api/screen/data` 请求确实到达目标后端。

## 不可直接运行的历史脚本

包内 `apply-*.py`、`publish-*.js` 等是原操作的**参考配方**，带原机器绝对路径、原数据库保护条件和当时的 expectedVersion，不能照抄执行。有些依赖如原 `before.private.json.gz`、原备份 checksum 清单和早期 ranking plan 未按原路径打包；本包只保留了经过筛选的配置前态。必须改为新环境明确的输入和新生成的审查计划，不能移除并发/权限保护来“让脚本通过”。

## 尚缺且必须在目标环境核对的内容

- 所有后续编辑完成后的完整最终草稿/发布包；目前只能根据前态、变更记录与观测结果重建，不能声称逐字节等同原库。
- 目标库真实 schema、机构/人员/授权目录与最新变动；包不是全库备份，不包含登录凭据。
- 在目标库执行、幂等复跑与隔离恢复演练的证据；本机仅验证解密、成员完整性和工具测试。

若以上缺项使可靠重建无法完成，应明确列出缺少的对象及原因，保留已完成步骤，不伪造配置或业务值。结算性存款、中间业务收入等原来源未接入项保持缺失。
