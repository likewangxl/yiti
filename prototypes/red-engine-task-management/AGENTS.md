# Prototype Instructions

Run the local server yourself and open the preview in the browser available to this environment. Do not give the user server-start instructions when you can run it.

Before making substantial visual changes, use the Product Design plugin's `get-context` skill when the visual source is unclear or no longer matches the current goal. When the user gives durable prototype-specific design feedback, preferences, or decisions, record them in `AGENTS.md`.

When implementing from a selected generated mock, treat that image as the source of truth for layout, component anatomy, density, spacing, color, typography, visible content, and hierarchy.

Build app UI in `src/`. Keep `.openai/hosting.json`, `worker/index.js`, `scripts/prepare-sites-build.mjs`, and `tests/sites-worker.test.mjs` intact so the same local prototype can be handed to Sites. Before a Sites handoff, run `npm run build` and `npm run test:sites`; the build must leave `dist/client/index.html`, `dist/server/index.js`, and `dist/.openai/hosting.json`.

Prototype-specific role decisions:

- “支部审核员”职责已合并到“支部书记”，演示身份不再保留“支部审核员”。
- “沉浸式审核工作台”更名为“工作台”；组织审核员保留组织管理员的相似管理能力并增加“工作台”，但隐藏“四大维度材料上报”“上报信息”和“任务处理”。
- 支部书记仅使用“首页”“支部审核工作台”和“红黄牌预警池”，不展示“四大维度材料上报”和“上报信息”。
- 原型演示身份使用正式业务称谓“报送员”，不再使用“一线员工”。
- 员工任务待办入口使用“首页”名称，避免与组织审核员专属“工作台”重名。

本轮持久化角色与状态决策（2026-08-27）：

- 报送员菜单固定为：首页、四大维度材料上报、上报信息、红黄牌预警池；原“任务处理”入口删除，任务处理并入上报信息四页签。
- 支部书记只保留：首页、支部审核工作台、红黄牌预警池；不展示四大维度材料上报和上报信息。支部工作台页签为待处理、审核中、已通过、已驳回。
- 组织审核员不展示四大维度材料上报和上报信息，使用“工作台”；工作台左侧筛选四大维度材料上报/临时任务，另保留组织管理员的其余管理能力。
- 任务使用统一 stage：reporter-pending、branch-pending、org-pending、approved、rejected。报送员提交临时任务进入 branch-pending；支部书记先通过再提交组织审核；组织审核通过或驳回，驳回回退报送员已驳回页。
- 四大维度材料上报按既有入口处理，材料明细允许多次上传，单明细上传次数大于等于 1 即本季度完成。导出前必须选择字典明细项。
- 组织管理员任务详情与组织审核工作台的附件下载、ZIP 导出均为原型模拟动作，README 必须标明不生成真实文件。
