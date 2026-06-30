# 2026-06-30 担保同步 `updateToGuarantee` / `saveToGuarantee` 性能重写

## 背景

定时任务 `GUARANTEE_INFO_SYNC`（`GuaranteeSyncJob` → `GuaranteeSyncService.processing()`）中，
`updateToGuarantee` 单条 SQL 执行 **> 10s**，导致数据库连接超时、整个同步任务失败。

源表 `clms_ed_credit_info` 由 Hive 抽数脚本**每日 truncate 重载，只含当日数据，月量级 1~2 万行**
（即每日数百~两千行）。几百行的表跑出 10s，**问题不在数据量，而在 SQL 执行计划本身是病态的**。

## 根因

原 SQL 用「内层 `GROUP BY customerid + MAX(creditno)` → 外层把派生表自连接回 `clms_ed_credit_info`」
的方式取「每客户最大 creditno 的整行」，存在三重放大：

1. **源表被扫两遍**：内层聚合扫一遍，外层自连接再扫一遍，且外层把 `substring()` / `REPLACE()` /
   `LIKE '%担保%'` 这组**不可索引谓词逐行重算**。
2. **`clms_ed_credit_info` 零业务索引**（仅主键），派生表也无可用 key → 自连接退化为嵌套循环，
   「行对比次数 × 每行函数计算」被放大到百万级。
3. **最终 JOIN 跨字符集**：`zh_guarantee_info.client_name`(utf8mb4) ↔ `clms.customername`(utf8mb3)，
   `client_name` 索引失效，再叠一层全量嵌套循环。

`saveToGuarantee` 是**同源反模式**（同样的自连接 groupwise-max 双扫），同样慢。

## 改动

仅改 `portal-content-center/src/main/resources/mapper/portal/GuaranteeSyncMapper.xml`，
**随应用部署生效，无需在生产库执行任何 DDL**：

- `updateToGuarantee` / `saveToGuarantee`：用 `ROW_NUMBER() OVER (PARTITION BY customerid ORDER BY creditno DESC)`
  **单遍扫描去重**，先把当日担保记录收敛成「每客户一行」的小集合，再 JOIN ~百行的担保表。
  消除自连接、消除谓词组合式重算、消除源表二次扫描。
- `credittype = 010010` → `credittype = '010010'`：原写法拿 varchar 列比数字字面量，触发逐行隐式转 double。
- `create_time = SYSDATE()` → `NOW()`：避免非确定性函数对 binlog/复制不友好。
- `saveToGuarantee` 的 `customername NOT IN (SELECT client_name ...)` 补 `WHERE client_name IS NOT NULL`，
  规避担保表存在 NULL 名时 `NOT IN` 整体落空导致一条都插不进的经典坑。

> 语义等价：`MAX(creditno)`（varchar 字典序）与 `ORDER BY creditno DESC` 取值一致；
> 窗口函数还顺手修掉「同客户并列最大 creditno 时更新多次」的不确定性。

**为什么不加列 / 不加索引**：源表每日 truncate、只有当日数据，日期过滤无选择性、索引无收益；
提速完全来自干掉自连接，与是否把 `substring`/`REPLACE` 前移到 shell 抽数脚本无关，故均不做。

## 回归护栏

新增 `GuaranteeSyncMapperIntegrationTest`（直连本地 MySQL，真实复现跨字符集 JOIN），
覆盖：按客户取最大 creditno、按客户名更新、字段映射、过滤非担保/已过期/错类型/非当日、
`saveToGuarantee` 只补录不存在客户。**重写前（旧 SQL）已先跑通刻画语义，重写后保持全绿**
（`Tests run: 2, Failures: 0`）。

测试 DDL（`clms_ed_credit_info` / `zh_guarantee_info` / `ccms_business_contract`，沿用 prod 字符集）
已补入 `src/test/resources/sql/test-ddl-portal-clean.sql`，清理脚本 `clean-guarantee-sync.sql`。

## 生产验证建议

部署后对当日数据 `EXPLAIN ANALYZE` 重写后的 SELECT 子句，确认 `clms_ed_credit_info` 只出现一次
（单遍扫描）、无自连接嵌套循环；并观察 `GUARANTEE_INFO_SYNC` 任务日志中 `updateToGuarantee` 耗时
应从 10s+ 降到亚秒级。
