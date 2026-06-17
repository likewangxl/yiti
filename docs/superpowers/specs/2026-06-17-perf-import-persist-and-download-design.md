# 数据导入记录落库（列表读 DB）+ 源文件下载 — 设计

> 日期：2026-06-17
> 作者：wangyq（前端）+ lf 后端（本次由我方一并改 lf 后端）
> 模块：performance-engine-center（后端）/ xanzc_frontend（前端）

## 1. 背景与问题

数据导入页（`perf/Import.vue`）「最近导入」列表当前**只读浏览器 localStorage**（`api/perf.js` 的 `listImports()` → `loadLocalImports()`），换浏览器/清缓存即丢失，且无法跨终端查看。

实际后端**早已把每次导入落库**到 `perf_import_batch`（`PerfImportServiceImpl.startImport` 内 `batchMapper.insert(batch)`），字段含 `id / batch_no / import_type / file_name / status / total_rows / success_rows / error_rows / updated_rows / source_object_key / created_by / created_time / updated_time / remark`，但：

- **缺少列表查询接口**（控制器只有 upload / 详情 / errors / retry / delete）。
- 源文件仅在 `archiveSource=true`（「立即上传」）时归档到 OBS 并记 `source_object_key`；`archiveSource=false`（「上传并导入」）不归档、无文件可下。
- `source_object_key` 未暴露在 `PerfImportBatchRespDTO`，前端拿不到。

通用文件下载端点 `GET /api/files/{fileId}/download` 已存在（`FileController` → `FileService.getFileContent` 从 OBS 流式返回），`fileId` 即 `source_object_key`。

## 2. 目标

1. 导入记录列表改为**从 DB 查询**（替换 localStorage），应用**统一 DATA_SCOPE**（管理员全见 / 普通用户仅自己）、服务端分页、按时间倒序、**不分导入类型**。
2. 源文件**一律归档到 OBS**（强制归档），保证每条新记录可下载。
3. 操作列新增「**下载文件**」按钮，从 OBS 下载源文件；旧数据 `source_object_key` 为空时按钮**置灰并提示"无源文件"**。

非目标（YAGNI）：异步导入、MD5 去重、按类型/状态筛选、跨用户查看、修改 OBS 客户端与归档前缀（沿用 `FileCategory.PERF_IMPORT` = `sjdr`）。

## 3. 详细设计

### 3.1 后端（performance-engine-center）

**(A) 强制归档** — `PerfImportServiceImpl.startImport`
- 删除 `archiveSource` 分支判断，**无条件**执行 `fileApi.upload(file, operatorId, FileCategory.PERF_IMPORT)` 并写 `sourceObjectKey`。
- `archiveSource` 入参保留以兼容现有签名/调用，但实现内忽略（始终归档）；控制器 `upload` 的该参数后续可不传。

**(B) DTO 暴露** — `PerfImportBatchRespDTO`
- 新增字段 `String sourceObjectKey`，`getBatchDto` 装配时填充 `b.getSourceObjectKey()`。

**(C) 列表接口** — `GET /api/perf/import/batches`
- Query 参：`pageNo`（默认 1）、`pageSize`（默认 10，最大 100）。**无 importType 过滤**。
- **数据范围（DATA_SCOPE）**：复用 perf 现有先例 `PerfRunTaskService.resolveScopeFilter` 同款逻辑——
  `DataScopeType scope = bizScopeApi.resolveScope(empId, BizType.PERF_CONFIG)`：
  - `scope == ALL`（管理员/全局）→ **不加** `created_by` 约束（全见）；
  - 其他 scope → 加 `created_by = empId`（仅见自己；`empId` 取自认证 ThreadLocal，可信，禁止用户入参拼入）。
- 固定过滤：`status <> 'DELETED'`；排序 `created_time desc`。
- 返回统一分页结构（与项目 `ResponseWrapper` + 分页约定一致），`records` 为 `PerfImportBatchRespDTO`。
- 数据访问：MyBatis-Plus `LambdaQueryWrapper` + `Page<PerfImportBatch>`（符合 2026-06-10 MBP 红线）；
  数据范围用 `.eq(scope != ALL, PerfImportBatch::getCreatedBy, empId)` 条件式拼接，**不**走 `${dataScopeFilter}` 字符串片段。
  - 前置：`PerfImportBatchMapper` 若未 `extends BaseMapper<PerfImportBatch>` 则补上；确认 performance 模块 `MybatisPlusConfig` 已注册分页插件（无则在实现计划中加 `PaginationInnerInterceptor`）。
