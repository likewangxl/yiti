# 手写大屏统一入口实施与验收

## 分行草稿顶部业务分布调整（2026-09-29）

- 需求：顶部按“存款总额、存款业务分布、贷款总额、贷款业务分布”排列，删除存贷款合计；下方不重复存贷款分布，收入结构保留。
- 范围：沿用现有全辖分行草稿开关和业务结构数据，保留已发布、支行及其他模板行为；不改保存配置、API、数据库或连接配置。
- 计划：Luna 先归档失败测试，顺序完成局部组件实现；主代理独立核对 diff，执行相关测试和生产构建，通过官方 playwright-cli 检查实际页面、窄屏、单位切换、点击及缺失数据。
- 验收：原值与占比不变，存款与贷款环仅显示一次，收入保留，业务线点击事件保真；1920/1366/390px 无数值裁切或横向溢出。命令、路由、原始 console、请求/响应摘要及截图归档于 `../runtime/branch-summary-composition-20260929/`；真实服务读取与开发态 mock 分开记录。
- 状态：主代理独立验收通过；四张顶部卡交错排列，下方仅保留收入结构。复用原数据与导航上下文，存贷款总额和构成比例与改动前一致。
- 证据：`red.log` 保存实现前失败，`green.log` 保存执行者结果；主代理 `independent-tests.log` 为 8 文件 74 项通过，响应式修复后 `independent-tests-final.log` 为相关 2 文件 21 项重复回归通过，`independent-build-final.log` 为生产构建成功。`responsive-red.log` 先证明手机 compact 被旧 200px 最小高度覆盖，最终官方 CLI 确认修复。
- 浏览器：真实服务无 mock 读取并对比原总额/占比，验证元/万元/亿元、1920/1366/390px、公司存款与零售贷款跳转；保留页面原有“演示补齐·非业务数据”标识和状态。开发态 mock 单独验证发布态三环保留、缺失显示待接入；全部样本明确标为“仅开发态 mock，非联调”。原始命令、拦截器、console、请求/响应摘要和主代理检查过的截图均归档于证据目录。
- 边界：不作为真实经营数据准确性验收；真实 CLI 会话启动前的未登录 401 与登录平台四条既有缺失路由警告已单列，最终布局、导航及 mock 验收均无新增 browser error。未修改数据库、保存配置或共享连接设置。

## 分行草稿完成率进度圆环（2026-09-29）

- 需求：零售、对公的存款和贷款完成率改为紧凑进度圆环，圆心保留准确百分比，同时保留三维对比。
- 范围：仅调整草稿分组指标卡展示；已发布默认仪表盘、金额口径、数据源、API 和数据库不变。
- 计划：主代理确认当前展示分支与验收标准，Luna 顺序完成失败测试和最小实现；主代理检查实际 diff，独立运行相关测试、构建及官方 playwright-cli 浏览器验收后提交。
- 验收：四项完成率分别取自身数据；弧长限定 0–100，超额与负值文字保真，缺失显示空环和待接入；1920、1366、390px 检查百分比与三维对比无裁切和横向溢出，回归已发布模式。原始 Red/Green、CLI 命令、拦截器、console、请求/响应和截图归档于 `../runtime/branch-completion-rings-20260929/`，浏览器样本标注“仅开发态 mock，非联调”。
- 状态：主代理独立验收通过，四项完成率以紧凑圆环显示；1920px 并排对比，窄卡按容器宽度将三维对比移至圆环下方。超额、零、负数与缺失数据验证通过，已发布默认仪表盘不变。
- 证据：`red.log` 保存实现前失败及零弧可见性回归失败，`green.log` 保存执行者 Green；`independent-test.log` 为主代理 4 文件 37 项通过，`independent-build.log` 为生产构建成功。`browser-result.json`、`commands.jsonl`、`mock-routes.json`、`console.json`、`request-response-summary.json` 归档官方 CLI、实际注册的 page.route 拦截器与 55 次请求摘要；浏览器错误为零。1920/1366/390px、单位切换、边界及已发布截图由主代理目视检查。所有浏览器样本均为“仅开发态 mock，非联调”，真实数据联调未验证。

