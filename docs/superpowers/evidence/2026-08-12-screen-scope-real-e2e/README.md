# 大屏范围真实运行验收（受控停止证据）

## 结论

**FAIL，已安全停机。** 阶段 A、B 通过；阶段 C 的真实隔离启动达到 ready，但阶段 D 在自动日志观测中发现硬性隔离违规，故立即受控停机。未调用 `/ishealth`，未登录，未启动前端或 Playwright CLI，未执行任何 API 写操作。

启动期三项事实均与隔离 profile 中相应的“关闭”声明冲突：

- `ObsStorageClient` 构造了 OBS 客户端；
- `sidecar-registration-check` 实际请求 `127.0.0.1:8089/isready`，停机时又请求 `/down`；
- `EVAL_ASSIGN_BATCH WHERE status=3` 启动期查询实际发生；按该查询的唯一源码调用点，这是评价导入补偿器扫描。

三项最小脱敏原始日志片段见 `10-d-policy-violations-minimal-redacted.raw.txt`。启动命令、各次退出码及停机原因见 `09-c-startup-command-and-exits.md`。未将具体凭据、Cookie、Session、Authorization、OBS endpoint、bucket 或完整原始应用日志归档。

## 已完成的阶段 A 项目

- 重算了隔离 profile、Vite、HTTP/路由/Screen API、前端锁文件和三份 SQL 的 SHA-256；完整结果见 `01-relevant-file-hashes.raw.txt`。
- 盘点了要求的端口。`18082` 与 `8092` 空闲；既有监听仅记录为未知进程，未复用、未停止，见 `02-port-process-preflight.raw.txt`。
- 静态确认主/RPT 数据源 URL 都精确指向 `127.0.0.1:3306/yiti_test`，且隔离 profile 仅绑定 loopback；官方项目内 `playwright-cli` 可执行，版本为 `0.1.18`，见 `03-profile-and-tool-preflight.raw.txt`。
- 环境变量存在性检查只输出 `present`/`missing`，不输出任何值；结果为全部缺失，见 `04-credential-environment-preflight.raw.txt`。
- 经会话授权的受控子进程环境已再次确认四个必需变量均为 `present`（无值输出），并用该环境成功完成 `DATABASE()`、server UUID、hostname、port 和 `CURRENT_USER()` 的只读确认，见 `05-database-identity-A.raw.txt`。

## 已完成的阶段 B 项目

- 先从 `information_schema.tables` 确认所有纳入基线的实际表名；包含 Flowable repository/runtime job、Quartz、任务配置/日志、Spring Session、锁、审计、文件/关系、评价导入/补偿、screen/config/org-group 和认证/授权配置表，见 `06-b-table-discovery.raw.txt`。
- 只对已确认的表执行精确 `COUNT(*)`；完整 61 表聚合基线见 `07-b-db-baseline.raw.txt`。
- 另采集不含人员明细的登录副作用聚合，以及目标 screen/org-group/resource 编码计数，见 `08-b-login-and-target-aggregate.raw.txt`。

## 阶段 C/D 结果

- C：成功启动至 `screen-scope-e2e` 单一 active profile、Tomcat `127.0.0.1:18082`、Spring `Started`。启动时使用临时 `LOG_HOME`，未改产品文件。
- D：**FAIL**。日志出现上述 OBS 初始化、sidecar 真实 HTTP 探测、评价导入补偿查询；因此停止，不进行匿名 health、API、登录或浏览器阶段。
- 停机后 `18082`、`8092`、`30523`、`11003` 均无监听；唯一仍见的 `8089` 是未知既有监听，未停止或复用。见 `13-d-poststop-ports-and-network.raw.txt`。
- 启动前后 61 张受控表的精确 `COUNT(*)` 逐行一致，所有 delta 为 0；PT_USER 登录副作用聚合和目标 screen/org-group/resource 编码聚合亦一致。见 `11-d-db-poststartup-61-table-snapshot.raw.txt` 和 `12-d-db-61-table-diff.md`。

静态 profile 的关闭项与实际启动行为的矛盾摘录见 `14-static-isolation-settings.raw.txt`。本证据只陈述观察到的结果，不推断属性覆盖/装配问题的具体根因。

