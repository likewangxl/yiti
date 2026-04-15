# D-中模块审计实施计划

> **面向执行代理**：建议使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐任务执行。所有步骤使用 `- [ ]` 复选框记录进度。
>
> **规范依据**：`docs/superpowers/specs/2026-04-15-d-med-module-audit-design.md`（已评审通过并获用户批准）

**目标**：恢复 Maven 构建可执行，对 portal / customer / bizapp 三个漂移模块做清单驱动的静态审计，产出分级缺陷清单，同步根 `CLAUDE.md` 与 `bootstrap/CLAUDE.md`。**不改任何业务代码。**

**架构**：主会话主导基础设施修复与文档同步；3 × Explore 子代理并行执行模块代码静态审计；主会话对子代理报告做结构校验并汇总。产物分 6 次 git commit，每次独立可 revert。

**技术栈**：Maven 多模块、Git、Markdown（审计报告）、子代理分派（Explore subagent_type）。

**范围边界（重述）**：

- ✅ 纯静态代码审查（只读源码）
- ✅ 文档同步（根 `CLAUDE.md` + 新建 `bootstrap/CLAUDE.md`）
- ❌ 不修任何业务代码（缺陷只出清单）
- ❌ 不做 SQL / 性能 / 安全渗透
- ❌ 不审计 auth / governance / workflow（不在漂移范围）
- ❌ 不规划 performance / report（目录不存在）

---

## 任务总览

| 阶段 | 任务 | 执行方 | 产物 | 提交次数 |
|---|---|---|---|---|
| 0 | 基础设施修复 | 主会话 | pom 修复 + 垃圾清理 + mvn 验证 | 2 |
| 1 | 生成 checklist | 主会话 | `2026-04-15-module-audit-checklist.md` | 0（与阶段 2 合并提交）|
| 2 | 3 模块并行审计 | 3 × Explore 子代理 | `portal-audit.md` / `customer-audit.md` / `bizapp-audit.md` | 1 |
| 3 | 汇总总报告 | 主会话 | `2026-04-15-d-med-audit-report.md` | 1 |
| 4 | 文档同步 | 主会话 | 根 `CLAUDE.md` 更新 + `bootstrap/CLAUDE.md` 新建 | 2 |
| 5 | 停止与交接 | 主会话 | 交接信息（会话内） | 0 |

**总计 6 次提交**，编号与 spec §8 对齐（commit 0 已在 spec 落盘时提交）。

---

## 阶段 0：基础设施修复

### 任务 0.1：修父 pom.xml，补齐缺失的 dependencyManagement

**文件**：
- 修改：`pom.xml:111`（在 portal-content-center 声明后追加 2 条）

- [ ] **步骤 1：读 pom.xml 确认当前结构**

运行：读取 `pom.xml` 的 100-115 行（dependencyManagement 尾部段落）。

期望看到：`portal-content-center` 声明块终止于第 111 行 `</dependency>`，紧随第 112 行是 `<!-- 第三方 -->` 注释。

- [ ] **步骤 2：使用 Edit 追加两条声明**

在 `<!-- 第三方 -->` 注释**之前**插入：

```xml
            <!-- customer-marketing-center -->
            <dependency>
                <groupId>com.bank.branch.platform</groupId>
                <artifactId>customer-marketing-center</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- business-application-center -->
            <dependency>
                <groupId>com.bank.branch.platform</groupId>
                <artifactId>business-application-center</artifactId>
                <version>${project.version}</version>
            </dependency>

```

Edit 参数（示范）：

- `old_string`: 包含 portal-content-center 完整 `<dependency>` 块到 `<!-- 第三方 -->` 的 4-5 行上下文（唯一锚点）
- `new_string`: 上述上下文 + 新增的 2 个 `<dependency>` 块

缩进严格保持 12 个空格（对齐现有兄弟节点）。

- [ ] **步骤 3：读修改后的 pom.xml 验证插入成功**

运行：再读 pom.xml 100-130 行。

期望：customer-marketing-center / business-application-center 两条依赖声明位于 portal-content-center 之后、`<!-- 第三方 -->` 注释之前；`${project.version}` 占位符正确；缩进对齐。

- [ ] **步骤 4：提交 commit #1**

```bash
cd C:/Users/52140/Desktop/yiti && git add pom.xml && git commit -m "chore(pom): 补齐父 pom dependencyManagement 缺失的两个模块版本声明

恢复父 pom 对 customer-marketing-center 与 business-application-center 的
版本托管，使 bootstrap 等下游模块可以跨模块引用它们而不再需要显式声明版本，
解决 'dependencies.dependency.version is missing' 的 Maven 构建阻塞问题。

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

期望：git 显示 1 文件修改，10 行 insertions 左右。

---

### 任务 0.2：清理 JVM 崩溃日志垃圾

**文件**：
- 删除：仓库根目录下所有 `hs_err_pid*.log`（约 6 个）
- 删除：仓库根目录下所有 `replay_pid*.log`（约 6 个）
- 删除：`portal-content-center/hs_err_pid*.log`（约 20 个）
- 删除：`portal-content-center/replay_pid*.log`（约 7 个）
- 删除：`customer-marketing-center/hs_err_pid*.log`（1 个）
- 删除：`customer-marketing-center/replay_pid*.log`（1 个）

**注意**：这些文件不在 git 跟踪中（`git ls-files` 为空），直接从工作区删除即可，不会影响历史。

- [ ] **步骤 1：列出待删除文件（核对范围）**

```bash
cd C:/Users/52140/Desktop/yiti && find . -maxdepth 3 -name "hs_err_pid*.log" -o -name "replay_pid*.log" 2>/dev/null | sort
```

期望：看到约 36 个 `.log` 文件分布在根目录、portal-content-center、customer-marketing-center。

- [ ] **步骤 2：删除**

```bash
cd C:/Users/52140/Desktop/yiti && find . -maxdepth 3 \( -name "hs_err_pid*.log" -o -name "replay_pid*.log" \) -delete
```

期望：命令静默完成。

- [ ] **步骤 3：复核删除结果**

```bash
cd C:/Users/52140/Desktop/yiti && find . -maxdepth 3 \( -name "hs_err_pid*.log" -o -name "replay_pid*.log" \) 2>/dev/null
```

期望：无输出。

- [ ] **步骤 4：确认 git 无脏状态**

```bash
cd C:/Users/52140/Desktop/yiti && git status
```

期望：`working tree clean`（因为 `.gitignore` 已包含 `*.log`，这些文件从未被跟踪）。

**注意**：如果 `git status` 显示"待提交修改"，说明存在 tracked 的 log 文件，需要额外 `git add -u` 后提交；否则本任务不产生 commit，并入 commit #2 与 checklist 生成合并提交。

- [ ] **步骤 5：提交 commit #2（仅在存在需提交的删除时）**

```bash
# 仅在步骤 4 显示有 tracked log 文件被删除时执行
# 限定 pathspec 为 *.log，避免把无关删除一并 stage
cd C:/Users/52140/Desktop/yiti && git ls-files --deleted | grep -E "(hs_err|replay_pid).*\.log$" > /tmp/.to-stage.txt && cat /tmp/.to-stage.txt
# 若 /tmp/.to-stage.txt 非空再执行下一行
cd C:/Users/52140/Desktop/yiti && git rm --cached $(cat /tmp/.to-stage.txt) 2>/dev/null; git add -u -- '*hs_err_pid*.log' '*replay_pid*.log' && git commit -m "chore: 清理 JVM 崩溃日志垃圾 (hs_err_* / replay_*)

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

