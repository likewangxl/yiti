-- 2026-05-31 PERF_ALLOC_ADJUST_ITEM 增加员工/部门快照列
-- 背景：新建调整申请的分配明细，提交时把员工 username/中文姓名/所属部门号/部门名称快照存入本表；
--      后续预览/详情查询直接读这些列，不再实时关联 PT_USER / 机构表（口径冻结在提交时点，利于审计）。
-- 双库执行：dev(yiti) + 生产(onepl)。ALTER 为新增列（幂等：列已存在则报错可忽略/手工跳过）。

ALTER TABLE PERF_ALLOC_ADJUST_ITEM
  ADD COLUMN username     varchar(64)  NULL COMMENT '员工登录名(快照)'   AFTER emp_id,
  ADD COLUMN emp_chn_name varchar(64)  NULL COMMENT '员工中文姓名(快照)' AFTER username,
  ADD COLUMN org_code     varchar(32)  NULL COMMENT '所属部门号(快照)'   AFTER emp_chn_name,
  ADD COLUMN org_name     varchar(128) NULL COMMENT '所属部门名称(快照)' AFTER org_code;

-- 存量回填（best-effort）：按 emp_id 命中 PT_USER.USER_ID 或 USERNAME，取主机构号/名补全。
-- 历史数据无快照时用此回填，保证旧申请展示不空白。新数据由提交逻辑直接写入。
UPDATE PERF_ALLOC_ADJUST_ITEM i
  LEFT JOIN PT_USER u      ON (u.USER_ID = i.emp_id OR u.USERNAME = i.emp_id)
  LEFT JOIN EXT_USER_ORG uo ON uo.USER_ID = u.USER_ID
  LEFT JOIN EXT_ORG_INFO o  ON o.ORG_CODE = uo.ORG_CODE
SET i.username     = COALESCE(u.USERNAME, i.emp_id),
    i.emp_chn_name = u.USERCHNNAME,
    i.org_code     = uo.ORG_CODE,
    i.org_name     = o.ORG_NAME
WHERE i.username IS NULL;