## 未执行的阶段

E（登录和只读 API）、F（前端/官方 Playwright CLI）和 G（API 后 diff）均未开始。草稿 screen 未发布，运行时 `#/screen/...` 未纳入结论。

## 恢复条件

后续修复后重新验收时，以下变量仍只能注入受控命令进程，不输出值：

```text
YITI_SCREEN_SCOPE_DB_USERNAME
YITI_SCREEN_SCOPE_DB_PASSWORD
YITI_TEST_USERNAME
YITI_TEST_PASSWORD
```

值不得写入本目录、终端归档、命令行参数、Cookie、Authorization 或截图。恢复时仍须保持 `SERVER_PORT=18082`、`VITE_DEV_PORT=8092` 和本 profile 的 loopback/yiti_test 隔离约束。

## 事实与限制

- 事实：`18080`/`30522` 已由同一未知 Java PID 监听，`8090` 与 `8091` 分别由未知 Node PID 监听；本次没有接触这些进程。
- 事实：启动前 `18082`/`8092` 空闲；后端只在本次隔离启动期间监听 `18082`，已由本次执行者停止。
- 事实：当前数据库身份已经实测为 `yiti_test` / `d3a209c4-42bd-11f1-bb1f-000c299f5629` / `ubuntu` / `3306` / `root@localhost`。
- 事实：启动后 61 表计数和有限登录/目标编码聚合均未改变；这不抵消 OBS/sidecar 真实活动造成的 D 失败。
- 限制：没有健康端点、API、认证、页面、无 mock 浏览器、端到端请求响应或真实运行态页面结论。
- 既有 `2026-08-11-screen-scope-playwright` 是开发态 browser-route mock 证据，不属于本次真实联调结果。

## Round 2：恢复后的最终结论（2026-08-12）

**PASS（仅限用户收窄的大屏只读页面验收），已安全停机。** 本段不改变上方 Round 1 的 **FAIL** 结论：Round 1 的 OBS/sidecar/Eval 启动期违规仍被保留为历史失败证据。Round 2 在重新建立隔离运行态后完成了最小 A/B/D 门禁和官方 `playwright-cli` 的只读 F 阶段；没有发布草稿、保存配置、试跑/探测数据源、运行时大屏、管理写操作或对 `yiti` 的访问。

### Round 2 的先前消息事实与原始证据缺口

上一次被中断的 Round 2 曾通过代理消息报告：A/B 重跑通过、三处 runtime class hash 一致、新 61 表基线已采；D 报告 `/ishealth` 为 200/body `0`、61 表零变化、OBS skip 且无 Sidecar/Eval/Flowable/Quartz/lock/metric 活动；E 报告唯一登录 POST 为 200、12 个白名单 GET 均为 200、最终仅 Session/Attributes 各增加 1。恢复时只按 `screen-scope`、`round2`、`18082` 精确检索 `/tmp`，三次 stdout 均为空，因此这些是**消息事实而非可恢复的原始证据**，不能冒充本目录原始输出。检索记录见 `round2-recovery-00-raw-artifact-recovery.md`。

本次恢复没有为了重造“0 基线”而删除 Session；开始时当前 `SPRING_SESSION=1`、`SPRING_SESSION_ATTRIBUTES=1`，完整 61 表基线见 `round2-recovery-01-port-and-db-baseline.raw.txt`。

### Stale jar 根因与 Green 门

恢复前发现 OBS、Sidecar、Eval 三个目标类与本地 `.m2` runtime jar 的 SHA-256 不一致，属于 stale jar 风险。按授权执行根目录 `mvn clean install -DskipTests` 后，18 个 reactor 模块全部 SUCCESS（退出码 0）；三套 `target/classes`、模块 jar、`.m2` jar hash 一致，且 `javap` 门确认 OBS/Sidecar/Eval 的三项 isolation guard 存在。详细的原始构建摘要、hash 和门结论见 `round2-recovery-02-build-and-runtime-artifact-gates.raw.txt`。

### 受控隔离启动与 D 门

