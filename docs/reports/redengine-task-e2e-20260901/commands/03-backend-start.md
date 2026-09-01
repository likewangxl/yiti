# 后端启动尝试（阻塞）

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

## 现场结果

- Maven 编译阶段通过（`Nothing to compile - all classes are up to date`）。
- 启动日志确认 profile 为 `redengine-task-e2e`，Tomcat 曾绑定 `127.0.0.1:18091`，报表只读数据源日志指向 `jdbc:mysql://127.0.0.1:3306/yit_test`。
- 启动在 Spring Context 初始化阶段失败，未进入 HTTP/API 或页面业务：

```text
UnsatisfiedDependencyException
WorkflowQueryFacade -> TodoQueryService
No qualifying bean of type 'org.flowable.engine.TaskService' available
APPLICATION FAILED TO START
```

- 原因：隔离 profile 按要求禁用 Flowable，但当前组合根仍装配 `WorkflowQueryFacade/TodoQueryService` 并强制注入 Flowable `TaskService`。不能通过开启 Flowable、切换 `application-test.yml` 或使用 `onepl_test_bootstrap` 绕过隔离要求。
- 失败后 `ss -ltnp 'sport = :18091'` 无监听；未启动前端、未执行业务 API、未创建任务、未提交/审核任务。
- 失败后再次只读计数 `RE_TASK`、`RE_TASK_INSTANCE`、`RE_TASK_BRANCH_ASSIGNMENT`、`RE_TASK_TODO`、`RE_TASK_SUBMISSION`、`RE_TASK_EXPORT_TASK` 均为 0。

该项需要后端代理修复并重新提交后才能继续真实页面验收；本验收代理不修改业务源码。
