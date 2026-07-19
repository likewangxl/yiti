-- PerfTriggerAuthWhitelistRemovalIT 收尾清理：精确删除前置夹具插入的两行,无残留。
-- CREATE_USER='it-fixture' 指纹保证只删夹具行——若测试库基线未来真的补登记了该资源,不会误删。
DELETE FROM PT_ROLE_RESOURCE WHERE ID = 'ITPERFTRIG00001';
DELETE FROM PT_RESOURCE WHERE RESOURCE_ID = 'P_PERF_CALC_TRIG' AND CREATE_USER = 'it-fixture';
