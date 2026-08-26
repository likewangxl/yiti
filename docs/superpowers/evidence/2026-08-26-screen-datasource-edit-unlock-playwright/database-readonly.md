# 真实 yiti 只读核对

核对时间：2026-08-26。仅执行 SELECT/JSON 读取，没有 DDL、DML 或测试写入。

## 数据源

| ID | 编码 | 名称 | 类型 | 来源 | 条线 | 状态 |
|---:|---|---|---|---|---|---|
| 9012 | SCRDS_CE5438AD | 日均存款详图 | TIMESERIES | WIDE_TABLE | COMMON | ACTIVE |

## 引用结果

- `RPT_SCREEN_BLOCK.bind_json` 中 `dsId=9012`：0 行。
- 当前发布包：`SCR_PROVINCE`、`SCR_BRANCH`、`SCR_PERSON` 的 `bindSnapshots` 均不含 9012。
- 6 条 `RPT_SCREEN_PUBLISH_LOG.snapshot_json` 归档的 `bindSnapshots` 均不含 9012。

结论：现场数据中 9012 确实没有草稿、当前发布或发布归档引用。浏览器验收为覆盖“存在发布引用仍可编辑”的新契约，单独使用开发态 mock 注入了 `SCR_PROVINCE` 引用，不能当作真实引用证据。
