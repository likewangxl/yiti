# 奖励分配（REWARD）导入与分配 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现奖励分配（REWARD）全链路——管理员导入「被分配人 + 分配人 + 原始值/兑现值/分配合计」，分配人在待处理任务中按部门把「分配合计」这笔总额分给部门下每一个人（每人>0、求和=合计），一次性提交。

**Architecture:** 新建 `EVAL_REWARD_ITEM` 表存奖励明细，批次复用 `EVAL_ASSIGN_BATCH`（task_type=REWARD）。后端镜像现有 EVAL 三层（Import/User/Admin Service + 三个 Controller），全部落 `com.bank.branch.platform.performance.eval` 包。前端汇总页并行拉 EVAL+REWARD 两接口客户端合并，REWARD 明细走新 `RewardTask.vue`。

**Tech Stack:** Spring Boot 3.2.3 + MyBatis-Plus 3.5.7 + EasyExcel + POI(SXSSF) + JUnit5/Mockito/AssertJ；前端 Vue3 + Element Plus。

**参考规格:** `docs/superpowers/specs/2026-07-11-eval-reward-assign-design.md`

**镜像对象（照抄结构，改字段语义）:**
- `performance-engine-center/.../eval/service/EvalAssignImportService.java`
- `performance-engine-center/.../eval/service/EvalAssignService.java`
- `performance-engine-center/.../eval/service/EvalAssignAdminService.java`
- `performance-engine-center/.../eval/mapper/EvalAssignItemMapper.java` + `resources/mapper/performance/EvalAssignItemMapper.xml`
- 对应三个 Controller

**测试执行约定（见根 CLAUDE.md）:** 跨模块改动后先 `mvn clean install -DskipTests` 再跑 IT；单测 `mvn test -pl performance-engine-center`。

---

## 阶段划分

| 阶段 | 内容 |
|---|---|
| Phase 0 | DDL 迁移脚本 + 基线同步 |
| Phase 1 | 错误码 + Entity + Mapper + XML |
| Phase 2 | 导入服务 `EvalRewardImportService`（TDD） |
| Phase 3 | 用户端服务 `EvalRewardService`（TDD） |
| Phase 4 | 管理端服务 `EvalRewardAdminService`（TDD） |
| Phase 5 | DTO + 三个 Controller |
| Phase 6 | 前端（api/eval.js + MyTasks 合并 + RewardTask.vue + 管理端导入） |
| Phase 7 | PT_RESOURCE 资源 + 联调验证 |

---

## Phase 0：DDL 迁移

### Task 0.1：建表脚本

**Files:**
- Create: `docs/superpowers/sql/2026-07-11-eval-reward-item.sql`

- [ ] **Step 1: 写建表脚本（幂等）**

```sql
-- 奖励分配明细表 EVAL_REWARD_ITEM（2026-07-11）
-- 批次复用 EVAL_ASSIGN_BATCH（task_type=REWARD）；本表仅存奖励分配明细。
-- 目标库：yiti + onepl_test_bootstrap 手工执行。EVAL_IMP_REWARD 字典项已存在，无需新增。
CREATE TABLE IF NOT EXISTS EVAL_REWARD_ITEM (
    item_id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    batch_id              BIGINT       NOT NULL COMMENT '所属批次(EVAL_ASSIGN_BATCH)',
    assign_user_id        VARCHAR(64)  NOT NULL COMMENT '分配人USER_ID(归一化后)',
    be_assigned_user_id   VARCHAR(64)  NOT NULL COMMENT '被分配人工号(原样快照,不校验)',
    be_assigned_user_name VARCHAR(128)          COMMENT '被分配人姓名(快照)',
    dept_name             VARCHAR(128) NOT NULL DEFAULT '' COMMENT '部门名称(分组键)',
    original_value        DECIMAL(18,4)         COMMENT '原始值(展示)',
    cash_value            DECIMAL(18,4)         COMMENT '兑现值(展示)',
    assign_total          DECIMAL(18,4) NOT NULL COMMENT '分配合计(组内一致,分配目标池)',
    assign_value          DECIMAL(18,4)         COMMENT '分配值(提交时填入)',
    submitted             TINYINT      NOT NULL DEFAULT 0 COMMENT '0未提交/1已提交',
    submit_time           DATETIME              COMMENT '提交时间',
    create_time           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (item_id),
    KEY idx_reward_assigner (assign_user_id, batch_id, dept_name, submitted),
    KEY idx_reward_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖励分配明细';
```

- [ ] **Step 2: 在本地两库执行**

Run:
```bash
mysql -uroot -pdjdev yiti < docs/superpowers/sql/2026-07-11-eval-reward-item.sql
mysql -uroot -pdjdev onepl_test_bootstrap < docs/superpowers/sql/2026-07-11-eval-reward-item.sql
```
Expected: 无报错；`SHOW TABLES LIKE 'EVAL_REWARD_ITEM';` 返回 1 行。

> 注（记忆）：本机所有库用 root/djdev；eval 表只在 yiti + onepl_test_bootstrap。

- [ ] **Step 3: 同步基线 DDL**

在 `docs/schema/ddl-eval.sql` 末尾追加与 Step 1 相同的 `CREATE TABLE EVAL_REWARD_ITEM` 语句（去掉 IF NOT EXISTS 也可，与该文件风格一致）。

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/sql/2026-07-11-eval-reward-item.sql docs/schema/ddl-eval.sql
git commit -m "feat(perf-eval): EVAL_REWARD_ITEM 建表脚本 + 基线同步"
```

---

## Phase 1：错误码 + Entity + Mapper + XML

### Task 1.1：新增两个错误码

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java`（在 `EVAL_TASK_DELETE_BEFORE_DEADLINE("PERF-40068", ...)` 之后追加）

- [ ] **Step 1: 追加错误码枚举项**

在 `EVAL_TASK_DELETE_BEFORE_DEADLINE("PERF-40068", "截止时间未到不可删除"),` 这一行之后插入：

```java
    EVAL_REWARD_ASSIGN_NOT_POSITIVE("PERF-40069", "分配值必须大于0"),
    EVAL_REWARD_SUM_MISMATCH("PERF-40070", "分配值之和必须等于分配合计"),
```

- [ ] **Step 2: 编译确认**

Run: `mvn -q -pl performance-engine-center compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java
git commit -m "feat(perf-eval): 新增奖励分配错误码 PERF-40069/40070"
```

### Task 1.2：Entity `EvalRewardItem`

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/entity/EvalRewardItem.java`

- [ ] **Step 1: 写实体**

```java
package com.bank.branch.platform.performance.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 奖励分配明细 EVAL_REWARD_ITEM 贫血实体。
 * <p>每行为一个被分配人，含分配人归属、导入快照(原始值/兑现值/分配合计)与提交后的分配值。</p>
 */
@Data
@TableName("EVAL_REWARD_ITEM")
public class EvalRewardItem {
    /** 主键. */
    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;
    /** 所属批次. */
    private Long batchId;
    /** 分配人工号(归一化后的 USER_ID). */
    private String assignUserId;
    /** 被分配人工号(原样快照,不校验). */
    private String beAssignedUserId;
    /** 被分配人姓名(快照). */
    private String beAssignedUserName;
    /** 部门名称(分组键,空存空串). */
    private String deptName;
    /** 原始值(展示). */
    private BigDecimal originalValue;
    /** 兑现值(展示). */
    private BigDecimal cashValue;
    /** 分配合计(组内一致,分配目标池). */
    private BigDecimal assignTotal;
    /** 分配值(提交时填入). */
    private BigDecimal assignValue;
    /** 是否已提交:0未提交/1已提交. */
    private Integer submitted;
    /** 提交时间. */
    private LocalDateTime submitTime;

    /**
     * 分配人登录名(PT_USER.username,即工号),非表字段。
     * <p>仅管理端批次详情展示用:由 Service 经 UserApi 按 assign_user_id 反查填充。</p>
     */
    @TableField(exist = false)
    private String assignUserUsername;
}
```

- [ ] **Step 2: 编译确认**

Run: `mvn -q -pl performance-engine-center compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/entity/EvalRewardItem.java
git commit -m "feat(perf-eval): EvalRewardItem 实体"
```

### Task 1.3：Mapper 接口 + XML

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/mapper/EvalRewardItemMapper.java`
- Create: `performance-engine-center/src/main/resources/mapper/performance/EvalRewardItemMapper.xml`
- 依赖 DTO（Task 3.1 会正式建，这里先建汇总 DTO 以便 Mapper 编译）
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalRewardPendingGroupDTO.java`

- [ ] **Step 1: 写汇总 DTO（Mapper resultType 依赖）**

```java
package com.bank.branch.platform.performance.eval.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的奖励分配待处理汇总行（当前登录人作为分配人，按部门聚合未提交明细）。
 */
@Data
public class EvalRewardPendingGroupDTO {
    /** 所属批次. */
    private Long batchId;
    /** 任务类型编码(固定 REWARD). */
    private String taskType;
    /** 任务名称(批次名称). */
    private String taskName;
    /** 任务类型展示名(字典翻译,如"奖励分配"). */
    private String taskTypeLabel;
    /** 部门名称. */
    private String dept;
    /** 该部门下待分配人数(submitted=0). */
    private Integer pendingCount;
    /** 分配合计(组内一致). */
    private BigDecimal assignTotal;
    /** 分配截止时间. */
    private LocalDateTime deadline;
}
```

- [ ] **Step 2: 写 Mapper 接口**

```java
package com.bank.branch.platform.performance.eval.mapper;

import com.bank.branch.platform.performance.eval.dto.EvalRewardPendingGroupDTO;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalRewardItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 奖励分配明细 Mapper。
 * <p>单条 CRUD 由 BaseMapper 提供；以下为自定义方法（批量插入、聚合、条件更新）。</p>
 */
@Mapper
public interface EvalRewardItemMapper extends BaseMapper<EvalRewardItem> {

    /** 批量插入明细。 */
    int batchInsert(@Param("items") List<EvalRewardItem> items);

    /**
     * 查询某分配人未提交明细，按 batch_id + 部门聚合（仅 ACTIVE status=0 且未过期批次）。
     * @return 汇总行(batchId/taskType/taskName/dept/pendingCount/assignTotal/deadline)
     */
    List<EvalRewardPendingGroupDTO> selectRewardPendingGroups(@Param("assignUserId") String assignUserId);

    /** 查询某分配人在指定批次+部门下的全部明细（含已提交，处理列表展示）。 */
    List<EvalRewardItem> selectByAssignerBatchDept(@Param("assignUserId") String assignUserId,
                                                   @Param("batchId") Long batchId,
                                                   @Param("dept") String dept);

