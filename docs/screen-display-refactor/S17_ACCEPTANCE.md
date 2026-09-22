# S17 运行与浏览器验收记录

日期：2026-09-22

代码：`feat/code-screen-panorama` / 当前 HEAD（最终提交见Git日志）

结论：前端真实进程与官方 CLI 的受控开发态 mock 验收通过；真实后端/数据库联调为 `BLOCKED_ENV`，本记录不把 mock 证据当作真实数据或权限证据。

## 1. 启动状态

- 前端：Vite，`127.0.0.1:8092`，命令级环境变量设置 host/port/strictPort/proxyTarget；未修改共享配置。
- MySQL：本机 MySQL 8.4，运行时强制 `bind-address=127.0.0.1`，监听 `3306`。
- 后端：未启动。`application-screen-scope-e2e.yml`只允许`yiti_test`，但本机没有`YITI_SCREEN_SCOPE_DB_USERNAME/YITI_SCREEN_SCOPE_DB_PASSWORD`；现有本地配置凭据也无法只读连接。未尝试默认`application.yml`的`yiti`业务库，未改数据库。
- 官方CLI：仓库安装的`@playwright/cli 0.1.18`；先执行`playwright-cli --help`和`--help route/run-code`核对能力。

当前监听：

```text
127.0.0.1:8092  frontend vite
127.0.0.1:3306  local mysql
127.0.0.1:18080 not listening
```

## 2. 浏览器执行方式

真实页面地址：

```text
http://127.0.0.1:8092/#/screen-pages/branch-overview-v1?businessLine=COMMON
```

使用官方CLI启动独立会话，并通过`run-code --filename`注册受控路由、执行交互和截图。完整路由与夹具脚本：

```text
E:\cx-workspace\runtime\screen-display-refactor\S17-20260922\install-mock-routes.js
```

路由清单（仅开发态 mock，非联调）：

```text
GET  /api/auth/current-user
GET  /api/auth/my-menus
GET  /api/auth/permissions
GET  /api/screen/view/catalog
GET  /api/screen/view/{SCR_PROVINCE|SCR_CORP_OVERVIEW|SCR_RETAIL_OVERVIEW}
POST /api/screen/data
```

路由使用严格正则`^https?://[^/]+/api/`，不会拦截Vite的`/src/api/*.js`模块。最终console原始消息只有：

```text
debug [vite] connecting...
debug [vite] connected.
```

无`console.error`、`pageerror`或页面`role=alert`。

## 3. 可见结果与交互

受控夹具明确标识为`TEST`，包含12家授权机构、3个城市、1家缺坐标机构和统一批次`S17-MOCK-BATCH-1 / 2026-09-20`。

- 指标卡：显示配置标题、副标题和`123.40亿元`。
- 趋势：4期存款/贷款系列可见，不生成额外历史点。
- 明细表：12家中文机构名和金额均可见；S17首次发现的维度文本被显示为“—”问题已修复。
- 业务结构：核定总量、公司、零售及“其他”缺口可见；S17首次发现的`total`绑定校验缺口已修复，页面不再出现“字段不受支持: total”。
- 地图：省级地图、图例、12家/已定位11家统计和缺坐标旁列表可见。
- 城市下钻：点击西安市后保持在“西安市 · 支行经营全景”，显示4家机构；初始化不再自动跳机构页。该路径发现并修复了内部默认选中误触发导航的问题。
- 排名：12/12行完整可到达；切换到`increase`成功，列表仍为12行，手动滚动到底部成功。
- 条线导航：点击“查看公司”后URL进入固定`corporate-overview-v1?businessLine=CORP&metricKey=deposit`，标题为“对公经营总览”，无导航错误；请求顺序包含目录复核和目标视图复核。
- 批次：首个`POST /api/screen/data`不带batchId，后续4个请求均携带同一`S17-MOCK-BATCH-1`。

截图：

- `E:\cx-workspace\runtime\screen-display-refactor\S17-20260922\branch-overview-mock.png`
- `E:\cx-workspace\runtime\screen-display-refactor\S17-20260922\city-overview-mock.png`
- `E:\cx-workspace\runtime\screen-display-refactor\S17-20260922\corporate-overview-mock.png`

## 4. S17矩阵状态

| 场景 | 状态 | 说明 |
| --- | --- | --- |
| B01 标题 | PARTIAL_MOCK | 运行展示已验证；真实保存/回读/发布未验证 |
| B02 换源与内容 | PARTIAL_MOCK | 组件数据/单位链可见；真实候选与保存未验证 |
| B03 趋势和表格 | PASS_MOCK | 受控响应下系列、列、维度文本通过 |
| B04 结构页签 | PASS_MOCK | 显式总量、公司/零售/其他及条线动作通过 |
| B05 地图 | PARTIAL_MOCK | 省→市停留、缺坐标列表通过；真实机构画像规则未签认 |
| B06 导航 | PARTIAL_MOCK | 综合→公司和城市状态通过；真实账号/目录未验证 |
| B07 刷新异常 | UNIT_ONLY | 迟到、403、混批由单测覆盖；真实网络故障未验证 |
| B08 发布回退 | BLOCKED_ENV | 需要获准隔离测试屏和后端数据库凭据 |
| B09 非大屏 | UNIT_ONLY | F2保护区64/65，唯一失败为基线旧路由测试；未做真实页面抽查 |
| B10 全量排名 | PASS_MOCK | 12家、切指标、滚动均通过；不是真实机构数据 |

## 5. 未解除条件

1. D03/D04：允许展示的`operatingLevel/orgNature`枚举或签认机构名单尚未提供；S11/S13保持fail-close。
2. 隔离数据库凭据缺失，后端不能在`screen-scope-e2e`安全启动；因此无真实登录、权限、数据源、草稿保存、发布、回退和真实请求响应证据。
3. D01/D02/D05/D07相关真实指标编码、KPI方案、目标、分母和排序方向仍需业务签认。
4. 基线失败继续保留：Retail CSS换行断言、旧客户路由测试、FreeReport Controller两项架构守护。

满足以上条件后，必须重新执行无mock B01～B10；本次结果只能表述为“大屏软件改造完成，真实限定范围验收未完成”。
