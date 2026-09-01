# Flowable 配置修复后的后端启动尝试（阻塞）

## 版本与授权

- 使用提交 `ebc1b7f2`：仅把隔离 profile 的 `flowable.process.enabled` 调为 `true`，以满足组合根既有 `TaskService` 依赖。
- 仍按隔离要求关闭 Quartz、Flowable 异步执行器/异步历史、Task Scheduling、通知外发、sidecar 注册、SOAP Netty、OBS 和其他调度器。

## 命令（凭据已脱敏）

```text
REDENGINE_TASK_SERVER_PORT=18091 \
REDENGINE_TASK_DB_URL='jdbc:mysql://127.0.0.1:3306/yit_test?...' \
REDENGINE_TASK_DB_USERNAME='[REDACTED]' \
REDENGINE_TASK_DB_PASSWORD='[REDACTED]' \
REDENGINE_TASK_SESSION_COOKIE=REDENGINE_TASK_E2E_SESSION_20260901 \
REDENGINE_TASK_INSTANCE_ID=redengine-task-e2e-20260901 \
mvn -pl bootstrap spring-boot:run -Dspring-boot.run.profiles=redengine-task-e2e
```

## 结果

- Maven 编译阶段通过，Flowable `ProcessEngine default created`，Tomcat 曾绑定 `127.0.0.1:18091`，报表只读数据源指向 `yit_test`，OBS 日志明确为 `obs.enabled=false`。
- Spring Context 在装配 `ReTaskExportController` 时失败：

```text
UnsatisfiedDependencyException
ReTaskExportController -> ReTaskExportServiceImpl -> ReTaskExportAsyncExecutor
BeanCreationException: Failed to instantiate ReTaskExportAsyncExecutor
No default constructor found
NoSuchMethodException: ReTaskExportAsyncExecutor.<init>()
APPLICATION FAILED TO START
```

- 服务未进入 HTTP/API；启动失败后 `18091` 无监听，未启动前端，未创建/提交/审核任务。
- 启动后 ACT_* 行数/checksum 与启动前快照一致（见 `04-prestart-db.md`；再次逐表只读盘点未发现变化）。task 域表仍为 0 行。

该问题属于后端业务代码/Bean 装配缺陷，应由另一 Luna 代理修复；本验收代理不修改业务源码，不通过切换 profile 绕过。
