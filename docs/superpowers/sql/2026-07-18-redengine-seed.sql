-- ============================================================================
-- 红色引擎权限/字典/党组织种子（Task 4：权限/字典种子 SQL）
-- 只允许在 yiti_test / onepl_test_bootstrap 两个测试库执行；严禁对 yiti 生产库执行任何写操作。
-- 幂等：全部语句可重复执行（INSERT IGNORE 依赖显式主键/唯一键冲突去重；
--        RE_PARTY_ORG 无 org_code 唯一约束，改用 INSERT...SELECT...WHERE NOT EXISTS）。
--
-- 前置核查结论（2026-07-18，两库均已核实，详见 task-4-report.md）：
--   1) SYS_DICT 列：id/dict_type/dict_code/dict_label/dict_value/sort_order/status/remark/
--      created_by/created_time/updated_by/updated_time；唯一键 uk_dict_type_code(dict_type,dict_code)
--      SYS_DICT_ITEM 列：id/dict_type/item_code/item_label/item_value/sort_order/status/remark/
--      created_by/created_time/updated_by/updated_time；唯一键 uk_dict_type_item_code(dict_type,item_code)
--      两库结构完全一致。
--   2) 管理员角色码实际为 'SYS_ADMIN'（简报占位符 'R_ADMIN' 与实际不符，已改用真实值）：
--      yiti_test：ROLE_ID='1'  ROLE_CODE='SYS_ADMIN'
--      onepl_test_bootstrap：ROLE_ID='R_ADMIN'  ROLE_CODE='SYS_ADMIN'
--      两库 ROLE_CODE 一致，故本脚本统一用 WHERE ROLE_CODE='SYS_ADMIN' 定位，两库通用无需改写。
--   3) RE_ROLE_1..4 / P_RE_* 资源 / RED_ENGINE BizScope 在两库核查时均无冲突（查询结果为空）。
--   4) onepl_test_bootstrap 已具备本脚本涉及的全部表（PT_RESOURCE/PT_ROLE/PT_ROLE_RESOURCE/
--      PT_ROLE_BIZ_SCOPE/SYS_DICT/SYS_DICT_ITEM/RE_PARTY_ORG），结构与 yiti_test 一致，两库
--      执行同一份脚本，无需拆分。
--
-- 修复记录（2026-07-18 二次修复，控制器核实后要求）：
--   governance 模块的 DictApi/DictController/AdminDictController 与 SysDict 实体
--   （system-governance-center/.../entity/SysDict.java，@TableName("SYS_DICT")）只读写
--   SYS_DICT 单表，全平台代码不存在任何 SYS_DICT_ITEM 引用（grep 确认）。第 6 段原先落地的
--   "类型头(SYS_DICT 4行)+字典项(SYS_DICT_ITEM 18行)"两级设计因此对平台完全不可见。现改为
--   按平台既有拍平惯例（抽样 yiti_test 现存 174 行 SYS_DICT，如 NOTIFY_TYPE/BIZ_KIND/
--   AUDIT_BIZ_TYPE 均为 dict_type=类别、dict_code=项编码、status='ACTIVE'/'DISABLED' 字符串
--   枚举，与 SysDict.java 类注释一致）把 18 项直接落 SYS_DICT，dict_type 加 RE_ 前缀命名空间
--   （RE_ORG_TYPE/RE_DIMENSION/RE_SUBMIT_STATUS/RE_ITEM_CODE）防止与通用字典类型撞车；
--   第 6 段新增 DELETE 清理此前误写的 4 条类型头行 + 18 行 SYS_DICT_ITEM，不再写 SYS_DICT_ITEM。
-- ============================================================================


-- ============================================================================
-- 1) PT_RESOURCE：红色引擎 17 条 API 资源（端点↔资源对照表，见 task-4-brief.md）
-- ============================================================================
INSERT IGNORE INTO PT_RESOURCE
 (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER, REMARK)
