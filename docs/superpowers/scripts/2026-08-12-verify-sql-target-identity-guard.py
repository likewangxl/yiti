#!/usr/bin/env python3
"""三份 screen-scope 手工 SQL 的 fail-close 静态结构检查器。

本程序从不连接数据库，也不执行 SQL。它只实现这三份已批准脚本所需的受限 MySQL grammar：
身份 CASE 的前三个 WHEN、append-only preflight error、固定三参数 hard-stop IF、guard
前的 PREPARE/EXECUTE/写语句顺序、所有目标 error 变量赋值上下文，以及过程体外的顶层
statement/mysql client 指令与 DELIMITER 形态。字符串和普通注释不会伪装代码；MySQL
可执行/version 注释及本 grammar 未覆盖的危险形态一律 fail-close。

它不是通用 MySQL parser、SQL 执行器或数据库运行时语义证明。
"""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
import sys


SCRIPT_PATH = Path(__file__).resolve()
REPO_ROOT = SCRIPT_PATH.parents[3]
DEFAULT_SQL_FILES = (
    REPO_ROOT / "docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql",
    REPO_ROOT / "docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql",
    REPO_ROOT / "docs/superpowers/sql/2026-08-11-screen-scope-map-seed.sql",
)
REQUIRED_EXECUTION_COMMENT = "2026-08-12-controlled-sql-batch-runner.sh"
REQUIRED_BARE_CLIENT_BAN_COMMENT = "禁止裸 mysql"

# 受控 batch stdin 不允许任何 mysql client 长命令。DELIMITER 是唯一例外，但仍只能是
# 三份批准脚本逐字需要的两行，交由 ``validate_delimiter_layout`` 再作精确验证。
MYSQL_CLIENT_LONG_COMMANDS = frozenset(
    {
        "CHARSET",
        "CLEAR",
        "CONNECT",
        "EDIT",
        "EGO",
        "EXIT",
        "GO",
        "HELP",
        "NOPAGER",
        "NOTEE",
        "NOWARNING",
        "PAGER",
        "PRINT",
        "PROMPT",
        "QUERY_ATTRIBUTES",
        "QUIT",
        "RECONNECT",
        "REHASH",
        "RESETCONNECTION",
        "SOURCE",
        "SSL_SESSION_DATA_PRINT",
        "STATUS",
        "SYSTEM",
        "TEE",
        "USE",
        "WARNINGS",
    }
)
APPROVED_DELIMITER_LINES = frozenset({"DELIMITER $$", "DELIMITER ;"})


@dataclass(frozen=True)
class Profile:
    """一份 SQL guard 的静态命名契约。"""

    prefix: str
    prepare_name: str
    success_query: str
    procedure_name: str
    cleanup_uses_if_exists: bool

    @property
    def error_variable(self) -> str:
        return f"@{self.prefix}_preflight_error".upper()

    @property
    def guard_variable(self) -> str:
        return f"@{self.prefix}_preflight_guard_sql".upper()


PROFILES = (
    Profile(
        "auth",
        "auth_preflight_guard",
        "SELECT 'AUTH deploy zero-DDL preflight passed' AS auth_preflight_guard",
        "sp_auth_org_profile_group_20260811",
        False,
    ),
    Profile(
        "rpt_align",
        "rpt_align_preflight_guard",
        "SELECT 'REPORT align zero-DDL preflight passed' AS preflight_guard",
        "sp_screen_scope_map_align_20260811",
        True,
    ),
    Profile(
        "rpt_seed",
        "rpt_seed_preflight_guard",
        "SELECT 'REPORT seed zero-DDL preflight passed' AS preflight_guard",
        "sp_screen_scope_map_seed_20260811",
        True,
    ),
)


@dataclass(frozen=True)
class Token:
    """词法 token；WORD 为大写，STRING 保留解码后的文本。"""

    kind: str
    value: str
    line: int
    column: int


@dataclass(frozen=True)
class VariableWrite:
    """受限 grammar 中一个会改写目标用户变量的 token 级赋值上下文。"""

    kind: str
    statement_start: int
    variable_index: int
    operator_index: int | None
    statement_end: int


class LexError(ValueError):
    """无法安全跳过字符串或注释时抛出，调用端必须 fail-close。"""


def _advance_position(fragment: str, line: int, column: int) -> tuple[int, int]:
    newlines = fragment.count("\n")
    if newlines:
        return line + newlines, len(fragment) - fragment.rfind("\n")
    return line, column + len(fragment)


def is_mysql_dash_comment_terminator(character: str) -> bool:
    """MySQL 仅在第二个 dash 后紧跟空白或 control 时开始 ``--`` 注释。"""
    return bool(character) and (ord(character) <= 0x20 or ord(character) == 0x7F)