    /** 标记某明细已分配并写入分配值与提交时间（带 submitted=0 乐观条件）。 */
    int markAssigned(@Param("itemId") Long itemId,
                     @Param("assignValue") BigDecimal assignValue,
                     @Param("submitTime") LocalDateTime submitTime);

    /** 管理端-分页查询批次下明细。 */
    List<EvalRewardItem> selectByBatchId(@Param("batchId") Long batchId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /** 管理端-批次下明细总数。 */
    long countByBatchId(@Param("batchId") Long batchId);

    /** 批次下去重的全部分配人 USER_ID（导出反查工号用）。 */
    List<String> selectDistinctAssignerIdsByBatch(@Param("batchId") Long batchId);

    /** 管理端-按条件分页查询 REWARD 批次列表（含明细数 itemCount）。 */
    List<EvalAssignBatch> selectRewardBatchesByCondition(@Param("status") Integer status,
                                                         @Param("keyword") String keyword,
                                                         @Param("offset") int offset,
                                                         @Param("limit") int limit);

    /** 管理端-按条件统计 REWARD 批次数。 */
    long countRewardBatchesByCondition(@Param("status") Integer status,
                                       @Param("keyword") String keyword);

    /** 按批次ID删除全部明细。 */
    int deleteByBatchId(@Param("batchId") Long batchId);
}
```

- [ ] **Step 3: 写 Mapper XML**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.bank.branch.platform.performance.eval.mapper.EvalRewardItemMapper">

    <sql id="BASE_COLUMNS">
        item_id, batch_id, assign_user_id, be_assigned_user_id, be_assigned_user_name,
        dept_name, original_value, cash_value, assign_total, assign_value, submitted, submit_time
    </sql>

    <insert id="batchInsert">
        INSERT INTO EVAL_REWARD_ITEM
        (batch_id, assign_user_id, be_assigned_user_id, be_assigned_user_name,
         dept_name, original_value, cash_value, assign_total, assign_value, submitted, submit_time)
        VALUES
        <foreach collection="items" item="i" separator=",">
            (#{i.batchId}, #{i.assignUserId}, #{i.beAssignedUserId}, #{i.beAssignedUserName},
             #{i.deptName}, #{i.originalValue}, #{i.cashValue}, #{i.assignTotal},
             #{i.assignValue}, #{i.submitted}, #{i.submitTime})
        </foreach>
    </insert>

    <!-- 当前分配人未提交明细，按 批次 + 部门 聚合（含 assign_total，组内一致取 MAX 即该值） -->
    <select id="selectRewardPendingGroups" resultType="com.bank.branch.platform.performance.eval.dto.EvalRewardPendingGroupDTO">
        SELECT i.batch_id     AS batchId,
               b.task_type     AS taskType,
               b.batch_name    AS taskName,
               i.dept_name     AS dept,
               COUNT(*)        AS pendingCount,
               MAX(i.assign_total) AS assignTotal,
               b.deadline      AS deadline
        FROM EVAL_REWARD_ITEM i
        JOIN EVAL_ASSIGN_BATCH b ON i.batch_id = b.batch_id
        WHERE i.assign_user_id = #{assignUserId} AND i.submitted = 0 AND b.status = 0
          AND b.deadline IS NOT NULL AND b.deadline > NOW()
        GROUP BY i.batch_id, b.task_type, b.batch_name, i.dept_name, b.deadline
        ORDER BY b.deadline ASC, i.batch_id ASC
    </select>

    <select id="selectByAssignerBatchDept" resultType="com.bank.branch.platform.performance.eval.entity.EvalRewardItem">
        SELECT <include refid="BASE_COLUMNS"/> FROM EVAL_REWARD_ITEM
        WHERE assign_user_id = #{assignUserId} AND batch_id = #{batchId} AND dept_name = #{dept}
        ORDER BY submitted ASC, item_id ASC
    </select>

    <update id="markAssigned">
        UPDATE EVAL_REWARD_ITEM
        SET assign_value = #{assignValue}, submitted = 1, submit_time = #{submitTime}
        WHERE item_id = #{itemId} AND submitted = 0
    </update>

    <select id="selectByBatchId" resultType="com.bank.branch.platform.performance.eval.entity.EvalRewardItem">
        SELECT <include refid="BASE_COLUMNS"/> FROM EVAL_REWARD_ITEM
        WHERE batch_id = #{batchId}
        ORDER BY submitted ASC, item_id ASC
        LIMIT #{offset}, #{limit}
    </select>

    <select id="countByBatchId" resultType="long">
        SELECT COUNT(*) FROM EVAL_REWARD_ITEM WHERE batch_id = #{batchId}
    </select>

    <select id="selectDistinctAssignerIdsByBatch" resultType="java.lang.String">
        SELECT DISTINCT assign_user_id FROM EVAL_REWARD_ITEM
        WHERE batch_id = #{batchId} AND assign_user_id IS NOT NULL AND assign_user_id != ''
    </select>

    <select id="selectRewardBatchesByCondition" resultType="com.bank.branch.platform.performance.eval.entity.EvalAssignBatch">
        SELECT b.*, COUNT(i.item_id) AS itemCount
        FROM EVAL_ASSIGN_BATCH b
        LEFT JOIN EVAL_REWARD_ITEM i ON b.batch_id = i.batch_id
        <where>
            b.task_type = 'REWARD'
            <if test="status != null">AND b.status = #{status}</if>
            <if test="keyword != null and keyword != ''">
                AND (b.batch_id LIKE CONCAT('%', #{keyword}, '%') OR b.create_by LIKE CONCAT('%', #{keyword}, '%'))
            </if>
        </where>
        GROUP BY b.batch_id
        ORDER BY b.create_time DESC
        LIMIT #{offset}, #{limit}
    </select>

    <select id="countRewardBatchesByCondition" resultType="long">
        SELECT COUNT(*) FROM EVAL_ASSIGN_BATCH b
        <where>
            b.task_type = 'REWARD'
            <if test="status != null">AND b.status = #{status}</if>
            <if test="keyword != null and keyword != ''">
                AND (b.batch_id LIKE CONCAT('%', #{keyword}, '%') OR b.create_by LIKE CONCAT('%', #{keyword}, '%'))
            </if>
        </where>
    </select>

    <delete id="deleteByBatchId">
        DELETE FROM EVAL_REWARD_ITEM WHERE batch_id = #{batchId}
    </delete>
</mapper>
```

- [ ] **Step 4: 编译确认**

Run: `mvn -q -pl performance-engine-center compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/mapper/EvalRewardItemMapper.java \
        performance-engine-center/src/main/resources/mapper/performance/EvalRewardItemMapper.xml \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalRewardPendingGroupDTO.java
git commit -m "feat(perf-eval): EvalRewardItemMapper + XML + 汇总 DTO"
```

---

## Phase 2：导入服务 `EvalRewardImportService`（TDD）

### Task 2.1：导入 Excel 行模型 DTO

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalRewardImportRow.java`

- [ ] **Step 1: 写 8 列行模型**

```java
package com.bank.branch.platform.performance.eval.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 奖励分配导入 Excel 行模型（8 列，按业务样例顺序）。
 * <p>分配值列导入时忽略（提交时由分配人填入）。数值列用 BigDecimal，空/非法在校验期落行级错误。</p>
 */
@Data
public class EvalRewardImportRow {
    @ExcelProperty("被分配人工号")
    private String beAssignedUserId;
    @ExcelProperty("被分配人用户姓名")
    private String beAssignedUserName;
    @ExcelProperty("部门名称")
    private String deptName;
    @ExcelProperty("原始值")
    private BigDecimal originalValue;
    @ExcelProperty("分配值")
    private String assignValueIgnored;
    @ExcelProperty("兑现值")
    private BigDecimal cashValue;
    @ExcelProperty("分配人工号")
    private String assignUserId;
    @ExcelProperty("分配合计")
    private BigDecimal assignTotal;
}
```

- [ ] **Step 2: 编译确认**

Run: `mvn -q -pl performance-engine-center compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalRewardImportRow.java
git commit -m "feat(perf-eval): EvalRewardImportRow 8 列导入行模型"
```

### Task 2.2：受理 DTO（复用现有 `EvalAssignImportAcceptedDTO`）

无需新建——`EvalAssignImportAcceptedDTO(batchId, status)` 已通用，Controller 直接复用。本任务仅确认，不产出代码。

### Task 2.3：导入服务（先写失败测试）

**Files:**
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalRewardImportServiceTest.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalRewardImportService.java`

- [ ] **Step 1: 写失败测试（镜像 EvalAssignImportServiceTest 结构）**

```java
package com.bank.branch.platform.performance.eval.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.performance.eval.dto.EvalRewardImportRow;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalRewardItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRewardItemMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalRewardImportServiceTest {

    @Mock UserApi userApi;
    @Mock EvalAssignBatchMapper batchMapper;
    @Mock EvalRewardItemMapper itemMapper;
    EvalRewardImportService service;

    private final LocalDateTime deadline = LocalDateTime.now().plusDays(7);

    private EvalRewardImportRow row(String beId, String beName, String dept,
                                    String original, String cash, String assignUser, String total) {
        EvalRewardImportRow r = new EvalRewardImportRow();
        r.setBeAssignedUserId(beId);
        r.setBeAssignedUserName(beName);
        r.setDeptName(dept);
        r.setOriginalValue(original == null ? null : new BigDecimal(original));
        r.setCashValue(cash == null ? null : new BigDecimal(cash));
        r.setAssignUserId(assignUser);
        r.setAssignTotal(total == null ? null : new BigDecimal(total));
        return r;
    }

    private EvalAssignBatch importingBatch(long id) {
        EvalAssignBatch b = new EvalAssignBatch();
        b.setBatchId(id);
        b.setStatus(3);
        b.setSource("IMPORT");
        b.setCreateTime(LocalDateTime.now());
        return b;
    }

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        service = new EvalRewardImportService(userApi, batchMapper, itemMapper, new ObjectMapper(), tm);
    }

    private void mockAssignersExist(String... usernames) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        for (String n : usernames) map.put(n, "ID_" + n);
        when(userApi.mapUsernamesToEmpId(anyList())).thenReturn(map);
    }

    private EvalAssignBatch captureUpdatedBatch() {
        ArgumentCaptor<EvalAssignBatch> cap = ArgumentCaptor.forClass(EvalAssignBatch.class);
        verify(batchMapper).updateById(cap.capture());
        return cap.getValue();
    }

    @Test
    @DisplayName("parseRows：空文件 → EVAL_IMPORT_FILE_EMPTY")
    void parseRows_empty_throws() {
        MultipartFile empty = new MockMultipartFile("file", "t.xlsx", null, new byte[0]);
        assertThatThrownBy(() -> service.parseRows(empty))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("导入文件为空");
    }

    @Test
    @DisplayName("createImportingBatch：建 STATUS=3 REWARD 批次")
    void createImportingBatch_status3Reward() {
        when(batchMapper.insert(any(EvalAssignBatch.class))).thenAnswer(inv -> {
            ((EvalAssignBatch) inv.getArgument(0)).setBatchId(77L);
            return 1;
        });
        Long id = service.createImportingBatch("奖励任务", deadline, "ADMIN");
        assertThat(id).isEqualTo(77L);
        ArgumentCaptor<EvalAssignBatch> cap = ArgumentCaptor.forClass(EvalAssignBatch.class);
        verify(batchMapper).insert(cap.capture());
        assertThat(cap.getValue().getTaskType()).isEqualTo("REWARD");
        assertThat(cap.getValue().getStatus()).isEqualTo(3);
    }

    @Test
    @DisplayName("全部合法 → 批量插明细 + 批次 STATUS=2；分配人归一 USER_ID，被分配人原样")
    void processImport_allValid_persistsDraft() {
        mockAssignersExist("A1");
        when(batchMapper.selectById(77L)).thenReturn(importingBatch(77L));
        List<EvalRewardImportRow> rows = List.of(
                row("B1", "被一", "信贷部", "76.5", "80", "A1", "100"),
                row("B2", "被二", "信贷部", "60", "65", "A1", "100"));

        service.processImport(77L, rows, "奖励任务", deadline, "ADMIN");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EvalRewardItem>> cap = ArgumentCaptor.forClass(List.class);
        verify(itemMapper).batchInsert(cap.capture());
        List<EvalRewardItem> items = cap.getValue();
        assertThat(items).hasSize(2);
        assertThat(items).allMatch(i -> i.getAssignUserId().equals("ID_A1"));
        assertThat(items.stream().map(EvalRewardItem::getBeAssignedUserId).collect(Collectors.toList()))
                .containsExactly("B1", "B2");
        assertThat(items.get(0).getAssignTotal()).isEqualByComparingTo("100");
        assertThat(items.get(0).getSubmitted()).isEqualTo(0);
        assertThat(items.get(0).getAssignValue()).isNull();
        EvalAssignBatch b = captureUpdatedBatch();
        assertThat(b.getStatus()).isEqualTo(2);
        assertThat(b.getImportedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("行错误：分配人工号不存在 → STATUS=4 + errorSummary 含『分配人工号不存在』")
    void processImport_assignerNotExist_fails() {
        mockAssignersExist("A1"); // A2 不存在
        when(batchMapper.selectById(77L)).thenReturn(importingBatch(77L));
        service.processImport(77L, List.of(row("B1", "被一", "信贷部", "10", "10", "A2", "100")),
                "奖励任务", deadline, "ADMIN");
        verify(itemMapper, never()).batchInsert(anyList());
        assertThat(captureUpdatedBatch().getErrorSummary()).contains("分配人工号不存在");
    }

    @Test
    @DisplayName("行错误：分配合计<=0 → errorSummary 含『分配合计』")
    void processImport_totalNotPositive_fails() {
        mockAssignersExist("A1");
        when(batchMapper.selectById(77L)).thenReturn(importingBatch(77L));
        service.processImport(77L, List.of(row("B1", "被一", "信贷部", "10", "10", "A1", "0")),
                "奖励任务", deadline, "ADMIN");
        assertThat(captureUpdatedBatch().getErrorSummary()).contains("分配合计");
    }

    @Test
    @DisplayName("跨行：同(分配人+部门)组 分配合计不一致 → errorSummary 含『分配合计不一致』")
    void processImport_totalInconsistentWithinGroup_fails() {
        mockAssignersExist("A1");
        when(batchMapper.selectById(77L)).thenReturn(importingBatch(77L));
        service.processImport(77L, List.of(
                row("B1", "被一", "信贷部", "10", "10", "A1", "100"),
                row("B2", "被二", "信贷部", "10", "10", "A1", "200")), // 同组不同合计
                "奖励任务", deadline, "ADMIN");
        verify(itemMapper, never()).batchInsert(anyList());
        assertThat(captureUpdatedBatch().getErrorSummary()).contains("分配合计不一致");
    }

    @Test
    @DisplayName("行错误：被分配人工号为空 → errorSummary 含『被分配人工号』")
    void processImport_emptyBeAssigned_fails() {
        mockAssignersExist("A1");
        when(batchMapper.selectById(77L)).thenReturn(importingBatch(77L));
        service.processImport(77L, List.of(row("", "被一", "信贷部", "10", "10", "A1", "100")),
                "奖励任务", deadline, "ADMIN");
        assertThat(captureUpdatedBatch().getErrorSummary()).contains("被分配人工号");
    }

    @Test
    @DisplayName("行错误：分配合计为空(无法解析) → errorSummary 含『分配合计』")
    void processImport_totalNull_fails() {
        mockAssignersExist("A1");
        when(batchMapper.selectById(77L)).thenReturn(importingBatch(77L));
        service.processImport(77L, List.of(row("B1", "被一", "信贷部", "10", "10", "A1", null)),
                "奖励任务", deadline, "ADMIN");
        assertThat(captureUpdatedBatch().getErrorSummary()).contains("分配合计");
    }
}
```

- [ ] **Step 2: 运行测试确认失败（类不存在）**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalRewardImportServiceTest`
Expected: 编译失败 / FAIL —— `EvalRewardImportService` 不存在。

- [ ] **Step 3: 写实现（镜像 EvalAssignImportService，改字段+校验）**

```java
package com.bank.branch.platform.performance.eval.service;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportResultDTO;
import com.bank.branch.platform.performance.eval.dto.EvalRewardImportRow;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalRewardItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRewardItemMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 奖励分配（REWARD）Excel 导入服务（异步 + 前端轮询，镜像 EvalAssignImportService）。
 *
 * <p>接口线程仅 {@link #parseRows} 同步解析 + {@link #createImportingBatch} 建 IMPORTING(3)
 * REWARD 批次并立即返回 batchId；逐行校验入库挪到 {@link #processImport} 后台异步执行。</p>
 *
 * <p>校验：分配人工号填登录名(PT_USER.USER_NAME)且系统有效（归一 USER_ID）；被分配人工号仅非空
 * 快照不校验；原始值/兑现值/分配合计可解析 BigDecimal，分配合计>0；同(分配人+部门)组分配合计一致；
 * all-or-none（任一行错误则一条不写，失败明细 JSON 写批次 ERROR_SUMMARY）。</p>
 */
@Slf4j
@Service
public class EvalRewardImportService {

    private static final int STATUS_DRAFT = 2;
    private static final int STATUS_IMPORTING = 3;
    private static final int STATUS_FAILED = 4;
    private static final String TASK_TYPE_REWARD = "REWARD";

    @Value("${perf.eval.import.batch-insert-size:1000}")
    private int batchInsertSize = 1000;
    @Value("${perf.eval.import.error-keep:500}")
    private int errorKeep = 500;

    private final UserApi userApi;
    private final EvalAssignBatchMapper batchMapper;
    private final EvalRewardItemMapper itemMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    public EvalRewardImportService(UserApi userApi,
                                   EvalAssignBatchMapper batchMapper,
                                   EvalRewardItemMapper itemMapper,
                                   ObjectMapper objectMapper,
                                   PlatformTransactionManager transactionManager) {
        this.userApi = userApi;
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /** 同步解析上传的 Excel 为内存行集（接口线程调用）。 */
    public List<EvalRewardImportRow> parseRows(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_EMPTY);
        }
        try {
            return EasyExcel.read(file.getInputStream())
                    .head(EvalRewardImportRow.class)
                    .sheet()
                    .doReadSync();
        } catch (Exception e) {
            log.warn("[EvalRewardImportService.parseRows] 解析失败: {}", e.getMessage());
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_INVALID, e.getMessage());
        }
    }

    /** 建 IMPORTING(3) REWARD 批次并独立事务提交，使 batchId 立即对前端轮询可见。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long createImportingBatch(String taskName, LocalDateTime deadline, String createBy) {
        EvalAssignBatch batch = new EvalAssignBatch();
        batch.setTaskType(TASK_TYPE_REWARD);
        batch.setBatchName(taskName);
        batch.setSource("IMPORT");
        batch.setDeadline(deadline);
        batch.setStatus(STATUS_IMPORTING);
        batch.setCreateBy(createBy);
        batch.setCreateTime(LocalDateTime.now());
        batchMapper.insert(batch);
        log.info("[EvalRewardImportService.createImportingBatch] 受理导入 batchId={} createBy={}",
                batch.getBatchId(), createBy);
        return batch.getBatchId();
    }

    /** 异步逐行校验 + 入库（后台线程执行）。 */
    @Async("evalImportExecutor")
    public void processImport(Long batchId, List<EvalRewardImportRow> rows,
                              String taskName, LocalDateTime deadline, String createBy) {
        int total = rows == null ? 0 : rows.size();
        try {
            ValidationOutcome outcome = validate(rows, deadline);
            if (!outcome.errors.isEmpty()) {
                markFailed(batchId, buildErrorSummary(outcome.errors), total);
                log.info("[EvalRewardImportService.processImport] batchId={} 校验失败 {} 条错误（共 {} 行）",
                        batchId, outcome.errors.size(), total);
                return;
            }
            persistSuccess(batchId, outcome.parsed, total);
            log.info("[EvalRewardImportService.processImport] batchId={} 导入成功 {} 条", batchId, outcome.parsed.size());
        } catch (Exception e) {
            log.error("[EvalRewardImportService.processImport] batchId={} 导入异常", batchId, e);
            markFailed(batchId, buildExceptionSummary(e), total);
        }
    }

    void persistSuccess(Long batchId, List<EvalRewardItem> parsed, int total) {
        txTemplate.executeWithoutResult(status -> {
            for (EvalRewardItem item : parsed) {
                item.setBatchId(batchId);
            }
            for (int from = 0; from < parsed.size(); from += batchInsertSize) {
                int to = Math.min(from + batchInsertSize, parsed.size());
                itemMapper.batchInsert(parsed.subList(from, to));
            }
            EvalAssignBatch batch = batchMapper.selectById(batchId);
            batch.setStatus(STATUS_DRAFT);
            batch.setImportedCount(parsed.size());
            batch.setTotalRows(total);
            batchMapper.updateById(batch);
        });
    }

    void markFailed(Long batchId, String errorSummary, int total) {
        EvalAssignBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            log.warn("[EvalRewardImportService.markFailed] batchId={} 不存在", batchId);
            return;
        }
        batch.setStatus(STATUS_FAILED);
        batch.setErrorSummary(errorSummary);
        batch.setTotalRows(total);
        batchMapper.updateById(batch);
    }

