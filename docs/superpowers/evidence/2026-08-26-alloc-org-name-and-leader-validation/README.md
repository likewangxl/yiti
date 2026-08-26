# 业绩调整机构名称展示验收证据

## 验收边界

- 页面：`http://127.0.0.1:8090/#/perf/adjust`
- 场景：业绩调整 -> 新建调整申请 -> 原业绩分配
- 浏览器：项目本地官方 `playwright-cli`，Firefox，视口 `1440x900`
- 数据说明：本次页面证据使用开发态路由 mock，仅验证前端展示逻辑，不代表后端、权限或数据库联调。

## Mock 输入

原业绩预览接口返回的单条数据同时包含机构号和机构名称：

```json
{
  "acctNo": "62220001",
  "username": "E001",
  "empChnName": "张三",
  "orgCode": "ORG-001",
  "orgName": "南山支行",
  "ratio": 100
}
```

接口请求：

```text
GET /api/report/alloc-preview?custType=CORP&custNo=CUST-PW-01&allocDim=RULE&statisDt=2026-08-25
HTTP 200
```

## 验收结果

页面对话框检查结果：

```json
{
  "title": true,
  "orgCell": "南山支行",
  "hasOrgName": true,
  "hasOrgCode": false
}
```

- 原业绩分配“所属机构”单元格只显示 `南山支行`。
- 对话框中未出现 `ORG-001`。
- 控制台：`Errors: 0, Warnings: 0`。
- 截图：[original-allocation-org-name-only.png](./original-allocation-org-name-only.png)
