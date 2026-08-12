# Round 3 控制台、请求与方法审计

本文件是各页面 CLI console 和 requests 输出的忠实选择性汇总。每次进入页面或打开关键弹窗前均执行 console --clear 与 requests --clear；保留所有受测大屏范围 API 行，省略静态资源和登录后自动工作台请求。

## 请求方法结论

| 范围 | 观察到的方法 | 结果 |
| --- | --- | --- |
| 一次前置登录（不纳入功能评价） | POST /api/auth/login => 200 | 唯一获准的非 GET |
| 设计器：屏列表、画布、范围/角色依赖 | GET | 全部 200 |
| 数据源：列表、筛选、编辑前读取依赖 | GET | 全部 200 |
| 命名机构组：列表、画像、有效角色读取 | GET | 全部 200 |
| 机构经营画像：列表、关键词筛选、复位 | GET | 全部 200 |

本轮受测大屏范围内没有 POST、PUT、PATCH 或 DELETE。没有出现意外写请求，因此未触发“立即停止并 FAIL”规则。

完整的受测 API 路径集合如下；重复的同路径 GET 是页面切换、筛选或复位造成，均为 200：

~~~text
GET /api/auth/permissions
GET /api/screen/admin/screens
GET /api/screen/admin/canvas/9104
GET /api/screen/admin/canvas/9105
GET /api/screen/admin/screens/9104/access-roles
GET /api/screen/admin/screens/9105/access-roles
GET /api/screen/admin/datasources
GET /api/perf/metrics?pageSize=100
GET /api/screen/admin/kpi-schemes
GET /api/admin/org-groups
GET /api/admin/roles/all?recordStatus=0
GET /api/admin/org-profiles
GET /api/admin/org-profiles?keyword=<URL 编码的本地筛选词>
~~~

CLI 实际输出的 URL 均以 http://127.0.0.1:8092/api/ 开头。前端显式 Vite proxy target 是 http://127.0.0.1:18082；本轮后端只有 18082 监听，且最终日志扫描未出现 [api fallback]。代理链路依据与限制见 round3-02-runtime-startup-health-and-proxy.raw.txt。

## 控制台摘要

所有受测大屏页面和关键对话框均没有 console error。最终 CLI console 输出为：

~~~text
Total messages: 0 (Errors: 0, Warnings: 0)
~~~

页面初次加载时有 13 条已知 legacy sidebar Vue Router warning。以下为 warning 的完整路径集合（堆栈行省略；无 error）：

~~~text
/customers/list
/customers/leads/new
/customers/leads/approval
/customers/tags
/customers/tags/approval
/customers/pool/available
/customers/pool/claimed
/customers/cross-org
/touches/mine
/touches/overview
/customers/transfer-log
/bizexec/loans
/bizexec/supports
~~~

另有新出现但非 error 的 Element Plus radio deprecated warning：

| 触发位置 | 数量 | 结果 |
| --- | ---: | --- |
| 设计器地图属性 | 5 | warning，0 errors |
| 数据源编辑弹窗 | 9 | warning，0 errors |
| 机构画像编辑弹窗 | 2 | warning，0 errors |

这些 warning 记录为 caveat，不在本轮只读回归中修复；没有与范围、地图、数据源、机构组或画像相关的新 console error。

## route-list

初始和最终官方 CLI 输出都完整为：

~~~text
No active routes
~~~

因此，本轮没有注册 mock route。

