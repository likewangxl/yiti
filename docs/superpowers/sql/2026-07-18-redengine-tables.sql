-- 红色引擎业务表（2026-07-18，只在 yiti_test / onepl_test_bootstrap 执行，禁止动 yiti 正式库）
-- 幂等：IF NOT EXISTS

CREATE TABLE IF NOT EXISTS RE_PARTY_ORG (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '党组织ID',
  org_name VARCHAR(100) NOT NULL COMMENT '党组织名称',
  parent_id BIGINT COMMENT '上级党组织ID',
  org_level INT COMMENT '层级(1:分行党委 2:党支部)',
  org_code VARCHAR(50) COMMENT '党组织编码',
  org_type VARCHAR(20) DEFAULT NULL COMMENT '类型：经营单位/营销部室/中后台部门',
  principal VARCHAR(50) COMMENT '负责人姓名',
  contact_phone VARCHAR(20) COMMENT '联系电话',
  org_address VARCHAR(255) COMMENT '地址',
  secretary_id VARCHAR(50) DEFAULT NULL COMMENT '支部书记平台用户工号(PT_USER.USER_ID)',
  remark TEXT COMMENT '备注',
  deleted TINYINT DEFAULT 0 COMMENT '软删(0有效 1删除)',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_parent_id (parent_id),
  KEY idx_org_code (org_code),
  KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-党组织';

CREATE TABLE IF NOT EXISTS RE_USER_PARTY_MAP (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id VARCHAR(50) NOT NULL COMMENT '平台用户工号(PT_USER.USER_ID)',
  party_org_id BIGINT NOT NULL COMMENT '党组织ID(RE_PARTY_ORG.id)',
  party_role VARCHAR(30) NOT NULL COMMENT '党内角色:ORG_REVIEWER/BRANCH_REVIEWER/SECRETARY/REPORTER',
  deleted TINYINT DEFAULT 0 COMMENT '软删(0有效 1删除)',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user (user_id),
  KEY idx_party_org_id (party_org_id),
  KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-平台用户党组织映射';

CREATE TABLE IF NOT EXISTS RE_SUBMIT (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '上报ID',
  org_id BIGINT NOT NULL COMMENT '党组织ID',
  submitter_id VARCHAR(50) COMMENT '提交人平台工号',
  dimension VARCHAR(20) COMMENT '考核维度(dim1~dim4)',
  item_code VARCHAR(50) COMMENT '考核项编码(如1.1)',
  item_name VARCHAR(100) COMMENT '考核项名称',
  max_score DECIMAL(5,1) COMMENT '该考核项满分上限',
  project_name VARCHAR(255) COMMENT '项目名称',
  submit_type INT COMMENT '上报类型(1月度 2季度 3年度)',
  submit_date DATE COMMENT '上报日期',
  status INT DEFAULT 0 COMMENT '状态(0草稿 1已提交 2已通过 3已驳回)',
  form_data LONGTEXT COMMENT '表单数据(JSON)',
  file_urls LONGTEXT COMMENT '附件URL列表(JSON数组,兼容迁移数据)',
  review_feedback VARCHAR(500) COMMENT '审核意见',
  reviewer_id VARCHAR(50) COMMENT '审核人平台工号',
  review_date TIMESTAMP NULL DEFAULT NULL COMMENT '审核时间',
  deleted TINYINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_org_id (org_id), KEY idx_submitter_id (submitter_id),
  KEY idx_dimension (dimension), KEY idx_item_code (item_code),
  KEY idx_status (status), KEY idx_submit_date (submit_date), KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-材料上报';

CREATE TABLE IF NOT EXISTS RE_SUBMIT_FILE (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  submit_id BIGINT NOT NULL COMMENT '上报ID',
  file_object_id VARCHAR(64) DEFAULT NULL COMMENT '平台文件ID(FILE_OBJECT,经 governance FileApi 上传)',
  file_name VARCHAR(255) NOT NULL COMMENT '文件名',
  file_path VARCHAR(500) DEFAULT NULL COMMENT '旧文件路径(仅迁移数据兼容,新数据置空)',
  file_size BIGINT COMMENT '文件大小',
  file_type VARCHAR(50) COMMENT '文件类型',
  deleted TINYINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_submit_id (submit_id), KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-上报附件';

CREATE TABLE IF NOT EXISTS RE_SCORE (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  org_id BIGINT NOT NULL COMMENT '党组织ID',
  submit_id BIGINT COMMENT '关联上报ID',
  item_code VARCHAR(50) COMMENT '考核项编码',
  score_year INT COMMENT '考核年度',
  score_period VARCHAR(20) COMMENT '考核期间(YYYY-MM)',
  base_score DECIMAL(10,2) DEFAULT 100.00,
  deduction_score DECIMAL(10,2) DEFAULT 0.00,
  final_score DECIMAL(10,2) COMMENT '最终得分',
  remark TEXT,
  deleted TINYINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_org_id (org_id), KEY idx_submit_id (submit_id), KEY idx_item_code (item_code),
  KEY idx_score_year (score_year), KEY idx_score_period (score_period), KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-评分';

CREATE TABLE IF NOT EXISTS RE_MEMBER_SCORE (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  org_id BIGINT NOT NULL COMMENT '党组织ID',
  user_id VARCHAR(50) COMMENT '党员平台工号',
  member_name VARCHAR(50) COMMENT '党员姓名',
  score_period VARCHAR(20) COMMENT '考核期间(YYYY-MM)',
  score DECIMAL(10,2) COMMENT '党员得分',
  remark TEXT,
  deleted TINYINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_org_id (org_id), KEY idx_user_id (user_id),
  KEY idx_score_period (score_period), KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-党员评分';

CREATE TABLE IF NOT EXISTS RE_OVERDUE_DEDUCTION (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  org_id BIGINT NOT NULL COMMENT '党组织ID',
  submit_id BIGINT COMMENT '上报ID',
  deduction_reason VARCHAR(255) COMMENT '扣分原因',
  deduction_points DECIMAL(10,2) COMMENT '扣分分值',
  deduction_date DATE COMMENT '扣分日期',
  remark TEXT,
  deleted TINYINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_org_id (org_id), KEY idx_submit_id (submit_id),
  KEY idx_deduction_date (deduction_date), KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-逾期扣分';

CREATE TABLE IF NOT EXISTS RE_ANNUAL_RESULT (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  org_id BIGINT NOT NULL COMMENT '党支部ID',
  dim1_score DECIMAL(5,1) DEFAULT 0 COMMENT '维度一得分(满35)',
  dim2_score DECIMAL(5,1) DEFAULT 0 COMMENT '维度二得分(满50)',
  dim3_score DECIMAL(5,1) DEFAULT 0 COMMENT '维度三得分(满10)',
  dim4_score DECIMAL(5,1) DEFAULT 0 COMMENT '维度四得分(满5)',
  total_score DECIMAL(5,1) NOT NULL COMMENT '原始总分(满100)',
  final_score DECIMAL(5,1) NOT NULL COMMENT '折算得分(total*0.4)',
  is_qualified TINYINT NOT NULL DEFAULT 1 COMMENT '评优资格:1保留 0拦截(<60)',
  eval_year INT NOT NULL COMMENT '考核年度',
  remark VARCHAR(500) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_org_year (org_id, eval_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-年度考核结果';

-- 注意两处与源 schema 的有意差异（不是笔误）：
-- 1. secretary_id/submitter_id/reviewer_id/user_id 从 BIGINT 改为 VARCHAR(50)——平台用户主键是 PT_USER.USER_ID（工号字符串）
-- 2. RE_SUBMIT_FILE.file_object_id 新增
