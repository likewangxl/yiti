# 零售地图空地市交互验收（2026-11-10）

零售地图启用隐藏无机构地市时，同步隐藏三维光圈与二维标记，关闭区域悬停高亮、点击选择、详情浮层和空态提示；二维回退移除空区域键盘入口与选中样式。保留行政区轮廓以及有机构城市交互。未启用此开关的地图维持原有行为。

判定复用机构数量/明细证据；即使指标状态为 MISSING，明确 0 家机构也禁用交互，不以金额零值判断空城。本次无数据库或运行配置修改。

验证证据：`E:/cx-workspace/runtime/retail-map-interaction-20261110/`。

- `red.log`：新增回归用例先失败，发现初版改动破坏默认 inline 标记规则；修正后 `green.log` 中 4 个文件、36 项测试通过。
- `build.log`：前端构建成功，仍有既有 Sass 和 bundle 大小提示。
- `commands.jsonl`、`verify.js`、`live.txt`：官方 playwright-cli 验收真实零售页面，WebGL ready。对铜川、延安、汉中、安康、商洛逐一实际移动鼠标和点击，均无 hover、tooltip、状态弹窗或选择变化。榆林实际鼠标悬停与点击成功，最后恢复西安选择。
- `routes.txt`：No active routes；未注册 mock。`live.txt` 保存真实请求参数、HTTP 状态、业务码、traceId 与行数摘要；`backend-traces.log` 保存目标后端匹配 trace 的最小脱敏记录。
- `console.txt`：0 errors、0 warnings。`map.png` 已目视检查，仅有机构的 5 个地市保留名称与光圈，全部地市轮廓完整。

本次验收为当时运行状态快照；无后端改动，前端开发服务已加载修改。
