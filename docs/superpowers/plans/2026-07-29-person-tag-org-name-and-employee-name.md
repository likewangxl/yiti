# 业务标签机构名称导入与员工姓名展示 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将业务标签的两类机构 Excel 导入改为按机构名称唯一匹配，并在员工详情中批量补充员工姓名。

**Architecture:** `system-governance-center` 继续只通过认证模块的 `OrgApi`、`UserApi` 取数。机构导入先批量把名称解析为唯一 `OrgDTO`，再使用现有 `deptNo` 存储口径；员工详情按当前页批量建立 `username -> displayName` 映射。前端只消费新增的 `displayName` 字段，不改变现有接口路径和成员维护请求。

**Tech Stack:** Spring Boot 3.2.3、Java 17、EasyExcel、JUnit 5、Mockito、Vue 3、Element Plus、Vitest 1.6。

## Global Constraints

- 不修改 `PERSON_TAG_REL`、`EXT_ORG_INFO`、`PT_USER` 表结构。
- 不在标签关联表中冗余机构名称或员工姓名。
- 机构名称命中多条 `EXT_ORG_INFO` 时必须失败，不任意选择。
- Excel 任一行校验失败时整体不写库；成员覆盖导入不得删除原关联。
- 机构和员工信息必须批量查询，禁止 N+1。
- 手工新增、修改机构成员仍使用现有机构编号接口。
- 保留工作区中与本任务无关的已有修改，提交时只暂存本计划列出的文件。

---

### Task 1: 机构导入按名称唯一解析

**Files:**
- Modify: `system-governance-center/src/test/java/com/bank/branch/platform/governance/service/PersonTagImportServiceTest.java`
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/PersonTagOrgImportRow.java`
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/PersonTagOrgMemberImportRow.java`
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/service/PersonTagImportService.java`
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/AdminPersonTagController.java`（DTO 字段重命名后的最小编译适配；模板示例在 Task 2 完成）

**Interfaces:**
- Consumes: `List<OrgDTO> OrgApi.getOrgsByNames(Collection<String> orgNames)`
- Produces: ORG 全局导入和成员导入以 Excel `机构名称` 为输入，唯一解析后仍写 `PersonTagRel.orgDeptNo`

- [x] **Step 1: 将机构导入测试改为名称输入并增加唯一性边界**

在测试辅助方法中创建带名称和编号的机构：

```java
private static OrgDTO org(String deptNo, String orgName) {
    OrgDTO o = new OrgDTO();
    o.setDeptNo(deptNo);
    o.setOrgName(orgName);
    return o;
}
```

机构 Excel 行辅助方法改为 `orgName`。增加或调整以下断言：

```java
@Test
void importGlobal_org_shouldResolveUniqueOrgNameAndPersistDeptNo() {
    when(orgApi.getOrgsByNames(anyList()))
            .thenReturn(List.of(org("0101", "城东支行"), org("0102", "城西支行")));
    when(tagMapper.selectByTagNames(anyList())).thenReturn(List.of(tag(1L, "重点机构")));
    when(relMapper.selectByTagIds(anyList())).thenReturn(List.of());

    PersonTagImportResultDTO result = service.importGlobal(
            orgGlobalXlsx(List.of(orgRow("重点机构", "城东支行"), orgRow("重点机构", "城西支行"))),
            "ORG", "OP1");

    assertThat(result.isSuccess()).isTrue();
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PersonTagRel>> rows = ArgumentCaptor.forClass(List.class);
    verify(relMapper).insertBatch(rows.capture());
    assertThat(rows.getValue()).extracting(PersonTagRel::getOrgDeptNo)
            .containsExactly("0101", "0102");
}

@Test
void importGlobal_org_ambiguousName_shouldFailAtomically() {
    when(orgApi.getOrgsByNames(anyList()))
            .thenReturn(List.of(org("0101", "同名支行"), org("0102", "同名支行")));

    PersonTagImportResultDTO result = service.importGlobal(
            orgGlobalXlsx(List.of(orgRow("重点机构", "同名支行"))), "ORG", "OP1");

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getErrors().get(0).getMessage()).contains("不唯一");
    verify(relMapper, never()).insertBatch(anyList());
}

@Test
void importMembers_org_missingName_shouldKeepExistingRelations() {
    when(orgApi.getOrgsByNames(anyList())).thenReturn(List.of());

    PersonTagImportResultDTO result = service.importMembers(1L,
            orgMemberXlsx(List.of(orgMemberRow("不存在支行"))), "ORG", "OP1");

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getErrors().get(0).getMessage()).contains("不存在");
    verify(relMapper, never()).deleteByTagIdAndDim(anyLong(), anyString());
    verify(relMapper, never()).insertBatch(anyList());
}
```

