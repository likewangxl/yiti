# OBS 文件存储统一改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把全项目文件存储（自由报表、绩效导出、数据导入、公告附件、通用文件管理）统一收敛到华为云 OBS，移除 MinIO 与半成品本地存储。

**Architecture:** 复用 governance `FileApi`/`FileService` 这个跨模块存储缝，把其后端从本地磁盘换成 OBS（新增 `ObsStorageClient` 工具类，仿参考 `PdObsClient`）。所有写文件子系统统一走 `FileApi`。OBS 客户端懒连接，dev/CI 靠 mock 跑测试。

**Tech Stack:** Spring Boot 3.2.3 / JDK17 / 华为云 OBS SDK `com.huaweicloud:esdk-obs-java-bundle:3.24.3` / MyBatis-Plus / EasyExcel / JUnit5 + Mockito。

**Spec:** `docs/superpowers/specs/2026-06-12-obs-storage-migration-design.md`

**全局约束：** 遵守 TDD 红-绿-重构；Flyway 禁用（schema 走直执 SQL）；跨模块只走 `*Api`；子代理须为高能力模型。

---

## 文件结构地图

**新增**
- `system-governance-center/.../governance/storage/ObsStorageClient.java` — OBS 读写工具类（put/get/presigned/delete）
- `system-governance-center/.../governance/storage/FileCategory.java` — 类型前缀常量
- `docs/superpowers/sql/2026-06-12-perf-import-batch-source-object-key.sql` — 加列脚本
- 各新增类的测试

**修改**
- governance：`FileService.java`（后端换 OBS）、`FileApi.java` + `FileFacade.java`（加 `upload(byte[]..)`/`getFileContent`，删 `getFilePath`）、`config/MinioConfig.java`（删）、`pom.xml`
- governance：`FileController`（getFilePath→getFileContent 流式）
- performance：`AllocExportStrategy`/`DetailExportStrategy`/`KpiExportStrategy`/`MetricExportStrategy`（MinIO→FileApi）、`PerfImportServiceImpl`、`entity/PerfImportBatch`、`pom.xml`、对应测试
- report：`DynamicQueryExportStrategy`/`CustPoolSummaryExportStrategy`（传 category）、`FreeReport` 下载入口（getFilePath→getFileContent）
- portal：`AnnouncementService.uploadFile`（→FileApi）、`AnnouncementController.downloadFile`（→getFileContent）
- bootstrap：`TestMockConfig`/`FlowableE2ETestConfig`/`FlowableRealEnvTestConfig`/`LeadE2ETestConfig`（删 MinioClient mock）、`pom.xml`（删 minio test 依赖）
- root `pom.xml`（依赖管理加 OBS、删 `minio.version`）
- `bootstrap/.../application.yml`（加 `obs.*`）
- 删未跟踪 `performance/.../export/storage/LocalExportFileStore.java`；丢弃 stash@{0}

---

## Task 0：依赖与清理准备

**Files:**
- Modify: `pom.xml`（root）, `system-governance-center/pom.xml`, `bootstrap/pom.xml`, `performance-engine-center/pom.xml`
- Modify: `bootstrap/src/main/resources/application.yml`
- Delete: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/export/storage/LocalExportFileStore.java`

- [ ] **Step 1: root pom 依赖管理加 OBS、删 minio.version**

root `pom.xml`：`<properties>` 删 `<minio.version>8.5.7</minio.version>`；`<dependencyManagement><dependencies>` 加：

```xml
<dependency>
    <groupId>com.huaweicloud</groupId>
    <artifactId>esdk-obs-java-bundle</artifactId>
    <version>3.24.3</version>
</dependency>
```

- [ ] **Step 2: governance pom 引入 OBS、删 minio**

`system-governance-center/pom.xml`：删 `io.minio:minio` 依赖块；加：

```xml
<dependency>
    <groupId>com.huaweicloud</groupId>
    <artifactId>esdk-obs-java-bundle</artifactId>
</dependency>
```

- [ ] **Step 3: 删 bootstrap/performance 的 minio 依赖**

`bootstrap/pom.xml` 删 `io.minio:minio`（test scope）块；`performance-engine-center/pom.xml` 如有 `io.minio:minio` 一并删（stash 里曾加 test 依赖，确认当前文件无残留）。

- [ ] **Step 4: application.yml 加 obs 配置（xanpd 占位）**

`bootstrap/src/main/resources/application.yml` 顶层加（删除遗留 `minio:` 配置块如有）：

```yaml
obs:
  endPoint: obs.sh-dev.oshxccloud.spdbdev.com
  accessKey: QAU28T7BR57S2ENQDDNP
  secretKey: V87zaTAZM7NvESzL2dJB3HIVvAmf3xmYT5fkffSX
  bucketName: xanpd-kf-bucket-01
  presignExpireSeconds: 600