## 未发布分行页总览与信息密度调整（2026-09-29）

- 需求：顶部新增不分零售/对公的存款、贷款、合计汇总；压缩下方细分金额与收入面板；原较上月比较扩展为较上年、较上月、较上日。
- 范围：服务端确认的全辖分行草稿启用新布局；保留已发布及支行页面的既有布局，通过显式展示参数隔离。展示调整不写数据库、不改保存配置或连接设置。
- 口径：总览仅使用完整、同日、同量纲的余额来源；合计采用存款与贷款相加，并明确标为“存贷款合计”。上年比较上年末，上月比较上月末，上日比较前一自然日。准确历史日期、唯一记录、与主卡相同的当前值和单位均确认后才计算差值；缺失不补零，百分比差值使用百分点。浮点单位转换仅以有限的 IEEE 浮点误差容差判断等价，不借用业务金额或日期不一致的细分对比。
- 分工：主代理制定计划、复核 Red 与实际 diff、独立测试/构建/官方 playwright-cli 页面验收；Luna 顺序实现模型、组件与测试。尺寸、可读性及窄屏裁切通过真实浏览器验收。
- 验收：归档实现前稳定 Red 和后续 Green；验证汇总完整性、三维日期与单位、零/负值、重复/缺失历史、单位切换、草稿/发布/支行隔离，检查截图与交互。证据目录：`../runtime/branch-draft-density-20260929/`。
- 状态：前端实现与主代理独立功能/视觉验收完成；未发布全辖分行页显示三项总览，十张细分卡显示三维对比，已发布和单机构上下文保持原模式。未修改数据库、保存配置、API 或共享连接设置。
- TDD：`01-red.log` 保存实现前 6 项稳定业务失败；后续单位/日期、浮点回退和紧凑完成率的失败与修复记录亦已保存。主代理发现并修复了摘要回退、CSS 覆盖和窄屏汇总拥挤，原始 CLI 失败及最终通过均在 `commands.jsonl`。
- 独立验证：`independent-tests-complete.log` 为 10 文件 107 项通过；`independent-build-complete.log` 为生产构建成功。官方 CLI 记录 53 次请求、完整 mock 清单、request/response 摘要和原始 console，错误为零。元/万元/亿元切换、金额与百分点差值、草稿/已发布隔离、单机构上下文、1920/1366/390px 无新增数值裁切或横向溢出均通过。
- 视觉证据：`before-1920.png`、`after-1920.png`、`after-yuan-1920.png`、`after-1366.png`、`after-mobile.png` 和 `published-regression.png` 已由主代理目视核验。完整数据时顶部细分分组由 329.5px 降至 254.34px（约 23%）；完成率使用数字与细进度条，三维对比保持 11px 完整显示；手机汇总纵向排列。
- 验收边界：浏览器样本全部为“仅开发态 mock，非联调”。历史样本用于覆盖年末/月末/上一日计算，不代表真实数据源具有对应返回能力；真实来源未绑定或缺少准确历史时，相关维度显示暂无数据，不造数。当前后端未运行，真实经营数据联调未验证。
- 全量测试限制：`04-full-green-candidate.log` 含客户名单页面/接口/路由缺失、Windows Sass 绝对路径测试错误，并最终 OOM。主代理核实该客户名单页面在任务开始 HEAD 中不存在，相关 API/路由/普通样式文件没有本次 diff；未将全量日志表述为全仓通过。该记录不扩大本次修复范围。

## 支行复用新版分行布局（2026-09-28）

