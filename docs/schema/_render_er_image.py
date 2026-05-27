# -*- coding: utf-8 -*-
"""
yiti 数据表关联图 → PNG/SVG 渲染器
====================================

依赖: matplotlib + networkx (无需 Graphviz/Chromium)。
数据来源: 同目录 _gen_schema_doc.py 模块级常量(MODULES / ALL_TABLES / RELATIONSHIPS / DOT_MODULE_COLOR)。
中文字体: Noto Sans CJK SC(系统已装)。

产物:
  yiti-er-overview.png        — 跨模块总览(全部 58 张表,按 8 个模块扇区聚合)
  yiti-er-<module>.png        — 单模块视图(本模块表 + 1 跳邻居)
"""

import math
import os
import sys

import matplotlib
matplotlib.use("Agg")
from matplotlib import font_manager, patches as mpatches, pyplot as plt
from matplotlib.lines import Line2D
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch
import networkx as nx

# 引入 schema 数据
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from _gen_schema_doc import (  # noqa: E402
    MODULES, ALL_TABLES, RELATIONSHIPS, DOT_MODULE_COLOR,
)


# ─────────────────────────────────────────────────────────────────────────────
# 中文字体
# ─────────────────────────────────────────────────────────────────────────────
ZH_FONT_PATH = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
ZH_FONT_BOLD_PATH = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"

