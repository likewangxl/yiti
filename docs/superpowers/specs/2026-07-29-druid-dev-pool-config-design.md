# Druid 开发环境连接池配置设计

## 目标

在 `bootstrap/src/main/resources/application-dev.yml` 中补充 Druid 连接池容量、等待时间、空闲连接检测和主动保活配置，仅对 `dev` profile 生效，不改变其他环境的连接池行为。

## 配置设计

新增配置统一放在 `spring.datasource.druid` 下，并保留现有的监控、SQL 统计和日志过滤器配置：

| 配置项 | 值 | 作用 |
|---|---:|---|
| `initial-size` | `5` | 启动时创建 5 个连接 |
| `min-idle` | `5` | 连接池至少保留 5 个空闲连接 |
| `max-active` | `20` | 同时允许最多 20 个活跃连接 |
| `max-wait` | `60000` | 获取连接最长等待 60 秒 |
| `time-between-eviction-runs-millis` | `60000` | 每 60 秒运行一次空闲连接检测 |
| `min-evictable-idle-time-millis` | `300000` | 连接空闲满 5 分钟后允许回收 |
| `validation-query` | `SELECT 1` | 用轻量查询验证连接有效性 |
| `test-while-idle` | `true` | 在空闲检测时执行连接有效性验证 |
| `keep-alive` | `true` | 主动验证并保活达到最小空闲数的连接 |
| `keep-alive-between-time-millis` | `120000` | 同一连接两次保活检测至少间隔 2 分钟 |

## 作用范围

- 只修改 `application-dev.yml`，不把开发环境参数提升到全局 `application.yml`。
- 不修改数据源 URL、账号、Druid 类型或现有监控和日志配置。
- 不启用 `test-on-borrow` 或 `test-on-return`，避免每次借还连接都增加一次验证查询。
- 主动保活仅降低空闲连接失效的概率，不能防止事务执行期间发生 GoldenDB 节点切换、网络中断或 JDBC `socketTimeout`。

## 验证

1. 解析 YAML，确认文件语法有效。
2. 检查新增配置路径和取值与设计一致。
3. 执行 `git diff --check`，确认没有空白或格式错误。
4. 重启后端后确认应用启动成功且 `18080` 端口正常监听。
