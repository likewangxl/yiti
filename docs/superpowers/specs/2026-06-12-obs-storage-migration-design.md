# OBS 文件存储统一改造设计

- 日期：2026-06-12
- 状态：已批准设计，待写实现计划
- 范围：后端（lf 工作副本）。把全项目文件存储统一收敛到华为云 OBS，清除 MinIO 与半成品本地存储。

## 1. 背景与现状

项目文件存储当前处于"MinIO 还在 + 半截本地化"的混乱中间态：

| 子系统 | 当前存储 | 入口 |
|--------|----------|------|
| 文件管理 / 自由报表 / 通用下载 | **本地磁盘**（governance `FileService`，`file.storage.root`） | `FileApi.upload/getDownloadUrl/getFilePath/deleteFile` |
| 报表导出（CustPool/DynamicQuery 策略） | 走 `FileApi`（即本地磁盘） | `RptExportFacade.getDownloadUrl` |
| 绩效导出（Alloc/Detail/Kpi/Metric 策略） | **MinIO** `minioClient.putObject` 直连 | 下载 `fileApi.getDownloadUrl(fileKey)` |
| 公告附件 | **独立本地磁盘**（portal `AnnouncementService`，`announcement.upload-dir`） | controller `Files.copy` 流式 |
| 数据导入（PerfImport） | **不落存储**，收 `MultipartFile` 直接解析 | — |

遗留物：
- `governance/config/MinioConfig.java`（仅为绩效导出策略保留 `MinioClient` bean，懒连接）
- 未跟踪文件 `performance/service/export/storage/LocalExportFileStore.java`（只有 `write`，半成品）
- 未应用的 stash `stash@{0}: MinIO->local-storage refactor WIP 2026-05-27`
- 测试配置 mock 了 `MinioClient`：`bootstrap` 下 `TestMockConfig` / `FlowableE2ETestConfig` / `FlowableRealEnvTestConfig` / `LeadE2ETestConfig`

## 2. 目标

1. 全部文件读写统一走华为云 OBS（写=put，读=get），参照示例项目 `PdObsClient` 的工具类写法与调用习惯。
2. 彻底移除 MinIO（依赖、配置、bean、测试 mock）与半成品本地存储。
3. 数据导入新增"源文件归档到 OBS"。
4. 公告附件上传/下载迁到 OBS。
5. OBS SDK 版本**严格** `com.huaweicloud:esdk-obs-java-bundle:3.24.3`。
6. 无本地回退；dev/CI 靠 mock OBS 客户端跑测试。遵守 TDD 红-绿-重构。

## 3. 架构决策

- **方案 A**：复用现有 `FileApi`/`FileService`（governance）这个跨模块统一存储缝，把其后端从本地磁盘换成 OBS；所有写文件的子系统统一通过 `FileApi` 落 OBS。符合 CLAUDE.md "跨模块走 *Api" 规则，OBS 调用集中在 governance 一处。
- OBS 客户端**懒连接**（仿旧 `MinioClient` bean），启动不连、调用才连 → dev/CI 可正常启动，测试 mock。不加 `obs.enabled` 硬开关（无回退，OBS 是唯一实现）。
- 下载：基于"返回 URL"的流程（自由报表、报表导出、门户 Doc）→ OBS **预签名临时 URL**；基于流式端点的公告下载 → 后端 `getFileContent` 读 OBS 字节回传（保持其现有 `void`+`Files.copy` 端点契约，仅把数据源从本地换成 OBS）。

## 4. 详细设计

### 4.1 OBS 工具类（新增）

`com.bank.branch.platform.governance.storage.ObsStorageClient`（仿 `PdObsClient`）：

```
@Slf4j @Component
class ObsStorageClient {
    @Value("${obs.endPoint}")   endPoint
    @Value("${obs.accessKey}")  accessKey
    @Value("${obs.secretKey}")  secretKey
    @Value("${obs.bucketName}") bucketName
    ObsClient obsClient;                 // com.obs.services.ObsClient

    @PostConstruct init()  -> new ObsClient(accessKey, secretKey, endPoint)
    @PreDestroy   close()  -> obsClient.close()

    void   putObject(byte[] bytes, String key)            // 写
    void   putObject(InputStream in, String key, long len)
    byte[] getBytes(String key)                           // 读全部字节
    InputStream getResultInputStream(String key)          // 读流
    String getStrByKey(String key)                        // 读文本
    void   deleteByKey(String key)                        // 删
    String generatePresignedUrl(String key, long ttlSeconds) // createTemporarySignature(GET)
}
```

懒连接：`init()` 只构造客户端对象，不发网络请求（与旧 MinioClient 一致）。

