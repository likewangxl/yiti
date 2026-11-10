# 宝鸡市经营下钻指标接入（2026-11-10）

## 问题与修复

用户指明宝鸡市下钻页大量显示“待接入”。真实 API 核查发现：分行发布包 block 60 已返回完整直营机构明细，但旧 citySummary 仅映射存款、贷款、客户与收入；城市顶部又使用空绑定模板，丢失分行已配置指标引用。按 SUBJECT 查询历史单日时，后端仍读取当前快照行，导致历史空集。

- 城市顶部继承父屏已发布的可见 HEADER 和存贷款构成配置；只使用本城市数据，不回退全辖值。
- 根据授权机构目录和服务端 EXCLUSIVE 标志，投影正式直营指标；重复/缺失机构行、显式日期冲突保持缺失。比例按合计分子/分母计算，不平均各机构比例。
- 保存源列单位，万元展示只换算一次；保留来源名称，例如 FTP 收入。
- 指定同一 batchId 分别查询昨日、上月末、上年末；SUBJECT 单日历史读取不可变 historyRows，LATEST 和多日行为保持原约束。401/403 清空展示，批次不一致不混入基期。
- 无新端点、无数据库结构或业务数据变更。后端沿用当前进程环境、端口及数据库连接重启。

## 当前实际验收

宝鸡市授权范围为 3 家机构。2026-11-09 数据：存款 14,077.48 万元，贷款 11,705.28 万元；零售存款 6,896.42 万元、贷款 4,849.67 万元；对公存款 7,181.06 万元、贷款 6,855.61 万元；FTP 收入 105.48 万元。四项完成率分别为 89.67%、92.48%、101.84%、103.88%。已与 API 明细按相同授权机构独立核算。

结算性存款、中间业务收入及缺少年度目标基期的完成率比较保持缺失，与分行现有来源一致。

证据目录：`E:/cx-workspace/runtime/branch-loading-20261110/`。

- Red-Green：`city-header-*`、`city-summary-*`、`city-integration-*`、`city-unit-*`、`single-subject-*`。
- 主代理独立验证：`frontend-tests.log` 7 文件 93 项回归通过；`city-security-tests.log` 4 项接入测试通过（其中 3 项新增权限/批次边界测试）。`backend-independent.log` ScreenQueryEngineTest 56 项通过，包含历史授权过滤、LATEST 和多日行为。
- `backend-package.log`、`frontend-build.log` 构建通过；`contracts.log` 契约检查通过。构建使用 JDK 17；初次本机默认 JDK 11 错误与运行中 jar 锁定均已修正，最终打包后重启成功。
- `commands.jsonl`、`verify-city.js`、`city-final.txt`：官方 playwright-cli 真实页面验收。宝鸡/西安切换、单位切换、存贷款比较期和四项完成率正常，无 mock route。
- `history-evidence.json`：3 个基期请求各返回 52 行，锁定同批次，记录目标后端匹配 trace 的最小脱敏证据；`city-final.txt` 保存原始 console 与请求响应摘要。
- `baoji-header.png` 已目视确认数据、单位与排版；最终 console 无错误。最初 SSO 502 后使用系统既有普通登录完成认证；重启期间的权限加载失败在服务就绪后刷新恢复，未绕过权限。

本报告为本次运行快照；底层已有生成明细的来源标记保持原样。