VALUES
 ('P_RE_ORG_TREE',   '/api/re/orgs/tree',                 'GET',    '红色引擎-党组织树',         0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_ORG_GET',    '/api/re/orgs/*',                    'GET',    '红色引擎-党组织详情',       0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_ORG_ADD',    '/api/re/orgs',                      'POST',   '红色引擎-新增党组织',       0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_ORG_UPD',    '/api/re/orgs/*',                    'PUT',    '红色引擎-修改党组织',       0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_ORG_DEL',    '/api/re/orgs/*',                    'DELETE', '红色引擎-删除党组织(高危)', 0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_MAP_LIST',   '/api/re/user-party-maps',           'GET',    '红色引擎-用户党组织映射列表', 0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_MAP_BIND',   '/api/re/user-party-maps',           'POST',   '红色引擎-绑定用户党组织(高危)', 0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_SUBMIT_ADD', '/api/re/submits',                   'POST',   '红色引擎-新建上报',         0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_SUBMIT_MY',  '/api/re/submits/my',                'GET',    '红色引擎-我的上报',         0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_SUBMIT_GET', '/api/re/submits/*',                 'GET',    '红色引擎-上报详情',         0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_REVIEW_Q',   '/api/re/reviews/queue',              'GET',    '红色引擎-审核待审队列',     0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_REVIEW_APPR','/api/re/reviews/*/approve',          'POST',   '红色引擎-审核通过+评分',     0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_REVIEW_REJ', '/api/re/reviews/*/reject',           'POST',   '红色引擎-审核驳回',         0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_CKPT_VIEW',  '/api/re/cockpit/**',                 'GET',    '红色引擎-驾驶舱只读',       0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_CKPT_EXEC',  '/api/re/cockpit/overdue/execute',    'POST',   '红色引擎-执行逾期扣分(高危)', 0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_CKPT_ANNUAL','/api/re/cockpit/archive/generate/*', 'POST',   '红色引擎-生成年度归档(高危)', 0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 ('P_RE_EXPORT',     '/api/re/export/*',                   'GET',    '红色引擎-数据导出',         0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6');


-- ============================================================================
-- 2) PT_ROLE：4 个党建角色
-- ============================================================================
INSERT IGNORE INTO PT_ROLE (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS, SYS_CODE, CREATE_USER) VALUES
 ('RE_ROLE_1', 'R_RE_ORGREV', '党建组织审核员', 0, 'RE', 'redengine-merge'),
 ('RE_ROLE_2', 'R_RE_BRREV',  '党建支部审核员', 0, 'RE', 'redengine-merge'),
 ('RE_ROLE_3', 'R_RE_SECR',   '党建支部书记',   0, 'RE', 'redengine-merge'),
 ('RE_ROLE_4', 'R_RE_REPORT', '党建报送员',     0, 'RE', 'redengine-merge');


-- ============================================================================
-- 3) PT_ROLE_RESOURCE：按对照表"角色"列分组绑定（ID = MD5(ROLE_ID#RESOURCE_ID) 保幂等）
-- ============================================================================

-- 3a) 全部 4 角色：党组织树 / 党组织详情 / 上报详情
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.rid)), r.ROLE_ID, x.rid, 'RE'
FROM PT_ROLE r
JOIN (SELECT 'P_RE_ORG_TREE' rid UNION ALL SELECT 'P_RE_ORG_GET' UNION ALL SELECT 'P_RE_SUBMIT_GET') x
WHERE r.ROLE_CODE IN ('R_RE_ORGREV','R_RE_BRREV','R_RE_SECR','R_RE_REPORT');

-- 3b) 报送员 + 支部书记：新建上报
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.rid)), r.ROLE_ID, x.rid, 'RE'
FROM PT_ROLE r
JOIN (SELECT 'P_RE_SUBMIT_ADD' rid) x
WHERE r.ROLE_CODE IN ('R_RE_REPORT','R_RE_SECR');

-- 3c) 报送员 + 支部书记 + 支部审核员：我的上报分页
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.rid)), r.ROLE_ID, x.rid, 'RE'
FROM PT_ROLE r
JOIN (SELECT 'P_RE_SUBMIT_MY' rid) x
WHERE r.ROLE_CODE IN ('R_RE_REPORT','R_RE_SECR','R_RE_BRREV');

-- 3d) 支部审核员 + 组织审核员：待审队列 / 审核通过 / 审核驳回
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.rid)), r.ROLE_ID, x.rid, 'RE'
FROM PT_ROLE r
JOIN (SELECT 'P_RE_REVIEW_Q' rid UNION ALL SELECT 'P_RE_REVIEW_APPR' UNION ALL SELECT 'P_RE_REVIEW_REJ') x
WHERE r.ROLE_CODE IN ('R_RE_BRREV','R_RE_ORGREV');

-- 3e) 组织审核员 + 支部书记：驾驶舱只读 / 数据导出
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.rid)), r.ROLE_ID, x.rid, 'RE'
FROM PT_ROLE r
JOIN (SELECT 'P_RE_CKPT_VIEW' rid UNION ALL SELECT 'P_RE_EXPORT') x
WHERE r.ROLE_CODE IN ('R_RE_ORGREV','R_RE_SECR');

-- 3f) 仅组织审核员：执行逾期扣分(高危) / 生成年度归档(高危)
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.rid)), r.ROLE_ID, x.rid, 'RE'
FROM PT_ROLE r
JOIN (SELECT 'P_RE_CKPT_EXEC' rid UNION ALL SELECT 'P_RE_CKPT_ANNUAL') x
WHERE r.ROLE_CODE = 'R_RE_ORGREV';


-- ============================================================================
-- 4) 管理员补行 SOP：管理员获得全部 17 条 P_RE_* 资源
--    注：管理员真实 ROLE_CODE 为 'SYS_ADMIN'（简报占位符 'R_ADMIN' 与实际不符，见前置核查结论 2）
-- ============================================================================
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', p.RESOURCE_ID)), r.ROLE_ID, p.RESOURCE_ID, 'RE'
FROM PT_ROLE r JOIN PT_RESOURCE p ON p.RESOURCE_ID LIKE 'P\_RE\_%'
WHERE r.ROLE_CODE = 'SYS_ADMIN';


-- ============================================================================
-- 5) PT_ROLE_BIZ_SCOPE：BIZ_TYPE='RED_ENGINE'，模块内自管，数据范围统一 ALL
--    4 个党建角色 + 管理员(SYS_ADMIN)，共 5 行
-- ============================================================================
INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_USER)
SELECT MD5(CONCAT(r.ROLE_ID, '#RED_ENGINE')), r.ROLE_ID, 'RED_ENGINE', 'ALL', 0, 'redengine-merge'
FROM PT_ROLE r
WHERE r.ROLE_CODE IN ('R_RE_ORGREV','R_RE_BRREV','R_RE_SECR','R_RE_REPORT','SYS_ADMIN');


-- ============================================================================
-- 6) SYS_DICT：红色引擎字典 4 类 18 项（拍平惯例，dict_type 加 RE_ 前缀命名空间）
--    governance 模块只读写 SYS_DICT 单表，SYS_DICT_ITEM 全平台未被任何代码读取（见文件头
--    "修复记录"）。按平台拍平惯例把 18 项直接落 SYS_DICT，每项一行，(dict_type, dict_code)
--    唯一；dict_type 用 RE_ORG_TYPE / RE_DIMENSION / RE_SUBMIT_STATUS / RE_ITEM_CODE。
--    内容照抄 redengine data.sql 158-193 行。id 用 MD5(...) 生成 32 位十六进制，天然贴合
--    varchar(32) 主键并保证幂等重跑。不再写 SYS_DICT_ITEM（该表全平台未被读取，保持为空）。
-- ============================================================================

-- 6-0) 清理修复前误写的 4 条"类型头"行（无 RE_ 前缀的旧 dict_type）与 SYS_DICT_ITEM 18 行
--      已核实：这 4 个 dict_type（org_type/dimension/submit_status/item_code）在
--      yiti_test/onepl_test_bootstrap 两库仅由本任务本次写入，无其它模块/既有数据共用，
--      可安全清理；SYS_DICT_ITEM 两库本为空表，仅本任务误写过 18 行。DELETE 语句天然幂等
--      （重跑时条件不再命中，不报错，不影响其它 dict_type 数据）。
DELETE FROM SYS_DICT WHERE dict_type IN ('org_type','dimension','submit_status','item_code');
DELETE FROM SYS_DICT_ITEM WHERE dict_type IN ('org_type','dimension','submit_status','item_code');

-- 6a) RE_ORG_TYPE（3 项）
INSERT IGNORE INTO SYS_DICT (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, created_by) VALUES
 (MD5('RE_DICT#RE_ORG_TYPE#经营单位'),   'RE_ORG_TYPE', '经营单位',   '经营单位',   '经营单位',   1, 'ACTIVE', 'redengine-merge'),
 (MD5('RE_DICT#RE_ORG_TYPE#营销部室'),   'RE_ORG_TYPE', '营销部室',   '营销部室',   '营销部室',   2, 'ACTIVE', 'redengine-merge'),
 (MD5('RE_DICT#RE_ORG_TYPE#中后台部门'), 'RE_ORG_TYPE', '中后台部门', '中后台部门', '中后台部门', 3, 'ACTIVE', 'redengine-merge');

-- 6b) RE_DIMENSION（4 项）
INSERT IGNORE INTO SYS_DICT (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, created_by) VALUES
 (MD5('RE_DICT#RE_DIMENSION#dim1'), 'RE_DIMENSION', 'dim1', '党建联建(35分)',     'dim1', 1, 'ACTIVE', 'redengine-merge'),
 (MD5('RE_DICT#RE_DIMENSION#dim2'), 'RE_DIMENSION', 'dim2', '业务提升(50分)',     'dim2', 2, 'ACTIVE', 'redengine-merge'),
 (MD5('RE_DICT#RE_DIMENSION#dim3'), 'RE_DIMENSION', 'dim3', '头雁与先锋(10分)',   'dim3', 3, 'ACTIVE', 'redengine-merge'),
 (MD5('RE_DICT#RE_DIMENSION#dim4'), 'RE_DIMENSION', 'dim4', '督导与工作总结(5分)', 'dim4', 4, 'ACTIVE', 'redengine-merge');

-- 6c) RE_SUBMIT_STATUS（3 项）
INSERT IGNORE INTO SYS_DICT (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, created_by) VALUES
 (MD5('RE_DICT#RE_SUBMIT_STATUS#pending'),  'RE_SUBMIT_STATUS', 'pending',  '待审核', 'pending',  1, 'ACTIVE', 'redengine-merge'),
 (MD5('RE_DICT#RE_SUBMIT_STATUS#approved'), 'RE_SUBMIT_STATUS', 'approved', '已通过', 'approved', 2, 'ACTIVE', 'redengine-merge'),
 (MD5('RE_DICT#RE_SUBMIT_STATUS#rejected'), 'RE_SUBMIT_STATUS', 'rejected', '已驳回', 'rejected', 3, 'ACTIVE', 'redengine-merge');

-- 6d) RE_ITEM_CODE（8 项，含 remark）
INSERT IGNORE INTO SYS_DICT (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by) VALUES
 (MD5('RE_DICT#RE_ITEM_CODE#1.1'), 'RE_ITEM_CODE', '1.1', '1.1 外联共建分数',   '1.1', 1, 'ACTIVE', '最高35分', 'redengine-merge'),
 (MD5('RE_DICT#RE_ITEM_CODE#1.2'), 'RE_ITEM_CODE', '1.2', '1.2 党建项目',       '1.2', 2, 'ACTIVE', '最高35分', 'redengine-merge'),
 (MD5('RE_DICT#RE_ITEM_CODE#1.3'), 'RE_ITEM_CODE', '1.3', '1.3 党建创新',       '1.3', 3, 'ACTIVE', '最高35分', 'redengine-merge'),
 (MD5('RE_DICT#RE_ITEM_CODE#2.1'), 'RE_ITEM_CODE', '2.1', '2.1 业务提升分数',   '2.1', 4, 'ACTIVE', '最高50分', 'redengine-merge'),
 (MD5('RE_DICT#RE_ITEM_CODE#2.2'), 'RE_ITEM_CODE', '2.2', '2.2 市场开拓',       '2.2', 5, 'ACTIVE', '最高50分', 'redengine-merge'),
 (MD5('RE_DICT#RE_ITEM_CODE#4.1'), 'RE_ITEM_CODE', '4.1', '4.1 督导与工作总结', '4.1', 6, 'ACTIVE', '最高5分',  'redengine-merge'),
 (MD5('RE_DICT#RE_ITEM_CODE#4.2'), 'RE_ITEM_CODE', '4.2', '4.2 年度工作总结',   '4.2', 7, 'ACTIVE', '最高5分',  'redengine-merge'),
 (MD5('RE_DICT#RE_ITEM_CODE#sup'), 'RE_ITEM_CODE', 'sup', 'sup 补充加分',       'sup', 8, 'ACTIVE', 'N/A',      'redengine-merge');


-- ============================================================================
-- 7) RE_PARTY_ORG：党组织树 10 行（照抄 redengine data.sql 5-22 行，去 secretary_id）
--    id 为 auto_increment 主键且 org_code 无唯一约束，INSERT IGNORE 无法据此去重，
--    改用 INSERT...SELECT...WHERE NOT EXISTS(org_code) 保证幂等；parent_id 用子查询按
--    org_code 动态解析，不写死自增值。
-- ============================================================================

-- 7a) 顶级：分行党委（org_level=1）
INSERT INTO RE_PARTY_ORG (org_name, parent_id, org_level, org_code, org_type, principal, contact_phone, org_address)
SELECT '西安分行党委', NULL, 1, 'ORG_001', NULL, '李明', '029-8888-0001', '陕西省西安市高新区'
WHERE NOT EXISTS (SELECT 1 FROM RE_PARTY_ORG WHERE org_code = 'ORG_001');

-- 7b) 9 个下级党支部（org_level=2，全部为经营单位；parent_id 指向 ORG_001）
INSERT INTO RE_PARTY_ORG (org_name, parent_id, org_level, org_code, org_type, principal, contact_phone, org_address)
SELECT '高新支行党支部', (SELECT id FROM RE_PARTY_ORG WHERE org_code = 'ORG_001'), 2, 'ORG_002', '经营单位', '张明', '029-8888-0002', '西安市高新区科技路'
WHERE NOT EXISTS (SELECT 1 FROM RE_PARTY_ORG WHERE org_code = 'ORG_002');

INSERT INTO RE_PARTY_ORG (org_name, parent_id, org_level, org_code, org_type, principal, contact_phone, org_address)
SELECT '经开支行党支部', (SELECT id FROM RE_PARTY_ORG WHERE org_code = 'ORG_001'), 2, 'ORG_003', '经营单位', '陈明', '029-8888-0003', '西安市经开区凤城路'
WHERE NOT EXISTS (SELECT 1 FROM RE_PARTY_ORG WHERE org_code = 'ORG_003');

INSERT INTO RE_PARTY_ORG (org_name, parent_id, org_level, org_code, org_type, principal, contact_phone, org_address)
SELECT '曲江支行党支部', (SELECT id FROM RE_PARTY_ORG WHERE org_code = 'ORG_001'), 2, 'ORG_004', '经营单位', '王强', '029-8888-0004', '西安市曲江新区雁南路'
WHERE NOT EXISTS (SELECT 1 FROM RE_PARTY_ORG WHERE org_code = 'ORG_004');

INSERT INTO RE_PARTY_ORG (org_name, parent_id, org_level, org_code, org_type, principal, contact_phone, org_address)
SELECT '碑林支行党支部', (SELECT id FROM RE_PARTY_ORG WHERE org_code = 'ORG_001'), 2, 'ORG_005', '经营单位', '赵敏', '029-8888-0005', '西安市碑林区长安路'
WHERE NOT EXISTS (SELECT 1 FROM RE_PARTY_ORG WHERE org_code = 'ORG_005');

INSERT INTO RE_PARTY_ORG (org_name, parent_id, org_level, org_code, org_type, principal, contact_phone, org_address)
SELECT '雁塔支行党支部', (SELECT id FROM RE_PARTY_ORG WHERE org_code = 'ORG_001'), 2, 'ORG_006', '经营单位', '刘伟', '029-8888-0006', '西安市雁塔区小寨路'
WHERE NOT EXISTS (SELECT 1 FROM RE_PARTY_ORG WHERE org_code = 'ORG_006');

INSERT INTO RE_PARTY_ORG (org_name, parent_id, org_level, org_code, org_type, principal, contact_phone, org_address)
SELECT '未央支行党支部', (SELECT id FROM RE_PARTY_ORG WHERE org_code = 'ORG_001'), 2, 'ORG_007', '经营单位', '陈静', '029-8888-0007', '西安市未央区凤城路'
WHERE NOT EXISTS (SELECT 1 FROM RE_PARTY_ORG WHERE org_code = 'ORG_007');

INSERT INTO RE_PARTY_ORG (org_name, parent_id, org_level, org_code, org_type, principal, contact_phone, org_address)
SELECT '莲湖支行党支部', (SELECT id FROM RE_PARTY_ORG WHERE org_code = 'ORG_001'), 2, 'ORG_008', '经营单位', '周波', '029-8888-0008', '西安市莲湖区北大街'
WHERE NOT EXISTS (SELECT 1 FROM RE_PARTY_ORG WHERE org_code = 'ORG_008');

INSERT INTO RE_PARTY_ORG (org_name, parent_id, org_level, org_code, org_type, principal, contact_phone, org_address)
SELECT '灞桥支行党支部', (SELECT id FROM RE_PARTY_ORG WHERE org_code = 'ORG_001'), 2, 'ORG_009', '经营单位', '吴刚', '029-8888-0009', '西安市灞桥区纺织路'
WHERE NOT EXISTS (SELECT 1 FROM RE_PARTY_ORG WHERE org_code = 'ORG_009');

INSERT INTO RE_PARTY_ORG (org_name, parent_id, org_level, org_code, org_type, principal, contact_phone, org_address)
SELECT '长安支行党支部', (SELECT id FROM RE_PARTY_ORG WHERE org_code = 'ORG_001'), 2, 'ORG_010', '经营单位', '孙丽', '029-8888-0010', '西安市长安区韦曲路'
WHERE NOT EXISTS (SELECT 1 FROM RE_PARTY_ORG WHERE org_code = 'ORG_010');
