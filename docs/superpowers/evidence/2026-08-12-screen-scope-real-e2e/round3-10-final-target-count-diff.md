# Round 3 最小目标 COUNT(*) 差分

初始和最终均确认 DATABASE=yiti_test。比较 round3-01-preflight-build-and-isolation.raw.txt 与 round3-09-final-target-count-snapshot.raw.txt：

| 表或活动聚合 | 初始 | 最终 | Delta |
| --- | ---: | ---: | ---: |
| RPT_SCREEN | 5 | 5 | 0 |
| RPT_SCREEN_DATASOURCE | 11 | 11 | 0 |
| RPT_SCREEN_ACCESS_ROLE | 2 | 2 | 0 |
| RPT_SCREEN_MAP_POINT | 3 | 3 | 0 |
| PT_ORG_PROFILE | 0 | 0 | 0 |
| PT_ORG_GROUP | 2 | 2 | 0 |
| PT_ORG_GROUP_MEMBER | 0 | 0 | 0 |
| PT_ROLE_ORG_GROUP | 0 | 0 | 0 |
| PT_LOCK | 0 | 0 | 0 |
| ACT_RE_DEPLOYMENT | 145 | 145 | 0 |
| ACT_RU_JOB | 0 | 0 | 0 |
| ACT_RU_TIMER_JOB | 0 | 0 | 0 |
| ACT_RU_DEADLETTER_JOB | 0 | 0 | 0 |
| ACT_RU_EXTERNAL_JOB | 0 | 0 | 0 |
| ACT_RU_HISTORY_JOB | 0 | 0 | 0 |
| ACT_RU_SUSPENDED_JOB | 0 | 0 | 0 |
| QRTZ_FIRED_TRIGGERS | 0 | 0 | 0 |
| QRTZ_SCHEDULER_STATE | 0 | 0 | 0 |
| QRTZ_TRIGGERS | 0 | 0 | 0 |

结论：本轮所要求的大屏、范围、机构组、画像、锁、Flowable/Quartz 活动最小 COUNT 快照没有变化。此结论只覆盖上表的 COUNT(*)，不扩展为全库无变化声明；登录属于允许的前置动作且不纳入其功能或副作用评价。

