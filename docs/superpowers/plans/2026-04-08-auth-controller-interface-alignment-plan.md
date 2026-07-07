# Auth Controller 接口对齐实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal**: 将 auth-permission-center 模块下的各 Controller 实现与 `docs/modules/auth-permission-center/03-接口设计与报文.md` 严格对齐，修复 5 处不一致。

**Architecture**: 纯接口对齐修复，修改 Controller 路径、返回类型、参数结构，确保与文档定义的 API 契约一致。不涉及业务逻辑变更。涉及文件类型：Controller（6）、Service（4）、Mapper（3）、DTO（3）、Entity（0）、XML（1）、Test（6）。

**Tech Stack**: Spring Boot 3.2.3, MyBatis 3.0.3, JUnit 5, Mockito

---

## 阶段一: G.2 - OrgController.getOrgUsers 添加分页

### Task 1: G.2.1 - 更新 OrgUserDTO 字段

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/OrgUserDTO.java:9-14`

- [ ] **Step 1: 添加字段（不删除旧字段，保持向后兼容）**

```java
@Data
public class OrgUserDTO {
    private String userId;        // 保留，向后兼容
    private String empId;        // 新增，与 userId 同值
    private String username;       // 保留
    private String userChnName;   // 保留，向后兼容
    private String displayName;    // 新增，userChnName 的别名
    private String email;        // 新增
    private List<RoleSimpleDTO> roles; // 新增
}
```

- [ ] **Step 2: 提交**

```bash
git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/api/dto/OrgUserDTO.java
git commit -m "fix(auth): OrgUserDTO 添加文档要求的字段 empId/displayName/email/roles"
```

---

### Task 1: G.2.2 - UserMapper 添加分页查询

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/mapper/UserMapper.java:32-32`
- Modify: `auth-permission-center/src/main/resources/mapper/auth/UserMapper.xml:35-43`

- [ ] **Step 1: UserMapper.java 添加分页查询方法签名**

```java
/**
 * 分页查询机构下的用户（G.2）
 *
 * @param orgCode 机构编码
 * @param keyword 关键字（工号/姓名），可选
 * @param offset 偏移量
 * @param limit 每页条数
 * @return 用户列表
 */
List<PtUser> selectOrgUsersByPage(@Param("orgCode") String orgCode,
                                @Param("keyword") String keyword,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

/**
 * 统计机构下的用户总数（G.2）
 *
 * @param orgCode 机构编码
 * @param keyword 关键字
 * @return 用户总数
 */
long countOrgUsers(@Param("orgCode") String orgCode, @Param("keyword") String keyword);
```

- [ ] **Step 2: UserMapper.xml 添加 SQL**

```xml
<!-- 分页查询机构下的用户（G.2） -->
<select id="selectOrgUsersByPage" resultType="com.bank.branch.platform.auth.entity.PtUser">
    SELECT <include refid="BASE_COLUMNS"/>
    FROM PT_USER u
    INNER JOIN EXT_USER_ORG uo ON u.USER_ID = uo.USER_ID
    WHERE uo.ORG_CODE = #{orgCode}
    <if test="keyword != null and keyword != ''">
        AND (u.USER_ID LIKE CONCAT('%', #{keyword}, '%')
             OR u.USERCHNNAME LIKE CONCAT('%', #{keyword}, '%'))
    </if>
    ORDER BY u.USER_ID
</select>

<!-- 统计机构下的用户总数（G.2） -->
<select id="countOrgUsers" resultType="long">
    SELECT COUNT(*)
    FROM PT_USER u
    INNER JOIN EXT_USER_ORG uo ON u.USER_ID = uo.USER_ID
    WHERE uo.ORG_CODE = #{orgCode}
    <if test="keyword != null and keyword != ''">
        AND (u.USER_ID LIKE CONCAT('%', #{keyword}, '%')
             OR u.USERCHNNAME LIKE CONCAT('%', #{keyword}, '%'))
    </if>
</select>
```

- [ ] **Step 3: 提交**

```bash
git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/mapper/UserMapper.java
git add auth-permission-center/src/main/resources/mapper/auth/UserMapper.xml
git commit -m "feat(auth): UserMapper 添加分页查询机构用户方法"
```

---

### Task 1: G.2.3 - OrgService 添加分页查询

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/OrgService.java:109-127`

- [ ] **Step 1: 添加分页查询方法**

```java
/**
 * 分页查询机构下的用户（G.2）
 *
 * @param orgCode 机构编码
 * @param keyword 关键字（工号/姓名）
 * @param pageNo 页码
 * @param pageSize 每页条数
 * @return 分页结果
 */
