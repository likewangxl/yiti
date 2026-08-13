# Align 第二次执行：零 DDL guard 语法停止证据

执行时间：2026-08-12；归档时间：2026-08-12T12:31:04+08:00。

## 结果与边界

已在 `localhost:3306/yiti_test` 上先复跑 auth，退出码 `0`，确认 auth 幂等成功；随后执行
修复后的 align。align 退出码为 `1`，在过程创建和所有 DDL/DML 前的机器零 DDL guard 发生
SQL 语法错误。seed 未执行。

未连接、读取或写入 `yiti`，未启动应用或前端。

## 客户端完整错误块（脱敏）

```text
ERROR 1064 (42000) at line 137 in file: 'docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql':
You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version for the right syntax to use near ')
            THEN 'REPORT align zero-DDL preflight: RPT_SCREEN_ACCESS_ROLE 定ä' at line 85
EXIT_CODE 1
```

MySQL 文件行号 137 是机器 guard 语句的起点：

```sql
SET @rpt_align_preflight_error := (
    SELECT CASE
```

错误中的 `line 85` 是该 `SET ... SELECT CASE` 语句内部的相对行号。

## 精确 SQL 片段与已确认问题

guard 的相关分支位于文件第 211–222 行，末段如下：

```sql
WHEN EXISTS (SELECT 1 FROM information_schema.tables
             WHERE table_schema=DATABASE() AND table_name='RPT_SCREEN_ACCESS_ROLE')
     AND ((SELECT COUNT(*) FROM information_schema.columns WHERE ... ) <> 8)
         OR EXISTS (SELECT 1 FROM (SELECT screen_id,role_code FROM RPT_SCREEN_ACCESS_ROLE
                                   GROUP BY screen_id,role_code HAVING COUNT(*)>1) d))
    THEN 'REPORT align zero-DDL preflight: RPT_SCREEN_ACCESS_ROLE 定义或重复冲突'
```

此前 `AND ((SELECT ...) <> 8)` 已闭合；最后的 `d))` 使 `EXISTS` 子查询之后多出一个右括号，
与客户端报告的 `near ')' THEN` 语法错误一致。此缺陷由当前 SQL 文件文本和 MySQL 解析错误
共同确认；本轮未尝试修改、绕过或重跑该脚本。

## 后续停止条件

- align 未完成，故不能执行 seed。
- 需最小修复该 guard 括号并完成相应验证后，才能从 align 重新开始；auth 已验证幂等，不需
  对其做补偿操作。

## 当前脚本哈希

```text
900407245a163246939b1cba15606c68a612e3093fd700b3c3c6a713b8d86554  2026-08-11-auth-org-profile-group.sql
2da8bbcfb97d3d559ba8f87643f5d614131b54d9c4d61be214c75d221b1f779e  2026-08-11-screen-scope-map-align.sql
5c1b22ecdd8c74480e0607237d58316a71e762383cab1dead4c7cfe20798d500  2026-08-11-screen-scope-map-seed.sql
```
