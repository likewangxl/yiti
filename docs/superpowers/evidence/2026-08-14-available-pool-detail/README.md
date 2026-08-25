# 待认领线索池中文展示、详情与附件预览验收

日期：2026-08-14

检出：`/home/djdev/lijh/yiti`

前端：`http://127.0.0.1:8090/#/customers/pool/available`

Playwright CLI 会话：`available-pool-detail-20260814`

## 证据边界

- 当前开发环境的统一认证跳转到 `uias.uat.spdb.com` 后返回 502，且工作区没有可复用的认证状态或授权测试凭据。
- 因此本目录截图使用 Playwright `route` 注入的只读开发态数据，仅验证前端真实构建页面的渲染和交互，不冒充真实后端联调结果。
- 后端翻译与详情契约另由 `CustomerPoolServiceTest`、`CustomerPoolControllerTest`、`ClaimServiceTest` 共 15 项测试验证；真实运行服务完成 19 模块隔离构建，健康基线为未登录 `current-user` 返回 401、`/v3/api-docs` 返回 200。
- 验收过程未执行认领、修改、上传或任何数据库写入。

## 路由数据与断言

Playwright 会话从 `about:blank` 启动，在进入业务页面前注册以下只读路由：

- `/api/auth/current-user`、`/api/auth/my-menus`、`/api/auth/permissions`、`/api/notifications/unread-count`
- `/api/customer-pool`：同时返回英文码 `MANUFACTURING`、`CORPORATE` 和中文名 `制造业`、`企业客户`
- `/api/lead-approvals/LEAD-PUBLIC-001`：返回联系人、联系电话、经营授信、分配审批、标签和附件等录入字段
- `/api/sys/dicts/{INDUSTRY,CUSTOMER_TYPE,GROUP_TYPE,ENTERPRISE_TYPE,LEAD_SOURCE}/items`
- `/api/files/FILE-DEMO-1/download`：返回可预览 SVG 图片

验收结果：

1. 列表显示“制造业”“企业客户”“公司业务部”，没有展示英文行业码和客户类型码。
2. “详情”抽屉显示联系人、联系电话、行业、客户类型、集团/企业信息、开户/基石标记、线索来源、授信、客户说明、分配/审批、备注、标签和附件。
3. 图片附件同时提供“预览”和“下载”，点击预览后创建独立对话框并正确显示图片。
4. 最终控制台：0 errors、0 warnings。
5. 关键请求均为 200：`customer-pool`、`lead-approvals/LEAD-PUBLIC-001`、五类字典以及 `files/FILE-DEMO-1/download`。

## 截图

- `01-list-chinese.png`：待认领池列表中文展示
- `02-detail-all-fields.png`：详情基础信息与经营授信信息
- `02b-detail-tags-attachments.png`：详情分配审批、标签和附件区域
- `03-attachment-preview.png`：图片附件预览对话框