```

- [ ] **Step 5: 删半成品本地存储类 + 丢弃 stash**

```bash
rm -f performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/export/storage/LocalExportFileStore.java
git stash drop stash@{0}   # MinIO->local-storage refactor WIP（已被本方案取代）
```

- [ ] **Step 6: 编译占位通过**（OBS 依赖能拉取）

Run: `mvn -q -pl system-governance-center -am dependency:resolve 2>&1 | tail -5`
Expected: 无 `Could not resolve` 报错。

- [ ] **Step 7: Commit**

```bash
git add pom.xml system-governance-center/pom.xml bootstrap/pom.xml performance-engine-center/pom.xml bootstrap/src/main/resources/application.yml
git commit -m "chore(storage): 引入华为云 OBS SDK 3.24.3 + obs 配置，移除 minio 依赖与半成品本地存储"
```

---

## Task 1：FileCategory 前缀常量

**Files:**
- Create: `system-governance-center/src/main/java/com/bank/branch/platform/governance/storage/FileCategory.java`

- [ ] **Step 1: 写常量类**

```java
package com.bank.branch.platform.governance.storage;

/** 文件存储 key 类型前缀：OBS 对象名形如 {yyyy/MM/dd}/{prefix}_{uuid}.{ext}，便于按前缀区分文件类型。 */
public final class FileCategory {
    private FileCategory() {}

    public static final String FREE_REPORT      = "zybb";  // 自由报表上传
    public static final String PERF_IMPORT      = "sjdr";  // 数据导入源文件
    public static final String EXPORT_KPI       = "jxkpi"; // 绩效 KPI 导出
    public static final String EXPORT_METRIC    = "jxzb";  // 绩效指标导出
    public static final String EXPORT_ALLOC     = "jxfp";  // 绩效分配导出
    public static final String EXPORT_DETAIL    = "jxmx";  // 绩效明细导出
    public static final String EXPORT_DYNAMIC   = "bbdc";  // 报表动态查询导出
    public static final String EXPORT_CUSTPOOL  = "khchz"; // 客户池汇总导出
    public static final String ANNOUNCEMENT     = "gg";    // 公告附件
    public static final String GENERAL          = "wj";    // 通用/默认
}
```

- [ ] **Step 2: Commit**

```bash
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/storage/FileCategory.java
git commit -m "feat(storage): 新增 FileCategory OBS key 类型前缀常量"
```

---

## Task 2：ObsStorageClient 工具类（TDD）

**Files:**
- Create: `system-governance-center/src/main/java/com/bank/branch/platform/governance/storage/ObsStorageClient.java`
- Test: `system-governance-center/src/test/java/com/bank/branch/platform/governance/storage/ObsStorageClientTest.java`

OBS SDK 关键 API（3.24.3）：`new ObsClient(ak, sk, endPoint)`；`obsClient.putObject(bucket, key, InputStream)`；`obsClient.getObject(bucket, key).getObjectContent()`；`obsClient.deleteObject(bucket, key)`；预签名 `new TemporarySignatureRequest(HttpMethodEnum.GET, expire)` + `setBucketName/setObjectKey` → `obsClient.createTemporarySignature(req).getSignedUrl()`；`obsClient.close()`。

- [ ] **Step 1: 写失败测试**（mock 注入 `ObsClient`）

```java
package com.bank.branch.platform.governance.storage;

