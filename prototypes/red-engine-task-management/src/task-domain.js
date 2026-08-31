const CYCLE_META = {
  "weekly-start": { label: "每周初", unit: "week", anchor: "start" },
  "weekly-end": { label: "每周末", unit: "week", anchor: "end" },
  "monthly-start": { label: "每月初", unit: "month", anchor: "start" },
  "monthly-end": { label: "每月末", unit: "month", anchor: "end" },
  "quarterly-start": { label: "每季度初", unit: "quarter", anchor: "start" },
  "quarterly-end": { label: "每季度末", unit: "quarter", anchor: "end" },
};

export const TASK_STAGES = {
  REPORTER_PENDING: "reporter-pending",
  BRANCH_PENDING: "branch-pending",
  ORG_PENDING: "org-pending",
  APPROVED: "approved",
  REJECTED: "rejected",
};

export const REPORT_TABS = [
  { value: "pending", label: "待处理" },
  { value: "review", label: "审核中" },
  { value: "approved", label: "已通过" },
  { value: "rejected", label: "已驳回" },
];

// 明细项来自字典配置，原型先用可调整的常量模拟字典维护结果。
export const MATERIAL_DETAIL_ITEMS = ["联建规范度", "合作契约化", "业务转换实质", "服务融合度"];

const DAY_MS = 24 * 60 * 60 * 1000;

function localDate(value) {
  if (value instanceof Date) {
    return new Date(value.getFullYear(), value.getMonth(), value.getDate());
  }
  if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split("-").map(Number);
    return new Date(year, month - 1, day);
  }
  const parsed = new Date(value);
  return new Date(parsed.getFullYear(), parsed.getMonth(), parsed.getDate());
}

function addDays(date, days) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

function formatDate(date) {
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, "0"), String(date.getDate()).padStart(2, "0")].join("-");
}

function daysInMonth(year, monthIndex) {
  return new Date(year, monthIndex + 1, 0).getDate();
}

function quarterOf(monthIndex) {
  return Math.floor(monthIndex / 3);
}

function quarterBounds(date) {
  const quarterStartMonth = quarterOf(date.getMonth()) * 3;
  const start = new Date(date.getFullYear(), quarterStartMonth, 1);
  const end = new Date(date.getFullYear(), quarterStartMonth + 3, 0);
  return { start, end };
}

function nextAnchor(cycle, referenceDate, duration = 1) {
  const meta = CYCLE_META[cycle];
  const reference = localDate(referenceDate);
  if (!meta) throw new Error("不支持的周期");

  if (meta.unit === "week") {
    const day = reference.getDay();
    const daysFromMonday = (day + 6) % 7;
    const monday = addDays(reference, -daysFromMonday);
    const sunday = addDays(monday, 6);
    if (meta.anchor === "start") {
      return addDays(monday, 7);
    }
    const candidate = day === 0 ? addDays(sunday, 7) : sunday;
    return reference >= addDays(candidate, -(duration - 1)) ? addDays(candidate, 7) : candidate;
  }

  if (meta.unit === "month") {
    const monthStart = new Date(reference.getFullYear(), reference.getMonth(), 1);
    const monthEnd = new Date(reference.getFullYear(), reference.getMonth() + 1, 0);
    if (meta.anchor === "start") {
      return new Date(reference.getFullYear(), reference.getMonth() + 1, 1);
    }
    const candidate = reference.getDate() === monthEnd.getDate()
      ? new Date(reference.getFullYear(), reference.getMonth() + 2, 0)
      : monthEnd;
    if (meta.anchor === "end" && reference >= addDays(candidate, -(duration - 1))) {
      return new Date(candidate.getFullYear(), candidate.getMonth() + 2, 0);
    }
    return candidate;
  }

  const bounds = quarterBounds(reference);
  if (meta.anchor === "start") {
    return new Date(bounds.end.getFullYear(), bounds.end.getMonth() + 1, 1);
  }
  const candidate = reference.getTime() === bounds.end.getTime()
    ? new Date(bounds.end.getFullYear(), bounds.end.getMonth() + 4, 0)
    : bounds.end;
  if (meta.anchor === "end" && reference >= addDays(candidate, -(duration - 1))) {
    return new Date(candidate.getFullYear(), candidate.getMonth() + 4, 0);
  }
  return candidate;
}

