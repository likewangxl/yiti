# yiti_test 大屏范围 SQL 独立只读复核

复核日期：2026-08-12（Asia/Shanghai）

## 结论

**总体结论：同意数据库阶段通过，但归档证据存在两项非阻断缺口和一项摘要不一致。**

- 当前 `yiti_test` 终态达到本轮数据库验收要求：三份 SQL 哈希匹配；7 张目标表的全部 90 个列契约和 23 个目标索引契约均匹配；资源、授权、机构组壳、草稿屏、复合地图、白名单、数据源条线、业务键唯一性和临时过程清理均通过独立查询。
- 历史执行证据内部的成功输出与最终状态一致，但 `02-exit-codes.md` 和 `04-execution-output.raw.md` 没有保存 shell 可机读退出码捕获行，因此本复核只能确认“归档声称的退出码与各输出节标题/内容一致”，不能从 stdout/stderr 本身独立重建历史 exit code。
- “首次 seed 增量”和“复跑后聚合数量未增加”可由归档的前、后聚合快照支持；“执行前确为该状态”和“复跑零行级变化/零 checksum diff”属于历史事实，不能由当前数据库终态单独证明，且归档没有行级/checksum diff。
- `README.md` 称本轮输出 8 条 `BINARY expr` deprecation warning，但 `04-execution-output.raw.md` 实际包含 32 条（4 个成功 `SOURCE` 区段各 8 条）。这是摘要计数不一致，不影响 SQL 成功路径或当前数据库正确性。
- **`yiti` 仍未授权。** 本结论仅覆盖当前 `127.0.0.1:3306/yiti_test` 的数据库阶段，不构成应用启动、API 联调、无 mock Playwright 验收或向 `yiti` 执行 SQL 的授权。

## 复核边界和只读保证

本复核完整读取了根 `AGENTS.md`、`docs/AGENTS.md` 指向的 `docs/CLAUDE.md`、交接文件 `/tmp/yiti-screen-scope-handoff-2026-08-12.md`、三份目标 SQL 及本目录原有全部文件。

数据库侧只执行 `SELECT`（包括 `information_schema` 和 `JSON_TABLE` 只读聚合）；没有执行 DDL、DML、`SET`、过程调用、应用启动或任何 `yiti` 查询/写入。连接后的第一条数据库查询独立、单独执行且只有四个身份字段：

```sql
SELECT DATABASE() AS database_name,
       @@server_uuid AS server_uuid,
       @@hostname AS hostname,
       @@port AS port;
```

结果为：

```text
yiti_test | d3a209c4-42bd-11f1-bb1f-000c299f5629 | ubuntu | 3306
```

四项与 `00-local-manifest.txt` 精确一致，因此才继续后续只读复核。

## 脱敏命令模板

身份和数据库聚合查询使用以下模板；密码未写入证据：

```bash
env MYSQL_PWD='***' mysql --protocol=TCP -h127.0.0.1 -P3306 -uroot \
  --database=yiti_test --default-character-set=utf8mb4 \
  --batch --raw --show-warnings -e '<SELECT only>'
```

多条聚合使用同样连接参数及只含 `WITH ... SELECT ...` 的标准输入：

```bash
env MYSQL_PWD='***' mysql --protocol=TCP -h127.0.0.1 -P3306 -uroot \
  --database=yiti_test --default-character-set=utf8mb4 \
  --batch --raw --show-warnings <<'SQL'
<SELECT-only aggregate queries>
SQL
```

本地文件校验使用：

```bash
sha256sum \
  docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql \
  docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql \
  docs/superpowers/sql/2026-08-11-screen-scope-map-seed.sql
```

证据敏感信息扫描使用 `rg` 检查明文密码、`MYSQL_PWD` 非掩码值、secret/token、私钥头和常见 JWT 形态；只输出命中文件名，没有读取或展示秘密值。

## 独立命令退出码

| 独立检查 | 退出码 | 结果 |
| --- | ---: | --- |
| 首条目标身份 `SELECT` | 0 | PASS，四项身份精确匹配。 |
| 7 表列数、39 个关键列和 23 个目标索引聚合 | 0 | PASS；39/39、23/23。该批出现两条 `GROUP BY` 列名歧义 warning，不影响结果；随后完整列复核未依赖该歧义写法。 |
| 资源、授权、机构组、屏/JSON/地图、白名单、数据源、重复键、过程残留聚合 | 0 | PASS。 |
| 7 表全部 90 个列定义的独立逐列契约聚合 | 0 | PASS，90/90。 |
| 三份 SQL 与本地 manifest 哈希重算、归档一致性和敏感信息扫描 | 0 | PASS；另发现 warning 摘要计数不一致，见下文。 |

## 逐项复核

### 1. SQL 文件与归档哈希：PASS

