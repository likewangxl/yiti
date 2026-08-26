import test from "node:test";
import assert from "node:assert/strict";

import {
  calculateWindow,
  validateTaskDraft,
  validateTaskSubmission,
  linkifyDescription,
  publishTask,
  completeTask,
  getEmployeeTasks,
} from "../src/task-domain.js";

test("周期窗口计算遵循自然日含首尾：月末5天为27-31", () => {
  assert.deepEqual(
    calculateWindow("monthly-end", 5, new Date("2026-08-15T00:00:00")),
    { start: "2026-08-27", end: "2026-08-31" },
  );
  assert.deepEqual(
    calculateWindow("monthly-end", 5, new Date("2026-08-29T00:00:00")),
    { start: "2026-09-26", end: "2026-09-30" },
  );
});

test("月初5天为1-5，周初/周末分别按周一和周日锚定", () => {
  assert.deepEqual(
    calculateWindow("monthly-start", 5, new Date("2026-08-15T00:00:00")),
    { start: "2026-09-01", end: "2026-09-05" },
  );
  assert.deepEqual(
    calculateWindow("weekly-start", 3, new Date("2026-08-15T00:00:00")),
    { start: "2026-08-17", end: "2026-08-19" },
  );
  assert.deepEqual(
    calculateWindow("weekly-end", 3, new Date("2026-08-12T00:00:00")),
    { start: "2026-08-14", end: "2026-08-16" },
  );
});

test("季度初/末按实际季度边界计算", () => {
  assert.deepEqual(
    calculateWindow("quarterly-start", 5, new Date("2026-08-15T00:00:00")),
    { start: "2026-10-01", end: "2026-10-05" },
  );
  assert.deepEqual(
    calculateWindow("quarterly-end", 5, new Date("2026-08-15T00:00:00")),
    { start: "2026-09-26", end: "2026-09-30" },
  );
});

test("持续天数超出自然周期时拒绝", () => {
  assert.throws(
    () => calculateWindow("weekly-start", 8, new Date("2026-08-15T00:00:00")),
    /持续天数不能超过周周期/,
  );
  assert.throws(
    () => calculateWindow("monthly-start", 32, new Date("2026-08-15T00:00:00")),
    /持续天数不能超过当月自然天数/,
  );
});

test("发布临时/定时任务后出现在管理列表，非四大维度任务进入员工任务和首页待办", () => {
  const base = {
    object: "all",
    title: "学习通知",
    description: "请查看 https://example.com/notice",
    type: "党建学习",
    requiresFile: false,
  };
  const published = publishTask(
    publishTask([], { ...base, nature: "temporary", startAt: "2026-08-20", endAt: "2026-08-31" }),
    { ...base, nature: "scheduled", cycle: "monthly-end", duration: 5, referenceDate: "2026-08-15" },
  );
  assert.equal(published.length, 2);
  const employeeTasks = getEmployeeTasks(published, new Date("2026-08-28T00:00:00"));
  assert.equal(employeeTasks.length, 2);
  assert.equal(employeeTasks.filter((task) => task.status === "pending").length, 2);
  const completed = completeTask(employeeTasks, employeeTasks[0].id);
  assert.equal(completed.find((task) => task.id === employeeTasks[0].id).status, "completed");
  assert.equal(getEmployeeTasks(completed, new Date("2026-08-28T00:00:00")).filter((task) => task.status === "pending").length, 1);
});

test("四大维度材料任务进入既有上报入口说明分支", () => {
  const [task] = publishTask([], {
    title: "季度材料上报",
    description: "请按要求上报",
    type: "四大维度材料上报",
    nature: "scheduled",
    cycle: "quarterly-end",
    duration: 5,
    object: "all",
    requiresFile: true,
    fileTypes: ["PDF"],
  });
  assert.equal(task.route, "materials-entry");
});

test("描述仅将 http/https URL 转成安全新窗口链接", () => {
  const links = linkifyDescription("打开 https://example.com/a 和 http://demo.test；文本 example.com 不转换");
  assert.equal(links.filter((part) => part.type === "link").length, 2);
  assert.equal(links.find((part) => part.type === "link").target, "_blank");
  assert.equal(links.filter((part) => part.type === "text").some((part) => part.value.includes("example.com 不转换")), true);
});

test("文件要求为否时忽略文件类型，为是时类型必填", () => {
  const withoutFile = validateTaskDraft({ title: "通知", description: "内容", object: "all", nature: "temporary", type: "通知确认", startAt: "2026-08-20", endAt: "2026-08-31", requiresFile: false, fileTypes: [] });
  assert.equal(withoutFile.valid, true);
  const missingTypes = validateTaskDraft({ title: "反馈", description: "内容", object: "all", nature: "temporary", type: "整改反馈", startAt: "2026-08-20", endAt: "2026-08-31", requiresFile: true, fileTypes: [] });
  assert.equal(missingTypes.valid, false);
  assert.match(missingTypes.errors.fileTypes, /至少选择一种/);
});

test("指定人员任务至少选择一名人员", () => {
  const result = validateTaskDraft({
    title: "定向通知",
    description: "请确认",
    object: "specified",
    people: [],
    nature: "temporary",
    type: "通知确认",
    startAt: "2026-08-20",
    endAt: "2026-08-31",
    requiresFile: false,
    fileTypes: [],
  });
  assert.equal(result.valid, false);
  assert.match(result.errors.object, /至少选择一名/);
});

test("需要上传文件的任务提交前必须已选文件", () => {
  assert.equal(validateTaskSubmission({ requiresFile: false }, "").valid, true);
  const missing = validateTaskSubmission({ requiresFile: true, fileTypes: ["PDF"] }, "");
  assert.equal(missing.valid, false);
  assert.match(missing.errors.file, /请先选择文件/);
  assert.equal(validateTaskSubmission({ requiresFile: true, fileTypes: ["PDF"] }, "材料.pdf").valid, true);
});
