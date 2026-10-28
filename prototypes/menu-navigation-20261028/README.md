# 菜单改造交互原型

基于当前 `AppSidebar.vue`、`AppHeader.vue`、`router/index.js` 与 `styles/tokens.scss` 制作。保留平台深蓝侧栏、浅灰背景、蓝色操作按钮、顶部页签和白色业务表格。

- `menu-navigation.html`：可在对话中展示的 HTML fragment，包含“任务分组（推荐）”与“业务域双栏”。
- 分组、菜单选择、完整面包屑、跨业务域搜索、收藏、客户查询和重置均可交互。两方案保持独立导航状态。
- 菜单分类为建议方案；客户与任务均为虚构数据，其他业务页面只展示入口预览。
- **仅开发态 mock，非联调。** 无 API 请求、无网络 mock route、无真实权限校验、无数据库写入。正式项目源代码未改变。

## 验证

使用仓库已有官方 `@playwright/cli`，实际执行命令如下（从本目录运行）：

```powershell
$cli = 'E:/cx-workspace/yiti/xanzc_frontend/node_modules/.bin/playwright-cli.cmd'
& $cli -s=menu-prototype open 'http://127.0.0.1:8766/preview.html'
& $cli -s=menu-prototype resize 1080 900
& $cli -s=menu-prototype run-code --filename evidence/verify-navigation.js
& $cli -s=menu-prototype run-code --filename evidence/verify-dual-responsive.js
& $cli -s=menu-final route-list
& $cli -s=menu-final requests --static
& $cli -s=menu-final console
```

最终两份行为检查均通过；320px 内容宽度没有整体水平溢出。截图已人工查看。全新浏览器会话无 console 错误/警告，网络只有预览文档和渲染器图标/浮层依赖，均返回 200，无业务 API。完整命令与执行代码还保留在 `*-check.txt` 中。

`initial-check.txt` 和 `initial-review.png` 保留了早期搜索路径错误的证据，已由统一菜单索引修复。查询按钮已适配对话 iframe 的禁止表单提交限制。

## 本地预览

`evidence/preview.html` 为本机 Visualize 渲染器生成的临时预览，不入库。当前预览地址是 `http://127.0.0.1:8766/preview.html`，需预览服务保持运行。对话内展示直接使用原始 fragment。

本原型无需应用打包或后端启动，未宣称生产前端构建或真实权限联调完成。
