# 信息聚合模块（前端）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: subagent-driven-development 或 executing-plans，逐任务实现。步骤用 `- [ ]` 跟踪。

**Goal:** 在 wangyq 前端新增「信息聚合」一级菜单，照效果图做网址导航/通讯录/产品资料库/常用文档 4 个 PC 页面，接 portal-content-center 既有 API。

**Architecture:** 纯前端为主（后端 CRUD 现成）；api 层封装 `nav/products/documents/employees`，页面用 Element Plus，路由+菜单接入。后端缺口（文档大小/下载次数/更新人、产品负责人姓名）由我在 lf 补，TDD。

**Tech Stack:** Vue3 + Element Plus + axios（`call`/`http.js`）；后端 Spring Boot（portal-content-center）。

---

## 后端 API 契约（已确认）

- **网址导航**：`GET /api/nav` → `{groups:[{category, navs:[{id,navName,navUrl,navIcon,navCategory,sortOrder,status}]}]}`；`POST /api/admin/nav`(NavCreateReqDTO: navName/navUrl/navIcon/navCategory/sortOrder)；`PUT /api/admin/nav/{id}`(NavUpdateReqDTO +status)；`DELETE /api/admin/nav/{id}`；`PUT /api/admin/nav/sort`(List<{id,sortOrder}>)。
- **通讯录**：`GET /api/employees`(EmployeeQueryReqDTO: keyword/orgCode/position/status/pageNo/pageSize) → PageResult<EmployeeDetailDTO>(empId/empName/mobile/email/orgCode/orgName/position/positionDesc/selfDesc/responsibleProductIds/responsibleProducts[{id,productCode,productName,productCategory}]/status/updatedTime/canEdit)；`GET /api/employees/search?keyword=` → List<EmployeeSearchDTO>；`GET /api/employees/{empId}`；`PUT /api/employees/{empId}`(EmployeeUpdateReqDTO: mobile/email/position/selfDesc/responsibleProductIds)。
- **产品资料库**：`GET /api/products`(ProductQueryReqDTO: keyword/category/productDeptOrgCode/supportForSupportRequest/status/pageNo/pageSize) → PageResult<ProductDTO>(id/productCode/productName/productCategory/productCategoryDesc/description/supportForSupportRequest/productDeptOrgCode/productDeptOrgName/fileObjectId/responsibleEmpIds/status/createdTime/updatedTime)；`GET /api/products/support-available` → List<ProductSimpleDTO>(id/productCode/productName/productCategory/productDeptOrgCode)；`GET /api/products/export`；`GET/POST/PUT/DELETE /api/products[/{id}]`。
- **常用文档**：`GET /api/documents?keyword&category&status&pageNo&pageSize` → PageResult<DocumentDTO>(id/docTitle/docCategory/docCategoryDesc/fileObjectId/fileName/status/updatedTime)；`GET /api/documents/{id}/download` → 预签名 URL(String)；`POST /api/admin/documents`(DocumentCreateReqDTO: docTitle/docCategory/fileObjectId)；`PUT /api/admin/documents/{id}`(+status)；`DELETE /api/admin/documents/{id}`。
- **通用**：文件上传 `POST /api/files/upload`(multipart file) → FileObjectDTO{id,...}；组织树 `src/api/orgs.js`；http.js 分页响应自动取 `body.page`（`{records,total,pageNo,pageSize}`）。

## File Structure

- `src/api/nav.js`（新）— 网址导航 5 接口
- `src/api/products.js`（新）— 产品 CRUD + support-available
- `src/api/documents.js`（新）— 文档 CRUD + 下载 + 分类树派生
- `src/api/employees.js`（改）— 补 search/get/update + 通讯录页字段（保留现有 dynamic-query 用法）
- `src/views/info/NavHub.vue` / `AddressBook.vue` / `ProductLib.vue` / `DocCenter.vue`（新）
- `src/router/index.js`（改）— 加「信息聚合」一级 + 4 子路由 + 左侧菜单项
- 后端缺口（lf，可选增强）：`DocumentDTO` 补 `fileSize/downloadCount/updatedBy/updatedByName`；`ProductDTO` 补 `responsibleEmpNames`

---

## Task 0：脚手架（菜单 + 路由 + api 空壳）

**Files:** Modify `src/router/index.js`；Create `src/views/info/{NavHub,AddressBook,ProductLib,DocCenter}.vue`（占位）、`src/api/{nav,products,documents}.js`（空导出）。

