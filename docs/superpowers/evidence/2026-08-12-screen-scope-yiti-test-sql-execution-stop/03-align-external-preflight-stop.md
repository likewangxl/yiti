# 第三次继续执行：auth 成功、align 外置预检停止证据

执行时间：2026-08-12；归档时间：2026-08-12T12:18:32+08:00。

## 结果

1. 修复后的 `2026-08-11-auth-org-profile-group.sql` 完成，退出码 `0`。
   - 零 DDL preflight 通过；
   - 两个大屏查看角色存在；
   - 7 个本期 auth 资源已存在；
   - 4 张机构画像/机构组表当前均为 0 行（只记录聚合）。
2. 随后执行 `2026-08-11-screen-scope-map-align.sql`，退出码 `1`。
3. `2026-08-11-screen-scope-map-seed.sql` 未执行。

所有执行只指定 `localhost:3306/yiti_test`；未访问或写入 `yiti`，未启动应用或前端。

## 首个失败

```text
ERROR 1146 (42S02) at line 103 in file: 'docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql':
Table 'yiti_test.RPT_SCREEN_ACCESS_ROLE' doesn't exist
EXIT_CODE 1
```

该错误位于 align 的外置只读 preflight：

```sql
SELECT 'REPORT external preflight: RPT_SCREEN_ACCESS_ROLE 存在重复屏角色' AS violation,screen_id,role_code
  FROM RPT_SCREEN_ACCESS_ROLE GROUP BY screen_id,role_code HAVING COUNT(*)>1;
```

`RPT_SCREEN_ACCESS_ROLE` 在这一时点按设计尚不存在，且同一脚本的后续过程负责创建它。因此该
无条件查询使脚本在进入机器 hard-stop guard 和所有 DDL/DML 之前失败。

## 边界与风险

- auth 已成功，不需要回滚；align 的本轮失败发生在外置 SELECT，未执行其过程或 DDL/DML。
- 不得跳过该失败直接执行 seed：seed 依赖 align 创建的白名单表和屏扩展结构。
- 需要最小修正 align 外置 preflight 对缺失 `RPT_SCREEN_ACCESS_ROLE` 的处理后，再从 align
  开始；本次不修改 SQL、不补写表或数据。

## 脚本哈希

```text
900407245a163246939b1cba15606c68a612e3093fd700b3c3c6a713b8d86554  2026-08-11-auth-org-profile-group.sql
81310dbb0232f23e5374207c93d427512429dad7012ce08ea39d6e4a1dc33349  2026-08-11-screen-scope-map-align.sql
5c1b22ecdd8c74480e0607237d58316a71e762383cab1dead4c7cfe20798d500  2026-08-11-screen-scope-map-seed.sql
```