- [x] **Step 2: 运行机构导入测试并确认 RED**

Run:

```bash
mvn -pl system-governance-center \
  -Dtest=PersonTagImportServiceTest \
  test
```

Expected: FAIL；现有实现仍读取 `机构号`、调用 `getOrgsByDeptNos()`，无法按名称解析，也不会拒绝重名。

- [x] **Step 3: 调整两个机构 Excel 行模型**

`PersonTagOrgImportRow`：

```java
@ExcelProperty("机构名称")
private String orgName;
```

`PersonTagOrgMemberImportRow`：

```java
@ExcelProperty("机构名称")
private String orgName;
```

同步修改 Javadoc，明确 Excel 输入是 `EXT_ORG_INFO.ORG_NAME`，最终存储仍为 `DEPT_NO`。

- [x] **Step 4: 实现名称批量解析结果**

在 `PersonTagImportService` 中增加清晰的内部结果类型：

```java
private record OrgNameResolution(Map<String, String> deptNoByName,
                                 Set<String> ambiguousNames) {
}
```

新增批量解析方法：

```java
private OrgNameResolution resolveOrgNames(List<String> orgNames) {
    List<OrgDTO> orgs = orgApi.getOrgsByNames(orgNames);
    Map<String, List<OrgDTO>> byName = new HashMap<>();
    if (orgs != null) {
        for (OrgDTO org : orgs) {
            if (org != null && org.getOrgName() != null) {
                byName.computeIfAbsent(org.getOrgName(), key -> new ArrayList<>()).add(org);
            }
        }
    }
    Map<String, String> deptNoByName = new HashMap<>();
    Set<String> ambiguousNames = new HashSet<>();
    for (String orgName : new LinkedHashSet<>(orgNames)) {
        List<OrgDTO> matches = byName.getOrDefault(orgName, List.of());
        if (matches.size() > 1) {
            ambiguousNames.add(orgName);
        } else if (matches.size() == 1 && matches.get(0).getDeptNo() != null
                && !matches.get(0).getDeptNo().isBlank()) {
            deptNoByName.put(orgName, matches.get(0).getDeptNo());
        }
    }
    return new OrgNameResolution(deptNoByName, ambiguousNames);
}
```

ORG 解析方法分别读取 `getOrgName()`。格式校验和错误文案改为：

```text
机构名称不能为空
标签+机构名称在文件内重复
机构名称在文件内重复
机构名称在系统中不存在
机构名称不唯一
```

通过校验的行用 `deptNoByName.get(orgName)` 替换成员标识，然后进入现有查重、覆盖和 `newRel` 写库流程。删除不再使用的 `batchExistingDeptNos` 导入校验辅助方法。

- [x] **Step 5: 运行测试并确认 GREEN**

Run:

```bash
mvn -pl system-governance-center \
  -Dtest=PersonTagImportServiceTest \
  test
```

Expected: `PersonTagImportServiceTest` 全部通过，机构测试验证写入的是唯一匹配记录的 `deptNo`。

- [x] **Step 6: 提交机构导入改动**

```bash
git add \
  system-governance-center/src/test/java/com/bank/branch/platform/governance/service/PersonTagImportServiceTest.java \
  system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/PersonTagOrgImportRow.java \
  system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/PersonTagOrgMemberImportRow.java \
  system-governance-center/src/main/java/com/bank/branch/platform/governance/service/PersonTagImportService.java \
  system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/AdminPersonTagController.java
git commit -m "feat(gov): 业务标签机构导入按名称匹配"
```

### Task 2: 模板改为机构名称

**Files:**
- Modify: `system-governance-center/src/test/java/com/bank/branch/platform/governance/controller/AdminPersonTagControllerTest.java`
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/AdminPersonTagController.java`

**Interfaces:**
- Consumes: `PersonTagOrgImportRow.orgName`、`PersonTagOrgMemberImportRow.orgName`
- Produces: 两个 ORG 模板的表头和示例均使用机构名称

- [x] **Step 1: 增加模板内容测试**

将 ORG 模板响应字节通过 EasyExcel 读取为 `Map<Integer, String>`，断言：

```java
assertThat(rows.get(0)).containsEntry(0, "标签名称").containsEntry(1, "机构名称");
assertThat(rows.get(1)).containsEntry(1, "城东支行");
```

成员模板断言：

```java
assertThat(rows.get(0)).containsEntry(0, "机构名称");
assertThat(rows.get(1)).containsEntry(0, "城东支行");
```

- [x] **Step 2: 运行控制器测试并确认 RED**

Run:

```bash
mvn -pl system-governance-center \
  -Dtest=AdminPersonTagControllerTest \
  test
