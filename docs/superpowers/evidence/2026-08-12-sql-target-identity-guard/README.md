# SQL target-identity guard 与受控 runner 静态证据

本目录的新增记录均由 `docs/superpowers/scripts/tests/record_static_evidence.py` 在本地生成：每份原始
记录含 UTC 起止时间、实际命令、退出码和输入 SHA-256。全程未连接数据库、未执行 SQL、未启动服务或
进行网络访问。

`mutation-red/03-legacy-acceptance-final-fixtures.raw.txt` 是第八轮的 Red 基线：SHA-256 为
`6ce2129d…` 的文本 checker 对当时 10/10 fixture 错误返回 0。`00-fixture-anchor-validation.raw.txt`
保留了 fixture 开发时替换锚点不唯一的早期 Red，未被删改。

第九轮新增 8 个实际 mutation：三类 `/*!80000 DDL/DML/CALL */`、完整批准条件外包后追加
`AND 1=0`、`SET @error = NULL`、`SELECT NULL INTO @error`、交换 hard-stop IF 成功/失败参数，以及
`--x` 非注释语义。`mutation-red/04-round9-current-checker-unexpected-accept.raw.txt` 证明第九轮开始前的
Python checker 对这新增 **8/8** 均错误返回 0；`mutation-red/05-round9-legacy-checker-accepts-all-fixtures.raw.txt`
证明更早文本 checker 对累计 **18/18** 均错误返回 0。两份均含 UTC、实际命令、退出码、checker、fixture
和三份原 SQL SHA-256。

`mutation-green/01-real-sql-structural-check.raw.txt` 与
`mutation-green/02-mutation-fixtures-rejected.raw.txt` 是第八轮的 3/3 与 10/10 Green 快照。
第九轮权威 Green 为 `mutation-green/03-round9-real-sql-restricted-grammar.raw.txt`（真实 SQL **3/3 PASS**）
和 `mutation-green/04-round9-all-18-mutations-rejected.raw.txt`（mutation **18/18 fail-close**）。
当前 checker 明确是仅覆盖三份批准脚本的受限 MySQL grammar，不是通用 MySQL parser：它拒绝全部
可执行/version 注释，按 MySQL 空白/control 规则处理 `--`，精确比较前三个 identity WHEN 的批准原子
OR 集合，追踪 error 的 SET/SELECT INTO 写入，精确验证 IF 三参数位置，并禁止首 guard EXECUTE 前的
DDL/DML/CALL 或额外 PREPARE/EXECUTE。

`runner-red/01-runner-tdd-red.raw.txt` 是 runner 不存在时的真实 TDD Red；
`runner-red/03-ambiguous-mode-tdd-red-final.raw.txt` 记录了随后发现的真实参数互斥缺陷：旧 runner 会让
`--validate-only --execute` 进入执行路径。修复后的权威 Green 为
`runner-green/03-fake-mysql-static-contract-final-after-mode-fix.raw.txt`：它使用临时 RSA 密钥、只读签名
manifest 与 fake `mysql`，证明默认/`--validate-only` 不启动客户端、显式执行固定
`--batch --skip-reconnect`、单一进程/双跑顺序、六变量及原 manifest SHA 注入、拒绝歧义模式、
`--force`/CLI 覆盖/defaults 中 force/坏签名，以及非零退出不重试。fake client 不会打开网络连接或执行 SQL。

第九轮 runner 信任边界的 TDD Red/Green 分别为
`runner-round9-red/01-trust-hardening-tdd-red.txt` 与
`runner-round9-green/01-trust-hardening-green.txt`。后者以纯单元 mock 验证绝对解释器和工具 pin、
受信目录元数据、单次审计读取、严格 manifest/defaults/receipt 与固定双跑；并真实调用入口证明当前仓库
没有受控部署根时 `--execute` 在 helper/mysql 之前以 64 fail-close。因此此前 fake-client 记录仅保留为
历史静态契约证据，不能表示当前仓库可执行 SQL。

历史根目录的 `RED.raw.txt` 和 `GREEN.raw.txt` 是加固前文本检查器的早期证据，不应被误解为本轮结构
解析结论；本 README 列出的子目录记录才是本轮最终证据链。