- 需求：支行经营总览复用新版分行经营总览；中央地图替换为未完成指标横向柱状图；右下机构排名替换为当前支行员工排名。
- 计划：复用现有配置化布局和展示组件，以局部插槽替换中央、右下组件；保留单机构来源与机构切换边界；沿用现有 KPI 结果读取、同分排名及分页逻辑。
- 口径：横向柱状图只包含可判定的未完成指标，以完成率统一量纲，实际、目标及缺口保留各项单位。缺失与零目标单独提示，不补零、不混入已完成项；员工按同一考核方案、同一日期总得分排序。
- 分工：主代理分析、集成与独立验收，Luna 顺序负责前端实现与 TDD。范围限于前端及实施记录，不写数据库、不更改共享连接配置。
- 验收：新增失败测试后实现；定向回归与生产构建；官方 playwright-cli 验证新版布局、横向图完整列表、员工搜索/分页/同分、机构切换、权限失败清空和窄屏。归档真实命令、mock 清单、console、请求/响应摘要和截图。
- 状态：前端实现与独立功能验收完成。`BranchOperatingOverview` 直接复用 `PanoramaDashboard`，旧支行仪表盘保持原有兼容实现。当前后端未运行，真实业务联调未验证。
- 独立测试：`../runtime/branch-overview-layout-20260928/independent-tests-initial.log` 记录 15 文件 127 项通过；最后修复及新增展示配置测试由 `independent-tests-final.log` 记录 3 文件 8 项通过，合计覆盖 16 文件 130 个不同用例。`independent-build-final.log` 记录生产构建成功。
- 官方 CLI：`browser-summary.json` / `commands.jsonl` / `mock-routes.json` / `console.json` / `request-response-summary.json` 归档完整命令、拦截器、响应及原始 console。验证 28 项未完成指标、151 名完整计分员工跨服务分页检索、1/1/3 并列名次、上下页可操作、机构切换、403 清空、单机构展示块过滤及 390px 无横向溢出。两条故意制造的 403 console 错误已单列，其他 page error 为零。
- 视觉验收：`dashboard-desktop.png`、`dashboard-final.png`、`dashboard-mobile.png`、`dashboard-parent.png` 均由主代理检查；桌面和窄屏分页裁切分别先由 CLI 几何断言复现再修复。上述截图和浏览器样本均为“仅开发态 mock，非联调”，不作为真实经营数据或生产联调证据。
- TDD 记录限制：执行者称初始 Red 已运行，但原始输出未归档，主代理无法核验初始顺序；`01-red.log` 为实现过程中的回归失败，不作为初始 Red 证据。后续包装组件和紧凑排名的 Red/Green 日志及 CLI 布局失败/通过记录已保存。不宣称初始 TDD 证据完整。

## 支行经营总览改版（2026-09-28）

- 需求：展示对公/对私存贷款、营业收入、中间业务收入；统计全部已绑定考核指标完成情况；全部未完成指标可筛选、排序、分页查看；增加 KPI 统计及当前支行个人 KPI 排名。
- 口径：不同单位不累加缺口；目标缺失、零目标、实际缺失单列。个人按同一方案、同一日期总得分降序，同分并列；缺失计分不参与完整排名。不同方案和期间不混算。
- 实施：Luna 分别负责纯计算模型、经营统计界面、绩效结果只读机构过滤；主代理负责来源适配、KPI 读取组件、集成与独立验收。已有 PanoramaMap 改动不纳入本次提交。
- 边界：复用已发布屏来源和已有 KPI REST 路径，不写数据库、不修改权限或共享连接配置。未绑定六项明确显示缺失，存款总量不冒充对公/对私明细，FTP 收入不冒充营业收入。
- 验收：先保存 Red，再 Green；验证全量未完成清单、缺失与零值、反向指标、同分排名、分页完整性、权限失败和机构/日期切换竞态。前端定向测试与生产构建、后端无真库定向测试、契约检查和官方 playwright-cli 页面验收。mock 验收明确标记非联调并归档命令、routes、console、request/response 和截图。
- 状态：代码实现及独立验证完成，当前运行后端仍需更新后才能使用真实KPI统计；源屏没有绑定的考核指标及中间业务收入保持缺失，不创建业务数据。
- 独立证据：`../runtime/branch-overview-20260928/`。前端10个文件67项测试、生产构建通过；JDK17后端38项无真库测试通过，新可执行聚合JAR在独立目录打包成功，内嵌performance模块SHA-256与最新模块匹配。
- 官方CLI：开发态mock（非联调）证明28项未完成指标全量分页、151名完整计分员工检索、1/1/3并列排名、机构切换、403清空旧排名与390px无页面横向溢出。原始命令、完整mock清单、console、request/response摘要与桌面/窄屏截图均已归档；两次预期403网络console错误已单列，其余错误为0。
- 真实读取：既有登录会话无mock读取父屏单机构来源，五项经营值可展示、中间业务收入未绑定、考核目标未绑定。旧后端缺少scopeOrgCode时页面拒绝展示个人排名；不将该读取宣称为新版KPI业务联调通过。
- 打包恢复：默认bootstrap JAR被当前Windows Java进程锁定，聚合install在repackage阶段失败；全部依赖模块已刷新，再通过仓库外临时POM将bootstrap打入独立目录，现有服务与共享端口/连接配置未改变。