- [ ] Step 1：读 `src/router/index.js` 现有结构，确认菜单/路由声明方式（meta.title/icon、children、菜单分组）。
- [ ] Step 2：新建 4 个占位页（`<template><div>页名</div></template>` + `<script setup>`）。
- [ ] Step 3：在路由加「信息聚合」分组（放「客户营销」之上），4 子路由：`/info/nav`、`/info/address-book`、`/info/products`、`/info/documents`，title=网址导航/通讯录/产品资料库/常用文档。
- [ ] Step 4：建 `api/nav.js`、`api/products.js`、`api/documents.js`，先 `import { call } from './http'` + 空函数占位。
- [ ] Step 5：`npm run build`（后台）验证通过；浏览器 8090 看到菜单 4 项可点开占位页。
- [ ] Step 6：commit `feat(info): 信息聚合脚手架(菜单+路由+占位页)`。

---

## Task 1：网址导航 NavHub.vue

**Files:** `src/api/nav.js`、`src/views/info/NavHub.vue`。

- [ ] Step 1：`api/nav.js` 实现：
```js
import { call } from './http';
export const listNav = () => call('get', '/nav', {}, { groups: [] });
export const createNav = (data) => call('post', '/admin/nav', { data }, { ok: true });
export const updateNav = (id, data) => call('put', `/admin/nav/${id}`, { data }, { ok: true });
export const deleteNav = (id) => call('delete', `/admin/nav/${id}`, {}, { ok: true });
export const sortNav = (items) => call('put', '/admin/nav/sort', { data: items }, { ok: true });
```
- [ ] Step 2：NavHub.vue 按效果图5：顶部标题 + 「排序模式」开关 + 「新增网址」；`listNav()` 渲染分组卡片网格（每卡 icon 首字/navIcon + navName + navUrl + 操作 编辑/禁用启用/删除）；禁用项(status!=ACTIVE)灰显 + 「已禁用/启」。
- [ ] Step 3：新增/编辑用 el-dialog（navName/navUrl/navIcon/navCategory），提交 create/update 后 `listNav()` 刷新。删除 el-popconfirm。禁用/启用调 updateNav 改 status。
- [ ] Step 4：排序模式：开关打开后组内可拖拽（优先 SortableJS；若 package.json 无则用「上移/下移」按钮兜底——Step 4a 先查依赖），点「保存排序」调 `sortNav` 传 [{id,sortOrder}]。
- [ ] Step 5：`npm run build` 验证；8090 联调（需登录 admin）。
- [ ] Step 6：commit `feat(info): 网址导航页`。

---

## Task 2：分行通讯录 AddressBook.vue

**Files:** `src/api/employees.js`（改，保留现有 listEmployees 的 dynamic-query 形态，新增下列）、`src/views/info/AddressBook.vue`。

- [ ] Step 1：`employees.js` 新增（不破坏现有 `listEmployees/getEmployee` 的 toFront 形态——通讯录页用独立函数取原始 DTO）：
```js
export const pageEmployees = (params) => call('get', '/employees', { params }, { records: [], total: 0 });
export const searchEmployees = (keyword) => call('get', '/employees/search', { params: { keyword } }, []);
export const updateEmployee = (empId, data) => call('put', `/employees/${empId}`, { data }, { ok: true });
```
- [ ] Step 2：AddressBook.vue 按效果图2：筛选行(搜索 keyword、组织节点 orgCode[树选，用 orgs.js]、岗位 position、负责产品[support-available]、「仅显示60天未更新」前端过滤)；表格列 姓名(首字头像)/工号/组织节点/岗位tag/联系方式(mobile+email)/负责产品(responsibleProducts→tag)/更新时间(updatedTime, >60天显示「60天未更新」badge)/操作(编辑 v-if canEdit 常显)。`pageEmployees` 分页。
- [ ] Step 3：编辑抽屉(el-drawer，效果图4)：基本信息只读(empId/empName/orgName/positionDesc)；可编辑 mobile/email/position/负责产品(多选 checkbox，来源 support-available)/selfDesc；顶部 el-alert「勾选负责产品将自动反向更新产品资料库的产品负责人」。保存 `updateEmployee(empId,{mobile,email,position,selfDesc,responsibleProductIds})` → 刷新。
- [ ] Step 4：导出按钮（前端导出当前结果为 xlsx，复用项目已有 xlsx 用法；无则后端 export 缺，暂隐藏）。
- [ ] Step 5：`npm run build` + 8090 联调。
- [ ] Step 6：commit `feat(info): 分行通讯录页`。

---

## Task 3：产品资料库 ProductLib.vue（含后端补负责人姓名）

**Files:** `src/api/products.js`、`src/views/info/ProductLib.vue`；（lf）`ProductDTO` + 转换器补 `responsibleEmpNames`。

