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
  getReportTabTasks,
  submitReporterTask,
  approveBranchTask,
  submitBranchTask,
  approveOrganizationTask,
  rejectOrganizationTask,
  addMaterialUpload,
  getMaterialCompletion,
  buildExportPreview,
  MATERIAL_DETAIL_ITEMS,
  getHistoryTaskGroups,
  historyStatusLabel,
  getPublishedTasks,
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

test("临时任务统一 stage 按报送员、支部书记、组织审核员顺序流转", () => {
  const [task] = publishTask([], {
    title: "临时填报",
    description: "请提交情况",
    type: "整改反馈",
    nature: "temporary",
    startAt: "2026-08-20",
    endAt: "2026-08-31",
    object: "all",
    requiresFile: true,
    fileTypes: ["PDF"],
  });
  assert.equal(task.stage, "reporter-pending");
  const reporterSubmitted = submitReporterTask([task], task.id, { content: "支部填报内容", fileNames: ["说明.pdf"] });
  assert.equal(reporterSubmitted[0].stage, "branch-pending");
  assert.equal(reporterSubmitted[0].submission.content, "支部填报内容");
  assert.equal(reporterSubmitted[0].submission.fileNames[0], "说明.pdf");
  const branchApproved = approveBranchTask(reporterSubmitted, task.id);
  assert.equal(branchApproved[0].stage, "branch-pending");
  assert.equal(branchApproved[0].branchApproved, true);
  const orgPending = submitBranchTask(branchApproved, task.id);
  assert.equal(orgPending[0].stage, "org-pending");
  const approved = approveOrganizationTask(orgPending, task.id);
  assert.equal(approved[0].stage, "approved");
  const rejected = rejectOrganizationTask(orgPending, task.id, "材料不完整");
  assert.equal(rejected[0].stage, "rejected");
  assert.equal(rejected[0].organizationOpinion, "材料不完整");
});

test("四页签按统一 stage 映射，报送员提交后进入审核中", () => {
  const base = {
    title: "阶段任务",
    description: "内容",
    type: "通知确认",
    nature: "temporary",
    startAt: "2026-08-20",
    endAt: "2026-08-31",
    object: "all",
    requiresFile: false,
  };
  const [task] = publishTask([], base);
  assert.equal(getReportTabTasks([task], "pending").length, 1);
  const branch = submitReporterTask([task], task.id, { content: "已填报" });
  assert.equal(getReportTabTasks(branch, "review").length, 1);
  const approved = approveOrganizationTask(submitBranchTask(approveBranchTask(branch, task.id), task.id), task.id);
  assert.equal(getReportTabTasks(approved, "approved").length, 1);
  const rejected = rejectOrganizationTask(branch, task.id, "退回");
  assert.equal(getReportTabTasks(rejected, "rejected").length, 1);
});

test("四维材料每个明细支持重复上传，上传次数至少一次即判定完成", () => {
  assert.equal(MATERIAL_DETAIL_ITEMS.includes("联建规范度"), true);
  assert.equal(MATERIAL_DETAIL_ITEMS.includes("合作契约化"), true);
  assert.equal(MATERIAL_DETAIL_ITEMS.includes("业务转换实质"), true);
  const [task] = publishTask([], {
    title: "季度材料",
    description: "材料",
    type: "四大维度材料上报",
    nature: "scheduled",
    cycle: "quarterly-end",
    duration: 5,
    object: "all",
    requiresFile: true,
    fileTypes: ["PDF"],
    referenceDate: "2026-08-15",
  });
  const once = addMaterialUpload([task], task.id, "联建规范度", "联建.pdf");
  assert.equal(once[0].materialUploads["联建规范度"].count, 1);
  assert.equal(getMaterialCompletion(once[0]).completed.includes("联建规范度"), true);
  const twice = addMaterialUpload(once, task.id, "联建规范度", "联建-补充.pdf");
  assert.equal(twice[0].materialUploads["联建规范度"].count, 2);
  assert.equal(getMaterialCompletion(twice[0]).completed.length, 1);
});

