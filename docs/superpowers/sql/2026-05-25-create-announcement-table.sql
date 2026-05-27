-- 公告表 DDL
-- 需要在 yiti 和 onepl 两个库都执行

CREATE TABLE IF NOT EXISTS ANNOUNCEMENT (
    id              VARCHAR(36)   NOT NULL PRIMARY KEY COMMENT '公告ID（UUID）',
    title           VARCHAR(200)  NOT NULL             COMMENT '公告标题',
    content         TEXT                               COMMENT '公告内容',
    publisher_id    VARCHAR(50)                        COMMENT '发布人ID',
    publisher_name  VARCHAR(100)                       COMMENT '发布人姓名',
    publish_date    DATETIME                           COMMENT '发布日期',
    is_deleted      TINYINT       NOT NULL DEFAULT 0   COMMENT '逻辑删除：0-未删除，1-已删除',
    created_by      VARCHAR(50)                        COMMENT '创建人',
    created_time    DATETIME                           COMMENT '创建时间',
    updated_by      VARCHAR(50)                        COMMENT '更新人',
    updated_time    DATETIME                           COMMENT '更新时间',
    INDEX idx_announcement_publish_date (publish_date DESC),
    INDEX idx_announcement_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告信息表';

-- 公告接口注册到 PT_RESOURCE
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS, SYS_CODE) VALUES
('RES_ANN_LIST',    '/api/portal/announcements',        'GET',    '公告列表',     0, 0, 'PLATFORM'),
('RES_ANN_RECENT',  '/api/portal/announcements/recent', 'GET',    '最近公告',     0, 0, 'PLATFORM'),
('RES_ANN_DETAIL',  '/api/portal/announcements/*',      'GET',    '公告详情',     0, 0, 'PLATFORM'),
('RES_ANN_CREATE',  '/api/admin/announcements',         'POST',   '新增公告',     0, 0, 'PLATFORM'),
('RES_ANN_DELETE',  '/api/admin/announcements/*',       'DELETE', '删除公告',     0, 0, 'PLATFORM');

-- 所有角色可查看公告（列表/最近/详情）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT REPLACE(UUID(),'-',''), r.ROLE_ID, res.RESOURCE_ID
FROM (SELECT DISTINCT ROLE_ID FROM PT_ROLE_RESOURCE WHERE ROLE_ID LIKE 'R_%') r
CROSS JOIN (SELECT RESOURCE_ID FROM PT_RESOURCE WHERE RESOURCE_ID IN ('RES_ANN_LIST','RES_ANN_RECENT','RES_ANN_DETAIL')) res;

-- 仅 ADMIN 可新增/删除公告
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID) VALUES
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'RES_ANN_CREATE'),
(REPLACE(UUID(),'-',''), 'R_ADMIN', 'RES_ANN_DELETE');

-- 公告管理菜单项（系统设置分组下）
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, MENU_RANK_NO, PARENT_RESOURCE_ID, STATUS, SYS_CODE)
VALUES ('M_SYS_ANN', '/system/announcements', 'GET', '公告管理', 1, 12, 'M_GROUP_SYSTEM', 0, 'PLATFORM');

-- 所有角色可看到公告管理菜单
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT REPLACE(UUID(),'-',''), r.ROLE_ID, 'M_SYS_ANN'
FROM (SELECT DISTINCT ROLE_ID FROM PT_ROLE_RESOURCE WHERE ROLE_ID LIKE 'R_%') r;