| 文件 | 独立重算 SHA-256 | 与 `01-sql-hashes.txt`/manifest |
| --- | --- | --- |
| `2026-08-11-auth-org-profile-group.sql` | `900407245a163246939b1cba15606c68a612e3093fd700b3c3c6a713b8d86554` | 匹配 |
| `2026-08-11-screen-scope-map-align.sql` | `2688292af5dcdd5b59acf3dab0cd08a9648dbd6047abab1febcf8da8b2cb51f7` | 匹配 |
| `2026-08-11-screen-scope-map-seed.sql` | `5c1b22ecdd8c74480e0607237d58316a71e762383cab1dead4c7cfe20798d500` | 匹配 |

`00-local-manifest.txt` 独立重算为 `36561c45410287fe9981a563bd081116a38c049dc1eb517c3758b89a28c381a5`，与 `03-command-templates.md` 中设置的 manifest SHA 匹配。

### 2. 历史退出码和原始输出：PARTIAL PASS（证据缺口）

- `02-exit-codes.md` 的 8 个步骤与 `04-execution-output.raw.md` 的 8 个编号区段一一对应；各区段标题中的 exit 值与退出码表一致。
- 不兼容选项区段保存了 `unknown option '--abort-source-on-error'`，与“客户端参数解析失败、exit 2”相符。
- seed 首次、auth/align/seed 复跑区段均保存了正确目标身份、硬停止门 passed、脚本最终查询结果；没有 SQL error，内容与声称的成功路径一致。
- **缺口：** `04-execution-output.raw.md` 自称“摘录”，且没有 `EXIT_CODE=...`、shell 状态捕获或逐步命令原文。因此历史 exit 0/2 是归档声明，不能只凭该 stdout/stderr 摘录独立重建。
- **不一致：** 原始摘录共有 32 条 Code 1287 warning；`README.md` 的“输出 8 条”若指本轮总数则不正确。正确表述应是 4 个成功 `SOURCE` 区段各 8 条、合计 32 条。

### 3. 七张目标表列与索引：PASS

独立 CTE 将三份 SQL 中的全部列契约逐列与 `information_schema.columns` 比较，包括类型、字符长度、`DECIMAL(10,6)` 精度/小数位、NULL、default、自动增长/自动更新时间和生成列表达式关键语义：

| 表 | 列契约匹配 | 目标索引匹配 |
| --- | ---: | ---: |
| `PT_ORG_PROFILE` | 16/16 | 4/4 |
| `PT_ORG_GROUP` | 11/11 | 3/3 |
| `PT_ORG_GROUP_MEMBER` | 9/9 | 4/4 |
| `PT_ROLE_ORG_GROUP` | 9/9 | 4/4 |
| `RPT_SCREEN` | 22/22 | 3/3 |
| `RPT_SCREEN_DATASOURCE` | 15/15 | 2/2 |
| `RPT_SCREEN_ACCESS_ROLE` | 8/8 | 3/3 |

合计列契约 90/90、目标索引 23/23。`active_screen_code` 为 nullable `VARCHAR(64)` STORED GENERATED，表达式引用 `screen_code` 和 `deleted`；活跃编码唯一键、范围索引、数据源条线索引及屏白名单唯一/状态索引均匹配列序和唯一性。

### 4. 四个 REPORT 资源：PASS

独立按 `RESOURCE_ID` 同时核对 URL、METHOD、SYS_CODE，4/4 精确匹配、0 mismatch：

| RESOURCE_ID | URL | METHOD | SYS_CODE |
| --- | --- | --- | --- |
| `R_RPT_SCR_AR_LIST` | `/api/screen/admin/screens/*/access-roles` | GET | RPT |
| `R_RPT_SCR_AR_SAVE` | `/api/screen/admin/screens/*/access-roles` | PUT | RPT |
| `R_RPT_SCR_META_SAVE` | `/api/screen/admin/screens/*/metadata` | PUT | RPT |
| `R_RPT_SCR_DS_PROBE` | `/api/screen/admin/datasources/*/probe-columns` | POST | RPT |

### 5. 角色资源绑定：PASS

目标绑定总数 9，重复 `(ROLE_ID, RESOURCE_ID)` 对为 0：

| 资源 | 绑定数 | 独立复核要点 |
| --- | ---: | --- |
| AR_LIST | 4 | `SYS_ADMIN`、`FINANCE_LEADER`、`R_2FAB45A1`、`BACK_FINANCE` |
| AR_SAVE | 1 | 仅 `SYS_ADMIN`；非管理员绑定 0 |
| META_SAVE | 4 | 与已归档分布一致 |
| DS_PROBE | 0 | 默认未授权 |

### 6. 机构组壳：PASS

- 两个目标组均存在、`REPORT_SCREEN`/`ACTIVE` 业务身份正确，身份 mismatch 为 0。
- 两组的 `PT_ORG_GROUP_MEMBER` 目标成员总数为 0。
- 两组的 `PT_ROLE_ORG_GROUP` 目标绑定总数为 0。