> 2026-09-10 后续需求：用户确认分行接入现有测试库并标注测试数据，随后要求审计页面全部可见组件。第二阶段只使用已有源数据完成十槽绑定和四槽缺失说明；用户之后明确授权按系统既有存储方式与业务关联生成测试数据，第三阶段据此补齐十四槽。分行目录模式、混合测试数据口径、受控 DML fallback 和验收边界以 [BRANCH_TEST_DATA_PLAN.md](BRANCH_TEST_DATA_PLAN.md) 为准；零售仍保持 DEMO。下文保留前次统一入口的实施与验收记录。

## 目标

统一导航只展示这两天实现的“分行经营总览”和“零售经营总览”，按登录用户权限控制菜单，点击进入对应手写页面。旧设计器发布画布不再作为导航内容。

## 冻结方案

- 后端固定登记 SCR_PROVINCE → branch-overview-v1、SCR_RETAIL_OVERVIEW → retail-overview-v1。
- 关联屏存在且 ACTIVE，并通过既有屏级白名单与机构范围授权才返回。保留 SYS_ADMIN 不绕过、NAMED_GROUP 非空有效范围、LEGACY_CONTEXT 既有兼容语义；基础设施异常整体失败。
- 目录不读取或依赖发布画布。DTO 为 screenCode/screenName/viewLevel/bizLine/template/dataMode，当前两项 dataMode=DEMO。
- `/screens` 卡片进入受保护的 `/screen-pages/:template`，目标页面重新获取目录确认权限，直接渲染 PanoramaDashboard 或 RetailDashboard 与对应演示模型。
- 卡片和页面明确演示数据性质，返回中心；不跳 public Preview/旧设计器/旧发布页，不把演示值伪装为真实数据。
- 用户、模板路由切换或退出废弃迟到请求；未知模板/数据模式与失败均不渲染旧内容。
- 不修改数据库、账号权限、旧发布包或既有 ScreenView/Runtime。

## 分工与验收

Sol 规划和独立复核；一个 Luna 顺序修改后端契约与前端组件/测试；主代理负责文档、集成构建、运行与官方 CLI 验收。

1. TDD 首先断言旧目录不符合两手写页面契约并保存失败证据。
2. 后端覆盖仅两模板、未发布零售可见、停用/缺失排除、真实权限子集和基础设施错误。
3. 前端覆盖导航、受保护路由、两种手写组件、演示提示、403/未知模板与会话/路由竞态。
4. JDK 17 后端定向测试及打包、前端测试及构建通过。
5. 官方 playwright-cli 验证真实登录、两卡、新分行与零售页面、返回中心和未授权直链，保存无 mock 清单、命令、console、请求/响应及截图。
6. 契约检查与独立 diff 复核后聚焦提交。

## 当前状态

修正已完成，验收证据在仓库外 `../runtime/screen-code-entry-*`。

