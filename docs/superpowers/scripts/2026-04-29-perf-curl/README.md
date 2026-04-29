# performance-engine-center curl 测试脚本（2026-04-29）

> 目标：用 bash + curl 对 `performance-engine-center` 全部 57 个 REST 端点做 happy path / 业务校验 / 鉴权失败矩阵测试。
> 数据库：`yiti`（root/djdev），服务地址：`http://127.0.0.1:8080`。
> 测试账号：`admin / 123456`（角色 R_ADMIN/SYS_ADMIN，跳过 RBAC，最易跑通）。

## 文件清单

| 文件 | 作用 |
|---|---|
| `lib.sh` | 通用库：登录、curl 包装、断言计数、JSON 取值（python 实现，无需 jq）、KV 状态文件 |
| `00-login.sh` | 登录冒烟，把 empId/orgCode 写入 state 供后续脚本复用 |
| `01-seed.sh` | 造测试种子：1×L1 SQL + 1×L2 SUMMARY 指标、1×KPI 方案+2 item、1×目标方案+1 目标值、sys-control init |
| `02-metric.sh` | MetricDefController 13 端点（含 trial-run / execute / DELETE / sampleRows 守护） |
| `03-kpi.sh` | KpiSchemeController 9 端点（含发布 / item CRUD） |
| `04-target.sh` | TargetPlan + TargetValue 7 端点（含 batch upsert） |
| `05-alloc.sh` | AllocRelation 3 + AllocAdjust 4 端点 |
| `06-runtask-syscontrol.sh` | RunTask 2 + SysControl 5 端点 |
| `07-import-export.sh` | Import 5 + Export 5 端点 |
| `08-recalc-targetadjust.sh` | Recalc 1 + TargetAdjust 4 端点 |
| `09-error-paths.sh` | 401/403/404/422 失败路径矩阵 |
| `99-cleanup.sh` | 删除本次种子，让脚本可重跑 |
| `run-all.sh` | 入口，串行跑 00→09 + 99 |
| `diagnose-perm.sh` | 401/403 时直查 PT_USER / PT_RESOURCE / PT_ROLE_RESOURCE / PT_ROLE_BIZ_SCOPE |

## 一键运行

```bash
cd docs/superpowers/scripts/2026-04-29-perf-curl
chmod +x *.sh
./run-all.sh
```

只跑指定步骤（按 stage 编号）：

```bash
./run-all.sh 00 01 02      # 只跑登录+种子+metric
./run-all.sh --skip-cleanup
```

## 关键设计

- **唯一性靠时间戳前缀**：`TS_PREFIX=$(date +%Y%m%d_%H%M%S)`，metricCode/schemeCode/planCode 统一前缀 `*_TST_<TS>_*`，多次重跑不冲突。
- **跨脚本传值**：种子 ID 写到 `/tmp/yiti-perf.state`（KV 文件），后续脚本用 `state_get` 读取。
- **不依赖 jq**：JSON 取值用 python 单行 `json_get`，shell 端 0 依赖。
- **绕过 shell 代理**：所有 curl 加 `--noproxy '*'`，避免本机 HTTP_PROXY 把请求转走。
- **响应留痕**：每次 curl 的响应 JSON 都保存到 `/tmp/yiti-perf-results/`，便于事后排查。
- **断言宽严有度**：HTTP 200 + 业务 code=0 是 ok；高危/校验路径用 HTTP 400 + 业务 code 模式断言；不确定字段（如 owner_emp_id 自动补全行为）只 warn 不 fail。

## 失败排查（按出现频率）

### 鉴权失败 (401 / 403)
**用户已明确：先排查 pt_* 表数据，再怀疑代码。**

```bash
./diagnose-perm.sh admin GET /api/perf/metrics
```

输出包含：
1. `PT_USER` 行（ISENABLED=0 才是启用，反直觉）
2. 用户的角色列表
3. 角色绑定的资源数（admin 的 R_ADMIN 应有 ~84 条）
4. PERF_* 资源在 PT_RESOURCE 的注册情况
5. 该用户在 PERF_CONFIG/QUERY/ADJUST 上的 BizScope
6. URL → PT_RESOURCE 匹配
7. 该用户能否访问该 URL

任一行数为 0 就是数据问题。重置基线种子的脚本：
```bash
mysql -uroot -pdjdev yiti < docs/superpowers/sql/2026-04-10-pt-align-and-test-seed.sql
```
（执行前先 mysqldump 备份到 `docs/superpowers/sql/backup/`）

### 业务校验失败
- `PERF-40001`：metricCode/kpiSchemeId/planId 不存在
- `PERF-40901`：编码重复（重跑时不刷 TS_PREFIX 会触发）
- `PERF-40902`：指标有下游引用，先 disable 引用方再删
- `PERF-42201`：层级引用 / SQL / Groovy 校验失败
- `PERF-50007`：幂等锁冲突（同指标正在执行）

### sys_control 未生效
metric `execute` 与 `recalc` 依赖 `sys_control.is_valid=1`。`yiti` 库 `sys_control` 已有 3 行，但若 EMP scope 没有 valid 版本，execute 会拿不到 dataVersion 而失败。
```sql
SELECT scope_dim, data_version, is_valid, valid_from FROM sys_control WHERE is_valid=1;
```

## 期望验收

- 00-04: 全部 PASS（前置数据 + Metric/KPI/Target 主路径）
- 05-08: 主路径 PASS，部分 adjust 申请的子结构（items/adjustments）若与服务端 DTO 不完全对齐会 warn，不 fail
- 09: 鉴权/校验路径 100% PASS

跑完一次 `run-all.sh`，目标状态：

```
PASS=80+   FAIL=<5   SKIP=<10
```

`FAIL>0` 时第一步：`./diagnose-perm.sh <user> <method> <url>`。