- 预启动确认 `18082`、`8092`、`30523` 无监听/连接；数据库只读身份为 `yiti_test`。证据见 `round2-recovery-01-port-and-db-baseline.raw.txt`。
- 后端仅以 `screen-scope-e2e`、loopback `18082` 和 `/tmp` 临时日志启动；`RptPrimaryDataSource` 明确为 `127.0.0.1:3306/yiti_test`。`GET /ishealth` 返回 200、body `0`。
- 启动期与 health 后的 61 表仍等于当时的当前基线。日志显示 OBS skip、Quartz/Metric startup sync skip；禁止项扫描未发现 Sidecar 注册/探测、Eval compensation、`PT_LOCK` 或 Quartz 活动。原始最小摘录见 `round2-recovery-03-backend-startup-and-d-gate.raw.txt`。

### 官方 CLI 的收窄页面验收

前端显式以 `VITE_USE_MOCK=false`、`VITE_API_BASE=/api`、`VITE_DEV_HOST=127.0.0.1`、`VITE_DEV_PORT=8092`、`VITE_DEV_STRICT_PORT=true`、`VITE_PROXY_TARGET=http://127.0.0.1:18082` 启动。唯一浏览器写请求是进入页面所必需的登录 POST（200）；登录功能本身、自动工作台 GET 和其它模块均不纳入测试结论。

- 初始/最终 `route-list` 均为 `No active routes`。
- `designer`：只读加载成功；范围与查看角色对话框均关闭/取消未提交。页面明确显示设计态不生成假点位，需先配置并授权机构画像。
- `datasources`：列表及其读取依赖均为 GET 200；未使用试跑、探测列、新建、编辑、复制、删除。
- `org-groups` 与 `org-profiles`：只读 GET 200；未新建、编辑、成员/角色管理或保存。
- CLI 请求经 `127.0.0.1:8092/api/...` 获得 200，后端对应 handler 在 `http-nio-127.0.0.1-18082-exec-*` 运行；没有 `[api fallback]`。路由/拦截器/fallback 清单见 `round2-recovery-14-static-route-interceptor-fallback.md`，CLI 原始摘要与控制台记录见 `round2-recovery-04-official-cli-login-and-routing.raw.txt`、`round2-recovery-05-cli-scoped-pages.raw.txt`、`round2-recovery-11-proxy-and-runtime-log-gate.raw.txt`。
- 截图为 `round2-recovery-04-login-before-submit.png` 至 `round2-recovery-10-org-profiles-readonly.png`。控制台各页为 0 errors、13 条既有未注册 legacy sidebar 路由警告；该警告作为 caveat 记录，未出现受测页面/API 错误。

### 最终 DB、停机与敏感日志处置

最终 61 表 diff 仅为本次唯一登录产生 `SPRING_SESSION` 与 `SPRING_SESSION_ATTRIBUTES` 各 `+1`；其他 59 表为 delta 0，包含 screen/org/role/datasource、`PT_LOCK`、Flowable、Quartz、file、Eval 和审计计数。完整 snapshot/diff 见 `round2-recovery-12-final-61-table-snapshot.raw.txt` 和 `round2-recovery-13-final-db-diff.md`。

浏览器、Vite 与本次 Maven/backend 进程均已受控停止；`18082`、`8092`、`30523` 无监听/连接，见 `round2-recovery-15-safe-stop-and-port-release.raw.txt`。停机后发现本次 `/tmp/yiti-screen-scope-round2-Erv2w0/branch-platform.log` 含认证字段；其内容从未复制到 evidence。按明确授权，已只删除该**精确**临时日志文件并验证路径不存在；未删除目录、glob、其它临时文件或任何既有日志。该文件已不可恢复，删除证据见 `round2-recovery-16-sensitive-temp-log-removal.raw.txt`；新增 Round 2 文本证据的值型敏感模式扫描为 exit 1/stdout 空。这揭示应用日志脱敏仍需另行处理，但不扩大本次收窄页面验收范围。

## Round 3：扩展大屏只读交互回归（2026-08-12）

