# 系统治理中心运维 Runbook

> 本文面向当前部署的日常核对、健康检查、任务暂停/恢复、故障定位和安全恢复。
> 配置以活动 profile 与 `bootstrap/src/main/resources/application*.yml` 为准，
> 行为以当前源码和 [03-接口设计与报文.md](03-接口设计与报文.md) 为准。
> 命令中的地址、数据库名和凭据均使用现场受控变量，不在文档中固化。

## 1. 操作边界

- system-governance-center 负责字典、配置、工作日历、通知、文件、审计和 Quartz
  任务配置/执行日志；Quartz 使用 JDBC JobStore 和 `QRTZ_` 表。
- 应用不自动创建或修改业务 schema。结构和权限由 DBA/权限管理员按审批执行，本
  Runbook 不提供交付 DDL/DML，也不通过数据库直接暂停、恢复或触发任务。
- 任何写操作先取得工单/变更审批，确认目标实例、目标 `jobId`/资源和回退负责人；
  只读检查可以先行。
- 不在排障中复制口令、OBS 密钥、Cookie、完整个人信息、完整 SQL 结果或未脱敏日志；
  证据使用 Trace、时间窗口和脱敏摘要关联。

## 2. 启动前核对（只读）

### 2.1 配置和进程归属

1. 确认待启动的 checkout、活动 profile 和配置文件来源；重点核对
   `spring.quartz.enabled`、`job-store-type=jdbc`、`jdbc.initialize-schema=never`、
   `obs.enabled`、multipart 限制和 Session JDBC 配置。
2. 若实例已运行，只读取进程命令行、工作目录和监听关系，确认它属于目标 checkout；
   不因端口冲突停止或复用其他实例。
3. 读取应用日志中的配置加载、数据源连接、Quartz 初始化和 OBS 初始化结果。凭据值只
   看是否已注入，不输出具体值。

可使用现场变量执行只读检查（变量由运维工具或受控终端提供）：

```bash
ps -ef | rg '[b]ootstrap|[s]pring'
readlink -f /proc/<PID>/cwd
tr '\0' ' ' < /proc/<PID>/cmdline
ss -ltnp
```

以上命令只用于确认进程归属和监听关系；`<PID>`、端口和路径必须替换为现场核对结果，
不能写入工单模板作为固定值。

### 2.2 数据库和调度状态

仅使用具有最小权限的只读账号，在目标 profile 指向的数据库执行以下核对。SQL 只用于
现场诊断，不保存为交付脚本：

```sql
SELECT job_key, status, cron_expr, quartz_job_class, allow_manual_trigger, last_run_time
  FROM SYS_JOB_CONF
 ORDER BY job_key;

SELECT job_id, trigger_type, status, start_time, end_time, error_msg
  FROM SYS_JOB_RUN_LOG
 ORDER BY start_time DESC;

SELECT SCHED_NAME, INSTANCE_NAME, LAST_CHECKIN_TIME
  FROM QRTZ_SCHEDULER_STATE;

SELECT SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP, TRIGGER_STATE, NEXT_FIRE_TIME,
       PREV_FIRE_TIME
  FROM QRTZ_TRIGGERS;
```

核对结果至少包括：业务任务配置是否存在、状态与 Cron 是否可解释、最近执行是否有
异常、Quartz 节点心跳是否更新、触发器状态是否与业务配置一致。不要以固定任务数或
固定节点数判断健康。

### 2.3 权限和外部依赖

- 管理端 URL、HTTP 方法、`BizType`/`BizAction` 和 `PT_RESOURCE` 绑定一致；
  管理操作由授权账号执行，不能以未标注 `@BizAuth` 推断公开。
- 用户/机构解析通过 auth-permission-center 的公开 API；OBS 端点、桶和凭据来自受控
  注入；Spring Session JDBC、Quartz 表和治理表均应已由责任方准备。
- 启动前不执行初始化 SQL、不复制生产数据、不修改代理/端口/数据源或外联凭据。

## 3. 健康检查

按实际网关和认证方式访问，不在命令中记录 Cookie 或 Token：

1. 访问应用基础健康端点或首页，确认 HTTP 响应、Trace 和应用日志时间一致。
2. 通过授权管理端读取 `GET /api/admin/sys/jobs`，再按需读取
   `GET /api/admin/sys/jobs/{jobId}/logs`；确认配置列表能读、分页参数生效、日志
   状态可追踪。
3. 读取 `GET /api/sys/dicts`、`GET /api/sys/calendar`，确认公共读取链路；
   通过授权端读取审计列表，确认数据范围和响应包装正确。
4. 按审批范围验证文件对象开关和通知自服务；`obs.enabled=false` 时文件操作应明确
   返回治理错误，不得把数据库元数据当成对象读取成功。
5. 对配置、字典和年度日历执行一次读后写缓存失效核对（只在有审批的变更窗口进行）；
   运行实例内 Caffeine 缓存，不把另一实例的读取作为生效证明。

健康检查应记录：实例标识、活动 profile、检查时间、Trace、接口状态、最近 Job 状态、
Quartz 心跳和 OBS/Session 结果。日志和截图脱敏后归档。

## 4. 任务暂停、恢复和人工触发

### 4.1 暂停

1. 记录工单、原因、目标 `jobId`、当前配置状态、是否正在运行以及最近失败信息。
2. 用授权账号调用当前接口：

   `PUT /api/admin/sys/jobs/{jobId}/pause`

3. 再读任务配置和运行日志，确认状态与 Quartz Trigger 均已进入预期暂停状态。暂停只
   影响后续调度，不强行终止已经运行的 Job；运行中的 Job 由其自身完成或按独立的应急
   审批处理。

### 4.2 恢复

