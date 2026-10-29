# 全部大屏管理工作台核对报告（2026-10-29）

本报告为本轮改造的时间点验收记录。当前真实目录共六个大屏，82个主配置入口，全部采用可点击缩略图与右侧配置区。保留各运行页面实际排列、标题和来源，不将旧坐标画布转换为CODE模板。

## 范围与结果

|屏幕|配置入口|运行结构|
|---|---:|---|
|9101 分行经营总览|18|schema1分行模板|
|9102 支行经营详情|9|坐标画布，完整树及原参数保留|
|9103 个人业绩详情|13|坐标画布，完整树及原参数保留|
|9104 对公经营总览|12|旧对公模板，共享存款卡联动|
|9105 零售经营总览|13|旧零售模板，共享存款及信贷卡联动|
|9106 支行经营总览（测试数据）|17|旧分行模板，含4个系统派生摘要|

分行管理页另提供城市下钻17个区域、支行下钻18个区域。城市地图来自授权机构目录；支行员工排名来自独立绩效接口，不能误绑为机构排名。父子页共用指标明确提示共享关系。个人经营驾驶舱管理页也统一了风格，但当前真实目录没有PERSONAL_COCKPIT，使用隔离开发态样本验收，未创建真实屏。

## 配置语义修复

- 城市汇总补齐对公/零售金额、结算性存款、中间业务收入及四类完成率；城市排名使用四类完成率和两类不良率。多机构口径只合计金额/数量，完成率缺少城市来源时不推算。
- 机构KPI快照字段严格对齐ScreenQueryEngine实际输出；指标筛选项不再被误当作输出列。来源9030原绑定有效，没有改写数据库数据。
- WIDE_TABLE可合并指标目录声明的列；固定来源只显示真实固定列。元/万元/亿元等已知单位别名仅在类型比较时规范化，未知单位不猜测。
- 专用FREE_REPORT测试数据集沿用原绑定，禁止通用字段重映射。系统派生项、固定标题及无独立字段的区域显示说明，不提供无效编辑入口。
- 旧画布按真实组件类型编辑items/categoryCol/valueCol、样式和钻取。排名兼容字段被主字段覆盖时禁用；时间序列与KPI详情来源限制组合生效。完整组件树、子节点、隐藏节点与内部JSON保留。
- 保存返回缺少版本、版本不递增或画布不完整时保留本地编辑并报错。个人驾驶舱区域选择对应当前/历史/机构/目标绑定，保存后恢复选中指标。

## 验证证据与边界

- 主代理独立运行前端29文件257项测试通过；Java校验器54项通过；Maven依赖刷新与启动包构建、前端生产构建通过。构建保留既有大分包提示。
- 官方playwright-cli逐项核验六个大屏82入口及两个下钻上下文；1586/1366/900/390宽均无页面横向溢出。最终真实请求摘要中所有已收到响应均200，console无error/warning，未注册mock路由。
- 六屏可编辑项及个人驾驶舱保存/重载在独立浏览器以开发态mock验证，非真库持久化联调；未保存/发布真实画布。mock请求明细、提交屏ID/版本、原始命令、console、截图均归档，最终注销路由并关闭mock会话。
- 没有执行数据库DDL/DML脚本；继续使用用户指定数据库地址。空来源和未配置字段如实展示，不用占位值伪装业务配置完成。
- 原始证据目录：E:/cx-workspace/runtime/all-screen-workbench-20261029/。主要文件frontend-verified.log、build-verified.log、java-independent.log、real-*-verified.json、real-drill-verified.json、final-network.json、routes-verified.txt、console-verified.txt、final-mock-evidence.json、commands.jsonl。测试Red/Green与中间修复证据同目录保留，最终结果以verified文件为准。

## 逐项入口与字段核对

下表来自真实页面点击后的表单回读。schema1复杂展示项通过展示协议编辑器配置，旧画布通过类型专属字段编辑；只读项目不会伪造独立绑定。

### 9101 分行经营总览（18项）

