# 1920×1080 最终官方 CLI 验收

## 结论与发布边界

- 状态：**PASS**；代码基线 `22108e35edb4f859c2d806ec4b1868d7aac274f6`。
- 使用项目官方 `playwright-cli`、Chromium、1920×1080、`/#/login?normal` 和真实 admin 会话；凭据未归档。
- 统一会话 `final1920xianfinal` 的起点与末尾 `route-list` 均为 `No active routes`。普通后台 59/59、XIAN 草稿总览和四个二级分行钻取均通过，首错为空。
- 除登录和 `/api/screen/data` 只读取数 POST 外没有写请求；API 无失败响应、无 fallback、console error 为 0。本批未访问或修改红色引擎。
- 本目录只发布 `22108e35` clean 批。早期 41px、ProductLib hover、20px tag 等首错批的 JSON、raw 和截图已从最终根移除，不参与最终结论。

## 脱敏说明

- 六个参数化详情页确实使用浏览器内 GET 发现的真实 ID 完成访问。发布 JSON 不保存这些值：`routes[*].dom.actualPath` 机械还原为 `templatePath`（如 `/announcement/:id`），URL 实体路径段统一为 `<redacted-id>`，查询值为 `<redacted>`。
- 其他 GET URL 中的用户、角色、KPI 方案、绩效指标、字典等实体路径段也统一为 `<redacted-id>`。脱敏仅改字符串，不改路由覆盖、PASS/FAIL、HTTP 状态、DOM 尺寸或交互结果。
- [redaction-manifest-1920-22108e35.json](json/redaction-manifest-1920-22108e35.json) 记录变更 JSON Pointer、发布替换值、脱敏前/后文件 SHA 及不变量校验；不保存原始 ID、其摘要或可恢复映射。
- 密码、Cookie、Authorization/Bearer、会话令牌、完整请求/响应体、真实详情 ID、真实 dsId 数值均未发布。后台表格/输入/描述数据和四个 XIAN 详情业务区块已在截图中遮罩。
- XIAN 的 128、191、169、129 是需求明确公开的机构 `orgCode`，并非普通后台动态发现的详情 ID；保留用于验证四节点路由/取数契约。`empId:''` 是已确认合法 String 假值。

## 普通后台 59/59

- 59/59 路由通过，外层横向溢出路由为 0。
- 48 个实载表头行、630 个实载数据行、341 个表头单元格、4553 个数据单元格均为 40px；4894 个单元格底边框均为 1px，4553 个数据单元格上下 padding 均为 0。
- 260 个紧凑双行单元格处于 39px 内容区；587 个 tag 均为 `24px / line-height 22px / padding-inline 8px`；943 个控件均为 32px；554 个操作单元格 sticky 契约通过。
- SysWorkflowMonitor 的 20 个 `code.mono` 均为 `rect 18 / client 16 / scroll 16 / border 1+1 / line-height 16 / row 40`，无裁切。
- EvalTasks、SysConfig 重置契约通过；六个点名弹窗均完成“打开后取消”，无写请求；四个目标页非动态 warning 为 0。
- ProductLib 当前九个 clamp cell 为两行紧凑样式，八个真实短文本样本未误加 title。真实数据无 `>=2px` 溢出长文本，按批准边界记录 `not_exercised_no_real_overflow_sample`；未造数据、未 mock、未注入 DOM。

## XIAN 草稿总览与钻取

- 总览 GET 为 HTTP 200/code 0；`schemaVersion=2`、`mode=XIAN_COMPOSITE`、`baseRegion=XIAN_OUTLINE`。
- ECharts 真实运行实例的 geo map 为 `xian-six-districts`，六区为未央区、莲湖区、新城区、碑林区、雁塔区、长安区；两层标题不重叠，OSM 链接契约通过。
- 宝鸡（click→128/LEFT）、渭南（Enter→191/RIGHT）、咸阳（Space→169/TOP）、榆林（click→129/FAR_TOP）均进入 `SCR_BRANCH`。
- 四个详情布局一致，每节点五个 `/api/screen/data` POST，共 20/20 HTTP 200/code 0；`dsId` 仅归档类型和有效性，`period` 非空，`orgCode` 正确，递归无 null/undefined。

## 权威证据索引

- [route-dom-probes-1920-22108e35.json](json/route-dom-probes-1920-22108e35.json)：脱敏后的 59 路由 DOM、computed style、交互、console 和请求摘要。
- [xian-final-1920-22108e35.json](json/xian-final-1920-22108e35.json)：XIAN 草稿、ECharts 六区、四节点和 20 次取数契约。
- [final-scan-1920.js](raw/final-scan-1920.js)、[xian-final-1920.js](raw/xian-final-1920.js)：原始验证逻辑；发布保存逻辑会先生成脱敏副本。
- [route-list-unified.txt](raw/route-list-unified.txt)、[console-unified.txt](raw/console-unified.txt)、[requests-unified-sanitized.txt](raw/requests-unified-sanitized.txt)、[commands-sanitized.txt](raw/commands-sanitized.txt)：统一会话原始状态和脱敏摘要。
- [sensitive-scan-1920.txt](raw/sensitive-scan-1920.txt)：完整目录敏感模式扫描、命中解释与边界。
- [SHA256SUMS.txt](SHA256SUMS.txt)：最终目录内所有发布文件（清单自身除外）的 SHA-256。
- `screenshots/g-01-login.png` 至 `screenshots/g-59-SysPersonTags.png` 及五张 `screenshots/XIAN-*-1920.png`：clean 批截图。

CLI 会话已关闭；8091 按协调要求继续运行。本轮证据脱敏未重跑页面、未修改产品代码/配置/服务/数据库，也未提交。
