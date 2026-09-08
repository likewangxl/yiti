# 真实机构与数据接入验收记录

日期：2026-09-08。状态：本轮代码能力已实现并完成构建与针对性回归；真实位置存储、资源登记、数据源新配置和发布尚未执行。

## 已核实的输入与现场

- 用户确认当前机构指标宽表的存贷款原始金额为亿元，绑定明确使用 `HUNDRED_MILLION`；不将此确认推广到其他来源。
- 用户确认暂无详细营业地址台账；无坐标、无地址的机构保留待定位。
- 真实机构管理接口返回 114 条，7 条已配置画像、107 条未配置，现有有效坐标 0。7 个非空城市字段中仅 4 个为标准六位编码，另 3 个含异常字符。
- 只读核实 `yiti` / `yiti_test` 没有 `PT_ORG_LOCATION`，目标库未登记新位置资源；没有执行建表、覆盖、克隆、资源写入、画像修改、数据源保存或大屏发布。

## 官方 playwright-cli 验收

使用仓库安装的官方 CLI；原始命令、响应、console、route 清单和截图保存在受限临时目录 `/tmp/yiti-realdata-implementation-qa`，不提交原始机构台账和登录凭据。

| 场景 | 证据及结果 | 边界 |
| --- | --- | --- |
| 真实机构管理列表 | `org-qa.txt`，统计 114 / 7 / 107 / 0 / 114 与真实 GET 返回一致；`org-profiles-1366.png` | 实际后端和远端数据库读取 |
| 缺画像筛选及编辑入口 | `org-gap-3.txt`，107 条，本地筛选没有新增网络请求，可打开编辑后取消 | 未执行画像保存 |
| 接入检查 | `bindings-live.txt`，14 个槽位；画像检查点击前无相关 GET，点击后返回 114 / 7 / 107 / 0 | 静态配置检查，不证明已取指标或已发布 |
| 构成图两列绑定 | `composition-live-2.txt`，从真实目录选择 9014 的对公/零售一般性存款列，单位亿元，结构可用；模式切换清空四个字段/单位控件 | 仅浏览器未保存编辑；未改远端草稿 |
| 机构目录交互 | `preview-directory.txt`、`preview-final.txt`，43 条示例机构、3 条待定位；搜索、空城市筛选、详情、Esc、焦点恢复通过；`preview-demo-final-assert.txt` 与 `directory-demo-final-1920.png` 核对最终演示标识 | 明确标记的开发态 fixture，非真实机构联调 |
| 请求与拦截器 | `routes-final.txt` 为 No active routes；`requests-final.txt`、`console-debug.txt` 记录原始输出 | 未注册 mock route；fixture 页面零业务请求 |
| 新后端登录与机构入口 | `login-requests-new-backend.txt` 登录及工作台接口 200；`backend-cli-final-4.txt` 机构 GET 200，统计 114 / 7 / 107 / 0 / 114 | 实际新 JAR 和原远端数据库读取 |
| 地址能力未授权路径 | `backend-cli-final-4.txt`：点击前无位置请求，点击后恰好一次 capabilities GET，实际 403；明确错误且无记录、保存或解析请求；`location-capability-real-403.png` | 新资源未登记；未绕过授权，也未虚称地址保存成功 |

登录工作台曾有既有菜单路由警告和业务 GET 超时。远端 MySQL 中途出现连接中断，后续只读连接及屏配置/机构接口重试成功；失败命令一并保留，未通过延长前端超时或改连接配置掩盖。

## 代码测试与运行验证

各功能先写失败用例，红绿日志位于同一证据目录。所有实现冻结后的主代理验证：

- 前端大屏、机构画像工具与 API：61 个测试文件，562 项全部通过（`frontend-final-tests-2.log`）；生产构建通过（`frontend-final-build-2.log`）。构建仅有既有 Sass 与 chunk 大小警告。官方 CLI 发现的重复能力请求已用红测固定并修复，最终点击一次只请求一次。
- 完整后端 `mvn clean install -DskipTests` 通过，刷新跨模块 SNAPSHOT，编译所有测试但不执行真库测试（`backend-clean-install.log`）。
- 后端定向与架构检查：404 项中 402 通过、2 项基线既有失败。auth 的 61 项全部通过；report 的 343 项中 341 通过。本轮位置、大屏绑定、权限范围、保存发布和 HTTP 契约用例均通过（`backend-final-tests.log`、`backend-test-summary.json`）。
- 两项失败为 `RptNoEntityInControllerLocalsArchTest` 与 `RptNoEntityInControllerArchTest`，均指向 `FreeReportController.listBatches` 返回 `RptFreeReportBatch` 实体。已核对改动前 HEAD `8875593e7` 同样存在该签名，相关 Controller、Service 和架构测试本轮无改动（`backend-baseline-failures.txt`）。保留失败，不删除或放宽守护规则，不宣称后端全绿。
- 新增位置存储默认关闭时可完成最小 Spring 装配且不注册位置 Mapper；开启分支只验证 Mapper 注册元数据，不代表真实数据库可写。地址变更清坐标、首次重复插入及更新版本冲突、审计失败、越权、令牌篡改/过期均有针对性用例。

真实数据库集成测试未执行，外部地理编码未调用。地址表的真实事务回滚、并发、外部服务额度与落点质量仍须在获准隔离环境验证。

本地后端使用新的独立 JAR 启动，原 JAR 保留可供回退；进程、JAR SHA256、原配置 SHA256 和启动摘要归档于 `backend-runtime.json`、`backend-startup-summary.txt`。原数据库连接、端口、代理和外联开关未改。新服务启动成功，未出现位置表缺失错误；登录、菜单、权限和机构接口返回 200。位置资源未登记时返回真实 403，浏览器显示权限错误并停止后续操作，页面无 JavaScript 异常；该 403 的 console error 和既有两个菜单路由警告如实归档，不能写成 console 全空。最后复验未注册 mock route。

CLI 原会话中断后重新建立会话并正常登录；辅助脚本的选择器、运行环境或等待条件失败均保留原始输出，以最终成功记录为验收依据。Sol 对最终实际 diff 复审未发现本轮阻断问题；既有自由报表架构失败及真实启用前置条件保留在交付范围说明中。

## 真实启用仍需完成

详见 [位置能力启用清单](code-screen-location-enablement.md) 与 [真实接入方案及第一批候选](code-screen-real-data-integration-plan.md)。必须分别核对机构经营性质/城市/授权范围、父子汇总去重、数据源语义与单位、位置表和独立资源。隔离环境的结构、审计回滚、版本并发、外部服务和真实地址落点验收完成后，再依根 `AGENTS.md` 确认目标库写入及发布。

`/screen-preview` 继续是视觉和交互演示入口；正式真实数据应通过有权限的已保存草稿和发布屏访问。不能把演示有图、有数当作真实接入验收完成。