# matplotlib 3.3.4 读 .ttc 只取首字体(JP),但中日韩共享 CJK 统一汉字区 → JP 字体也能写汉字。
# 设置 rcParams 用其报告的家族名,避免 fallback 到 DejaVu Sans 出现方框。
plt.rcParams["font.family"] = ["Noto Sans CJK JP", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False
# 二保险:留一个全局 FontProperties,关键文本里直接传 fontproperties=ZH_FP 防止个别 axes 不继承
ZH_FP = font_manager.FontProperties(fname=ZH_FONT_PATH)
ZH_FP_BOLD = font_manager.FontProperties(fname=ZH_FONT_BOLD_PATH)


# ─────────────────────────────────────────────────────────────────────────────
# 工具: 把所有表 → 模块映射, 颜色映射
# ─────────────────────────────────────────────────────────────────────────────
def build_table_meta():
    """返回 {table_name: {'module':..., 'color':..., 'comment':..., 'pk':...}}"""
    meta = {}
    for mod_key, tabs in ALL_TABLES.items():
        for (tname, comment, pk, cols, idxs) in tabs:
            meta[tname] = {
                "module": mod_key,
                "color": DOT_MODULE_COLOR[mod_key],
                "comment": comment,
                "pk": pk,
            }
    return meta


TABLE_META = build_table_meta()
MOD_ORDER = list(MODULES.keys())  # 渲染顺序与扇区分配


# 模块级别加深色(节点边框/聚类轮廓用)
def _darken(hex_color: str, factor: float = 0.65) -> str:
    h = hex_color.lstrip("#")
    r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    return "#{:02x}{:02x}{:02x}".format(int(r * factor), int(g * factor), int(b * factor))


# ─────────────────────────────────────────────────────────────────────────────
# 布局: 把节点按模块分到固定扇区
# ─────────────────────────────────────────────────────────────────────────────
def cluster_layout(node_set, *, R_outer=10.0, R_inner=2.0):
    """
    按 MOD_ORDER 给每个模块分一个角度扇区。
    扇区中心在 (R_inner + (R_outer-R_inner)/2) 半径,角度为模块在 MOD_ORDER 中的位置。
    扇区内的节点再围绕扇区中心做小圆排列。
    没节点的模块自动跳过。
    """
    # 按模块分组节点
    by_mod = {m: [] for m in MOD_ORDER}
    for n in node_set:
        m = TABLE_META.get(n, {}).get("module")
        if m in by_mod:
            by_mod[m].append(n)
        else:
            by_mod.setdefault("__other__", []).append(n)

    active_mods = [m for m in MOD_ORDER if by_mod.get(m)]
    if "__other__" in by_mod and by_mod["__other__"]:
        active_mods.append("__other__")
    n_mod = len(active_mods)
    if n_mod == 0:
        return {}

    # 扇区中心
    R_sector = (R_inner + R_outer) / 2.0
    pos = {}
    for i, mod in enumerate(active_mods):
        # 角度从 90° 开始(顶部),顺时针
        ang = math.radians(90 - 360 * i / n_mod)
        cx = R_sector * math.cos(ang)
        cy = R_sector * math.sin(ang)

        nodes = sorted(by_mod[mod])
        if not nodes:
            continue

        # 扇区内圆形排列
        # 节点数越多,半径越大
        cnt = len(nodes)
        r_local = max(0.6, 0.45 + 0.35 * math.sqrt(cnt))
        if cnt == 1:
            pos[nodes[0]] = (cx, cy)
        else:
            for j, name in enumerate(nodes):
                a = 2 * math.pi * j / cnt - math.pi / 2  # 从顶部开始
                pos[name] = (cx + r_local * math.cos(a), cy + r_local * math.sin(a))
    return pos, active_mods, by_mod


# ─────────────────────────────────────────────────────────────────────────────
# 绘制
# ─────────────────────────────────────────────────────────────────────────────
EDGE_KIND_STYLE = {
    "N1":   {"linestyle": "-",  "color": "#555555", "lw": 1.0, "alpha": 0.75},
    "11":   {"linestyle": "-",  "color": "#1f78b4", "lw": 1.4, "alpha": 0.85},
    "1N":   {"linestyle": "-",  "color": "#33a02c", "lw": 1.0, "alpha": 0.75},
    "NN":   {"linestyle": "-",  "color": "#6a3d9a", "lw": 1.0, "alpha": 0.75},
    "SELF": {"linestyle": "-",  "color": "#aa6633", "lw": 1.0, "alpha": 0.75},
    "LOG":  {"linestyle": "--", "color": "#888888", "lw": 0.8, "alpha": 0.6},
}


def _draw_node(ax, name, x, y, fillcolor, *, fontsize=8, scale=1.0, show_comment=True):
    """画一个圆角矩形节点,标 TABLE_NAME(可选 + 中文释义)。"""
    label = name
    comment = TABLE_META.get(name, {}).get("comment", "") if show_comment else ""
    box_w = (len(name) * 0.04 + 0.6) * scale
    box_h = 0.34 * scale
    box = FancyBboxPatch(
        (x - box_w / 2, y - box_h / 2),
        box_w, box_h,
        boxstyle="round,pad=0.02,rounding_size=0.07",
        facecolor=fillcolor, edgecolor=_darken(fillcolor, 0.55), linewidth=1.0,
    )
    ax.add_patch(box)
    if comment:
        ax.text(x, y + box_h * 0.18, label, ha="center", va="center",
                fontsize=fontsize, fontweight="bold", color="#222222")
        ax.text(x, y - box_h * 0.18, comment[:18] + ("…" if len(comment) > 18 else ""),
                ha="center", va="center", fontsize=max(6, fontsize - 2), color="#444444")
    else:
        ax.text(x, y, label, ha="center", va="center",
                fontsize=fontsize, fontweight="bold", color="#222222")


def _curved_arrow(ax, x1, y1, x2, y2, *, style, label=None, label_size=6, rad=0.18):
    """画一条带箭头的曲线边,可选边标签。"""
    arrow = FancyArrowPatch(
        (x1, y1), (x2, y2),
        connectionstyle=f"arc3,rad={rad}",
        arrowstyle="-|>",
        mutation_scale=10,
        linewidth=style["lw"],
        linestyle=style["linestyle"],
        color=style["color"],
        alpha=style["alpha"],
        zorder=1,
    )
    ax.add_patch(arrow)
    if label:
        # 标签放在中点稍微偏移
        mx, my = (x1 + x2) / 2, (y1 + y2) / 2
        # 偏移方向: 垂直于线的法向
        dx, dy = x2 - x1, y2 - y1
        d = (dx * dx + dy * dy) ** 0.5 or 1
        nx_, ny_ = -dy / d, dx / d
        ox, oy = nx_ * 0.18, ny_ * 0.18
        ax.text(mx + ox, my + oy, label, ha="center", va="center",
                fontsize=label_size, color=style["color"], alpha=0.9,
                bbox=dict(facecolor="white", edgecolor="none", pad=0.6, alpha=0.7))


def _draw_legend(ax, used_modules):
    handles = []
    for m in used_modules:
        if m == "__other__":
            continue
        mc = DOT_MODULE_COLOR[m]
        handles.append(mpatches.Patch(facecolor=mc, edgecolor=_darken(mc, 0.55),
                                      label=f"{m}  {MODULES[m]['zh']}"))
    handles += [
        Line2D([0], [0], color="#555555", lw=1.4, label="N1 多对一(物理外键)"),
        Line2D([0], [0], color="#1f78b4", lw=1.6, label="11 一对一"),
        Line2D([0], [0], color="#aa6633", lw=1.4, label="SELF 自关联"),
        Line2D([0], [0], color="#888888", lw=1.2, linestyle="--",
               label="LOG 逻辑关联(business_key/version 等)"),
    ]
    ax.legend(handles=handles, loc="upper left", bbox_to_anchor=(1.02, 1.0),
              fontsize=8, frameon=True, framealpha=0.95)


def render_overview(out_path: str):
    """跨模块总览 — 全部 58 张表。"""
    nodes = set(TABLE_META.keys())
    pos, active_mods, by_mod = cluster_layout(nodes, R_outer=11.0, R_inner=2.5)

    fig, ax = plt.subplots(figsize=(28, 22), dpi=130)
    ax.set_aspect("equal")
    ax.axis("off")

    # 模块扇区背景圆形 + 标签
    R_sector = (2.5 + 11.0) / 2
    n_mod = len(active_mods)
    for i, m in enumerate(active_mods):
        ang = math.radians(90 - 360 * i / n_mod)
        cx, cy = R_sector * math.cos(ang), R_sector * math.sin(ang)
        cnt = len(by_mod[m])
        r_local = max(0.6, 0.45 + 0.35 * math.sqrt(cnt)) + 0.5
        color = DOT_MODULE_COLOR.get(m, "#cccccc")
        bg = mpatches.Circle((cx, cy), r_local, facecolor=color, alpha=0.18,
                             edgecolor=_darken(color, 0.55), linewidth=1.5, zorder=0)
        ax.add_patch(bg)
        # 模块标题: 放在扇区外侧
        outer_r = R_sector + r_local + 0.6
        lx, ly = outer_r * math.cos(ang), outer_r * math.sin(ang)
        ax.text(lx, ly,
                f"{MODULES[m]['zh']}\n({m})  {cnt} 张",
                ha="center", va="center", fontsize=14, fontweight="bold",
                color=_darken(color, 0.55),
                bbox=dict(facecolor=color, alpha=0.5, edgecolor=_darken(color, 0.55),
                          boxstyle="round,pad=0.4"))

    # 边
    for (ft, fc, tt, tc, kind, note) in RELATIONSHIPS:
        if ft not in pos or tt not in pos:
            continue
        if ft == tt:  # 自关联简化处理
            continue
        x1, y1 = pos[ft]
        x2, y2 = pos[tt]
        style = EDGE_KIND_STYLE.get(kind, EDGE_KIND_STYLE["N1"])
        _curved_arrow(ax, x1, y1, x2, y2, style=style, label=None, rad=0.13)

    # 自关联(画小圆环)
    for (ft, fc, tt, tc, kind, note) in RELATIONSHIPS:
        if ft != tt or ft not in pos:
            continue
        x, y = pos[ft]
        style = EDGE_KIND_STYLE.get(kind, EDGE_KIND_STYLE["SELF"])
        loop = mpatches.FancyArrowPatch(
            (x, y + 0.12), (x + 0.12, y),
            connectionstyle="arc3,rad=2.5",
            arrowstyle="-|>", mutation_scale=8,
            color=style["color"], lw=style["lw"], alpha=style["alpha"],
        )
        ax.add_patch(loop)

    # 节点(后画,叠在边上面)
    for n, (x, y) in pos.items():
        m = TABLE_META.get(n, {}).get("module")
        color = DOT_MODULE_COLOR.get(m, "#dddddd")
        _draw_node(ax, n, x, y, color, fontsize=8, scale=0.9)

    # 标题与图例
    ax.set_title("yiti(分行业务平台) 数据表关联总览  ·  58 张业务表  ·  83 条业务关联(跳过 Quartz)",
                 fontsize=16, fontweight="bold", color="#1F4E78", pad=18)
    _draw_legend(ax, active_mods)

    pad = 2.5
    ax.set_xlim(-15, 15)
    ax.set_ylim(-15, 15)

    _save_all(fig, out_path)
    plt.close(fig)


def _save_all(fig, base_path: str):
    """同时保存 PNG 与 SVG;base_path 可以是 .png 或 .svg,两份都会落盘。"""
    root, _ = os.path.splitext(base_path)
    fig.savefig(root + ".png", bbox_inches="tight", facecolor="white")
    fig.savefig(root + ".svg", bbox_inches="tight", facecolor="white")


def render_module(mod_key: str, out_path: str):
    """
    单模块视图: 本模块表填中央大圈,邻居按各自模块方向排在外圈。
    布局公式 — 内圈半径根据节点数自动放大,保证最少 1.0 的最近邻间距。
    """
    own_tables = [t[0] for t in ALL_TABLES[mod_key]]
    own = set(own_tables)
    neighbors = set()
    edges_kept = []
    for (ft, fc, tt, tc, kind, note) in RELATIONSHIPS:
        if ft in own or tt in own:
            edges_kept.append((ft, fc, tt, tc, kind, note))
            if ft not in own:
                neighbors.add(ft)
            if tt not in own:
                neighbors.add(tt)

    pos = {}

    # ── 中央: 本模块表 ──────────────────────────────────────────────
    n_own = len(own_tables)
    # 保证相邻节点弧长 ≥ 1.6
    R_inner = max(2.5, 1.6 * n_own / (2 * math.pi))
    # 节点按表名稳定排序,避免每次跳动
    own_sorted = sorted(own_tables)
    for i, name in enumerate(own_sorted):
        a = 2 * math.pi * i / max(1, n_own) - math.pi / 2
        pos[name] = (R_inner * math.cos(a), R_inner * math.sin(a))

    # ── 外圈: 邻居,按所属模块聚团 ───────────────────────────────────
    nb_by_mod = {}
    for nb in neighbors:
        m = TABLE_META.get(nb, {}).get("module", "__other__")
        nb_by_mod.setdefault(m, []).append(nb)

    other_mods = [m for m in MOD_ORDER if m != mod_key and nb_by_mod.get(m)]
    if "__other__" in nb_by_mod:
        other_mods.append("__other__")
    R_outer = R_inner + 4.5

    # 给每个邻居模块一个角度区间;均匀划分整圈
    n_nb_mod = max(1, len(other_mods))
    nb_centers = {}
    for i, m in enumerate(other_mods):
        ang = math.radians(90 - 360 * i / n_nb_mod - 22.5)  # 整体偏 22.5° 避开内圈"顶部"
        nb_centers[m] = ang

    for m in other_mods:
        ang_center = nb_centers[m]
        nbs = sorted(nb_by_mod[m])
        cnt = len(nbs)
        # 邻居在以 ang_center 为中心、半径 R_outer 的弧段上展开;弧段宽度根据数量
        arc_span = math.radians(min(50, 8 + 7 * cnt))
        if cnt == 1:
            angles = [ang_center]
        else:
            angles = [ang_center - arc_span / 2 + arc_span * j / (cnt - 1) for j in range(cnt)]
        for nb, a in zip(nbs, angles):
            pos[nb] = (R_outer * math.cos(a), R_outer * math.sin(a))

    # ── 画布 ─────────────────────────────────────────────────────
    fig_size = max(18, R_outer * 1.6)
    fig, ax = plt.subplots(figsize=(fig_size, fig_size * 0.85), dpi=140)
    ax.set_aspect("equal")
    ax.axis("off")

    # 中央底色环(本模块)
    self_color = DOT_MODULE_COLOR[mod_key]
    inner_bg = mpatches.Circle((0, 0), R_inner + 0.7,
                               facecolor=self_color, alpha=0.18,
                               edgecolor=_darken(self_color, 0.55), linewidth=2.0, zorder=0)
    ax.add_patch(inner_bg)
    ax.text(0, R_inner + 1.1,
            f"{MODULES[mod_key]['zh']}  ({mod_key})  · 本模块 {n_own} 张表",
            ha="center", va="bottom",
            fontsize=14, fontweight="bold",
            color=_darken(self_color, 0.55))

    # 邻居模块标签
    for m in other_mods:
        if m == "__other__":
            continue
        ang = nb_centers[m]
        lx = (R_outer + 1.3) * math.cos(ang)
        ly = (R_outer + 1.3) * math.sin(ang)
        c = DOT_MODULE_COLOR.get(m, "#cccccc")
        ax.text(lx, ly,
                f"{MODULES[m]['zh']}\n({m}) · {len(nb_by_mod[m])} 张",
                ha="center", va="center", fontsize=10, fontweight="bold",
                color=_darken(c, 0.55),
                bbox=dict(facecolor=c, alpha=0.55,
                          edgecolor=_darken(c, 0.55),
                          boxstyle="round,pad=0.30"))

    # 边
    dense_threshold = 6
    is_dense = n_own >= dense_threshold
    for (ft, fc, tt, tc, kind, note) in edges_kept:
        if ft not in pos or tt not in pos or ft == tt:
            continue
        x1, y1 = pos[ft]
        x2, y2 = pos[tt]
        style = EDGE_KIND_STYLE.get(kind, EDGE_KIND_STYLE["N1"])
        # 跳过冗余 LOG 标签(大量 business_key→business_key 重复)
        if kind == "LOG" and fc == tc:
            lbl = None
        else:
            lbl = f"{fc}→{tc}"
            if len(lbl) > 30:
                lbl = lbl[:28] + "…"
        _curved_arrow(ax, x1, y1, x2, y2, style=style, label=lbl,
                      label_size=5 if is_dense else 6, rad=0.18)

    # 自关联
    for (ft, fc, tt, tc, kind, note) in edges_kept:
        if ft != tt or ft not in pos:
            continue
        x, y = pos[ft]
        style = EDGE_KIND_STYLE.get(kind, EDGE_KIND_STYLE["SELF"])
        loop = mpatches.FancyArrowPatch(
            (x, y + 0.15), (x + 0.15, y),
            connectionstyle="arc3,rad=2.5",
            arrowstyle="-|>", mutation_scale=8,
            color=style["color"], lw=style["lw"], alpha=style["alpha"],
        )
        ax.add_patch(loop)
        ax.text(x + 0.22, y + 0.22, fc, fontsize=6,
                color=style["color"], alpha=0.9)

    # 节点(后画)
    for n, (x, y) in pos.items():
        m = TABLE_META.get(n, {}).get("module")
        color = DOT_MODULE_COLOR.get(m, "#dddddd")
        is_own = n in own
        # 内圈密集时只显表名,不显中文释义
        show_comment = (is_own and not is_dense) or (not is_own)
        _draw_node(ax, n, x, y, color,
                   fontsize=10 if is_own else 8,
                   scale=1.10 if is_own else 0.90,
                   show_comment=show_comment)

    meta = MODULES[mod_key]
    ax.set_title(f"{meta['zh']}({meta['code']}) 数据表关联图\n"
                 f"本模块 {n_own} 张表 · 1 跳邻居 {len(neighbors)} 张 · 关联 {len(edges_kept)} 条",
                 fontsize=15, fontweight="bold", color="#1F4E78", pad=16)
    _draw_legend(ax, [mod_key] + [m for m in other_mods if m != "__other__"])

    lim = R_outer + 3.0
    ax.set_xlim(-lim, lim)
    ax.set_ylim(-lim, lim)
    _save_all(fig, out_path)
    plt.close(fig)


# ─────────────────────────────────────────────────────────────────────────────
# main
# ─────────────────────────────────────────────────────────────────────────────
def main():
    out_dir = HERE
    overview = os.path.join(out_dir, "yiti-er-overview.png")
    render_overview(overview)
    print(f"  overview  → {overview}")

    for mod_key in MOD_ORDER:
        path = os.path.join(out_dir, f"yiti-er-{mod_key}.png")
        render_module(mod_key, path)
        print(f"  {mod_key:11s} → {path}")


if __name__ == "__main__":
    main()
