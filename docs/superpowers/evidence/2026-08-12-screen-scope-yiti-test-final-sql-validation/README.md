# yiti_test 大屏范围 SQL 最终验证证据

## 范围与断点

本轮唯一写入目标为 `127.0.0.1:3306/yiti_test`。同一实例身份为：

```text
DATABASE() = yiti_test
@@server_uuid = d3a209c4-42bd-11f1-bb1f-000c299f5629
@@hostname = ubuntu
@@port = 3306
```

按交接断点，auth（SHA `900407...`）与 align（SHA `268829...`）的首次成功执行已经在上一个会话完成；本轮从 seed 开始，**没有**把历史失败写成本轮结果，也没有重做克隆、启动应用或写入 `yiti`。此前失败的原始证据仅见 [旧停止证据](../2026-08-12-screen-scope-yiti-test-sql-execution-stop/README.md)。

## 执行顺序与结果

1. 重算三份完整 SHA-256，均精确匹配交接值，见 [01-sql-hashes.txt](01-sql-hashes.txt)。
2. 写前最小只读盘点：seed 目标组/屏/白名单均为 `0`，对齐资源为 `4`、对齐角色资源为 `9`。
3. 首次调用所带的 `--abort-source-on-error` 被当前 mysql 客户端在参数解析阶段拒绝（exit `2`，未建库会话、未 `SOURCE`）；去掉不兼容选项后，明确未使用 `--force`。
4. 同一 mysql 会话依次执行身份核验、审批变量设置、`SOURCE seed`：exit `0`。
5. 在每次新会话中重复身份核验与变量设置后，按 **auth → align → seed** 完整复跑：exit 分别为 **0 / 0 / 0**。
6. 最终只读验收：exit `0`。

详细退出码见 [02-exit-codes.md](02-exit-codes.md)，脱敏真实命令模板见 [03-command-templates.md](03-command-templates.md)，原始 stdout/stderr 见 [04-execution-output.raw.md](04-execution-output.raw.md)。本地 manifest 在 [00-local-manifest.txt](00-local-manifest.txt)：它是 `yiti_test` 可审计标识，**不是**外部工单，也不代表生产授权。

## 最终只读验收摘要

| 项目 | 结果 |
| --- | --- |
| 目标表列数 | 7 张目标表均与预期一致：16/11/9/9/22/15/8。 |
| 目标索引 | 机构画像/机构组/成员/角色组、屏活跃编码、范围索引、数据源条线索引、屏级白名单索引均存在且列序/唯一性匹配。 |
| 四项 REPORT 资源 | 目标数 `4`，精确 URL/METHOD/SYS_CODE 匹配数 `4`。 |
| 角色资源绑定 | 目标数 `9`；AR_LIST `4`、AR_SAVE 的 SYS_ADMIN `1`、META_SAVE `4`、DS_PROBE 默认 `0`，重复 `0`。 |
| 机构组壳 | 目标数 `2`、身份不匹配 `0`；成员和角色-机构组绑定均为 `0`（本 seed 的刻意边界）。 |
| 草稿大屏与地图 | 两屏均存在、范围不匹配 `0`、草稿 JSON 有效 `2`、西安复合地图 `1`、四个异地节点 `1`、未发布草稿 `2`。 |
| 屏级白名单 | 预期角色对 `2`、非预期对 `0`、重复对 `0`。 |
| 数据源 | 共 `11` 行，`BIZ_LINE` 为空/非 `COMMON` 为 `0`。 |
| 业务键/过程残留 | 目标 screen/group/role 业务键重复均为 `0`；临时过程残留为 `0`。 |
| 前后聚合 diff | seed 首次写入：机构组 `0→2`、草稿屏 `0→2`、白名单 `0→2`；已完成的四项资源 `4→4`、角色资源 `9→9`。复跑后无额外变化。 |

## 修改与测试

- 未修改三份 SQL、未修改生产库、未改动 runner/checker，也没有新增测试；seed 没有暴露 SQL 缺陷，故无 SQL 修复的 Red/Green 测试。
- 本目录仅新增证据文件。
- MySQL 对脚本中 `BINARY expr` 在 4 个成功 `SOURCE` 区段各输出 8 条、共 32 条 deprecation warning；不影响本轮 exit code 或验收，但未来升级前应将比较表达式迁移为推荐的 `CAST` 语义并补回归验证。

## 剩余风险和停止条件

- `yiti_test` 是离线克隆快照，不是当前 `yiti` 的实时镜像。
- 两个命名机构组当前只创建壳；成员及角色-机构组绑定需业务负责人另行审批填写，不能由本 seed 自动推导。
- 本轮没有启动后端/前端，也不构成应用联调或无 mock Playwright 验收。
- 任何 `yiti` 写入仍须先报告本证据并取得用户的单独、明确确认；本轮没有该授权，也没有尝试执行。
