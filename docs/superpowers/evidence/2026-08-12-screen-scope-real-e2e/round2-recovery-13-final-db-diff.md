# Round 2 恢复最终数据库差分

比较对象：`round2-recovery-01-port-and-db-baseline.raw.txt` 的当前基线与 `round2-recovery-12-final-61-table-snapshot.raw.txt` 的最终精确 `COUNT(*)`。两次均只连接 `yiti_test`，未连接或修改 `yiti`。

唯一变化是本次唯一允许的浏览器登录所产生的 Session 行：

| 表 | 基线 | 最终 | delta | 结论 |
| --- | ---: | ---: | ---: | --- |
| `SPRING_SESSION` | 1 | 2 | +1 | 允许：唯一登录 POST |
| `SPRING_SESSION_ATTRIBUTES` | 1 | 2 | +1 | 允许：唯一登录 POST |

其余 59 个受控表逐行一致（delta 0）：

- Flowable repository 3 表与 runtime-job 6 表；
- Quartz 11 表（含 `QRTZ_LOCKS=2`）；
- `SYS_JOB_CONF=9`、`SYS_JOB_RUN_LOG=2864`；
- `PT_LOCK=0`、`AUDIT_LOG=852`；
- 文件/关系 7 表；
- Eval/import/compensation 13 表；
- 大屏/机构组/config 11 表，尤其 `PT_ORG_PROFILE=0`、`PT_ORG_GROUP=2`、`PT_ROLE_ORG_GROUP=0`、`RPT_SCREEN=5`、`RPT_SCREEN_ACCESS_ROLE=2`、`RPT_SCREEN_BLOCK=18`、`RPT_SCREEN_DATASOURCE=11`、`RPT_SCREEN_MAP_POINT=3`、`RPT_SCREEN_PUBLISH_LOG=6`；
- 认证/授权配置 4 表（`PT_USER=90`、`PT_ROLE=43`、`PT_RESOURCE=570`、`PT_ROLE_RESOURCE=7579`）。

因此，这次只读大屏验收未改变 screen/org/role/datasource、锁、Flowable、Quartz、文件或 Eval 数据；未将 session 清零或删除既有 Session 来制造基线。