function naturalPeriodDays(cycle, anchor) {
  const meta = CYCLE_META[cycle];
  if (meta.unit === "week") return 7;
  if (meta.unit === "month") return daysInMonth(anchor.getFullYear(), anchor.getMonth());
  const bounds = quarterBounds(anchor);
  return Math.round((bounds.end - bounds.start) / DAY_MS) + 1;
}

/**
 * 计算下一个周期窗口。自然日包含首尾，开始锚点向后延展，结束锚点向前倒推。
 */
export function calculateWindow(cycle, duration, referenceDate = new Date()) {
  const meta = CYCLE_META[cycle];
  const numberOfDays = Number(duration);
  if (!meta) throw new Error("不支持的周期");
  if (!Number.isInteger(numberOfDays) || numberOfDays < 1) throw new Error("持续天数必须为正整数");
  const anchor = nextAnchor(cycle, referenceDate, numberOfDays);
  const maxDays = naturalPeriodDays(cycle, anchor);
  if (numberOfDays > maxDays) {
    throw new Error(meta.unit === "week" ? "持续天数不能超过周周期" : meta.unit === "month" ? "持续天数不能超过当月自然天数" : "持续天数不能超过季度自然天数");
  }
  const start = meta.anchor === "start" ? anchor : addDays(anchor, -(numberOfDays - 1));
  const end = meta.anchor === "start" ? addDays(anchor, numberOfDays - 1) : anchor;
  return { start: formatDate(start), end: formatDate(end) };
}

export function cycleLabel(cycle) {
  return CYCLE_META[cycle]?.label || "—";
}

export function formatWindow(task) {
  if (task.nature === "temporary") return `${task.startAt || "—"} 至 ${task.endAt || "—"}`;
  return `${cycleLabel(task.cycle)} · ${task.window?.start || "—"} 至 ${task.window?.end || "—"}`;
}

export function validateTaskDraft(draft) {
  const errors = {};
  if (!String(draft.title || "").trim()) errors.title = "请输入任务标题";
  if (!String(draft.description || "").trim()) errors.description = "请输入任务说明";
  if (!draft.type) errors.type = "请选择任务类型";
  if (!draft.object) errors.object = "请选择任务对象";
  if (draft.object === "specified" && (!Array.isArray(draft.people) || draft.people.length === 0)) errors.object = "指定人员任务至少选择一名人员";
  if (!draft.nature) errors.nature = "请选择任务性质";
  if (draft.nature === "scheduled") {
    if (!draft.cycle) errors.cycle = "请选择周期";
    if (!Number.isInteger(Number(draft.duration)) || Number(draft.duration) < 1) errors.duration = "请输入持续天数";
    if (!errors.cycle && !errors.duration) {
      try {
        calculateWindow(draft.cycle, Number(draft.duration), draft.referenceDate || new Date());
      } catch (error) {
        errors.duration = error.message;
      }
    }
  }
  if (draft.nature === "temporary") {
    if (!draft.startAt) errors.startAt = "请选择开始时间";
    if (!draft.endAt) errors.endAt = "请选择截止时间";
    if (draft.startAt && draft.endAt && new Date(draft.endAt) < new Date(draft.startAt)) errors.endAt = "截止时间不能早于开始时间";
  }
  if (draft.requiresFile && (!Array.isArray(draft.fileTypes) || draft.fileTypes.length === 0)) errors.fileTypes = "至少选择一种允许文件类型";
  return { valid: Object.keys(errors).length === 0, errors };
}

