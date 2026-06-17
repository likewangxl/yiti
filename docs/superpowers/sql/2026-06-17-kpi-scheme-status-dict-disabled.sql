-- ============================================================================
-- 2026-06-17 KPI 方案状态字典：「停用(INACTIVE)」改为「禁用(DISABLED)」
--   SYS_DICT dict_type='KPI_SCHEME_STATUS'：
--   原 dict_code/value=INACTIVE、label=停用 → 改为 DISABLED / 禁用
--   （后端 disable 实际落 status=DISABLED，原 INACTIVE 选项过滤不到任何方案，本次一并修正）
-- 幂等（仅命中 INACTIVE 行）+ 双库 yiti/onepl。
-- ============================================================================

UPDATE yiti.SYS_DICT
   SET dict_code='DISABLED', dict_label='禁用', dict_value='DISABLED'
 WHERE dict_type='KPI_SCHEME_STATUS' AND dict_value='INACTIVE';

UPDATE onepl.SYS_DICT
   SET dict_code='DISABLED', dict_label='禁用', dict_value='DISABLED'
 WHERE dict_type='KPI_SCHEME_STATUS' AND dict_value='INACTIVE';
-- ============================================================================