public PageResult<OrgUserDTO> getOrgUsers(String orgCode, String keyword, int pageNo, int pageSize) {
    log.debug("[OrgService.getOrgUsers] orgCode={}, keyword={}, pageNo={}, pageSize={}", orgCode, keyword, pageNo, pageSize);
    int offset = (pageNo - 1) * pageSize;

    // 查询列表
    List<PtUser> users = userMapper.selectOrgUsersByPage(orgCode, keyword, offset, pageSize);

    // 查询总数
    long total = userMapper.countOrgUsers(orgCode, keyword);

    // 转换并填充角色信息
    List<OrgUserDTO> records = users.stream().map(u -> {
        OrgUserDTO dto = new OrgUserDTO();
        dto.setUserId(u.getUserId());
        dto.setEmpId(u.getUserId());
        dto.setUsername(u.getUsername());
        dto.setUserChnName(u.getUserchnname());
        dto.setDisplayName(u.getUserchnname());
        dto.setEmail(u.getEmail());
        // TODO: 后续 Task A.3 完成后再填充 roles 字段
        return dto;
    }).collect(Collectors.toList());

    return new PageResult<>(pageNo, pageSize, total, records);
}
```

- [ ] **Step 2: 添加 import**

```java
import com.bank.branch.platform.common.web.PageResult;
```

- [ ] **Step 3: 提交**

```bash
git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/OrgService.java
git commit -m "feat(auth): OrgService 添加分页查询方法"
```

---

### Task 1: G.2.4 - OrgController.getOrgUsers 添加分页

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/OrgController.java:68-81`

- [ ] **Step 1: 修改方法签名和返回类型**

```java
/**
 * 根据机构编码查询该机构下的所有用户（G.2）
 *
 * @param orgCode 机构编码
 * @param keyword 关键字（工号/姓名），可选
 * @param pageNo 页码，默认 1
 * @param pageSize 每页条数，默认 20
 * @return 机构下的用户列表（分页）
 */
@GetMapping("/{orgCode}/users")
@Operation(summary = "查询机构下的用户列表", description = "根据机构编码查询该机构绑定的所有用户（分页）")
@BizAuth(bizType = BizType.ORG, action = BizAction.READ)
public ResponseWrapper<PageResult<OrgUserDTO>> getOrgUsers(
        @PathVariable String orgCode,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
    log.debug("[OrgController.getOrgUsers] orgCode={}, keyword={}, pageNo={}, pageSize={}", orgCode, keyword, pageNo, pageSize);
    PageResult<OrgUserDTO> result = orgService.getOrgUsers(orgCode, keyword, pageNo, pageSize);
    return ResponseWrapper.page(result);
}
```

- [ ] **Step 2: 添加 import**

```java
import com.bank.branch.platform.common.web.PageResult;
```

- [ ] **Step 3: 运行测试验证**

```bash
cd auth-permission-center && mvn test -Dtest=OrgControllerTest -q
```

- [ ] **Step 4: 提交**

```bash
git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/OrgController.java
git commit -m "fix(auth): G.2 getOrgUsers 添加分页支持"
```

---

## 阶段二: G.3 - OrgController.getOrgSubtree 返回类型修复

### Task 2: G.3.1 - OrgService 添加返回树形结构的方法

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/OrgService.java:52-64`

- [ ] **Step 1: 添加新方法 getOrgSubtreeAsTree**

```java
/**
 * 获取机构子树（含自身），返回树形结构
 *
 * @param orgCode 根机构编码
 * @return 子树节点列表（含 children）
 */
public List<OrgTreeNodeDTO> getOrgSubtreeAsTree(String orgCode) {
    ExtOrgInfo root = orgMapper.selectByOrgCode(orgCode);
    if (root == null) return List.of();
    // 使用已存在的 getOrgTree() 逻辑，从 root 开始构建
    List<OrgTreeNodeDTO> tree = getOrgTree();
    // 找到根节点并返回其子树（不含兄弟节点）
    return tree.stream()
        .filter(t -> t.getOrgCode().equals(orgCode))
        .findFirst()
        .map(t -> {
            List<OrgTreeNodeDTO> result = new ArrayList<>();
            result.add(t);
            return result;
        })
        .orElse(List.of());
}
```

- [ ] **Step 2: 提交**

```bash
git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/OrgService.java
git commit -m "feat(auth): OrgService 添加返回树形结构方法"
```

---

### Task 2: G.3.2 - OrgController.getOrgSubtree 改返回类型

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/OrgController.java:52-66`