```

Expected: FAIL；控制器仍调用旧字段并写示例机构号 `0101`。

- [x] **Step 3: 更新模板示例和接口注释**

在两个 ORG 分支中分别设置：

```java
sample.setOrgName("城东支行");
```

将模板 Javadoc 从“机构号”改为“机构名称”。

- [x] **Step 4: 运行控制器测试并确认 GREEN**

Run:

```bash
mvn -pl system-governance-center \
  -Dtest=AdminPersonTagControllerTest \
  test
```

Expected: 测试全部通过。

- [x] **Step 5: 提交模板改动**

```bash
git add \
  system-governance-center/src/test/java/com/bank/branch/platform/governance/controller/AdminPersonTagControllerTest.java \
  system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/AdminPersonTagController.java
git commit -m "feat(gov): 更新业务标签机构导入模板"
```

### Task 3: 员工详情批量补充姓名

**Files:**
- Modify: `system-governance-center/src/test/java/com/bank/branch/platform/governance/service/PersonTagServiceTest.java`
- Modify: `system-governance-center/src/test/java/com/bank/branch/platform/governance/controller/AdminPersonTagControllerTest.java`
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/PersonTagMemberRespDTO.java`
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/service/PersonTagService.java`

**Interfaces:**
- Consumes: `List<UserDTO> UserApi.getUsersByUsernames(List<String> usernames)`
- Produces: `PersonTagMemberRespDTO.displayName`

- [x] **Step 1: 增加员工姓名服务和 JSON 契约测试**

服务测试建立两条员工关联，只返回一个用户：

```java
UserDTO user = new UserDTO();
user.setUsername("100001");
user.setDisplayName("张三");
when(userApi.getUsersByUsernames(List.of("100001", "100002")))
        .thenReturn(List.of(user));
```

断言第一条 `displayName` 为“张三”，第二条为 `null`，并验证 `getUsersByUsernames` 只调用一次。

控制器员工成员测试给 DTO 设置：

```java
m.setDisplayName("张三");
```

并断言：

```java
jsonPath("$.data.records[0].displayName").value("张三")
```

- [x] **Step 2: 运行测试并确认 RED**

Run:

```bash
mvn -pl system-governance-center \
  -Dtest=PersonTagServiceTest,AdminPersonTagControllerTest \
  test
```

Expected: 编译或断言失败；DTO 尚无 `displayName`，服务也未查询用户。

- [x] **Step 3: 增加 DTO 字段并批量装配**

`PersonTagMemberRespDTO` 增加：

```java
/** 员工姓名（PT_USER.DISPLAY_NAME）；DIM_TYPE=EMP 时按工号实时解析。 */
private String displayName;
```

`toEmpMemberDtos` 先收集用户名并批量查询：

```java
List<String> usernames = rels.stream()
        .map(PersonTagRel::getUsername)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
Map<String, String> displayNameByUsername = new HashMap<>();
List<UserDTO> users = userApi.getUsersByUsernames(usernames);
// 过滤 null，并按 username 建映射。
```

组装每行 DTO 时设置：

```java
dto.setDisplayName(displayNameByUsername.get(rel.getUsername()));
```

同步更新类和方法 Javadoc，去除“员工只展示工号”的旧描述。

- [x] **Step 4: 运行测试并确认 GREEN**

Run:

```bash
mvn -pl system-governance-center \
  -Dtest=PersonTagServiceTest,AdminPersonTagControllerTest \
  test
```

Expected: 两个测试类全部通过。

- [x] **Step 5: 提交员工姓名后端改动**

```bash
git add \
  system-governance-center/src/test/java/com/bank/branch/platform/governance/service/PersonTagServiceTest.java \
  system-governance-center/src/test/java/com/bank/branch/platform/governance/controller/AdminPersonTagControllerTest.java \
  system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/PersonTagMemberRespDTO.java \
  system-governance-center/src/main/java/com/bank/branch/platform/governance/service/PersonTagService.java