### 4.2 FileService 改造（governance，核心缝）

`FileService` 现有方法语义保持，存储后端从 `Files.copy`/本地 Path 换为 OBS：

- objectKey 方案带**类型前缀**：`yyyy/MM/dd/{prefix}_{uuid}.ext`（见 §4.9），便于在 OBS 上按前缀区分文件类型；`obsStorageClient.putObject(bytes, objectKey)`；`FILE_OBJECT.storage_path` 列存 objectKey（语义变 OBS key，列不变）。
- 上传方法增加 `category`（前缀字符串）入参；`upload(MultipartFile, uploadedBy, bizType, bizId, category)` 与新增重载 `upload(byte[] bytes, String filename, String contentType, String uploadedBy, String category)`（给导出策略/导入跨模块用，免传 `MultipartFile`）。
- `getDownloadUrl(fileId)` → `obsStorageClient.generatePresignedUrl(objectKey, ttl)`。
- `getFilePath(fileId):Path`：OBS 无本地 Path → **废弃**。新增 `getFileContent(fileId):byte[]`（`obsStorageClient.getBytes(objectKey)`）替代。
- `deleteFile(fileId)` → `obsStorageClient.deleteByKey(objectKey)` + 删库。
- `getFileName`/`bindFile`/`listBizFiles`/`listAllFiles` 不变（纯元数据）。

### 4.3 FileApi 契约变更（governance）

- 新增 `FileObjectDTO upload(byte[] bytes, String filename, String contentType, String uploadedBy, String category)`。
- `upload(MultipartFile, uploadedBy)` 保留（默认 category，如 `wj`）；另提供带 `category` 的重载。
- 新增 `byte[] getFileContent(String fileId)`。
- 移除 `Path getFilePath(String fileId)`（迁移消费方后删除）。
- `getDownloadUrl/upload(MultipartFile..)/deleteFile/getFileName/bindFile/listBizFiles` 签名不变。

### 4.4 绩效 4 个导出策略（performance）

`AllocExportStrategy`/`DetailExportStrategy`/`KpiExportStrategy`/`MetricExportStrategy`：
- 删除 `MinioClient` 注入与 `minioClient.putObject(...)`。
- 改为 `FileObjectDTO dto = fileApi.upload(bytes, filename, contentType, operator); task.fileKey = dto.getFileId();`（与报表 CustPool/DynamicQuery 策略已有写法对齐）。
- 下载侧 `fileApi.getDownloadUrl(fileKey)` 自动变 OBS 预签名，无需改。

### 4.5 数据导入归档（performance）

`PerfImportServiceImpl.startImport`：
- `byte[] raw = file.getBytes();`（读一次）→ `FileObjectDTO dto = fileApi.upload(raw, file.getOriginalFilename(), file.getContentType(), operatorId);` → `batch.setSourceObjectKey(dto.getFileId());`
- 解析改用 `new ByteArrayInputStream(raw)` 包成 `MultipartFile`/`InputStream` 传给 `strategy.execute`，避免上传与解析重复消费同一个流。
- DB：`PERF_IMPORT_BATCH` 加列 `SOURCE_OBJECT_KEY VARCHAR(64) NULL`（Flyway 禁用 → 提供直执 SQL 脚本 `docs/superpowers/sql/2026-06-12-perf-import-batch-source-object-key.sql`）；`PerfImportBatch` 实体 + mapper insert/列同步加字段。

### 4.6 公告附件（portal）

`AnnouncementService.uploadFile`：
- 删本地 `announcement.upload-dir` 落盘逻辑；改 `fileApi.upload(file.getBytes(), name, contentType, operator)` → 把返回的 fileObjectId 存进 `AnnouncementFile.filePath`（列复用，存 FileApi fileId）。

`AnnouncementController.downloadFile`：
- 不再 `Paths.get(af.getFilePath())` + `Files.copy`；改 `byte[] data = fileApi.getFileContent(af.getFilePath());` 写入 `response.getOutputStream()`（保持现有流式 `void` 端点契约，仅数据源换 OBS）。

### 4.7 MinIO 清理

- 删 `governance/config/MinioConfig.java`。
- 删依赖：`system-governance-center/pom.xml` 与 `bootstrap/pom.xml`（test）的 `io.minio:minio`，root `pom.xml` 的 `minio.version` 属性。
- 删未跟踪 `performance/service/export/storage/LocalExportFileStore.java`，丢弃 stash `stash@{0}`。
- 测试配置 `TestMockConfig`/`FlowableE2ETestConfig`/`FlowableRealEnvTestConfig`/`LeadE2ETestConfig` 移除 `MinioClient` mock；如导出策略测试改为 mock `FileApi` 则不再需要 OBS bean mock。