- 主代理独立前端回归：9 个文件、41 项通过；最终徽标样式修正后的 CodeScreenPage 8 项另外通过（与前一组重叠，不累加）。
- Sol 独立后端复核：4 个 catalog 测试类、16 项通过；前端另行独立 6 个文件、17 项通过。
- JDK 17 后端 reactor package、最终前端生产构建通过。
- 真实 API：admin 目录仅含分行/零售两项，两个非管理员账号登录成功后目录 HTTP 403；未修改权限。
- 官方 CLI 从中心分别进入 `/screen-pages/branch-overview-v1` 和 `/screen-pages/retail-overview-v1`，确实渲染对应原始 Dashboard，旧 `.scr-stage` 数量为 0；返回中心与搜索通过。
- 两张目标页最终截图已目视核验，内部重复徽标不可见、顶栏保留演示标识，标题无遮挡。test-yulin 直链进入 no-access，两个新 Dashboard 均未渲染。
- 无 active mock routes；新页面请求只有授权目录与平台认证/通知，没有旧发布渲染或业务数据请求；最终页面 console 为 0 errors / 0 warnings。
- 首次后端更新后同 hash 保留了开发态旧目录，整页刷新后新目录正确加载；交付时刷新页面即可。
- 本次验证范围为新目录与手写页面入口、相关权限和既有 ScreenView 回归，不声称整个仓库全量测试通过。

### 分行全组件后续状态

- 前两阶段已完成 TEST 入口、CODE 运行时、十槽真实接口绑定、四槽缺失说明、授权机构趋势预取和所有可见空态；历史验收证据见 [BRANCH_TEST_DATA_PLAN.md](BRANCH_TEST_DATA_PLAN.md)。
- 第三阶段已按 `TEST_BRANCH_20260910` 创建专用绩效指标，补齐存款基期、对公存款、净增、客户、测试营收、目标和待跟进测试数据。客户/认领/任务通过营销审批链建立，目标通过 KPI/目标计划 API 建立；当前四家客户及待跟进计数为 2、0、0、5，零值为合法结果。
- 标准指标结果导入因当前对象存储不可用而未落库；本批次采用测试库受控 DML fallback，只插入缺失基期行、只更新专用空槽并保留前像/逐值对账，不由 report 代码写上游数据。
- `attention` 绑定增加可选机构号，使全局关注列表仍按 `label`/`count` 展示，并可将每行安全关联到服务端授权目录中的机构；该字段不改变屏级或机构范围授权。
- 第三阶段十四槽已保存发布，接口返回逐值与测试库一致，越组访问继续拒绝；前端 34 个文件 270 项、生产构建和后端聚合包构建通过。官方 Playwright CLI 无 mock 验证已覆盖 17 次刷新查询、4 项机构排名、4 张城市卡和 0 个 console error/warning。最终页面暴露的金额差 `-0` 显示问题已按 TDD 修复并经更新截图确认，榆林目标差正确显示为 `-0.004221` 亿元；第三阶段已完成视觉冻结，测试数据仍不代表生产经营事实。

## 分行大屏数据治理与自动批次（2026-09-11）

### 目标与边界

- 把指标名称、业务说明、单位、小数位、维度、频率、计算方式和测试/正式分类作为可校验契约；未确认的存款、贷款、月均和营收继续明确标为 TEST，不把 RAND 或人工样本提升为正式经营口径。
- 以 `PERF_RUN_TASK` 的专用任务类型记录每个机构组、数据日期和版本的一次完整大屏批次，保存成员快照、必需指标、目标方案、每来源截至时间、覆盖数、缺失明细以及本批不可变输出快照。现有 schema 已能承载，不新增 DDL，也不把 report 变成上游写入方。
- 自动汇总只通过领域 API 读取客户营销数据，通过 performance 自身目标能力读取动态目标，再由 performance 写入本域 `ORG_INDEX_RESULT`。存贷款等源指标仍由既有指标流水线提供；没有可信来源时批次失败或保持测试说明。
- 调度继续使用治理中心 `JobApi`/Quartz，不增加 `@Scheduled`。手工触发与定时触发必须调用同一个批次 Service；测试 profile 继续保持外发隔离。