    /** 逐行 + 跨行校验，不写库。 */
    private ValidationOutcome validate(List<EvalRewardImportRow> rows, LocalDateTime deadline) {
        if (rows == null || rows.isEmpty()) {
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_EMPTY);
        }
        if (deadline == null) {
            throw new PerfException(PerfErrorCode.EVAL_TASK_END_TIME_INVALID, (Object) null);
        }

        // 分配人工号有效性：填登录名，批量归一为 USER_ID
        Set<String> allAssigners = new HashSet<>();
        for (EvalRewardImportRow r : rows) {
            String a = trim(r.getAssignUserId());
            if (!a.isEmpty()) allAssigners.add(a);
        }
        Map<String, String> nameToUserId = allAssigners.isEmpty()
                ? new HashMap<>()
                : userApi.mapUsernamesToEmpId(new ArrayList<>(allAssigners));
        Set<String> existing = nameToUserId.keySet();

        List<EvalAssignImportResultDTO.RowError> errors = new ArrayList<>();
        List<EvalRewardItem> parsed = new ArrayList<>();
        // 跨行分组合计一致性：key = 归一后分配人 + '' + 部门 → 首个 assign_total
        Map<String, BigDecimal> groupTotal = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNo = i + 1;
            EvalRewardImportRow r = rows.get(i);
            String beId = trim(r.getBeAssignedUserId());
            String assignName = trim(r.getAssignUserId());
            String dept = trim(r.getDeptName());

