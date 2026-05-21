-- ============================================================
-- 审批流记录端点：PT_RESOURCE 注册 + 全角色绑定（2026-05-20）
-- ============================================================
-- 背景：
--   新增 2 个查询端点（perf 模块聚合 workflow-center 流程历史）：
--     GET /api/perf/alloc-adjust/{id}/approval-history
--     GET /api/perf/target-adjust/{id}/approval-history
--   既有 P_PERF_ALLOC_AD_GET /api/perf/alloc-adjust/* 只覆盖单段，
--   不会匹配多段路径 /alloc-adjust/{id}/approval-history。需独立资源。
--
-- 策略：所有角色（12 个）均可查看，业务上审批轨迹无脱敏需求，
--      “提议+追加约束”原则下尽量减少角色矩阵复杂度（SYS_ADMIN 跳过 RBAC，
--      仍冗余写入以保留显式痕迹）。
--
-- 适用库：yiti（dev）+ onepl（prod-like）双库同步执行，幂等。
-- 注意：本脚本仅插入数据，不改 schema；可重跑（INSERT IGNORE / ON DUPLICATE KEY UPDATE）。
-- ============================================================

-- 1) PT_RESOURCE：登记 2 条审批流记录资源
INSERT IGNORE INTO PT_RESOURCE
  (`RESOURCE_ID`, `RESOURCE_URL`, `RESOURCE_METHOD`, `MENU_NAME`, `MENU_ICON_URL`,
   `MENU_RANK_NO`, `ISMENU`, `MENU_ENDFLAG`, `PARENT_RESOURCE_ID`, `STATUS`,
   `SYS_CODE`, `CREATE_TIME`, `CREATE_USER`, `UPDATE_TIME`, `UPDATE_USER`, `REMARK`)
VALUES
  ('P_PERF_ALLOC_AD_HIS', '/api/perf/alloc-adjust/*/approval-history', 'GET',
   '分配调整申请审批流记录', NULL, 0, 0, '0', NULL, 0,
   'PERF', NOW(), 'approval-hist-2026-05-20', NOW(), 'approval-hist-2026-05-20',
   '2026-05-20 审批流记录端点'),
  ('P_PERF_TGT_AD_HIS', '/api/perf/target-adjust/*/approval-history', 'GET',
   '目标调整申请审批流记录', NULL, 0, 0, '0', NULL, 0,
   'PERF', NOW(), 'approval-hist-2026-05-20', NOW(), 'approval-hist-2026-05-20',
   '2026-05-20 审批流记录端点');

-- 2) PT_ROLE_RESOURCE：全角色绑定（12 角色 × 2 资源 = 24 条）
--    使用 INSERT SELECT 自动覆盖所有 RECORD_STATUS=0 的现存角色，
--    ON DUPLICATE KEY UPDATE 容忍重跑（PT_ROLE_RESOURCE 业务唯一键 (ROLE_ID, RESOURCE_ID)）。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '|', 'P_PERF_ALLOC_AD_HIS')),
       r.ROLE_ID, 'P_PERF_ALLOC_AD_HIS', 'PERF'
  FROM PT_ROLE r
 WHERE COALESCE(r.RECORD_STATUS, 0) = 0
ON DUPLICATE KEY UPDATE ROLE_ID = VALUES(ROLE_ID);

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '|', 'P_PERF_TGT_AD_HIS')),
       r.ROLE_ID, 'P_PERF_TGT_AD_HIS', 'PERF'
  FROM PT_ROLE r
 WHERE COALESCE(r.RECORD_STATUS, 0) = 0
ON DUPLICATE KEY UPDATE ROLE_ID = VALUES(ROLE_ID);

-- 3) 校验：每个资源应至少绑定 12 条（与当前角色总数一致）
-- SELECT RESOURCE_ID, COUNT(*) AS bind_count
--   FROM PT_ROLE_RESOURCE
--  WHERE RESOURCE_ID IN ('P_PERF_ALLOC_AD_HIS', 'P_PERF_TGT_AD_HIS')
--  GROUP BY RESOURCE_ID;
