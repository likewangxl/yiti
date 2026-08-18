# 四张业务表排序规则隔离库验证记录（2026-08-18）

> 性质：隔离库验证证据，不是生产变更脚本，不含可执行 DDL。

## 结论

在本机隔离库 `yiti_test`（MySQL 8.0.33）中，将以下四张表的字符列和表默认
排序规则统一为 `utf8mb4_general_ci` 后，客户转交记录 Mapper 的原始联表查询不再报
`Illegal mix of collations`。索引定义和行数均未变化。

已在验证结束后恢复四表原始结构；恢复后的无数据 schema dump 与变更前 dump 字节级一致。
未连接或修改 `yiti`。

## 范围与变更前状态

| 表 | 变更前文本字段排序规则 | 隔离库行数 |
|---|---|---:|
| `CUST_TRANSFER_LOG` | `utf8mb4_0900_ai_ci`（12 列） | 0 |
| `CUST_TRANSFER_TARGET` | `utf8mb4_0900_ai_ci`（5 列） | 0 |
| `t_accountability_for_violations` | `utf8mb4_0900_ai_ci`（3 列）、`utf8mb4_unicode_ci`（41 列） | 0 |
| `t_credit_violation` | `utf8mb4_0900_ai_ci`（6 列）、`utf8mb4_unicode_ci`（27 列） | 0 |

客户转交查询的关联键中，`CUSTOMER_MARKET_CUSTOMER.id` 和 `CUST_CLAIM.id` 已为
`utf8mb4_general_ci`，而 `CUST_TRANSFER_LOG.cust_id`、`claim_id` 为
`utf8mb4_0900_ai_ci`。变更前按页面 Mapper 原始联表逻辑查询，稳定复现：

```text
ERROR 1267 (HY000): Illegal mix of collations
(utf8mb4_general_ci,IMPLICIT) and (utf8mb4_0900_ai_ci,IMPLICIT)
for operation '='
```

四表均无被其他表引用的外键。两张转交表的主键、唯一索引和普通索引均已在变更前记录。
两张违规表只有数值主键，不存在文本唯一索引。

## 隔离验证结果

对四表完成全字符列转换后：

| 表 | 变更后文本字段排序规则 |
|---|---|
| `CUST_TRANSFER_LOG` | `utf8mb4_general_ci`（12 列） |
| `CUST_TRANSFER_TARGET` | `utf8mb4_general_ci`（5 列） |
| `t_accountability_for_violations` | `utf8mb4_general_ci`（44 列） |
| `t_credit_violation` | `utf8mb4_general_ci`（33 列） |

- 四表的表默认排序规则均为 `utf8mb4_general_ci`。
- 两张转交表的 `PRIMARY`、`uk_transfer_no`、`uk_transfer_target` 及两个普通索引定义保持不变。
- 四表行数保持为 0。
- 使用 [`CustTransferLogMapper.xml`](../../../customer-marketing-center/src/main/resources/mapper/customer/CustTransferLogMapper.xml)
  中的原始 `SELECT DISTINCT l.*`、两处 `LEFT JOIN` 和排序条件执行成功；针对
  `CUST_CLAIM.id = CUST_TRANSFER_LOG.claim_id` 的联表也执行成功。

## 恢复与限制

变更前已保存仅结构备份并记录 SHA-256：

```text
2a53b1c27a4425feffc618ec9b33d1a16e09b0a8f7f57ee6aa9ac1cf6bf9a889
```

验证后使用该备份恢复隔离表结构，并重新导出比较；两份 schema dump 完全一致。恢复后：

- `CUST_TRANSFER_LOG`、`CUST_TRANSFER_TARGET` 恢复为 `utf8mb4_0900_ai_ci`；
- 两张违规表恢复为原 `utf8mb4_0900_ai_ci`/`utf8mb4_unicode_ci` 混用状态。

恢复核验完成后，受限临时目录中的两份仅结构备份已删除，未保留业务数据副本。

本次隔离库四表均无业务数据，尚未覆盖生产数据的字符转换、锁等待、备份恢复或真实页面/API
联调。若 DBA 提交生产变更申请，必须重新在含脱敏代表性数据的隔离库完成这些验证，并按审批
直接实施；不得将本记录或历史 `docs/superpowers/sql/` 文件当作可执行结构变更脚本。