            if (beId.isEmpty()) {
                errors.add(err(rowNo, "被分配人工号不能为空"));
                continue;
            }
            if (assignName.isEmpty()) {
                errors.add(err(rowNo, "分配人工号不能为空"));
                continue;
            }
            if (!existing.contains(assignName)) {
                errors.add(err(rowNo, "分配人工号不存在：" + assignName));
                continue;
            }
            BigDecimal total = r.getAssignTotal();
            if (total == null) {
                errors.add(err(rowNo, "分配合计不能为空或非法"));
                continue;
            }
            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(err(rowNo, "分配合计必须大于0"));
                continue;
            }
            String assignUserId = nameToUserId.getOrDefault(assignName, assignName);
            // 跨行：同(分配人+部门)组 分配合计必须一致
            String groupKey = assignUserId + "" + dept;
            BigDecimal seen = groupTotal.get(groupKey);
            if (seen == null) {
                groupTotal.put(groupKey, total);
            } else if (seen.compareTo(total) != 0) {
                errors.add(err(rowNo, "同部门分配合计不一致：" + dept));
                continue;
            }

            EvalRewardItem item = new EvalRewardItem();
            item.setAssignUserId(assignUserId);
            item.setBeAssignedUserId(beId);
            item.setBeAssignedUserName(trim(r.getBeAssignedUserName()));
            item.setDeptName(dept);
            item.setOriginalValue(r.getOriginalValue());
            item.setCashValue(r.getCashValue());
            item.setAssignTotal(total);
            item.setAssignValue(null);
            item.setSubmitted(0);
            parsed.add(item);
        }
        return new ValidationOutcome(errors, parsed);
    }

    private String buildErrorSummary(List<EvalAssignImportResultDTO.RowError> errors) {
        int total = errors.size();
        List<EvalAssignImportResultDTO.RowError> kept = total > errorKeep ? errors.subList(0, errorKeep) : errors;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", total);
        payload.put("truncated", total > errorKeep);
        payload.put("errors", kept);
        return toJson(payload, "{\"total\":" + total + ",\"errors\":[]}");
    }

    private String buildExceptionSummary(Exception e) {
        String msg = "导入异常：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", 1);
        payload.put("truncated", false);
        payload.put("errors", List.of(new EvalAssignImportResultDTO.RowError(0, msg)));
        return toJson(payload, "{\"total\":1,\"errors\":[]}");
    }

    private String toJson(Object payload, String fallback) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.warn("[EvalRewardImportService.toJson] 序列化失败: {}", ex.getMessage());
            return fallback;
        }
    }

    void setBatchInsertSize(int batchInsertSize) { this.batchInsertSize = batchInsertSize; }
    void setErrorKeep(int errorKeep) { this.errorKeep = errorKeep; }

    private static String trim(String s) { return s == null ? "" : s.trim(); }

    private static EvalAssignImportResultDTO.RowError err(int row, String msg) {
        return new EvalAssignImportResultDTO.RowError(row, msg);
    }

    private static final class ValidationOutcome {
        private final List<EvalAssignImportResultDTO.RowError> errors;
        private final List<EvalRewardItem> parsed;
        private ValidationOutcome(List<EvalAssignImportResultDTO.RowError> errors, List<EvalRewardItem> parsed) {
            this.errors = errors;
            this.parsed = parsed;
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalRewardImportServiceTest`
Expected: BUILD SUCCESS，全部绿。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalRewardImportService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalRewardImportServiceTest.java
git commit -m "feat(perf-eval): EvalRewardImportService 奖励分配异步导入 + 单测"
```

---

## Phase 3：用户端服务 `EvalRewardService`（TDD）

### Task 3.1：明细 DTO + 提交请求 DTO

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalRewardPendingItemDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/SubmitRewardBatchReq.java`

- [ ] **Step 1: 写明细 DTO**

```java
package com.bank.branch.platform.performance.eval.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 奖励分配明细行（处理某部门时展示的被分配人 + 分配值录入）。
 */
@Data
public class EvalRewardPendingItemDTO {
    /** 明细ID. */
    private Long itemId;
    /** 被分配人工号(快照). */
    private String beAssignedUserId;
    /** 被分配人姓名. */
    private String beAssignedUserName;
    /** 部门名称. */
    private String deptName;
    /** 原始值(展示). */
    private BigDecimal originalValue;
    /** 兑现值(展示). */
    private BigDecimal cashValue;
    /** 分配合计(该组一致). */
    private BigDecimal assignTotal;
    /** 分配值(已提交则回填,未提交为 null). */
    private BigDecimal assignValue;
    /** 是否已提交:0未提交/1已提交. */
    private Integer submitted;
}
```

- [ ] **Step 2: 写提交请求 DTO**

```java
package com.bank.branch.platform.performance.eval.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 奖励分配-批量提交请求体（一次性提交某部门下全部被分配人的分配值）。
 */
@Data
public class SubmitRewardBatchReq {
    /** 批次ID(必填). */
    @NotNull
    private Long batchId;
    /** 部门名称(必填,分组键). */
    @NotNull
    private String dept;
    /** 分配明细(必填,至少一条). */
    @NotEmpty
    @Valid
    private List<Entry> items;

    /** 单条分配:明细ID + 分配值. */
    @Data
    public static class Entry {
        @NotNull
        private Long itemId;
        @NotNull
        private BigDecimal assignValue;
    }
}
```

- [ ] **Step 3: 编译确认**

Run: `mvn -q -pl performance-engine-center compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/EvalRewardPendingItemDTO.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/dto/SubmitRewardBatchReq.java
git commit -m "feat(perf-eval): 奖励分配明细 DTO + 批量提交请求 DTO"
```

### Task 3.2：用户端服务（先写失败测试）

**Files:**
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalRewardServiceTest.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalRewardService.java`

- [ ] **Step 1: 写失败测试**

```java
package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.EvalRewardPendingGroupDTO;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalRewardItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRewardItemMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalRewardServiceTest {

    @Mock EvalRewardItemMapper itemMapper;
    @Mock EvalAssignBatchMapper batchMapper;
    @Mock DictApi dictApi;
    EvalRewardService service;

    @BeforeEach
    void setUp() {
        service = new EvalRewardService(itemMapper, batchMapper, dictApi);
    }

    private EvalAssignBatch activeBatch() {
        EvalAssignBatch b = new EvalAssignBatch();
        b.setBatchId(5L);
        b.setStatus(0); // ACTIVE
        b.setDeadline(LocalDateTime.now().plusDays(3));
        return b;
    }

    private EvalRewardItem item(long id, String dept, String total) {
        EvalRewardItem it = new EvalRewardItem();
        it.setItemId(id);
        it.setBatchId(5L);
        it.setAssignUserId("A1");
        it.setDeptName(dept);
        it.setAssignTotal(new BigDecimal(total));
        it.setSubmitted(0);
        return it;
    }

    @Test
    @DisplayName("汇总：taskTypeLabel 经字典翻译")
    void listGroups_translatesLabel() {
        EvalRewardPendingGroupDTO g = new EvalRewardPendingGroupDTO();
        g.setTaskType("REWARD");
        when(itemMapper.selectRewardPendingGroups("A1")).thenReturn(List.of(g));
        DictItemDTO d = new DictItemDTO();
        d.setDictCode("REWARD");
        d.setDictLabel("奖励分配");
        when(dictApi.getDictItems("EVAL_IMPORT_TYPE")).thenReturn(List.of(d));

        List<EvalRewardPendingGroupDTO> out = service.listMyRewardPendingGroups("A1");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getTaskTypeLabel()).isEqualTo("奖励分配");
    }

    @Test
    @DisplayName("提交成功：每人>0 且 求和=分配合计 → 全部 markAssigned")
    void submit_success() {
        when(batchMapper.selectById(5L)).thenReturn(activeBatch());
        // 该组两条未提交，合计 100
        when(itemMapper.selectByAssignerBatchDept("A1", 5L, "信贷部"))
                .thenReturn(List.of(item(11L, "信贷部", "100"), item(12L, "信贷部", "100")));
        when(itemMapper.markAssigned(any(), any(), any())).thenReturn(1);

        service.submitRewardBatch("A1", 5L, "信贷部", List.of(
                entry(11L, "60"), entry(12L, "40")));

        verify(itemMapper).markAssigned(eq(11L), eq(new BigDecimal("60")), any());
        verify(itemMapper).markAssigned(eq(12L), eq(new BigDecimal("40")), any());
    }

    @Test
    @DisplayName("提交失败：某人分配值<=0 → EVAL_REWARD_ASSIGN_NOT_POSITIVE，一条不写")
    void submit_notPositive() {
        when(batchMapper.selectById(5L)).thenReturn(activeBatch());
        when(itemMapper.selectByAssignerBatchDept("A1", 5L, "信贷部"))
                .thenReturn(List.of(item(11L, "信贷部", "100"), item(12L, "信贷部", "100")));
        assertThatThrownBy(() -> service.submitRewardBatch("A1", 5L, "信贷部", List.of(
                entry(11L, "100"), entry(12L, "0"))))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("分配值必须大于0");
        verify(itemMapper, never()).markAssigned(any(), any(), any());
    }

    @Test
    @DisplayName("提交失败：求和≠分配合计 → EVAL_REWARD_SUM_MISMATCH")
    void submit_sumMismatch() {
        when(batchMapper.selectById(5L)).thenReturn(activeBatch());
        when(itemMapper.selectByAssignerBatchDept("A1", 5L, "信贷部"))
                .thenReturn(List.of(item(11L, "信贷部", "100"), item(12L, "信贷部", "100")));
        assertThatThrownBy(() -> service.submitRewardBatch("A1", 5L, "信贷部", List.of(
                entry(11L, "60"), entry(12L, "50")))) // 和=110≠100
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("分配值之和必须等于分配合计");
        verify(itemMapper, never()).markAssigned(any(), any(), any());
    }

    @Test
    @DisplayName("提交失败：未覆盖该组全部未提交明细 → EVAL_REWARD_SUM_MISMATCH（人数不符）")
    void submit_notCoverAll() {
        when(batchMapper.selectById(5L)).thenReturn(activeBatch());
        when(itemMapper.selectByAssignerBatchDept("A1", 5L, "信贷部"))
                .thenReturn(List.of(item(11L, "信贷部", "100"), item(12L, "信贷部", "100")));
        // 只提交 1 条
        assertThatThrownBy(() -> service.submitRewardBatch("A1", 5L, "信贷部", List.of(entry(11L, "100"))))
                .isInstanceOf(PerfException.class);
        verify(itemMapper, never()).markAssigned(any(), any(), any());
    }

    @Test
    @DisplayName("提交失败：批次已过截止 → EVAL_TASK_CLOSED")
    void submit_deadlinePassed() {
        EvalAssignBatch b = activeBatch();
        b.setDeadline(LocalDateTime.now().minusDays(1));
        when(batchMapper.selectById(5L)).thenReturn(b);
        assertThatThrownBy(() -> service.submitRewardBatch("A1", 5L, "信贷部", List.of(entry(11L, "100"))))
                .isInstanceOf(PerfException.class)
                .hasMessageContaining("任务已结束");
    }

    @Test
    @DisplayName("提交失败：批次非 ACTIVE → EVAL_BATCH_NOT_ACTIVE")
    void submit_notActive() {
        EvalAssignBatch b = activeBatch();
        b.setStatus(2); // DRAFT
        when(batchMapper.selectById(5L)).thenReturn(b);
        assertThatThrownBy(() -> service.submitRewardBatch("A1", 5L, "信贷部", List.of(entry(11L, "100"))))
                .isInstanceOf(PerfException.class);
    }

    private SubmitRewardEntryFixture entry(long id, String v) {
        return new SubmitRewardEntryFixture(id, new BigDecimal(v));
    }

    /** 测试内联的 (itemId, assignValue) 二元组，映射到 service 的 record 入参。 */
    record SubmitRewardEntryFixture(Long itemId, BigDecimal assignValue) {
    }
}
```

> 注：`entry(...)` 产出的 `SubmitRewardEntryFixture` 需转换为 service 的 `EvalRewardService.RewardEntry`。为避免类型不匹配，Step 1 测试里把 `service.submitRewardBatch(...)` 的第 4 参改为 `List<EvalRewardService.RewardEntry>`，并将 `entry()` 直接构造 `new EvalRewardService.RewardEntry(id, new BigDecimal(v))`。实现见下（RewardEntry 为 service 内 record）。**实施时删除 SubmitRewardEntryFixture，直接用 RewardEntry**。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalRewardServiceTest`
Expected: 编译失败 —— `EvalRewardService` 不存在。

- [ ] **Step 3: 写实现**

```java
package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.dto.DictItemDTO;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.dto.EvalRewardPendingGroupDTO;
import com.bank.branch.platform.performance.eval.dto.EvalRewardPendingItemDTO;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalRewardItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRewardItemMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 奖励分配（REWARD）用户端服务。
 *
 * <p>当前登录人作为分配人：按部门查看待分配汇总、查看某部门明细、一次性提交整组分配。</p>
 */
@Slf4j
@Service
public class EvalRewardService {

    private static final String DICT_IMPORT_TYPE = "EVAL_IMPORT_TYPE";

    private final EvalRewardItemMapper itemMapper;
    private final EvalAssignBatchMapper batchMapper;
    private final DictApi dictApi;

    @Autowired
    public EvalRewardService(EvalRewardItemMapper itemMapper,
                             EvalAssignBatchMapper batchMapper,
                             DictApi dictApi) {
        this.itemMapper = itemMapper;
        this.batchMapper = batchMapper;
        this.dictApi = dictApi;
    }

    /** 我的奖励分配待处理汇总（按部门聚合未提交明细）。 */
    public List<EvalRewardPendingGroupDTO> listMyRewardPendingGroups(String assignUserId) {
        List<EvalRewardPendingGroupDTO> groups = itemMapper.selectRewardPendingGroups(assignUserId);
        Map<String, String> labelMap = new HashMap<>();
        for (DictItemDTO d : dictApi.getDictItems(DICT_IMPORT_TYPE)) {
            labelMap.put(d.getDictCode(), d.getDictLabel());
        }
        for (EvalRewardPendingGroupDTO g : groups) {
            g.setTaskTypeLabel(labelMap.getOrDefault(g.getTaskType(), g.getTaskType()));
        }
        return groups;
    }

    /** 查询我在指定批次+部门下的全部明细（含已提交）。 */
    public List<EvalRewardPendingItemDTO> listMyRewardPendingItems(String assignUserId, Long batchId, String dept) {
        List<EvalRewardItem> items = itemMapper.selectByAssignerBatchDept(
                assignUserId, batchId, dept == null ? "" : dept);
        return items.stream().map(this::toItemDTO).collect(Collectors.toList());
    }

    /**
     * 一次性提交某(分配人+批次+部门)组的分配。
     *
     * <p>校验：批次 ACTIVE 且未过期 → entries 覆盖该组全部未提交明细且归属当前人 →
     * 每人分配值>0 → 求和严格等于该组分配合计 → 同一事务逐条写库(all-or-none)。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitRewardBatch(String assignUserId, Long batchId, String dept, List<RewardEntry> entries) {
        EvalAssignBatch batch = batchMapper.selectById(batchId);
        if (batch == null || batch.getDeadline() == null || !batch.getDeadline().isAfter(LocalDateTime.now())) {
            throw new PerfException(PerfErrorCode.EVAL_TASK_CLOSED, batchId);
        }
        if (batch.getStatus() == null || batch.getStatus() != 0) {
            throw new PerfException(PerfErrorCode.EVAL_BATCH_NOT_ACTIVE);
        }
        List<EvalRewardItem> groupItems = itemMapper.selectByAssignerBatchDept(assignUserId, batchId, dept);
        // 该组未提交明细（提交对象）
        Map<Long, EvalRewardItem> unsubmitted = groupItems.stream()
                .filter(i -> i.getSubmitted() != null && i.getSubmitted() == 0)
                .collect(Collectors.toMap(EvalRewardItem::getItemId, i -> i));
        if (entries == null || entries.isEmpty() || unsubmitted.isEmpty()) {
            throw new PerfException(PerfErrorCode.EVAL_ASSIGN_ITEM_NOT_FOUND, batchId);
        }
        // 必须覆盖该组全部未提交明细（给每个人都分配）
        if (entries.size() != unsubmitted.size()) {
            throw new PerfException(PerfErrorCode.EVAL_REWARD_SUM_MISMATCH);
        }
        // 该组分配合计（组内一致，取任一条）
        BigDecimal groupTotal = unsubmitted.values().iterator().next().getAssignTotal();

        BigDecimal sum = BigDecimal.ZERO;
        for (RewardEntry e : entries) {
            EvalRewardItem it = unsubmitted.get(e.itemId());
            if (it == null) {
                // 明细不属于该组未提交集合（非归属 / 已提交 / 不存在）
                throw new PerfException(PerfErrorCode.EVAL_ASSIGN_ITEM_NOT_FOUND, e.itemId());
            }
            if (e.assignValue() == null || e.assignValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new PerfException(PerfErrorCode.EVAL_REWARD_ASSIGN_NOT_POSITIVE);
            }
            sum = sum.add(e.assignValue());
        }
        // 求和严格等于分配合计
        if (groupTotal == null || sum.compareTo(groupTotal) != 0) {
            throw new PerfException(PerfErrorCode.EVAL_REWARD_SUM_MISMATCH);
        }
        LocalDateTime now = LocalDateTime.now();
        for (RewardEntry e : entries) {
            itemMapper.markAssigned(e.itemId(), e.assignValue(), now);
        }
        log.info("[EvalRewardService.submitRewardBatch] 提交成功 assignUserId={} batchId={} dept={} count={}",
                assignUserId, batchId, dept, entries.size());
    }

    /** 批量提交单条:明细ID + 分配值. */
    public record RewardEntry(Long itemId, BigDecimal assignValue) {
    }

    private EvalRewardPendingItemDTO toItemDTO(EvalRewardItem i) {
        EvalRewardPendingItemDTO d = new EvalRewardPendingItemDTO();
        d.setItemId(i.getItemId());
        d.setBeAssignedUserId(i.getBeAssignedUserId());
        d.setBeAssignedUserName(i.getBeAssignedUserName());
        d.setDeptName(i.getDeptName());
        d.setOriginalValue(i.getOriginalValue());
        d.setCashValue(i.getCashValue());
        d.setAssignTotal(i.getAssignTotal());
        d.setAssignValue(i.getAssignValue());
        d.setSubmitted(i.getSubmitted());
        return d;
    }
}
```

- [ ] **Step 4: 修正测试的 entry 类型**

把 Step 1 测试里的 `entry(...)` 改为返回 `EvalRewardService.RewardEntry`，`submitRewardBatch` 第 4 参类型 `List<EvalRewardService.RewardEntry>`，删除 `SubmitRewardEntryFixture` record。即：

```java
    private EvalRewardService.RewardEntry entry(long id, String v) {
        return new EvalRewardService.RewardEntry(id, new BigDecimal(v));
    }
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalRewardServiceTest`
Expected: BUILD SUCCESS，全绿。

- [ ] **Step 6: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalRewardService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalRewardServiceTest.java
git commit -m "feat(perf-eval): EvalRewardService 汇总/明细/一次性分配提交 + 单测"
```

---

## Phase 4：管理端服务 `EvalRewardAdminService`（TDD）

### Task 4.1：管理端服务（先写失败测试）

**Files:**
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalRewardAdminServiceTest.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalRewardAdminService.java`

> 说明：`publishBatch`（草稿 2→ACTIVE 0）复用现有 `EvalAssignAdminService.publishBatch`（只改批次状态，与 item 表无关），本类不重复实现。本类负责 REWARD 专属的批次列表/详情/导出。

- [ ] **Step 1: 写失败测试**

```java
package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalRewardItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRewardItemMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalRewardAdminServiceTest {

    @Mock EvalAssignBatchMapper batchMapper;
    @Mock EvalRewardItemMapper itemMapper;
    @Mock UserApi userApi;
    EvalRewardAdminService service;

    @BeforeEach
    void setUp() {
        service = new EvalRewardAdminService(batchMapper, itemMapper, userApi);
    }

    private EvalRewardItem item(long id, String be, String total) {
        EvalRewardItem it = new EvalRewardItem();
        it.setItemId(id);
        it.setAssignUserId("ID_A1");
        it.setBeAssignedUserId(be);
        it.setBeAssignedUserName("被" + id);
        it.setDeptName("信贷部");
        it.setOriginalValue(new BigDecimal("76.5"));
        it.setCashValue(new BigDecimal("80"));
        it.setAssignTotal(new BigDecimal(total));
        it.setSubmitted(0);
        return it;
    }

    @Test
    @DisplayName("导出：分配人工号列输出 PT_USER.username（经 UserApi 反查）；数值列正确")
    void exportItems_assignerUsernameAndValues() throws Exception {
        EvalAssignBatch batch = new EvalAssignBatch();
        batch.setBatchId(9L);
        when(batchMapper.selectById(9L)).thenReturn(batch);
        when(itemMapper.selectByBatchId(eq(9L), anyInt(), anyInt())).thenReturn(List.of(item(1L, "B1", "100")));
        when(itemMapper.selectDistinctAssignerIdsByBatch(9L)).thenReturn(List.of("ID_A1"));
        when(userApi.mapEmpIdsToUsername(anyList())).thenReturn(java.util.Map.of("ID_A1", "assigner01"));

        byte[] data = service.exportItems(9L);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = wb.getSheetAt(0);
            // 表头：被分配人工号/姓名/部门/原始值/兑现值/分配合计/分配人工号/分配值/提交状态
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("被分配人工号");
            assertThat(sheet.getRow(0).getCell(6).getStringCellValue()).isEqualTo("分配人工号");
            Row r1 = sheet.getRow(1);
            assertThat(r1.getCell(0).getStringCellValue()).isEqualTo("B1");
            assertThat(r1.getCell(6).getStringCellValue()).isEqualTo("assigner01");
        }
    }

    @Test
    @DisplayName("批次详情：分配人工号(USER_ID)经 UserApi 翻译为 username")
    void getBatchDetail_fillsAssignerUsername() {
        EvalAssignBatch batch = new EvalAssignBatch();
        batch.setBatchId(9L);
        when(batchMapper.selectById(9L)).thenReturn(batch);
        when(itemMapper.selectByBatchId(eq(9L), anyInt(), anyInt())).thenReturn(List.of(item(1L, "B1", "100")));
        when(itemMapper.countByBatchId(9L)).thenReturn(1L);
        when(userApi.mapEmpIdsToUsername(anyList())).thenReturn(java.util.Map.of("ID_A1", "assigner01"));

        @SuppressWarnings("unchecked")
        PageResult<EvalRewardItem> page =
                (PageResult<EvalRewardItem>) service.getBatchDetail(9L, 1, 50).get("items");
        assertThat(page.getRecords().get(0).getAssignUserUsername()).isEqualTo("assigner01");
    }

    @Test
    @DisplayName("批次列表：委托 mapper REWARD 过滤查询")
    void pageBatches_delegates() {
        when(itemMapper.selectRewardBatchesByCondition(eq(2), eq("k"), anyInt(), anyInt()))
                .thenReturn(List.of(new EvalAssignBatch()));
        when(itemMapper.countRewardBatchesByCondition(2, "k")).thenReturn(1L);
        PageResult<EvalAssignBatch> page = service.pageBatches(2, "k", 1, 20);
        assertThat(page.getTotal()).isEqualTo(1L);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalRewardAdminServiceTest`
Expected: 编译失败 —— `EvalRewardAdminService` 不存在。

- [ ] **Step 3: 写实现（镜像 EvalAssignAdminService）**

```java
package com.bank.branch.platform.performance.eval.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.entity.EvalRewardItem;
import com.bank.branch.platform.performance.eval.mapper.EvalAssignBatchMapper;
import com.bank.branch.platform.performance.eval.mapper.EvalRewardItemMapper;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 奖励分配（REWARD）批次管理端服务。
 * <p>提供 REWARD 批次列表、详情（含分页明细）、Excel 导出。发布(草稿→ACTIVE)复用
 * {@link EvalAssignAdminService#publishBatch(Long)}（仅改批次状态）。</p>
 */
@Slf4j
@Service
public class EvalRewardAdminService {

    private static final int EXPORT_PAGE_SIZE = 5000;

    private final EvalAssignBatchMapper batchMapper;
    private final EvalRewardItemMapper itemMapper;
    private final UserApi userApi;

    @Autowired
    public EvalRewardAdminService(EvalAssignBatchMapper batchMapper,
                                  EvalRewardItemMapper itemMapper,
                                  UserApi userApi) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.userApi = userApi;
    }

    /** 分页查询 REWARD 导入批次列表。 */
    public PageResult<EvalAssignBatch> pageBatches(Integer status, String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<EvalAssignBatch> records = itemMapper.selectRewardBatchesByCondition(status, keyword, offset, pageSize);
        long total = itemMapper.countRewardBatchesByCondition(status, keyword);
        PageResult<EvalAssignBatch> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setPageNo(page);
        result.setPageSize(pageSize);
        return result;
    }

    /** 查询批次详情（含分页明细，分配人工号反查 username）。 */
    public Map<String, Object> getBatchDetail(Long batchId, int page, int pageSize) {
        EvalAssignBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new PerfException(PerfErrorCode.EVAL_ASSIGN_ITEM_NOT_FOUND, batchId);
        }
        int offset = (page - 1) * pageSize;
        List<EvalRewardItem> items = itemMapper.selectByBatchId(batchId, offset, pageSize);
        long total = itemMapper.countByBatchId(batchId);
        fillAssignerUsernames(items);

        Map<String, Object> result = new HashMap<>();
        result.put("batch", batch);
        PageResult<EvalRewardItem> itemPage = new PageResult<>();
        itemPage.setRecords(items);
        itemPage.setTotal(total);
        itemPage.setPageNo(page);
        itemPage.setPageSize(pageSize);
        result.put("items", itemPage);
        return result;
    }

    private void fillAssignerUsernames(List<EvalRewardItem> items) {
        if (items == null || items.isEmpty()) return;
        Set<String> ids = new LinkedHashSet<>();
        for (EvalRewardItem it : items) {
            if (it.getAssignUserId() != null && !it.getAssignUserId().isEmpty()) ids.add(it.getAssignUserId());
        }
        if (ids.isEmpty()) return;
        Map<String, String> idToUsername = userApi.mapEmpIdsToUsername(new ArrayList<>(ids));
        for (EvalRewardItem it : items) {
            it.setAssignUserUsername(idToUsername.getOrDefault(it.getAssignUserId(), it.getAssignUserId()));
        }
    }

    /** 导出批次明细为 Excel（流式）。 */
    public byte[] exportItems(Long batchId) {
        EvalAssignBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new PerfException(PerfErrorCode.EVAL_ASSIGN_ITEM_NOT_FOUND, batchId);
        }
        List<String> assignerIds = itemMapper.selectDistinctAssignerIdsByBatch(batchId);
        Map<String, String> idToUsername = (assignerIds == null || assignerIds.isEmpty())
                ? java.util.Collections.emptyMap()
                : userApi.mapEmpIdsToUsername(assignerIds);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        SXSSFWorkbook wb = new SXSSFWorkbook(100);
        try {
            Sheet sheet = wb.createSheet("奖励分配明细");
            Row header = sheet.createRow(0);
            String[] cols = {"被分配人工号", "被分配人姓名", "部门名称", "原始值", "兑现值",
                    "分配合计", "分配人工号", "分配值", "提交状态", "提交时间"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            int rowIdx = 1;
            int offset = 0;
            while (true) {
                List<EvalRewardItem> items = itemMapper.selectByBatchId(batchId, offset, EXPORT_PAGE_SIZE);
                if (items == null || items.isEmpty()) break;
                for (EvalRewardItem it : items) {
                    Row row = sheet.createRow(rowIdx++);
                    String assigner = idToUsername.getOrDefault(it.getAssignUserId(), it.getAssignUserId());
                    row.createCell(0).setCellValue(it.getBeAssignedUserId() == null ? "" : it.getBeAssignedUserId());
                    row.createCell(1).setCellValue(it.getBeAssignedUserName() == null ? "" : it.getBeAssignedUserName());
                    row.createCell(2).setCellValue(it.getDeptName() == null ? "" : it.getDeptName());
                    row.createCell(3).setCellValue(it.getOriginalValue() == null ? "" : it.getOriginalValue().toPlainString());
                    row.createCell(4).setCellValue(it.getCashValue() == null ? "" : it.getCashValue().toPlainString());
                    row.createCell(5).setCellValue(it.getAssignTotal() == null ? "" : it.getAssignTotal().toPlainString());
                    row.createCell(6).setCellValue(assigner == null ? "" : assigner);
                    row.createCell(7).setCellValue(it.getAssignValue() == null ? "" : it.getAssignValue().toPlainString());
                    row.createCell(8).setCellValue(it.getSubmitted() != null && it.getSubmitted() == 1 ? "已提交" : "未提交");
                    row.createCell(9).setCellValue(it.getSubmitTime() == null ? "" : it.getSubmitTime().format(dtf));
                }
                if (items.size() < EXPORT_PAGE_SIZE) break;
                offset += EXPORT_PAGE_SIZE;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("[EvalRewardAdminService.exportItems] 导出失败 batchId={}", batchId, e);
            throw new PerfException(PerfErrorCode.EVAL_IMPORT_FILE_INVALID, "Excel 生成失败");
        } finally {
            wb.dispose();
            try {
                wb.close();
            } catch (IOException ignore) {
                // 关闭异常不影响已生成字节流
            }
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl performance-engine-center test -Dtest=EvalRewardAdminServiceTest`
Expected: BUILD SUCCESS，全绿。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/service/EvalRewardAdminService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/service/EvalRewardAdminServiceTest.java
git commit -m "feat(perf-eval): EvalRewardAdminService 批次列表/详情/导出 + 单测"
```

---

## Phase 5：Controller

### Task 5.1：管理端控制器 `EvalRewardAdminController`

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/controller/EvalRewardAdminController.java`

- [ ] **Step 1: 写控制器（镜像 EvalAssignAdminController + 批次列表/详情/发布/导出）**

```java
package com.bank.branch.platform.performance.eval.controller;

import com.alibaba.excel.EasyExcel;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.dto.EvalAssignImportAcceptedDTO;
import com.bank.branch.platform.performance.eval.dto.EvalRewardImportRow;
import com.bank.branch.platform.performance.eval.entity.EvalAssignBatch;
import com.bank.branch.platform.performance.eval.service.EvalAssignAdminService;
import com.bank.branch.platform.performance.eval.service.EvalRewardAdminService;
import com.bank.branch.platform.performance.eval.service.EvalRewardImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 奖励分配（REWARD）管理端控制器。
 * <p>模板下载、Excel 导入（异步受理）、批次列表/详情、发布、导出。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/eval/reward")
@Tag(name = "Eval Reward", description = "奖励分配导入（管理端）")
@RequiredArgsConstructor
public class EvalRewardAdminController {

    private final EvalRewardImportService importService;
    private final EvalRewardAdminService adminService;
    private final EvalAssignAdminService assignAdminService; // 复用 publishBatch
    private final CurrentUserApi currentUserApi;

    /** 下载奖励分配导入模板（8 列）。 */
    @GetMapping("/import-template")
    @Operation(summary = "下载奖励分配导入模板")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.IMPORT)
    public void importTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode("奖励分配导入模板.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        EvalRewardImportRow sample = new EvalRewardImportRow();
        sample.setBeAssignedUserId("100002");
        sample.setBeAssignedUserName("张三");
        sample.setDeptName("信贷部");
        sample.setOriginalValue(new BigDecimal("76.5"));
        sample.setAssignValueIgnored("");
        sample.setCashValue(new BigDecimal("80"));
        sample.setAssignUserId("100001");
        sample.setAssignTotal(new BigDecimal("100"));
        EasyExcel.write(response.getOutputStream(), EvalRewardImportRow.class)
                .sheet("奖励分配")
                .doWrite(List.of(sample));
    }

    /** 导入奖励分配（Excel，异步受理）。 */
    @PostMapping("/import")
    @Operation(summary = "导入奖励分配（异步受理）")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.IMPORT)
    public ResponseWrapper<EvalAssignImportAcceptedDTO> importExcel(
            @RequestPart("file") MultipartFile file,
            @RequestParam("taskName") String taskName,
            @RequestParam("deadline")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime deadline) {
        String createBy = currentUserApi.getCurrentEmpId();
        log.info("[EvalRewardAdminController.importExcel] fileName={}, taskName={}, deadline={}, createBy={}",
                file != null ? file.getOriginalFilename() : null, taskName, deadline, createBy);
        List<EvalRewardImportRow> rows = importService.parseRows(file);
        Long batchId = importService.createImportingBatch(taskName, deadline, createBy);
        importService.processImport(batchId, rows, taskName, deadline, createBy);
        return ResponseWrapper.success(new EvalAssignImportAcceptedDTO(batchId, 3));
    }

    /** 分页查询 REWARD 批次列表。 */
    @GetMapping("/batches")
    @Operation(summary = "奖励分配批次列表")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<PageResult<EvalAssignBatch>> batches(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseWrapper.success(adminService.pageBatches(status, keyword, page, pageSize));
    }

    /** 批次详情（含分页明细）。 */
    @GetMapping("/batches/{batchId}")
    @Operation(summary = "奖励分配批次详情")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.READ)
    public ResponseWrapper<Map<String, Object>> batchDetail(
            @PathVariable("batchId") Long batchId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseWrapper.success(adminService.getBatchDetail(batchId, page, pageSize));
    }

    /** 发布草稿批次 → ACTIVE（复用通用 publishBatch）。 */
    @PostMapping("/batches/{batchId}/publish")
    @Operation(summary = "发布奖励分配批次")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<EvalAssignBatch> publish(@PathVariable("batchId") Long batchId) {
        return ResponseWrapper.success(assignAdminService.publishBatch(batchId));
    }

    /** 导出批次明细 Excel。 */
    @GetMapping("/batches/{batchId}/export")
    @Operation(summary = "导出奖励分配批次明细")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.EXPORT)
    public void export(@PathVariable("batchId") Long batchId, HttpServletResponse response) throws IOException {
        byte[] data = adminService.exportItems(batchId);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode("奖励分配明细.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        response.getOutputStream().write(data);
    }
}
```

- [ ] **Step 2: 编译确认**

Run: `mvn -q -pl performance-engine-center compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/controller/EvalRewardAdminController.java
git commit -m "feat(perf-eval): EvalRewardAdminController 管理端端点"
```

### Task 5.2：用户端控制器 `EvalRewardPendingController`

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/controller/EvalRewardPendingController.java`

- [ ] **Step 1: 写控制器**

```java
package com.bank.branch.platform.performance.eval.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.eval.dto.EvalRewardPendingGroupDTO;
import com.bank.branch.platform.performance.eval.dto.EvalRewardPendingItemDTO;
import com.bank.branch.platform.performance.eval.dto.SubmitRewardBatchReq;
import com.bank.branch.platform.performance.eval.service.EvalRewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 奖励分配（REWARD）用户端控制器。
 * <p>当前登录人作为分配人：按部门查看待分配汇总、查看某部门明细、一次性提交整组分配。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/eval/reward-tasks")
@Tag(name = "Eval Reward Pending", description = "奖励分配（用户端）")
@Validated
@RequiredArgsConstructor
public class EvalRewardPendingController {

    private final EvalRewardService rewardService;
    private final CurrentUserApi currentUserApi;

    /** 我的奖励分配待处理汇总（按部门聚合未提交明细）。 */
    @GetMapping
    @Operation(summary = "我的奖励分配待处理汇总")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.LIST)
    public ResponseWrapper<List<EvalRewardPendingGroupDTO>> myPending() {
        String assignUserId = currentUserApi.getCurrentEmpId();
        log.debug("[EvalRewardPendingController.myPending] assignUserId={}", assignUserId);
        return ResponseWrapper.success(rewardService.listMyRewardPendingGroups(assignUserId));
    }

    /** 处理某部门：查询该批次+部门下分配给我的明细。 */
    @GetMapping("/items")
    @Operation(summary = "奖励分配明细")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.READ)
    public ResponseWrapper<List<EvalRewardPendingItemDTO>> items(
            @RequestParam("batchId") Long batchId,
            @RequestParam(value = "dept", required = false, defaultValue = "") String dept) {
        String assignUserId = currentUserApi.getCurrentEmpId();
        log.debug("[EvalRewardPendingController.items] assignUserId={}, batchId={}, dept={}", assignUserId, batchId, dept);
        return ResponseWrapper.success(rewardService.listMyRewardPendingItems(assignUserId, batchId, dept));
    }

    /** 一次性提交某部门下全部被分配人的分配值。 */
    @PostMapping("/submit-batch")
    @Operation(summary = "提交奖励分配")
    @BizAuth(bizType = BizType.EVAL, action = BizAction.WRITE)
    public ResponseWrapper<Void> submit(@RequestBody @Valid SubmitRewardBatchReq req) {
        String assignUserId = currentUserApi.getCurrentEmpId();
        log.info("[EvalRewardPendingController.submit] assignUserId={}, batchId={}, dept={}, count={}",
                assignUserId, req.getBatchId(), req.getDept(), req.getItems().size());
        List<EvalRewardService.RewardEntry> entries = req.getItems().stream()
                .map(i -> new EvalRewardService.RewardEntry(i.getItemId(), i.getAssignValue()))
                .toList();
        rewardService.submitRewardBatch(assignUserId, req.getBatchId(), req.getDept(), entries);
        return ResponseWrapper.success();
    }
}
```

- [ ] **Step 2: 编译确认**

Run: `mvn -q -pl performance-engine-center compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/eval/controller/EvalRewardPendingController.java
git commit -m "feat(perf-eval): EvalRewardPendingController 用户端端点"
```

### Task 5.3：Mapper IT（真库聚合查询）

**Files:**
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/mapper/EvalRewardItemMapperIT.java`

> 参考现有 `EvalAssignItemMapperIT`（同目录）的基类与 @Sql 注入方式；命名 `*IT.java` 走 failsafe。

- [ ] **Step 1: 定位现有 IT 基类**

Run: `ls performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/mapper/`
读现有 `EvalAssignItemMapperIT.java` 头部，照抄其 `@SpringBootTest`/`@Sql`/基类继承方式。

- [ ] **Step 2: 写 IT（覆盖 selectRewardPendingGroups 聚合 + selectByAssignerBatchDept）**

按现有 IT 基类模式写：先插一个 ACTIVE、未过期、task_type=REWARD 批次；插同一分配人同部门 2 条未提交明细（assign_total=100）；断言 `selectRewardPendingGroups(assignUserId)` 返回 1 组、pendingCount=2、assignTotal=100；`selectByAssignerBatchDept` 返回 2 条。（具体基类样板以 Step 1 读到的现有 IT 为准，保持一致。）

- [ ] **Step 3: 跑 IT**

Run:
```bash
mvn clean install -DskipTests -q
mvn -q -pl performance-engine-center verify -Dit.test=EvalRewardItemMapperIT
```
Expected: 绿。

- [ ] **Step 4: Commit**

```bash
git add performance-engine-center/src/test/java/com/bank/branch/platform/performance/eval/mapper/EvalRewardItemMapperIT.java
git commit -m "test(perf-eval): EvalRewardItemMapperIT 聚合/明细查询 IT"
```

---

## Phase 6：前端

### Task 6.1：api/eval.js 新增 reward 接口

**Files:**
- Modify: `xanzc_frontend/src/api/eval.js`（在文件末尾/待处理任务段之后追加）

- [ ] **Step 1: 追加接口函数**

在 `eval.js` 中现有 `pending-tasks` 段之后追加（`call` 为该文件已有的统一请求包装，签名参考现有 `call('get', '/eval/pending-tasks', ...)`）：

```js
// 奖励分配（用户端） (EvalRewardPendingController: /api/eval/reward-tasks)
export function rewardPendingGroups() {
  return call('get', '/eval/reward-tasks', {}, []);
}
export function rewardPendingItems(batchId, dept) {
  return call('get', '/eval/reward-tasks/items', { params: { batchId, dept } }, []);
}
export function submitRewardBatch(batchId, dept, items) {
  // items: [{ itemId, assignValue }]
  return call('post', '/eval/reward-tasks/submit-batch', { data: { batchId, dept, items } }, { ok: true });
}

// 奖励分配（管理端） (EvalRewardAdminController: /api/admin/eval/reward)
export function importReward(formData) {
  // formData 含 file / taskName / deadline
  return call('post', '/admin/eval/reward/import', { data: formData }, null);
}
export function rewardBatches(params) {
  return call('get', '/admin/eval/reward/batches', { params }, { records: [], total: 0 });
}
export function rewardBatchDetail(batchId, params) {
  return call('get', `/admin/eval/reward/batches/${batchId}`, { params }, null);
}
export function publishRewardBatch(batchId) {
  return call('post', `/admin/eval/reward/batches/${batchId}/publish`, {}, null);
}
```

> 模板下载/导出为浏览器直下（`window.open` 或 a 标签指向 `/api/admin/eval/reward/import-template`、`/api/admin/eval/reward/batches/{id}/export`），按现有 EVAL 导入页同款方式处理，不走 `call`。

- [ ] **Step 2: 校对现有 `call` 签名**

Run: `grep -n "function call\|const call" xanzc_frontend/src/api/eval.js`
按实际签名微调上面 4 个参数位置（method/url/config/fallback），确保与现有 EVAL 接口写法一致。

- [ ] **Step 3: Commit**

```bash
git add xanzc_frontend/src/api/eval.js
git commit -m "feat(perf-fe): api/eval.js 新增奖励分配用户端/管理端接口"
```

### Task 6.2：MyTasks.vue 汇总合并 EVAL + REWARD

**Files:**
- Modify: `xanzc_frontend/src/views/eval/MyTasks.vue`

- [ ] **Step 1: 读现有 MyTasks.vue**

Run: `sed -n '1,80p' xanzc_frontend/src/views/eval/MyTasks.vue`
了解现有汇总拉取（调 `pendingGroups()`）与表格渲染、行点击跳转逻辑。

- [ ] **Step 2: 并行拉取两个汇总并合并**

改造加载逻辑：`Promise.all([pendingGroups(), rewardPendingGroups()])`，把两份结果合并为一个数组渲染（EVAL 行保留原字段；REWARD 行带 `taskType='REWARD'` 与 `assignTotal`）。行点击时：`row.taskType === 'REWARD'` → 跳转/打开奖励分配明细组件（携带 `batchId`、`dept`）；否则走原有打分明细。列展示保持「任务类型(taskTypeLabel)/部门(dept)/待处理数(pendingCount)/截止时间」通用列。

（具体 diff 依 Step 1 读到的现有结构编写，保持与现有代码风格一致；REWARD 明细可用路由参数区分或条件渲染 `RewardTask.vue`。）

- [ ] **Step 3: 前端本地验证**

Run: `cd xanzc_frontend && npm run build`
Expected: 构建通过（无语法/引用错误）。

- [ ] **Step 4: Commit**

```bash
git add xanzc_frontend/src/views/eval/MyTasks.vue
git commit -m "feat(perf-fe): 待处理汇总页合并 EVAL + REWARD"
```

### Task 6.3：RewardTask.vue 奖励分配明细页

**Files:**
- Create: `xanzc_frontend/src/views/eval/RewardTask.vue`

- [ ] **Step 1: 写组件（表格 + 快捷填充 + 剩余提示 + 提交）**

要点（用 Element Plus，风格对齐现有 `Tasks.vue`）：
- 进入时调 `rewardPendingItems(batchId, dept)` 拉明细；`assignTotal` 取首条 `assign_total`
- 表格列：被分配人工号 / 姓名 / 部门 / 原始值 / 兑现值 / 分配值（`el-input-number` 每行可编辑，min>0）
- 顶部信息条：
  - 「给每个人分配」`el-input-number` + 按钮 → 点击把所有行 `assignValue` 设为该值
  - 「距离还剩」= `assignTotal - Σ assignValue`（computed，负数红色显示）
- 「提交」按钮：`:disabled` 当 `remaining !== 0 || 任一行 assignValue<=0`；点击调 `submitRewardBatch(batchId, dept, rows.map(r => ({itemId: r.itemId, assignValue: r.assignValue})))`
- 提交成功后 `ElMessage.success` 并返回汇总/刷新

关键 computed 片段：

```js
const remaining = computed(() =>
  Number(assignTotal.value || 0) - rows.value.reduce((s, r) => s + Number(r.assignValue || 0), 0)
);
const canSubmit = computed(() =>
  remaining.value === 0 && rows.value.every(r => Number(r.assignValue) > 0)
);
function fillEach(v) { rows.value.forEach(r => (r.assignValue = v)); }
```

> 浮点相等风险：`remaining.value === 0` 对小数可能有精度误差。用整数分（乘 100 四舍五入比较）或 `Math.abs(remaining) < 1e-6` 判定。实施时采用 `Math.abs(remaining.value) < 1e-6`。后端仍以 BigDecimal 严格校验为准。

- [ ] **Step 2: 前端构建验证**

Run: `cd xanzc_frontend && npm run build`
Expected: 构建通过。

- [ ] **Step 3: Commit**

```bash
git add xanzc_frontend/src/views/eval/RewardTask.vue
git commit -m "feat(perf-fe): RewardTask.vue 奖励分配明细录入页（快捷填充+剩余提示）"
```

### Task 6.4：管理端导入页增加 REWARD 入口

**Files:**
- Modify: 现有 EVAL 导入所在管理页（Run 定位：`grep -rn "assign/import\|importTemplate\|评价任务导入" xanzc_frontend/src`）

- [ ] **Step 1: 定位现有 EVAL 导入 UI**

Run: `grep -rn "admin/eval/assign\|importAssign\|评价任务" xanzc_frontend/src`
找到现有 EVAL 导入入口页面（上传 + taskName + deadline + 模板下载 + 批次列表）。

- [ ] **Step 2: 复制一套 REWARD 导入区**

在同页/新增 tab 增加「奖励分配」导入：上传控件 → 调 `importReward(formData)`（formData 含 file/taskName/deadline）；模板下载指向 `/api/admin/eval/reward/import-template`；批次列表调 `rewardBatches`，详情 `rewardBatchDetail`，发布 `publishRewardBatch`，导出指向 `/api/admin/eval/reward/batches/{id}/export`。导入说明标注 8 列（被分配人工号/姓名/部门名称/原始值/分配值(留空)/兑现值/分配人工号/分配合计）。

- [ ] **Step 3: 构建验证 + Commit**

Run: `cd xanzc_frontend && npm run build`

```bash
git add xanzc_frontend/src
git commit -m "feat(perf-fe): 管理端新增奖励分配导入入口 + 批次管理"
```

---

## Phase 7：PT_RESOURCE 资源 + 全量回归

### Task 7.1：注册 PT_RESOURCE 资源

**Files:**
- Create: `docs/superpowers/sql/2026-07-11-eval-reward-resources.sql`

- [ ] **Step 1: 参照 2026-06-10 EVAL 资源命名，写 REWARD 端点资源行**

Run: `grep -rn "PERF_EVAL_2\|/api/admin/eval/assign\|/api/eval/pending-tasks" docs/ | grep -i resource`
定位现有 EVAL 待处理任务的 PT_RESOURCE 注册行（`PERF_EVAL_23~27`），照抄结构为下列 7 个 REWARD 端点各注册一行（RESOURCE_ID 续编，如 `PERF_EVAL_30~36`；bizType=EVAL、action 对应 IMPORT/LIST/READ/WRITE/EXPORT）：
- GET `/api/admin/eval/reward/import-template`
- POST `/api/admin/eval/reward/import`
- GET `/api/admin/eval/reward/batches`
- GET `/api/admin/eval/reward/batches/{batchId}`
- POST `/api/admin/eval/reward/batches/{batchId}/publish`
- GET `/api/admin/eval/reward/batches/{batchId}/export`
- GET `/api/eval/reward-tasks`、`/items`、POST `/submit-batch`（3 个用户端端点）

- [ ] **Step 2: 目标库执行 + 角色绑定**

Run:
```bash
mysql -uroot -pdjdev yiti < docs/superpowers/sql/2026-07-11-eval-reward-resources.sql
mysql -uroot -pdjdev onepl_test_bootstrap < docs/superpowers/sql/2026-07-11-eval-reward-resources.sql
```
并为管理员/分配人角色补 `pt_role_resource` 绑定（参照 EVAL 现有绑定行）。

> 记忆红线：鉴权失败(401/403/AUTH-40304) 先查 PT_* 表；新端点上线务必补角色-资源绑定，否则有效用户也会被 fail-close 拒绝。

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/sql/2026-07-11-eval-reward-resources.sql
git commit -m "feat(perf-eval): 注册奖励分配端点 PT_RESOURCE 资源 + 角色绑定"
```

### Task 7.2：全量回归

- [ ] **Step 1: 上游 install 防 stale jar**

Run: `mvn clean install -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: perf 模块全量单测**

Run: `mvn -q -pl performance-engine-center test`
Expected: 新增 3 个测试类全绿，既有 eval 测试不回归。

- [ ] **Step 3: failsafe IT**

Run: `mvn -q -pl performance-engine-center verify -DskipUTs=false`
Expected: `EvalRewardItemMapperIT` 绿；无新增红。

- [ ] **Step 4: 更新模块变更日志**

在 `performance-engine-center/CLAUDE.md` 顶部 eval 子域变更日志追加一行 `2026-07-11 eval 子域：奖励分配(REWARD) 全链路 ...` 摘要（表名/端点/错误码/前端页面/迁移脚本）。

```bash
git add performance-engine-center/CLAUDE.md
git commit -m "docs(perf-eval): CLAUDE.md 追加奖励分配 REWARD 变更日志"
```

---

## Self-Review 结果（spec 覆盖核对）

- §2 Excel 8 列 → Task 2.1 `EvalRewardImportRow` ✅
- §3.1 建表 → Task 0.1 ✅；§3.2 批次复用 → Task 2.3 `createImportingBatch(REWARD)` ✅
- §4 后端组件 → Task 1.2/1.3/2.3/3.2/4.1/5.1/5.2 ✅
- §5 导入校验（分配人有效/被分配人快照/数值/合计>0/组内一致/all-or-none）→ Task 2.3 validate + 测试 ✅
- §6 一次性提交（覆盖全组/每人>0/求和=合计/事务）→ Task 3.2 submitRewardBatch + 测试 ✅
- §7 错误码 40069/40070 → Task 1.1 ✅
- §8 前端（汇总合并/RewardTask/api/管理端）→ Task 6.1~6.4 ✅
- §9 迁移 & 资源 → Task 0.1 + Task 7.1 ✅
- §10 测试 → Task 2.3/3.2/4.1/5.3 ✅
- §11 非目标（不回写宽表/不做草稿/不校验被分配人）→ 设计与实现一致 ✅

类型一致性核对：`RewardEntry(Long, BigDecimal)`、`markAssigned(Long, BigDecimal, LocalDateTime)`、`createImportingBatch(String, LocalDateTime, String)`、`processImport(Long, List, String, LocalDateTime, String)` 在计划各处签名一致 ✅。