def lex_sql(source: str) -> list[Token]:
    """为三份固定脚本生成 token；无法落入受限 grammar 的注释形态一律拒绝。"""
    tokens: list[Token] = []
    index = 0
    line = 1
    column = 1
    length = len(source)

    def consume(fragment: str) -> None:
        nonlocal index, line, column
        line, column = _advance_position(fragment, line, column)
        index += len(fragment)

    while index < length:
        current = source[index]
        if current.isspace():
            consume(current)
            continue
        if source.startswith("--", index):
            following = source[index + 2 : index + 3]
            if not is_mysql_dash_comment_terminator(following):
                raise LexError(
                    f"第 {line} 行出现 -- 后未跟空白/control；不是 MySQL 注释，受限 grammar fail-close"
                )
            end = source.find("\n", index)
            consume(source[index : length if end < 0 else end])
            continue
        if current == "#":
            end = source.find("\n", index)
            consume(source[index : length if end < 0 else end])
            continue
        if source.startswith("/*", index):
            end = source.find("*/", index + 2)
            if end < 0:
                raise LexError(f"第 {line} 行存在未闭合块注释")
            if source.startswith(("/*!", "/*+", "/*M!"), index):
                raise LexError(f"第 {line} 行存在 MySQL 可执行/version 注释，受限 grammar fail-close")
            consume(source[index : end + 2])
            continue
        if current in "'\"":
            quote = current
            token_line, token_column = line, column
            index += 1
            column += 1
            value: list[str] = []
            closed = False
            while index < length:
                character = source[index]
                if character == "\\":
                    if index + 1 >= length:
                        raise LexError(f"第 {token_line} 行字符串以反斜杠结束")
                    value.append(source[index + 1])
                    consume(source[index : index + 2])
                    continue
                if character == quote:
                    if index + 1 < length and source[index + 1] == quote:
                        value.append(quote)
                        consume(source[index : index + 2])
                        continue
                    consume(character)
                    closed = True
                    break
                value.append(character)
                consume(character)
            if not closed:
                raise LexError(f"第 {token_line} 行存在未闭合字符串")
            tokens.append(Token("STRING", "".join(value), token_line, token_column))
            continue
        if current == "`":
            token_line, token_column = line, column
            end = source.find("`", index + 1)
            if end < 0:
                raise LexError(f"第 {line} 行存在未闭合反引号标识符")
            value = source[index + 1 : end]
            consume(source[index : end + 1])
            tokens.append(Token("WORD", value.upper(), token_line, token_column))
            continue
        if current.isalpha() or current in "_@$":
            token_line, token_column = line, column
            end = index + 1
            while end < length and (source[end].isalnum() or source[end] in "_@$."):
                end += 1
            value = source[index:end]
            consume(value)
            tokens.append(Token("WORD", value.upper(), token_line, token_column))
            continue
        if current.isdigit():
            token_line, token_column = line, column
            end = index + 1
            while end < length and (source[end].isalnum() or source[end] in "._"):
                end += 1
            value = source[index:end]
            consume(value)
            tokens.append(Token("NUMBER", value, token_line, token_column))
            continue
        token_line, token_column = line, column
        two_character = source[index : index + 2]
        if two_character in {":=", "<>", "<=", ">=", "!=", "||", "&&"}:
            consume(two_character)
            tokens.append(Token("SYMBOL", two_character, token_line, token_column))
            continue
        consume(current)
        tokens.append(Token("SYMBOL", current, token_line, token_column))
    return tokens


def mask_strings_and_ordinary_comments(source: str) -> str:
    """保留代码的行列，遮蔽普通字符串/注释，供 mysql client 行命令扫描使用。

    lexer 已先验证引号和注释能安全闭合；这里仅复用同一边界，使 ``\\r``、``tee`` 等
    出现在字符串或普通注释中时无法伪装成 client 指令。
    """
    masked = list(source)
    index = 0
    length = len(source)

    def hide(start: int, end: int) -> None:
        for position in range(start, end):
            if source[position] not in "\r\n":
                masked[position] = " "

    while index < length:
        current = source[index]
        if source.startswith("--", index):
            following = source[index + 2 : index + 3]
            if is_mysql_dash_comment_terminator(following):
                end = source.find("\n", index)
                end = length if end < 0 else end
                hide(index, end)
                index = end
                continue
        if current == "\\":
            # ``\\#`` 是 mysql client rehash 短命令，不能让后一个 # 被误作 SQL 注释。
            index += 2 if index + 1 < length else 1
            continue
        if current == "#":
            end = source.find("\n", index)
            end = length if end < 0 else end
            hide(index, end)
            index = end
            continue
        if source.startswith("/*", index):
            end = source.find("*/", index + 2)
            end = length if end < 0 else end + 2
            hide(index, end)
            index = end
            continue
        if current in "'\"":
            quote = current
            start = index
            index += 1
            while index < length:
                character = source[index]
                if character == "\\":
                    index += 2 if index + 1 < length else 1
                    continue
                if character == quote:
                    if index + 1 < length and source[index + 1] == quote:
                        index += 2
                        continue
                    index += 1
                    break
                index += 1
            hide(start, index)
            continue
        if current == "`":
            start = index
            end = source.find("`", index + 1)
            index = length if end < 0 else end + 1
            hide(start, index)
            continue
        index += 1
    return "".join(masked)


def first_word_at_client_line_start(masked_line: str) -> str | None:
    """仅识别 mysql client 实际会在物理行首解析的长命令，避免误伤缩进 SQL 片段。"""
    if not masked_line or masked_line[0].isspace() or not (masked_line[0].isalpha() or masked_line[0] == "_"):
        return None
    end = 1
    while end < len(masked_line) and (masked_line[end].isalnum() or masked_line[end] == "_"):
        end += 1
    return masked_line[:end].upper()


def token_values(tokens: list[Token]) -> list[str]:
    """为结构比较生成 token 值；字符串保持独立标记，不能伪装代码。"""
    return [f"<STRING:{token.value}>" if token.kind == "STRING" else token.value for token in tokens]


def contains_sequence(tokens: list[Token], expected: tuple[str, ...]) -> bool:
    values = token_values(tokens)
    width = len(expected)
    return any(tuple(values[index : index + width]) == expected for index in range(len(values) - width + 1))


def first_sequence(tokens: list[Token], expected: tuple[str, ...], start: int = 0) -> int | None:
    values = token_values(tokens)
    width = len(expected)
    for index in range(start, len(values) - width + 1):
        if tuple(values[index : index + width]) == expected:
            return index
    return None


def find_matching_paren(tokens: list[Token], opening: int) -> int | None:
    if tokens[opening].value != "(":
        return None
    depth = 0
    for index in range(opening, len(tokens)):
        value = tokens[index].value
        if value == "(":
            depth += 1
        elif value == ")":
            depth -= 1
            if depth == 0:
                return index
    return None


def statement_end(tokens: list[Token], start: int) -> int:
    """返回当前受限 statement 的分号位置；缺分号返回 token 尾端供后续严格校验拒绝。"""
    for index in range(start, len(tokens)):
        if tokens[index].value == ";":
            return index
    return len(tokens)


def statement_start(tokens: list[Token], index: int) -> int:
    """从一个 token 回溯到所属 statement 的首 token。"""
    cursor = index - 1
    while cursor >= 0 and tokens[cursor].value != ";":
        cursor -= 1
    return cursor + 1