### 4.8 依赖与配置

- root `pom.xml` `<dependencyManagement>` 加 `com.huaweicloud:esdk-obs-java-bundle:3.24.3`；`system-governance-center/pom.xml` 引入该依赖。
- `bootstrap/src/main/resources/application.yml` 加（先填 xanpd 占位，用户自行替换）：
  ```yaml
  obs:
    endPoint: obs.sh-dev.oshxccloud.spdbdev.com
    accessKey: QAU28T7BR57S2ENQDDNP
    secretKey: V87zaTAZM7NvESzL2dJB3HIVvAmf3xmYT5fkffSX
    bucketName: xanpd-kf-bucket-01
  ```

### 4.9 存储 key 类型前缀规约

objectKey = `yyyy/MM/dd/{prefix}_{uuid}.{ext}`，`{prefix}` 由各调用点传入：

| 文件类型 | prefix | 调用点 |
|----------|--------|--------|
| 自由报表上传 | `zybb` | `FreeReportServiceImpl.importExcel` |
| 数据导入源文件 | `sjdr` | `PerfImportServiceImpl.startImport` |
| 绩效 KPI 导出 | `jxkpi` | `KpiExportStrategy` |
| 绩效指标导出 | `jxzb` | `MetricExportStrategy` |
| 绩效分配导出 | `jxfp` | `AllocExportStrategy` |
| 绩效明细导出 | `jxmx` | `DetailExportStrategy` |
| 报表动态查询导出 | `bbdc` | `DynamicQueryExportStrategy` |
| 客户池汇总导出 | `khchz` | `CustPoolSummaryExportStrategy` |
| 公告附件 | `gg` | `AnnouncementService.uploadFile` |
| 其他/通用 | `wj` | `FileApi.upload(MultipartFile, uploadedBy)` 默认 |

前缀常量集中定义（如 governance `storage.FileCategory` 常量类），调用点引用，避免散落魔法串。除自由报表 `zybb` 为用户指定外，其余为本设计拟定，用户可调整。

## 5. 测试策略（TDD 红-绿-重构）

- `ObsStorageClientTest`：注入 mock `com.obs.services.ObsClient`，验证 put/getBytes/getResultInputStream/deleteByKey/generatePresignedUrl 的委托与参数（bucket、key）。
- `FileServiceTest`：mock `ObsStorageClient`，验证 `upload→putObject`、`getDownloadUrl→generatePresignedUrl`、`getFileContent→getBytes`、`deleteFile→deleteByKey`、storagePath 落 objectKey。
- 4 个绩效导出策略测试：由 mock `MinioClient` 改为 mock `FileApi.upload`，验证调用 + fileKey 落 dto.fileId。
- `PerfImportServiceImpl` 测试：验证 `fileApi.upload` 被调一次 + `sourceObjectKey` 落库 + 解析行数仍正确（流不被重复消费）。
- 公告：`AnnouncementService.uploadFile` 验证走 `fileApi.upload`；下载端点验证读 `fileApi.getFileContent`。
- 删除 MinIO 相关 mock 后，全量 `mvn clean install` 通过。

## 6. 改动模块清单

| 模块 | 改动 |
|------|------|
| system-governance-center | 新增 `ObsStorageClient`；改 `FileService`、`FileApi`/`FileFacade`；删 `MinioConfig`；pom |
| performance-engine-center | 4 导出策略；`PerfImportServiceImpl`；`PerfImportBatch` 实体+mapper；SQL 脚本；删 `LocalExportFileStore`；pom |
| report-analytics-center | 自由报表下载消费 `getFilePath→getFileContent`（核实 FreeReport 下载入口） |
| portal-content-center | `AnnouncementService` 上传、`AnnouncementController` 下载、Doc 下载（`getDownloadUrl` 已兼容） |
| bootstrap | 测试配置移除 MinIO mock；pom test 依赖 |
| 根 / 脚本 | root pom 依赖管理 + 删 minio.version；SQL 脚本 |

## 7. 风险与注意

- **流重复消费**：导入/公告需先 `getBytes()` 一次，再分别归档与解析，禁止对同一 `MultipartFile.getInputStream()` 读两次。
- **getFilePath 删除**：必须先迁移全部消费方（自由报表下载、公告下载）再删，避免编译断裂。
- **OBS 预签名 TTL**：给一个合理默认（如 600s），可配置。
- **既有 FILE_OBJECT 历史数据**：storage_path 旧值是本地相对路径，迁移后新值是 OBS key；历史文件不自动搬迁（如需可另起数据迁移，本期不含）。
- **凭证**：xanpd 占位仅为能编译/启动；真实下载/上传需用户替换为本项目 OBS 资源。
