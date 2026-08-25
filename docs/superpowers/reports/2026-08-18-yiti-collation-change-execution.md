# 四张业务表排序规则生产变更记录（2026-08-18）

> 性质：已执行变更的核验记录；不含可执行 DDL，不替代 DBA 审批单或回退操作手册。

## 变更范围与目标

在 `yiti`（MySQL 8.0.33）完成以下四张表的全字符列和表默认排序规则统一：

- `CUST_TRANSFER_LOG`
- `CUST_TRANSFER_TARGET`
- `t_accountability_for_violations`
- `t_credit_violation`

目标字符集为 `utf8mb4`，目标排序规则为 `utf8mb4_general_ci`。

## 执行前保护与隔离恢复演练

执行前对上述四表保存了受限访问的结构及数据备份，文件权限为目录 `0700`、备份文件
`0600`。生产备份 SHA-256：

```text
cf340090e7d3056c9acf5e39d1088077cea2129f384c59c05e24cc6d78695d22
```

经单独授权后，将该生产备份临时恢复至空的 `yiti_test` 对应四表，验证含真实数据的转换：

- 行数保持 `0 / 0 / 1 / 10`；
- 四表校验值保持不变；
- 客户转交 Mapper 的原始联表查询成功，不再出现 1267 排序规则冲突；
- 隔离库已用其变更前备份精确恢复，恢复前后 schema dump 字节级一致。

## 生产执行后核验

| 表 | 文本字段排序规则 | 行数 | 校验值 |
|---|---|---:|---:|
| `CUST_TRANSFER_LOG` | `utf8mb4_general_ci`（12 列） | 0 | 0 |
| `CUST_TRANSFER_TARGET` | `utf8mb4_general_ci`（5 列） | 0 | 0 |
| `t_accountability_for_violations` | `utf8mb4_general_ci`（44 列） | 1 | 1940727133 |
| `t_credit_violation` | `utf8mb4_general_ci`（33 列） | 10 | 1290318409 |

两张转交表的 `PRIMARY`、`uk_transfer_no`、`uk_transfer_target` 以及两个普通索引定义均保持不变；
两张违规表仍仅有数值主键。

生产库中使用
[`CustTransferLogMapper.xml`](../../../customer-marketing-center/src/main/resources/mapper/customer/CustTransferLogMapper.xml)
的原始联表和排序条件复核成功；与 `CUST_CLAIM` 的关联复核同样成功。

## 边界与后续

- 本次仅修改上述四张表，未修改其他表、应用代码、配置或服务进程。
- 当前生产备份仍以受限权限保留，供本次变更的回退窗口使用；不得将本文档作为备份替代品。
- 未进行需要登录态的浏览器页面验收；数据库层使用页面原始 Mapper SQL 完成了同等联表验证。
