# 审批流监控 + 转交待认领 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让秘书岗按机构监控进行中/已完成审批流并查看详情，且支持"发起即锁定 → 接收人认领生效 / 拒绝填理由退回"的两阶段转交。

**Architecture:** 全部落在 `workflow-center`（唯一可碰 Flowable 的模块）。新增两张表：`WF_PROCESS_ORG`（参与机构快照，把"经过本机构"物化成索引 EXISTS）与 `WF_TASK_TRANSFER`（转交生命周期）。监控查询复用平台数据范围体系（`@BizAuth` 注入 `DataScopeContext` → 服务按 `scope` 过滤）；转交用状态机表 + Flowable `setAssignee` 认领时才改办理人。前端加监控页 + 工作台待认领/我转出的。

**Tech Stack:** Spring Boot 3.2.3 / JDK17 / MyBatis-Plus（BaseMapper）/ Flowable 7.0.1 / Vue3 + Element Plus（xanzc_frontend）/ MySQL 8（库名 `yiti`）。

## Global Constraints

- 新增 DB 访问统一 **MyBatis-Plus**：Mapper `extends BaseMapper<T>`；单条 CRUD 用内置方法不写 XML；自定义 SQL 才落 `src/main/resources/mapper/workflow/*Mapper.xml`（namespace=全限定接口名）。
- 实体：`@TableName("表名")` + `@TableId(value="id", type=IdType.INPUT)`，id 为 `String`，手动 `UUID.randomUUID().toString().replace("-","")`。
- 跨模块只经 `*Api`（`OrgApi`/`CurrentUserApi`/`NotifyApi`/`BizScopeApi`），**禁止**直连其它模块 mapper/entity。
- **每个新接口必须**：登记 `PT_RESOURCE` 行（否则 `ResourceMatcher` 直接 403）；读接口加 `@BizAuth(bizType=..., action=...)`；高危写操作（发起/认领/拒绝/撤回转交）加 `@AuditLog(action=..., resourceType="WORKFLOW_TASK", reasonRequired=true)`。
- **TDD 红-绿-重构**：先写失败测试再写实现。`*Test.java`→surefire（`mvn test`）；`*IT.java`→failsafe（`mvn verify`）。
- **Flyway 已废弃**：schema 变更走 SQL 直接执行，DDL/seed 落 **yiti** 库。
- 分页默认 `pageSize=20`，最大 100。
- 跨模块改动后：`mvn clean install -DskipTests` 再 `mvn verify`（防 stale jar）。
- 打开/编辑文件一律 UTF-8。派遣 subagent 时 model ≥ sonnet。
- 机构↔用户 **1:1**；参与机构口径：仅在**有具体受理人**（claim/approve/transfer accept）或**流程发起**时记 org（仅挂候选组、无人认领的不记）。

---

## File Structure

**后端 workflow-center**
- Create `entity/WfProcessOrg.java` — 参与机构快照实体
- Create `entity/WfTaskTransfer.java` — 转交生命周期实体
- Create `mapper/WfProcessOrgMapper.java` + `resources/mapper/workflow/WfProcessOrgMapper.xml` — insertIgnore / 按实例查机构
- Create `mapper/WfTaskTransferMapper.java` + `resources/mapper/workflow/WfTaskTransferMapper.xml` — 活跃转交查询 / 乐观流转 / inbox / outbox
- Create `service/WfProcessOrgService.java` — `record(pi, empId, source)` 统一写入口（唯一挂点，防遗漏）
- Create `service/ProcessMonitorService.java` — 监控列表 + 数据范围过滤 + 批量补当前处理人机构
- Create `service/TaskTransferService.java` — 两阶段状态机（initiate/accept/decline/cancel/hasPendingTransfer）
- Create `controller/ProcessMonitorController.java` — `GET /api/workflow/monitor/processes`
- Create `controller/TaskTransferController.java` — 发起/收件箱/认领/拒绝/发件箱/撤回
- Create DTOs：`api/dto/ProcessMonitorItemDTO.java`, `api/dto/monitor/ProcessMonitorQuery.java`(或用 @RequestParam), `api/dto/TransferInitiateReqDTO.java`, `api/dto/TransferDecisionReqDTO.java`, `api/dto/TransferItemDTO.java`
- Modify `service/TaskOperationService.java` — approve/reject/claim 加转交锁卫语句；accept 时复用其 updateCurrentAssignee 思路
- Modify `service/TodoQueryService.java:269-277` — `RuntimeAccessDTO` 结合转交锁算 canApprove/canReject/canClaim
- Modify `service/ProcessStartService.java` — 发起时 `WfProcessOrgService.record(pi, startUser, "START")`
- Modify `listener/TaskAssignmentListener.java` — 有具体受理人时 record ASSIGN（视候选解析结果）
- Modify `mapper/BizProcessMapMapper.java` + `.xml` — 新增 `selectMonitorPage` / `countMonitor`（带 WF_PROCESS_ORG EXISTS）
- Delete/Deprecate 旧单阶段：`TaskController.transferTask` + `TaskOperationService.transferTask`

**枚举/种子/DDL**
- Modify `common/common-security/.../enums/BizType.java` — 加 `WORKFLOW_MONITOR`
- Create `docs/schema/ddl-workflow-monitor.sql` — 两表 DDL + 回填 SQL
- Modify seed（`docs/schema/seed-v1.sql` 或 `data.sql`）— 新端点 PT_RESOURCE 行 + 秘书/行长 `PT_ROLE_BIZ_SCOPE` + 侧边栏 `M_` 菜单行

**前端 xanzc_frontend**
- Create `src/views/system/WorkflowMonitor.vue` — 监控页
- Modify `src/api/workflow.js` — 补 monitor/transfer 封装
- Modify `src/views/workspace/Index.vue` — "待认领转交" + "我转出的(只读)"
- Modify `src/router/index.js` — 监控路由

---

## Phase P1 — 数据地基

### Task 1: `WF_PROCESS_ORG` 表 + 实体 + Mapper + 写入服务

