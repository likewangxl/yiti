# 信息聚合模块（前端）— 设计

> 日期：2026-06-17
> 范围：前端 wangyq（4 个页面）+ 必要时补 lf 后端缺口
> 平台：**仅 PC**（H5 不做）
> 参考：`wangyq/效果/` 5 张效果图

## 1. 背景与结论

新增「信息聚合」一级菜单，含 4 个子页：网址导航、分行通讯录、产品资料库、常用文档下载。

**后端已基本现成**（均在 `portal-content-center`，BizType 已有 NAV/ADDRBOOK/PRODUCT/DOC）：

| 子功能 | 已有接口 |
|---|---|
| 网址导航 | `GET /api/nav`（分组）；`POST/PUT/DELETE /api/admin/nav[/{id}]`；`PUT /api/admin/nav/sort` |
| 通讯录 | `GET /api/employees`（分页）；`GET /api/employees/search`；`GET/PUT /api/employees/{empId}` |
| 产品资料库 | `GET /api/products`（分页）；`/support-available`；`/export`；`GET/POST/PUT/DELETE /api/products[/{id}]` |
| 常用文档 | `GET /api/documents`（分页）；`GET /api/documents/{id}/download`（返回预签名URL）；`POST/PUT/DELETE /api/admin/documents[/{id}]` |

→ 本任务**以前端为主**：照效果图做 4 页 + 接已有 API + 加菜单/路由。前端现仅有半成品 `api/employees.js`。

## 2. 决策（已确认）

1. **文档分类**：`docCategory` 是自由文本、无分类管理接口 → 左侧分类树由文档列表**去重 docCategory 自动生成**（带计数）；上传时分类用「可选已有 + 可新输」下拉；**不做**独立「分类管理」弹窗。**零后端改动**。
2. **网址导航入口**：V1 只做左侧「信息聚合 > 网址导航」菜单页，不加工作台图标入口。
3. **后端缺口**：实现中若发现后端缺字段/接口，**我直接在 `/home/djdev/lf` 补**（前后端一起，TDD）。

## 3. 架构与组件

- 目录：`src/views/info/`，4 个页面组件：`NavHub.vue`、`AddressBook.vue`、`ProductLib.vue`、`DocCenter.vue`。
- API 层：新增 `src/api/nav.js`、`src/api/products.js`、`src/api/documents.js`；整理既有 `src/api/employees.js`（补 search/get/update + 通讯录页所需字段）。
- 菜单/路由：`src/router/index.js` 增「信息聚合」一级 + 4 子路由；左侧菜单同步（按效果图放在「客户营销」之上）。
- 复用：`call`/`unwrapPage`（http.js）、Element Plus、OBS 文件下载（documents 用后端预签名 URL，products 附件用 `/api/files` 上传 + 下载）。
- 统一口径（V1）：编辑/新增/删除按钮**前端常显**，真实执行权以后端 `@BizAuth` Action + DATA_SCOPE + 实体守卫为准；无权时后端返回 403/业务错，前端给提示（沿用 http.js 拦截器）。

## 4. 各页设计

### 4.1 网址导航 `NavHub.vue`（效果图5）
- 调 `GET /api/nav` → 分组（日常办公/业务系统/运营报表/外部站点…），每组卡片网格：图标(首字/icon) + 名称 + URL + 操作(编辑/禁用|启用/删除)。卡片点击在新标签打开 URL。
- 顶部：「排序模式」切换 + 「新增网址」。
- 新增/编辑弹窗：名称、URL、图标、分组。提交 `POST/PUT /api/admin/nav`。
- 排序模式：组内拖拽（复用现有拖拽方案或 SortableJS，若无则用上/下移按钮兜底），保存调 `PUT /api/admin/nav/sort`（批量 id+排序值）。
- 禁用/启用、删除：调对应 admin 接口。禁用项灰显 + 「已禁用」+「启」。

### 4.2 分行通讯录 `AddressBook.vue`（效果图2、4）
- 筛选：搜索(姓名/工号/联系方式)、组织节点(树选，来源组织 API)、岗位(字典下拉)、负责产品(下拉，来源 `/api/products/support-available`)、「仅显示60天未更新」勾选。
- 列表 `GET /api/employees`（分页）：姓名(首字头像)、工号、组织节点、岗位(tag)、联系方式(电话/邮箱)、负责产品(tag 多个)、更新时间(超60天显示「60天未更新」badge)、操作(编辑)。
- 编辑抽屉（右侧 drawer）：基本信息只读(工号/姓名/组织节点/岗位)；可编辑：电话、邮箱、负责产品(多选 checkbox，来源 support-available)、自我描述。顶部提示「勾选负责产品将自动反向更新产品资料库的产品负责人」。提交 `PUT /api/employees/{empId}`。
- 导出按钮（若后端有导出则接，否则前端导出当前页/全部）。
- 字段以后端 `EmployeeDetailDTO`/`EmployeeUpdateReqDTO` 为准，api 层适配。

### 4.3 产品资料库 `ProductLib.vue`（效果图3）
- 筛选：产品部门、状态、关键词、（仅中场支持？按 DTO 决定）。
- 列表 `GET /api/products`（分页）：产品部门、产品名称、产品说明、是否中场支持(tag)、产品负责人(多个逗号分隔)、状态(启用/禁用)、更新人、更新时间、操作(编辑/删除/详情/附件下载)。
- 新增/编辑弹窗：产品部门(新建默认本人组织节点)、产品名称、产品说明、附件(走 `/api/files` 上传取 fileObjectId)、是否支持中场支持、状态。**产品负责人不在此维护**（由通讯录「负责产品」反向关联，只读展示）。提交 `POST/PUT /api/products`。
- 删除：`DELETE /api/products/{id}`。
- 字段以 `ProductDTO`/`ProductCreateReqDTO`/`ProductUpdateReqDTO` 为准。

### 4.4 常用文档下载 `DocCenter.vue`（效果图1）
- 左侧分类树：从 `GET /api/documents` 结果按 `docCategory` 去重 + 计数生成（含「全部」）。点击分类前端过滤当前列表（或带 category 参数请求，按后端 query 支持决定）。
- 右侧列表：文档名称、分类(tag)、大小、更新人、更新时间、下载次数、操作(下载/编辑/删除)。
- 顶部：「上传文档」（标题、分类[可选已有+可新输]、文件[/api/files 上传取 fileObjectId]）→ `POST /api/admin/documents`；编辑 `PUT`；删除 `DELETE`。
- 下载：`GET /api/documents/{id}/download` 返回预签名 URL → 浏览器打开/另存。
- 不做独立「分类管理」弹窗（决策1）。

## 5. 测试

- 前端无测试框架 → 手工联调每页（列表/新增/编辑/删除/下载/排序/筛选）。
- 若补 lf 后端：按 TDD 先红后绿（沿用 perf 模块单测风格）。

## 6. 边界与风险

- 各页 DTO 字段需在实现时逐个核对后端真实 DTO（本 spec 未逐字段固化，api 层负责适配）。
- 组织树/岗位字典数据源：复用既有 `api/orgs.js` 等；若缺岗位字典接口，按现状（positionDesc 文本）展示。
- 拖拽排序库：若项目未装 SortableJS，先用「上移/下移」兜底，避免引入新依赖（实现时确认）。
- 权限：菜单可见性与按钮可见性 V1 常显；真实执行以后端为准。

## 7. 实施顺序（分阶段，每阶段可独立联调）

P0 脚手架（菜单+路由+目录+api 空壳） → P1 网址导航 → P2 通讯录 → P3 产品资料库 → P4 常用文档。
