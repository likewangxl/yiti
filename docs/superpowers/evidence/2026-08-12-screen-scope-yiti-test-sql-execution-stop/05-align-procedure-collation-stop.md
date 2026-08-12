# Align 第三次执行：过程内排序规则冲突停止证据

执行时间：2026-08-12；归档时间：2026-08-12T12:38:49+08:00。

## 结果与边界

已在 `localhost:3306/yiti_test` 上复跑 auth，退出码 `0`；随后执行 align。align 的零 DDL
guard 已通过，但过程调用返回退出码 `1`。seed 未执行。

未连接、读取或写入 `yiti`，未启动应用或前端。

## 首个错误

```text
ERROR 1267 (HY000) at line 964 in file: 'docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql':
Illegal mix of collations (utf8mb4_general_ci,IMPLICIT) and (utf8mb4_0900_ai_ci,IMPLICIT) for operation '<=>'
EXIT_CODE 1
```

文件第 964 行是：

```sql
CALL sp_screen_scope_map_align_20260811();
```

因此 MySQL 客户端没有报告过程内部的精确语句行号。根据过程中的 `<=>` 位置、执行后的最小
聚合状态和只读排序规则元数据，首个触发点可精确定位为过程资源核验段：

```sql
-- 约第 890–895 行：临时资源表只声明 DEFAULT CHARSET=utf8mb4，未声明 COLLATE。
CREATE TEMPORARY TABLE tmp_rpt_scope_resources_20260811 (...)
    ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 第 902–904 行：
SELECT COUNT(*) INTO v_count FROM PT_RESOURCE p JOIN tmp_rpt_scope_resources_20260811 e
  ON p.RESOURCE_ID = e.resource_id
 WHERE NOT (p.RESOURCE_URL <=> e.resource_url
        AND p.RESOURCE_METHOD <=> e.resource_method
        AND p.SYS_CODE <=> 'RPT');
```

## 操作数来源与只读元数据

```text
连接字符集/排序规则                    utf8mb4 / utf8mb4_0900_ai_ci
PT_RESOURCE.RESOURCE_ID                 utf8mb4 / utf8mb4_general_ci
PT_RESOURCE.RESOURCE_URL                utf8mb4 / utf8mb4_general_ci
PT_RESOURCE.RESOURCE_METHOD             utf8mb4 / utf8mb4_general_ci
PT_RESOURCE.SYS_CODE                    utf8mb4 / utf8mb4_general_ci
```

操作数 `p.*` 来自上述既有 `PT_RESOURCE` 的 `general_ci` 列；`e.*` 来自未显式声明排序规则的
临时表。临时表由异常处理器删除，不能在失败后直接读取其列元数据；结合当前会话的
`0900_ai_ci` 和报错，两者构成该次冲突的证据链。`'RPT'` 是当前会话文本字面量，亦采用连接
排序规则，但错误发生在前两组列比较中的至少一组即可成立。

同一临时表区域尚有相同风险的后续比较（本轮未到达或未能确认是否逐个执行）：

- `p.RESOURCE_ID = e.resource_id`（join）；
- `p.RESOURCE_URL = e.resource_url`、`p.RESOURCE_METHOD = e.resource_method`；
- `p.RESOURCE_ID <> e.resource_id`；
- `NOT EXISTS (... p.RESOURCE_ID = e.resource_id)`。

## 部分应用状态与风险

失败后最小聚合显示 align 已在资源段之前执行部分 DDL：`RPT_SCREEN` 的 5 个扩展列已存在，
`RPT_SCREEN_ACCESS_ROLE` 已创建；4 个本期 report 资源计数仍为 `0`。这些 DDL 的隐式提交
不能视为已回滚。

必须在最小修复 align 的临时资源表/比较排序规则并完成验证后，才可从 align 重新开始；本轮
不修改 SQL、不补写资源或数据，也不执行 seed。

## 哈希留痕

实际执行前读取的 align SQL SHA-256：

```text
8a90999e060cfa38de991d2c36c75d18613e18efe5c13054f0fedb3a2e63bafa
```

归档时工作区脚本已被其他代理继续最小修复，当前本地 SHA-256 为：

```text
2688292af5dcdd5b59acf3dab0cd08a9648dbd6047abab1febcf8da8b2cb51f7
```

归档后的新哈希未被本轮执行，不能被解读为本次失败的输入版本。