如步骤 4 已 `working tree clean`，跳过本步骤，在计划记录中注明"commit #2 无需生成，文件本未被跟踪"。

**兼容 Windows bash 注意**：上述 `/tmp/` 路径在 Git Bash / WSL 下可用；若用原生 Windows cmd 需调整为 `%TEMP%`。

---

### 任务 0.3：验证 mvn test 可通过

**注意**：此任务不产生 commit，结果决定是否进入下一阶段。

- [ ] **步骤 1：运行聚合测试**

```bash
cd C:/Users/52140/Desktop/yiti && mvn -pl portal-content-center,customer-marketing-center,business-application-center -am test 2>&1 | tail -80
```

期望（正常路径）：`BUILD SUCCESS`，所有 3 个模块测试通过。

- [ ] **步骤 2：根据结果分叉**

读 mvn 输出并判断：

| 观察 | 判定 | 动作 |
|---|---|---|
| `BUILD SUCCESS` 且所有 `@Test` 通过 | 正常路径 | 继续阶段 1 |
| `BUILD FAILURE` 且有 `compilation failure` / `Could not resolve dependencies` / `classpath ...` | **编译失败** | 执行步骤 3 |
| `BUILD FAILURE` 但 Maven 到达 Surefire 并报告 `Tests failed` | **测试失败** | 执行步骤 4 |

- [ ] **步骤 3：编译失败分支（仅在编译失败时执行）**

按 spec §6 阶段 0.3 规则：

> 视为阶段 0.1 未完成的延续；主会话自行诊断根因并向用户上报，**不扩大修复范围**（不碰业务代码）；若根因非 pom 且无法用"补 dependencyManagement"级别的最小改动解决，上报后转 D-超深讨论

具体动作：
1. 收集编译错误首条：`mvn ... 2>&1 | grep -A5 "COMPILATION ERROR\|Could not resolve\|missing"`
2. 判断是否仍为 dependencyManagement 级别问题
3. 若是 → 作为 0.1 的延续修复，回到任务 0.1 追加依赖声明
4. 若否（例如某 java 源文件本身编译错） → **立即停止**，向用户汇报错误片段与初步诊断，等待用户决定

- [ ] **步骤 4：测试失败分支（仅在测试失败时执行）**

按 spec §6 阶段 0.3 规则：

> 立即停止，向用户上报，转入 D-深讨论（因为测试失败意味着代码 vs 功能规范有实质偏离）

具体动作：
1. 收集失败测试列表：`mvn ... 2>&1 | grep -E "Tests run.*Failures|FAILED"`
2. 汇报到用户：哪个模块、哪些用例、首条错误摘要
3. **不继续阶段 1**，等待用户决定是否升级到 D-深

---

## 阶段 1：生成审计 Checklist

### 任务 1.1：创建 audits 目录并写入 checklist 文件

**文件**：
- 创建：`docs/superpowers/audits/2026-04-15-module-audit-checklist.md`

- [ ] **步骤 1：创建 audits 目录**

```bash
mkdir -p C:/Users/52140/Desktop/yiti/docs/superpowers/audits
```

- [ ] **步骤 2：写入 checklist 文件**

使用 `Write` 工具创建 `docs/superpowers/audits/2026-04-15-module-audit-checklist.md`，内容结构（完整 25 条，每条含 ID / 所属大类 / 标题 / 规范依据 / 检查方法 / 期望输出格式）：

