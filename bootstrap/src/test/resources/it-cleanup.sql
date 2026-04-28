-- ============================================================
-- IT 三 profile 共享库 cleanup（FU-25 抽出，2026-04-29）
-- 用途：onepl_test_bootstrap 库被 default / flowable-e2e / lead-e2e 三 profile 共用，
--      flowable-e2e 引入 PT_RESOURCE 100-105（/api/workflow/tasks/...）
--      lead-e2e 引入 PT_RESOURCE LR101-105（同一组 URL+METHOD），
--      uk_pt_resource_url_method_sys 唯一键互冲突。
--      之前是 flowable-e2e-data.sql 与 lead-e2e-data.sql 各自顶部加双向 DELETE 矩阵，
--      维护性差。本 SQL 抽出统一前置 cleanup。
-- 引入方式：application-flowable-e2e.yml / application-lead-e2e.yml 在
--          spring.sql.init.data-locations 中放在 data.sql 之前（第一个）。
-- DELETE 顺序：先删依赖（PT_ROLE_RESOURCE 引用 100-105/WRR/LRR）→ 再删主体 PT_RESOURCE。
-- ============================================================
DELETE FROM PT_ROLE_RESOURCE WHERE ID LIKE 'WRR%' OR ID LIKE 'LRR%'
    OR RESOURCE_ID IN ('100','101','102','103','104','105')
    OR RESOURCE_ID IN ('LR001','LR002','LR003','LR101','LR102','LR103','LR104','LR105');
DELETE FROM PT_RESOURCE WHERE RESOURCE_ID IN ('100','101','102','103','104','105')
    OR RESOURCE_ID IN ('LR001','LR002','LR003','LR101','LR102','LR103','LR104','LR105');
