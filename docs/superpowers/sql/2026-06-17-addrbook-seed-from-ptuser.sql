-- 2026-06-17 通讯录测试数据重建：用真实 PT_USER（启用）+ EXT_USER_ORG + EXT_ORG_INFO 生成 12 条，
-- 替换原有孤立合成数据（E001/张三2/BJ_CY 等与 PT_USER/机构对不上）。
-- 使通讯录展示与「工号关联 PT_USER、机构关联 EXT_ORG_INFO」的导入模型自洽。
-- emp_id = PT_USER.USERNAME（工号）；emp_name = USERCHNNAME；机构取该用户在 EXT_USER_ORG 的首个机构。

DELETE FROM ADDRBOOK_EMPLOYEE;

INSERT INTO ADDRBOOK_EMPLOYEE
    (emp_id, emp_name, mobile, email, org_code, org_name, position, self_desc,
     responsible_product_ids, status, maintainer_emp_id, created_time, updated_time, deleted)
SELECT
    t.USERNAME,
    t.USERCHNNAME,
    CONCAT('138', RIGHT(CONCAT('00000000', t.USERNAME), 8)),
    COALESCE(NULLIF(t.EMAIL, ''), CONCAT(t.USERNAME, '@bank.cn')),
    o.ORG_CODE,
    o.ORG_NAME,
    '客户经理',
    NULL,
    NULL,
    'ACTIVE',
    'admin',
    NOW(),
    NOW(),
    0
FROM (
    SELECT u.USERNAME, u.USERCHNNAME, u.EMAIL,
           (SELECT uo.ORG_CODE FROM EXT_USER_ORG uo WHERE uo.USER_ID = u.USER_ID LIMIT 1) AS oc
    FROM PT_USER u
    WHERE u.ISENABLED = 0
      AND u.USERCHNNAME IS NOT NULL
      AND EXISTS (SELECT 1 FROM EXT_USER_ORG uo2 WHERE uo2.USER_ID = u.USER_ID)
    ORDER BY u.USER_ID
    LIMIT 12
) t
JOIN EXT_ORG_INFO o ON o.ORG_CODE = t.oc;
