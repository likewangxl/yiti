# 绩效导入「下载文件」改走 OBS 预签名 URL 直连 设计（spec，方案甲）

- 日期：2026-07-16
- 作者：刘杨
- 范围：`performance-engine-center`（下载端点/服务）+ `xanzc_frontend`（下载 api）
- 关联页面：数据导入页 `xanzc_frontend/src/views/perf/Import.vue` →「最近导入」操作栏「下载文件」

## 1. 需求

数据导入「最近导入」操作栏的「下载文件」，从**应用服务器透传字节流**改为**走 OBS 预签名 URL 直连下载**（减轻应用带宽、大文件更稳）。

## 2. 现状（已核实）

- 下载：`PerfImportController.downloadSourceFile`（`GET /api/perf/import/batches/{batchId}/source-file`）→ `PerfImportService.getSourceFile` 读出字节 → controller `response.getOutputStream().write(data)` **透传字节流**。
- 源文件**双模式存储**（按导入类型 `PerfImportServiceImpl.LOCAL_STORAGE_TYPES = {METRIC_DEF, KPI_SCHEME, TARGET_PLAN}`）：
  - 这 3 类 → **本地磁盘**（`localImportFileStorage`），`sourceObjectKey` = 相对路径 `yyyyMMdd/<uuid>.<ext>`。
  - 其余类型 → **OBS**（`fileApi.upload`），`sourceObjectKey` = `file_object` 主键。
- `FileApi.getDownloadUrl(fileId)` 已存在，返回 OBS 预签名 URL（governance 实现，1 小时有效）。

## 3. 方案甲（不动存储、不加 PT_RESOURCE）

**只把"OBS 存的类型"下载改成预签名 URL 直连；本地 3 类保持字节流透传（它们没 OBS URL）。**

### 3.1 后端

**Service** `PerfImportService`：新增
```java
/** 取源文件的 OBS 预签名下载 URL；本地存储类型(无 OBS 对象)返回 null，调用方回退字节流下载。 */
String getSourceFileDownloadUrl(String batchId);
```
`PerfImportServiceImpl.getSourceFileDownloadUrl`（复用 `getSourceFile` 的校验，逻辑一致）：
1. `getBatch(batchId)`；
2. 权限校验：`selfEmpId != null && !selfEmpId.equals(b.getCreatedBy())` → `IMPORT_BATCH_NO_PERMISSION`；
3. `objectKey` 空 → `IMPORT_BATCH_NO_SOURCE_FILE`；
4. `LOCAL_STORAGE_TYPES.contains(b.getImportType())` → 返回 `null`（本地文件无 OBS URL）；
5. 否则 → `return fileApi.getDownloadUrl(objectKey);`。

**Controller** `PerfImportController`：在**同一路径**加带 `params="asUrl"` 的新方法（原字节流方法保留不动）：
```java
@GetMapping(value = "/batches/{batchId}/source-file", params = "asUrl")
@Operation(summary = "获取导入源文件下载 URL（OBS 预签名；本地文件返回空 URL 由前端回退字节流）")
@BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXPORT)
@AuditLog(action = "PERF_IMPORT_DOWNLOAD_SOURCE", resourceType = "PERF_IMPORT_BATCH")
public ResponseWrapper<Map<String, String>> sourceFileUrl(@PathVariable("batchId") @NotBlank String batchId) {
    String url = perfImportService.getSourceFileDownloadUrl(batchId);
    return ResponseWrapper.success(Map.of("url", url != null ? url : ""));
}
```
- **同 URL 路径** → 命中**同一条 PT_RESOURCE**（ResourceMatcher 按路径匹配、忽略 query），**无需新建资源/跑 SQL**。
- `@BizAuth`/`@AuditLog` 与原字节流端点一致；权限校验在"签发 URL 时"生效。

### 3.2 前端 `api/perf.js downloadImportSourceFile(batchId, fileName)`

```js
export async function downloadImportSourceFile(batchId, fileName) {
  // 先取 OBS 预签名 URL；有则直连 OBS 下载，无则回退应用透传字节流(本地存储的 3 类)
  const r = await call('get', `/perf/import/batches/${batchId}/source-file`, { params: { asUrl: true } }, null);
  const url = r && r.url;
  if (url) { window.open(url, '_blank'); return; }
  // 回退：本地文件仍走字节流(现有逻辑)
  const blob = await call('get', `/perf/import/batches/${batchId}/source-file`, { responseType: 'blob' }, null);
  if (!blob) return;
  const data = blob instanceof Blob ? blob : new Blob([blob]);
  const objectUrl = URL.createObjectURL(data);
  const a = document.createElement('a');
  a.href = objectUrl; a.download = fileName || `import-${batchId}.xlsx`;
  document.body.appendChild(a); a.click();
  setTimeout(() => { URL.revokeObjectURL(objectUrl); a.remove(); }, 0);
}
```
- `Import.vue` 的按钮与 `onDownloadSource` 不改（`:disabled="!row.sourceObjectKey"` 逻辑保留）。

## 4. 结果

- OBS 存的类型（基础数据/分配/指标结果/KPI结果…）→ 下载走 **OBS 直连**（`window.open` 预签名 URL），不再经应用透传。
- 本地存的 3 类 → **回退字节流透传**（照旧）。
- 权限校验仍在签发 URL 时生效；审计保留（同 `PERF_IMPORT_DOWNLOAD_SOURCE`）。

## 5. 测试（TDD）

- **后端** `PerfImportServiceImplTest`（或新增用例）——`getSourceFileDownloadUrl`：
  - OBS 类型批次（如 `BASE_DATA`，`sourceObjectKey=file_object 主键`）→ 返回 `fileApi.getDownloadUrl` 的预签名 URL（mock 返回固定串）。
  - 本地类型批次（`METRIC_DEF`）→ 返回 `null`。
  - 非本人（`selfEmpId != createdBy`）→ 抛 `IMPORT_BATCH_NO_PERMISSION`。
  - `sourceObjectKey` 空 → 抛 `IMPORT_BATCH_NO_SOURCE_FILE`。
- **前端** vitest `perf.download.spec.js`：`downloadImportSourceFile`
  - URL 端点返回 `{url:'https://obs/...'}` → 调 `window.open('https://obs/...','_blank')`，**不**再请求 blob。
  - URL 端点返回 `{url:''}` → 回退请求 `source-file`（`responseType:'blob'`）。

## 6. 不做（YAGNI）

- 不改导入存储逻辑、不动本地 3 类、不迁移历史批次。
- 不新增 PT_RESOURCE / 不跑 SQL（复用同路径资源）。
- 不改「下载错误」（本地文本 Blob，与 OBS 无关）。

## 7. 风险

- OBS 预签名 URL 是否强制下载取决于 governance `getDownloadUrl` 是否带 `Content-Disposition: attachment`；若未带，`window.open` 可能在新标签预览而非下载（xlsx 一般浏览器不预览，会下载）。若发现预览问题，后续在 governance 侧或改用带 disposition 的签名参数处理（本次不改 governance）。
- 预签名 URL 有效期内（约 1 小时）任何持有者可下载——预签名标准特性，权限已在签发时校验，可接受。
