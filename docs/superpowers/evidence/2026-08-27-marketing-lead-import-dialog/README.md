# 线索录入导入弹窗验收证据

- 验收时间：2026-08-27
- 页面：`http://127.0.0.1:8090/#/customers/leads/new`
- 前端 checkout：`/home/djdev/lijh/yiti/xanzc_frontend`
- 后端：`http://127.0.0.1:18080`
- 浏览器：官方 `playwright-cli`，真实 Chromium 页面
- 网络路由：`route-list` 返回 `No active routes`，未注册 mock
- 数据边界：完成登录后只执行 GET 查询、模板下载和浏览器本地文件选择；未点击弹窗“上传”，未新增导入批次，未写数据库

## 验收动作

1. 进入线索录入页，确认第二个 Tab 文案为“导入记录”。
2. 点击右上角“批量导入”，确认仍停留在原 Tab，并打开独立“批量导入”弹窗。
3. 检查弹窗包含“下载模板”“选择文件”“上传”，并显示 xlsx、xls、csv 和 10MB 限制。
4. 点击“下载模板”，浏览器成功下载 `lead-import-template.csv`。
5. 从本机选择该模板，文件名在弹窗中显示；检查网络请求，确认选择文件未发起导入 POST。
6. 取消弹窗后点击“导入记录”，确认真实批次列表接口返回 200。
7. 检查活动路由、控制台和请求列表。

## 结果

- 页头“批量导入”已改为弹窗入口，不再切换 Tab。
- 第二个 Tab 已显示为“导入记录”，仍保留批次筛选、表格和分页。
- 本地模板下载成功；选择文件只保存在浏览器文件列表中，没有立即上传。
- `GET /api/marketing/lead-import-batches?keyword=&status=&pageNo=1&pageSize=20` 返回 200。
- 文件选择后的导入批次请求过滤结果只有上述 GET，没有 POST。
- 控制台中的 401 来自建立登录会话之前的 `current-user`；登录后的目标页面请求均正常。另有既有 `/bizexec/supports` 路由未匹配 warning，与本次改动无关。

## 证据文件

- `import-dialog.png`：独立导入弹窗、模板下载、本地文件选择和上传按钮。
- `local-file-selected.png`：本地模板已选择但尚未上传。
- `import-records-tab.png`：“导入记录”Tab 与真实批次列表。
- `commands.txt`：CLI 验收命令。
- `console.txt`：控制台原始摘要。
- `requests.txt`：目标请求原始摘要。
- `route-list.txt`：活动路由原始结果。

## 自动化验证

```text
npm test -- --run src/views/customerMarketing/__tests__/MarketingLeadEntry.spec.js
Test Files  1 passed (1)
Tests       27 passed (27)

npm run build
3089 modules transformed
built successfully

git diff --check
passed
```

## 模板字段对齐增量验收

- 模板表头已按“录入线索”抽屉顺序调整为 18 个可表格化字段。
- 弹窗明确说明：客户标签填写标签 ID、指定客户经理范围填写工号，多个值用分号分隔；附件需在线索生成后单独补充。
- 浏览器下载的 `lead-import-template.csv` 与前端静态模板逐字节一致，首行共 18 列。
- 选择本地模板后，`requests --filter='marketing/lead-import-batches'` 无输出，证明未发起导入批次 GET/POST；未点击“上传”。
- 当前验收会话 `route-list` 为 `No active routes`，未注册 mock；console 为 0 errors、2 条既有 `/bizexec/supports` 路由 warning。
- 新增截图：`import-dialog-aligned-template.png`、`local-file-selected-aligned-template.png`。
- 完整模板首行、浏览器命令和原始摘要见 `template-alignment.txt`。