这与 seed “只建壳，不自动推导成员或角色组授权”的边界一致，不应误报为已完成业务成员配置。

### 7. 两个草稿屏和复合地图：PASS

- 两个活跃目标屏均存在；对公屏为 `CORP / NAMED_GROUP / ORG_GRP_CORP_DEPARTMENTS`，零售屏为 `RETAIL / NAMED_GROUP / ORG_GRP_PRIMARY_OPERATING_UNITS`，scope mismatch 为 0。
- 两屏 `canvas_style_json` 和 `canvas_draft_json` 均为合法 JSON，2/2。
- 两屏均为干净未发布草稿：`publish_status=0`、`canvas_version=0`、`canvas_published_json IS NULL`、`published_at IS NULL`、`published_by IS NULL`，2/2。
- 零售屏精确命中 `MapCenter / XIAN_COMPOSITE / XIAN_OUTLINE / cityCode=610100 / operatingLevel=PRIMARY`，1/1。
- `JSON_TABLE` 独立展开得到 4 个节点，且 `128/LEFT`、`191/RIGHT`、`169/TOP`、`129/FAR_TOP` 均精确指向 `SCR_BRANCH`；4/4，无重复节点键。

### 8. 屏级白名单及角色映射：PASS

- 当前目标屏白名单实际 2 对，预期活跃对 2/2、缺失 0、非预期 0、重复 0。
- `SCR_CORP_OVERVIEW → R_SCREEN_CORP_VIEWER`、`SCR_RETAIL_OVERVIEW → R_SCREEN_RETAIL_VIEWER` 均可映射到唯一、启用、数字 `ROLE_ID`；2/2。

### 9. 数据源 biz_line：PASS

`RPT_SCREEN_DATASOURCE` 当前共 11 行；`biz_line='COMMON'` 为 11 行，NULL、空白或非 COMMON 为 0。

### 10. 目标业务键重复与临时过程：PASS

- 目标活跃 `screen_code` 重复键：0。
- 目标 `group_code` 重复键：0。
- 目标大屏 `role_code` 重复键：0。
- 四项资源的 URL/METHOD/SYS_CODE 目标身份重复键：0。
- 屏白名单重复对：0；角色资源重复对：0。
- 三个临时过程名在 `information_schema.routines` 中残留：0。

### 11. 敏感信息泄漏：PASS

本目录未发现明文数据库密码、未掩码 `MYSQL_PWD`、secret/token、私钥或常见 JWT。命令模板使用 `MYSQL_PWD='***'`。归档包含数据库名、主机名、server UUID、端口、资源 URL、角色编码和非人员角色 ID；这些是本地审计所需运行元数据，不是认证秘密。未发现人员、客户或业务明细导出。

## 执行前后 diff 的可证边界

### 当前状态可直接证明

- 当前目标实例身份及全部终态契约。
- 当前两组、两屏、两白名单的数量与内容口径。
- 当前四资源、9 个角色资源绑定的分布和零重复。
- 当前 11 个数据源全部为 COMMON。
- 当前不存在目标业务键重复和临时过程残留。

### 只能由归档快照支持的历史事实

- `04-execution-output.raw.md` 的写前快照称两组、两屏、两白名单均为 0；seed 后快照均为 2，因此归档支持首次 seed 的聚合增量 `0→2`。
- 写前快照称四资源为 4、目标角色资源为 9；最终仍为 4/9，因此归档支持这些聚合数量未增加。
- seed 后快照与最终验收在目标组、屏、白名单及资源/授权聚合上相同，因此归档支持“复跑未新增目标行”。

### 归档不能严格证明

- 当前查询不能时间旅行，不能独立证明首次 seed 之前确为 0；该事实依赖已归档写前快照。
- 没有行级前后快照、受控 checksum 或数据库审计日志，故不能把“聚合数量相同”扩大解释成“所有字段/时间戳绝对零变化”。
- 历史 shell exit code 没有机器捕获行，故不能从摘录独立重建。

上述缺口不推翻当前数据库终态和业务约束的独立 PASS，但应在后续对外陈述中把“当前终态已验证”“归档聚合支持幂等”与“行级零 diff 已证明”严格区分；最后一项当前并未被证明。

## 最终判定

| 判定项 | 结论 |
| --- | --- |
| 当前 `yiti_test` 数据库终态 | PASS |
| 三份 SQL 文件身份 | PASS |
| 历史输出与业务结果一致性 | PASS，带退出码可重建性缺口 |
| README warning 数量摘要 | FAIL（32，不是总计 8；非阻断） |
| 聚合级首次写入/复跑证据 | PASS，依赖归档快照 |
| 行级/checksum 零 diff | NOT PROVEN |
| 数据库阶段是否通过 | **同意通过，带上述 caveat** |
| `yiti` 是否获准执行 | **否，仍未授权** |
