# 脱敏请求/响应摘要

普通后台 59 路由：

- 记录请求 166；只读方法；HTTP/业务坏响应、失败请求、业务写请求均为 0。
- 6 个详情 ID 来自真实 GET，值未归档；查询参数值使用 `<redacted>`。

XIAN：

- 概览草稿 GET：HTTP 200、`code=0`。
- 四节点钻取：20 个 `/api/screen/data` POST，20/20 HTTP 200、`code=0`、`nullPaths=[]`。
- 每个 body 均保留 `schemaVersion=1`、`screenCode=SCR_BRANCH`、`dsId`、`period`、目标 `contextParams.orgCode`。
- `dateFrom`、`dateTo` 不存在；`empId` 为合法 String 假值并保留，不是 null。
- 登录与 `/api/screen/data` 之外没有 POST/PUT/PATCH/DELETE。

响应正文、凭据和真实参数 ID 不归档。逐路由和逐请求最小投影分别见 `../json/routes-59.json` 与 `../json/xian-drill.json`。
