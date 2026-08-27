# 线索审批入池回写修复验证

- 日期：2026-08-27
- 目标：修复审批状态 CAS 后继续使用旧乐观锁版本回写 `cust_id/pool_status` 的问题

## 代码验证

1. `updateStatusIf` 将审批状态更新为 `APPROVED` 并把 `lock_version` 加一。
2. 新增 `finalizeApproval` 专用 SQL，仅更新 `cust_id`、`pool_status` 和审计字段，
   并使用状态更新后的版本号再次做 CAS。
3. `finalizeApproval` 影响行数不是 1 时抛出业务异常，使整个审批事务回滚，
   不再返回假成功。
4. 隔离运行 `MarketingLeadApprovalServiceTest` 与
   `MarketingLeadInfoMapperXmlContractTest`：10 项全部通过。
5. 全量 Maven `clean install -Dmaven.test.skip=true` 通过；常规测试编译仍受工作树中
   无关的未完成 `NameListServiceTest` 阻断。

## 现有数据修复

修复前严格预检仅命中一条：线索 ID 1、客户主档 ID 1、线索版本 10，状态为
`APPROVED + PUBLIC + NOT_READY`，`cust_id` 为空，客户主档有效且无主办。

事务更新使用主键、版本号、业务状态、客户主档关联和无主办条件共同保护，实际影响 1 行。
修复后该线索为 `APPROVED + PUBLIC + AVAILABLE`、`cust_id=1`、版本 11；按待认领池
核心 SQL 统计可见记录数由 0 变为 1。
