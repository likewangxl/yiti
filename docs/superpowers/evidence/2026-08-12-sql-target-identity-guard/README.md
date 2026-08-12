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

第十轮补齐目标 `@*_preflight_error` 的 `SELECT ... :=`、`DO ... :=` 与嵌套表达式赋值追踪，
同时禁止 mysql client `SOURCE`、`\\.`、`SYSTEM`、`\\!`，并将过程体外顶层 statement 与
`DELIMITER` 收紧为三份实际脚本所需的受限 grammar。Red
`mutation-red/06-round10-current-checker-unexpected-accept.raw.txt` 证明修复前新增 **10/10** 均被错误放行；
权威 Green 为 `mutation-green/05-round10-real-sql-client-command-grammar.raw.txt`（真实 SQL **3/3 PASS**）和
`mutation-green/06-round10-all-28-mutations-rejected.raw.txt`（累计 **28/28 fail-close**）。三份记录都含 UTC、
实际命令、退出码和参与文件 SHA-256，且仅执行静态 checker/文本 fixture，不连接数据库、不执行 SQL。

历史根目录的 `RED.raw.txt` 和 `GREEN.raw.txt` 是加固前文本检查器的早期证据，不应被误解为本轮结构
解析结论；本 README 列出的子目录记录才是本轮最终证据链。

第十轮 runner 启动链、在线 receipt claim、A2 proof 和 mysql 单进程约束的 TDD Red 为
`runner-round10-red/01-launcher-claim-a2-mysql-tdd-red.raw.txt`：新增覆盖在旧实现上得到 **7 failures + 9 errors**，
即 **16** 个未闭合缺口。开发过程保留在 `runner-round10-development/`；权威 Green 是
`runner-round10-green/01-runner-unit-tests.raw.txt`，纯 mock 单元测试 **36/36 PASS**，不创建 socket，
不调用真实 mysql、systemd、网络或数据库。它覆盖 shell execute 永久拒绝、unit/净化环境静态契约、
root launcher sealed PID/starttime proof、在线 claim 成功/已消费/撤销/断线、A2 重复键/签名/目标/状态/绑定、
以及完整 mock 子进程序列中仅有 checker、claim helper 与一个 mysql，且没有 mysql version/help 探测。

同目录的 `02-python-syntax.raw.txt`、`03-shell-syntax.raw.txt`、`04-json-contracts.raw.txt`、
`05-static-execute-contract.raw.txt` 和 `07-diff-check.raw.txt` 分别记录 Python/Shell 语法、9 个 JSON
协议/fixture、双轮/无 mysql probe/固定 unit 契约和目标文件 diff 检查。`06-shell-execute-rejected.raw.txt`
真实执行普通 shell `--execute`，返回预期 **64**，在 Python、claim helper 和 mysql 之前拒绝。

生产 execute 仍以仓库外 root 部署、root-only key/pin、安装的 unit、service account 和在线原子 claim
服务为信任边界；这些条件缺失时设计上必须 fail-close，不以本地 fixture 或本记录替代真实审批服务。
