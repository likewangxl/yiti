# 大屏设计器图表切换数据源回显验收

- 日期：2026-08-25
- 页面：`http://127.0.0.1:8091/#/screen-admin/designer`
- 工具：官方 `playwright-cli 0.1.18`，Chromium，会话 `screen-ds-switch`
- 视口：1920 × 1080
- 模式：**仅开发态 mock，非联调**。11 条路由均在浏览器上下文内注册；没有请求真实后端，也没有保存、发布或数据库写入。

## 验收结论

1. 选择图表 A 后，右侧数据源显示“全省存款聚合”，标题显示“图表A-存款余额”。
2. 随后切换到同类型图表 B，右侧数据源更新为“全省贷款聚合”，标题更新为“图表B-贷款余额”。
3. 切换过程中数据源目录分别请求两次且均返回 200；没有 POST、PUT、PATCH、DELETE 请求。
4. Console 为 0 error、12 warning；12 条均为既有 Element Plus `el-radio label` 弃用提示，与本次修复无关。

## 证据

- `01-chart-a-selected.png`：图表 A 选中，数据源为“全省存款聚合”。
- `02-chart-b-selected.png`：切换图表 B 后，数据源为“全省贷款聚合”。
- `routes.raw.txt`：11 条开发态 mock 路由及响应摘要。
- `network.raw.txt`：切换阶段原始 API 请求/响应摘要。
- `console.raw.txt`：官方 CLI 产生的原始 Console 日志。
- `commands.md`：实际执行命令与首次浏览器运行时检查记录。
