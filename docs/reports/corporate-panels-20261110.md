# 对公大屏机构表与 KPI 区调整（2026-11-10）

机构表从重复的七列精简为机构、当前指标、补充指标三列。存款模式补充净增，净增或完成率模式补充存款；保留数据可用性、排序、城市筛选与机构详情入口。表头单位仅显示一次，与数值列对齐。

左下待办区域改为“经营规模与质量”，直接复用 corpLoan、corpNplRate、corpRevenue 的现有 KPI 值、单位、来源名称和数据日期。当前显示贷款 11.76 亿元、不良率 2.65%、对公 FTP 收入 1,058.95 万元。未生成新数据、未修改数据库。配置目录移除不再显示的待办入口，KPI 缩略图映射到其原配置项。

验收目录：`E:/cx-workspace/runtime/corporate-panels-20261110/`。

- `red.log`、`catalog-red.log`：修改前失败记录；`independent-tests.log`：主代理独立运行 8 个文件、50 项测试通过。
- `build.log`：最终构建通过，仍有既有 Sass 与 bundle 大小提示。
- `commands.jsonl`、`verify.js`、`final-layout.txt`：官方 playwright-cli 真实页面验收。1915×884、1366×768 下三列表头与行对齐，KPI 三行完整，无横向溢出；指标切换、键盘打开机构目录、Escape 关闭通过。
- 审查发现清理待办样式时误删机构目录共享样式，`directory-style-red.txt` 记录失败；恢复机构目录专属样式后，最终 CLI 验证固定定位与视口边界通过，`directory.png` 已目视确认。
- `routes.txt`：未注册 mock route；`final-layout.txt` 包含真实请求、响应状态、traceId 与原始 console；`backend-trace.txt` 为目标后端匹配 trace 的最小脱敏记录。
- `quality-1915.png`、`ranking-1915.png`、`page-1366.png` 为最终截图，已目视检查。console 无页面错误。

本报告记录此次运行状态，开发服务已加载修改；未改后端或运行参数。
