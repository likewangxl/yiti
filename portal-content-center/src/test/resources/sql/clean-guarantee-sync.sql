-- 担保信息同步集成测试前置清理：仅删除 TEST_ 前缀造数，避免污染共享测试库其它数据
DELETE FROM clms_ed_credit_info WHERE customerid LIKE 'TEST_%';
DELETE FROM zh_guarantee_info WHERE client_num LIKE 'TEST_%' OR client_name LIKE 'TEST\_%';
DELETE FROM ccms_business_contract WHERE customerid LIKE 'TEST_%';
