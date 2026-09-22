# S17 运行与浏览器验收记录

日期：2026-09-22

代码：`feat/code-screen-panorama` / 当前 HEAD（最终提交见Git日志）

结论：已连接 `192.168.50.100/yiti`，在关闭调度与外发的本地后端上完成真实登录、三屏迁移保存、TEST 屏发布/回退和无 mock 页面验收。最终发布态已恢复原包，三个经营屏保留 `displaySchemaVersion=1` 新草稿。状态为 `VERIFIED_REAL_WITH_LIMITATIONS`；不把仍缺失的业务数据、5 家缺坐标或未执行的故障注入写成通过。

## 1. 启动状态

- 前端：Vite，`127.0.0.1:8092`，命令级环境变量设置 host/port/strictPort/proxyTarget；未修改共享配置。
- MySQL：目标库为 `192.168.50.100:3306/yiti`；本机 MySQL 仍监听 `127.0.0.1:3306`，不参与本次后端连接。
- 后端：`127.0.0.1:18080`，使用 `screen-scope-e2e` 并以命令级环境变量把主数据源和报表只读数据源指向目标库；Quartz、启动同步和 OBS 外发保持关闭。
- 官方CLI：仓库安装的`@playwright/cli 0.1.18`；先执行`playwright-cli --help`和`--help route/run-code`核对能力。

当前监听：

```text
127.0.0.1:8092  frontend vite
127.0.0.1:3306  local mysql
127.0.0.1:18080 backend (datasource 192.168.50.100/yiti)
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
| B01 标题 | PARTIAL_REAL | 新草稿真实保存/重读/发布字段未丢；未额外写入临时验收标题 |
| B02 换源与内容 | PARTIAL_REAL | 真实旧绑定、字段、单位、内容迁入并回读一致；未改写共享数据源 |
| B03 趋势和表格 | PARTIAL_REAL | 三个机构表均只显示7家允许机构；真实趋势当前无可绘制历史，明确显示空态 |
| B04 结构页签 | PARTIAL_REAL | 公司/零售结构与导航动作可见；核定总量来源未接入，页面未计算占比 |
| B05 地图 | PASS_REAL | 7家目录、2家可信点位、5家待定位列表；地图复用排名指标并显示亿元 |
| B06 导航 | PASS_REAL | 综合/公司/零售真实目录复核和快速直达均成功，63个请求全200、无console错误 |
| B07 刷新异常 | PARTIAL_REAL | 真实刷新/快速切页无串数；迟到、403、混批仍由单测覆盖，未对目标服务注入故障 |
| B08 发布回退 | PASS_REAL | TEST屏完成迁移→保存→发布→原归档回退；原包SHA-256恢复一致，随后重存新草稿 |
| B09 非大屏 | PASS_WITH_BASELINE_WARNINGS | 7个只读页面、66个响应全200、无写请求/console错误；保留4类既有缺路由warning |
| B10 全量排名 | PARTIAL_REAL | 当前规则下7/7家全部可排名、未参与0家；>10家压力由单测/mock覆盖，真实范围只有7家 |

## 5. 真实执行与最终状态

- 三屏草稿：`SCR_PROVINCE` 15个旧槽位→16个展示组件，`SCR_CORP_OVERVIEW` 10→10，`SCR_RETAIL_OVERVIEW` 8→8；三次均 `unresolved=0 / missingFields=0 / needsConfirmation=0`。
- 机构规则：后端返回 `allowedOperatingLevels=[PRIMARY]`、`allowedOrgNatures=[SECONDARY_BRANCH]`；运行目录严格为7家。
- 坐标：仅复用授权范围内 ACTIVE 旧点位，来源 `LEGACY_MAP_POINT`；2家已定位、5家保留在待定位列表，未伪造坐标。
- 发布回退：原发布包哈希 `c1fbc532...3a82f`；每轮回退均恢复相同哈希和无 `displaySchemaVersion` 的旧包。最终线上为旧发布包，三屏为新草稿。
- 最终页面：配置化整页、地图1个、排名7行、旧排名0行、旧硬编码 `93.6/88.2/91.8` 均不存在；20个API响应全200，console错误/警告均为0。
- 最终截图：`E:\cx-workspace\runtime\screen-display-refactor\S17-20260922\published-final-1920x1080.png`。

## 6. 保留限制

1. 7家中仍有5家没有获准坐标；地图保留可访问清单，不能宣称点位已补齐。
2. 业务结构总量和真实趋势历史未接入；页面明确空态，不补算、不造曲线。
3. B01/B02未为了验收写入临时标题或改共享来源；B07未对真实服务注入403/延迟；B10真实范围不足10家。
4. 非大屏只读抽查出现既有菜单指向4个未注册路由的 Vue Router warning；本轮未修改这些功能。
5. 基线失败继续保留：Retail CSS换行断言、旧客户路由测试、FreeReport Controller两项架构守护。
