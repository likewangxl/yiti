# 另一台电脑的大屏测试交接

分支：`codex/screen-metric-binding-20261015`。仓库：`https://github.com/likewangxl/yiti`。

## 1. 拉取与环境

```powershell
git clone --branch codex/screen-metric-binding-20261015 https://github.com/likewangxl/yiti.git
cd yiti
git log -1 --oneline
```

已有 checkout 请先保存自己的改动，再 `git fetch origin`、切换上述分支并 `git pull --ff-only`，不要强制覆盖本地文件。

使用 **JDK 17**、Maven 和符合 `xanzc_frontend/package.json` engines 的 Node/npm。Windows 默认 Java 可能是 11；先检查 `java -version`、`mvn -version`，必要时在当前终端设置 JAVA_HOME/PATH。

## 2. 复用现有数据库（本次推荐测试方式）

上一轮获准测试连接为 `192.168.50.100:3306/yiti`。电脑需要能访问该内网地址，数据库账号密码由授权渠道取得，不存入 Git。该库已实施的指标、数据源、发布包与生成明细可以直接复用，**不要重跑初始化或复制旧 SQL 全库导入**。

后端使用既有 `screen-scope-e2e` profile，并用当前终端环境变量显式覆盖主库和报表只读库；该 profile 文件原默认仍是本机 `yiti_test`，仅选 profile 不足以连接目标库。调度、schema 初始化等限制沿用此 profile。以下是与前次联调一致的本机参数，不会修改仓库配置文件：

```powershell
mvn clean install -DskipTests
$env:SERVER_PORT = '18080'
$env:SPRING_PROFILES_ACTIVE = 'screen-scope-e2e'
$env:YITI_SCREEN_SCOPE_DB_USERNAME = Read-Host '数据库用户名'
$dbPassword = Read-Host '数据库密码' -AsSecureString
$env:YITI_SCREEN_SCOPE_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbPassword).Password
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://192.168.50.100:3306/yiti?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&nullCatalogMeansCurrent=true&sessionVariables=time_zone%3D%27%2B08%3A00%27'
$env:RPT_DATASOURCE_READ_ONLY_URL = $env:SPRING_DATASOURCE_URL
java -jar bootstrap/target/bootstrap-1.0.0-SNAPSHOT.jar --spring.profiles.active=screen-scope-e2e
```

另开终端，在仓库前端目录启动：

```powershell
cd xanzc_frontend
npm ci
$env:VITE_DEV_HOST = '127.0.0.1'
$env:VITE_DEV_PORT = '8092'
$env:VITE_DEV_STRICT_PORT = 'true'
$env:VITE_PROXY_TARGET = 'http://127.0.0.1:18080'
$env:VITE_USE_MOCK = 'false'
npm run dev
```

Vite 仓库默认代理不是上述后端端口，务必设置 `VITE_PROXY_TARGET`。端口被占用时先排查进程，不要让 Vite 悄悄换端口。只有浏览器和 API 真正联通才算服务恢复，不能只看启动日志。

访问 `http://localhost:8092/`，使用有大屏授权的账户登录；若统一认证环境不可达，系统既有普通登录入口为 `http://localhost:8092/#/login?normal`，仍需合法账号密码，不能跳过鉴权。

## 3. 数据库脚本与配置资料

补充：原库不可达时，可使用 [加密恢复资料包](screen-recovery-20261116/README.md) 与 [另一台 Codex 提示词](screen-recovery-20261116/CODEX_PROMPT.md)。其中有配置前态、后续变更配方及不可变批次，但不是最终数据库完整导出；需要单独传输密钥并按说明重建、核对。

以下均已随仓库交付，不包含连接凭据；Python 文件是**纯函数模块**，不是自动导库命令。接口和输入示例见各自测试：

| 文件 | 用途 |
| --- | --- |
| `scripts/formal_metric_sql.py` | 正式金融指标的参数化查询 SQL 定义 |
| `scripts/formal_screen_source_data.py` | 有来源标记的基础明细生成与核验 |
| `scripts/branch_ranking_metrics.py` | 直营机构指标和比例计算 |
| `scripts/branch_ranking_sql.py` | 历史 TEST 指标 SQL 定义生成，当前正式口径不应回退为 TEST 方案 |
| `scripts/screen_daily_test_data.py` | 历史每日 TEST 快照生成/校验，不能替代当前正式来源 |
| `scripts/tests/test_*.py` | 上述模块的输入格式、示例与可重复验证 |
| `docs/reports/formal-screen-sources-20261109.md` | 正式来源接入、日期范围、数据分类和执行边界 |
| `docs/reports/retail-map-targets-20261110.md` | 零售目标方案、指标、数据源与发布配置记录 |
| `docs/reports/baoji-city-operating-20261110.md` | 城市指标与基期修复、实测值和验收范围 |

可在仓库根目录运行纯函数验证（不连接数据库）：

```powershell
python -m unittest discover -s scripts/tests -p 'test_branch_ranking*.py'
python -m unittest discover -s scripts/tests -p 'test_formal*.py'
python -m unittest discover -s scripts/tests -p 'test_screen_daily_test_data.py'
```

**共享库与空库不可混淆。** 本交付不包含完整数据库 dump、真实身份映射或本机私有执行计划，不是空库一键部署包。新建另一套数据库时，还需获准的 schema、机构/权限目录、指标及依赖定义、画布/绑定/发布快照、目标配置和不可变批次；按仓库数据库门禁备份、隔离恢复并验证后另行部署。历史初始化 SQL 不能当作当前库权威结构。本次没有新增 DDL；不要新增 Flyway 或自动建表。

现有数据覆盖 2026-10-11～2026-11-09，并包含必要基期。到另一台电脑测试时，页面可能提示数据陈旧；这些是固定历史样本，不能改日期冒充新业务数据。基础数据的生成来源标记必须保留。

## 4. 验收顺序

1. 登录后检查 Session、菜单和大屏授权；无权限不要改前端绕过。
2. 打开 `/#/screen-pages/branch-overview-v1`、`corporate-overview-v1`、`retail-overview-v1`，确认实际 `/api/screen/data` 请求成功、无 mock。
3. 无经营机构城市保留轮廓，无标签/光圈/悬停或点击弹窗。
4. 分行地图进入宝鸡市，检查城市存贷款、业务构成、完成率、FTP 收入与基期；切换城市和金额单位，确认不串值。
5. 城市支行排名行约 64px；完成率页显示实际/目标/差额，不良率页显示贷款/不良本金；搜索与六个页签切换正常。
6. 对公右侧机构表为三列，左下为规模质量 KPI；零售目标区有存款与个人贷款目标。未接入的结算性存款、中间业务收入等仍应显示缺失。

前次完整验证记录位于 `docs/reports/`，其中本机 `E:/cx-workspace/runtime/...` 证据路径是历史位置，不是另一台电脑的运行依赖。此交接不自动重跑业务计算或更新数据库记录。