### 跨模块契约

1. `customer-marketing-center` 提供只读机构经营快照 API：输入机构编码集合和截至日期，输出每机构有效客户数、待跟进任务数和源数据截至时间。聚合、去重和状态解释在客户域内部完成，不向消费者暴露 mapper/entity。
2. `performance-engine-center` 提供批次编排和只读查询 API：解析命名机构组，校验指标定义和单位，读取客户快照、目标值和已落地实际指标，原子更新本域专用槽位，并把批次状态、质量摘要和不可变输出写入 `PERF_RUN_TASK`；查询方可按 latest 或明确 batchId 读取。
3. `report-analytics-center` 的 WIDE_TABLE 最新批次查询按当前版本、授权机构范围和配置的全部必需槽位选择最近完整日期；不得使用全库 `MAX(data_date)`。分行发布源启用批次策略后，通过 performance 公共 API 读取不可变批次。响应增加向后兼容的批次质量元数据，包含 batchId、数据日期、版本、完整性、机构覆盖、每来源截至时间、缺失指标和时效。
4. `xanzc_frontend` 首个批次响应锁定 batchId 并透传本轮其余请求，顶栏区分各来源“数据截至时间”和“本次刷新时间”，在旧批次、混合期间、部分覆盖或无完整批次时显示清晰状态；静态 `sourceAvailability` 不能覆盖运行时质量失败。
5. `system-governance-center` 提供可选 JobKey 允许集合；默认空集合保持正式行为，隔离 profile 只启动、注册和触发分行批次任务，RAM Quartz 与现有外发禁用配置并存。

### TDD 与实施顺序

1. Red：客户域聚合 API 先覆盖机构去重、有效状态、截至日期、零值机构和输入上限；再实现 mapper/service/facade。
2. Red：绩效批次先覆盖单位/维度/测试分类校验、动态目标、除零、成员缺失、幂等重跑、批次终态和失败可追溯；再实现 Service、Quartz 包装和同服务手工入口。
3. Red：report 先固化“全库未来日期、组内缺机构、必需槽位为 null、旧完整批次回退”四种错误场景；再实现完整批次 SQL和质量 DTO。
4. Red：前端先断言数据截至时间与刷新时间分离，以及 STALE/PARTIAL/NO_COMPLETE_BATCH 的展示，再实现页面状态。
5. 更新 performance/report/customer 对外契约、代码示例和资源数据；运行契约检查。跨模块测试前先 `mvn clean install -DskipTests` 刷新 SNAPSHOT。

### 验收标准

- 同一页面本轮 14 个槽位只能使用同一 batchId、版本和机构组成员快照；任何必需机构或必需值缺失都不能被 SUM 忽略后显示为正常合计。客户当前状态与财务历史日期必须分别记录 sourceAsOf，并显示混合期间提醒。
- 客户和待跟进值能由现有 `MARKETING_*` 关系重新汇总；目标修改后重跑下一批即可更新目标与完成率，不再依赖一次性人工复制。
- 批次重复执行不产生重复业务关系，只覆盖该批次日期/version 下的专用指标槽位；失败留下可读任务记录且不被 report 选为完整批次。
- 页面明确显示 TEST、数据截至日期、刷新时间、覆盖机构数和时效状态；当前测试库 14 槽无 mock 浏览器验收保持可用，越组访问继续拒绝。
- JDK 17 目标模块测试、跨模块聚合包、前端定向测试和生产构建通过；官方 `playwright-cli` 归档无 mock route、请求/响应、console 和截图证据；Sol 独立检查实际 diff 与运行结果后才可完成。

### 本次独立验收记录（2026-09-11；Sol 终审通过，有边界）

独立验收已完成，Sol 已批准本次 TEST 测试库演示交付（有边界）。证据集中归档于 [`../runtime/branch-data-governance/`](../runtime/branch-data-governance/)；本节记录实际证据和已知边界，不将交付表述为 PROD 或整个仓库全量测试通过。

