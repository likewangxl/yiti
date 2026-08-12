# 阶段 C 启动命令与退出结果

所有凭据只注入子进程环境；下列模板使用 `***` 掩码，且不包含实际值。

## C-1：日志路径前置失败

```bash
env SERVER_PORT=18082 SPRING_PROFILES_ACTIVE=screen-scope-e2e \
  YITI_SCREEN_SCOPE_DB_USERNAME='***' YITI_SCREEN_SCOPE_DB_PASSWORD='***' \
  YITI_TEST_USERNAME='***' YITI_TEST_PASSWORD='***' \
  mvn -Dstyle.color=never -pl bootstrap spring-boot:run \
  -Dspring-boot.run.profiles=screen-scope-e2e
```

- Maven exit：`1`。
- 原因：Logback 默认文件路径位于只读位置，应用在 Bean、数据库、监听端口之前退出。
- 处理：没有修改产品配置；读取 `logback-spring.xml` 后，仅为后续受控尝试增加 `LOG_HOME=/tmp/yiti-screen-scope-real-e2e-20260812/log-home-attempt3`。

## C-2：普通沙箱 loopback socket 前置失败

```bash
env LOG_HOME=/tmp/yiti-screen-scope-real-e2e-20260812/log-home-attempt2 \
  SERVER_PORT=18082 SPRING_PROFILES_ACTIVE=screen-scope-e2e \
  YITI_SCREEN_SCOPE_DB_USERNAME='***' YITI_SCREEN_SCOPE_DB_PASSWORD='***' \
  YITI_TEST_USERNAME='***' YITI_TEST_PASSWORD='***' \
  mvn -Dstyle.color=never -pl bootstrap spring-boot:run \
  -Dspring-boot.run.profiles=screen-scope-e2e
```

- Maven exit：`1`。
- 原因：普通运行沙箱拒绝 Java 创建到 `127.0.0.1:3306` 的 TCP socket（`SocketException: Operation not permitted`）；未形成可用应用上下文。
- 处理：在确认 18082/8092/30523 空闲后，仅对同一隔离命令申请受控运行权限。

## C-3：受控权限下的真实隔离启动

```bash
env LOG_HOME=/tmp/yiti-screen-scope-real-e2e-20260812/log-home-attempt3 \
  SERVER_PORT=18082 SPRING_PROFILES_ACTIVE=screen-scope-e2e \
  YITI_SCREEN_SCOPE_DB_USERNAME='***' YITI_SCREEN_SCOPE_DB_PASSWORD='***' \
  YITI_TEST_USERNAME='***' YITI_TEST_PASSWORD='***' \
  mvn -Dstyle.color=never -pl bootstrap spring-boot:run \
  -Dspring-boot.run.profiles=screen-scope-e2e
```

- 应用 PID：`3966416`。
- 观察到：单一 active profile `screen-scope-e2e`；Tomcat 启动到 `127.0.0.1:18082`；Spring `Started`。
- 停止动作：发现阶段 D 硬性隔离违规后，向仅本次启动的前台会话发送中断；应用优雅关闭。
- Maven exit：`0`，该值表示受控停机后 Maven 正常收尾，**不表示阶段 D 通过**。
- 没有调用 `/ishealth`；违规在启动日志已经出现时即停止。没有登录、前端或 Playwright 会话。
