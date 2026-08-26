# 业绩调整用户主键与工号语义验收

## 验收边界

- 页面：`http://127.0.0.1:8090/#/perf/adjust`
- 场景：业绩调整 -> 新建调整申请 -> 分配明细员工反查 -> 提交审批
- 浏览器：项目本地官方 `xanzc_frontend/node_modules/.bin/playwright-cli`，Firefox，视口 `1440x900`
- 数据说明：本次使用开发态网络 route mock，只验证前端字段映射与请求组装，不代表后端、RBAC 或数据库联调。
- 本机配置了外部代理，CLI 命令显式清空代理并设置 `NO_PROXY=127.0.0.1,localhost`，确保访问当前 checkout 的 8090 端口。

## Mock 契约

员工反查接口返回同时包含平台用户主键和用户工号：

```json
{
  "empId": "2347",
  "username": "12038011",
  "empChnName": "刘鑫",
  "mainOrgCode": "131",
  "mainOrgName": "榆林分行府谷支行"
}
```

完整 route 清单见 [route-list.txt](./route-list.txt)。所有 route 都限定为 `http://127.0.0.1:8090/api/**`，未拦截前端源码和静态资源。

## 验收结果

- 页面员工号输入框显示 `12038011（刘鑫）`，截图见 [employee-number-display.png](./employee-number-display.png)。
- `POST /api/perf/alloc-adjust/create` 返回 HTTP 200。
- 请求体 `items[0].empId` 为平台用户主键 `2347`，未提交工号 `12038011`；原始请求体见 [create-request-body.json](./create-request-body.json)。
- 浏览器 console：Errors 0，Warnings 0；见 [console.txt](./console.txt)。
- 请求/响应摘要见 [requests.txt](./requests.txt) 与 [create-response-body.json](./create-response-body.json)。

## 关键命令

命令清单见 [commands.txt](./commands.txt)。最终证据来自干净会话 `alloc-user-id-final`，验收完成后已关闭。