1. 确认故障原因已处理、依赖可用、Cron 和 Job 类仍正确，且没有未完成的并发运行。
2. 用原工单授权账号调用：

   `PUT /api/admin/sys/jobs/{jobId}/resume`

3. 重新读取配置、Quartz Trigger 和执行日志，确认下一次触发时间和状态；记录恢复时间、
   操作人、Trace 和验证结果。当前恢复接口没有理由请求体，理由必须留在受控工单/审计
   记录中，不得自行扩展请求格式。

### 4.3 人工触发

人工触发是高风险动作，需 `SYS_CONFIG/JOB_TRIGGER` 授权和理由：

```text
POST /api/admin/sys/jobs/{jobId}/trigger
{
  "reason": "审批单中的最小必要说明",
  "dataDate": "按任务契约提供或省略",
  "allocDate": "按任务契约提供或省略"
}
```

触发后不要仅凭 HTTP 成功判断 Job 已完成。读取返回的触发信息、`SYS_JOB_RUN_LOG` 和
应用日志；`runLogId` 可能在响应时尚未生成。重复触发前先确认 `isJobRunning` 结果、
幂等语义和业务负责人批准。

## 5. 常见故障定位

### 5.1 应用启动但任务没有调度

- 先核对活动 profile 中 Quartz 是否启用、JobStore 是否为 JDBC、自动启动策略和表前缀；
  再查启动日志是否完成 Scheduler 初始化。
- 对照 `SYS_JOB_CONF` 检查任务状态、Cron、`quartz_job_class` 和
  `allow_manual_trigger`；类加载或 Cron 校验失败时按日志定位，不手工写 Quartz 表。
- 查看 `QRTZ_SCHEDULER_STATE` 心跳和 `QRTZ_TRIGGERS` 状态，区分单实例未启动、
  集群未登记和业务任务自身暂停。

### 5.2 触发接口失败或没有执行记录

- 先确认资源授权、目标 `jobId` 存在、任务未禁止人工触发，理由已提供。
- 读取应用错误码和 Trace，再查询任务配置、Quartz Trigger、`QRTZ_FIRED_TRIGGERS`
  和运行日志；不要绕过 API 直接触发。
- Scheduler 不可用时，按错误边界处理并联系部署负责人；不要把“配置已保存”当作“已
  调度成功”。

### 5.3 任务持续 RUNNING、失败或重复执行

- 以 `SYS_JOB_RUN_LOG`、Quartz fired trigger、节点心跳和应用日志交叉核对，确认是否仍
  有真实执行节点；`isJobRunning` 是只读判断，不提供通用互斥锁。
- 先暂停后续调度并通知任务负责人，保留异常摘要和业务影响；不直接杀进程、不删除运行
 记录、不修改状态字段。
- 集群重复现象优先核对 JDBC JobStore、`QRTZ_LOCKS`、节点时钟和实例标识，涉及数据
  修复时转 DBA 审批。

### 5.4 暂停/恢复状态不一致

- 记录接口响应和 Trace，重新读取业务配置、Trigger 状态、Scheduler 日志。
- 若 Quartz 操作失败而业务配置已改变，保持暂停或停止继续触发，提交运维/DBA 复核；
  不在数据库中手工补写状态。
- 恢复前重新验证 Job 类、Cron、依赖服务和未完成运行，避免在原因未消除时重复放量。

### 5.5 审计、通知或文件异常

- 审计缺失：按 Trace 查 `GovAuditLogHandler`、`AuditLogService` 和事务日志；区分
  普通切面失败隔离与 Service-managed 审计随业务事务回滚，不补造成功记录。
- 通知部分失败：按单条结果和服务端日志核对，批量接口不是整体回滚；重试前确认接收人
  和幂等策略。
- 文件失败：确认 `obs.enabled`、端点/桶注入和对象访问结果；新对象元数据落库失败时
  检查服务的补偿日志，避免重复上传或误删已有 MD5 对象。
- 字典、配置或日历读到旧值：确认写事务已提交、受影响缓存是否清除，并在同一实例重新
  读取；不能通过修改缓存表或重启其他实例代替核对。

## 6. 安全恢复与审批边界

以下动作必须由对应责任人审批并留痕：修改配置或资源授权、重启实例、暂停/恢复/人工
触发任务、OBS 对象处理、审计数据导出、任何数据库结构或业务数据变更。执行前后都要
保存脱敏的配置摘要、日志、接口响应、Trace 和状态核对结果。

- 数据库结构、权限和数据修复只由 DBA/权限管理员按审批流程实施；本 Runbook 的只读
  查询不得改写成执行脚本。
- 不执行 `UPDATE`、`DELETE`、`INSERT`、DDL、存储过程或自动结构升级；不清空、
  覆盖、克隆数据库，不把测试环境结论直接用于目标环境。
- 不使用未经核实的 PID、端口、checkout、代理、账号或凭据；不停止其他实例，不以
  强制杀进程替代任务暂停。
- 外部对象存储、通知发送和人工触发可能产生不可逆业务副作用；先确认范围、幂等策略、
  回退方案和业务窗口。
- 恢复完成的判据是：接口权限正确、目标配置与 Quartz 状态一致、最近执行结果可追踪、
  审计/日志证据齐全，且责任人确认业务影响已关闭。

## 7. 交接清单

- [ ] 目标实例、活动 profile、数据源和外部依赖已核实。
- [ ] `SYS_JOB_CONF`、运行日志、Quartz 心跳/Trigger 与接口返回已交叉核对。
- [ ] 所有暂停、恢复、触发和重启均有审批、理由、操作人、时间和 Trace。
- [ ] 未执行越过 SQL 门禁的变更；未触碰其他实例或无关模块。
- [ ] 日志、请求、截图和数据库结果均已脱敏并按工单归档。
