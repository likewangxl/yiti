# 隔离 profile 核对

核对文件：`bootstrap/src/main/resources/application-redengine-task-e2e.yml`

已确认的配置契约：

- HTTP 绑定 `127.0.0.1`，端口由 `REDENGINE_TASK_SERVER_PORT` 覆盖；Session Cookie 使用独立的 `REDENGINE_TASK_SESSION_COOKIE`。
- 主数据源和报表只读数据源均由 `REDENGINE_TASK_DB_URL` 覆盖，默认数据库为 `yit_test`；用户名/密码必须由环境变量传入。
- Spring Session JDBC 使用 `SPRING_SESSION`，不初始化 schema，不执行 cleanup cron。
- Quartz `enabled=false`、`auto-startup=false`，并显式排除 `QuartzAutoConfiguration`；Task Scheduling `enabled=false`，并显式排除 `TaskSchedulingAutoConfiguration`。
- Flowable process、schema update、process definitions、async executors 及 idm/app/eventregistry/dmn/cmmn/form 均禁用。
- OBS `enabled=false`，endpoint 默认保留到本机端口 9 的占位地址；sidecar 注册、SOAP Netty、通知外发、锁清理和绩效调度均关闭。

验收启动时必须使用该 profile，不能使用 `application-test.yml`、`redengine-smoke` 或 `onepl_test_bootstrap`。