- [ ] Step 1（lf 后端，TDD）：`ProductDTO` 加 `String responsibleEmpNames`（逗号分隔），在 Product→DTO 转换处用通讯录/用户 API 解析 empId→姓名填充；先写/改服务单测断言含姓名，再实现；`mvn -pl portal-content-center test` 绿。
- [ ] Step 2：`api/products.js`：
```js
import { call } from './http';
export const listProducts = (params) => call('get', '/products', { params }, { records: [], total: 0 });
export const supportAvailable = () => call('get', '/products/support-available', {}, []);
export const getProduct = (id) => call('get', `/products/${id}`, {}, {});
export const createProduct = (data) => call('post', '/products', { data }, { ok: true });
export const updateProduct = (id, data) => call('put', `/products/${id}`, { data }, { ok: true });
export const deleteProduct = (id) => call('delete', `/products/${id}`, {}, { ok: true });
```
- [ ] Step 3：ProductLib.vue 按效果图3：筛选(keyword/category/productDeptOrgCode/supportForSupportRequest/status) + 表格(产品部门 productDeptOrgName/产品名称/说明/中场支持 tag/产品负责人 responsibleEmpNames/状态 tag/更新时间/操作 编辑·删除·附件下载)。`listProducts` 分页。
- [ ] Step 4：新增/编辑 el-dialog：productName/productCategory/description/supportForSupportRequest/状态/附件(走 `/api/files/upload` 取 fileObjectId)；productDeptOrgCode 新建默认本人组织(从 user store)；**产品负责人只读**(反向来自通讯录)。提交 create/update。删除 popconfirm。
- [ ] Step 5：附件下载：有 fileObjectId 则 `GET /api/files/{fileObjectId}/download`。
- [ ] Step 6：`npm run build` + 联调；commit（前端）`feat(info): 产品资料库页` + （lf）`feat(portal): ProductDTO 补负责人姓名`。

---

## Task 4：常用文档 DocCenter.vue（含后端补 大小/下载次数/更新人）

**Files:** `src/api/documents.js`、`src/views/info/DocCenter.vue`；（lf，可选增强）`DocumentDTO` 补 `fileSize/downloadCount/updatedBy(Name)`。

- [ ] Step 1（lf 后端，TDD，可选）：`DocumentDTO` 补 `Long fileSize`、`Integer downloadCount`、`String updatedByName`；下载接口对 downloadCount +1；转换器填充 fileSize(来自 FILE_OBJECT)/updatedByName。先改测试再实现，`mvn -pl portal-content-center test` 绿。【若暂不补，前端这些列显示 '-'】
- [ ] Step 2：`api/documents.js`：
```js
import { call } from './http';
export const listDocuments = (params) => call('get', '/documents', { params }, { records: [], total: 0 });
export const downloadDocument = (id) => call('get', `/documents/${id}/download`, {}, null); // 返回预签名URL
export const createDocument = (data) => call('post', '/admin/documents', { data }, { ok: true });
export const updateDocument = (id, data) => call('put', `/admin/documents/${id}`, { data }, { ok: true });
export const deleteDocument = (id) => call('delete', `/admin/documents/${id}`, {}, { ok: true });
```
- [ ] Step 3：DocCenter.vue 按效果图1：左侧分类树(「全部」+ 各 docCategory 去重+计数，从列表派生)；右侧表格(docTitle/分类 tag/大小/更新人/更新时间/下载次数/操作 下载·编辑·删除)。点分类前端过滤(或带 category 请求)。
- [ ] Step 4：上传文档 el-dialog：docTitle、docCategory(可选已有+可新输 el-select allow-create filterable)、文件(`/api/files/upload` 取 fileObjectId) → `createDocument`。编辑 update；删除 popconfirm。
- [ ] Step 5：下载：`downloadDocument(id)` 拿预签名 URL → `window.open(url)` 或 a.click 另存。
- [ ] Step 6：`npm run build` + 联调；commit `feat(info): 常用文档下载页`。

---

## 验证与收尾

- [ ] 每页手工联调：列表/筛选/新增/编辑/删除/下载/排序（需 admin 登录、lf 18080 在跑、vite 代理 18080）。
- [ ] 全量 `npm run build` 通过。
- [ ] 若动了 lf 后端：`mvn -pl portal-content-center test` 绿 + 重打 fat jar 重启 18080（`--server.port=18080`）+ PT_RESOURCE 若需新资源则补 SQL。
- [ ] push（前端 wangyq、后端 lf 分别 commit/rebase/push，按既有约定；不提交 vite.config 本地改动）。

## 备注（后端缺口汇总）
1. ProductDTO 无负责人姓名（只有 empId）→ Task3 Step1 补。
2. DocumentDTO 无 大小/下载次数/更新人 → Task4 Step1 补（可选；不补则前端显 '-'）。
3. 通讯录导出、岗位字典：若后端无对应接口，前端降级（前端导出 / positionDesc 文本展示）。
