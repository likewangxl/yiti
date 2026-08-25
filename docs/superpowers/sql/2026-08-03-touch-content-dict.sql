-- ============================================================================
-- 触达模块：“本次触达内容”字典
-- 字典类型：TOUCH_CONTENT
-- 说明：脚本可重复执行；标签“待发进企”按需求原文保留。
-- ============================================================================

START TRANSACTION;

INSERT INTO SYS_DICT
  (id, dict_type, dict_code, dict_label, dict_value, sort_order, status,
   remark, created_by, created_time, updated_by, updated_time)
VALUES
  ('D_TCONT_PENDING_ENT', 'TOUCH_CONTENT', 'PENDING_ENTERPRISE', '待发进企', 'PENDING_ENTERPRISE',
   1, 'ACTIVE', '本次触达内容', 'seed', NOW(), 'seed', NOW()),
  ('D_TCONT_PRODUCT_MKT', 'TOUCH_CONTENT', 'PRODUCT_MARKETING', '产品营销', 'PRODUCT_MARKETING',
   2, 'ACTIVE', '本次触达内容', 'seed', NOW(), 'seed', NOW()),
  ('D_TCONT_DATA_COLLECT', 'TOUCH_CONTENT', 'DATA_COLLECTION', '资料收集', 'DATA_COLLECTION',
   3, 'ACTIVE', '本次触达内容', 'seed', NOW(), 'seed', NOW()),
  ('D_TCONT_OTHER', 'TOUCH_CONTENT', 'OTHER', '其他', 'OTHER',
   4, 'ACTIVE', '本次触达内容', 'seed', NOW(), 'seed', NOW())
ON DUPLICATE KEY UPDATE
  dict_label = VALUES(dict_label),
  dict_value = VALUES(dict_value),
  sort_order = VALUES(sort_order),
  status = VALUES(status),
  remark = VALUES(remark),
  updated_by = VALUES(updated_by),
  updated_time = VALUES(updated_time);

COMMIT;