import com.obs.services.ObsClient;
import com.obs.services.model.ObsObject;
import com.obs.services.model.TemporarySignatureResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObsStorageClientTest {

    @Mock ObsClient obsClient;
    ObsStorageClient store;

    @BeforeEach
    void setUp() {
        store = new ObsStorageClient();
        ReflectionTestUtils.setField(store, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(store, "presignExpireSeconds", 600L);
        ReflectionTestUtils.setField(store, "obsClient", obsClient); // 跳过 @PostConstruct 真连
    }

    @Test
    void putObject_writesToBucketWithKey() {
        store.putObject("hello".getBytes(), "zybb/k1.xlsx");
        ArgumentCaptor<String> bucket = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(obsClient).putObject(bucket.capture(), key.capture(), any(InputStream.class));
        assertThat(bucket.getValue()).isEqualTo("test-bucket");
        assertThat(key.getValue()).isEqualTo("zybb/k1.xlsx");
    }

    @Test
    void getBytes_readsObjectContent() throws Exception {
        ObsObject obj = mock(ObsObject.class);
        when(obj.getObjectContent()).thenReturn(new ByteArrayInputStream("data".getBytes()));
        when(obsClient.getObject("test-bucket", "k2")).thenReturn(obj);
        assertThat(store.getBytes("k2")).isEqualTo("data".getBytes());
    }

    @Test
    void deleteByKey_deletesObject() {
        store.deleteByKey("k3");
        verify(obsClient).deleteObject("test-bucket", "k3");
    }

    @Test
    void generatePresignedUrl_returnsSignedUrl() {
        TemporarySignatureResponse resp = mock(TemporarySignatureResponse.class);
        when(resp.getSignedUrl()).thenReturn("https://obs/test-bucket/k4?sig=x");
        when(obsClient.createTemporarySignature(any())).thenReturn(resp);
        assertThat(store.generatePresignedUrl("k4")).isEqualTo("https://obs/test-bucket/k4?sig=x");
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `mvn -q -pl system-governance-center test -Dtest=ObsStorageClientTest 2>&1 | tail -15`
Expected: 编译失败 / `ObsStorageClient` 不存在。

- [ ] **Step 3: 写实现**

```java
package com.bank.branch.platform.governance.storage;

import com.bank.branch.platform.common.web.exception.BizException;
import com.obs.services.ObsClient;
import com.obs.services.model.HttpMethodEnum;
import com.obs.services.model.ObsObject;
import com.obs.services.model.TemporarySignatureRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** 华为云 OBS 读写工具类（仿参考 PdObsClient）。懒连接：@PostConstruct 仅构造 client，不发网络请求。 */
@Slf4j
@Component
public class ObsStorageClient {

    @Value("${obs.endPoint}")   private String endPoint;
    @Value("${obs.accessKey}")  private String accessKey;
    @Value("${obs.secretKey}")  private String secretKey;
    @Value("${obs.bucketName}") private String bucketName;
    @Value("${obs.presignExpireSeconds:600}") private long presignExpireSeconds;

    private ObsClient obsClient;

    @PostConstruct
    public void init() {
        this.obsClient = new ObsClient(accessKey, secretKey, endPoint);
        log.info("[ObsStorageClient] 初始化完成 endPoint={}, bucket={}", endPoint, bucketName);
    }

    @PreDestroy
    public void close() {
        if (obsClient != null) {
            try { obsClient.close(); } catch (Exception e) { log.warn("[ObsStorageClient] 关闭失败", e); }
        }
    }

    /** 写：字节数组 → OBS 对象。 */
    public void putObject(byte[] bytes, String key) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            obsClient.putObject(bucketName, key, in);
        } catch (Exception e) {
            log.error("[ObsStorageClient] putObject 失败 key={}", key, e);
            throw new BizException("GOV-50001", "OBS 上传失败: " + e.getMessage(), e);
        }
    }

    /** 读：OBS 对象 → 字节数组。 */
    public byte[] getBytes(String key) {
        try (ObsObject obj = obsClient.getObject(bucketName, key);
             InputStream in = obj.getObjectContent()) {
            return in.readAllBytes();
        } catch (Exception e) {
            log.error("[ObsStorageClient] getBytes 失败 key={}", key, e);
            throw new BizException("GOV-50001", "OBS 读取失败: " + e.getMessage(), e);
        }
    }

    /** 删：OBS 对象。 */
    public void deleteByKey(String key) {
        try {
            obsClient.deleteObject(bucketName, key);
        } catch (Exception e) {
            log.error("[ObsStorageClient] deleteByKey 失败 key={}", key, e);
            throw new BizException("GOV-50001", "OBS 删除失败: " + e.getMessage(), e);
        }
    }

    /** 预签名临时下载 URL。 */
    public String generatePresignedUrl(String key) {
        TemporarySignatureRequest req =
                new TemporarySignatureRequest(HttpMethodEnum.GET, presignExpireSeconds);
        req.setBucketName(bucketName);
        req.setObjectKey(key);
        return obsClient.createTemporarySignature(req).getSignedUrl();
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `mvn -q -pl system-governance-center test -Dtest=ObsStorageClientTest 2>&1 | tail -8`
Expected: `Tests run: 4, Failures: 0`

- [ ] **Step 5: Commit**

```bash
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/storage/ObsStorageClient.java \
        system-governance-center/src/test/java/com/bank/branch/platform/governance/storage/ObsStorageClientTest.java
git commit -m "feat(storage): 新增 ObsStorageClient OBS 读写工具类 + 单测"
```

---

## Task 3：FileService 后端换 OBS（TDD）

**Files:**
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/service/FileService.java`
- Test: `system-governance-center/src/test/java/com/bank/branch/platform/governance/service/FileServiceTest.java`（若已存在则增量改；否则新建）

签名变化：`upload(MultipartFile, uploadedBy, bizType, bizId, category)`；新增 `upload(byte[] bytes, String filename, String contentType, String uploadedBy, String category)`；新增 `byte[] getFileContent(String fileId)`；删 `getFilePath`。注入 `ObsStorageClient obsStorageClient`（删 storageRoot/本地磁盘逻辑）。

- [ ] **Step 1: 写失败测试**（mock `ObsStorageClient` + `FileObjectMapper`）

```java
@Test
void upload_putsToObsWithCategoryPrefix_andStoresKey() {
    MockMultipartFile f = new MockMultipartFile("file", "r.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "x".getBytes());
    when(fileObjectMapper.selectByMd5Hash(any())).thenReturn(null);
    FileObjectDTO dto = fileService.upload(f, "U1", null, null, FileCategory.FREE_REPORT);
    ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
    verify(obsStorageClient).putObject(any(byte[].class), keyCap.capture());
    assertThat(keyCap.getValue()).matches("\\d{4}/\\d{2}/\\d{2}/zybb_[0-9a-f]+\\.xlsx");
    ArgumentCaptor<FileObject> foCap = ArgumentCaptor.forClass(FileObject.class);
    verify(fileObjectMapper).insert(foCap.capture());
    assertThat(foCap.getValue().getStoragePath()).isEqualTo(keyCap.getValue());
    assertThat(foCap.getValue().getBucketName()).isEqualTo("obs");
}

@Test
void getDownloadUrl_returnsPresigned() {
    FileObject fo = new FileObject(); fo.setId("F1"); fo.setStoragePath("2026/06/12/zybb_a.xlsx");
    when(fileObjectMapper.selectById("F1")).thenReturn(fo);
    when(obsStorageClient.generatePresignedUrl("2026/06/12/zybb_a.xlsx")).thenReturn("https://obs/x");
    assertThat(fileService.getDownloadUrl("F1")).isEqualTo("https://obs/x");
}

@Test
void getFileContent_readsFromObs() {
    FileObject fo = new FileObject(); fo.setId("F2"); fo.setStoragePath("k");
    when(fileObjectMapper.selectById("F2")).thenReturn(fo);
    when(obsStorageClient.getBytes("k")).thenReturn("bytes".getBytes());
    assertThat(fileService.getFileContent("F2")).isEqualTo("bytes".getBytes());
}

@Test
void deleteFile_deletesObsObjectAndRecord() {
    FileObject fo = new FileObject(); fo.setId("F3"); fo.setStoragePath("k3");
    when(fileObjectMapper.selectById("F3")).thenReturn(fo);
    fileService.deleteFile("F3");
    verify(obsStorageClient).deleteByKey("k3");
    verify(fileObjectMapper).deleteById("F3");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl system-governance-center test -Dtest=FileServiceTest 2>&1 | tail -15`
Expected: 编译失败（方法签名不存在）。

- [ ] **Step 3: 改 FileService 实现**

- 构造器/字段：删 `storageRoot` 与 `Files.createDirectories`，注入 `private final ObsStorageClient obsStorageClient;`（构造器加参）。
- 把现有 `upload(file, uploadedBy, bizType, bizId)` 改名加 `category` 形参：`upload(MultipartFile file, String uploadedBy, String bizType, String bizId, String category)`；其中"5. 生成存储路径"改：

```java
String prefix = (category == null || category.isBlank()) ? FileCategory.GENERAL : category;
String storagePath = now.format(PATH_DATE_FORMAT) + "/"
        + prefix + "_" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
```

"6. 写入" 改：

```java
obsStorageClient.putObject(file.getBytes(), storagePath);
```

`fileObject.setBucketName("obs");`

- 新增字节重载（抽公共逻辑或复用 MD5/插入流程）：

```java
@Transactional
public FileObjectDTO upload(byte[] bytes, String filename, String contentType,
                           String uploadedBy, String category) {
    String extension = getFileExtension(filename);
    if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase()))
        throw new BizException(GovErrorCode.FILE_FORMAT_INVALID.getCode(), GovErrorCode.FILE_FORMAT_INVALID.getMessage());
    if (bytes.length > MAX_FILE_SIZE)
        throw new BizException(GovErrorCode.FILE_SIZE_EXCEEDED.getCode(), GovErrorCode.FILE_SIZE_EXCEEDED.getMessage());
    String md5Hash;
    try {
        md5Hash = String.format("%032x", new BigInteger(1, MessageDigest.getInstance("MD5").digest(bytes)));
    } catch (Exception e) {
        throw new BizException(GovErrorCode.MINIO_ERROR.getCode(), "MD5 计算失败: " + e.getMessage(), e);
    }
    FileObject existing = fileObjectMapper.selectByMd5Hash(md5Hash);
    if (existing != null) return toDTO(existing);
    LocalDateTime now = LocalDateTime.now();
    String prefix = (category == null || category.isBlank()) ? FileCategory.GENERAL : category;
    String storagePath = now.format(PATH_DATE_FORMAT) + "/" + prefix + "_"
            + UUID.randomUUID().toString().replace("-", "") + "." + extension;
    obsStorageClient.putObject(bytes, storagePath);
    String id = "F_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    FileObject fo = new FileObject();
    fo.setId(id); fo.setFileName(filename); fo.setFileSize((long) bytes.length);
    fo.setFileType(contentType); fo.setStoragePath(storagePath); fo.setBucketName("obs");
    fo.setMd5Hash(md5Hash); fo.setUploadedBy(uploadedBy); fo.setUploadedTime(now);
    fileObjectMapper.insert(fo);
    return toDTO(fo);
}
```

> 注意：PATH_DATE_FORMAT 之前生成的 storagePath 带前导 `/`（本地路径用）。OBS key 不应带前导 `/`，本任务统一去掉前导 `/`（上面两处均无前导 `/`）。

- `getDownloadUrl`：把 `return "/api/files/" + fileId + "/download";` 改为：

```java
return obsStorageClient.generatePresignedUrl(fileObject.getStoragePath());
```

- 新增：

```java
public byte[] getFileContent(String fileId) {
    FileObject fo = fileObjectMapper.selectById(fileId);
    if (fo == null) throw new BizException(GovErrorCode.FILE_NOT_FOUND.getCode(), GovErrorCode.FILE_NOT_FOUND.getMessage());
    return obsStorageClient.getBytes(fo.getStoragePath());
}
```

- `deleteFile`：在 `deleteById` 前加 `obsStorageClient.deleteByKey(fileObject.getStoragePath());`（改原"MinIO 不删"注释）。
- 删 `getFilePath` 方法。

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl system-governance-center test -Dtest=FileServiceTest 2>&1 | tail -8`
Expected: 全绿（注意：删 getFilePath 会让本模块其它引用编译失败 → 由 Task 4 修）。如本步因下游引用编译失败，先只跑本类：确认 FileService 自身逻辑测试通过，下游编译在 Task 4 修复后再整体编译。

- [ ] **Step 5: Commit**

```bash
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/service/FileService.java \
        system-governance-center/src/test/java/com/bank/branch/platform/governance/service/FileServiceTest.java
git commit -m "feat(storage): FileService 后端切 OBS（带类型前缀 key/预签名/字节上传/内容读取/删除）"
```

---

## Task 4：FileApi/FileFacade 契约调整 + 通用下载入口迁移

**Files:**
- Modify: `system-governance-center/.../governance/api/FileApi.java`
- Modify: `system-governance-center/.../governance/facade/FileFacade.java`
- Modify: governance `FileController`（`/api/files/{id}/download`）

- [ ] **Step 1: FileApi 加方法、删 getFilePath**

`FileApi.java`：删 `java.nio.file.Path getFilePath(String fileId);`；加：

```java
/** 字节直传（导出/导入/公告等无 MultipartFile 场景）。category 见 FileCategory。 */
FileObjectDTO upload(byte[] bytes, String filename, String contentType, String uploadedBy, String category);

/** 带类型前缀的 MultipartFile 上传。 */
FileObjectDTO upload(MultipartFile file, String uploadedBy, String category);

/** 读取文件字节内容（替代原 getFilePath 的本地读流）。 */
byte[] getFileContent(String fileId);
```

- [ ] **Step 2: FileFacade 实现新方法、删 getFilePath**

```java
@Override
public FileObjectDTO upload(MultipartFile file, String uploadedBy) {
    return fileService.upload(file, uploadedBy, null, null, com.bank.branch.platform.governance.storage.FileCategory.GENERAL);
}

@Override
public FileObjectDTO upload(MultipartFile file, String uploadedBy, String category) {
    return fileService.upload(file, uploadedBy, null, null, category);
}

@Override
public FileObjectDTO upload(byte[] bytes, String filename, String contentType, String uploadedBy, String category) {
    return fileService.upload(bytes, filename, contentType, uploadedBy, category);
}

@Override
public byte[] getFileContent(String fileId) {
    return fileService.getFileContent(fileId);
}
```

删 FileFacade 的 `getFilePath` 覆写。

- [ ] **Step 3: governance FileController `/api/files/{id}/download` 改读 OBS**

把原 `getFilePath` + `Files.copy` 改为：

```java
byte[] data = fileApi.getFileContent(fileId);  // 或注入 FileService.getFileContent
String fileName = fileApi.getFileName(fileId);
String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
response.setContentType("application/octet-stream");
response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
response.setContentLengthLong(data.length);
response.getOutputStream().write(data);
```

（先 `git grep -n "getFilePath" -- 'system-governance-center/**/controller/*.java'` 定位 FileController 实际文件与写法。）

- [ ] **Step 4: 编译 governance**

Run: `mvn -q -pl system-governance-center -am test-compile 2>&1 | tail -15`
Expected: 编译通过（governance 内 getFilePath 引用清零）。

- [ ] **Step 5: Commit**

```bash
git add system-governance-center/src/main/java/com/bank/branch/platform/governance/api/FileApi.java \
        system-governance-center/src/main/java/com/bank/branch/platform/governance/facade/FileFacade.java \
        system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/*.java
git commit -m "feat(storage): FileApi 加 upload(bytes/category)+getFileContent，删 getFilePath，通用下载改读 OBS"
```

---

## Task 5：迁移跨模块 getFilePath 消费方（report 自由报表 + portal 公告下载）

**Files:**
- Modify: `report-analytics-center/.../report/...`（FreeReport 下载入口，`git grep -n "getFilePath" -- 'report-analytics-center/**'`）
- Modify: `portal-content-center/.../portal/controller/AnnouncementController.java`

- [ ] **Step 1: 定位所有跨模块 getFilePath 调用**

Run: `git grep -n "getFilePath" -- '*.java' | grep -v governance`
Expected: 列出 report 自由报表下载、portal 公告下载等。

- [ ] **Step 2: 自由报表下载改 getFileContent**

把 `Path p = fileApi.getFilePath(fileId); Files.copy(p, response...)` 模式改为：

```java
byte[] data = fileApi.getFileContent(fileId);
response.setContentType("application/octet-stream");
response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
response.setContentLengthLong(data.length);
response.getOutputStream().write(data);
```

- [ ] **Step 3: 公告下载改 getFileContent**

`AnnouncementController.downloadFile`：把

```java
Path path = Paths.get(af.getFilePath());
if (!Files.exists(path)) { response.sendError(404, "文件不存在"); return; }
... Files.copy(path, response.getOutputStream());
```

改为（`af.getFilePath()` 现在存的是 FileApi fileId，见 Task 8）：

```java
byte[] data = fileApi.getFileContent(af.getFilePath());
String encoded = URLEncoder.encode(af.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
response.setContentType("application/octet-stream");
response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
response.setContentLengthLong(data.length);
response.getOutputStream().write(data);
```

注入 `FileApi fileApi`（portal 已依赖 governance）。

- [ ] **Step 4: 编译两模块**

Run: `mvn -q -pl report-analytics-center,portal-content-center -am test-compile 2>&1 | tail -15`
Expected: 通过；`git grep getFilePath -- '*.java'` 应只剩 0 处或测试残留。

- [ ] **Step 5: Commit**

```bash
git add report-analytics-center portal-content-center
git commit -m "refactor(storage): 自由报表/公告下载由本地 getFilePath 改 OBS getFileContent"
```

---

## Task 6：4 个绩效导出策略 MinIO→FileApi（TDD）

**Files（4 个，改法一致）:**
- Modify: `performance-engine-center/.../service/export/impl/{Metric,Kpi,Alloc,Detail}ExportStrategy.java`
- Test: 对应 `*ExportStrategyTest.java`

每个策略的差异表（其余完全一致）：

| 策略 | category 常量 | 原 fileKey 文件名片段 |
|------|---------------|----------------------|
| MetricExportStrategy | `FileCategory.EXPORT_METRIC` | `metric_result` |
| KpiExportStrategy | `FileCategory.EXPORT_KPI` | `kpi_result` |
| AllocExportStrategy | `FileCategory.EXPORT_ALLOC` | `alloc_result` |
| DetailExportStrategy | `FileCategory.EXPORT_DETAIL` | `detail_result` |

- [ ] **Step 1: 改测试（以 MetricExportStrategyTest 为例，其余同构）**

删除对 `MinioClient`/`PutObjectArgs` 的 mock；改为 mock `FileApi.upload(byte[]...)`：

```java
@Mock FileApi fileApi;
// 构造：new MetricExportStrategy(..., fileApi);
@Test
void export_uploadsViaFileApi_andStoresFileId() {
    FileObjectDTO dto = new FileObjectDTO(); dto.setId("F_OBS_1");
    when(fileApi.upload(any(byte[].class), anyString(), anyString(), any(), eq(FileCategory.EXPORT_METRIC)))
        .thenReturn(dto);
    // ... 触发 export(task, ...)
    verify(fileApi).upload(any(byte[].class), endsWith(".xlsx"), anyString(), any(), eq(FileCategory.EXPORT_METRIC));
    assertThat(task.getFileKey()).isEqualTo("F_OBS_1");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=MetricExportStrategyTest 2>&1 | tail -15`
Expected: 编译失败（构造器/字段已变）。

- [ ] **Step 3: 改实现（MetricExportStrategy，其余按差异表同构）**

- 删字段 `private final MinioClient minioClient;` 与 `minioBucketName`；构造器去掉 MinioClient 参，加 `private final FileApi fileApi;`（import `com.bank.branch.platform.governance.api.FileApi/dto.FileObjectDTO` + `storage.FileCategory`）。
- 把"5) 上传 MinIO"整块替换为：

```java
// 5) 上传 OBS（统一走 FileApi）
String fileName = "metric_result_" + System.currentTimeMillis() + ".xlsx";
FileObjectDTO dto = fileApi.upload(bytes, fileName,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        task.getCreatedBy(), FileCategory.EXPORT_METRIC);
task.setFileKey(dto.getId());
task.setFileSize((long) bytes.length);
```

> `task.getCreatedBy()` 若无该字段，用导出发起人字段（执行时 `grep "getCreatedBy\|operator\|empId" PerfExportTask.java` 确认）。

- [ ] **Step 4: 对 Kpi/Alloc/Detail 重复 Step 1–3**（按差异表替换 category 与文件名片段）。

- [ ] **Step 5: 运行 4 个策略测试确认通过**

Run: `mvn -q -pl performance-engine-center test -Dtest='*ExportStrategyTest' 2>&1 | tail -10`
Expected: 全绿。

- [ ] **Step 6: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/export/impl/ \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/export/impl/
git commit -m "refactor(perf-export): 4 个导出策略由 MinIO 直连改走 FileApi(OBS)+类型前缀"
```

---

## Task 7：报表导出策略补类型前缀

**Files:**
- Modify: `report-analytics-center/.../service/export/impl/DynamicQueryExportStrategy.java`（用 `FileCategory.EXPORT_DYNAMIC`）
- Modify: `report-analytics-center/.../service/export/impl/CustPoolSummaryExportStrategy.java`（用 `FileCategory.EXPORT_CUSTPOOL`）

- [ ] **Step 1: 改调用传 category**

这两个策略已调 `fileApi.upload(...)`。把调用切到带 category 的重载：
- 若当前是 `fileApi.upload(new ByteArrayMultipartFile(bytes, name), operator)` → 改为 `fileApi.upload(bytes, name, contentType, operator, FileCategory.EXPORT_DYNAMIC)`（CustPool 用 `EXPORT_CUSTPOOL`）。
- 相应测试 mock 改 `upload(byte[]..., eq(FileCategory.EXPORT_DYNAMIC))`。

- [ ] **Step 2: 运行测试**

Run: `mvn -q -pl report-analytics-center test -Dtest='DynamicQueryExportStrategyTest,CustPoolSummaryExportStrategyTest' 2>&1 | tail -8`
Expected: 全绿。

- [ ] **Step 3: Commit**

```bash
git add report-analytics-center/src/main/java/com/bank/branch/platform/report/service/export/impl/ \
        report-analytics-center/src/test/java/com/bank/branch/platform/report/service/export/impl/
git commit -m "refactor(rpt-export): 动态查询/客户池导出补 OBS 类型前缀"
```

---

## Task 8：数据导入源文件归档 + 公告上传走 OBS（TDD）

**Files:**
- Create: `docs/superpowers/sql/2026-06-12-perf-import-batch-source-object-key.sql`
- Modify: `performance-engine-center/.../entity/PerfImportBatch.java`
- Modify: `performance-engine-center/.../service/importer/impl/PerfImportServiceImpl.java`
- Test: `performance-engine-center/.../service/importer/...PerfImportServiceImplTest`（增量）
- Modify: `portal-content-center/.../service/AnnouncementService.java`
- Test: `portal-content-center/.../service/AnnouncementServiceTest`（增量）

- [ ] **Step 1: SQL 加列脚本**

```sql
-- 2026-06-12 数据导入源文件归档到 OBS：记录 FileApi fileId
ALTER TABLE PERF_IMPORT_BATCH ADD COLUMN SOURCE_OBJECT_KEY VARCHAR(64) NULL COMMENT '导入源文件 OBS fileId';
```

在 dev 库执行：`mysql -h127.0.0.1 -uroot -pdjdev yiti < docs/superpowers/sql/2026-06-12-perf-import-batch-source-object-key.sql`

- [ ] **Step 2: 实体加字段**

`PerfImportBatch.java` 加：

```java
/** 导入源文件 OBS fileId（FileApi） */
private String sourceObjectKey;
```

（MyBatis-Plus map-underscore-to-camel-case 自动映射 SOURCE_OBJECT_KEY；BaseMapper.insert 自动带上。）

- [ ] **Step 3: 写导入归档失败测试**

```java
@Test
void startImport_archivesSourceFileToObs() {
    MockMultipartFile f = new MockMultipartFile("file", "imp.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "rows".getBytes());
    FileObjectDTO dto = new FileObjectDTO(); dto.setId("F_IMP_1");
    when(fileApi.upload(any(byte[].class), eq("imp.xlsx"), anyString(), any(), eq(FileCategory.PERF_IMPORT)))
        .thenReturn(dto);
    // strategyMap 注入一个 mock ImportStrategy 返回 ImportResult(1,1,0,0)
    service.startImport("METRIC_RESULT", f, "U1", LocalDate.of(2026,6,10));
    verify(fileApi).upload(any(byte[].class), eq("imp.xlsx"), anyString(), any(), eq(FileCategory.PERF_IMPORT));
    ArgumentCaptor<PerfImportBatch> cap = ArgumentCaptor.forClass(PerfImportBatch.class);
    verify(batchMapper).insert(cap.capture());
    assertThat(cap.getValue().getSourceObjectKey()).isEqualTo("F_IMP_1");
}
```

- [ ] **Step 4: 运行确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=PerfImportServiceImplTest 2>&1 | tail -15`
Expected: 编译失败（fileApi 未注入 / sourceObjectKey 不存在）。

- [ ] **Step 5: 改 PerfImportServiceImpl**

- 注入 `private final FileApi fileApi;`（import governance FileApi/DTO + FileCategory）。
- 在"1) 创建批次"之前读字节并归档（避免流被消费两次）：

```java
byte[] raw;
try { raw = file.getBytes(); }
catch (Exception e) { throw new PerfException(PerfErrorCode.VALIDATION_FAILED, "读取上传文件失败"); }
FileObjectDTO archived = fileApi.upload(raw, file.getOriginalFilename(),
        file.getContentType(), operatorId, FileCategory.PERF_IMPORT);
```

- batch 设值：`batch.setSourceObjectKey(archived.getId());`（在 `batchMapper.insert(batch)` 之前）。
- 解析仍用原 `file`（MultipartFile 未被 `getBytes()` 消费流，可继续 `strategy.execute(batch, file, ctx)`）。
  > 校验：`MultipartFile.getBytes()` 不消费 `getInputStream()`，解析仍可正常读取。若某 ImportStrategy 依赖一次性 InputStream，则改为传 `new ByteArrayInputStream(raw)` 包装。执行时确认 `ImportStrategy.execute` 入参类型。

- [ ] **Step 6: 运行确认通过**

Run: `mvn -q -pl performance-engine-center test -Dtest=PerfImportServiceImplTest 2>&1 | tail -8`
Expected: 全绿。

- [ ] **Step 7: 公告上传走 FileApi（TDD）**

`AnnouncementServiceTest` 加：

```java
@Test
void uploadFile_putsToObsViaFileApi() {
    MockMultipartFile f = new MockMultipartFile("file","gg.pdf","application/pdf","x".getBytes());
    FileObjectDTO dto = new FileObjectDTO(); dto.setId("F_GG_1");
    when(fileApi.upload(any(byte[].class), eq("gg.pdf"), anyString(), any(), eq(FileCategory.ANNOUNCEMENT)))
        .thenReturn(dto);
    AnnouncementFileDTO r = service.uploadFile("ANN1", f);
    verify(fileApi).upload(any(byte[].class), eq("gg.pdf"), anyString(), any(), eq(FileCategory.ANNOUNCEMENT));
    // AnnouncementFile.filePath 存 fileId
}
```

改 `AnnouncementService.uploadFile`：删 `announcement.upload-dir` + `Files.createDirectories/copy`；注入 `FileApi fileApi`；改为：

```java
FileObjectDTO dto = fileApi.upload(file.getBytes(), file.getOriginalFilename(),
        file.getContentType(), /*operator*/ uploadedBy, FileCategory.ANNOUNCEMENT);
// AnnouncementFile.setFilePath(dto.getId());  // 复用 filePath 列存 FileApi fileId
```

（`uploadFile` 现签名无 operator，则用现有 ID 字段或传 "system"；执行时按方法上下文取。）

- [ ] **Step 8: 运行公告测试**

Run: `mvn -q -pl portal-content-center test -Dtest=AnnouncementServiceTest 2>&1 | tail -8`
Expected: 全绿。

- [ ] **Step 9: Commit**

```bash
git add docs/superpowers/sql/2026-06-12-perf-import-batch-source-object-key.sql \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfImportBatch.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/importer/impl/PerfImportServiceImpl.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/importer/ \
        portal-content-center
git commit -m "feat(storage): 数据导入源文件归档 OBS + 公告附件上传走 OBS"
```

---

## Task 9：删除 MinioConfig + 清理测试 mock

**Files:**
- Delete: `system-governance-center/.../governance/config/MinioConfig.java`
- Modify: `bootstrap/.../it/config/{TestMockConfig,FlowableE2ETestConfig,FlowableRealEnvTestConfig,LeadE2ETestConfig}.java`

- [ ] **Step 1: 删 MinioConfig**

```bash
git rm system-governance-center/src/main/java/com/bank/branch/platform/governance/config/MinioConfig.java
```

- [ ] **Step 2: 删测试配置里的 MinioClient mock**

`git grep -n "MinioClient\|io.minio" -- 'bootstrap/src/test/**'`，逐个删除 `@Bean MinioClient` mock 方法与 import。这些 mock 原为满足导出策略对 MinioClient 的依赖；策略已改 FileApi，bean 不再需要。

- [ ] **Step 3: 全量编译 + 单测**

Run: `mvn -q clean install -DskipTests 2>&1 | tail -8`
Expected: `BUILD SUCCESS`。再 `git grep -n "io.minio\|MinioClient\|getFilePath\|LocalExportFileStore" -- '*.java'` → 0 处（除文档）。

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore(storage): 删除 MinioConfig 与测试 MinioClient mock，MinIO 彻底移除"
```

---

## Task 10：全量验证

- [ ] **Step 1: 全量构建 + 测试**

Run: `mvn clean install 2>&1 | tail -20`
Expected: `BUILD SUCCESS`，无 minio/getFilePath 残留编译错误。

- [ ] **Step 2: 启动冒烟（懒连接，不连真 OBS）**

Run: 打包 bootstrap 启动，`curl /api/auth/current-user` 返回 401（应用正常起来，OBS 懒连接不阻塞启动）。

- [ ] **Step 3: 残留扫描**

Run: `git grep -nE "io\.minio|MinioClient|getFilePath|LocalExportFileStore|file\.storage\.root|announcement\.upload-dir" -- '*.java' '*.yml' '*.xml'`
Expected: 仅文档/spec 命中，代码 0 残留。

---

## Self-Review（已执行）

- **Spec 覆盖**：§4.1 ObsStorageClient→Task2；§4.2 FileService→Task3；§4.3 FileApi→Task4；§4.4 绩效导出→Task6；§4.5 导入归档→Task8；§4.6 公告→Task5+Task8；§4.7 MinIO 清理→Task0+Task9；§4.8 依赖配置→Task0；§4.9 前缀→Task1+各调用点；§5 测试→各 Task 内 TDD。全覆盖。
- **占位扫描**：无 TBD；少数"执行时 grep 确认"为针对未逐字读取文件（FileController/PerfExportTask 字段/ImportStrategy 入参/AnnouncementFile 列）的精确定位指令，非内容缺失。
- **类型一致**：`upload(byte[], filename, contentType, uploadedBy, category)`、`getFileContent(fileId)`、`task.setFileKey(dto.getId())`、`FileCategory.*` 全程一致。