- [ ] **Step 1: 修改方法返回类型**

```java
/**
 * 获取当前用户所在机构的子树
 * 基于当前登录用户的主机构编码，查询其机构子树（含自身）
 *
 * @return 当前用户机构子树中所有机构的列表
 */
@GetMapping("/subtree")
@Operation(summary = "获取当前用户机构子树", description = "返回当前用户主机构及其下属所有机构列表")
public ResponseWrapper<List<OrgTreeNodeDTO>> getOrgSubtree() {
    CurrentUserContext ctx = currentUserProvider.get();
    String orgCode = ctx.mainOrgCode();
    log.debug("[OrgController.getOrgSubtree] 获取机构子树 orgCode={}", orgCode);
    List<OrgTreeNodeDTO> nodes = orgService.getOrgSubtreeAsTree(orgCode);
    return ResponseWrapper.success(nodes);
}
```

- [ ] **Step 2: 运行测试验证**

```bash
cd auth-permission-center && mvn test -Dtest=OrgControllerTest -q
```

- [ ] **Step 3: 提交**

```bash
git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/OrgController.java
git commit -m "fix(auth): G.3 getOrgSubtree 返回类型改为 OrgTreeNodeDTO"
```

---

## 阶段三: F.1/F.3 - BizScopeController 路径修复

### Task 3: F.1/F.3.1 - BizScopeController 路径去掉尾部斜杠

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/BizScopeController.java:33-110`

- [ ] **Step 1: 修改路径注解**

```java
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/biz-scopes")  // 之前是 /api/admin/biz-scopes/
@Tag(name = "业务数据范围管理", description = "角色业务数据范围配置管理")
public class BizScopeController {

    // F.1 列表查询
    @GetMapping("")  // 之前是 "/"
    @Operation(summary = "分页查询业务数据范围列表")
    // ...

    // F.3 保存
    @PostMapping("")  // 之前是 "/"
    @Operation(summary = "保存业务数据范围配置", description = "存在则更新，不���在���新增（UPSERT 语义）")
    // ...

    // F.4 删除（路径带 {id}，保持不变）
    @DeleteMapping("/{id}")  // 保持不变
    // ...
}
```

- [ ] **Step 2: 运行测试验证**

```bash
cd auth-permission-center && mvn test -Dtest=BizScopeControllerTest -q
```

- [ ] **Step 3: 提交**

```bash
git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/BizScopeController.java
git commit -m "fix(auth): F.1/F.3 BizScopeController 路径去掉尾部斜杠"
```

---

## 阶段四: C.1/C.2 - UserRoleController 路径修复

### Task 4: C.1/C.2.1 - UserRoleController 路径去掉尾部斜杠

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/UserRoleController.java:38-90`

- [ ] **Step 1: 修改路径注解**

```java
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users/{userId}/roles")  // 之前是 /api/admin/users/{userId}/roles/
@Tag(name = "用户角色管理", description = "用户角色绑定/解绑操作")
public class UserRoleController {

    // C.1 查询
    @GetMapping("")  // 之前是 "/"
    @Operation(summary = "查询用户已绑定角色列表")
    // ...

    // C.2 绑定
    @PostMapping("")  // 之前是 "/"
    @Operation(summary = "批量绑定角色到用户")
    // ...

    // C.3 解绑（路径带 {roleId}，保持不变）
    @DeleteMapping("/{roleId}")  // 保持不变
    // ...
}
```

- [ ] **Step 2: 运行测试验证**

```bash
cd auth-permission-center && mvn test -Dtest=UserRoleControllerTest -q
```

- [ ] **Step 3: 提交**

```bash
git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/UserRoleController.java
git commit -m "fix(auth): C.1/C.2 UserRoleController 路径去掉尾部斜杠"
```

---

## 阶段五: A.3 - AuthController.current-user 字段补全

### Task 5: A.3.1 - CurrentUserContext 添加字段（修改 common-security）