```markdown
# 模块审计 Checklist（D-中版）

> Date: 2026-04-15
> Applicable to: portal-content-center / customer-marketing-center / business-application-center
> Source: `docs/superpowers/specs/2026-04-15-d-med-module-audit-design.md` §4
>
> **子代理禁止自行增删 ID**。本清单共 25 条（V1:8 / V2:7 / V3:5 / V4:5），IDs 固定为 V1-01 ~ V4-05。

## 检查结论标记规范

- ✅ 通过：所有检查点均符合规范
- ⚠️ 部分通过：存在个别偏离但不构成缺陷
- ❌ 未通过：存在至少 1 条缺陷（同时生成 D-NNN 条目）
- N/A：本模块不适用此条（需说明理由）
- 🟡 假设性验证：无法在静态审计阶段完全确认，标注"假设成立/不成立"

---

## V1 - 规范合规（8 条）

### V1-01 跨模块调用是否只走 *Api（不直连 mapper/entity/serviceImpl）

- **规范依据**：根 `CLAUDE.md` 第 85 行（依赖规则 1）+ 第 192 行（开发 Checklist 3）
- **检查方法**：grep 模块下所有 `import com.bank.branch.platform.<其他模块>.*`，确认只命中 `api` 包（不命中 `mapper` / `entity` / `service.impl`）
- **期望输出**：列出所有违规 import（文件:行号 + import 语句）

### V1-02 所有对外接口是否声明 @BizAuth

- **规范依据**：根 `CLAUDE.md` 第 86 行（依赖规则 2）+ 第 191 行（开发 Checklist 2）
- **检查方法**：grep 模块下所有 `@RestController` / `@RequestMapping` 方法，检查每个 public 方法（@GetMapping / @PostMapping / @PutMapping / @DeleteMapping）是否有 `@BizAuth` 注解
- **期望输出**：列出缺失 @BizAuth 的端点（文件:行号 + 方法签名）

### V1-03 PT_RESOURCE 登记【假设性验证】

- **规范依据**：根 `CLAUDE.md` 第 86 行（依赖规则 2）
- **检查方法**：不做硬检查，仅记录 Controller 方法数量，供后续数据库比对使用
- **期望输出**：总方法数统计，标注"假设成立"（如需硬验证，需要数据库访问，超出 D-中范围）

### V1-04 写操作是否在 Service 层做二次权限校验

- **规范依据**：根 `CLAUDE.md` 第 193 行（开发 Checklist 4）
- **检查方法**：定位所有 `@Transactional` 标注的 Service 写方法，检查是否调用了 `DataPermissionChecker` / `@BizAuth` 或等价权限校验
- **期望输出**：列出无二次权限校验的写方法（文件:行号 + 方法名）

### V1-05 高危操作是否独立 URL + 独立授权 + 独立审计

- **规范依据**：根 `CLAUDE.md` 第 163 行（安全规范）+ 第 195 行（开发 Checklist 6）
- **检查方法**：识别删除/批量/导出等高危操作，确认是否使用独立 URL 路径（非复用 CRUD URL）并带 `@AuditLog` 注解
- **期望输出**：列出复用 URL 或缺失审计的高危操作

### V1-06 读接口 / 导出接口是否统一应用 DATA_SCOPE

- **规范依据**：`docs/common-dev-guide.md` §5 + 根 `CLAUDE.md` 第 194 行（开发 Checklist 5）
- **检查方法**：检查 Mapper XML / Mapper 注解 SQL 中，涉及业务数据的查询是否引用了 DATA_SCOPE 过滤片段
- **期望输出**：列出未应用 DATA_SCOPE 的查询（文件:行号 + SQL 片段）

### V1-07 流程类业务是否维护 business_key 与 biz_process_map

- **规范依据**：根 `CLAUDE.md` 第 196 行（开发 Checklist 7）
- **检查方法**：对有 Flowable 流程介入的模块（bizapp / customer 的审批场景），检查流程启动调用是否设置了 businessKey 并写入 biz_process_map
- **期望输出**：列出未维护 businessKey 的流程启动点

### V1-08 错误码前缀是否符合模块约定（PORTAL / CUST / BIZ）

- **规范依据**：`docs/common-dev-guide.md` §2（错误码模块前缀注册表）
- **检查方法**：grep 模块下 `ErrorCode` enum 文件，确认所有错误码以 PORTAL-/CUST-/BIZ- 开头
- **期望输出**：列出前缀错误的错误码（文件:行号 + 错误码常量）

---

## V2 - 代码质量（7 条）

### V2-01 重复代码（跨类 / 跨方法）

- **规范依据**：DRY 原则（CLAUDE.md §核心设计原则：简单优于复杂）
- **检查方法**：人工浏览 Service 层寻找明显重复块（> 10 行相似逻辑在 2 个以上类出现）
- **期望输出**：列出重复代码对（文件A:行号 ↔ 文件B:行号 + 简述）

### V2-02 过大文件（单文件 > 500 行警示）

- **规范依据**：CLAUDE.md §设计原则（单元应有清晰边界）
- **检查方法**：`wc -l` 统计每个 .java 文件行数
- **期望输出**：列出 > 500 行的文件（文件路径 + 行数）

### V2-03 死代码 / 未调用方法 / 无用 import

- **规范依据**：代码质量基本要求
- **检查方法**：识别所有 public 方法检查是否有调用方（基于 grep 即可得粗判），识别 import 后未使用的类
- **期望输出**：列出可疑死方法（文件:行号 + 方法名），无用 import（文件:行号）

### V2-04 异常处理完整性（是否吞异常 / 是否泄露内部信息 / catch(Exception e) 滥用）

- **规范依据**：代码质量基本要求
- **检查方法**：grep `catch (Exception` / `catch (Throwable` / 空 catch 块 / `e.printStackTrace()` / 异常 message 直接返回给用户
- **期望输出**：列出异常处理问题（文件:行号 + 问题类型）

### V2-05 敏感字段日志脱敏（手机号 / 身份证 / 账号 / 金额）

- **规范依据**：根 `CLAUDE.md` 第 164 行（敏感字段日志必须脱敏）
- **检查方法**：grep 日志调用 `log.info/debug/error(...)`，检查是否有直接输出 `phone` / `idCard` / `mobile` / `account` / `amount` 字段
- **期望输出**：列出疑似泄露的日志行（文件:行号 + 日志语句）

### V2-06 Service 类 + public 方法注释齐全性

- **规范依据**：根 `CLAUDE.md` 第 135 行（代码风格第 1 条）+ 第 197 行（开发 Checklist 8）
- **检查方法**：检查所有 `*Service` / `*ServiceImpl` 类的 Javadoc，以及类内所有 public 方法的 Javadoc
- **期望输出**：列出缺注释的类 / 方法（文件:行号 + 类名/方法名）

### V2-07 是否使用 Object 作为通用参数

- **规范依据**：根 `CLAUDE.md` 第 137 行（禁止 any 等价物）
- **检查方法**：grep `Object ` 作为方法参数或字段类型（排除 equals/hashCode/toString/Object[] 构造器等合法用法）
- **期望输出**：列出违规用法（文件:行号 + 方法签名）

---

## V3 - 测试完备性（5 条）

### V3-01 是否存在"事后补测试"痕迹

**识别启发式（命中任一即判红）**：

1. 测试方法命名仅 `testXxx` / `testXxxSuccess` / `testCreate` 等 happy path 模式，无 `when<边界条件>_then<预期>` 结构
2. 单个测试方法同时覆盖多个逻辑分支（一个 `@Test` 内多次变更状态并多次断言）
3. Service 层断言只 verify "非 null" 或 verify 调用次数，而不 verify 关键业务字段的具体值
4. 核心业务分支（状态转换、场景路由、错误码触发）零对应测试用例

- **规范依据**：根 `CLAUDE.md` §TDD 绝对红线（禁止事后狂补测试）
- **检查方法**：抽查 Service 测试类，对照上述 4 条启发式逐条判断
- **期望输出**：命中启发式的测试类清单 + 命中条目（文件:类名 + 启发式 1/2/3/4）

### V3-02 单元测试 vs 集成测试边界（单元测试是否误用 SpringBootTest）

- **规范依据**：测试分层最佳实践
- **检查方法**：grep `@SpringBootTest` 在单元测试（非 `*IT` / `*IntegrationTest` 命名）中的使用
- **期望输出**：列出误用 @SpringBootTest 的单元测试类

### V3-03 核心业务分支覆盖（状态机转换、场景路由、错误码触发路径）

- **规范依据**：测试完备性基本要求
- **检查方法**：识别 Service 内所有 if/switch/状态转换分支，对照测试方法名确认是否有对应用例
- **期望输出**：未覆盖的分支（文件:行号 + 分支条件 + 缺失的测试场景）

### V3-04 过度 mock 识别（是否 mock 掉了本应 verify 的逻辑）

- **规范依据**：测试质量基本要求
- **检查方法**：在 Service 测试中，识别 `when(xxx.yyy(...)).thenReturn(...)` 对本模块 Mapper / 业务 bean 的 mock，是否导致测试变成"自证式"（只验证自己的 mock 被调用）
- **期望输出**：列出过度 mock 的测试方法（文件:行号 + 问题描述）

### V3-05 @Transactional 回滚路径测试覆盖

- **规范依据**：测试完备性基本要求
- **检查方法**：对每个 `@Transactional` 写方法，检查是否有对应的"异常路径 / 回滚验证"测试
- **期望输出**：无回滚测试的事务方法（文件:行号 + 方法名）

---

## V4 - 文档一致性（5 条）

### V4-01 模块级 CLAUDE.md 自述的端点数 vs 实际 Controller 端点数

- **规范依据**：文档与代码一致性
- **检查方法**：读模块 CLAUDE.md 声称的"X 个端点"，对比 grep `@GetMapping|@PostMapping|@PutMapping|@DeleteMapping|@RequestMapping` 的实际计数
- **期望输出**：`<claim> vs <actual>`，偏差 > 5% 视为 Important

### V4-02 模块级 CLAUDE.md 自述的 Service / Facade 类清单 vs 实际文件

- **规范依据**：文档与代码一致性
- **检查方法**：读 CLAUDE.md 列出的 Service / Facade 清单，对比 `find . -name "*Service*.java" -o -name "*Facade*.java"` 的实际文件
- **期望输出**：清单不一致的条目（缺失 or 未列出）

### V4-03 模块级 CLAUDE.md 自述的测试用例数 vs 实际 @Test 计数

- **规范依据**：文档与代码一致性
- **检查方法**：读 CLAUDE.md 声称的"N 测试"，对比 `grep -r "@Test" src/test --include="*.java"` 的实际计数
- **期望输出**：`<claim> vs <actual>`，偏差 > 5% 视为 Important

### V4-04 错误码 enum 清单 vs CLAUDE.md 列出的错误码前缀

- **规范依据**：文档与代码一致性
- **检查方法**：读 CLAUDE.md 声称的"错误码前缀 X-"，对比 ErrorCode enum 类的实际使用前缀
- **期望输出**：不一致项（文档声称 vs 实际 enum）

### V4-05 状态机描述 vs 实际 Service 转换代码

- **规范依据**：文档与代码一致性
- **检查方法**：若 CLAUDE.md 描述了状态机（如"审批 → 入池 → 认领"），对照 Service 内的状态转换代码检查状态节点、转移条件是否一致
- **期望输出**：状态/转移不一致项（文档位置 vs 代码位置）

---

## 使用须知（给子代理）

1. 逐条执行（V1-01 → V4-05），不得跳过，不得新增 ID
2. 每条输出一个"结论标记"（✅/⚠️/❌/N/A/🟡），结论为 ❌ 时必须生成至少 1 条 D-NNN 缺陷
3. 缺陷详单按发现顺序编号（D-001 → D-NNN），严重程度分 Critical / Important / Minor
4. 每条缺陷必须齐全 5 字段：位置 / 规范依据 / 问题描述 / 建议修复 / 证据
5. 严禁修改任何业务代码；仅读取，不编辑
```

- [ ] **步骤 3：读回验证 25 条 ID 完整**

```bash
cd C:/Users/52140/Desktop/yiti && grep -E "^### V[1-4]-0[1-9]" docs/superpowers/audits/2026-04-15-module-audit-checklist.md | wc -l
```

期望：输出 `25`。

```bash
cd C:/Users/52140/Desktop/yiti && grep -cE "^### V1-" docs/superpowers/audits/2026-04-15-module-audit-checklist.md
cd C:/Users/52140/Desktop/yiti && grep -cE "^### V2-" docs/superpowers/audits/2026-04-15-module-audit-checklist.md
cd C:/Users/52140/Desktop/yiti && grep -cE "^### V3-" docs/superpowers/audits/2026-04-15-module-audit-checklist.md
cd C:/Users/52140/Desktop/yiti && grep -cE "^### V4-" docs/superpowers/audits/2026-04-15-module-audit-checklist.md
```

期望：分别输出 `8` / `7` / `5` / `5`。

**注意**：此任务不产生 commit，与阶段 2 合并提交为 commit #3。

---

## 阶段 2：并行模块审计

### 任务 2.1：派发 3 个 Explore 子代理并行审计

**文件**：
- 创建：`docs/superpowers/audits/2026-04-15-portal-audit.md`
- 创建：`docs/superpowers/audits/2026-04-15-customer-audit.md`
- 创建：`docs/superpowers/audits/2026-04-15-bizapp-audit.md`

**关键要求**：3 个子代理必须在**同一条 assistant 消息中**作为多个 Agent 调用并行派发（不要串行）。

- [ ] **步骤 1：准备统一任务书模板**

每个子代理的 prompt 结构（变量部分为 `{module}`）：

```
任务：对 {module} 模块执行 D-中静态审计，按固定 checklist 逐条打勾，产出 Markdown 报告。

背景：
- 当前项目 portal/customer/bizapp 3 个模块的代码已实现但根 CLAUDE.md 标签滞后；本次审计为"改标签前先确认代码质量"的静态审查。
- 你是 3 个并行子代理之一，各自审计 1 个模块，互不影响。
- 范围边界：**只读不写**，不得编辑任何业务代码。仅产出审计报告。

输入文件（必读）：
1. 审计清单（强制逐条执行）：docs/superpowers/audits/2026-04-15-module-audit-checklist.md
2. 规范依据 1：docs/common-dev-guide.md
3. 规范依据 2：C:/Users/52140/Desktop/yiti/CLAUDE.md（根目录）
4. 自述依据：{module}/CLAUDE.md
5. 目标模块代码：{module}/src/

执行流程：
1. 先通读 4 个输入文件
2. 逐条（V1-01 → V4-05，共 25 条）检查模块代码
3. 每条给出结论标记（✅/⚠️/❌/N/A/🟡）
4. ❌ 结论必须生成 D-NNN 缺陷条目，齐 5 字段：位置 / 规范依据 / 问题描述 / 建议修复 / 证据（3-5 行关键代码）
5. 产出报告落盘到：docs/superpowers/audits/2026-04-15-{module-short}-audit.md
   - portal-content-center → 2026-04-15-portal-audit.md
   - customer-marketing-center → 2026-04-15-customer-audit.md
   - business-application-center → 2026-04-15-bizapp-audit.md

产出格式（强制，见 spec §7）：
```markdown
# {module} 审计报告

> Date: 2026-04-15
> Auditor: Explore subagent
> Checklist version: 2026-04-15-module-audit-checklist.md

## 执行摘要

| 大类 | Critical | Important | Minor | 合计 |
|---|---|---|---|---|
| V1 规范合规 | n | n | n | n |
| V2 代码质量 | n | n | n | n |
| V3 测试完备性 | n | n | n | n |
| V4 文档一致性 | n | n | n | n |
| **合计** | n | n | n | n |

## Checklist 通过情况

| ID | 标题 | 结论 | 说明 |
|---|---|---|---|
| V1-01 | 跨模块调用是否只走 *Api | ✅ | — |
| V1-02 | @BizAuth 齐全 | ❌ | 见 D-001 |
| ... （共 25 行）|

## 缺陷详单

### D-001 [Critical] V1-02 | <简短标题>

- **位置**: `{module}/src/.../FileName.java:142`
- **规范依据**: `common-dev-guide.md §4.3` / 根 `CLAUDE.md` 第 190 行
- **问题描述**: ...
- **建议修复**: ...
- **证据**:
  \`\`\`java
  // 3-5 行关键代码
  \`\`\`

### D-002 [Important] V2-01 | ...
...
```

严重程度定义：
- Critical：违反 CLAUDE.md 明文禁止条款 / 可能导致运行时错误 / 存在安全隐患 / 权限绕过
- Important：违反最佳实践 / 影响可维护性 / 测试覆盖重大缺口 / 文档与代码不一致
- Minor：代码风格 / 注释缺失 / 轻微重复 / 命名不一致

报告完成标准（主会话会做结构校验）：
- "执行摘要"矩阵表存在且 5 行合计齐全
- "Checklist 通过情况"表恰好 25 行（V1-01 ~ V4-05）
- 所有 D-NNN 缺陷齐 5 字段（位置 / 规范依据 / 问题描述 / 建议修复 / 证据）
- 编号连续（D-001, D-002, ...）

**严禁事项**：
- 严禁编辑任何 .java 文件
- 严禁新增或删除 checklist ID
- 严禁修改 checklist 文件本身
- 报告产出仅限上述目标文件

任务完成后，回复"完成"并附简短汇总（总缺陷数 / Critical 数 / 最严重 Top 3）。
```

- [ ] **步骤 2：在同一消息内并行派发 3 个 Agent 调用**

使用单条 assistant 消息包含 3 个 Agent 工具调用（subagent_type=Explore），分别针对：
- `portal-content-center`
- `customer-marketing-center`
- `business-application-center`

每个调用的 `description` 字段（3-5 词）：
- `Audit portal-content-center module`
- `Audit customer-marketing-center module`
- `Audit business-application-center module`

不要使用 `run_in_background`（需在当前会话等待报告完成后继续）。

- [ ] **步骤 3：等待 3 个子代理全部返回**

3 个报告应已落盘到：
- `docs/superpowers/audits/2026-04-15-portal-audit.md`
- `docs/superpowers/audits/2026-04-15-customer-audit.md`
- `docs/superpowers/audits/2026-04-15-bizapp-audit.md`

---

### 任务 2.2：主会话对 3 份报告做结构校验

**注意**：对应 spec §7.1 主会话收验规则。

- [ ] **步骤 1：验证 3 个文件存在**

```bash
cd C:/Users/52140/Desktop/yiti && ls docs/superpowers/audits/2026-04-15-{portal,customer,bizapp}-audit.md
```

期望：3 个文件均存在。

- [ ] **步骤 2：逐份做 4 项结构校验**

对每份报告做以下检查（可用 Read + Grep 组合）：

**校验项 A：存在"执行摘要"矩阵表**

```bash
cd C:/Users/52140/Desktop/yiti && grep -c "^| V1 规范合规" docs/superpowers/audits/2026-04-15-portal-audit.md
```

期望：≥ 1。

**校验项 B：存在"Checklist 通过情况"全量 25 行表**

```bash
cd C:/Users/52140/Desktop/yiti && grep -cE "^\| V[1-4]-0[1-9] \|" docs/superpowers/audits/2026-04-15-portal-audit.md
```

期望：`25`。

**校验项 C：缺陷详单 ID 是否以 D- 开头且顺序编号**

```bash
cd C:/Users/52140/Desktop/yiti && grep -E "^### D-[0-9]{3} " docs/superpowers/audits/2026-04-15-portal-audit.md | head -5
```

检查编号是否 D-001, D-002, D-003... 连续。

**校验项 D：每条缺陷齐 5 字段**

对每个 D-NNN 条目检查是否包含以下 5 个加粗字段：
- `- **位置**:`
- `- **规范依据**:`
- `- **问题描述**:`
- `- **建议修复**:`
- `- **证据**:`

```bash
cd C:/Users/52140/Desktop/yiti && grep -cE "^- \*\*(位置|规范依据|问题描述|建议修复|证据)\*\*" docs/superpowers/audits/2026-04-15-portal-audit.md
```

期望：缺陷数 × 5 = 总匹配数（如果报告里有 8 个缺陷，应匹配 40 次）。

- [ ] **步骤 3：校验不通过时回写子代理（最多 1 次）**

按 spec §7.1：

> 校验不通过则主会话一次性回写子代理（通过 SendMessage）要求补齐，最多 1 次；仍不合规则按现有报告汇总并在总报告中标注"该模块报告格式部分缺失"。

**实现机制**（优先级从高到低，择可用即采用）：

1. **优先使用 SendMessage 续派**：任务 2.1 步骤 2 派发每个子代理返回后会附带 `agentId`（形如 `"agentId: aa47bbb...（use SendMessage with to: '...' to continue this agent）"`）。主会话捕获该 ID，使用 SendMessage 工具以 `to: <agentId>` 发送回写消息，子代理在原有上下文内续执行（保留它已阅读的 checklist / 模块代码上下文，token 开销低）。
2. **若 SendMessage 工具不可用或 agentId 未返回**：退回到重派新 Explore 子代理（Agent 工具 subagent_type=Explore），prompt 中提供：
   - 原任务书（任务 2.1 步骤 1 的模板）
   - 当前偏离的报告文件路径
   - 具体偏离的校验项清单
   - 明确要求："请读取当前报告 + 按清单补齐缺失字段 + 覆盖原文件。仅允许补齐格式，不得修改结论。"

回写 / 重派消息模板（两种机制共用）：

```
你的报告 docs/superpowers/audits/2026-04-15-{module}-audit.md 结构校验未通过：

[列出具体校验项：如 "执行摘要矩阵表缺 V3 行" / "缺陷 D-005 缺少 **证据** 字段"]

请补齐后覆盖原文件。仅允许补齐格式，不得修改结论。
```

若二次仍不合规，在总报告（阶段 3 产物）§6 标注该模块"报告格式部分缺失"，继续推进阶段 3，不再尝试第三次。

---

### 任务 2.3：提交阶段 1 + 阶段 2 产物（commit #3）

- [ ] **步骤 1：确认 4 个文件存在**

```bash
cd C:/Users/52140/Desktop/yiti && ls docs/superpowers/audits/
```

期望看到：
- `2026-04-15-module-audit-checklist.md`
- `2026-04-15-portal-audit.md`
- `2026-04-15-customer-audit.md`
- `2026-04-15-bizapp-audit.md`

- [ ] **步骤 2：提交**

```bash
cd C:/Users/52140/Desktop/yiti && git add docs/superpowers/audits/2026-04-15-module-audit-checklist.md docs/superpowers/audits/2026-04-15-portal-audit.md docs/superpowers/audits/2026-04-15-customer-audit.md docs/superpowers/audits/2026-04-15-bizapp-audit.md && git commit -m "docs(audit): 生成 D-中模块审计 checklist 与 3 份模块报告

- checklist: 25 条（V1:8 / V2:7 / V3:5 / V4:5）
- portal / customer / bizapp 三模块并行审计报告

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

期望：4 文件新增，git 提交成功。

---

## 阶段 3：汇总总报告

### 任务 3.1：合并 3 份模块报告为总报告

**文件**：
- 创建：`docs/superpowers/audits/2026-04-15-d-med-audit-report.md`

- [ ] **步骤 1：读 3 份模块报告（关键数据提取）**

逐份读 portal/customer/bizapp 三份 audit 报告，提取：
- 执行摘要矩阵（4x4 表）
- Critical / Important / Minor 数量
- 所有 Critical 条目的标题 + 位置
- Checklist 通过情况（谁 ❌ 了哪些条目）

- [ ] **步骤 2：识别跨模块共性问题**

对比 3 份报告的 Checklist 通过情况表，识别：
- 3 模块都 ❌ 的条目（强共性）
- 2 模块 ❌ 的条目（中共性）
- 仅 1 模块 ❌ 的条目（个性）

特别关注 V3 测试完备性、V4 文档一致性类别的共性问题。

- [ ] **步骤 3：写总报告**

使用 Write 工具创建 `docs/superpowers/audits/2026-04-15-d-med-audit-report.md`，结构：

```markdown
# D-中模块审计总报告

> Date: 2026-04-15
> Audit depth: D-中（清单驱动 + 3 模块并行静态审计）
> Spec: `docs/superpowers/specs/2026-04-15-d-med-module-audit-design.md`
> Checklist: `docs/superpowers/audits/2026-04-15-module-audit-checklist.md`

## 1. 执行摘要

### 1.1 总缺陷数量矩阵

| 模块 | Critical | Important | Minor | 合计 |
|---|---|---|---|---|
| portal-content-center | n | n | n | n |
| customer-marketing-center | n | n | n | n |
| business-application-center | n | n | n | n |
| **合计** | n | n | n | n |

### 1.2 基础设施状态

- 父 pom dependencyManagement：已修复（commit #1）
- JVM 崩溃日志垃圾：已清理
- `mvn test` 验证结果：[正常 / 编译失败 / 测试失败 —— 填阶段 0.3 实际结果]

## 2. 跨模块共性问题

### 2.1 强共性（3 模块均命中）

[列出所有 3 模块都 ❌ 的 checklist 条目，每条给出：ID、标题、3 模块对应的 D-NNN 编号、修复建议的共性方向]

### 2.2 中共性（2 模块命中）

...

### 2.3 其他个性问题

参见各模块详细报告。

## 3. 各模块报告链接

- [portal-content-center 审计报告](2026-04-15-portal-audit.md)
- [customer-marketing-center 审计报告](2026-04-15-customer-audit.md)
- [business-application-center 审计报告](2026-04-15-bizapp-audit.md)

## 4. 建议整改优先级

### 4.1 P0（立即整改）

[所有 Critical 条目，按影响面排序]

### 4.2 P1（下一迭代）

[跨模块共性 Important 条目]

### 4.3 P2（可延后）

[个别模块的 Important + 全部 Minor]

## 5. 后续路径

按 spec §6 阶段 5 交接：

- 选项甲：启动 TDD 整改（writing-plans → 子代理 TDD 修复每个 Critical / Important 项）
- 选项乙：仅保留报告，暂不修复，下次开发新功能时附带整改
- 选项丙：发现报告内容严重偏离，需要升级为 D-深审计

主会话等待用户决策。

## 6. 报告格式缺陷（如有）

[若阶段 2 校验中有模块报告格式部分缺失，在此标注]
```

- [ ] **步骤 4：提交 commit #4**

```bash
cd C:/Users/52140/Desktop/yiti && git add docs/superpowers/audits/2026-04-15-d-med-audit-report.md && git commit -m "docs(audit): 汇总 D-中模块审计总报告

总缺陷数 / 跨模块共性问题 / 整改优先级建议

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## 阶段 4：文档同步

### 任务 4.1：更新根 CLAUDE.md 的 5 处位置

**文件**：
- 修改：`CLAUDE.md:52-58`（"当前已实现的模块"表）
- 修改：`CLAUDE.md:60-65`（"尚未实现的模块"列表）
- 修改：`CLAUDE.md:67-79`（"当前模块依赖图"）
- 修改：`CLAUDE.md:94-107`（"包结构规范"状态标记）
- 修改：`CLAUDE.md:207-211`（"模块级 CLAUDE.md"链接清单）

- [ ] **步骤 1：修改"当前已实现的模块"表（第 52-58 行）**

在表格现有 5 行（common / auth / governance / workflow / bootstrap）之间适当插入 3 行：
- portal-content-center（在 workflow 之后、bootstrap 之前）
- customer-marketing-center（同上）
- business-application-center（同上）

使用 Edit 工具。old_string 应覆盖从 `| \`workflow-center\` |` 行到 `| \`bootstrap\` |` 行的唯一片段。

新表主体（示例 — 具体描述文字保持与模块 CLAUDE.md 自述一致）：

```
| `common` | com.bank.branch.platform.common.* | 已完成 | 公共基础设施层 (5 个子模块) |
| `auth-permission-center` | com.bank.branch.platform.auth | 已完成 | 认证授权中心 (RBAC + 数据范围) |
| `system-governance-center` | com.bank.branch.platform.governance | 已完成 | 系统治理中心 (7 大治理域) |
| `workflow-center` | com.bank.branch.platform.workflow | 已完成 | 工作流中心 (Flowable 7.0.1 集成) |
| `portal-content-center` | com.bank.branch.platform.portal | 已完成 | 门户与内容中心 |
| `customer-marketing-center` | com.bank.branch.platform.customer | 已完成 | 客户营销中心 |
| `business-application-center` | com.bank.branch.platform.bizapp | 已完成 | 业务申请中心 |
| `bootstrap` | com.bank.branch.platform | 已完成 | Spring Boot 启动入口 |
```

- [ ] **步骤 2：修改"尚未实现的模块"列表（第 60-65 行）**

old_string：

```
**尚未实现的模块** (代码骨架和 DDL 已存在):
- `portal-content-center` (门户与内容中心)
- `customer-marketing-center` (客户营销中心)
- `business-application-center` (业务申请中心)
- `performance-engine-center` (绩效计算中心)
- `report-analytics-center` (报表分析中心)
```

new_string：

```
**尚未实现的模块** (DDL 已存在，代码骨架待创建):
- `performance-engine-center` (绩效计算中心)
- `report-analytics-center` (报表分析中心)
```

- [ ] **步骤 3：扩展"当前模块依赖图"（第 67-79 行）**

**先实测再编辑**（评审建议采纳）：在修改前先读取以下文件确认真实依赖顺序，不要直接使用模板：

```bash
cd C:/Users/52140/Desktop/yiti && grep -A3 "<artifactId>" portal-content-center/pom.xml | grep -B1 "branch.platform" | head -40
cd C:/Users/52140/Desktop/yiti && grep -A3 "<artifactId>" customer-marketing-center/pom.xml | grep -B1 "branch.platform" | head -40
cd C:/Users/52140/Desktop/yiti && grep -A3 "<artifactId>" business-application-center/pom.xml | grep -B1 "branch.platform" | head -40
```

并读 `docs/modules/portal-content-center/09-依赖契约摘要.md`（若存在）、customer 与 bizapp 的同名文件。以这 3 个模块 pom.xml 中实际声明的 dependency 为权威，template 中的链式写法仅作示例。

old_string（完整的依赖图代码块）：

```
### 当前模块依赖图

​```
common (common-web → common-trace → common-security → common-aop → common-db)
  ↑
auth-permission-center (无其他业务模块依赖)
  ↑
system-governance-center (依赖 auth)  ← 被 workflow 依赖
  ↑
workflow-center (依赖 auth + governance)

bootstrap (依赖所有已实现模块, 是唯一的 Spring Boot 启动入口)
​```
```

new_string（扩展后的依赖图，包含 portal/customer/bizapp；具体层级以模块 CLAUDE.md 与 docs/modules/09 依赖契约摘要为准）：

```
### 当前模块依赖图

​```
common (common-web → common-trace → common-security → common-aop → common-db)
  ↑
auth-permission-center (无其他业务模块依赖)
  ↑
system-governance-center (依赖 auth)
  ↑
workflow-center (依赖 auth + governance)
  ↑
portal-content-center (依赖 auth + governance + workflow)
  ↑
customer-marketing-center (依赖 auth + workflow + portal)
  ↑
business-application-center (依赖 auth + workflow + customer + portal)

bootstrap (依赖所有已实现模块, 是唯一的 Spring Boot 启动入口)
​```
```

**注意**：依赖关系的具体声明以各模块 CLAUDE.md / 模块 pom.xml / docs/modules/\<m>/09-依赖契约摘要.md 为准；若本步骤填写与实际模块声明不一致，按实际模块声明为权威更新依赖图。

- [ ] **步骤 4：修改"包结构规范"状态标记（第 94-107 行）**

old_string：

```
├─ portal-content-center         门户与内容中心 ⏳ 骨架
├─ customer-marketing-center     客户营销中心 ⏳ 骨架
├─ business-application-center   业务申请中心 ⏳ 骨架
├─ performance-engine-center     绩效计算中心 ⏳ 骨架
└─ report-analytics-center       报表分析中心 ⏳ 骨架
```

new_string：

```
├─ portal-content-center         门户与内容中心 ✅ 已完成
├─ customer-marketing-center     客户营销中心 ✅ 已完成
├─ business-application-center   业务申请中心 ✅ 已完成
├─ performance-engine-center     绩效计算中心 ⏳ 骨架
└─ report-analytics-center       报表分析中心 ⏳ 骨架
```

- [ ] **步骤 5：扩展"模块级 CLAUDE.md"链接清单（第 207-211 行）**

old_string：

```
### 模块级 CLAUDE.md (开发时必须参考)
- **公共基础设施**: [common/CLAUDE.md](common/CLAUDE.md)
- **认证授权**: [auth-permission-center/CLAUDE.md](auth-permission-center/CLAUDE.md)
- **系统治理**: [system-governance-center/CLAUDE.md](system-governance-center/CLAUDE.md)
- **工作流**: [workflow-center/CLAUDE.md](workflow-center/CLAUDE.md)
```

new_string：

```
### 模块级 CLAUDE.md (开发时必须参考)
- **公共基础设施**: [common/CLAUDE.md](common/CLAUDE.md)
- **认证授权**: [auth-permission-center/CLAUDE.md](auth-permission-center/CLAUDE.md)
- **系统治理**: [system-governance-center/CLAUDE.md](system-governance-center/CLAUDE.md)
- **工作流**: [workflow-center/CLAUDE.md](workflow-center/CLAUDE.md)
- **门户与内容**: [portal-content-center/CLAUDE.md](portal-content-center/CLAUDE.md)
- **客户营销**: [customer-marketing-center/CLAUDE.md](customer-marketing-center/CLAUDE.md)
- **业务申请**: [business-application-center/CLAUDE.md](business-application-center/CLAUDE.md)
- **启动入口**: [bootstrap/CLAUDE.md](bootstrap/CLAUDE.md)
```

- [ ] **步骤 6：复核全文无遗漏**

```bash
cd C:/Users/52140/Desktop/yiti && grep -E "⏳ 骨架|portal-content-center|customer-marketing-center|business-application-center" CLAUDE.md
```

检查：
- portal / customer / bizapp 不应再带 ⏳ 骨架 标记
- 仍保留 performance / report 的 ⏳ 骨架

- [ ] **步骤 7：提交 commit #5**

```bash
cd C:/Users/52140/Desktop/yiti && git add CLAUDE.md && git commit -m "docs: 同步根 CLAUDE.md 模块状态与依赖图（portal/customer/bizapp → ✅）

基于 D-中审计结论，将 3 个已实现模块从 ⏳ 骨架 改为 ✅ 已完成，
同步更新模块状态表、依赖图、包结构规范与模块级 CLAUDE.md 链接清单。

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### 任务 4.2：新建 bootstrap/CLAUDE.md

**文件**：
- 创建：`bootstrap/CLAUDE.md`

- [ ] **步骤 1：探查 bootstrap 模块实际结构作为依据**

```bash
cd C:/Users/52140/Desktop/yiti/bootstrap && ls src/main/java/com/bank/branch/platform/ && ls src/main/resources/ && ls src/test/
```

记录：
- 主类名（应为 `BranchPlatformApplication.java`）
- config 包下的配置类列表
- resources 下的配置文件
- 测试目录结构

```bash
cd C:/Users/52140/Desktop/yiti/bootstrap && cat pom.xml | head -80
```

记录 pom 里的 dependencies 清单（尤其 commons-compress 等特殊版本覆盖）。

- [ ] **步骤 2：写 bootstrap/CLAUDE.md**

使用 Write 工具创建 `bootstrap/CLAUDE.md`，内容结构（仿照其他模块 CLAUDE.md 风格）：

```markdown
# bootstrap/CLAUDE.md

本文件为 `bootstrap/` 模块提供上下文说明。bootstrap 是 **Spring Boot 启动入口模块**，不包含业务逻辑。

## 模块职责

- 聚合所有已实现的业务模块为可执行的 Spring Boot 应用
- 定义主类 `BranchPlatformApplication`，启用 `@SpringBootApplication` 自动装配
- 承载全局配置（`application.yml`）、全局 Bean（`config/` 包）
- 提供集成测试基类（`AbstractIntegrationTest`）

## 主类

- `com.bank.branch.platform.BranchPlatformApplication` — Spring Boot 启动类

## 依赖关系

本模块依赖所有 7 个已实现的业务 / 基础设施模块：

- common-web / common-trace / common-security / common-aop / common-db
- auth-permission-center
- system-governance-center
- workflow-center
- portal-content-center
- customer-marketing-center
- business-application-center

**注意**：bootstrap 是唯一的 Spring Boot 启动入口；所有业务模块都不应有自己的启动类。

## 包结构

​```
bootstrap/
├── src/main/java/com/bank/branch/platform/
│   ├── BranchPlatformApplication.java   # 主类
│   └── config/                          # 全局配置 Bean
├── src/main/resources/
│   └── application.yml                  # 全局配置文件
├── src/test/                            # 集成测试（AbstractIntegrationTest 基类）
├── pom.xml
└── CLAUDE.md（本文件）
​```

## 启动命令

​```bash
cd bootstrap
mvn spring-boot:run
​```

启动后访问：
- API 根: `http://localhost:8080/`
- Knife4j UI: `http://localhost:8080/doc.html`

## 配置文件

- `src/main/resources/application.yml` — 主配置
  - Session 超时：7200 秒
  - Flowable history level：`audit`
  - MyBatis mapper 位置：`classpath*:mapper/**/*Mapper.xml`

## 运行期依赖

- MySQL 8.0 （localhost:3306/onepl，开发默认）
- Redis 6.X （localhost:6379，Session 存储）
- MinIO （对象存储，可选）

## 特殊说明

### commons-compress 版本覆盖

`pom.xml` 中显式声明 commons-compress 1.25.0 覆盖传递依赖版本。原因见 pom 注释（若有）或历史 commit message。

### 集成测试基类

`src/test/java/.../AbstractIntegrationTest.java` 提供 `@SpringBootTest` 预配置，用于各模块的 IT（`*IT` / `*IntegrationTest`）测试复用。

## 修改须知

- 新增业务模块时，**必须**在 bootstrap 的 pom.xml 中添加依赖
- 修改 `application.yml` 的 Session / Datasource 配置会影响全局
- 不要在本模块添加任何业务代码（应该归到对应的业务模块）
```

**注意**：上述内容中如下字段需根据步骤 1 的实际探查结果修正：
- config 包下的具体类列表（如果不止一个 AbstractIntegrationTest）
- `application.yml` 外是否有 `application-{dev,test,prod}.yml`
- commons-compress 注释的实际原因

- [ ] **步骤 3：提交 commit #6**

```bash
cd C:/Users/52140/Desktop/yiti && git add bootstrap/CLAUDE.md && git commit -m "docs: 补全 bootstrap 模块级 CLAUDE.md

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## 阶段 5：停止与交接

### 任务 5.1：主会话向用户交接

**注意**：本阶段不产生 commit，不写文件。仅在会话内给出总结消息。

- [ ] **步骤 1：确认 6 次提交均已成功**

```bash
cd C:/Users/52140/Desktop/yiti && git log --oneline -8
```

期望看到（最新在上）：
```
<hash6> docs: 补全 bootstrap 模块级 CLAUDE.md
<hash5> docs: 同步根 CLAUDE.md 模块状态与依赖图（portal/customer/bizapp → ✅）
<hash4> docs(audit): 汇总 D-中模块审计总报告
<hash3> docs(audit): 生成 D-中模块审计 checklist 与 3 份模块报告
<hash2> chore: 清理 JVM 崩溃日志垃圾 (hs_err_* / replay_*)（如有）
<hash1> chore(pom): 补齐父 pom dependencyManagement 缺失的两个模块版本声明
<hash0> docs(spec): D-中模块审计设计规格（已存在）
```

若 commit #2 因文件未被跟踪而跳过，则此处为 5 个新 commit。

- [ ] **步骤 2：产出给用户的交接消息**

消息模板：

```
D-中审计已完成，产出位于 docs/superpowers/audits/ 目录：

- 审计 checklist（25 条）：2026-04-15-module-audit-checklist.md
- 3 份模块详细报告：portal / customer / bizapp
- 总报告：2026-04-15-d-med-audit-report.md

**基础设施状态**：父 pom 已修复，`mvn -pl portal-content-center,customer-marketing-center,business-application-center -am test` [正常 / 编译失败 / 测试失败 —— 以阶段 0.3 实际结果填写]。

**缺陷数量**（替换为实际数值）：
- portal: Critical <n> / Important <n> / Minor <n>
- customer: Critical <n> / Important <n> / Minor <n>
- bizapp: Critical <n> / Important <n> / Minor <n>

**根 CLAUDE.md 已同步**：portal / customer / bizapp 从 ⏳ 骨架 改为 ✅ 已完成；bootstrap/CLAUDE.md 已新建。

请审阅报告，决定后续路径：
- 选项甲：启动 TDD 整改（走 writing-plans → 子代理 TDD 修复每个 Critical / Important 项）
- 选项乙：仅保留报告，暂不修复，下次开发新功能时附带整改
- 选项丙：发现报告内容严重偏离，需要升级为 D-深审计
```

**本计划到此结束**。不在本次会话执行任何业务代码修复。

---

## 附录 A：回退预案

每个 commit 独立可 revert。对照 spec §10：

| 回退场景 | 操作 |
|---|---|
| 审计过程意识到 D-中不够 | `git revert <hash3> <hash4> <hash5> <hash6>`，保留 pom 修复 + 垃圾清理 |
| pom 修复引入新问题 | `git revert <hash1>` |
| 文档同步误判（如某模块其实不完整） | `git revert <hash5>` |
| 全部放弃本次审计 | 按倒序 `git revert <hash6>` → `<hash1>` |

---

## 附录 B：异常路径总览

| 异常 | 触发点 | 处理 | 是否进入下一阶段 |
|---|---|---|---|
| mvn 编译失败（pom 修复后仍红） | 任务 0.3 步骤 2 | 按任务 0.3 步骤 3 分叉：若仍为依赖问题回任务 0.1，否则立即停止上报 | 否 |
| mvn 测试失败（编译通过但 @Test 红） | 任务 0.3 步骤 2 | 任务 0.3 步骤 4：立即停止上报，转 D-深讨论 | 否 |
| 子代理报告结构校验未通过 | 任务 2.2 | 任务 2.2 步骤 3：回写子代理 1 次；仍不合规则在总报告标注缺失 | 是 |
| 子代理报告中出现 25 条 ID 外的自创 ID | 任务 2.2 | 回写要求改正；二次仍不合规在总报告标注 | 是 |
| 根 CLAUDE.md 预期行号与实际偏移 | 任务 4.1 | Edit 的 old_string 以唯一内容锚定（非行号），偏移不影响定位 | 是 |

---

## 附录 C：与 writing-plans 规范的一致性

- ✅ 每个任务均给出 Files 清单（创建 / 修改 / 测试，具体路径）
- ✅ 每个步骤 2-5 分钟粒度
- ✅ 精确命令与期望输出
- ✅ 分步提交（6 个独立 commit，每个 revert 安全）
- ✅ 复选框 `- [ ]` 语法全覆盖
- ✅ 不涉及代码修改（纯审计 + 文档同步，故本计划无 TDD 红-绿-重构步骤，这符合 spec 范围边界）