**PASS（仅限本轮指定的大屏只读交互），已安全停机。** 本段不覆盖或改写上方 Round 1 的 FAIL 与 Round 2 的既有结论。Round 3 仅使用项目内官方 playwright-cli，并把登录作为一次前置而非验收对象；未使用 Playwright Test、MCP 浏览器或 Vitest，没有连接或操作 yiti。

### 门禁、无 mock 与真实链路

- 初始与最终 playwright-cli route-list 均为 No active routes，前端显式 VITE_USE_MOCK=false。
- 预启动时 18082、8092、30523 全部空闲；数据库只读身份为 yiti_test。三处隔离 guard 的 target/classes、模块 jar、.m2 jar hash 在一次获准的根 mvn clean install -DskipTests 后一致，构建 exit 0。
- 后端只以 screen-scope-e2e、127.0.0.1:18082 和本轮 /tmp 日志启动；GET /ishealth 为 HTTP 200/body 0。启动和最终日志扫描没有 Sidecar、OBS init、Eval 补偿、Flowable deploy、Quartz/lock 活动或 [api fallback]。
- Vite 仅监听 127.0.0.1:8092，/api target 显式为 127.0.0.1:18082。官方 CLI 的大屏 API 在 8092 均为 200，结合唯一后端监听，证明受控 8092 -> 18082 代理链路。详见 round3-02-runtime-startup-health-and-proxy.raw.txt。

### 扩展交互结果

- 设计器：记录 5 个可见屏；切换零售经营总览（全辖）与对公经营总览（全辖）。两屏的范围均显示全辖、各自命名机构组与查看角色白名单；四个范围/角色对话框均取消，未保存。零售地图因 PT_ORG_PROFILE=0 正确 fail-close；组件属性仍可见西安复合地图和宝鸡、渭南、咸阳、榆林四个卫星配置，未修改。
- 数据源：验证 11 条列表、BIZ_LINE 可见、零售筛选空态和共用复位；打开已发布数据源的冻结语义编辑弹窗后取消。未试跑、探测列、保存、新建、复制确认或删除。
- 命名机构组：两个 seed 组可见并可筛选/查看。应用实际提供的是内联成员/角色面板，而不是独立编辑、成员、角色对话框；两组面板的新增/移除、已选成员、已选角色均为 0，未点击覆盖保存。
- 机构画像：PT_ORG_PROFILE 计数为 0；UI 显示外部机构行但本地属性和坐标为空。筛选、打开一条编辑画像弹窗并取消后复位；未保存。

### 请求、控制台、COUNT 与停机

- 唯一非 GET 是获准的前置登录 POST；所有大屏范围请求均为 GET 200，没有 PUT/POST/PATCH/DELETE 意外出现。
- 所有受测页面和关键弹窗为 0 console errors。13 条既有 legacy sidebar Vue Router warnings 持续记录；另有 Element Plus radio deprecated warnings（地图属性 5、数据源弹窗 9、画像弹窗 2），均为 warning，未扩大本轮修复范围。
- 最终最小目标 COUNT(*) 与初始一致：RPT_SCREEN、RPT_SCREEN_DATASOURCE、RPT_SCREEN_ACCESS_ROLE、RPT_SCREEN_MAP_POINT、PT_ORG_PROFILE、PT_ORG_GROUP、PT_ORG_GROUP_MEMBER、PT_ROLE_ORG_GROUP、PT_LOCK 及 Flowable/Quartz 活动聚合均为 delta 0。
- 已关闭 CLI、Vite、后端；18082、8092、30523 无监听。因本轮应用日志含认证字段，只精确删除本轮 branch-platform.log 并验证已不存在；未删除目录、glob 或其他日志，该文件不可恢复。

Round 3 证据入口：round3-00-scope-and-transcript-policy.md、round3-04-designer-readonly-interactions.raw.txt、round3-05-datasources-readonly-interactions.raw.txt、round3-06-org-groups-readonly-interactions.raw.txt、round3-07-org-profiles-readonly-interactions.raw.txt、round3-08-console-and-request-methods.md、round3-10-final-target-count-diff.md、round3-11-safe-stop-and-port-release.raw.txt 和 round3-13-screenshot-index.md。