|入口ID|标题/表单标题|字段或配置控件|
|---|---|---|
|overview-deposit|配置信息|deposit-value|
|overview-deposit-composition|存款业务分布|组件标题、数据来源、统计周期、存款·corporate、存款·retail、存款·total、构成模式|
|overview-loan|配置信息|loan-value|
|overview-loan-composition|贷款业务分布|组件标题、数据来源、统计周期、贷款·corporate、贷款·retail、贷款·total、构成模式|
|overview-settlementDeposit|配置信息|组件标题、比较方式|
|business-retail-deposit-balance|存款余额|组件标题、数据来源、比较方式、展示字段、统计周期|
|business-retail-deposit-rate|存款完成率|组件标题、数据来源、比较方式、展示字段、统计周期|
|business-retail-loan-balance|贷款余额|组件标题、数据来源、比较方式、展示字段、统计周期|
|business-retail-loan-rate|贷款完成率|组件标题、数据来源、比较方式、展示字段、统计周期|
|business-corp-deposit-balance|存款余额|组件标题、数据来源、比较方式、展示字段、统计周期|
|business-corp-deposit-rate|存款完成率|组件标题、数据来源、比较方式、展示字段、统计周期|
|business-corp-loan-balance|贷款余额|组件标题、数据来源、比较方式、展示字段、统计周期|
|business-corp-loan-rate|贷款完成率|组件标题、数据来源、比较方式、展示字段、统计周期|
|business-revenue-operating|营业收入|组件标题、数据来源、比较方式、展示字段、统计周期|
|business-revenue-fee|中间业务收入|组件标题、数据来源、比较方式、展示字段、统计周期|
|business-growth|配置信息|组件标题、数据来源、统计周期、零售存款、零售贷款、对公存款、对公贷款|
|institution-map|配置信息|组件标题|
|institution-ranking|配置信息|组件标题、数据来源、统计周期、测试_直营存款余额、测试_直营存款较上月净增、测试_直营存款月日均|

### 9102 支行经营详情（9项）

|入口ID|标题/表单标题|字段或配置控件|
|---|---|---|
|w-9102t|支行经营详情|文字、字号、对齐、预设|
|w-9102ck|时钟|格式、文字颜色、字号|
|w-9102pf|周期过滤器|默认周期、可用周期|
|w-9102d1|装饰线|预设、方向、粗细|
|w-9102c1|本机构核心指标|数据来源、统计周期、标题、单位兜底（元数据缺失时）、小数位、刷新秒数、钻取启用、目标屏编码|
|w-9102c2|本机构存款净增趋势|数据来源、统计周期、标题、刷新秒数、钻取启用、目标屏编码|
|w-9102c3|辖内支行存款排名明细(轮播)|数据来源、统计周期、标题、刷新秒数、钻取启用、目标屏编码|
|w-9102c4|支行净增排名榜|数据来源、统计周期、标题、刷新秒数、钻取启用、目标屏编码、分类列、兼容名称列（分类列为空时使用）、兼容数值列（指标列表为空时使用）|
|w-9102c5|支行净增对比(横向)|排列方式、数据来源、统计周期、标题、刷新秒数、钻取启用、目标屏编码、分类列|

### 9103 个人业绩详情（13项）

|入口ID|标题/表单标题|字段或配置控件|
|---|---|---|
|w-9103t|个人业绩详情|文字、字号、对齐、预设|
|w-9103ck|时钟|格式、文字颜色、字号|
|w-9103pf|周期过滤器|默认周期、可用周期|
|w-9103d1|上分隔线|预设、方向、粗细|
|w-9103c1|最新KPI总分|数据来源、统计周期、标题、单位兜底（元数据缺失时）、小数位、刷新秒数、钻取启用、目标屏编码|
|w-9103c2|个人存款核心指标|数据来源、统计周期、标题、单位兜底（元数据缺失时）、小数位、刷新秒数、钻取启用、目标屏编码|
|w-9103c3|总体完成率(水波)|数据来源、统计周期、标题、小数位、刷新秒数、钻取启用、目标屏编码、数值列|
|w-9103c4|目标达成仪表|数据来源、统计周期、标题、小数位、刷新秒数、钻取启用、目标屏编码、数值列|
|w-9103c5|KPI 细项完成情况(T+1 快照)|数据来源、统计周期、标题、刷新秒数、钻取启用、目标屏编码|
|w-9103c6|KPI 细项雷达|数据来源、统计周期、标题、刷新秒数、钻取启用、目标屏编码、评分/完成率字段|
|w-9103c7|细项完成进度|数据来源、统计周期、标题、刷新秒数、钻取启用、目标屏编码|
|w-9103d2|下分隔线|预设、方向、粗细|
|w-9103c8|KPI 细项得分趋势|数据来源、统计周期、标题、刷新秒数、钻取启用、目标屏编码|

### 9104 对公经营总览（12项）

