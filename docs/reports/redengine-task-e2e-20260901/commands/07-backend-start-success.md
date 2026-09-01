# 后端第三次启动核验（成功）

执行日期：2026-09-01（Asia/Shanghai）

## 启动命令（敏感值脱敏）

在 `/home/djdev/leid/yiti` 执行：

```bash
REDENGINE_TASK_SERVER_PORT=18091 \
REDENGINE_TASK_DB_URL='jdbc:mysql://127.0.0.1:3306/yit_test?...' \
REDENGINE_TASK_DB_USERNAME=root \
REDENGINE_TASK_DB_PASSWORD='[REDACTED]' \
REDENGINE_TASK_SESSION_COOKIE='REDENGINE_TASK_E2E_SESSION_20260901' \
REDENGINE_TASK_INSTANCE_ID='redengine-task-e2e-20260901' \
mvn -pl bootstrap spring-boot:run -Dspring-boot.run.profiles=redengine-task-e2e
```

启动前为修复后的 `red-engine-center` SNAPSHOT 执行了只构建安装（不修改源码、不写业务库）：

```bash
mvn -pl red-engine-center -am install -DskipTests
```

## 现场核验

- Maven 输出：`The following profiles are active: redengine-task-e2e`。
- Maven 输出：`ProcessEngine default created`；Flowable 同步引擎启动成功。
- Maven 输出：`ObsStorageClient ... obs.enabled=false`。
- Maven 输出：`Started BranchPlatformApplication`。
- 18091 监听进程：PID `4135214`，Java；cwd `/home/djdev/leid/yiti/bootstrap`。
- 进程环境只读核验：实例标识 `redengine-task-e2e-20260901`、端口 `18091`、数据库 URL 指向 `yit_test`；数据库密码和 Session Cookie 均未归档。
- `GET http://127.0.0.1:18091/api/auth/current-user` 返回 `401`，表明服务已响应并进入鉴权链路。
- `spring.quartz.enabled=false`、任务调度/异步执行器/异步历史/外发/OBS 均由 `redengine-task-e2e` profile 禁用；启动日志中的 scheduler unavailable 仅为 Quartz 关闭下的预期提示。

## 数据安全边界

启动前 ACT_* 只读快照见 `commands/06-prestart-db-third-attempt.md`。本步骤未执行 DML、DDL、DELETE，也未连接 `yiti` 或 `yiti_test`；业务链路完成后须再次执行 ACT_* 只读快照并对比。