def find_set_writes(tokens: list[Token], variable: str) -> list[VariableWrite]:
    """追踪 SET 语句中所有顶层的 ``@var :=`` 与 ``@var =`` 写入。"""
    target = variable.upper()
    writes: list[VariableWrite] = []
    for start, token in enumerate(tokens):
        if token.value != "SET":
            continue
        end = statement_end(tokens, start)
        depth = 0
        for index in range(start + 1, max(start + 1, end - 1)):
            value = tokens[index].value
            if value == "(":
                depth += 1
                continue
            if value == ")":
                depth -= 1
                continue
            if (
                depth == 0
                and tokens[index].value == target
                and index + 1 < end
                and tokens[index + 1].value in {":=", "="}
            ):
                writes.append(VariableWrite("SET", start, index, index + 1, end))
    return writes


def find_select_into_writes(tokens: list[Token], variable: str) -> list[VariableWrite]:
    """追踪 ``SELECT ... INTO @var``，它与 SET 一样能够清空既有 guard error。"""
    target = variable.upper()
    writes: list[VariableWrite] = []
    for index in range(len(tokens) - 1):
        if tokens[index].value != "INTO" or tokens[index + 1].value != target:
            continue
        start = statement_start(tokens, index)
        if not any(token.value == "SELECT" for token in tokens[start:index]):
            continue
        writes.append(VariableWrite("SELECT_INTO", start, index + 1, None, statement_end(tokens, index)))
    return writes


def find_error_variable_writes(tokens: list[Token], variable: str) -> list[VariableWrite]:
    """追踪目标 error 变量的全部可执行赋值上下文。

    MySQL 用户变量的 ``:=`` 可以出现在 ``SELECT``、``DO`` 和嵌套表达式中，不能只看
    ``SET`` 的最外层。``=`` 仅在 ``SET`` 的直接赋值位有赋值语义，因此保留
    :func:`find_set_writes` 的受限识别；``SELECT ... INTO`` 也单独计入。
    """
    direct_set_writes = find_set_writes(tokens, variable)
    direct_set_by_index = {write.variable_index: write for write in direct_set_writes}
    writes = list(direct_set_writes)
    target = variable.upper()
    for index in range(len(tokens) - 1):
        if tokens[index].value != target or tokens[index + 1].value != ":=":
            continue
        if index in direct_set_by_index:
            continue
        writes.append(
            VariableWrite(
                "EXPRESSION_ASSIGNMENT",
                statement_start(tokens, index),
                index,
                index + 1,
                statement_end(tokens, index),
            )
        )
    writes.extend(find_select_into_writes(tokens, variable))
    return sorted(writes, key=lambda write: (write.variable_index, write.kind))


def find_exact_statement(
    tokens: list[Token], expected: tuple[str, ...], start: int = 0
) -> int | None:
    """查找恰好由 expected 构成的单条 statement，而不是子串命中。"""
    for index in range(start, len(tokens)):
        if tokens[index].value != expected[0]:
            continue
        end = statement_end(tokens, index)
        if tuple(token_values(tokens[index:end])) == expected:
            return index
    return None


def find_matching_case(tokens: list[Token], opening: int, limit: int) -> int | None:
    """在初始 ``SELECT CASE`` 中匹配对应的 END；嵌套 CASE 必须平衡。"""
    depth = 0
    for index in range(opening, limit):
        value = tokens[index].value
        if value == "CASE":
            depth += 1
        elif value == "END":
            depth -= 1
            if depth == 0:
                return index
    return None


def scan_top_level_until(
    tokens: list[Token], start: int, limit: int, stop_values: frozenset[str]
) -> int:
    """在括号平衡层级为零时寻找 CASE 子句分隔关键字。"""
    depth = 0
    for index in range(start, limit):
        value = tokens[index].value
        if value == "(":
            depth += 1
        elif value == ")":
            depth -= 1
            if depth < 0:
                raise ValueError(f"第 {tokens[index].line} 行括号层级异常")
        elif depth == 0 and value in stop_values:
            return index
    if depth != 0:
        raise ValueError("CASE 子句中的括号未闭合")
    return limit


def parse_initial_case_branches(tokens: list[Token], initial: VariableWrite) -> list[tuple[list[Token], list[Token]]]:
    """精确解析首个 ``SET @error := (SELECT CASE ...)`` 中前三个身份 WHEN 分支。

    三份脚本的后续业务 preflight CASE 很长，且不属于 target identity grammar；本检查器只把
    固定的前三个身份分支纳入严格语法域，不能借此宣称整个 SQL 是通用可解析 MySQL。
    """
    if initial.kind != "SET" or initial.operator_index is None or tokens[initial.operator_index].value != ":=":
        raise ValueError("首个 preflight error 必须使用 SET @error := (SELECT CASE ...)")
    expression = initial.operator_index + 1
    if initial.statement_end >= len(tokens):
        raise ValueError("首个 preflight error 赋值缺少分号")
    if tuple(token_values(tokens[expression : expression + 3])) != ("(", "SELECT", "CASE"):
        raise ValueError("首个 preflight error 必须精确以 (SELECT CASE 开始")
    case_index = expression + 2
    branches: list[tuple[list[Token], list[Token]]] = []
    cursor = case_index + 1
    while len(branches) < 3:
        if cursor >= initial.statement_end:
            raise ValueError("初始身份 CASE 不足三个顶层 WHEN 分支")
        if tokens[cursor].value == "ELSE":
            raise ValueError(f"第 {tokens[cursor].line} 行初始身份 CASE 不允许 ELSE")
        if tokens[cursor].value != "WHEN":
            raise ValueError(f"第 {tokens[cursor].line} 行初始身份 CASE 仅允许顶层 WHEN")
        then_index = scan_top_level_until(tokens, cursor + 1, initial.statement_end, frozenset({"THEN"}))
        if then_index == initial.statement_end:
            raise ValueError(f"第 {tokens[cursor].line} 行 WHEN 缺少 THEN")
        boundary = scan_top_level_until(tokens, then_index + 1, initial.statement_end, frozenset({"WHEN", "ELSE", "END"}))
        if boundary == initial.statement_end:
            raise ValueError("初始身份 CASE 的 WHEN body 后缺少下一分支")
        if tokens[boundary].value in {"ELSE", "END"}:
            raise ValueError(f"第 {tokens[boundary].line} 行初始身份 CASE 不允许 ELSE 或提前 END")
        branches.append((tokens[cursor + 1 : then_index], tokens[then_index + 1 : boundary]))
        cursor = boundary
    return branches