|入口ID|标题/表单标题|字段或配置控件|
|---|---|---|
|corpDeposit|对公一般性存款余额（主库）|corpDeposit-value、corpDeposit-change、corpDeposit-date|
|corpDepositAverage|对公一般性存款月均（主库）|corpDepositAverage-value、corpDepositAverage-change、corpDepositAverage-date|
|corpLoan|对公一般性贷款余额（主库）|corpLoan-value、corpLoan-change、corpLoan-date|
|corpRevenue|对公FTP收入（全币折人民币）|corpRevenue-value、corpRevenue-change、corpRevenue-date|
|corpCustomers|配置信息|corpCustomers-value、corpCustomers-change、corpCustomers-date|
|corpNplRate|对公贷款不良率（分类样本）|corpNplRate-value、corpNplRate-change、corpNplRate-date|
|corpSegments|重点客群信贷|corpSegments-name、corpSegments-customers、corpSegments-loan|
|corpAttention|需要协调的事项|corpAttention-label、corpAttention-count、corpAttention-owner、corpAttention-deadline|
|branches|陕西省机构分布|组件标题|
|corpTrend|对公存贷款趋势|corpTrend-date、corpTrend-deposit、corpTrend-loan|
|corpRanking|对公存款排名|corpRanking-orgCode、corpRanking-name、corpRanking-deposit、corpRanking-increase、corpRanking-rate、corpRanking-nplRate|
|corpTargets|对公经营目标|corpTargets-name、corpTargets-actual、corpTargets-target|

### 9105 零售经营总览（13项）

|入口ID|标题/表单标题|字段或配置控件|
|---|---|---|
|retailAum|配置信息|retailAum-value、retailAum-change、retailAum-date|
|retailDeposit|一般性存款余额（本级·测试）|retailDeposit-value、retailDeposit-change、retailDeposit-date|
|retailDepositAverage|存款月日均（本级·测试）|retailDepositAverage-value、retailDepositAverage-change、retailDepositAverage-date|
|retailRevenue|零售FTP收入（主库）|retailRevenue-value、retailRevenue-change、retailRevenue-date|
|retailValueCustomers|配置信息|retailValueCustomers-value、retailValueCustomers-change、retailValueCustomers-date|
|retailLoan|个人及信用卡贷款（主库）|retailLoan-value、retailLoan-change、retailLoan-date|
|retailNplRate|零售信贷产品不良率（分类样本）|retailNplRate-value、retailNplRate-change、retailNplRate-date|
|retailSegments|客户分层|retailSegments-name、retailSegments-customers、retailSegments-aum|
|retailAttention|经营事项与数据核验|retailAttention-label、retailAttention-count、retailAttention-owner、retailAttention-deadline|
|branches|陕西省机构分布|组件标题|
|retailTrend|储蓄余额与月日均趋势|retailTrend-date、retailTrend-deposit、retailTrend-depositAverage|
|retailRanking|机构存款对照|retailRanking-orgCode、retailRanking-name、retailRanking-deposit、retailRanking-average、retailRanking-date|
|retailTargets|零售经营目标|retailTargets-name、retailTargets-actual、retailTargets-target|

### 9106 支行经营总览（测试数据）（17项）

|入口ID|标题/表单标题|字段或配置控件|
|---|---|---|
|deposit|配置信息|deposit-value、deposit-change、deposit-date|
|loan|配置信息|loan-value、loan-change、loan-date|
|customers|配置信息|customers-value、customers-change、customers-date|
|revenue|配置信息|revenue-value、revenue-change、revenue-date|
|leadership-corporateDeposit|对公存款目标完成率|组件标题|
|leadership-corporateLoan|对公贷款目标完成率|组件标题|
|leadership-corporateRevenue|对公营业收入目标完成率|组件标题|
|leadership-institutionCompletion|辖内机构达标率|组件标题|
|depositIncrease|较上月净增|depositIncrease-value、depositIncrease-change、depositIncrease-date|
|depositAverage|月均余额|depositAverage-value、depositAverage-change、depositAverage-date|
|composition|业务构成与占比|composition-name、composition-value|
|attention|流程与经营关注|组件标题|
|branches|辖区机构分布|组件标题|
|trend|主要指标趋势|trend-date、trend-deposit、trend-loan、trend-depositIncrease、trend-customers、trend-rate|
|ranking|全辖机构对比|ranking-orgCode、ranking-name、ranking-value、ranking-increase、ranking-average、ranking-change|
|rate|配置信息|rate-value、rate-change、rate-date|
|loanRate|配置信息|loanRate-value、loanRate-change、loanRate-date|

### 城市下钻（17项）