export function validateTaskSubmission(task, fileName = "") {
  const errors = {};
  if (task.requiresFile && !String(fileName || "").trim()) errors.file = "请先选择文件后再提交";
  return { valid: Object.keys(errors).length === 0, errors };
}

export function linkifyDescription(description) {
  const source = String(description || "");
  const parts = [];
  const pattern = /https?:\/\/[^\s\u3000<>]+/gi;
  let cursor = 0;
  let match;
  while ((match = pattern.exec(source))) {
    if (match.index > cursor) parts.push({ type: "text", value: source.slice(cursor, match.index) });
    const raw = match[0];
    const trailing = raw.match(/[，。；！？、)）】】]+$/)?.[0] || "";
    const value = trailing ? raw.slice(0, -trailing.length) : raw;
    parts.push({ type: "link", value, href: value, target: "_blank", rel: "noreferrer noopener" });
    if (trailing) parts.push({ type: "text", value: trailing });
    cursor = match.index + raw.length;
  }
  if (cursor < source.length) parts.push({ type: "text", value: source.slice(cursor) });
  return parts.length ? parts : [{ type: "text", value: source }];
}

function nextId(tasks) {
  const numericIds = tasks.map((task) => Number(String(task.id).replace(/\D/g, ""))).filter(Number.isFinite);
  return `task-${Math.max(0, ...numericIds) + 1}`;
}

export function publishTask(tasks, draft, now = new Date()) {
  const validation = validateTaskDraft(draft);
  if (!validation.valid) {
    const error = new Error("任务信息未填写完整");
    error.validation = validation.errors;
    throw error;
  }
  const window = draft.nature === "scheduled" ? calculateWindow(draft.cycle, Number(draft.duration), draft.referenceDate || now) : null;
  const task = {
    id: draft.id || nextId(tasks),
    title: String(draft.title).trim(),
    description: String(draft.description).trim(),
    type: draft.type,
    nature: draft.nature,
    cycle: draft.nature === "scheduled" ? draft.cycle : "",
    duration: draft.nature === "scheduled" ? Number(draft.duration) : null,
    window,
    startAt: draft.nature === "temporary" ? draft.startAt : "",
    endAt: draft.nature === "temporary" ? draft.endAt : "",
    object: draft.object,
    objectLabel: draft.objectLabel || (draft.object === "all" ? "本组织全员" : "指定人员"),
    requiresFile: Boolean(draft.requiresFile),
    fileTypes: draft.requiresFile ? [...draft.fileTypes] : [],
    publishedAt: draft.publishedAt || formatDate(localDate(now)),
    status: "published",
    route: draft.type === "四大维度材料上报" ? "materials-entry" : "task-detail",
    stage: TASK_STAGES.REPORTER_PENDING,
    employeeStatus: "pending",
    branchApproved: false,
    submission: null,
    organizationOpinion: "",
    materialUploads: {},
  };
  return [...tasks, task];
}

export function getEmployeeTasks(tasks, asOf = new Date()) {
  const current = localDate(asOf);
  return tasks
    .filter((task) => {
      if (task.employeeStatus === "completed" || [TASK_STAGES.BRANCH_PENDING, TASK_STAGES.ORG_PENDING, TASK_STAGES.APPROVED, TASK_STAGES.REJECTED].includes(task.stage)) return true;
      if (task.nature === "temporary") return task.startAt && task.endAt && formatDate(current) >= task.startAt && formatDate(current) <= task.endAt;
      return task.window && formatDate(current) >= task.window.start && formatDate(current) <= task.window.end;
    })
    .map((task) => ({
      ...task,
      status: task.employeeStatus === "completed" || task.stage !== TASK_STAGES.REPORTER_PENDING ? "completed" : "pending",
    }));
}

export function completeTask(tasks, taskId) {
  return tasks.map((task) => task.id === taskId ? {
    ...task,
    employeeStatus: "completed",
    status: "completed",
    stage: TASK_STAGES.APPROVED,
  } : task);
}

function cloneTask(task, patch) {
  return { ...task, ...patch };
}