def split_boolean_top_level(condition: list[Token]) -> tuple[list[list[Token]], list[str]]:
    """按最外层 AND/OR 分割受限 identity WHEN；函数调用括号不会被误当连接符。"""
    if not condition:
        raise ValueError("WHEN 条件为空")
    terms: list[list[Token]] = []
    connectors: list[str] = []
    start = 0
    depth = 0
    for index, token in enumerate(condition):
        if token.value == "(":
            depth += 1
        elif token.value == ")":
            depth -= 1
            if depth < 0:
                raise ValueError(f"第 {token.line} 行 WHEN 条件括号不平衡")
        elif depth == 0 and token.value in {"AND", "OR"}:
            if index == start:
                raise ValueError(f"第 {token.line} 行 WHEN 存在空布尔项")
            terms.append(condition[start:index])
            connectors.append(token.value)
            start = index + 1
    if depth != 0 or start == len(condition):
        raise ValueError("WHEN 条件括号未闭合或末尾缺少布尔项")
    terms.append(condition[start:])
    return terms, connectors


def strip_outer_parentheses(tokens: list[Token]) -> list[Token]:
    """仅去除完整包裹整个表达式的括号，供有限静态真假判断使用。"""
    result = tokens
    while len(result) >= 2 and result[0].value == "(":
        closing = find_matching_paren(result, 0)
        if closing != len(result) - 1:
            break
        result = result[1:-1]
    return result


def literal_boolean(tokens: list[Token]) -> bool | None:
    values = token_values(strip_outer_parentheses(tokens))
    if values == ["1"] or values == ["TRUE"]:
        return True
    if values == ["0"] or values == ["FALSE"]:
        return False
    return None


def static_comparison_truth(tokens: list[Token]) -> bool | None:
    values = token_values(strip_outer_parentheses(tokens))
    if len(values) != 3 or values[1] not in {"=", "!=", "<>"}:
        return None
    left = literal_boolean([Token("NUMBER", values[0], 0, 0)]) if values[0] in {"0", "1"} else None
    right = literal_boolean([Token("NUMBER", values[2], 0, 0)]) if values[2] in {"0", "1"} else None
    if values[0] in {"TRUE", "FALSE"}:
        left = values[0] == "TRUE"
    if values[2] in {"TRUE", "FALSE"}:
        right = values[2] == "TRUE"
    if left is None or right is None:
        return None
    equal = left == right
    return equal if values[1] == "=" else not equal


def expression_is_static_false(tokens: list[Token]) -> bool:
    """仅证明有限常量布尔子树为假；无法证明时返回 False 而非猜测。"""
    reduced = strip_outer_parentheses(tokens)
    literal = literal_boolean(reduced)
    if literal is not None:
        return not literal
    comparison = static_comparison_truth(reduced)
    if comparison is not None:
        return not comparison
    if reduced and reduced[0].value == "NOT":
        return expression_is_static_true(reduced[1:])
    try:
        terms, connectors = split_boolean_top_level(reduced)
    except ValueError:
        return False
    if "OR" in connectors:
        return all(expression_is_static_false(term) for term in terms)
    if "AND" in connectors:
        return any(expression_is_static_false(term) for term in terms)
    return False


def expression_is_static_true(tokens: list[Token]) -> bool:
    """与 ``expression_is_static_false`` 配对，仅覆盖常量子树。"""
    reduced = strip_outer_parentheses(tokens)
    literal = literal_boolean(reduced)
    if literal is not None:
        return literal
    comparison = static_comparison_truth(reduced)
    if comparison is not None:
        return comparison
    if reduced and reduced[0].value == "NOT":
        return expression_is_static_false(reduced[1:])
    try:
        terms, connectors = split_boolean_top_level(reduced)
    except ValueError:
        return False
    if "OR" in connectors:
        return any(expression_is_static_true(term) for term in terms)
    if "AND" in connectors:
        return all(expression_is_static_true(term) for term in terms)
    return False


def contains_static_false_element(tokens: list[Token]) -> bool:
    """拒绝 ``A OR 1=0`` 和 ``(A) AND 1=0`` 等任意位置的死布尔项。"""
    if expression_is_static_false(tokens):
        return True
    reduced = strip_outer_parentheses(tokens)
    try:
        terms, connectors = split_boolean_top_level(reduced)
    except ValueError:
        return False
    return bool(connectors) and any(contains_static_false_element(term) for term in terms)


def validate_identity_condition(
    violations: list[str],
    path: Path,
    branch_name: str,
    condition: list[Token],
    required: tuple[tuple[str, ...], ...],
) -> None:
    """要求 identity WHEN 为批准原子条件的精确 OR 集合，不接受额外条件或替换。"""
    if contains_static_false_element(condition):
        line = condition[0].line if condition else "未知"
        violations.append(f"{path}: {branch_name}为静态死分支（第 {line} 行）")
        violations.append(f"{path}: {branch_name}包含静态恒假项，禁止以 AND/OR 拼接死条件")
    try:
        terms, connectors = split_boolean_top_level(condition)
    except ValueError as error:
        violations.append(f"{path}: {branch_name}条件集合/连接结构解析失败：{error}")
        return
    if any(connector != "OR" for connector in connectors):
        violations.append(f"{path}: {branch_name}条件集合/连接结构必须仅以 OR 连接批准原子条件")
    actual = Counter(tuple(token_values(term)) for term in terms)
    expected = Counter(required)
    missing = list((expected - actual).elements())
    if missing:
        violations.append(f"{path}: {branch_name}缺少可执行条件：{' | '.join(' '.join(item) for item in missing)}")
    extra = list((actual - expected).elements())
    if extra:
        violations.append(
            f"{path}: {branch_name}条件集合/连接结构含额外 OR 条件：{' | '.join(' '.join(item) for item in extra)}"
        )


def has_target_identity_message(body: list[Token]) -> bool:
    """前三个身份分支必须直接产生既有 target-identity 错误文本。"""
    return len(body) == 1 and body[0].kind == "STRING" and "target identity guard:" in body[0].value