git commit -m "feat(gov): 业务标签详情展示员工姓名"
```

### Task 4: 前端展示员工姓名

**Files:**
- Modify: `xanzc_frontend/src/views/system/__tests__/PersonTags.spec.js`
- Modify: `xanzc_frontend/src/views/system/PersonTags.vue`

**Interfaces:**
- Consumes: 成员接口响应字段 `displayName`
- Produces: 员工详情“工号 + 员工姓名 + 操作”表格

- [x] **Step 1: 增加前端列契约测试**

测试 mock 成员响应包含：

```javascript
records: [{ id: 11, dimType: 'EMP', username: '100001', displayName: '张三' }]
```

让 `el-table-column` 测试桩声明 `prop`、`label` props，然后在打开详情后断言存在：

```javascript
const columns = wrapper.findAllComponents({ name: 'ElTableColumn' });
expect(columns.some(c => c.props('prop') === 'displayName' && c.props('label') === '员工姓名')).toBe(true);
expect(wrapper.vm.detail.rows[0]).toMatchObject({ username: '100001', displayName: '张三' });
```

- [x] **Step 2: 运行前端测试并确认 RED**

Run:

```bash
cd xanzc_frontend
npm test -- src/views/system/__tests__/PersonTags.spec.js
```

Expected: FAIL；员工表尚无 `displayName` 列。

- [x] **Step 3: 增加员工姓名列**

员工维度表格调整为：

```vue
<el-table-column prop="username" label="工号" width="180" />
<el-table-column prop="displayName" label="员工姓名" min-width="200">
  <template #default="{ row }">{{ row.displayName || '—' }}</template>
</el-table-column>
```

同步修改注释为“员工维度：工号 + 员工姓名”，并将全局导入、成员导入的 ORG 模板提示及错误明细列名改为“机构名称”。

- [x] **Step 4: 运行测试和前端构建**

Run:

```bash
cd xanzc_frontend
npm test -- src/views/system/__tests__/PersonTags.spec.js
npm run build
```

Expected: 定向测试通过，Vite 构建成功。

- [x] **Step 5: 提交前端改动**

```bash
git add \
  xanzc_frontend/src/views/system/__tests__/PersonTags.spec.js \
  xanzc_frontend/src/views/system/PersonTags.vue
git commit -m "feat(fe): 业务标签详情展示员工姓名"
```

### Task 5: 契约文档与整体验证

**Files:**
- Modify: `docs/modules/system-governance-center/03-接口设计与报文.md`
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/AdminPersonTagController.java`（接口说明）
- Modify: `system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/PersonTagImportResultDTO.java`（错误标识说明）
- Modify: `xanzc_frontend/src/api/system.js`（导入接口说明）
- Include: `docs/superpowers/plans/2026-07-29-person-tag-org-name-and-employee-name.md`

**Interfaces:**
- Consumes: Tasks 1-4 的最终接口和模板语义
- Produces: 与代码一致的业务标签接口说明

- [x] **Step 1: 更新接口文档**

更新 I.5、I.9-I.12：

- I.5 响应字段按维度说明 `username/displayName/orgDeptNo/orgName`；
- I.9 全局导入分别说明 EMP=`标签名称+工号`、ORG=`标签名称+机构名称`；
- I.10 模板列与 I.9 一致；
- I.11 成员导入分别说明 EMP=`工号`、ORG=`机构名称`，ORG 名称不存在或不唯一整体失败；
- I.12 模板列与 I.11 一致。

- [x] **Step 2: 运行后端模块完整测试**

Run:

```bash
mvn -pl system-governance-center test
```

Expected: 模块测试全部通过。

- [x] **Step 3: 运行前端定向测试和构建**

Run:

```bash
cd xanzc_frontend
npm test -- src/views/system/__tests__/PersonTags.spec.js
npm run build
```

Expected: 测试和构建均通过。

- [x] **Step 4: 检查格式和修改范围**

Run:

```bash
git diff --check
git status --short
```

Expected: `git diff --check` 无输出；除用户原有未提交文件外，本任务只剩接口文档和实施计划待提交。

- [x] **Step 5: 提交文档与计划**

```bash
git add \
  docs/modules/system-governance-center/03-接口设计与报文.md \
  docs/superpowers/plans/2026-07-29-person-tag-org-name-and-employee-name.md \
  system-governance-center/src/main/java/com/bank/branch/platform/governance/controller/AdminPersonTagController.java \
  system-governance-center/src/main/java/com/bank/branch/platform/governance/api/dto/PersonTagImportResultDTO.java \
  xanzc_frontend/src/api/system.js
git commit -m "docs(gov): 更新业务标签导入与详情契约"
```