**Files:**
- Create: `docs/schema/ddl-workflow-monitor.sql`
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/entity/WfProcessOrg.java`
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/mapper/WfProcessOrgMapper.java`
- Create: `workflow-center/src/main/resources/mapper/workflow/WfProcessOrgMapper.xml`
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/service/WfProcessOrgService.java`
- Test: `workflow-center/src/test/java/com/bank/branch/platform/workflow/service/WfProcessOrgServiceTest.java`

**Interfaces:**
- Produces: `WfProcessOrgService.record(String processInstanceId, String empId, String source)`（empId 为 null 时静默跳过）；`WfProcessOrgMapper.insertIgnore(WfProcessOrg)`；`WfProcessOrgMapper.selectOrgCodesByPi(String pi)`。
- Consumes: `OrgApi.getUserMainOrg(String empId)`（返回 `OrgDTO`，其 `getOrgCode()`）。

- [ ] **Step 1: 建表 DDL**（写入 `ddl-workflow-monitor.sql`，随后在 yiti 库执行）

```sql
CREATE TABLE IF NOT EXISTS `WF_PROCESS_ORG` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `process_instance_id` varchar(64) NOT NULL COMMENT 'Flowable流程实例ID',
  `org_code` varchar(32) NOT NULL COMMENT '参与人主机构编码',
  `source` varchar(16) NOT NULL COMMENT 'START/ASSIGN/CLAIM/APPROVE/TRANSFER/BACKFILL',
  `first_seen_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '首次记录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pi_org` (`process_instance_id`,`org_code`),
  KEY `idx_org_pi` (`org_code`,`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批流参与机构快照';
-- COLLATE 必须与 BIZ_PROCESS_MAP(utf8mb4_general_ci) 一致，否则 Task5 的 process_instance_id EXISTS-join 报 Illegal mix of collations
```

- [ ] **Step 2: 实体**（`WfProcessOrg.java`）

```java
package com.bank.branch.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("WF_PROCESS_ORG")
public class WfProcessOrg {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String processInstanceId;
    private String orgCode;
    private String source;
    private LocalDateTime firstSeenTime;
}
```

- [ ] **Step 3: Mapper 接口 + XML**

`WfProcessOrgMapper.java`
```java
package com.bank.branch.platform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.workflow.entity.WfProcessOrg;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WfProcessOrgMapper extends BaseMapper<WfProcessOrg> {

    /** 幂等插入：同实例同机构只留一条（依赖 uk_pi_org）。 */
    int insertIgnore(WfProcessOrg row);

    /** 某实例已记录的参与机构编码。 */
    List<String> selectOrgCodesByPi(String processInstanceId);
}
```

`resources/mapper/workflow/WfProcessOrgMapper.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.bank.branch.platform.workflow.mapper.WfProcessOrgMapper">

    <insert id="insertIgnore" parameterType="com.bank.branch.platform.workflow.entity.WfProcessOrg">
        INSERT IGNORE INTO WF_PROCESS_ORG (id, process_instance_id, org_code, source, first_seen_time)
        VALUES (#{id}, #{processInstanceId}, #{orgCode}, #{source}, #{firstSeenTime})
    </insert>

    <select id="selectOrgCodesByPi" parameterType="string" resultType="string">
        SELECT org_code FROM WF_PROCESS_ORG WHERE process_instance_id = #{processInstanceId}
    </select>
</mapper>
```

- [ ] **Step 4: 写失败测试**（`WfProcessOrgServiceTest.java`）

```java
package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.workflow.entity.WfProcessOrg;
import com.bank.branch.platform.workflow.mapper.WfProcessOrgMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WfProcessOrgServiceTest {

    @Mock private WfProcessOrgMapper mapper;
    @Mock private OrgApi orgApi;
    @InjectMocks private WfProcessOrgService service;

    @Test
    void record_insertsMainOrg() {
        OrgDTO org = new OrgDTO();
        org.setOrgCode("ORG_A");
        when(orgApi.getUserMainOrg("E001")).thenReturn(org);

        service.record("PID_1", "E001", "START");

        verify(mapper).insertIgnore(ArgumentMatchers.<WfProcessOrg>argThat(r ->
                "PID_1".equals(r.getProcessInstanceId())
                        && "ORG_A".equals(r.getOrgCode())
                        && "START".equals(r.getSource())
                        && r.getId() != null));
    }

    @Test
    void record_skipsWhenEmpIdNull() {
        service.record("PID_1", null, "ASSIGN");
        verifyNoInteractions(mapper, orgApi);
    }

    @Test
    void record_skipsWhenOrgUnknown() {
        when(orgApi.getUserMainOrg("E404")).thenReturn(null);
        service.record("PID_1", "E404", "ASSIGN");
        verify(mapper, never()).insertIgnore(any());
    }
}
```

- [ ] **Step 5: 运行确认失败** — `cd workflow-center && mvn -q test -Dtest=WfProcessOrgServiceTest` → FAIL（`WfProcessOrgService` 不存在，编译错）。

- [ ] **Step 6: 实现服务**（`WfProcessOrgService.java`）

```java
package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.workflow.entity.WfProcessOrg;
import com.bank.branch.platform.workflow.mapper.WfProcessOrgMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/** 审批流参与机构快照写入唯一入口（所有挂点都调这里，避免遗漏）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfProcessOrgService {

    private final WfProcessOrgMapper wfProcessOrgMapper;
    private final OrgApi orgApi;

    /**
     * 记录"某工号（其主机构）参与了该流程实例"。幂等。
     * empId 为空、或机构查不到时静默跳过（不阻断主流程）。
     */
    public void record(String processInstanceId, String empId, String source) {
        if (empId == null || empId.isBlank() || processInstanceId == null) {
            return;
        }
        OrgDTO org = orgApi.getUserMainOrg(empId);
        if (org == null || org.getOrgCode() == null) {
            log.warn("[WfProcessOrgService.record] 机构未知, empId={}, pi={}", empId, processInstanceId);
            return;
        }
        WfProcessOrg row = new WfProcessOrg();
        row.setId(UUID.randomUUID().toString().replace("-", ""));
        row.setProcessInstanceId(processInstanceId);
        row.setOrgCode(org.getOrgCode());
        row.setSource(source);
        row.setFirstSeenTime(LocalDateTime.now());
        wfProcessOrgMapper.insertIgnore(row);
    }
}
```

- [ ] **Step 7: 运行确认通过** — 同 Step 5 命令 → PASS。

- [ ] **Step 8: 提交**

```bash
git add docs/schema/ddl-workflow-monitor.sql \
  workflow-center/src/main/java/com/bank/branch/platform/workflow/entity/WfProcessOrg.java \
  workflow-center/src/main/java/com/bank/branch/platform/workflow/mapper/WfProcessOrgMapper.java \
  workflow-center/src/main/resources/mapper/workflow/WfProcessOrgMapper.xml \
  workflow-center/src/main/java/com/bank/branch/platform/workflow/service/WfProcessOrgService.java \
  workflow-center/src/test/java/com/bank/branch/platform/workflow/service/WfProcessOrgServiceTest.java
git commit -m "feat(workflow): WF_PROCESS_ORG 参与机构快照表+写入服务"
```

---

### Task 2: `WF_TASK_TRANSFER` 表 + 实体 + Mapper

**Files:**
- Modify: `docs/schema/ddl-workflow-monitor.sql`（追加第二张表）
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/entity/WfTaskTransfer.java`
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/mapper/WfTaskTransferMapper.java`
- Create: `workflow-center/src/main/resources/mapper/workflow/WfTaskTransferMapper.xml`
- Test: `workflow-center/src/test/java/com/bank/branch/platform/workflow/mapper/WfTaskTransferMapperIT.java`

**Interfaces:**
- Produces:
  - `WfTaskTransferMapper.selectActiveByTaskId(String taskId)` → 返回 status=PENDING_ACCEPT 的记录或 null
  - `WfTaskTransferMapper.updateStatusIfPending(@Param("id") String id, @Param("status") String status, @Param("rejectReason") String rejectReason, @Param("decidedTime") LocalDateTime t)` → 影响行数（乐观流转）
  - `WfTaskTransferMapper.selectInbox(String toEmpId)` / `selectOutbox(String fromEmpId)` → `List<WfTaskTransfer>`
  - 状态常量约定：`PENDING_ACCEPT` / `ACCEPTED` / `REJECTED` / `CANCELLED`

- [ ] **Step 1: 建表 DDL（追加）**

```sql
CREATE TABLE IF NOT EXISTS `WF_TASK_TRANSFER` (
  `id` varchar(32) NOT NULL,
  `process_instance_id` varchar(64) NOT NULL,
  `task_id` varchar(64) NOT NULL,
  `business_key` varchar(100) DEFAULT NULL,
  `biz_type` varchar(50) DEFAULT NULL,
  `node_key` varchar(100) DEFAULT NULL,
  `node_name` varchar(200) DEFAULT NULL,
  `from_emp_id` varchar(32) NOT NULL,
  `initiator_emp_id` varchar(32) NOT NULL,
  `to_emp_id` varchar(32) NOT NULL,
  `org_code` varchar(32) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING_ACCEPT',
  `transfer_reason` varchar(500) NOT NULL,
  `reject_reason` varchar(500) DEFAULT NULL,
  `initiated_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `decided_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_task_status` (`task_id`,`status`),
  KEY `idx_to_status` (`to_emp_id`,`status`),
  KEY `idx_from` (`from_emp_id`),
  KEY `idx_pi` (`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务转交待认领生命周期';
```

- [ ] **Step 2: 实体**（`WfTaskTransfer.java`）— 字段逐一对应上表，`@TableId(value="id", type=IdType.INPUT) String id`，其余 `String`/`LocalDateTime`，`@Data @TableName("WF_TASK_TRANSFER")`。

```java
package com.bank.branch.platform.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("WF_TASK_TRANSFER")
public class WfTaskTransfer {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String processInstanceId;
    private String taskId;
    private String businessKey;
    private String bizType;
    private String nodeKey;
    private String nodeName;
    private String fromEmpId;
    private String initiatorEmpId;
    private String toEmpId;
    private String orgCode;
    private String status;
    private String transferReason;
    private String rejectReason;
    private LocalDateTime initiatedTime;
    private LocalDateTime decidedTime;
}
```

- [ ] **Step 3: Mapper 接口 + XML**

`WfTaskTransferMapper.java`
```java
package com.bank.branch.platform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.workflow.entity.WfTaskTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WfTaskTransferMapper extends BaseMapper<WfTaskTransfer> {

    WfTaskTransfer selectActiveByTaskId(String taskId);

    int updateStatusIfPending(@Param("id") String id,
                              @Param("status") String status,
                              @Param("rejectReason") String rejectReason,
                              @Param("decidedTime") LocalDateTime decidedTime);

    List<WfTaskTransfer> selectInbox(String toEmpId);

    List<WfTaskTransfer> selectOutbox(String fromEmpId);
}
```

`resources/mapper/workflow/WfTaskTransferMapper.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.bank.branch.platform.workflow.mapper.WfTaskTransferMapper">

    <resultMap id="BASE" type="com.bank.branch.platform.workflow.entity.WfTaskTransfer">
        <id column="id" property="id"/>
        <result column="process_instance_id" property="processInstanceId"/>
        <result column="task_id" property="taskId"/>
        <result column="business_key" property="businessKey"/>
        <result column="biz_type" property="bizType"/>
        <result column="node_key" property="nodeKey"/>
        <result column="node_name" property="nodeName"/>
        <result column="from_emp_id" property="fromEmpId"/>
        <result column="initiator_emp_id" property="initiatorEmpId"/>
        <result column="to_emp_id" property="toEmpId"/>
        <result column="org_code" property="orgCode"/>
        <result column="status" property="status"/>
        <result column="transfer_reason" property="transferReason"/>
        <result column="reject_reason" property="rejectReason"/>
        <result column="initiated_time" property="initiatedTime"/>
        <result column="decided_time" property="decidedTime"/>
    </resultMap>

    <select id="selectActiveByTaskId" parameterType="string" resultMap="BASE">
        SELECT * FROM WF_TASK_TRANSFER
        WHERE task_id = #{taskId} AND status = 'PENDING_ACCEPT'
        ORDER BY initiated_time DESC LIMIT 1
    </select>

    <update id="updateStatusIfPending">
        UPDATE WF_TASK_TRANSFER
        SET status = #{status}, reject_reason = #{rejectReason}, decided_time = #{decidedTime}
        WHERE id = #{id} AND status = 'PENDING_ACCEPT'
    </update>

    <select id="selectInbox" parameterType="string" resultMap="BASE">
        SELECT * FROM WF_TASK_TRANSFER
        WHERE to_emp_id = #{toEmpId} AND status = 'PENDING_ACCEPT'
        ORDER BY initiated_time DESC
    </select>

    <select id="selectOutbox" parameterType="string" resultMap="BASE">
        SELECT * FROM WF_TASK_TRANSFER
        WHERE from_emp_id = #{fromEmpId} AND status IN ('PENDING_ACCEPT','ACCEPTED')
        ORDER BY initiated_time DESC
    </select>
</mapper>
```

- [ ] **Step 4: 写失败 Mapper IT**（`WfTaskTransferMapperIT.java`，继承 `WfMapperTestBase` 自动回滚）

```java
package com.bank.branch.platform.workflow.mapper;

import com.bank.branch.platform.workflow.entity.WfTaskTransfer;
import com.bank.branch.platform.workflow.support.WfMapperTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WfTaskTransferMapperIT extends WfMapperTestBase {

    @Autowired private WfTaskTransferMapper mapper;

    private WfTaskTransfer pending(String taskId, String to) {
        WfTaskTransfer t = new WfTaskTransfer();
        t.setId(UUID.randomUUID().toString().replace("-", ""));
        t.setProcessInstanceId("PID_1");
        t.setTaskId(taskId);
        t.setFromEmpId("E_FROM");
        t.setInitiatorEmpId("E_SEC");
        t.setToEmpId(to);
        t.setStatus("PENDING_ACCEPT");
        t.setTransferReason("忙");
        t.setInitiatedTime(LocalDateTime.now());
        return t;
    }

    @Test
    void activeAndOptimisticTransition() {
        WfTaskTransfer t = pending("TASK_1", "E_TO");
        mapper.insert(t);

        assertThat(mapper.selectActiveByTaskId("TASK_1")).isNotNull();
        assertThat(mapper.selectInbox("E_TO")).hasSize(1);

        int first = mapper.updateStatusIfPending(t.getId(), "ACCEPTED", null, LocalDateTime.now());
        int second = mapper.updateStatusIfPending(t.getId(), "REJECTED", "晚了", LocalDateTime.now());
        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0); // 已非 PENDING，乐观流转拦截并发
        assertThat(mapper.selectActiveByTaskId("TASK_1")).isNull();
    }
}
```

- [ ] **Step 5: 运行失败** — `mvn -q verify -pl workflow-center -Dit.test=WfTaskTransferMapperIT`（先 `mvn -q install -pl common,auth-permission-center -DskipTests` 若跨模块类有更新）→ FAIL（表/mapper 未就绪）。先在 yiti 库执行 DDL。

- [ ] **Step 6: 建表并跑通** — 在 yiti 库执行 `ddl-workflow-monitor.sql` 两表；重跑 Step 5 → PASS。

- [ ] **Step 7: 提交**

```bash
git add docs/schema/ddl-workflow-monitor.sql \
  workflow-center/src/main/java/com/bank/branch/platform/workflow/entity/WfTaskTransfer.java \
  workflow-center/src/main/java/com/bank/branch/platform/workflow/mapper/WfTaskTransferMapper.java \
  workflow-center/src/main/resources/mapper/workflow/WfTaskTransferMapper.xml \
  workflow-center/src/test/java/com/bank/branch/platform/workflow/mapper/WfTaskTransferMapperIT.java
git commit -m "feat(workflow): WF_TASK_TRANSFER 转交生命周期表+乐观流转mapper"
```

---

### Task 3: 参与机构写入挂点（发起/分配/办理）+ 存量回填

**Files:**
- Modify: `workflow-center/.../service/ProcessStartService.java`（发起后 record START）
- Modify: `workflow-center/.../service/TaskOperationService.java`（claim/approve 成功后 record）
- Modify: `workflow-center/.../listener/TaskAssignmentListener.java`（解析出具体受理人时 record ASSIGN）
- Modify: `docs/schema/ddl-workflow-monitor.sql`（追加回填 SQL）
- Test: `workflow-center/.../service/ProcessParticipantOrgIT.java`

**Interfaces:**
- Consumes: `WfProcessOrgService.record(pi, empId, source)`（Task 1）。
- Note: `TaskOperationService` 现有构造注入 5 个依赖（taskService/runtimeService/bizProcessMapMapper/eventPublisher/currentUserApi），新增 `WfProcessOrgService wfProcessOrgService` 一个 final 字段（`@RequiredArgsConstructor` 自动入构造器）。

- [ ] **Step 1: 写失败 IT**（`ProcessParticipantOrgIT.java`）：启动一条流程（复用现有测试流程定义/`ProcessStartService`）→ 断言 `WfProcessOrgMapper.selectOrgCodesByPi(pi)` 含发起人机构；claim/approve 后含办理人机构。

```java
// 关键断言（骨架，Autowire ProcessStartService/TaskOperationService/WfProcessOrgMapper + 用 @MockBean OrgApi 固定机构）
@Test
void startAndClaim_recordsParticipantOrgs() {
    Mockito.when(orgApi.getUserMainOrg("E_START")).thenReturn(org("ORG_START"));
    Mockito.when(orgApi.getUserMainOrg("E_HANDLER")).thenReturn(org("ORG_H"));

    String pi = startProcess("E_START");                 // 内部调 ProcessStartService.start(cmd)
    assertThat(wfProcessOrgMapper.selectOrgCodesByPi(pi)).contains("ORG_START");

    Mockito.when(currentUserApi.getCurrentEmpId()).thenReturn("E_HANDLER");
    String taskId = firstTaskId(pi);
    taskOperationService.claimTask(taskId);
    assertThat(wfProcessOrgMapper.selectOrgCodesByPi(pi)).contains("ORG_START", "ORG_H");
}
```

- [ ] **Step 2: 运行失败** — 断言缺 org（未挂点）→ FAIL。

- [ ] **Step 3: 挂 `ProcessStartService`** — 在 `bizProcessMapMapper.insert(map)` 之后（`ProcessStartService.java:115` 附近）：

```java
bizProcessMapMapper.insert(map);
wfProcessOrgService.record(pi.getId(), cmd.getStartUser(), "START");   // ← 新增
```
（构造注入新增 `private final WfProcessOrgService wfProcessOrgService;`）

- [ ] **Step 4: 挂 `TaskOperationService`** — 在 `claimTask` 成功后、`approveTask`→`doApprove` 成功后各加一行（用 `currentUserApi.getCurrentEmpId()` 拿办理人）：

```java
// claimTask 末尾（更新 currentAssignee 之后）
wfProcessOrgService.record(task.getProcessInstanceId(), empId, "CLAIM");
// approveTask 内 doApprove 成功后
wfProcessOrgService.record(task.getProcessInstanceId(), empId, "APPROVE");
```
（构造注入新增 `private final WfProcessOrgService wfProcessOrgService;`；同时更新该类的单测 `@Mock` 增加 `WfProcessOrgService` 以免 `@InjectMocks` NPE — 见 `TaskOperationServiceTest` 补 `@Mock private WfProcessOrgService wfProcessOrgService;`）

- [ ] **Step 5: 挂 `TaskAssignmentListener`（ASSIGN，按 D5 仅有具体受理人时）** — 在 `notify` 里，当 `delegateTask.getAssignee()` 非空（分配到具体人而非仅候选组）时 record：

```java
if (delegateTask.getAssignee() != null) {
    wfProcessOrgService.record(delegateTask.getProcessInstanceId(),
            delegateTask.getAssignee(), "ASSIGN");
}
```
（该 listener 是 Flowable delegate，注入方式沿用其现有 `@Component`/字段注入风格；若为 `@RequiredArgsConstructor` 则加 final 字段。）

- [ ] **Step 6: 运行通过** — 重跑 IT → PASS；并 `mvn -q test -Dtest=TaskOperationServiceTest`（补 mock 后）→ PASS。

- [ ] **Step 7: 追加回填 SQL（写入 ddl 文件，人工执行一次）**

```sql
-- 历史办理人机构
INSERT IGNORE INTO WF_PROCESS_ORG(id, process_instance_id, org_code, source, first_seen_time)
SELECT REPLACE(UUID(),'-',''), t.PROC_INST_ID_, uo.ORG_CODE, 'BACKFILL', NOW()
FROM ACT_HI_TASKINST t JOIN EXT_USER_ORG uo ON uo.USER_ID = t.ASSIGNEE_
WHERE t.ASSIGNEE_ IS NOT NULL
GROUP BY t.PROC_INST_ID_, uo.ORG_CODE;
-- 发起人机构
INSERT IGNORE INTO WF_PROCESS_ORG(id, process_instance_id, org_code, source, first_seen_time)
SELECT REPLACE(UUID(),'-',''), m.process_instance_id, uo.ORG_CODE, 'BACKFILL', NOW()
FROM BIZ_PROCESS_MAP m JOIN EXT_USER_ORG uo ON uo.USER_ID = m.start_user
GROUP BY m.process_instance_id, uo.ORG_CODE;
```

- [ ] **Step 8: 提交** — `git add` 三个改动的 java + ddl + 测试；`git commit -m "feat(workflow): 参与机构写入挂点(发起/分配/办理)+存量回填SQL"`。

---

## Phase P2 — 监控查询 + 前端

### Task 4: `BizType.WORKFLOW_MONITOR` 枚举

**Files:**
- Modify: `common/common-security/src/main/java/com/bank/branch/platform/common/security/enums/BizType.java`
- Test: `common/common-security/src/test/java/com/bank/branch/platform/common/security/enums/BizTypeTest.java`（若无则新建一个最小断言）

- [ ] **Step 1: 失败测试**

```java
@Test
void workflowMonitorExists() {
    assertThat(BizType.valueOf("WORKFLOW_MONITOR").getCode()).isEqualTo("WORKFLOW_MONITOR");
}
```

- [ ] **Step 2: 运行失败** — `mvn -q test -pl common/common-security -Dtest=BizTypeTest` → FAIL。

- [ ] **Step 3: 加枚举项**（在 `EVAL(...)` 后、分号前）

```java
    EVAL("EVAL", "内部评价"),
    WORKFLOW_MONITOR("WORKFLOW_MONITOR", "工作流监控");
```

- [ ] **Step 4: 运行通过** → PASS。`mvn -q install -pl common/common-security -DskipTests`（下游要用新枚举，防 stale jar）。

- [ ] **Step 5: 提交** — `git commit -m "feat(security): 新增 BizType.WORKFLOW_MONITOR"`。

---

### Task 5: 监控查询 Service + Mapper（数据范围 EXISTS）

**Files:**
- Modify: `workflow-center/.../mapper/BizProcessMapMapper.java` + `resources/mapper/workflow/BizProcessMapMapper.xml`
- Create: `workflow-center/.../api/dto/ProcessMonitorItemDTO.java`
- Create: `workflow-center/.../service/ProcessMonitorService.java`
- Test: `workflow-center/.../service/ProcessMonitorServiceTest.java`

**Interfaces:**
- Produces:
  - `ProcessMonitorService.query(String status, String bizType, String keyword, String startedBy, int pageNo, int pageSize)` → `PageResult<ProcessMonitorItemDTO>`（内部读 `DataScopeContext.current()` 决定机构过滤）
  - `BizProcessMapMapper.selectMonitorPage(...)` / `countMonitor(...)`（见下）
- Consumes: `CurrentUserApi.isSystemAdmin()`, `OrgApi.getOrgsByCodes(...)`（批量补当前处理人机构），`DataScopeContext.current()`（common-security，字段 scope/orgCode/orgSubtreeCodes）。

- [ ] **Step 1: Mapper 新方法 + XML（带 WF_PROCESS_ORG EXISTS）**

`BizProcessMapMapper.java` 追加：
```java
java.util.List<BizProcessMap> selectMonitorPage(@Param("status") String status,
                                                @Param("bizType") String bizType,
                                                @Param("keyword") String keyword,
                                                @Param("startedBy") String startedBy,
                                                @Param("orgScope") java.util.Collection<String> orgScope,
                                                @Param("offset") int offset,
                                                @Param("size") int size);

long countMonitor(@Param("status") String status,
                  @Param("bizType") String bizType,
                  @Param("keyword") String keyword,
                  @Param("startedBy") String startedBy,
                  @Param("orgScope") java.util.Collection<String> orgScope);
```

`BizProcessMapMapper.xml` 追加（`orgScope==null` 表示全行，不加机构过滤；非 null 走 EXISTS）：
```xml
<sql id="MONITOR_WHERE">
    <where>
        <if test="status != null and status != ''"> AND m.process_status = #{status} </if>
        <if test="bizType != null and bizType != ''"> AND m.biz_type = #{bizType} </if>
        <if test="startedBy != null and startedBy != ''"> AND m.start_user = #{startedBy} </if>
        <if test="keyword != null and keyword != ''"> AND m.title LIKE CONCAT('%', #{keyword}, '%') </if>
        <if test="orgScope != null">
            AND EXISTS (SELECT 1 FROM WF_PROCESS_ORG wpo
                        WHERE wpo.process_instance_id = m.process_instance_id
                          AND wpo.org_code IN
                        <foreach collection="orgScope" item="oc" open="(" separator="," close=")">#{oc}</foreach>)
        </if>
    </where>
</sql>

<select id="selectMonitorPage" resultType="com.bank.branch.platform.workflow.entity.BizProcessMap">
    SELECT <include refid="BASE_COLUMNS"/> FROM BIZ_PROCESS_MAP m
    <include refid="MONITOR_WHERE"/>
    ORDER BY m.start_time DESC LIMIT #{offset}, #{size}
</select>

<select id="countMonitor" resultType="long">
    SELECT COUNT(*) FROM BIZ_PROCESS_MAP m <include refid="MONITOR_WHERE"/>
</select>
```
（`BASE_COLUMNS` 复用现有 sql fragment。约定：`orgScope==null` 才是"全行"不过滤；**空集合永远不会传到这里**——Service 在空集时已短路返回空结果，避免 `IN ()` MySQL 语法错。）

- [ ] **Step 2: DTO**（`ProcessMonitorItemDTO.java`）

```java
package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProcessMonitorItemDTO {
    private String processInstanceId;
    private String businessKey;
    private String bizType;
    private String title;
    private String processStatus;      // RUNNING / COMPLETED / CANCELLED
    private String startUser;
    private String startUserOrgCode;
    private String currentAssignee;
    private String currentAssigneeOrgCode;   // 批量补
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
```

- [ ] **Step 3: 失败测试**（`ProcessMonitorServiceTest.java`，Mockito）

```java
@ExtendWith(MockitoExtension.class)
class ProcessMonitorServiceTest {
    @Mock BizProcessMapMapper bizProcessMapMapper;
    @Mock CurrentUserApi currentUserApi;
    @Mock OrgApi orgApi;
    @InjectMocks ProcessMonitorService service;

    @Test
    void admin_noOrgFilter() {
        when(currentUserApi.isSystemAdmin()).thenReturn(true);
        when(bizProcessMapMapper.countMonitor(any(),any(),any(),any(),isNull())).thenReturn(0L);
        service.query("RUNNING", null, null, null, 1, 20);
        verify(bizProcessMapMapper).selectMonitorPage(any(),any(),any(),any(),isNull(),eq(0),eq(20));
    }

    @Test
    void secretary_orgScopeApplied() {
        // DataScopeContext.set(...) 造 ORG 范围；断言 selectMonitorPage 收到含 orgCode 的集合
        DataScopeContext ctx = new DataScopeContext();
        ctx.setBizType(BizType.WORKFLOW_MONITOR);
        ctx.setScope(DataScopeType.ORG);
        ctx.setOrgCode("ORG_A");
        DataScopeContext.set(ctx);
        try {
            when(currentUserApi.isSystemAdmin()).thenReturn(false);
            when(bizProcessMapMapper.countMonitor(any(),any(),any(),any(),argThat(c -> c.contains("ORG_A")))).thenReturn(0L);
            service.query("RUNNING", null, null, null, 1, 20);
            verify(bizProcessMapMapper).selectMonitorPage(any(),any(),any(),any(),
                    argThat(c -> c.contains("ORG_A")), eq(0), eq(20));
        } finally { DataScopeContext.clear(); }
    }
}
```

- [ ] **Step 4: 运行失败** → FAIL（Service 不存在）。

- [ ] **Step 5: 实现 Service**

```java
package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.workflow.api.dto.ProcessMonitorItemDTO;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessMonitorService {

    private final BizProcessMapMapper bizProcessMapMapper;
    private final CurrentUserApi currentUserApi;
    private final OrgApi orgApi;

    public PageResult<ProcessMonitorItemDTO> query(String status, String bizType, String keyword,
                                                   String startedBy, int pageNo, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int offset = (Math.max(pageNo, 1) - 1) * size;

        Collection<String> orgScope = resolveOrgScope();  // null=全行；空集=Fail-Close(看不到)
        // 空集(非 null)直接返回空结果，绝不进 SQL —— 否则 XML 的 IN () 是 MySQL 语法错
        if (orgScope != null && orgScope.isEmpty()) {
            return PageResult.of(pageNo, size, 0, List.of());
        }
        long total = bizProcessMapMapper.countMonitor(status, bizType, keyword, startedBy, orgScope);
        List<BizProcessMap> rows = total == 0 ? List.of()
                : bizProcessMapMapper.selectMonitorPage(status, bizType, keyword, startedBy, orgScope, offset, size);

        List<ProcessMonitorItemDTO> dtos = toDtos(rows);
        return PageResult.of(pageNo, size, total, dtos);
    }

    /** null=不加机构过滤（ALL/admin）；否则返回机构编码集合（ORG=单值，ORG_SUBTREE=子树）。 */
    private Collection<String> resolveOrgScope() {
        if (currentUserApi.isSystemAdmin()) return null;
        DataScopeContext ctx = DataScopeContext.current();
        if (ctx == null || ctx.getScope() == null) return Set.of();  // Fail-Close
        switch (ctx.getScope()) {
            case ALL: return null;
            case ORG: return ctx.getOrgCode() == null ? Set.of() : Set.of(ctx.getOrgCode());
            case ORG_SUBTREE: return ctx.getOrgSubtreeCodes() == null ? Set.of() : ctx.getOrgSubtreeCodes();
            default: return Set.of();
        }
    }

    private List<ProcessMonitorItemDTO> toDtos(List<BizProcessMap> rows) {
        // 批量补机构名/编码，避免 N+1
        Set<String> emps = new HashSet<>();
        rows.forEach(r -> { if (r.getStartUser()!=null) emps.add(r.getStartUser());
                            if (r.getCurrentAssignee()!=null) emps.add(r.getCurrentAssignee()); });
        Map<String,String> empToOrg = new HashMap<>();
        for (String e : emps) { OrgDTO o = orgApi.getUserMainOrg(e); if (o!=null) empToOrg.put(e, o.getOrgCode()); }

        return rows.stream().map(r -> {
            ProcessMonitorItemDTO d = new ProcessMonitorItemDTO();
            d.setProcessInstanceId(r.getProcessInstanceId());
            d.setBusinessKey(r.getBusinessKey());
            d.setBizType(r.getBizType());
            d.setTitle(r.getTitle());
            d.setProcessStatus(r.getProcessStatus());
            d.setStartUser(r.getStartUser());
            d.setStartUserOrgCode(empToOrg.get(r.getStartUser()));
            d.setCurrentAssignee(r.getCurrentAssignee());
            d.setCurrentAssigneeOrgCode(empToOrg.get(r.getCurrentAssignee()));
            d.setStartTime(r.getStartTime());
            d.setEndTime(r.getEndTime());
            return d;
        }).collect(Collectors.toList());
    }
}
```
（注：`empToOrg` 的 `getUserMainOrg` 循环，若量大可后续换 `OrgApi.getOrgsByCodes` 批量；当前单页≤100，OrgApi 结果有缓存，可接受。）

- [ ] **Step 6: 运行通过** → PASS。

- [ ] **Step 7: 提交** — `git commit -m "feat(workflow): 审批流监控查询Service+数据范围EXISTS过滤"`。

---

### Task 6: 监控 Controller + PT_RESOURCE 登记

**Files:**
- Create: `workflow-center/.../controller/ProcessMonitorController.java`
- Modify: seed SQL（`docs/schema/seed-v1.sql` 追加，并同步 `data.sql` 批量段）
- Test: `workflow-center/.../controller/ProcessMonitorControllerTest.java`（MockMvc 或直接调 service 的瘦测试）

**Interfaces:**
- Produces HTTP：`GET /api/workflow/monitor/processes?status=&bizType=&keyword=&startedBy=&pageNo=&pageSize=` → `ResponseWrapper`(page)。

- [ ] **Step 1: 失败测试** — 断言 controller 调 `processMonitorService.query(...)` 并返回 `ResponseWrapper.page`（用 `@ExtendWith(MockitoExtension)` mock service，直接调方法断言返回）。

```java
@ExtendWith(MockitoExtension.class)
class ProcessMonitorControllerTest {
    @Mock ProcessMonitorService service;
    @InjectMocks ProcessMonitorController controller;

    @Test
    void listDelegatesToService() {
        when(service.query("RUNNING", null, null, null, 1, 20))
            .thenReturn(PageResult.of(1,20,0,java.util.List.of()));
        var resp = controller.list("RUNNING", null, null, null, 1, 20);
        assertThat(resp).isNotNull();
        verify(service).query("RUNNING", null, null, null, 1, 20);
    }
}
```

- [ ] **Step 2: 运行失败** → FAIL。

- [ ] **Step 3: 实现 Controller**（参照 `WorkflowAdminController` 加 `@BizAuth`）

```java
package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.workflow.api.dto.ProcessMonitorItemDTO;
import com.bank.branch.platform.workflow.service.ProcessMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflow/monitor")
@Tag(name = "审批流监控", description = "秘书岗/行长按机构监控进行中与已完成审批流")
public class ProcessMonitorController {

    private final ProcessMonitorService processMonitorService;

    @GetMapping("/processes")
    @Operation(summary = "审批流监控列表")
    @BizAuth(bizType = BizType.WORKFLOW_MONITOR, action = BizAction.LIST)
    public ResponseWrapper<PageResult<ProcessMonitorItemDTO>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "startedBy", required = false) String startedBy,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[ProcessMonitorController.list] status={}, bizType={}", status, bizType);
        return ResponseWrapper.page(processMonitorService.query(status, bizType, keyword, startedBy, pageNo, pageSize));
    }
}
```

- [ ] **Step 4: 运行通过** → PASS。

- [ ] **Step 5: PT_RESOURCE 登记 + 数据范围种子**（seed SQL；`RESOURCE_ID`/`ROLE_ID` 按目标环境实际值）

```sql
INSERT IGNORE INTO PT_RESOURCE (`RESOURCE_ID`,`RESOURCE_URL`,`RESOURCE_METHOD`,`MENU_NAME`,`MENU_ICON_URL`,`MENU_RANK_NO`,`ISMENU`,`MENU_ENDFLAG`,`PARENT_RESOURCE_ID`,`STATUS`,`SYS_CODE`,`CREATE_TIME`,`CREATE_USER`,`UPDATE_TIME`,`UPDATE_USER`,`REMARK`)
VALUES ('RES_WF_MONITOR_LIST','/api/workflow/monitor/processes','GET','审批流监控列表',NULL,0,0,'0',NULL,0,'PLATFORM',NOW(),NULL,NOW(),NULL,NULL);
-- 数据范围：秘书岗=ORG，行长=ALL（ROLE_ID 用目标库真实值）
INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ROLE_ID, BIZ_TYPE, DATA_SCOPE, REMARK)
VALUES ('<ROLE_ID_秘书>','WORKFLOW_MONITOR','ORG','秘书岗-本机构'),
       ('<ROLE_ID_行长>','WORKFLOW_MONITOR','ALL','分行行长-全行');
```
（`PT_ROLE_BIZ_SCOPE` 列名以 `PtRoleBizScope` 实体为准；若列名不同按实体调整。也可改为由管理员在权限配置页手工配，则本步仅登记 PT_RESOURCE。）

- [ ] **Step 6: 提交** — `git commit -m "feat(workflow): 监控接口Controller+PT_RESOURCE与数据范围种子"`。

---

### Task 7: 前端监控页 + 路由 + 菜单 + api

**Files:**
- Modify: `xanzc_frontend/src/api/workflow.js`（追加 `monitorProcesses`）
- Create: `xanzc_frontend/src/views/system/WorkflowMonitor.vue`
- Modify: `xanzc_frontend/src/router/index.js`（加路由）
- Modify: seed（侧边栏 `M_` 菜单行 + 角色绑定，落 yiti 库）

**Interfaces:**
- Consumes: `GET /api/workflow/monitor/processes`（Task 6）；查看详情复用现有 `GET /api/workflow/processes/{id}` `/history` `/nodes` `/diagram`。

- [ ] **Step 1: api 封装**（`workflow.js` 追加）

```javascript
/** 审批流监控列表（分页） */
export function monitorProcesses(params = {}) {
  return call('get', '/workflow/monitor/processes', { params: { pageSize: 20, ...params } }, []).then(unwrapPage);
}
```

- [ ] **Step 2: 监控页**（`WorkflowMonitor.vue`）— 进行中/已完成 Tab（`status=RUNNING|COMPLETED`）、过滤（bizType/keyword/startedBy）、表格列（流程·标题 / 业务类型 / 当前节点或处理人+机构 / 发起人+机构 / 发起时间 / 状态 / 操作[查看·转交]）、查看抽屉复用流程图+历史+节点。参照现有 `views/perf/TaskMonitor.vue` 的表格/抽屉/分页写法（同一 Element Plus 风格）。转交按钮打开 Task 12 的转交弹窗组件。

- [ ] **Step 3: 路由**（`router/index.js`，系统设置组内）

```javascript
{ path: 'system/workflow-monitor', name: 'SysWorkflowMonitor', component: () => import('@/views/system/WorkflowMonitor.vue'), meta: { title: '审批流监控', group: '系统设置' } },
```

- [ ] **Step 4: 菜单种子** — 插 `M_审批流监控` 资源行（`ISMENU=1`）+ 绑定秘书/行长角色（记：侧边栏 DB 驱动，仅加路由不显示菜单）。

- [ ] **Step 5: 手测** — 秘书账号只见本机构流程；行长/admin 见全部；查看抽屉正常。`git commit -m "feat(fe): 审批流监控页+路由+菜单+api"`。

---

## Phase P3 — 两阶段转交

### Task 8: 转交发起 `TaskTransferService.initiate` + 接收人校验

**Files:**
- Create: `workflow-center/.../api/dto/TransferInitiateReqDTO.java`（`@NotBlank toEmpId`, `@NotBlank @Size(max=500) reason`）
- Create: `workflow-center/.../service/TaskTransferService.java`
- Test: `workflow-center/.../service/TaskTransferServiceTest.java`

**Interfaces:**
- Produces: `TaskTransferService.initiate(String taskId, TransferInitiateReqDTO req)` → `String transferId`；`boolean hasPendingTransfer(String taskId)`。
- Consumes: `TaskService`（Flowable，查任务/节点）, `CandidateResolverService.resolveCandidates(pdk,nodeKey)`（校验接收人∈节点候选）, `OrgApi.getUserMainOrg`（校验接收人∈本机构 = 与发起人同机构）, `CurrentUserApi.getCurrentEmpId/getCurrentOrgCode`, `WfTaskTransferMapper`, `BizProcessMapMapper`。

- [ ] **Step 1: 失败测试**（覆盖：正常发起写 PENDING_ACCEPT；接收人不在本机构→拒；接收人非该节点可办理者→拒；已有 PENDING_ACCEPT→拒）

```java
@Test
void initiate_createsPendingAndValidatesReceiver() {
    // mock 任务存在于节点 nodeKey；候选含 E_TO；E_TO 机构=发起人机构 ORG_A
    // 断言 mapper.insert 收到 status=PENDING_ACCEPT, toEmpId=E_TO, fromEmpId=当前assignee
}
@Test
void initiate_rejectsReceiverOutsideOrg() { assertThatThrownBy(...).isInstanceOf(BizException.class); }
@Test
void initiate_rejectsWhenAlreadyPending() { /* selectActiveByTaskId 非空 → 抛 */ }
```

- [ ] **Step 2: 运行失败** → FAIL。

- [ ] **Step 3: 实现 initiate**（核心校验链）

```java
public String initiate(String taskId, TransferInitiateReqDTO req) {
    String initiator = currentUserApi.getCurrentEmpId();
    String initiatorOrg = currentUserApi.getCurrentOrgCode();
    Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
    if (task == null) throw new BizException("WF-40401", "任务不存在");

    // 单活校验
    if (wfTaskTransferMapper.selectActiveByTaskId(taskId) != null)
        throw new BizException("WF-40910", "该任务已有待认领的转交");

    // 接收人 ∈ 本机构（1:1 → 主机构等于发起人机构）
    OrgDTO toOrg = orgApi.getUserMainOrg(req.getToEmpId());
    if (toOrg == null || !java.util.Objects.equals(initiatorOrg, toOrg.getOrgCode()))
        throw new BizException("WF-40911", "接收人不在本机构");

    // 接收人 ∈ 该节点可办理者（候选，去掉 ROLE:/ORG:/USER: 前缀后应含 toEmpId，或按候选解析结果判断）
    String pdk = task.getProcessDefinitionId();
    java.util.List<String> candidates = candidateResolverService.resolveCandidates(pdKey(pdk), task.getTaskDefinitionKey());
    if (!candidateContains(candidates, req.getToEmpId()))
        throw new BizException("WF-40912", "接收人无该节点办理资格");

    WfTaskTransfer t = new WfTaskTransfer();
    t.setId(UUID.randomUUID().toString().replace("-",""));
    t.setProcessInstanceId(task.getProcessInstanceId());
    t.setTaskId(taskId);
    t.setNodeKey(task.getTaskDefinitionKey());
    t.setNodeName(task.getName());
    t.setFromEmpId(task.getAssignee());
    t.setInitiatorEmpId(initiator);
    t.setToEmpId(req.getToEmpId());
    t.setOrgCode(initiatorOrg);
    t.setStatus("PENDING_ACCEPT");
    t.setTransferReason(req.getReason());
    t.setInitiatedTime(LocalDateTime.now());
    // 补 businessKey/bizType（可从 bizProcessMapMapper.selectByProcessInstanceId 取）
    BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(task.getProcessInstanceId());
    if (map != null) { t.setBusinessKey(map.getBusinessKey()); t.setBizType(map.getBizType()); }
    wfTaskTransferMapper.insert(t);
    taskService.addComment(taskId, task.getProcessInstanceId(), "TRANSFER_INITIATED", req.getReason());
    return t.getId();
}

public boolean hasPendingTransfer(String taskId) {
    return wfTaskTransferMapper.selectActiveByTaskId(taskId) != null;
}
```
（`candidateContains` 处理候选前缀；`pdKey` 由 `repositoryService.getProcessDefinition(pdId).getKey()` 或注入 `CandidateResolverService` 已封装的解析，与 `TaskAssignmentListener` 同法。若发起人是秘书而非 assignee 本人，`fromEmpId` 取 `task.getAssignee()`——即被转出者。）

- [ ] **Step 4: 运行通过** → PASS。

- [ ] **Step 5: 提交** — `git commit -m "feat(workflow): 两阶段转交发起+接收人机构/节点资格校验"`。

---

### Task 9: 转交锁卫语句（approve/reject/claim）+ RuntimeAccess

**Files:**
- Modify: `workflow-center/.../service/TaskOperationService.java`（approve/reject/claim 首部加卫语句）
- Modify: `workflow-center/.../service/TodoQueryService.java:269-277`（canApprove/canReject/canClaim 结合锁）
- Test: `workflow-center/.../service/TaskOperationServiceTest.java`（补锁定用例）

**Interfaces:**
- Consumes: `TaskTransferService.hasPendingTransfer(String taskId)`（Task 8）。`TaskOperationService` 与 `TaskTransferService` 互相依赖需避免循环——把 `hasPendingTransfer` 下沉为直接注入 `WfTaskTransferMapper.selectActiveByTaskId` 到 `TaskOperationService`（不注入 `TaskTransferService`）。

- [ ] **Step 1: 失败测试**

```java
@Test
void approve_blockedWhenPendingTransfer() {
    when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    Task t = buildMockTask("TASK_1","PID_1","E001"); mockTaskQuery(t);
    when(wfTaskTransferMapper.selectActiveByTaskId("TASK_1")).thenReturn(new WfTaskTransfer());
    assertThatThrownBy(() -> taskOperationService.approveTask("TASK_1", new ApproveReqDTO()))
        .isInstanceOf(BizException.class);
    verify(taskService, never()).complete(anyString(), anyMap());
}
```

- [ ] **Step 2: 运行失败** → FAIL。

- [ ] **Step 3: 加卫语句**（`approveTask`/`rejectTask`/`claimTask` 在 `verifyAssignee` 前）

```java
private void ensureNotTransferLocked(String taskId) {
    if (wfTaskTransferMapper.selectActiveByTaskId(taskId) != null) {
        throw new BizException("WF-40913", "任务转交待认领中，不可办理");
    }
}
```
在三个方法起始处调用 `ensureNotTransferLocked(taskId);`。构造注入新增 `private final WfTaskTransferMapper wfTaskTransferMapper;`（单测补 `@Mock`）。

- [ ] **Step 4: RuntimeAccess**（`TodoQueryService.java:269-277`）：

```java
boolean locked = wfTaskTransferMapper.selectActiveByTaskId(task.getId()) != null;
runtimeAccess.setCanApprove(isAssignee && !locked);
runtimeAccess.setCanReject(isAssignee && !locked);
runtimeAccess.setCanClaim(!isAssignee && task.getAssignee() == null && !locked);
runtimeAccess.setCanTransfer(isAssignee && !locked);
```
（`TodoQueryService` 注入 `WfTaskTransferMapper`。）

- [ ] **Step 5: 运行通过** → PASS。

- [ ] **Step 6: 提交** — `git commit -m "feat(workflow): 转交锁-待认领期间原办理人只读"`。

---

### Task 10: 认领 accept（接管 + 记机构 + 审计 + 通知）

**Files:**
- Modify: `workflow-center/.../service/TaskTransferService.java`（accept）
- Test: `TaskTransferServiceTest.java`（accept 用例）

**Interfaces:**
- Produces: `TaskTransferService.accept(String transferId)`（`@Transactional`）。
- Consumes: `WfProcessOrgService.record(pi, toEmp, "TRANSFER")`, `NotifyApi.sendNotification(...)`, `bizProcessMapMapper`（改 currentAssignee）。

- [ ] **Step 1: 失败测试** — accept 成功：`updateStatusIfPending` 返回 1 → `taskService.setAssignee(taskId,toEmp)` 被调、`bizProcessMap.currentAssignee=toEmp`、`WfProcessOrgService.record(...,"TRANSFER")` 被调；`updateStatusIfPending` 返回 0（并发）→ 抛冲突且不 setAssignee。

- [ ] **Step 2: 运行失败** → FAIL。

- [ ] **Step 3: 实现 accept**

```java
@Transactional
public void accept(String transferId) {
    String me = currentUserApi.getCurrentEmpId();
    WfTaskTransfer t = wfTaskTransferMapper.selectById(transferId);
    if (t == null || !"PENDING_ACCEPT".equals(t.getStatus())) throw new BizException("WF-40914","转交不存在或已处理");
    if (!me.equals(t.getToEmpId())) throw new BizException("WF-40303","只有接收人可认领");

    int n = wfTaskTransferMapper.updateStatusIfPending(transferId, "ACCEPTED", null, LocalDateTime.now());
    if (n == 0) throw new BizException("WF-40915","转交状态已变更");

    taskService.setAssignee(t.getTaskId(), me);
    // 改 biz_process_map.current_assignee
    BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(t.getProcessInstanceId());
    if (map != null) { map.setCurrentAssignee(me); bizProcessMapMapper.updateById(map); }
    wfProcessOrgService.record(t.getProcessInstanceId(), me, "TRANSFER");
    taskService.addComment(t.getTaskId(), t.getProcessInstanceId(), "TRANSFER_ACCEPTED", null);

    notifyApi.sendNotification(NotificationCmd.builder()
            .targetEmpId(t.getInitiatorEmpId()).title("转交已被认领")
            .content("任务["+t.getNodeName()+"]已被"+me+"认领")
            .notifyType("WORKFLOW").bizType(t.getBizType()).bizId(t.getBusinessKey()).build());
}
```
（controller 层加 `@AuditLog(action="TRANSFER_ACCEPT", resourceType="WORKFLOW_TASK")`。）

- [ ] **Step 4: 运行通过** → PASS。 **Step 5: 提交** `git commit -m "feat(workflow): 转交认领-接管+记机构+通知"`。

---

### Task 11: 拒绝 decline（理由必填 + 解锁 + 通知）+ 撤回 cancel

**Files:**
- Create: `workflow-center/.../api/dto/TransferDecisionReqDTO.java`（`@NotBlank @Size(max=500) reason`）
- Modify: `TaskTransferService.java`（decline/cancel）
- Test: `TaskTransferServiceTest.java`

**Interfaces:**
- Produces: `TaskTransferService.decline(String transferId, String reason)`；`TaskTransferService.cancel(String transferId)`。

- [ ] **Step 1: 失败测试** — decline：`updateStatusIfPending(id,"REJECTED",reason,...)` 返回 1；reason 空 → Controller `@Valid` 拦（服务层再兜底非空校验）；decline 后原办理人锁解除（selectActiveByTaskId 为 null）；cancel 仅 initiator 可撤回。

- [ ] **Step 2: 运行失败** → FAIL。

- [ ] **Step 3: 实现**

```java
@Transactional
public void decline(String transferId, String reason) {
    String me = currentUserApi.getCurrentEmpId();
    WfTaskTransfer t = requirePending(transferId);
    if (!me.equals(t.getToEmpId())) throw new BizException("WF-40303","只有接收人可拒绝");
    if (reason == null || reason.isBlank()) throw new BizException("WF-40006","拒绝理由必填");
    int n = wfTaskTransferMapper.updateStatusIfPending(transferId, "REJECTED", reason, LocalDateTime.now());
    if (n == 0) throw new BizException("WF-40915","转交状态已变更");
    taskService.addComment(t.getTaskId(), t.getProcessInstanceId(), "TRANSFER_REJECTED", reason);
    notifyInitiatorAndFrom(t, "转交被拒绝", "理由："+reason);
}

@Transactional
public void cancel(String transferId) {
    String me = currentUserApi.getCurrentEmpId();
    WfTaskTransfer t = requirePending(transferId);
    if (!me.equals(t.getInitiatorEmpId())) throw new BizException("WF-40303","只有发起人可撤回");
    int n = wfTaskTransferMapper.updateStatusIfPending(transferId, "CANCELLED", null, LocalDateTime.now());
    if (n == 0) throw new BizException("WF-40915","转交状态已变更");
    notifyApi.sendNotification(NotificationCmd.builder().targetEmpId(t.getToEmpId())
            .title("转交已撤回").content("["+t.getNodeName()+"]转交被发起人撤回")
            .notifyType("WORKFLOW").bizType(t.getBizType()).bizId(t.getBusinessKey()).build());
}
```
（`requirePending` 查 + 状态校验；解锁天然发生——`selectActiveByTaskId` 只认 PENDING_ACCEPT。）

- [ ] **Step 4: 运行通过** → PASS。 **Step 5: 提交** `git commit -m "feat(workflow): 转交拒绝(理由必填)与发起人撤回"`。

---

### Task 12: 转交 Controller（发起/收件箱/认领/拒绝/发件箱/撤回）+ PT_RESOURCE

**Files:**
- Create: `workflow-center/.../controller/TaskTransferController.java`
- Create: `workflow-center/.../api/dto/TransferItemDTO.java`（inbox/outbox 展示）
- Modify: `TaskTransferService.java`（`listInbox`/`listOutbox` 转 DTO）
- Modify: seed（6 个端点 PT_RESOURCE 行）
- Test: `TaskTransferControllerTest.java`

**Interfaces:**
- Produces HTTP：
  - `POST /api/workflow/monitor/tasks/{taskId}/transfer` {toEmpId,reason} → transferId
  - `GET  /api/workflow/transfers/inbox` → List<TransferItemDTO>
  - `POST /api/workflow/transfers/{id}/accept`
  - `POST /api/workflow/transfers/{id}/decline` {reason}
  - `GET  /api/workflow/transfers/outbox`
  - `POST /api/workflow/transfers/{id}/cancel`

- [ ] **Step 1: 失败测试** — Mockito mock service，逐端点断言委派 + 返回 `ResponseWrapper.success`。

- [ ] **Step 2: 运行失败** → FAIL。

- [ ] **Step 3: 实现 Controller**（发起端点加 `@BizAuth(WORKFLOW_MONITOR, TRANSFER)` + `@AuditLog(action="TRANSFER", resourceType="WORKFLOW_TASK", reasonRequired=true)`；accept/decline/cancel 加 `@AuditLog`；inbox/outbox 只读、登录即可，仍需 PT_RESOURCE 行）。

```java
@PostMapping("/monitor/tasks/{taskId}/transfer")
@BizAuth(bizType = BizType.WORKFLOW_MONITOR, action = BizAction.TRANSFER)
@AuditLog(action = "TRANSFER", resourceType = "WORKFLOW_TASK", reasonRequired = true)
public ResponseWrapper<String> initiate(@PathVariable String taskId,
        @Valid @RequestBody TransferInitiateReqDTO req) {
    return ResponseWrapper.success(taskTransferService.initiate(taskId, req));
}
// inbox/accept/decline/outbox/cancel 依次
```

- [ ] **Step 4: 运行通过** → PASS。

- [ ] **Step 5: PT_RESOURCE 6 行** — 按 Task 6 格式，url/method 对应上表（`ISMENU=0`）。

- [ ] **Step 6: 提交** — `git commit -m "feat(workflow): 转交Controller六端点+审计+PT_RESOURCE"`。

---

### Task 13: 前端 — 转交弹窗 + 工作台待认领/我转出的 + api

**Files:**
- Modify: `xanzc_frontend/src/api/workflow.js`（transferInitiate/transferInbox/transferAccept/transferDecline/transferOutbox/transferCancel + 接收人候选查询）
- Create: `xanzc_frontend/src/components/TransferDialog.vue`（接收人选择器仅本机构可办理该节点者 + 理由）
- Modify: `xanzc_frontend/src/views/system/WorkflowMonitor.vue`（接"转交"按钮）
- Modify: `xanzc_frontend/src/views/workspace/Index.vue`（"待认领转交" inbox：认领/拒绝(填理由)；"我转出的(只读)" outbox）

**Interfaces:**
- Consumes: Task 12 端点。接收人候选：若无专用端点，弹窗用"本机构人员"接口（`OrgController /api/orgs`）+ 后端 initiate 二次校验兜底。

- [ ] **Step 1: api 封装**（`workflow.js` 追加 6 个函数，POST 用 `call('post', ...)`，GET 列表用 `.then(r=>r||[])`）。

- [ ] **Step 2: 转交弹窗组件** — 表单：接收人（下拉，本机构人员）+ 理由（textarea，必填）；提交调 `transferInitiate(taskId, {toEmpId, reason})`；成功 toast + 刷新列表。

- [ ] **Step 3: 工作台 inbox/outbox** — "我的任务"下加 "待认领转交" 区（`transferInbox()`，行内 认领→`transferAccept(id)` / 拒绝→弹窗填理由→`transferDecline(id,{reason})`）；"我转出的(只读)"区（`transferOutbox()`，仅"查看"复用监控详情）。

- [ ] **Step 4: 手测端到端** — 秘书发起→接收人工作台待认领→认领生效(原办理人不可办、可只读见)→或拒绝(填理由、原办理人恢复)。

- [ ] **Step 5: 提交** — `git commit -m "feat(fe): 转交弹窗+工作台待认领/我转出的+api"`。

---

## Phase P4 — 收尾

### Task 14: 下线旧单阶段转交

**Files:**
- Modify: `workflow-center/.../controller/TaskController.java`（删除 `transferTask` 端点，约 :163-177）
- Modify: `workflow-center/.../service/TaskOperationService.java`（删除旧 `transferTask` :213-233 及 `TaskTransferredEvent`，若无其它引用）
- Modify: 相关单测（`TaskOperationServiceTest` 删旧 transfer 断言）
- Modify: seed（若旧端点有 PT_RESOURCE 行则移除或保留失效）

- [ ] **Step 1: 确认无调用方** — `grep -rn "tasks/.*/transfer\|transferTask\|TaskTransferredEvent" workflow-center xanzc_frontend front`，确认仅测试引用（前端本就未接旧端点）。

- [ ] **Step 2: 删除旧端点与旧 service 方法 + 事件**，跑 `mvn -q test -pl workflow-center` 保持绿。

- [ ] **Step 3: 提交** — `git commit -m "refactor(workflow): 下线旧单阶段转交，统一走两阶段"`。

---

### Task 15: 全量回归 + 端到端验证

- [ ] **Step 1: 全量构建** — `mvn clean install -DskipTests` 再 `mvn verify`（防 stale jar；确保 workflow-center + bootstrap IT 绿）。

- [ ] **Step 2: 数据范围验证** — 秘书账号(ORG) 只见本机构参与流程；行长(ALL)/admin 见全部（用 §11 合理性验证清单逐条核对）。

- [ ] **Step 3: 转交全链路** — 发起即锁→认领生效→原办理人只读→拒绝解锁；`audit_log` 有 TRANSFER/ACCEPT/REJECT 记录。

- [ ] **Step 4: 收尾提交/合并** — 依 `superpowers:finishing-a-development-branch` 决定 PR/合并。

---

## Self-Review（对照 spec 覆盖）

- 需求 1/2（秘书 ORG、行长/admin ALL 监控进行中/已完成）→ Task 4/5/6/7 ✓
- 需求 3（查看详情/流程图）→ Task 7 复用现有 Process 接口 ✓
- 需求 4（秘书发起转交、指定接收人）→ Task 8/12/13 ✓
- 需求 5（接收人待处理工作台看到）→ inbox（Task 12/13）✓
- 需求 6（认领后生效、认领后原办理人不可办）→ Task 9/10 ✓
- 需求 7（拒绝填理由）→ Task 11 ✓
- 需求 8（原办理人只读可见）→ outbox（Task 12/13）+ 锁（Task 9）✓
- 参与机构快照 + 回填（D1/D5）→ Task 1/3 ✓；数据范围 EXISTS（D2）→ Task 5 ✓；发起即锁（D3）→ Task 9 ✓；接收人范围（D4）→ Task 8 ✓；1:1 机构（D6）→ Task 8 校验用主机构相等 ✓；审计 → Task 12 ✓；旧转交下线 → Task 14 ✓。

**Placeholder / 类型一致性**：状态常量统一 `PENDING_ACCEPT/ACCEPTED/REJECTED/CANCELLED`；`WfTaskTransferMapper.updateStatusIfPending` 签名跨 Task 2/10/11 一致；`WfProcessOrgService.record(pi,empId,source)` 跨 Task 1/3/10 一致；`ProcessMonitorService.query(...)` 与 Controller 参数一致。`<ROLE_ID_秘书>`/`<RESOURCE_ID>` 为环境相关值，已在步骤内说明按目标库真实值填。
