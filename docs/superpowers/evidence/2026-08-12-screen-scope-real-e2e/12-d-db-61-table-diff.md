# 阶段 D 启动前后 61 表聚合 diff

比较对象：`07-b-db-baseline.raw.txt` 的 61 行 `category/table/count` 原始输出，与 `11-d-db-poststartup-61-table-snapshot.raw.txt` 的 61 行原始输出。

本地只读比较命令（exit `0`，无 diff 输出）：

```bash
diff -u \
  <(awk '/^flowable_repository|^flowable_runtime_job|^quartz|^job_config_log|^spring_session|^platform_lock|^audit|^file_or_relation|^eval_import_compensation|^screen_config_org_group|^auth_access_config/' 07-b-db-baseline.raw.txt) \
  <(awk '/^flowable_repository|^flowable_runtime_job|^quartz|^job_config_log|^spring_session|^platform_lock|^audit|^file_or_relation|^eval_import_compensation|^screen_config_org_group|^auth_access_config/' 11-d-db-poststartup-61-table-snapshot.raw.txt)
```

| 受控类别 | 表数 | 前后计数相同 | 非零 delta |
| --- | ---: | ---: | ---: |
| Flowable repository | 3 | 3 | 0 |
| Flowable runtime job | 6 | 6 | 0 |
| Quartz | 11 | 11 | 0 |
| job config/run log | 2 | 2 | 0 |
| Spring Session | 2 | 2 | 0 |
| PT_LOCK | 1 | 1 | 0 |
| AUDIT_LOG | 1 | 1 | 0 |
| 文件/关系 | 7 | 7 | 0 |
| eval/import/compensation | 13 | 13 | 0 |
| screen/config/org-group | 11 | 11 | 0 |
| auth/access config | 4 | 4 | 0 |
| **总计** | **61** | **61** | **0** |

登录副作用和目标编码聚合也在启动前后重跑，原始输出与 `08-b-login-and-target-aggregate.raw.txt` 完全相同：

- `PT_USER` 聚合：`90 / 0 / 0 / 89 / 0 / 56`；
- 两个目标 screen、两个机构组壳、四个资源编码均仍为 `1`；
- `SPRING_SESSION*`、`AUDIT_LOG`、`PT_LOCK`、Flowable、Quartz、文件、评价补偿及 screen/config 受控表计数均无变化。

结论：本次短时启动没有造成可由这些精确行数聚合观测到的数据库写入。该零 delta 不构成阶段 D 通过，因为 OBS 和 sidecar 的外联/初始化活动已触发独立停止条件。