- 鉴权：`@BizAuth(bizType = PERF_CONFIG, action = QUERY)`，登记 `PT_RESOURCE`（SQL 直接执行，不走 Flyway）。
- Service 新增 `IPage<PerfImportBatchRespDTO> pageBatches(int pageNo, int pageSize)`（内部解析 empId + scope）。

**(D) 源文件下载接口** — `GET /api/perf/import/batches/{batchId}/source-file`
- 取批次：不存在 → `IMPORT_BATCH_NOT_FOUND`。
- **数据范围校验**：同列表口径——`scope == ALL` 放行任意批次；其他 scope 要求 `created_by == empId`，否则越权拒绝（权限错误码）。
- `source_object_key` 为空 → 返回明确错误（前端按钮已置灰，双保险）。
- 经 `FileApi.getFileContent(objectKey)` 取字节 + `getFileName` 取原名，`Content-Disposition: attachment` 流式返回（与 `FileController.downloadFile` 同款写法）。
- 放在 perf 模块端点（不直接暴露 `/api/files/{id}`），保证 PERF 鉴权/审计一致；`@BizAuth(PERF_CONFIG, EXPORT)`。

### 3.2 前端（xanzc_frontend）

**(E) `api/perf.js`**
- `listImports({ pageNo, pageSize })` 改调 `GET /api/perf/import/batches`，返回 `{ records, total }`；**删除** localStorage 相关（`loadLocalImports/pushLocalImport/patchLocalImport/removeLocalImport`、上传后写本地、`time: fmtDateTime(...)` 那段）。
- 新增 `downloadImportSourceFile(batchId)`：GET blob → 触发浏览器下载（带后端 `Content-Disposition` 文件名）。
- `uploadImportFile`：不再写 localStorage；`archiveSource` 参数可移除（后端强制归档）。

**(F) `perf/Import.vue`**
- `reload()` 调分页接口，`rows`/`total` 来自服务端；分页改**服务端分页**（`pgNo/pgSize` 变化触发 `reload`，`:total="total"`）。
- 字段映射：`batchId←id`、`type←importType`、`file←fileName`、`uploader←createdBy`、`valid/total←successRows/totalRows`、`status`、`time←createdTime`、`sourceObjectKey`。
- 操作列新增「下载文件」按钮：`:disabled="!row.sourceObjectKey"`，无源文件时 `title="无源文件"`；点击 `downloadImportSourceFile(row.batchId)`。
- 删除"仅展示本浏览器最近 50 条"提示语。
- 上传成功后 `reload()`（DB 已有行，无需 setTimeout 拉本地）。

### 3.3 数据流

```
上传 → startImport: fileApi.upload→OBS(记 sourceObjectKey) → insert perf_import_batch → strategy.execute 解析入库
列表 → GET /batches?pageNo&pageSize → 仅当前用户分页 → 表格
下载 → GET /batches/{id}/source-file → 校验归属 → FileApi.getFileContent(OBS) → 浏览器另存
```

## 4. 边界与错误处理

- 旧批次 `source_object_key` 为空：前端按钮置灰提示"无源文件"；后端下载接口也对空值返回明确错误。
- 越权下载他人批次：后端校验 `created_by` 拒绝。
- 分页越界/空结果：返回空 `records`，前端显示"暂无导入记录"。
- OBS 不可用：下载接口透传底层异常为统一错误响应（不吞）。

## 5. 测试（TDD，先红后绿）

后端（performance 模块单测/IT）：
1. `pageBatches` 数据范围：普通用户(scope≠ALL)只返回自己的；管理员(scope==ALL)返回全部；均排除 DELETED、按 `created_time desc`、分页正确。
2. `startImport` 强制归档：`archiveSource=false` 时仍写 `sourceObjectKey`（mock `fileApi.upload`）。
3. 下载接口：普通用户下载他人批次 → 拒绝；管理员下载他人批次 → 放行；`source_object_key` 为空 → 明确错误；正常 → 流式字节 + 文件名。
4. `PerfImportBatchRespDTO`/`getBatchDto` 含 `sourceObjectKey`。

前端：无测试框架，手工联调（列表来自 DB、下载得到正确文件、旧数据按钮置灰）。

## 6. 影响与回滚

- 影响面：performance 导入相关 3 处后端 + 2 处前端；不动通用文件/OBS/导出。
- 回滚：前端 `listImports` 可临时切回 localStorage；后端新接口为新增，删除即回滚；强制归档改动可还原 `archiveSource` 分支。

## 7. 部署

- 增量 SQL：`PT_RESOURCE` 新增列表/下载两个资源登记（直接执行，禁 Flyway）。
- 无表结构变更（`source_object_key` 列已存在）。
- 后端重打包需 `clean`（fat jar 内嵌依赖，见 lf 打包约定）。