test("任务导出预览包含 Excel 字段、支部附件目录，四维导出前必须选择明细项", () => {
  const [temporary] = publishTask([], {
    title: "临时任务导出",
    description: "填报内容",
    type: "整改反馈",
    nature: "temporary",
    startAt: "2026-08-20",
    endAt: "2026-08-31",
    object: "all",
    requiresFile: true,
    fileTypes: ["PDF"],
  });
  const submitted = submitReporterTask([temporary], temporary.id, { content: "支部内容", fileNames: ["附件.pdf"] });
  const preview = buildExportPreview(submitted[0]);
  assert.equal(preview.archiveName.endsWith(".zip"), true);
  assert.deepEqual(preview.excelColumns, ["任务名称", "任务发布时间", "结束时间", "提交人", "提交时间", "填报内容"]);
  assert.equal(preview.entries.some((entry) => entry.path === "党支部一/附件.pdf"), true);
  const [material] = publishTask([], {
    title: "四维导出",
    description: "材料",
    type: "四大维度材料上报",
    nature: "scheduled",
    cycle: "quarterly-end",
    duration: 5,
    object: "all",
    requiresFile: true,
    fileTypes: ["PDF"],
  });
  assert.throws(() => buildExportPreview(material), /先选择明细项/);
  const materialPreview = buildExportPreview(material, ["联建规范度"]);
  assert.deepEqual(materialPreview.selectedDetails, ["联建规范度"]);
});

test("导出预览按 branchSubmissions 为每个有附件支部生成目录，无附件支部不生成目录", () => {
  const [task] = publishTask([], {
    title: "多支部填报导出",
    description: "内容",
    type: "整改反馈",
    nature: "temporary",
    startAt: "2026-08-20",
    endAt: "2026-08-31",
    object: "all",
    requiresFile: true,
    fileTypes: ["PDF"],
  });
  const withBranches = {
    ...task,
    branchSubmissions: [
      { branch: "党支部一", submitter: "张伟", submittedAt: "2026-08-27 09:00", content: "一支部", fileNames: ["一支部.pdf"] },
      { branch: "党支部二", submitter: "李娜", submittedAt: "2026-08-27 10:00", content: "二支部", fileNames: [] },
      { branch: "党支部三", submitter: "王强", submittedAt: "2026-08-27 11:00", content: "三支部", fileNames: ["三支部.pdf"] },
    ],
  };
  const preview = buildExportPreview(withBranches);
  assert.equal(preview.entries.some((entry) => entry.path === "党支部一/一支部.pdf"), true);
  assert.equal(preview.entries.some((entry) => entry.path === "党支部三/三支部.pdf"), true);
  assert.equal(preview.entries.some((entry) => entry.path.startsWith("党支部二/")), false);
});

test("四维导出预览只显示选中的明细及其材料条目", () => {
  const [task] = publishTask([], {
    title: "四维筛选导出",
    description: "材料",
    type: "四大维度材料上报",
    nature: "scheduled",
    cycle: "quarterly-end",
    duration: 5,
    object: "all",
    requiresFile: true,
    fileTypes: ["PDF"],
  });
  const withUploads = {
    ...task,
    materialUploads: {
      "联建规范度": { count: 1, files: [{ name: "联建规范度.pdf", uploadedAt: "2026-08-27 09:00" }] },
      "合作契约化": { count: 1, files: [{ name: "合作契约化.pdf", uploadedAt: "2026-08-27 10:00" }] },
    },
  };
  const preview = buildExportPreview(withUploads, ["联建规范度"]);
  assert.equal(preview.entries.some((entry) => entry.path.includes("联建规范度")), true);
  assert.equal(preview.entries.some((entry) => entry.path.includes("合作契约化")), false);
});

test("审核中、已通过、已驳回历史页签按任务类型拆分，审核中状态文案为审核中", () => {
  const tasks = [
    { id: "material-review", type: "四大维度材料上报", stage: "org-pending" },
    { id: "temporary-review", type: "整改反馈", stage: "branch-pending" },
  ];
  const groups = getHistoryTaskGroups(tasks, "review");
  assert.deepEqual(groups.materials.map((task) => task.id), ["material-review"]);
  assert.deepEqual(groups.temporary.map((task) => task.id), ["temporary-review"]);
  assert.equal(historyStatusLabel("org-pending"), "审核中");
  assert.equal(historyStatusLabel("branch-pending"), "审核中");
  assert.equal(historyStatusLabel("approved"), "已通过");
  assert.equal(historyStatusLabel("rejected"), "已驳回");
});

test("任务管理列表保留所有已发布任务，不因流程 status 变化而消失", () => {
  const tasks = [
    { id: "published", status: "published", publishedAt: "2026-08-20" },
    { id: "submitted", status: "submitted", publishedAt: "2026-08-20" },
    { id: "approved", status: "approved", publishedAt: "2026-08-20" },
    { id: "rejected", status: "rejected", publishedAt: "2026-08-20" },
    { id: "draft", status: "draft" },
  ];
  assert.deepEqual(getPublishedTasks(tasks).map((task) => task.id), ["published", "submitted", "approved", "rejected"]);
});
