# 合成验收 fixtures

`../scripts/register-mocks.js` 是个人驾驶舱浏览器验收使用的唯一可执行合成数据源。所有身份、机构、客户、任务、业务编号、日期和数值均为验收专用值，带有“验收示例”前缀，不代表真实业务数据。

本目录内容仅用于 **仅开发态 mock，非联调**。浏览器 mock 由 `../scripts/register-mocks.js` 在官方 `playwright-cli run-code` 中注册；不再维护第二份 JSON 响应副本，避免 fixture 漂移。

场景由页面 URL 查询参数选择：

- `qa=base`：四区有合成数据，包含 0、null、长名称和 touchRestricted 样本。
- `qa=partial`：工作流待办返回 500、我的客户返回 403，工作台指标、触达、业务进度仍返回成功数据。
- `qa=unauth`：`/api/auth/current-user` 返回 401，验证未登录守卫。
