# 执行退出码

| 步骤 | 退出码 | 说明 |
| --- | ---: | --- |
| 最小只读盘点 | 0 | `DATABASE()=yiti_test`，随后才允许写入。 |
| 客户端兼容性预检 | 2 | `--abort-source-on-error` 不受当前 mysql 客户端支持；解析阶段失败，未建立数据库会话、未执行 `SOURCE`。 |
| seed 首次执行 | 0 | 同会话身份核验、变量设置和 `SOURCE seed` 全部成功。 |
| seed 后聚合快照 | 0 | 仅只读。 |
| auth 幂等复跑 | 0 | 同会话身份核验、变量设置和 `SOURCE auth` 全部成功。 |
| align 幂等复跑 | 0 | 同会话身份核验、变量设置和 `SOURCE align` 全部成功。 |
| seed 幂等复跑 | 0 | 同会话身份核验、变量设置和 `SOURCE seed` 全部成功。 |
| 最终只读验收 | 0 | 仅结构和聚合，不读取业务明细。 |

没有使用 `mysql --force`，没有跳过 guard、切换数据库或写入 `yiti`。
