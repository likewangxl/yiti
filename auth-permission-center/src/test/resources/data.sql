-- ============================================================
-- H2 Test Data for Auth (auth-permission-center)
-- ============================================================

-- 用户表（密码是 BCrypt 加密后的 "password"）
INSERT INTO PT_USER (USER_ID, USERNAME, USERCHNNAME, PWD, EMAIL, ISENABLED, ISEXPIRED, ISLOCKED, PASS_WRONG_COUNT) VALUES
    ('admin', 'admin', '系统管理员', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@test.com', 0, 0, 0, 0),
    ('user001', 'user001', '张三', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'zhangsan@test.com', 0, 0, 0, 0),
    ('user002', 'user002', '李四', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'lisi@test.com', 0, 0, 0, 0);

-- 角色表
INSERT INTO PT_ROLE (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS, SYS_CODE) VALUES
    ('R001', 'ADMIN', '系统管理员', 0, 'PLATFORM'),
    ('R002', 'CUST_MANAGER', '客户经理', 0, 'PLATFORM'),
    ('R003', 'BRANCH_HEAD', '分行行长', 0, 'PLATFORM'),
    ('R004', 'AUDITOR', '审计员', 0, 'PLATFORM');

-- 资源表
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO, ISMENU, STATUS, SYS_CODE) VALUES
    ('1', '/api/auth/login', 'POST', '登录', 0, 1, 0, 'PLATFORM'),
    ('2', '/api/auth/logout', 'POST', '登出', 0, 1, 0, 'PLATFORM'),
    ('3', '/api/auth/currentUser', 'GET', '当前用户', 0, 1, 0, 'PLATFORM'),
    ('4', '/api/role/list', 'GET', '查询角色', 0, 1, 0, 'PLATFORM'),
    ('5', '/api/role/create', 'POST', '创建角色', 0, 1, 0, 'PLATFORM'),
    ('6', '/api/dict/list', 'GET', '查询字典', 0, 1, 0, 'PLATFORM'),
    ('7', '/api/config/list', 'GET', '查询配置', 0, 1, 0, 'PLATFORM');

-- 用户角色关联
INSERT INTO PT_USER_ROLE (USER_ID, ROLE_ID, DEFAULT_ASSIGN) VALUES
    ('admin', 'R001', 1),
    ('user001', 'R002', 1),
    ('user002', 'R003', 1);

-- 角色资源关联
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID) VALUES
    ('RR001', 'R001', '1'), ('RR002', 'R001', '2'), ('RR003', 'R001', '3'),
    ('RR004', 'R001', '4'), ('RR005', 'R001', '5'), ('RR006', 'R001', '6'),
    ('RR007', 'R001', '7'),
    ('RR008', 'R002', '1'), ('RR009', 'R002', '2'), ('RR010', 'R002', '3'),
    ('RR011', 'R002', '4'), ('RR012', 'R002', '6'),
    ('RR013', 'R003', '1'), ('RR014', 'R003', '2'), ('RR015', 'R003', '3'),
    ('RR016', 'R003', '4'), ('RR017', 'R003', '6'),
    ('RR018', 'R004', '1'), ('RR019', 'R004', '2'), ('RR020', 'R004', '3'),
    ('RR021', 'R004', '6'), ('RR022', 'R004', '7');

-- 角色业务范围
INSERT INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS) VALUES
    ('RBS001', 'R001', 'LEAD', 'ALL', 0),
    ('RBS002', 'R001', 'CUSTOMER', 'ALL', 0),
    ('RBS003', 'R002', 'LEAD', 'SELF_CREATED', 0),
    ('RBS004', 'R002', 'CUSTOMER', 'SELF_CREATED', 0),
    ('RBS005', 'R003', 'LEAD', 'ORG', 0),
    ('RBS006', 'R003', 'CUSTOMER', 'ORG', 0);

-- 机构数据
INSERT INTO EXT_ORG_INFO (ID, ORG_CODE, ORG_NAME, ORG_LEVEL, P_ID, ORGAN_STATE) VALUES
    (1, 'HQ', '总行', 1, NULL, 0),
    (2, 'BJ', '北京分行', 2, 'HQ', 0),
    (3, 'SH', '上海分行', 2, 'HQ', 0),
    (4, 'BJ_CY', '北京分行朝阳支行', 3, 'BJ', 0),
    (5, 'SH_PD', '上海分行浦东支行', 3, 'SH', 0);

-- 用户机构关联
INSERT INTO EXT_USER_ORG (USER_ID, ORG_CODE) VALUES
    ('admin', 'HQ'),
    ('user001', 'BJ_CY'),
    ('user002', 'SH_PD');
