# Prototype Instructions

Run the local server yourself and open the preview in the browser available to this environment. Do not give the user server-start instructions when you can run it.

Before making substantial visual changes, use the Product Design plugin's `get-context` skill when the visual source is unclear or no longer matches the current goal. When the user gives durable prototype-specific design feedback, preferences, or decisions, record them in `AGENTS.md`.

When implementing from a selected generated mock, treat that image as the source of truth for layout, component anatomy, density, spacing, color, typography, visible content, and hierarchy.

Build app UI in `src/`. Keep `.openai/hosting.json`, `worker/index.js`, `scripts/prepare-sites-build.mjs`, and `tests/sites-worker.test.mjs` intact so the same local prototype can be handed to Sites. Before a Sites handoff, run `npm run build` and `npm run test:sites`; the build must leave `dist/client/index.html`, `dist/server/index.js`, and `dist/.openai/hosting.json`.

Prototype-specific role decisions:

- “支部审核员”职责已合并到“支部书记”，演示身份不再保留“支部审核员”。
- “沉浸式审核工作台”更名为“工作台”；“组织审核员”继承“组织管理员”全部菜单权限，并额外增加“工作台”。
- “支部书记”继承“报送员”全部菜单权限，并额外增加“支部审核工作台”；不展示组织审核员的“工作台”。
- 原型演示身份使用正式业务称谓“报送员”，不再使用“一线员工”。
- 员工任务待办入口使用“首页”名称，避免与组织审核员专属“工作台”重名。
