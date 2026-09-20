# 支行经营总览第一版验收记录

日期：2026-09-20。

## 页面与范围

- 路由：`/#/branch-operating`；大屏中心对已授权的 LIVE 对公大屏提供“支行经营总览”入口。
- 主屏无地图。六项指标卡保留同比、环比；中央为目标缺口和经营趋势，左右为业务结构、客户营销、重点项目、经营关注，底部为团队贡献。
- 当前财务来源复用 `SCR_CORP_OVERVIEW` 已发布 schema 2 绑定。每项请求携带选中 `contextParams.orgCode`，由服务端在原授权机构组内收窄。没有新建或修改数据库、大屏配置、角色授权或数据源。
- 页面拒绝 TEST/DEMO 发布包；不查询原分行测试大屏、零售含随机计算的测试指标或对公测试排名。
- 默认候选为高新开发区支行（109）。当前真实库不可用，未能比较各支行财务数据覆盖，因此不宣称该支行最完整。
- 机构画像部分为 OTHER，候选名称后缀筛选仅用于 UI，不推断组织层级/地域，不扩大授权范围。

## 已绑定与缺口

- 已绑定：对公存款余额/月均、对公贷款、FTP收入、不良率、对公存款考核实际/目标、经营关注、经营趋势，以及管理触达汇总。
- 对公数据不表示全支行对公＋零售合计。金额沿用原发布包的元映射，原始单位待业务核验的提示保留在首屏。
- 目标使用目标源的实际值与目标值计算，不能把存款时点余额混进考核实际值，也不使用机构组贡献率当完成率。
- 存款/贷款同比环比仅在精确同期日期唯一且基数为正时计算。其他指标、历史不足及零基数保留缺失；没有伪造历史趋势。
- 业务结构缺少可信零售存款源。资产立项现有接口只提供个人视角，团队目标/贡献尚无支行聚合。上述区域明确待接入。考核关注不冒充审批待办。

## 验证结果

- TDD：模型与页面在文件不存在时首次失败；singleOrg 新增7项测试首次全部失败；实现后共6个相关测试文件53项通过。见 `tests.txt`。
- 前端生产构建成功，现有 Sass deprecation / chunk-size 警告保留。见 `build.txt`。
- 使用项目官方 playwright-cli 对正在运行的 `127.0.0.1:8092` 验证，没有注册 mock routes，见 `routes.txt`。
- 109 → 110 → 109 支行切换时指标立即清空，实际请求携带对应支行编码；趋势指标与来源详情交互正常。
- 1920×1080 大屏及1366×768视口检查，较小视口保留纵向滚动，不出现横向溢出。

## 真实联调受阻

触达接口成功返回109机构本月真实汇总，计数均为0，见 `touch-109-response.txt`。

财务绑定请求到达真实后端，但返回业务码 `RPT-43008`（HTTP 200不代表业务成功）。后端真实报错为 MySQL 1040 / `Too many connections`，只读数据池无法建立连接。见 `financial-response.txt`、`database-error.txt`。未终止数据库连接、未更改连接池/数据库配置，未用测试结果代替。

结论：页面布局、真实绑定、切换和失败态已验证；**财务实际值、目标图形、历史同比环比的成功态尚未完成真实联调**。需恢复数据源连接后刷新，才能继续核验各支行覆盖和业务口径。

## 浏览器命令

运行目录为 `yiti/xanzc_frontend`，Node runtime在当前工具PATH。

```sh
./node_modules/.bin/playwright-cli -s=branch-operating goto 'http://127.0.0.1:8092/#/branch-operating'
./node_modules/.bin/playwright-cli -s=branch-operating resize 1920 1080
./node_modules/.bin/playwright-cli -s=branch-operating route-list
./node_modules/.bin/playwright-cli -s=branch-operating request-body 172
./node_modules/.bin/playwright-cli -s=branch-operating response-body 172
./node_modules/.bin/playwright-cli -s=branch-operating response-body 149
./node_modules/.bin/playwright-cli -s=branch-operating resize 1366 768
./node_modules/.bin/playwright-cli -s=branch-operating console error
```

实际 `run-code` 交互命令与结果见 `interactions.txt`、`branch-switch.txt`；截图、请求摘要、控制台分别独立留存。认证状态仅用于临时受控目录，不进入仓库或验收证据。
