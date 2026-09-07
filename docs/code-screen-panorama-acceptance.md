# 代码化经营大屏首版验收

日期：2026-09-07。分支：`feat/code-screen-panorama`。

## 已落地

- Vue + ECharts + Three.js 代码模板替代新大屏的坐标设计流程，保留既有数据源、屏元数据、角色、草稿、发布和回滚入口。
- 省级总览 → 城市经营全景 → 支行指标与趋势；支持搜索、排序、分页、聚合网点展开及无坐标支行列表。
- 十二个语义槽位、明确原始单位、输出列与维度/指标角色校验；市级汇总要求已汇总来源，禁止重复城市覆盖或上下级盲聚合。
- 后端以发布快照身份及授权机构目录为准，单支行取数在服务端收窄。异步代际、403 清空、配置读取失败、CAS 冲突均有保护。
- 数据源引用索引由每个数据源重复扫描改为请求内一次汇集，保留不可信发布归档的保守引用提示。

## 自动验证

- 前端大屏、历史渲染、路由和 API：56 个文件 / 476 项通过；后续绑定与切屏补丁 7 个文件 / 62 项定向通过；最后 Escape 焦点丢失修复 7 项通过。
- 地图 4 个文件 / 19 项通过；城市/总览布局定向 11 项通过。
- 后端 12 个纯单元测试类 / 298 项通过；最终配置渲染校验补丁 `ScreenConfigServiceTest` 32 项通过。没有运行真库 IT。
- `mvn clean install -DskipTests` 全模块成功；前端 `npm run build` 成功。生产产物未包含本地演示 fixture/预览文案。仍有 Sass legacy API 与大 bundle 的构建提示，未做首屏性能专项验收。
- `scripts/check-contract-docs.sh report-analytics-center` 使用 macOS Bash 3 的临时 mapfile 兼容包装执行，通过；原检查脚本未改。`git diff --check` 通过。

## 浏览器验证与证据边界

使用仓库官方 `node_modules/.bin/playwright-cli` + Chrome。

- 真实页面视觉/交互：`/tmp/yiti-panorama-qa`、`/tmp/yiti-panorama-ui-qa`。无页面 API 拦截；`/screen-preview` 为开发态固定演示数据。
- CODE 运行契约：`/tmp/yiti-panorama-contract-qa`。独立 HTTP mock 验证加载、支行 orgCode、403 清空、路由迟到响应。结果不代表真实指标联调。
- 管理流程：`/tmp/yiti-panorama-admin-qa`。独立 HTTP mock 验证保存 4→5、发布保存 5→6→发布后 7、再次保存 7→8、切屏版本隔离及 Settings 取消无写入。最终 console 无错误。
- 各目录包含 CLI 命令、请求/响应摘要、mock 路由说明、console 与截图。临时 mock 和各工作者会话均已关闭。

## 本地运行及实际读取

本地前端 `http://127.0.0.1:8092`，后端 `http://127.0.0.1:18089`。后端已用新聚合包重启，继续使用既有外部配置 `/tmp/yiti-local-start-53937f8/local.yml` 连接用户指定的远端 `yiti`。凭据仅通过环境传入，不写入代码或本文。

重启后只读验证：登录成功，读取 5 张大屏、16 个数据源与现有草稿成功。热缓存复验耗时分别约 1.29 / 3.67 / 3.35 / 2.91 秒，记录于 `/tmp/yiti-panorama-qa/real-api-verification-final.jsonl`。首次权限缓存初始化较慢，不据此宣称所有用户/网络条件均满足这些耗时。

预览：`/#/screen-preview`。管理：`/#/screen-admin/designer`。数据源：`/#/screen-admin/datasources`。

本次未迁移或发布远端大屏配置，未修改业务数据/机构组/权限。预览中的金额和机构点位明确是示例。真实指标使用须按既有审批门禁核对数据源口径、机构成员、真实坐标并保存/发布配置；历史发布屏保留原渲染路径。
