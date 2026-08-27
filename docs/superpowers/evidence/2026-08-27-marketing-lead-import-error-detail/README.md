# 线索导入错误提示具体化验收

- 日期：2026-08-27
- 页面：`http://127.0.0.1:8090/#/customers/leads/new`
- 浏览器：官方 `playwright-cli`，真实 Chromium 页面
- 运行实例：前端 `/home/djdev/lijh/yiti/xanzc_frontend`；后端端口 `18080`

## 现场根因

运行日志确认用户上传 `lead-import-template (1).csv` 后，平台文件服务在模板解析前返回 `GOV-42203 文件格式不合法`。页面和线索导入服务都声明支持 CSV，但共享文件扩展名白名单未包含 `csv`。

## 验收结果

1. 未注册 mock route 时选择 `package.json`，页面直接提示“导入文件格式不支持：仅支持 .xlsx、.xls、.csv 文件”，且没有发起导入 POST。
2. 为验证后端明细错误的页面展示，注册了一条仅开发态 mock：`POST **/api/marketing/lead-import-batches` 返回 `ALL_FAILED`，明细原因为“文件缺少关键表头：客户名称、统一社会信用代码，请下载最新线索导入模板”。页面展示“第1行 + 具体原因”，并保留导入弹窗。
3. mock 验收完成后已移除路由；`route-list` 返回 `No active routes`。
4. Console 为 0 errors、1 条既有 `/bizexec/supports` 路由 warning，与本次改动无关。

## 验证边界

- 具体错误展示使用仅开发态 mock，非后端联调。
- 未对真实导入接口执行成功上传，避免向当前数据库和 OBS 新增验收数据。
- CSV 白名单、表头/空表/损坏 Excel/10MB 上限和物理行号由后端单元测试验证。

## 自动化验证

```text
前端：2 个测试文件，44 tests passed
前端构建：3089 modules transformed，build success
FileServiceTest：24 tests passed
MarketingLeadImportServiceTest：10 tests passed
customer-marketing-center 主代码编译：BUILD SUCCESS
```

`customer-marketing-center` 的标准 Maven test 生命周期仍被工作区已有、未跟踪的 `NameListServiceTest.java` 缺失类型阻塞；目标测试类已单独编译并由 Surefire 执行通过，未修改该无关文件。

## 证据文件

- `unsupported-format-specific-message.png`：真实页面本地文件格式校验提示。
- `backend-detail-specific-message-mock.png`：真实页面展示 mock 后端返回的行号和具体原因。
- `commands.txt`：官方 CLI 命令。
- `requests.txt`：脱敏请求/响应摘要。
- `route-list.txt`：路由注册及清理结果。
- `console.txt`：控制台摘要。
- `runtime-root-cause.txt`：修改前真实日志中的失败证据。