**Files:**
- Modify: `common/common-security/src/main/java/com/bank/branch/platform/common/security/context/CurrentUserContext.java:19-50`
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/AuthService.java:70-142`

> **重要**：不能创建新的 `CurrentUserContextExt` record。因为 `CurrentUserProvider.get()` 从 Session 读取后强制转型为 `CurrentUserContext`，如果存入不同类型会导致 `ClassCastException`。正确做法是直接修改 common-security 中的 `CurrentUserContext` record，添加新字段。Java record 添加字段对现有调用方向后兼容（不访问新字段的代码不受影响）。

- [ ] **Step 1: CurrentUserContext.java 添加字段**

修改 common-security 中的 CurrentUserContext record，添加 4 个新字段：

```java
public record CurrentUserContext(
    String empId,
    String mainOrgCode,
    Set<String> roleIds,
    Set<String> roleCodes,
    Set<String> candidateGroupKeys,
    boolean systemAdmin,
    // 新增字段（向后兼容：不访问这些字段的现有代码不受影响）
    String username,
    String displayName,
    String mainOrgName,
    Integer orgLevel
) implements Serializable {
    // 保留原有方法不变
}
```

- [ ] **Step 2: AuthService.login 填充新字段**

找到 AuthService.login 方法中构建 CurrentUserContext 的代码（原第120-121行附近），修改为：

```java
// 在查询 mainOrgName 之后添加 orgLevel 查询：
Integer orgLevel = null;
if (mainOrgCode != null) {
    ExtOrgInfo org = orgMapper.selectByOrgCode(mainOrgCode);
    if (org != null) {
        mainOrgName = org.getOrgName();
        orgLevel = org.getOrgLevel();  // 新增：获取机构等级
    }
}

// 修改 CurrentUserContext 构造函数，填充新字段：
CurrentUserContext userCtx = new CurrentUserContext(
    user.getUserId(),
    mainOrgCode,
    roleIds,
    roleCodes,
    candidateGroupKeys,
    isAdmin,
    user.getUsername(),      // 新增
    user.getUserchnname(),   // 新增：中文姓名
    mainOrgName,             // 新增
    orgLevel                 // 新增
);
```

- [ ] **Step 3: 提交**

```bash
git add common/common-security/src/main/java/com/bank/branch/platform/common/security/context/CurrentUserContext.java
git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/AuthService.java
git commit -m "feat(auth): A.3 CurrentUserContext 添加 username/displayName/mainOrgName/orgLevel"
```

---

### Task 5: A.3.2 - AuthController.getCurrentUser 补全字段

**Files:**
- Modify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/AuthController.java:69-90`

- [ ] **Step 1: 添加辅助方法获取 roles/permissions/bizScopes**

在 AuthService 中添加方法（如果不存在）：

```java
/**
 * 获取用户的角色列表
 */
public List<RoleSimpleDTO> getUserRoles(String empId) {
    List<PtRole> roles = userRoleMapper.selectRolesByUserId(empId);
    return roles.stream().map(r -> {
        RoleSimpleDTO dto = new RoleSimpleDTO();
        dto.setRoleId(r.getRoleId());
        dto.setRoleCode(r.getRoleCode());
        dto.setRoleChName(r.getRoleChName());
        return dto;
    }).collect(Collectors.toList());
}

/**
 * 获取用户的资源 URL 列表
 */
public List<String> getUserResourceUrls(String empId) {
    PermissionSetRespDTO permissions = getUserPermissions(empId);
    return new ArrayList<>(permissions.getResourceUrls());
}

/**
 * 获取用户的 BizScope 映射
 */
public Map<String, String> getUserBizScopes(String empId) {
    PermissionSetRespDTO permissions = getUserPermissions(empId);
    return permissions.getBizScopes();
}
```

- [ ] **Step 2: 修改 getCurrentUser 方法（简化版，无需类型判断）**

由于 Task A.3.1 已将新字段添加到 CurrentUserContext，Controller 中可直接使用：

```java
@GetMapping("/current-user")
@Operation(summary = "获取当前用户信息", description = "返回当前登录用户的权限和身份信息")
public ResponseWrapper<CurrentUserRespDTO> getCurrentUser(HttpSession session) {
    log.debug("[AuthController.getCurrentUser] 获取当前用户请求");

    // 直接从 Session 获取 CurrentUserContext（Task A.3.1 已扩展字段）
    CurrentUserContext ctx = authService.getCurrentUser(session);

    CurrentUserRespDTO dto = new CurrentUserRespDTO();
    dto.setEmpId(ctx.empId());
    dto.setUsername(ctx.username());
    dto.setDisplayName(ctx.displayName());
    dto.setMainOrgCode(ctx.mainOrgCode());
    dto.setMainOrgName(ctx.mainOrgName());
    dto.setOrgLevel(ctx.orgLevel());
    dto.setIsSystemAdmin(ctx.systemAdmin());

    // 填充 roles/permissions/bizScopes
    dto.setRoles(authService.getUserRoles(ctx.empId()));
    dto.setPermissions(authService.getUserResourceUrls(ctx.empId()));
    dto.setBizScopes(authService.getUserBizScopes(ctx.empId()));

    return ResponseWrapper.success(dto);
}
```