function updateTask(tasks, taskId, updater) {
  return tasks.map((task) => task.id === taskId ? updater(task) : task);
}

function nowLabel(value = new Date()) {
  const date = localDate(value);
  return `${formatDate(date)} 12:00`;
}

export function getTaskStage(task) {
  if (task?.stage) return task.stage;
  return task?.employeeStatus === "completed" ? TASK_STAGES.APPROVED : TASK_STAGES.REPORTER_PENDING;
}

/** 报送员临时任务提交后进入支部书记待处理，保留填报内容和附件名称供后续审核查看。 */
export function submitReporterTask(tasks, taskId, payload = {}) {
  return updateTask(tasks, taskId, (task) => cloneTask(task, {
    stage: TASK_STAGES.BRANCH_PENDING,
    employeeStatus: "completed",
    status: "submitted",
    branchApproved: false,
    submission: {
      content: String(payload.content || "").trim(),
      fileNames: Array.isArray(payload.fileNames) ? [...payload.fileNames] : [],
      submitter: payload.submitter || "张伟",
      branch: payload.branch || "党支部一",
      submittedAt: payload.submittedAt || nowLabel(),
    },
  }));
}

/** 支部书记先确认通过，状态仍在支部待处理，下一步才能提交组织审核。 */
export function approveBranchTask(tasks, taskId) {
  return updateTask(tasks, taskId, (task) => cloneTask(task, { branchApproved: true }));
}

export function submitBranchTask(tasks, taskId) {
  const target = tasks.find((task) => task.id === taskId);
  if (!target || !target.branchApproved) throw new Error("请先通过支部审核，再提交至组织审核");
  return updateTask(tasks, taskId, (task) => cloneTask(task, {
    stage: TASK_STAGES.ORG_PENDING,
    status: "submitted",
    branchSubmittedAt: nowLabel(),
  }));
}

export function approveOrganizationTask(tasks, taskId) {
  return updateTask(tasks, taskId, (task) => cloneTask(task, {
    stage: TASK_STAGES.APPROVED,
    status: "approved",
    organizationOpinion: "审核通过",
    organizationReviewedAt: nowLabel(),
  }));
}

export function rejectOrganizationTask(tasks, taskId, opinion = "请补充填报内容") {
  return updateTask(tasks, taskId, (task) => cloneTask(task, {
    stage: TASK_STAGES.REJECTED,
    status: "rejected",
    organizationOpinion: String(opinion || "请补充填报内容").trim(),
    organizationReviewedAt: nowLabel(),
  }));
}

export function getReportTabTasks(tasks, tab) {
  const groups = {
    pending: [TASK_STAGES.REPORTER_PENDING],
    review: [TASK_STAGES.BRANCH_PENDING, TASK_STAGES.ORG_PENDING],
    approved: [TASK_STAGES.APPROVED],
    rejected: [TASK_STAGES.REJECTED],
  };
  const accepted = groups[tab] || [];
  return tasks.filter((task) => accepted.includes(getTaskStage(task)));
}

export function getHistoryTaskGroups(tasks, tab) {
  const stageByTab = {
    review: [TASK_STAGES.BRANCH_PENDING, TASK_STAGES.ORG_PENDING],
    approved: [TASK_STAGES.APPROVED],
    rejected: [TASK_STAGES.REJECTED],
  };
  const scoped = (stageByTab[tab] || []).length
    ? tasks.filter((task) => stageByTab[tab].includes(getTaskStage(task)))
    : tasks;
  return {
    materials: scoped.filter((task) => task.type === "四大维度材料上报"),
    temporary: scoped.filter((task) => task.type !== "四大维度材料上报"),
  };
}

export function getPublishedTasks(tasks) {
  const publishedStatuses = ["published", "submitted", "approved", "rejected"];
  return tasks.filter((task) => Boolean(task?.publishedAt) || publishedStatuses.includes(task?.status));
}

