# 跨机构营销与云盾前端验收证据

验收日期：2026-08-19

## 环境与边界

- 前端：当前工作区 Vite 服务，`http://127.0.0.1:8090`
- 浏览器工具：官方 `playwright-cli`
- 本地后端登录接口使用 `admin/password` 返回 401；真实 SSO 跳转到 UAT 地址后返回 502。因此没有把本次页面检查描述为真实后端联调通过。
- 为验证受保护页面交互，当前用户、菜单和业务接口使用了 Playwright 显式路由模拟；静态模块请求保持由 Vite 正常加载。

## 跨机构营销

- `cross-org-page.png`：跨机构营销申请列表和待审批操作。
- `cross-org-approval-reason.png`：点击“通过”后出现审批意见弹窗，意见必填，最大长度 500。
- 提交请求：`POST /api/cross-org-marketing/APPLY-001/approve`
- 请求体：`{"reason":"同意联合营销申请"}`
- 模拟响应：HTTP 200；提交后重新加载申请列表。

## 云盾

- `yundun-accountability.png`：人员违规信息页面、查询区、导入导出和分页区域可正常加载。

## 浏览器结果

- Playwright 控制台检查：0 error。
- 页面验收使用网络模拟，只证明当前前端路由、渲染和请求契约；后端行为由对应 Java 测试覆盖。