- [ ] **Step 3: 运行测试验证**

```bash
cd auth-permission-center && mvn test -Dtest=AuthControllerTest -q
```

- [ ] **Step 4: 提交**

```bash
git add auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/AuthController.java
git commit -m "fix(auth): A.3 current-user 补全所有字段"
```

---

## 阶段六: 测试覆盖验证

### Task 6: 更新测试文件以适配新接口

**Files:**
- Modify: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/controller/OrgControllerTest.java`
- Modify: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/controller/AuthControllerTest.java`

- [ ] **Step 1: 更新 OrgControllerTest 测试 getOrgSubtree 返回类型**

修改测试断言，验证 children 字段存在：

```java
@Test
void getOrgSubtree_shouldReturnTreeWithChildren() throws Exception {
    // given
    CurrentUserContext ctx = new CurrentUserContext(
            "emp001", "ORG001", Set.of(), Set.of(), Set.of(), false);
    when(currentUserProvider.get()).thenReturn(ctx);

    OrgTreeNodeDTO orgDto = new OrgTreeNodeDTO();
    orgDto.setOrgCode("ORG001");
    orgDto.setOrgName("总行");
    orgDto.setChildren(List.of());  // 添加 children 字段
    when(orgService.getOrgSubtreeAsTree(anyString())).thenReturn(List.of(orgDto));

    // when & then - 验证返回树形结构
    mockMvc.perform(get("/api/orgs/subtree"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data[0].orgCode").value("ORG001"))
            .andExpect(jsonPath("$.data[0].children").exists());
}
```

- [ ] **Step 2: 更新 AuthControllerTest 测试 current-user 返回完整字段**

> 注意：Task A.3.1 已将新字段添加到 CurrentUserContext，所以测试中使用修改后的 CurrentUserContext。

```java
@Test
void getCurrentUser_shouldReturnAllFields() throws Exception {
    // given - CurrentUserContext 已有 username/displayName/mainOrgName/orgLevel 字段
    CurrentUserContext ctx = new CurrentUserContext(
            "emp001",  // empId
            "ORG001",  // mainOrgCode
            Set.of("R_001"),    // roleIds
            Set.of("CUST_MGR"), // roleCodes
            Set.of("ROLE:CUST_MGR"), // candidateGroupKeys
            false,       // systemAdmin
            "zhangsan",  // username (新增)
            "张三",       // displayName (新增)
            "总行",       // mainOrgName (新增)
            1            // orgLevel (新增)
    );
    when(authService.getCurrentUser(any(HttpSession.class))).thenReturn(ctx);

    when(authService.getUserRoles(anyString())).thenReturn(List.of());
    when(authService.getUserResourceUrls(anyString())).thenReturn(List.of());
    when(authService.getUserBizScopes(anyString())).thenReturn(Map.of());

    // when & then
    mockMvc.perform(get("/api/auth/current-user"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data.empId").value("emp001"))
            .andExpect(jsonPath("$.data.username").value("zhangsan"))
            .andExpect(jsonPath("$.data.displayName").value("张三"))
            .andExpect(jsonPath("$.data.mainOrgName").value("总行"))
            .andExpect(jsonPath("$.data.orgLevel").value(1))
            .andExpect(jsonPath("$.data.roles").isArray())
            .andExpect(jsonPath("$.data.permissions").isArray())
            .andExpect(jsonPath("$.data.bizScopes").isMap());
}
```

- [ ] **Step 3: 运行完整测试套件**

```bash
cd auth-permission-center && mvn test -q
```

- [ ] **Step 4: 提交**

```bash
git add auth-permission-center/src/test/
git commit -m "test(auth): 更新测试以适配接口对齐修改"
```

---

## 实施检查清单

每个 Task 完成后检查：

- [ ] 接口路径与文档完全一致
- [ ] 请求参数与文档一致
- [ ] 响应字段与文档一致
- [ ] @BizAuth 注解正确
- [ ] 测试通过

---

## 回滚方案

如有问题，可通过 git revert 快速回滚单个提交：

```bash
# 查看提交历史
git log --oneline -10

# 回滚单个提交
git revert <commit-hash>
```