APPROVAL_CONDITIONS = (
    ("@APPROVED_TARGET_SERVER_UUID", "IS", "NULL"),
    ("CHAR_LENGTH", "(", "TRIM", "(", "@APPROVED_TARGET_SERVER_UUID", ")", ")", "=", "0"),
    ("@APPROVED_TARGET_HOSTNAME", "IS", "NULL"),
    ("CHAR_LENGTH", "(", "TRIM", "(", "@APPROVED_TARGET_HOSTNAME", ")", ")", "=", "0"),
    ("@APPROVED_TARGET_PORT", "IS", "NULL"),
    ("CHAR_LENGTH", "(", "TRIM", "(", "@APPROVED_TARGET_PORT", ")", ")", "=", "0"),
    ("@APPROVED_TARGET_SCHEMA", "IS", "NULL"),
    ("CHAR_LENGTH", "(", "TRIM", "(", "@APPROVED_TARGET_SCHEMA", ")", ")", "=", "0"),
    ("@APPROVED_CHANGE_TICKET", "IS", "NULL"),
    ("CHAR_LENGTH", "(", "TRIM", "(", "@APPROVED_CHANGE_TICKET", ")", ")", "=", "0"),
    ("@APPROVED_MANIFEST_SHA256", "IS", "NULL"),
    ("CHAR_LENGTH", "(", "TRIM", "(", "@APPROVED_MANIFEST_SHA256", ")", ")", "=", "0"),
    ("@APPROVED_MANIFEST_SHA256", "NOT", "REGEXP", "<STRING:^[0-9A-Fa-f]{64}$>"),
)
ACTUAL_CONDITIONS = (
    ("@@SERVER_UUID", "IS", "NULL"),
    ("CHAR_LENGTH", "(", "TRIM", "(", "@@SERVER_UUID", ")", ")", "=", "0"),
    ("@@HOSTNAME", "IS", "NULL"),
    ("CHAR_LENGTH", "(", "TRIM", "(", "@@HOSTNAME", ")", ")", "=", "0"),
    ("@@PORT", "IS", "NULL"),
    ("CHAR_LENGTH", "(", "TRIM", "(", "CAST", "(", "@@PORT", "AS", "CHAR", ")", ")", ")", "=", "0"),
    ("DATABASE", "(", ")", "IS", "NULL"),
    ("CHAR_LENGTH", "(", "TRIM", "(", "DATABASE", "(", ")", ")", ")", "=", "0"),
)
MATCH_CONDITIONS = (
    ("BINARY", "@APPROVED_TARGET_SERVER_UUID", "<>", "BINARY", "@@SERVER_UUID"),
    ("BINARY", "@APPROVED_TARGET_HOSTNAME", "<>", "BINARY", "@@HOSTNAME"),
    ("BINARY", "@APPROVED_TARGET_PORT", "<>", "BINARY", "CAST", "(", "@@PORT", "AS", "CHAR", ")"),
    ("BINARY", "@APPROVED_TARGET_SCHEMA", "<>", "BINARY", "DATABASE", "(", ")"),
)
WRITE_KEYWORDS = frozenset(
    {
        "ALTER",
        "ANALYZE",
        "CALL",
        "CHECK",
        "CREATE",
        "DELETE",
        "DROP",
        "FLUSH",
        "GRANT",
        "INSTALL",
        "INSERT",
        "LOAD",
        "LOCK",
        "OPTIMIZE",
        "PURGE",
        "REPAIR",
        "RENAME",
        "RESET",
        "REPLACE",
        "REVOKE",
        "TRUNCATE",
        "UNLOCK",
        "UNINSTALL",
        "UPDATE",
    }
)


def is_write_token(tokens: list[Token], index: int) -> bool:
    """区分 ``REPLACE()`` 标量函数和 MySQL 的 ``REPLACE [INTO]`` 写语句。"""
    keyword = tokens[index].value
    if keyword not in WRITE_KEYWORDS:
        return False
    if keyword == "REPLACE":
        return index + 1 >= len(tokens) or tokens[index + 1].value != "("
    return True


def is_exact_null_set(tokens: list[Token], write: VariableWrite) -> bool:
    """判断一个 SET 是否直接把 error 置 NULL，以保留可读的 fail-close 诊断。"""
    if write.operator_index is None:
        return False
    return token_values(tokens[write.operator_index + 1 : write.statement_end]) == ["NULL"]


def is_append_only_error_extension(tokens: list[Token], write: VariableWrite, profile: Profile) -> bool:
    """只允许 guard 前现有的 ``@error := COALESCE(@error, (SELECT CASE ...))`` 链。"""
    if write.kind != "SET" or write.operator_index is None or tokens[write.operator_index].value != ":=":
        return False
    start = write.operator_index + 1
    expected_prefix = ("COALESCE", "(", profile.error_variable, ",", "(", "SELECT", "CASE")
    if tuple(token_values(tokens[start : start + len(expected_prefix)])) != expected_prefix:
        return False
    closing = find_matching_paren(tokens, start + 1)
    return closing == write.statement_end - 1


def validate_error_write_flow(
    violations: list[str],
    path: Path,
    tokens: list[Token],
    profile: Profile,
    initial: VariableWrite,
    guard: VariableWrite,
) -> None:
    """追踪 error 的全部赋值上下文，禁止 guard 计算前后把失败状态重新写成成功。"""
    writes = find_error_variable_writes(tokens, profile.error_variable)
    if not writes:
        violations.append(f"{path}: 缺少 {profile.error_variable} 的可追踪初始赋值")
        return
    if writes[0] != initial:
        violations.append(f"{path}: 初始身份 CASE 前不允许写入 {profile.error_variable}（第 {writes[0].variable_index} token）")
    for write in writes:
        if write == initial:
            continue
        line = tokens[write.variable_index].line
        if write.kind == "SELECT_INTO":
            phase = "guard 后" if write.variable_index > guard.variable_index else "guard 前"
            violations.append(f"{path}: {phase}不允许 SELECT ... INTO {profile.error_variable}（第 {line} 行）")
            continue
        if write.variable_index > guard.variable_index:
            violations.append(f"{path}: hard-stop guard 后不允许重写 {profile.error_variable}（第 {line} 行）")
            continue
        if write.kind == "EXPRESSION_ASSIGNMENT":
            violations.append(f"{path}: guard 前不允许重写 {profile.error_variable}（第 {line} 行）")
            continue
        if not is_append_only_error_extension(tokens, write, profile):
            if is_exact_null_set(tokens, write):
                violations.append(f"{path}: guard 执行前重置 {profile.error_variable} 为 NULL（第 {line} 行）")
            violations.append(f"{path}: guard 前不允许重写 {profile.error_variable}（第 {line} 行）")


