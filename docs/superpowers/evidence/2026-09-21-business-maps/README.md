# 比例带与业务地图验收

- 分行完整但过期批次：比例带正常展示 41.9973% / 58.0027%，保留过期提示。构成明细超高时支持滚动。
- 零售、对公：复用 relief + callout 地图与城市悬浮卡，按授权机构号关联当前业务 rankings；没有地市汇总时展示机构明细，不制造合计。机构明细可滚动；对公城市点击与清除筛选已验证。
- 数据日期来自本业务排名行或对应 sourceQualities，不套用其他 KPI 日期。
- 真实浏览器验收使用官方 playwright-cli，执行记录见 commands.jsonl。曾清除 page/context routes，route-list 为 No active routes；未注册 mock。实际页面为 /screen-pages/branch-overview-v1、/screen-pages/retail-overview-v1、/screen-pages/corporate-overview-v1。
- 已观测 /api/screen/view/catalog、/api/screen/view/SCR_PROVINCE、SCR_RETAIL_OVERVIEW、SCR_CORP_OVERVIEW、POST /api/screen/data 返回 200；业务模式与页面原有 TEST/LIVE 标识保持不变，不据此认证生产真实性。
- 分行截图 composition.png；零售验收截图 retail.png；对公最终截图 corporate.png、corporate-map.png。零售截图拍于明细压缩完成前，实际最终实现过滤空指标，保留无数据提示。
- 中途全量并行测试出现超时、开发中的新增红测失败；最终限定并发回归为 58 文件、447 项全通过，见 tests-final.txt。
- 最终命令：vitest run src/views/screen/panorama/__tests__ --maxWorkers=2 --minWorkers=1 --testTimeout=30000；vite build，均退出 0。构建保留既有 Sass 弃用与大包提示。
- 验收后半段本地 18089 后端停止监听，零售二次刷新 /api/screen/data 返回空响应 500，已归档原始 console/request 摘要；此后未再宣称刷新成功，未用 mock 覆盖故障。此前已有对应业务真实数据悬浮证据。
- 截图初看地图不可见，经移开悬浮卡确认只是卡片遮挡中心地图；未保留试验性 WebGL buffer 改动。
- 地图迁移涉及已有未提交零售改动，保持工作区供整体审阅，未夹带提交既有改动。