|入口ID|来源字段/说明|
|---|---|
|city-deposit-total|field-option-citySummary-orgCode、field-option-citySummary-cityCode、field-option-citySummary-cityName、field-option-citySummary-deposit|
|city-deposit-distribution|field-option-citySummary-orgCode、field-option-citySummary-cityCode、field-option-citySummary-cityName、field-option-citySummary-deposit、field-option-citySummary-corpDeposit、field-option-citySummary-retailDeposit|
|city-loan-total|field-option-citySummary-orgCode、field-option-citySummary-cityCode、field-option-citySummary-cityName、field-option-citySummary-loan|
|city-loan-distribution|field-option-citySummary-orgCode、field-option-citySummary-cityCode、field-option-citySummary-cityName、field-option-citySummary-loan、field-option-citySummary-corpLoan、field-option-citySummary-retailLoan|
|city-settlement-deposit|field-option-citySummary-orgCode、field-option-citySummary-cityCode、field-option-citySummary-cityName、field-option-citySummary-settlementDeposit|
|city-retail-deposit|field-option-citySummary-orgCode、field-option-citySummary-cityCode、field-option-citySummary-cityName、field-option-citySummary-retailDeposit|
|city-retail-deposit-rate|field-option-citySummary-orgCode、field-option-citySummary-cityCode、field-option-citySummary-cityName、field-option-citySummary-retailDepositRate|
|city-retail-loan|field-option-citySummary-orgCode、field-option-citySummary-cityCode、field-option-citySummary-cityName、field-option-citySummary-retailLoan|
|city-retail-loan-rate|field-option-citySummary-orgCode、field-option-citySummary-cityCode、field-option-citySummary-cityName、field-option-citySummary-retailLoanRate|
|city-corp-deposit|field-option-citySummary-orgCode、field-option-citySummary-cityCode、field-option-citySummary-cityName、field-option-citySummary-corpDeposit|
|city-corp-deposit-rate|field-option-citySummary-orgCode、field-option-citySummary-cityCode、field-option-citySummary-cityName、field-option-citySummary-corpDepositRate|
|city-corp-loan|field-option-citySummary-orgCode、field-option-citySummary-cityCode、field-option-citySummary-cityName、field-option-citySummary-corpLoan|
|city-corp-loan-rate|field-option-citySummary-orgCode、field-option-citySummary-cityCode、field-option-citySummary-cityName、field-option-citySummary-corpLoanRate|
|city-revenue|field-option-citySummary-orgCode、field-option-citySummary-cityCode、field-option-citySummary-cityName、field-option-citySummary-revenue|
|city-intermediary-income|field-option-citySummary-orgCode、field-option-citySummary-cityCode、field-option-citySummary-cityName、field-option-citySummary-intermediaryIncome|
|city-institution-map|授权机构目录及已核验坐标|
|city-branch-ranking|field-option-branches-orgCode、field-option-branches-retailDepositRate、field-option-branches-retailLoanRate、field-option-branches-retailNplRate、field-option-branches-corpDepositRate、field-option-branches-corpLoanRate、field-option-branches-corpNplRate|

### 支行下钻（18项）

|入口ID|来源字段/说明|
|---|---|
|branch-overview-deposit|field-option-deposit-value|
|branch-overview-deposit-composition|此组件与父屏共享配置，修改会同时作用于父屏与支行下钻页面。|
|branch-overview-loan|field-option-loan-value|
|branch-overview-loan-composition|此组件与父屏共享配置，修改会同时作用于父屏与支行下钻页面。|
|branch-overview-settlementDeposit|此组件与父屏共享配置，修改会同时作用于父屏与支行下钻页面。|
|branch-business-retail-deposit-balance|此组件与父屏共享配置，修改会同时作用于父屏与支行下钻页面。|
|branch-business-retail-deposit-rate|此组件与父屏共享配置，修改会同时作用于父屏与支行下钻页面。|
|branch-business-retail-loan-balance|此组件与父屏共享配置，修改会同时作用于父屏与支行下钻页面。|
|branch-business-retail-loan-rate|此组件与父屏共享配置，修改会同时作用于父屏与支行下钻页面。|
|branch-business-corp-deposit-balance|此组件与父屏共享配置，修改会同时作用于父屏与支行下钻页面。|
|branch-business-corp-deposit-rate|此组件与父屏共享配置，修改会同时作用于父屏与支行下钻页面。|
|branch-business-corp-loan-balance|此组件与父屏共享配置，修改会同时作用于父屏与支行下钻页面。|
|branch-business-corp-loan-rate|此组件与父屏共享配置，修改会同时作用于父屏与支行下钻页面。|
|branch-business-revenue-operating|此组件与父屏共享配置，修改会同时作用于父屏与支行下钻页面。|
|branch-business-revenue-fee|此组件与父屏共享配置，修改会同时作用于父屏与支行下钻页面。|
|branch-business-growth|此组件与父屏共享配置，修改会同时作用于父屏与支行下钻页面。|
|branch-institution-map|由目标实际值、目标值和方向计算，缺失数据不推算；此处没有独立数据绑定。|
|branch-employee-ranking|来自独立绩效方案与计分接口，按方案和日期读取；不是大屏机构排名数据源。|