def split_top_level_arguments(tokens: list[Token], opening: int, closing: int) -> list[list[Token]]:
    """拆分一个函数调用的顶层逗号参数；缺项或括号异常均拒绝。"""
    arguments: list[list[Token]] = []
    start = opening + 1
    depth = 0
    for index in range(opening + 1, closing):
        value = tokens[index].value
        if value == "(":
            depth += 1
        elif value == ")":
            depth -= 1
            if depth < 0:
                raise ValueError(f"第 {tokens[index].line} 行 IF 参数括号异常")
        elif value == "," and depth == 0:
            if index == start:
                raise ValueError(f"第 {tokens[index].line} 行 IF 存在空参数")
            arguments.append(tokens[start:index])
            start = index + 1
    if depth != 0 or start == closing:
        raise ValueError("IF 参数括号未闭合或末尾缺少参数")
    arguments.append(tokens[start:closing])
    return arguments


def expected_failure_argument(profile: Profile) -> tuple[str, ...]:
    """三份固定脚本共用的 UUID 不存在对象 hard-stop 失败参数 grammar。"""
    return (
        "CONCAT",
        "(",
        f"<STRING:SELECT * FROM __{profile.prefix}_preflight_stop_>",
        ",",
        "REPLACE",
        "(",
        "UUID",
        "(",
        ")",
        ",",
        "<STRING:->",
        ",",
        "<STRING:>",
        ")",
        ",",
        "<STRING:__>",
        ")",
    )


def validate_guard_if(
    violations: list[str], path: Path, tokens: list[Token], profile: Profile, guard: VariableWrite
) -> None:
    """精确验证 IF(condition, success-select, uuid-missing-object) 三参数位置。"""
    if guard.operator_index is None or tokens[guard.operator_index].value != ":=":
        violations.append(f"{path}: hard-stop guard 必须使用 := IF(...) 赋值")
        return
    start = guard.operator_index + 1
    if start + 1 >= guard.statement_end or tokens[start].value != "IF" or tokens[start + 1].value != "(":
        violations.append(f"{path}: hard-stop IF 缺少左括号")
        return
    closing = find_matching_paren(tokens, start + 1)
    if closing is None or closing != guard.statement_end - 1:
        violations.append(f"{path}: hard-stop IF 必须是完整且唯一的三参数表达式")
        return
    try:
        arguments = split_top_level_arguments(tokens, start + 1, closing)
    except ValueError as error:
        violations.append(f"{path}: hard-stop IF 参数解析失败：{error}")
        return
    if len(arguments) != 3:
        violations.append(f"{path}: hard-stop IF 必须精确含条件/成功/失败三个参数，实际 {len(arguments)} 个")
        return
    condition, success, failure = arguments
    if token_values(condition) != [profile.error_variable, "IS", "NULL"]:
        violations.append(
            f"{path}: hard-stop IF 条件必须精确为 {profile.error_variable} IS NULL，实际为 {' '.join(token_values(condition))}"
        )
    if token_values(success) != [f"<STRING:{profile.success_query}>"]:
        violations.append(f"{path}: hard-stop IF 成功参数必须是固定成功 SELECT（error IS NULL 分支）")
    if tuple(token_values(failure)) != expected_failure_argument(profile):
        violations.append(f"{path}: hard-stop IF 错误参数必须是 UUID 不存在对象 SELECT（error 非 NULL 分支）")


def validate_pre_guard_execution(
    violations: list[str],
    path: Path,
    tokens: list[Token],
    prepare_index: int,
    execute_index: int,
) -> None:
    """首个 guard EXECUTE 前仅允许该 guard 的 PREPARE，且不能出现任何 DDL/DML/CALL。"""
    for index, token in enumerate(tokens[:execute_index]):
        if token.value == "PREPARE" and index != prepare_index:
            violations.append(f"{path}: guard EXECUTE 前出现非 guard PREPARE（第 {token.line} 行）")
            return
        if token.value == "EXECUTE":
            violations.append(f"{path}: guard EXECUTE 前出现额外 EXECUTE（第 {token.line} 行）")
            return
        if is_write_token(tokens, index):
            violations.append(f"{path}: guard EXECUTE 前出现写语句 {token.value}（第 {token.line} 行）")
            return


@dataclass(frozen=True)
class DelimiterLayout:
    """三份批准脚本共有的 mysql client delimiter 与过程体边界。"""

    opening_index: int
    reset_index: int


def validate_forbidden_mysql_client_commands(violations: list[str], path: Path, source: str) -> None:
    """拒绝字符串/普通注释外的全部 mysql client 指令，包括过程体中的行首命令。

    反斜杠短命令没有安全子集，因此任何代码区 ``\\`` 都 fail-close；长命令则按 mysql
    client 的物理行首语义检查完整禁止集合。这样既覆盖 ``\\r``/``\\u`` 重连换库，也不会把
    多行 SQL 内缩进的 ``status`` 等标识符误判为 client 命令。
    """
    masked = mask_strings_and_ordinary_comments(source)
    source_lines = source.splitlines()
    masked_lines = masked.splitlines()
    if len(source_lines) != len(masked_lines):
        violations.append(f"{path}: mysql client 命令扫描行数不一致，受限 grammar fail-close")
        return
    for line_number, (raw_line, masked_line) in enumerate(zip(source_lines, masked_lines, strict=True), start=1):
        for column, character in enumerate(masked_line):
            if character != "\\":
                continue
            command = masked_line[column : column + 2]
            violations.append(f"{path}: 禁止 mysql client {command}（第 {line_number} 行）")
        command = first_word_at_client_line_start(masked_line)
        if command == "DELIMITER":
            if raw_line.rstrip("\r") not in APPROVED_DELIMITER_LINES:
                violations.append(
                    f"{path}: DELIMITER 仅允许精确形式 DELIMITER $$ / DELIMITER ;（第 {line_number} 行）"
                )
            continue
        if command in MYSQL_CLIENT_LONG_COMMANDS:
            violations.append(f"{path}: 禁止 mysql client {command}（第 {line_number} 行）")