- 独立后端与治理证据：[`customer-final-independent.log`](../runtime/branch-data-governance/customer-final-independent.log) 记录 10 项；[`performance-final-independent-v2.log`](../runtime/branch-data-governance/performance-final-independent-v2.log) 记录 23 项；[`metric-assembler-final.log`](../runtime/branch-data-governance/metric-assembler-final.log) 记录 1 项；[`report-final-independent-v2.log`](../runtime/branch-data-governance/report-final-independent-v2.log) 记录 57 项；[`governance-final-independent.log`](../runtime/branch-data-governance/governance-final-independent.log) 记录 29 项单测和 1 项 IT；[`retask-final-independent.log`](../runtime/branch-data-governance/retask-final-independent.log) 记录 8 项。
- 前端独立证据：[`frontend-scoped-final-v4.log`](../runtime/branch-data-governance/frontend-scoped-final-v4.log) 记录 38 个文件、329 项测试；[`frontend-build-final-v4.log`](../runtime/branch-data-governance/frontend-build-final-v4.log) 记录生产构建成功。额外 npm 全量测试存在无关测试失败/OOM，详见 [`frontend-final-independent-v2.log`](../runtime/branch-data-governance/frontend-final-independent-v2.log)，不把它表述为全仓全量通过。
- 后端包证据：[`backend-package-clean-final.log`](../runtime/branch-data-governance/backend-package-clean-final.log) 记录 clean package/install 成功；[`embedded-module-hashes.json`](../runtime/branch-data-governance/embedded-module-hashes.json) 核对五个模块的内嵌哈希一致，避免 bootstrap 复用旧 fat jar。
- 批次与目标回归：[`initial-live.json`](../runtime/branch-data-governance/initial-live.json)、[`target-changed-live.json`](../runtime/branch-data-governance/target-changed-live.json)、[`restored-live.json`](../runtime/branch-data-governance/restored-live.json)、[`final-live.json`](../runtime/branch-data-governance/final-live.json) 均保持 14 槽、4 家机构和 44/44 覆盖；目标按 1220 万 → 1230 万 → 1220 万变化，完成率按 85.922959 → 85.224398 → 85.922959 回归，触发方式为 AUTO，旧批次保持不变。`target-scenario-journal.json` 的 `restored=true`。
- 未来日期失败回退证据：[`failure-fallback-live.json`](../runtime/branch-data-governance/failure-fallback-live.json) 记录未来日期失败留痕，并回退到旧成功批次。
- 官方 CLI 证据：[`browser-acceptance-summary.json`](../runtime/branch-data-governance/browser-acceptance-summary.json) 记录新官方 CLI 会话 35 次请求（17+1+17），全部 HTTP 200/code 0；同一轮请求使用同一 `batchId`，无 routes，`consoleErrors`、`requestFailures`、`browserErrors` 均为空，页面无横向溢出。`dashboard-final.png` 与 [`dashboard-drill.png`](../runtime/branch-data-governance/dashboard-drill.png) 为真实页面截图，已由根代理目视核验。
- 目录额外 CLI 证据：[`browser-directory.log`](../runtime/branch-data-governance/browser-directory.log) 覆盖 4 家机构目录；待定位宝鸡机构仍显示 2 户，即 `0.0002` 万户，并保留已有指标。截图为 [`dashboard-directory.png`](../runtime/branch-data-governance/dashboard-directory.png)。
- 运行状态：[`runtime-manifest.json`](../runtime/branch-data-governance/runtime-manifest.json) 记录最终 PID `49492` 和默认 5 分钟调度。页面继续明确显示金融 `2026-08-30` 的 `STALE`（12 天）、营销 `2026-09-11`，以及 13 项元数据警告；金融历史日期和营销当前状态没有被合并。
- 已知非阻断风险：审计中偶发 `start_time` 比 `end_time` 晚 1 秒，原因登记为 `DATETIME(0)` 小数舍入与数据库 `NOW` 精度差异，已登记为 P2；本轮未修改共享 SQL。
