# 排行榜多指标排序与自动滚动验收

- 验收日期：2026-08-26
- 页面：`http://127.0.0.1:8091/#/screen-admin/designer`
- 工具：官方 `playwright-cli` 0.1.18
- 会话：`rank-list-multi`
- 验收性质：仅开发态 mock，非联调。

## 结论

- 属性面板显示“指标列”多选、“类目列”单选和“自动滚动”开关。
- 排行榜每行同时显示“存款余额”和“增幅”两个指标。
- 默认按第一个指标“存款余额”倒序排列。
- 点击“增幅”后显示 `当前排序：增幅（倒序）`；再次点击显示 `当前排序：增幅（正序）`，列表同步重排。
- 12 条数据在 260px 高组件内显示 6 行；等待 2.2 秒后可视机构列表发生变化，证明自动循环滚动生效。
- 设计器结构实测：`.scr-block` 为 flex 且高 260px，`.scr-block-body` 高 239px、overflow hidden，`.rl-wrap` 高 239px。
- 控制台 0 error；3 条 warning 均为既有 Element Plus `el-radio label` 弃用提示。
- 共注册 11 条开发态 mock 路由；未注册保存、发布等写接口。唯一 POST 为只读数据查询 `/api/screen/data`。

## 截图

- `01-rank-properties.png`：属性面板的多指标、类目列与自动滚动配置。
- `02-growth-ascending-and-scroll.png`：增幅正序状态、双指标行展示和固定高度滚动窗口。
