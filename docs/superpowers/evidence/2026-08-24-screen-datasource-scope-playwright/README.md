# 命名机构组大屏数据源过滤验收

- 日期：2026-08-24
- 前端：`http://127.0.0.1:8092/#/screen-admin/designer`
- 工具：官方 `playwright-cli`，会话 `screen-save-fix`
- 模式：仅开发态 mock，非联调；真实登录会话另行完成了无 mock 保存验证。

## 结论

1. `NAMED_GROUP + RETAIL` 大屏加载历史个人 KPI 绑定（`9009 / KPI_DETAIL / EMP`）后，属性面板会清除该绑定并提示重新选择机构宽表。
2. 数据源下拉只保留 `WIDE_TABLE + ORG_INDEX_RESULT + org_code` 的候选；个人 KPI 数据源不再出现。
3. 选择 `机构核心指标(宽表)`（`dsId=9002`）后，保存请求携带正确绑定并返回成功，页面状态为“已保存”。
4. 保存阶段 console 为 0 error、0 warning；保存和后续数据请求均为 HTTP 200。

## 截图

- `01-filtered-options.png`：非法历史绑定已清除，下拉只显示机构宽表。
- `02-valid-save.png`：选择机构宽表并保存成功。

## 归档

- `routes.txt`：全部开发态 mock route 清单。
- `console.txt`：保存阶段原始 console 摘要。
- `network.txt`：保存阶段原始请求摘要。
- `save-request.json` / `save-response.json`：保存请求与响应正文。
- `real-backend-validation.md`：真实登录、真实后端的无 mock 保存证据。
- `commands.md`：实际执行命令。
