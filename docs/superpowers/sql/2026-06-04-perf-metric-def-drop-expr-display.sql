-- ============================================================================
-- PERF_METRIC_DEF 删除 expr_display 列（含标签展示串改为读取时实时派生）
-- ----------------------------------------------------------------------------
-- 背景：expr_display（M_xxx·名称 展示串）原为持久化列、需与 expr_text 同步维护，
--      且导入/历史/直接入库的数据常为空。现改为：唯一真相 = expr_text，详情读取
--      （MetricDefService.getByCodeDto）按 expr_text 用 buildExprDisplay 实时派生
--      M_xxx → M_xxx·指标名称，响应 exprDisplay 字段保留但为派生值，不再入库。
--
-- 顺序：必须在"去除 expr_display 读写的新版后端已部署"之后再删列。
-- 双库：dev=yiti / prod=onepl 均需执行。
-- 影响：删列即抹掉历史 expr_display 文本，但展示串可由 expr_text 重新派生，无信息损失。
-- ============================================================================

ALTER TABLE PERF_METRIC_DEF DROP COLUMN expr_display;
