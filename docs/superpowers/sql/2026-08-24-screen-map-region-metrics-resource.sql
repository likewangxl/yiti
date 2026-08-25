-- 大屏地图经营指标只读端点资源注册（2026-08-24）
-- 目标：GET /api/screen/admin/screens/*/map-region-metrics
-- 权限策略：沿用 R_RPT_SCR_CFG_GET 的现有受众，不扩大屏配置读取权限。

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, UPDATE_TIME, REMARK)
SELECT 'R_RPT_SCR_MAP_KPI', '/api/screen/admin/screens/*/map-region-metrics', 'GET',
       '大屏-地图经营指标', 0, 0, '0', 0, 'RPT', NOW(), NOW(),
       '按屏权限和机构范围读取地图经营指标快照'
 WHERE NOT EXISTS (
   SELECT 1 FROM PT_RESOURCE
    WHERE RESOURCE_ID = 'R_RPT_SCR_MAP_KPI'
       OR (RESOURCE_URL = '/api/screen/admin/screens/*/map-region-metrics'
           AND RESOURCE_METHOD = 'GET' AND SYS_CODE = 'RPT')
 );

INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT REPLACE(UUID(), '-', ''), source_role.ROLE_ID, 'R_RPT_SCR_MAP_KPI', 'PLATFORM', NOW()
  FROM PT_ROLE_RESOURCE source_role
  JOIN PT_ROLE role_def ON role_def.ROLE_ID = source_role.ROLE_ID AND role_def.RECORD_STATUS = 0
 WHERE source_role.RESOURCE_ID = 'R_RPT_SCR_CFG_GET'
   AND NOT EXISTS (
     SELECT 1 FROM PT_ROLE_RESOURCE existing_binding
      WHERE existing_binding.ROLE_ID = source_role.ROLE_ID
        AND existing_binding.RESOURCE_ID = 'R_RPT_SCR_MAP_KPI'
   );

-- 执行前盘点与执行后验收通过独立只读命令完成，不混入交付 SQL。
