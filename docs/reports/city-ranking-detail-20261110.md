# 城市支行排名信息与行距调整（2026-11-10）

原列表将剩余高度按 1fr 均分，宝鸡 3 家机构每行约 123.55px。现改为顶部自然排布的 64px 两行：第一行机构、指标率及进度条，第二行经营金额。

- 完成率页签：实际余额、目标、距目标或超目标差额。
- 不良率页签：贷款余额、不良本金，不显示目标结论。
- 金额读取现有机构明细并按明确单位转换为万元；未知值显示破折号，不按率倒推目标。重复机构行、冲突金额保持缺失；缺目标时仍展示有效余额。
- 保留六个页签、比例排序、同分并列、搜索、分页、轮播和点击入口；长名称保留完整 title。

证据目录：`E:/cx-workspace/runtime/city-ranking-detail-20261110/`。

- `model-red.log`、`independent-red.log` 为失败记录；主代理补充并修复了缺目标导致有效余额消失、null 格式化为零的问题。
- `independent-tests.log`：3 文件 34 项测试通过；`build.log`：构建通过（既有 Sass/bundle 提示保留）。
- 官方 playwright-cli 的 `commands.jsonl`、`verify.js`、`final-layout.txt`：六个页签共 18 行补充金额与真实接口独立核对；1915×884 和 1366×768 下每行 64px，无横向溢出；搜索通过。
- `routes.txt` 无 mock route；`final-layout.txt` 归档原始 console 与请求响应摘要，`backend-trace.txt` 保存目标后端对应 trace 的最小脱敏证据。
- `retailDepositRate-1915.png`、`retailNplRate-1915.png`、`ranking-1366.png` 已目视检查。最终 console 无错误或警告。

本次仅前端变更，未修改数据库和服务参数；结果为当前运行快照。