def validate_delimiter_layout(
    violations: list[str], path: Path, source: str, tokens: list[Token], profile: Profile, execute_index: int
) -> DelimiterLayout | None:
    """只接受实际三脚本所需的两条精确 DELIMITER 命令及其唯一过程体边界。"""
    delimiter_indices = [index for index, token in enumerate(tokens) if token.value == "DELIMITER"]
    lines = source.splitlines()
    valid = True
    if len(delimiter_indices) != 2:
        violations.append(f"{path}: DELIMITER 仅允许两条批准命令，实际 {len(delimiter_indices)} 条")
        valid = False
    for index in delimiter_indices:
        token = tokens[index]
        raw_line = lines[token.line - 1].rstrip("\r") if token.line <= len(lines) else ""
        if raw_line not in APPROVED_DELIMITER_LINES:
            violations.append(
                f"{path}: DELIMITER 仅允许精确形式 DELIMITER $$ / DELIMITER ;（第 {token.line} 行）"
            )
            valid = False
        if index <= execute_index:
            violations.append(f"{path}: DELIMITER 仅允许在 hard-stop EXECUTE 后（第 {token.line} 行）")
            valid = False
    if len(delimiter_indices) != 2:
        return None

    opening_index, reset_index = delimiter_indices
    if opening_index >= reset_index:
        violations.append(f"{path}: DELIMITER 批准顺序必须先 $$ 后 ;")
        return None
    if lines[tokens[opening_index].line - 1].rstrip("\r") != "DELIMITER $$":
        violations.append(f"{path}: 首条 DELIMITER 必须精确为 DELIMITER $$")
        valid = False
    if lines[tokens[reset_index].line - 1].rstrip("\r") != "DELIMITER ;":
        violations.append(f"{path}: 第二条 DELIMITER 必须精确为 DELIMITER ;")
        valid = False
    if tuple(token_values(tokens[opening_index : opening_index + 2])) != ("DELIMITER", "$$"):
        violations.append(f"{path}: DELIMITER $$ token 结构异常")
        valid = False
    if tuple(token_values(tokens[reset_index : reset_index + 2])) != ("DELIMITER", ";"):
        violations.append(f"{path}: DELIMITER ; token 结构异常")
        valid = False

    body = tokens[opening_index + 2 : reset_index]
    expected_header = ("CREATE", "PROCEDURE", profile.procedure_name.upper(), "(", ")", "BEGIN")
    if tuple(token_values(body[: len(expected_header)])) != expected_header:
        violations.append(f"{path}: DELIMITER $$ 后必须是批准的 CREATE PROCEDURE {profile.procedure_name}")
        valid = False
    if tuple(token_values(body[-1:])) != ("END$$",):
        violations.append(f"{path}: 批准过程必须以唯一 END$$ 收束")
        valid = False
    dollar_indices = [index for index, token in enumerate(tokens) if token.kind != "STRING" and "$" in token.value]
    expected_dollar_indices = [opening_index + 1, reset_index - 1]
    if dollar_indices != expected_dollar_indices:
        violations.append(f"{path}: 仅允许 DELIMITER $$ 与批准过程尾部 END$$ 使用 $$")
        valid = False
    if not valid:
        return None
    return DelimiterLayout(opening_index, reset_index)


def collect_default_delimiter_statements(
    violations: list[str], path: Path, tokens: list[Token], start: int, end: int
) -> list[tuple[int, int]]:
    """在默认分号 delimiter 区域内切出完整顶层语句；空语句和悬挂 token 均拒绝。"""
    statements: list[tuple[int, int]] = []
    cursor = start
    for index in range(start, end):
        if tokens[index].value != ";":
            continue
        if cursor == index:
            violations.append(f"{path}: 顶层出现空 statement（第 {tokens[index].line} 行）")
        else:
            statements.append((cursor, index))
        cursor = index + 1
    if cursor != end:
        line = tokens[cursor].line if cursor < end else "未知"
        violations.append(f"{path}: 顶层 statement 缺少默认分号终止（第 {line} 行）")
    return statements


def validate_top_level_statement(
    violations: list[str], path: Path, tokens: list[Token], start: int, end: int, profile: Profile
) -> None:
    """批准三脚本在过程体外实际使用的有限 SQL statement 集合。"""
    values = tuple(token_values(tokens[start:end]))
    if not values:
        return
    first = values[0]
    expected_prepare = ("PREPARE", profile.prepare_name.upper(), "FROM", profile.guard_variable)
    expected_execute = ("EXECUTE", profile.prepare_name.upper())
    expected_deallocate = ("DEALLOCATE", "PREPARE", profile.prepare_name.upper())
    procedure = profile.procedure_name.upper()
    expected_drop_before = ("DROP", "PROCEDURE", "IF", "EXISTS", procedure)
    expected_drop_after = (
        expected_drop_before if profile.cleanup_uses_if_exists else ("DROP", "PROCEDURE", procedure)
    )
    expected_call = ("CALL", procedure, "(", ")")
    allowed = False
    if first in {"SELECT", "WITH"}:
        allowed = True
    elif first == "SET":
        allowed = (
            values == ("SET", "NAMES", "UTF8MB4")
            or (len(values) >= 3 and values[1] == profile.error_variable and values[2] in {":=", "="})
            or (len(values) >= 3 and values[1] == profile.guard_variable and values[2] == ":=")
        )
    elif first == "PREPARE":
        allowed = values == expected_prepare
    elif first == "EXECUTE":
        allowed = values == expected_execute
    elif first == "DEALLOCATE":
        allowed = values == expected_deallocate
    elif first == "DROP":
        allowed = values in {expected_drop_before, expected_drop_after}
    elif first == "CALL":
        allowed = values == expected_call
    if allowed:
        return
    rendered = " ".join(values[:8])
    violations.append(f"{path}: 顶层 statement 不在受限白名单（第 {tokens[start].line} 行）：{rendered}")