export function historyStatusLabel(stage) {
  if (stage === TASK_STAGES.APPROVED) return "已通过";
  if (stage === TASK_STAGES.REJECTED) return "已驳回";
  return "审核中";
}

export function getBranchTabTasks(tasks, tab) {
  const groups = {
    pending: [TASK_STAGES.BRANCH_PENDING],
    review: [TASK_STAGES.ORG_PENDING],
    approved: [TASK_STAGES.APPROVED],
    rejected: [TASK_STAGES.REJECTED],
  };
  return tasks.filter((task) => (groups[tab] || []).includes(getTaskStage(task)));
}

export function addMaterialUpload(tasks, taskId, detail, fileName, uploadedAt = nowLabel()) {
  if (!MATERIAL_DETAIL_ITEMS.includes(detail)) throw new Error("不支持的材料明细项");
  return updateTask(tasks, taskId, (task) => {
    const previous = task.materialUploads?.[detail] || { count: 0, files: [] };
    return cloneTask(task, {
      materialUploads: {
        ...(task.materialUploads || {}),
        [detail]: {
          count: previous.count + 1,
          files: [...previous.files, { name: String(fileName || "材料文件"), uploadedAt }],
        },
      },
    });
  });
}

export function getMaterialCompletion(task) {
  const uploads = task?.materialUploads || {};
  const completed = MATERIAL_DETAIL_ITEMS.filter((detail) => Number(uploads[detail]?.count || 0) >= 1);
  return { completed, total: MATERIAL_DETAIL_ITEMS.length, complete: completed.length === MATERIAL_DETAIL_ITEMS.length };
}

export function buildExportPreview(task, selectedDetails = []) {
  const normalizedDetails = [...new Set(selectedDetails)].filter((detail) => MATERIAL_DETAIL_ITEMS.includes(detail));
  if (task.type === "四大维度材料上报" && normalizedDetails.length === 0) throw new Error("请先选择明细项");
  const submissions = Array.isArray(task.branchSubmissions) && task.branchSubmissions.length
    ? task.branchSubmissions
    : task.submission ? [task.submission] : [];
  const entries = [{ path: "任务填报明细.xlsx", type: "excel", columns: ["任务名称", "任务发布时间", "结束时间", "提交人", "提交时间", "填报内容"] }];
  const excelRows = submissions.map((submission) => ({
    taskName: task.title,
    publishedAt: task.publishedAt || "—",
    endAt: task.nature === "scheduled" ? task.window?.end || "—" : task.endAt || "—",
    submitter: submission.submitter || "—",
    submittedAt: submission.submittedAt || "—",
    content: submission.content || "—",
    branch: submission.branch || "党支部一",
  }));
  if (task.type === "四大维度材料上报") {
    normalizedDetails.forEach((detail) => {
      const record = task.materialUploads?.[detail];
      entries.push({ path: `四维明细/${detail}`, type: "material-detail", detail });
      (record?.files || []).forEach((file) => entries.push({ path: `四维明细/${detail}/${file.name}`, type: "material", detail }));
    });
  } else {
    submissions.forEach((submission) => {
      const branch = submission.branch || "党支部一";
      (Array.isArray(submission.fileNames) ? submission.fileNames : []).forEach((name) => entries.push({ path: `${branch}/${name}`, type: "attachment" }));
    });
  }
  return {
    archiveName: `${task.title}-填报导出.zip`,
    selectedDetails: normalizedDetails,
    excelColumns: ["任务名称", "任务发布时间", "结束时间", "提交人", "提交时间", "填报内容"],
    excelRows,
    entries,
    note: "原型模拟生成结构预览，不生成真实 ZIP 文件",
  };
}

export const TASK_TYPES = ["四大维度材料上报", "党建学习", "通知确认", "整改反馈", "其他"];
export const FILE_TYPES = ["PDF", "Word", "Excel", "图片", "压缩包"];
export const CYCLES = Object.entries(CYCLE_META).map(([value, meta]) => ({ value, label: meta.label }));