def validate_top_level_statement_whitelist(
    violations: list[str], path: Path, tokens: list[Token], profile: Profile, layout: DelimiterLayout
) -> None:
    """过程体外只允许三份批准脚本已使用的受限 statement，拒绝 client 命令和未知 SQL。"""
    statements = [
        *collect_default_delimiter_statements(violations, path, tokens, 0, layout.opening_index),
        *collect_default_delimiter_statements(violations, path, tokens, layout.reset_index + 2, len(tokens)),
    ]
    for start, end in statements:
        validate_top_level_statement(violations, path, tokens, start, end, profile)


def detect_profile(tokens: list[Token]) -> Profile:
    profiles = [profile for profile in PROFILES if any(token.value == profile.error_variable for token in tokens)]
    if len(profiles) != 1:
        names = ", ".join(profile.prefix for profile in profiles) or "无"
        raise ValueError(f"无法唯一识别 preflight profile（命中：{names}）")
    return profiles[0]


def check_source(path: Path) -> tuple[list[str], str | None]:
    violations: list[str] = []
    try:
        source = path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as error:
        return [f"{path}: 无法以 UTF-8 读取：{error}"], None
    if REQUIRED_EXECUTION_COMMENT not in source:
        violations.append(f"{path}: 缺少受控 batch runner 执行注释")
    if REQUIRED_BARE_CLIENT_BAN_COMMENT not in source:
        violations.append(f"{path}: 缺少禁止裸 mysql 的执行注释")
    try:
        tokens = lex_sql(source)
        profile = detect_profile(tokens)
    except (LexError, ValueError) as error:
        violations.append(f"{path}: 词法/命名解析失败：{error}")
        return violations, None

    error_sets = find_set_writes(tokens, profile.error_variable)
    if not error_sets:
        violations.append(f"{path}: 缺少 SET {profile.error_variable} := ...")
        return violations, profile.prefix
    initial = error_sets[0]
    guard_candidates = [
        write
        for write in find_set_writes(tokens, profile.guard_variable)
        if write.operator_index is not None
        and write.operator_index + 1 < write.statement_end
        and tokens[write.operator_index].value == ":="
        and tokens[write.operator_index + 1].value == "IF"
    ]
    if len(guard_candidates) != 1:
        violations.append(f"{path}: 缺少 SET {profile.guard_variable} := IF(...) hard-stop")
        return violations, profile.prefix
    guard = guard_candidates[0]
    prepare_index = find_exact_statement(
        tokens,
        ("PREPARE", profile.prepare_name.upper(), "FROM", profile.guard_variable),
        guard.statement_start + 1,
    )
    if prepare_index is None:
        violations.append(f"{path}: 缺少 PREPARE {profile.prepare_name} FROM {profile.guard_variable}")
        return violations, profile.prefix
    execute_index = find_exact_statement(tokens, ("EXECUTE", profile.prepare_name.upper()), prepare_index + 1)
    if execute_index is None:
        violations.append(f"{path}: 缺少 EXECUTE {profile.prepare_name}")
        return violations, profile.prefix
    if not initial.statement_start < guard.statement_start < prepare_index < execute_index:
        violations.append(f"{path}: preflight error / hard-stop / PREPARE / EXECUTE 顺序不成立")

    try:
        branches = parse_initial_case_branches(tokens, initial)
    except ValueError as error:
        violations.append(f"{path}: 初始身份 CASE 解析失败：{error}")
        branches = []
    if len(branches) < 3:
        violations.append(f"{path}: 初始身份 CASE 必须至少有审批/实际会话/精确匹配三个 WHEN 分支")
    else:
        branch_specs = (
            ("审批身份分支", APPROVAL_CONDITIONS),
            ("实际会话身份分支", ACTUAL_CONDITIONS),
            ("精确匹配分支", MATCH_CONDITIONS),
        )
        for (branch_name, conditions), (condition, body) in zip(branch_specs, branches[:3], strict=True):
            validate_identity_condition(violations, path, branch_name, condition, conditions)
            if not has_target_identity_message(body):
                violations.append(f"{path}: {branch_name}未产生 target identity guard 错误文本")

    validate_error_write_flow(violations, path, tokens, profile, initial, guard)
    validate_guard_if(violations, path, tokens, profile, guard)
    validate_pre_guard_execution(violations, path, tokens, prepare_index, execute_index)
    validate_forbidden_mysql_client_commands(violations, path, source)
    delimiter_layout = validate_delimiter_layout(violations, path, source, tokens, profile, execute_index)
    if delimiter_layout is not None:
        validate_top_level_statement_whitelist(violations, path, tokens, profile, delimiter_layout)
    first_write_after = next(
        (token for index, token in enumerate(tokens) if index > execute_index and is_write_token(tokens, index)),
        None,
    )
    if first_write_after is None:
        violations.append(f"{path}: guard EXECUTE 后未找到受保护的 DDL/DML/CALL")
    return violations, profile.prefix


def relative_or_absolute(path: Path) -> str:
    try:
        return str(path.resolve().relative_to(REPO_ROOT))
    except ValueError:
        return str(path.resolve())


def main() -> int:
    parser = argparse.ArgumentParser(
        description="仅静态检查 SQL target-identity guard；不连接数据库、不执行 SQL。"
    )
    parser.add_argument(
        "--fixture",
        action="append",
        type=Path,
        default=[],
        help="检查指定 SQL fixture；可重复传入。未指定时检查三份真实 SQL。",
    )
    args = parser.parse_args()
    paths = tuple(args.fixture) if args.fixture else DEFAULT_SQL_FILES
    print("STATIC TARGET IDENTITY GUARD STRUCTURAL CHECK (no database connection)")
    print(
        "RESTRICTED_GRAMMAR: only the three approved scripts' CASE/IF/error-flow/top-level/client/DELIMITER forms; "
        "not a general MySQL parser."
    )
    failures = 0
    for path in paths:
        resolved = path.resolve()
        violations, profile = check_source(resolved)
        if violations:
            failures += len(violations)
            for violation in violations:
                print(f"FAIL {violation}")
        else:
            print(
                f"PASS {relative_or_absolute(resolved)}: profile={profile}, parsed restricted fail-close "
                "CASE/IF/error-flow/top-level client grammar and guard-before-write ordering"
            )
    if failures:
        print(f"RESULT: FAIL ({failures} violation(s))")
        return 1
    print(
        "RESULT: PASS (all target identity guards match the restricted grammar and fail-close before DDL/DML/CALL)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
