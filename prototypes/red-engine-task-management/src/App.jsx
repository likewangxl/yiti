import { useEffect, useMemo, useState } from "react";
import {
  IconAlertCircle as AlertCircle,
  IconArrowLeft as ArrowLeft,
  IconBuilding as Building2,
  IconCalendarEvent as CalendarEvent,
  IconCheck as Check,
  IconCircleCheck as CheckCircle2,
  IconChevronLeft as ChevronLeft,
  IconChevronRight as ChevronRight,
  IconClipboardCheck as ClipboardCheck,
  IconClipboardList as ClipboardList,
  IconClock as Clock3,
  IconCloudDownload as CloudDownload,
  IconDownload as Download,
  IconExternalLink as ExternalLink,
  IconEye as Eye,
  IconFileCheck as FileCheck2,
  IconFilePencil as FilePenLine,
  IconFileText as FileText,
  IconFilter as Filter,
  IconHome as Home,
  IconLayoutDashboard as LayoutDashboard,
  IconLink as Link2,
  IconList as ListTodo,
  IconMessageCircle as MessageCircle,
  IconPaperclip as Paperclip,
  IconPlus as Plus,
  IconRefresh as RotateCcw,
  IconSearch as Search,
  IconSettings as Settings,
  IconStar as Star,
  IconTrophy as Trophy,
  IconUpload as Upload,
  IconCloudUpload as UploadCloud,
  IconUsers as Users,
  IconX as X,
} from "@tabler/icons-react";
import {
  CYCLES,
  FILE_TYPES,
  MATERIAL_DETAIL_ITEMS,
  REPORT_TABS,
  TASK_STAGES,
  TASK_TYPES,
  addMaterialUpload,
  approveBranchTask,
  approveOrganizationTask,
  buildExportPreview,
  calculateWindow,
  cycleLabel,
  getBranchTabTasks,
  getEmployeeTasks,
  getHistoryTaskGroups,
  getMaterialCompletion,
  getPublishedTasks,
  getReportTabTasks,
  historyStatusLabel,
  linkifyDescription,
  publishTask,
  rejectOrganizationTask,
  submitBranchTask,
  submitReporterTask,
  validateTaskDraft,
  validateTaskSubmission,
} from "./task-domain.js";
import {
  ROLE_OPTIONS,
  getDefaultView,
  getRoleLabel,
  getVisibleMenuKeys,
} from "./role-access.js";

const PAGE_SIZE = 5;
const PEOPLE = [
  { value: "EMP001", label: "EMP001 · 张伟" },
  { value: "EMP002", label: "EMP002 · 李娜" },
  { value: "EMP003", label: "EMP003 · 王强" },
  { value: "EMP004", label: "EMP004 · 赵敏" },
];

const menuItems = [
  { key: "home", label: "首页", Icon: Home },
  { key: "materials", label: "四大维度材料上报", Icon: FilePenLine },
  { key: "records", label: "上报信息", Icon: FileText },
  { key: "review", label: "支部审核工作台", Icon: ClipboardCheck },
  { key: "dashboard", label: "全局数据驾驶舱", Icon: LayoutDashboard },
  { key: "warning", label: "红黄牌预警池", Icon: AlertCircle },
  { key: "workbench", label: "工作台", Icon: FileCheck2 },
  { key: "archive", label: "年度考核归档", Icon: Trophy },
  { key: "export", label: "数据导出", Icon: Download },
  { key: "org", label: "党组织管理", Icon: Settings },
  { key: "mapping", label: "用户党组织映射", Icon: Link2 },
  { key: "management", label: "任务管理", Icon: ClipboardList, isNew: true },
];

function toDate(value) {
  return value instanceof Date ? value : new Date(value);
}

function isoDate(value) {
  const date = toDate(value);
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, "0"), String(date.getDate()).padStart(2, "0")].join("-");
}

function shiftDate(value, days) {
  const date = toDate(value);
  return isoDate(new Date(date.getFullYear(), date.getMonth(), date.getDate() + days));
}

function makeSubmission(options) {
  const value = options || {};
  return {
    branch: value.branch || "党支部一",
    submitter: value.submitter || "张伟",
    content: value.content || "已完成本次任务填报。",
    submittedAt: value.submittedAt || "2026-08-27 10:20",
    fileNames: value.fileNames || [],
  };
}

function makeTask(task) {
  return {
    status: "published",
    object: "all",
    objectLabel: "本组织全员",
    requiresFile: false,
    fileTypes: [],
    cycle: "",
    duration: null,
    startAt: "",
    endAt: "",
    window: null,
    route: task.type === "四大维度材料上报" ? "materials-entry" : "task-detail",
    branchApproved: false,
    submission: null,
    branchSubmissions: [],
    organizationOpinion: "",
    materialUploads: {},
    ...task,
  };
}

function seedTasks() {
  const today = new Date();
  const activeStart = shiftDate(today, -2);
  const activeEnd = shiftDate(today, 7);
  const monthEnd = new Date(today.getFullYear(), today.getMonth() + 1, 0);
  const monthStart = new Date(today.getFullYear(), today.getMonth(), 1);
  const quarterStart = new Date(today.getFullYear(), Math.floor(today.getMonth() / 3) * 3, 1);
  const materialStart = isoDate(today);
  const materialEnd = isoDate(monthEnd);
  return [
    makeTask({
      id: "task-101",
      title: "临时任务：网点服务情况填报",
      description: "请各党支部汇总网点服务情况并在线填报，参考资料：https://example.com/service-guide",
      type: "整改反馈",
      nature: "temporary",
      startAt: activeStart,
      endAt: activeEnd,
      requiresFile: true,
      fileTypes: ["PDF", "Word"],
      publishedAt: shiftDate(today, -3),
      stage: TASK_STAGES.REPORTER_PENDING,
      employeeStatus: "pending",
    }),
    makeTask({
      id: "task-102",
      title: "本季度四大维度材料上报",
      description: "请按现有上报要求完成本季度材料整理，支持每个明细项多次上传。",
      type: "四大维度材料上报",
      nature: "scheduled",
      cycle: "quarterly-end",
      duration: 5,
      window: { start: materialStart, end: materialEnd },
      requiresFile: true,
      fileTypes: ["PDF", "Word", "Excel"],
      publishedAt: shiftDate(today, -6),
      stage: TASK_STAGES.REPORTER_PENDING,
      employeeStatus: "pending",
      materialUploads: {
        "联建规范度": { count: 1, files: [{ name: "联建规范度.pdf", uploadedAt: "2026-08-27 09:10" }] },
      },
    }),
    makeTask({
      id: "task-103",
      title: "支部通知确认",
      description: "请阅读通知并完成确认。",
      type: "通知确认",
      nature: "scheduled",
      cycle: "monthly-start",
      duration: 5,
      window: { start: isoDate(monthStart), end: shiftDate(monthStart, 4) },
      publishedAt: shiftDate(monthStart, -2),
      stage: TASK_STAGES.BRANCH_PENDING,
      employeeStatus: "completed",
      submission: makeSubmission({ content: "党支部一已完成通知确认，相关事项均已传达。", fileNames: [] }),
      branchSubmissions: [makeSubmission({ content: "党支部一已完成通知确认，相关事项均已传达。" })],
    }),
    makeTask({
      id: "task-104",
      title: "专项整改情况填报",
      description: "请填报整改进展，并上传佐证材料。",
      type: "整改反馈",
      nature: "temporary",
      startAt: activeStart,
      endAt: activeEnd,
      requiresFile: true,
      fileTypes: ["PDF", "图片"],
      publishedAt: shiftDate(today, -8),
      stage: TASK_STAGES.ORG_PENDING,
      employeeStatus: "completed",
      branchApproved: true,
      submission: makeSubmission({ branch: "党支部一", content: "已完成整改事项 4 项，剩余 1 项预计本周完成。", fileNames: ["整改说明.pdf", "现场照片.jpg"] }),
      branchSubmissions: [
        makeSubmission({ branch: "党支部一", content: "已完成整改事项 4 项，剩余 1 项预计本周完成。", fileNames: ["整改说明.pdf", "现场照片.jpg"] }),
        makeSubmission({ branch: "党支部二", submitter: "李娜", content: "整改事项已全部完成，附件为佐证材料。", submittedAt: "2026-08-27 14:06", fileNames: ["整改闭环.docx"] }),
      ],
    }),
    makeTask({
      id: "task-105",
      title: "季度党员教育培训报名",
      description: "请在截止时间前完成培训报名。",
      type: "党建学习",
      nature: "scheduled",
      cycle: "quarterly-start",
      duration: 7,
      window: { start: isoDate(quarterStart), end: shiftDate(quarterStart, 6) },
      publishedAt: shiftDate(quarterStart, -6),
      stage: TASK_STAGES.APPROVED,
      employeeStatus: "completed",
      submission: makeSubmission({ branch: "党支部二", submitter: "李娜", content: "已完成培训报名。", submittedAt: "2026-08-20 15:10" }),
      branchSubmissions: [makeSubmission({ branch: "党支部二", submitter: "李娜", content: "已完成培训报名。", submittedAt: "2026-08-20 15:10" })],
      organizationOpinion: "审核通过",
    }),
    makeTask({
      id: "task-106",
      title: "八月整改反馈收集",
      description: "请补充整改进展，必要时附上佐证材料。",
      type: "整改反馈",
      nature: "temporary",
      startAt: shiftDate(today, -18),
      endAt: shiftDate(today, 4),
      requiresFile: true,
      fileTypes: ["PDF", "图片"],
      publishedAt: shiftDate(today, -20),
      stage: TASK_STAGES.REJECTED,
      employeeStatus: "completed",
      submission: makeSubmission({ branch: "党支部三", submitter: "王强", content: "已提交阶段性整改情况。", fileNames: ["阶段说明.pdf"], submittedAt: "2026-08-25 09:30" }),
      branchSubmissions: [makeSubmission({ branch: "党支部三", submitter: "王强", content: "已提交阶段性整改情况。", fileNames: ["阶段说明.pdf"], submittedAt: "2026-08-25 09:30" })],
      organizationOpinion: "请补充整改前后对比材料",
    }),
    makeTask({
      id: "task-107",
      title: "上季度材料导出示例",
      description: "四大维度材料任务的历史填报示例。",
      type: "四大维度材料上报",
      nature: "scheduled",
      cycle: "quarterly-end",
      duration: 5,
      window: { start: shiftDate(today, -30), end: shiftDate(today, -26) },
      requiresFile: true,
      fileTypes: ["PDF", "Excel"],
      publishedAt: shiftDate(today, -45),
      stage: TASK_STAGES.APPROVED,
      employeeStatus: "completed",
      submission: makeSubmission({ branch: "党支部二", submitter: "李娜", content: "四大维度明细已完成。", fileNames: ["季度材料.xlsx"] }),
      branchSubmissions: [makeSubmission({ branch: "党支部二", submitter: "李娜", content: "四大维度明细已完成。", fileNames: ["季度材料.xlsx"] })],
      organizationOpinion: "审核通过",
      materialUploads: {
        "联建规范度": { count: 2, files: [{ name: "联建规范度-1.pdf", uploadedAt: "2026-07-24 09:10" }, { name: "联建规范度-2.pdf", uploadedAt: "2026-07-26 13:20" }] },
        "合作契约化": { count: 1, files: [{ name: "合作契约化.xlsx", uploadedAt: "2026-07-25 10:30" }] },
        "业务转换实质": { count: 1, files: [{ name: "业务转换实质.pdf", uploadedAt: "2026-07-25 11:00" }] },
      },
    }),
  ];
}

function seedOverdueItems() {
  const today = new Date();
  return [
    { id: "overdue-1", taskType: "四大维度材料上报", taskName: "上季度材料补报", content: "季度末材料未在窗口内完成上报", branch: "党支部三", startAt: shiftDate(today, -22), endAt: shiftDate(today, -17), submitter: "王强", submittedAt: shiftDate(today, -15), score: "", deducted: false },
    { id: "overdue-2", taskType: "临时任务", taskName: "整改情况回访", content: "逾期后补充提交回访记录", branch: "党支部二", startAt: shiftDate(today, -16), endAt: shiftDate(today, -10), submitter: "李娜", submittedAt: shiftDate(today, -8), score: "", deducted: false },
  ];
}

function Tag({ tone = "neutral", children }) {
  return <span className={"tag tag-" + tone}>{children}</span>;
}

function FieldError({ children }) {
  return children ? <div className="field-error">{children}</div> : null;
}

function Pagination({ page, total, onChange }) {
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  return <div className="pagination"><span className="pagination-total">共 {total} 条</span><button aria-label="上一页" className="page-button" disabled={page <= 1} onClick={() => onChange(page - 1)}><ChevronLeft size={15} /></button>{Array.from({ length: pages }, (_, index) => index + 1).map((value) => <button key={value} className={"page-number " + (page === value ? "is-active" : "")} onClick={() => onChange(value)}>{value}</button>)}<button aria-label="下一页" className="page-button" disabled={page >= pages} onClick={() => onChange(page + 1)}><ChevronRight size={15} /></button></div>;
}

function PageHeading({ title, subtitle, action }) {
  return <div className="page-heading"><div><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>{action}</div>;
}

function App() {
  const popupParams = typeof window === "undefined" ? null : new URLSearchParams(window.location.search);
  const popupTaskId = popupParams?.get("task") || "";
  const isPopup = popupParams?.get("popup") === "1" && Boolean(popupTaskId);
  const [role, setRole] = useState("admin");
  const [view, setView] = useState(() => isPopup ? "popup-detail" : getDefaultView("admin"));
  const [tasks, setTasks] = useState(() => seedTasks());
  const [selectedTaskId, setSelectedTaskId] = useState(() => popupTaskId || "");
  const [notice, setNotice] = useState("");
  const [overdueItems, setOverdueItems] = useState(() => seedOverdueItems());
  const selectedTask = tasks.find((task) => task.id === selectedTaskId) || null;

  const go = (nextView) => {
    setNotice("");
    setSelectedTaskId("");
    setView(nextView);
  };

  const openReporterTask = (task) => {
    if (task.type === "四大维度材料上报") {
      setSelectedTaskId(task.id);
      setView("materials-entry");
      return;
    }
    const url = window.location.pathname + "?popup=1&task=" + encodeURIComponent(task.id);
    const popup = window.open(url, "red-engine-task-entry", "width=1000,height=820,resizable=yes,scrollbars=yes");
    if (!popup) {
      setSelectedTaskId(task.id);
      setView("detail");
      setNotice("浏览器阻止了新窗口，已切换为当前窗口填报");
    }
  };

  const openManagementTask = (task) => {
    setSelectedTaskId(task.id);
    setView("management-detail");
  };

  const commitReporterSubmission = (taskId, payload) => {
    setTasks((current) => submitReporterTask(current, taskId, payload));
    if (!isPopup) {
      setNotice("填报已提交，任务进入审核中");
      setSelectedTaskId("");
      setView("records");
    }
  };

  useEffect(() => {
    const onMessage = (event) => {
      if (event.origin !== window.location.origin || !event.data || event.data.type !== "red-engine-task-submit") return;
      const data = event.data;
      setTasks((current) => submitReporterTask(current, data.taskId, { content: data.content, fileNames: data.fileNames, submitter: data.submitter, branch: data.branch }));
      setNotice("临时任务已由报送员提交，现进入审核中");
    };
    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, []);

  const handlePublished = (draft) => {
    try {
      setTasks((current) => publishTask(current, draft));
      setNotice("任务发布成功，已加入任务管理列表");
      setView("management");
    } catch (error) {
      setNotice(error.message);
    }
  };

  const handleBranchApprove = (taskId) => {
    setTasks((current) => approveBranchTask(current, taskId));
    setNotice("支部审核已通过，请继续提交至组织审核");
  };

  const handleBranchSubmit = (taskId) => {
    try {
      setTasks((current) => submitBranchTask(current, taskId));
      setNotice("已提交至组织审核，任务进入审核中");
    } catch (error) {
      setNotice(error.message);
    }
  };

  const handleOrganizationApprove = (taskId) => {
    setTasks((current) => approveOrganizationTask(current, taskId));
    setNotice("组织审核已通过");
  };

  const handleOrganizationReject = (taskId, opinion) => {
    setTasks((current) => rejectOrganizationTask(current, taskId, opinion));
    setNotice("任务已驳回，已回退至报送员端已驳回页");
  };

  const handleMaterialUpload = (taskId, detail, fileName) => {
    try {
      setTasks((current) => addMaterialUpload(current, taskId, detail, fileName));
      const task = tasks.find((item) => item.id === taskId);
      const nextCount = (task?.materialUploads?.[detail]?.count || 0) + 1;
      setNotice(detail + "已记录第 " + nextCount + " 次上传");
    } catch (error) {
      setNotice(error.message);
    }
  };

  const handleOverdueScore = (itemId, score) => {
    setOverdueItems((current) => current.map((item) => item.id === itemId ? { ...item, score: score, deducted: true } : item));
    setNotice("扣分已提交");
  };

  if (isPopup && selectedTask) {
    return <TemporaryTaskPopup task={selectedTask} onSubmit={(payload) => {
      window.opener?.postMessage({ type: "red-engine-task-submit", taskId: selectedTask.id, ...payload }, window.location.origin);
      commitReporterSubmission(selectedTask.id, payload);
    }} />;
  }

  const currentTitle = menuItems.find((item) => item.key === view)?.label;
  const reporterTasks = getEmployeeTasks(tasks, new Date());
  const visibleMenuKeys = getVisibleMenuKeys(role);
  const visibleMenuItems = menuItems.filter((item) => visibleMenuKeys.includes(item.key));

  return <div className="app-shell"><aside className="sidebar"><div className="brand"><span className="brand-mark"><Star size={19} strokeWidth={1.8} /></span><span>红色引擎</span></div><nav className="side-nav" aria-label="主导航">{visibleMenuItems.map(({ key, label, Icon, isNew }) => <button key={key} className={"side-nav-item " + (view === key ? "is-active" : "")} onClick={() => go(key)}><Icon size={16} strokeWidth={1.8} /><span>{label}</span>{isNew && <span className="new-mark">新增</span>}</button>)}</nav><div className="sidebar-foot">红色引擎工作平台<br /><span>原型演示环境</span></div></aside><section className="main-area"><header className="topbar"><div className="system-title">红色引擎工程管理系统</div><div className="topbar-right"><span className="prototype-label">原型演示</span><label className="role-switch"><span>演示身份</span><select value={role} onChange={(event) => { const nextRole = event.target.value; setRole(nextRole); go(getDefaultView(nextRole)); }} aria-label="演示身份">{ROLE_OPTIONS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label><button className="logout-button">退出</button></div></header><main className="content-area">{notice && <div className="notice success"><CheckCircle2 size={17} />{notice}<button aria-label="关闭提示" onClick={() => setNotice("")}><X size={16} /></button></div>}{view === "management" && <TaskManagementPage tasks={tasks} onAdd={() => go("new")} onView={openManagementTask} />}{view === "new" && <NewTaskPage onBack={() => go("management")} onPublish={handlePublished} />}{view === "records" && <ReportInformationPage tasks={tasks} onOpen={openReporterTask} role={role} />}{view === "review" && <BranchReviewPage tasks={tasks} onApprove={handleBranchApprove} onSubmit={handleBranchSubmit} />}{view === "workbench" && <OrganizationWorkbenchPage tasks={tasks} onApprove={handleOrganizationApprove} onReject={handleOrganizationReject} />}{view === "management-detail" && selectedTask && <TaskManagementDetailPage task={selectedTask} onBack={() => go("management")} />}{view === "detail" && selectedTask && <TemporaryTaskDetail task={selectedTask} onBack={() => go("records")} onSubmit={(payload) => commitReporterSubmission(selectedTask.id, payload)} />}{view === "materials-entry" && selectedTask && <MaterialsEntryPage task={selectedTask} onBack={() => go("records")} onUpload={handleMaterialUpload} />}{view === "home" && <HomePage tasks={reporterTasks} onOpen={openReporterTask} role={role} />}{view === "warning" && <WarningPoolPage role={role} overdueItems={overdueItems} onScore={handleOverdueScore} />}{["materials", "dashboard", "archive", "export", "org", "mapping"].includes(view) && <LegacyPage title={currentTitle} role={role} view={view} />}</main></section></div>;
}

function TaskManagementPage({ tasks, onAdd, onView }) {
  const [filters, setFilters] = useState({ title: "", nature: "", type: "", cycle: "" });
  const [applied, setApplied] = useState(filters);
  const [page, setPage] = useState(1);
  const filtered = useMemo(() => getPublishedTasks(tasks).filter((task) => (!applied.title || task.title.includes(applied.title)) && (!applied.nature || task.nature === applied.nature) && (!applied.type || task.type === applied.type) && (!applied.cycle || task.cycle === applied.cycle)), [tasks, applied]);
  const rows = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const reset = () => { const next = { title: "", nature: "", type: "", cycle: "" }; setFilters(next); setApplied(next); setPage(1); };
  return <div className="page-wrap"><PageHeading title="任务管理" subtitle="组织管理员查看已发布任务，组织审核员可查看填报与导出结果" action={<button className="primary-button" onClick={onAdd}><Plus size={16} />新增任务</button>} /><section className="filter-card panel-card"><div className="filter-row"><label className="filter-field"><span>任务标题</span><input value={filters.title} onChange={(event) => setFilters({ ...filters, title: event.target.value })} placeholder="请输入任务标题" /></label><label className="filter-field"><span>任务性质</span><select value={filters.nature} onChange={(event) => setFilters({ ...filters, nature: event.target.value })}><option value="">全部</option><option value="scheduled">定时任务</option><option value="temporary">临时任务</option></select></label><label className="filter-field"><span>任务类型</span><select value={filters.type} onChange={(event) => setFilters({ ...filters, type: event.target.value })}><option value="">全部</option>{TASK_TYPES.map((type) => <option key={type}>{type}</option>)}</select></label><label className="filter-field"><span>周期</span><select value={filters.cycle} onChange={(event) => setFilters({ ...filters, cycle: event.target.value })}><option value="">全部</option>{CYCLES.map((cycle) => <option key={cycle.value} value={cycle.value}>{cycle.label}</option>)}</select></label><div className="filter-actions"><button className="primary-button compact" onClick={() => { setApplied({ ...filters }); setPage(1); }}><Search size={15} />查询</button><button className="plain-button compact" onClick={reset}><RotateCcw size={15} />重置</button></div></div></section><section className="panel-card table-card"><div className="card-title-row"><div><h2>已发布任务</h2><span>点击任务可查看各支部填报时间、内容和附件</span></div><span className="result-count">共 {filtered.length} 条</span></div><div className="table-scroll"><table><thead><tr><th>任务标题</th><th>任务类型</th><th>任务性质</th><th>周期 / 时间窗</th><th>任务对象</th><th>填报情况</th><th>发布时间</th><th>操作</th></tr></thead><tbody>{rows.length ? rows.map((task) => <tr key={task.id}><td><button className="title-link" onClick={() => onView(task)}>{task.title}<Eye size={13} /></button></td><td><Tag tone={task.type === "四大维度材料上报" ? "red" : "blue"}>{task.type}</Tag></td><td>{task.nature === "scheduled" ? "定时任务" : "临时任务"}</td><td><span className="window-cell">{task.nature === "scheduled" ? cycleLabel(task.cycle) + " · " + task.window?.start + " 至 " + task.window?.end : task.startAt + " 至 " + task.endAt}</span></td><td>{task.objectLabel}</td><td>{(task.branchSubmissions?.length || (task.submission ? 1 : 0)) ? <Tag tone="green">已填报 {task.branchSubmissions?.length || 1} 个支部</Tag> : <span className="muted">待填报</span>}</td><td>{task.publishedAt}</td><td><button className="link-button" onClick={() => onView(task)}>查看详情</button></td></tr>) : <tr><td colSpan="8"><EmptyState title="暂无匹配任务" description="调整查询条件或新增一条任务" /></td></tr>}</tbody></table></div><Pagination page={page} total={filtered.length} onChange={setPage} /></section></div>;
}

function NewTaskPage({ onBack, onPublish }) {
  const today = isoDate(new Date());
  const [draft, setDraft] = useState({ nature: "scheduled", type: "党建学习", title: "", description: "", object: "all", people: [], cycle: "monthly-end", duration: 5, startAt: today, endAt: shiftDate(new Date(), 7), requiresFile: false, fileTypes: [] });
  const [errors, setErrors] = useState({});
  const preview = useMemo(() => { if (draft.nature !== "scheduled" || !draft.cycle || !draft.duration) return null; try { return calculateWindow(draft.cycle, Number(draft.duration), new Date()); } catch (error) { return { error: error.message }; } }, [draft.nature, draft.cycle, draft.duration]);
  const change = (key, value) => { setDraft((current) => ({ ...current, [key]: value })); setErrors((current) => { const next = { ...current }; delete next[key]; if (key === "people") delete next.object; if (key === "requiresFile" && !value) delete next.fileTypes; return next; }); };
  const submit = () => { const result = validateTaskDraft(draft); setErrors(result.errors); if (result.valid) onPublish({ ...draft, objectLabel: draft.object === "all" ? "本组织全员" : "指定 " + draft.people.length + " 人" }); };
  const toggleFileType = (type) => change("fileTypes", draft.fileTypes.includes(type) ? draft.fileTypes.filter((item) => item !== type) : [...draft.fileTypes, type]);
  return <div className="page-wrap form-page"><PageHeading title="新增任务" subtitle="面向一线业务人员提供简单的定时或临时任务配置" action={<button className="plain-button" onClick={onBack}><ArrowLeft size={16} />返回任务管理</button>} /><section className="panel-card form-card"><div className="form-section-title"><span>基础信息</span><small>带 <em>*</em> 字段为必填项</small></div><div className="form-grid"><label className="form-field required"><span>任务性质</span><div className="segmented"><button type="button" className={draft.nature === "scheduled" ? "selected" : ""} onClick={() => change("nature", "scheduled")}>定时任务</button><button type="button" className={draft.nature === "temporary" ? "selected" : ""} onClick={() => change("nature", "temporary")}>临时任务</button></div><FieldError>{errors.nature}</FieldError></label><label className="form-field required"><span>任务类型</span><select value={draft.type} onChange={(event) => change("type", event.target.value)}><option value="">请选择任务类型</option>{TASK_TYPES.map((type) => <option key={type}>{type}</option>)}</select><FieldError>{errors.type}</FieldError></label><label className="form-field required full"><span>任务标题</span><input value={draft.title} onChange={(event) => change("title", event.target.value)} placeholder="请输入任务标题" maxLength="60" /><div className="input-count">{draft.title.length}/60</div><FieldError>{errors.title}</FieldError></label><label className="form-field required full"><span>任务说明</span><textarea value={draft.description} onChange={(event) => change("description", event.target.value)} placeholder="请输入任务说明，可粘贴网页链接，处理时展示为可点击链接" rows="4" /><FieldError>{errors.description}</FieldError></label><fieldset className="form-field required full"><legend>任务对象</legend><div className="object-options"><label><input type="radio" checked={draft.object === "all"} onChange={() => change("object", "all")} />本组织全员</label><label><input type="radio" checked={draft.object === "specified"} onChange={() => change("object", "specified")} />指定人员</label></div>{draft.object === "specified" && <select className="people-select" multiple value={draft.people} onChange={(event) => change("people", Array.from(event.target.selectedOptions, (option) => option.value))}>{PEOPLE.map((person) => <option key={person.value} value={person.value}>{person.label}</option>)}</select>}<FieldError>{errors.object || (draft.object === "specified" && draft.people.length === 0 ? "请选择至少一名人员" : "")}</FieldError></fieldset></div><div className="form-divider" /><div className="form-section-title"><span>{draft.nature === "scheduled" ? "定时规则" : "临时时间"}</span><small>{draft.nature === "scheduled" ? "按下一个完整周期生效" : "临时任务必须补充开始与截止时间"}</small></div>{draft.nature === "scheduled" ? <div className="form-grid schedule-grid"><label className="form-field required"><span>周期</span><select value={draft.cycle} onChange={(event) => change("cycle", event.target.value)}><option value="">请选择周期</option>{CYCLES.map((cycle) => <option key={cycle.value} value={cycle.value}>{cycle.label}</option>)}</select><FieldError>{errors.cycle}</FieldError></label><label className="form-field required"><span>持续天数</span><div className="unit-input"><input type="number" min="1" value={draft.duration} onChange={(event) => change("duration", event.target.value === "" ? "" : Number(event.target.value))} /><span>天</span></div><FieldError>{errors.duration}</FieldError></label><div className={"window-preview " + (preview?.error ? "has-error" : "")}><div className="preview-icon"><Clock3 size={17} /></div><div><span>下一窗口自动预览</span><strong>{preview?.error || (preview ? preview.start + " 至 " + preview.end : "选择周期和持续天数后预览")}</strong><small>自然日含首尾，持续时间不能超过周期</small></div></div></div> : <div className="form-grid schedule-grid"><label className="form-field required"><span>开始时间</span><input type="date" value={draft.startAt} onChange={(event) => change("startAt", event.target.value)} /><FieldError>{errors.startAt}</FieldError></label><label className="form-field required"><span>截止时间</span><input type="date" value={draft.endAt} onChange={(event) => change("endAt", event.target.value)} /><FieldError>{errors.endAt}</FieldError></label><div className="window-preview"><div className="preview-icon"><CalendarEvent size={17} /></div><div><span>临时任务时间窗</span><strong>{draft.startAt || "—"} 至 {draft.endAt || "—"}</strong><small>时间窗内为收到任务的人员生成首页待办</small></div></div></div>}<div className="form-divider" /><div className="form-section-title"><span>附件要求</span><small>可按任务需要开启文件上传</small></div><div className="form-grid attachment-grid"><div className="form-field"><span>是否要求上传文件</span><div className="segmented"><button type="button" className={!draft.requiresFile ? "selected" : ""} onClick={() => change("requiresFile", false)}>否</button><button type="button" className={draft.requiresFile ? "selected" : ""} onClick={() => change("requiresFile", true)}>是</button></div></div>{draft.requiresFile && <fieldset className="form-field required"><legend>允许文件类型</legend><div className="checkbox-options">{FILE_TYPES.map((type) => <label key={type}><input type="checkbox" checked={draft.fileTypes.includes(type)} onChange={() => toggleFileType(type)} />{type}</label>)}</div><FieldError>{errors.fileTypes}</FieldError></fieldset>}</div><div className="form-footer"><button className="plain-button" onClick={onBack}>取消</button><button className="primary-button" onClick={submit}><Check size={16} />发布任务</button></div></section></div>;
}

function QueryBar({ filters, setFilters, onQuery, onReset, includeStatus = false }) {
  return <section className="filter-card panel-card"><div className="filter-row"><label className="filter-field"><span>任务标题</span><input value={filters.title} onChange={(event) => setFilters({ ...filters, title: event.target.value })} placeholder="请输入任务标题" /></label><label className="filter-field"><span>任务性质</span><select value={filters.nature} onChange={(event) => setFilters({ ...filters, nature: event.target.value })}><option value="">全部</option><option value="scheduled">定时任务</option><option value="temporary">临时任务</option></select></label>{includeStatus && <label className="filter-field"><span>处理状态</span><select value={filters.status} onChange={(event) => setFilters({ ...filters, status: event.target.value })}><option value="">全部</option><option value="pending">待处理</option><option value="review">审核中</option><option value="approved">已通过</option><option value="rejected">已驳回</option></select></label>}<label className="filter-field"><span>周期</span><select value={filters.cycle} onChange={(event) => setFilters({ ...filters, cycle: event.target.value })}><option value="">全部</option>{CYCLES.map((cycle) => <option key={cycle.value} value={cycle.value}>{cycle.label}</option>)}</select></label><div className="filter-actions"><button className="primary-button compact" onClick={onQuery}><Search size={15} />查询</button><button className="plain-button compact" onClick={onReset}><RotateCcw size={15} />重置</button></div></div></section>;
}

function ReportInformationPage({ tasks, onOpen, role }) {
  const [tab, setTab] = useState("pending");
  const [filters, setFilters] = useState({ title: "", nature: "", cycle: "" });
  const [applied, setApplied] = useState(filters);
  const [page, setPage] = useState(1);
  const reset = () => { const next = { title: "", nature: "", cycle: "" }; setFilters(next); setApplied(next); setPage(1); };
  const filtered = useMemo(() => getReportTabTasks(tasks, tab).filter((task) => (!applied.title || task.title.includes(applied.title)) && (!applied.nature || task.nature === applied.nature) && (!applied.cycle || task.cycle === applied.cycle)), [tasks, tab, applied]);
  const rows = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const statusText = tab === "pending" ? "待处理" : tab === "review" ? "审核中" : tab === "approved" ? "已通过" : "已驳回";
  return <div className="page-wrap"><PageHeading title="上报信息" subtitle={getRoleLabel(role) + "的任务填报与历史信息统一在此查看"} /><section className="tab-panel panel-card"><div className="tabs" role="tablist">{REPORT_TABS.map((item) => <button key={item.value} role="tab" aria-selected={tab === item.value} className={"tab-button " + (tab === item.value ? "is-active" : "")} onClick={() => { setTab(item.value); setPage(1); }}>{item.label}<span>{getReportTabTasks(tasks, item.value).length}</span></button>)}</div></section>{(tab === "pending" || tab === "review") && <QueryBar filters={filters} setFilters={setFilters} onQuery={() => { setApplied({ ...filters }); setPage(1); }} onReset={reset} />}<section className="panel-card table-card"><div className="card-title-row"><div><h2>{statusText}</h2><span>{tab === "pending" ? "点击标题处理任务，四大维度材料任务进入既有材料上报页面" : tab === "review" ? "报送员提交后进入审核流转，状态在这里持续更新" : "历史任务按原展示逻辑保留"}</span></div><span className="result-count">共 {filtered.length} 条</span></div>{tab !== "pending" ? <HistoryTables rows={rows} tab={tab} /> : <div className="table-scroll"><table><thead><tr><th>任务标题</th><th>任务说明</th><th>任务性质</th><th>周期 / 时间窗</th><th>截止时间</th><th>操作</th></tr></thead><tbody>{rows.length ? rows.map((task) => <tr key={task.id}><td><button className="title-link" onClick={() => onOpen(task)}>{task.title}<ExternalLink size={13} /></button></td><td><span className="ellipsis" title={task.description}>{task.description}</span></td><td>{task.nature === "scheduled" ? "定时任务" : "临时任务"}</td><td>{task.nature === "scheduled" ? cycleLabel(task.cycle) + " · " + task.window?.start + " 至 " + task.window?.end : "临时任务"}</td><td>{task.nature === "scheduled" ? task.window?.end : task.endAt}</td><td><button className="link-button" onClick={() => onOpen(task)}>{task.type === "四大维度材料上报" ? "进入材料上报" : "打开填报窗口"}</button></td></tr>) : <tr><td colSpan="6"><EmptyState title={"暂无" + statusText + "任务"} description="调整查询条件后重试" /></td></tr>}</tbody></table></div>}<Pagination page={page} total={filtered.length} onChange={setPage} /></section></div>;
}

function HistoryTables({ rows, tab }) {
  const groups = getHistoryTaskGroups(rows, tab);
  const materials = groups.materials;
  const temporary = groups.temporary;
  const stageText = historyStatusLabel(tab === "approved" ? TASK_STAGES.APPROVED : tab === "rejected" ? TASK_STAGES.REJECTED : TASK_STAGES.ORG_PENDING);
  return <div className="history-groups">{materials.length > 0 && <div className="history-group"><div className="subtable-title"><span><FilePenLine size={15} />四大维度材料上报</span><small>沿用原有审核字段</small></div><div className="table-scroll"><table><thead><tr><th>维度</th><th>考核项</th><th>提交人</th><th>提交日期</th><th>审核状态</th><th>审核意见</th><th>端员得分</th></tr></thead><tbody>{materials.map((task) => <tr key={task.id}><td>四大维度材料</td><td>{task.title}</td><td>{task.submission?.submitter || "—"}</td><td>{task.submission?.submittedAt || "—"}</td><td><Tag tone={tab === "approved" ? "green" : "orange"}>{stageText}</Tag></td><td>{task.organizationOpinion || "—"}</td><td>{tab === "approved" ? "—" : "待补充"}</td></tr>)}</tbody></table></div></div>}{temporary.length > 0 && <div className="history-group"><div className="subtable-title"><span><ClipboardList size={15} />临时任务</span><small>不展示审核状态、审核意见和端员得分</small></div><div className="table-scroll"><table><thead><tr><th>维度</th><th>考核项</th><th>提交人</th><th>提交日期</th></tr></thead><tbody>{temporary.map((task) => <tr key={task.id}><td>临时任务</td><td>{task.title}</td><td>{task.submission?.submitter || "—"}</td><td>{task.submission?.submittedAt || "—"}</td></tr>)}</tbody></table></div></div>}{!materials.length && !temporary.length && <EmptyState title="暂无历史记录" description="完成提交后记录会在这里展示" />}</div>;
}

function BranchReviewPage({ tasks, onApprove, onSubmit }) {
  const [tab, setTab] = useState("pending");
  const [filters, setFilters] = useState({ title: "", nature: "", cycle: "" });
  const [applied, setApplied] = useState(filters);
  const [page, setPage] = useState(1);
  const [selectedId, setSelectedId] = useState("");
  const reset = () => { const next = { title: "", nature: "", cycle: "" }; setFilters(next); setApplied(next); setPage(1); };
  const all = getBranchTabTasks(tasks, tab);
  const filtered = all.filter((task) => (!applied.title || task.title.includes(applied.title)) && (!applied.nature || task.nature === applied.nature) && (!applied.cycle || task.cycle === applied.cycle));
  const rows = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const selected = tasks.find((task) => task.id === selectedId);
  if (selected) return <BranchTaskDetail task={selected} tab={tab} onBack={() => setSelectedId("")} onApprove={onApprove} onSubmit={onSubmit} />;
  return <div className="page-wrap"><PageHeading title="支部审核工作台" subtitle="支部书记处理报送员提交的任务，再提交至组织审核" /><section className="tab-panel panel-card"><div className="tabs" role="tablist">{REPORT_TABS.map((item) => <button key={item.value} role="tab" aria-selected={tab === item.value} className={"tab-button " + (tab === item.value ? "is-active" : "")} onClick={() => { setTab(item.value); setPage(1); }}>{item.label}<span>{getBranchTabTasks(tasks, item.value).length}</span></button>)}</div></section>{(tab === "pending" || tab === "review") && <QueryBar filters={filters} setFilters={setFilters} onQuery={() => { setApplied({ ...filters }); setPage(1); }} onReset={reset} />}<section className="panel-card table-card"><div className="card-title-row"><div><h2>{REPORT_TABS.find((item) => item.value === tab)?.label}</h2><span>{tab === "pending" ? "报送员提交后到达这里，支持先通过再提交" : tab === "review" ? "已提交至组织审核的任务" : "组织审核结果同步到支部工作台"}</span></div><span className="result-count">共 {filtered.length} 条</span></div>{tab === "pending" ? <div className="table-scroll"><table><thead><tr><th>任务标题</th><th>任务说明</th><th>任务性质</th><th>周期 / 时间窗</th><th>提交人 / 时间</th><th>操作</th></tr></thead><tbody>{rows.length ? rows.map((task) => <tr key={task.id}><td><button className="title-link" onClick={() => setSelectedId(task.id)}>{task.title}<Eye size={13} /></button></td><td><span className="ellipsis" title={task.description}>{task.description}</span></td><td>{task.nature === "scheduled" ? "定时任务" : "临时任务"}</td><td>{task.nature === "scheduled" ? cycleLabel(task.cycle) : "临时任务"}</td><td>{task.submission?.submitter || "—"}<br /><span className="muted">{task.submission?.submittedAt || "—"}</span></td><td><button className="link-button" onClick={() => setSelectedId(task.id)}>查看并处理</button></td></tr>) : <tr><td colSpan="6"><EmptyState title="暂无任务" description="当前页签没有可展示数据" /></td></tr>}</tbody></table></div> : <HistoryTables rows={rows} tab={tab} />}<Pagination page={page} total={filtered.length} onChange={setPage} /></section></div>;
}

function TaskMeta({ task }) {
  return <div className="detail-meta-grid"><div><span>任务性质</span><strong>{task.nature === "scheduled" ? "定时任务" : "临时任务"}</strong></div><div><span>{task.nature === "scheduled" ? "周期" : "时间范围"}</span><strong>{task.nature === "scheduled" ? cycleLabel(task.cycle) : task.startAt + " 至 " + task.endAt}</strong></div><div><span>任务对象</span><strong>{task.objectLabel}</strong></div><div><span>{task.nature === "scheduled" ? "时间窗" : "截止时间"}</span><strong>{task.nature === "scheduled" ? task.window?.start + " 至 " + task.window?.end : task.endAt}</strong></div></div>;
}

function Description({ value }) {
  return <div className="description-content">{linkifyDescription(value).map((part, index) => part.type === "link" ? <a key={index} href={part.href} target={part.target} rel={part.rel}>{part.value}<ExternalLink size={13} /></a> : <span key={index}>{part.value}</span>)}</div>;
}

function AttachmentList({ fileNames = [], onDownload }) {
  return <div className="attachment-list">{fileNames.length ? fileNames.map((fileName) => <div className="attachment-row" key={fileName}><span><Paperclip size={14} />{fileName}</span><button className="link-button" onClick={() => onDownload?.(fileName)}><CloudDownload size={14} />下载</button></div>) : <span className="muted">未上传附件</span>}</div>;
}

function BranchTaskDetail({ task, tab, onBack, onApprove, onSubmit }) {
  const canApprove = tab === "pending" && task.stage === TASK_STAGES.BRANCH_PENDING && !task.branchApproved;
  const canSubmit = tab === "pending" && task.stage === TASK_STAGES.BRANCH_PENDING && task.branchApproved;
  return <div className="page-wrap detail-page"><PageHeading title="支部审核详情" subtitle="临时任务展示报送员填报内容与附件" action={<button className="plain-button" onClick={onBack}><ArrowLeft size={16} />返回工作台</button>} /><section className="panel-card detail-card"><div className="detail-header"><div><div className="eyebrow">{task.type}</div><h2>{task.title}</h2></div><Tag tone={task.stage === TASK_STAGES.REJECTED ? "orange" : task.stage === TASK_STAGES.APPROVED ? "green" : "blue"}>{task.stage === TASK_STAGES.BRANCH_PENDING ? task.branchApproved ? "支部已通过" : "待支部处理" : task.stage === TASK_STAGES.ORG_PENDING ? "组织审核中" : task.stage === TASK_STAGES.APPROVED ? "已通过" : "已驳回"}</Tag></div><TaskMeta task={task} /><div className="detail-divider" /><div className="detail-section"><h3>任务说明</h3><Description value={task.description} /></div><div className="detail-section submission-section"><div className="section-heading"><h3>报送员填报</h3><Tag tone="blue">{task.submission?.branch || "党支部一"}</Tag></div><div className="submission-meta"><span>提交人：{task.submission?.submitter || "—"}</span><span>提交时间：{task.submission?.submittedAt || "—"}</span></div><div className="submission-content">{task.submission?.content || "暂无填报内容"}</div><AttachmentList fileNames={task.submission?.fileNames || []} onDownload={(name) => window.alert("原型模拟下载：" + name)} /></div>{task.organizationOpinion && task.stage === TASK_STAGES.REJECTED && <div className="opinion-box"><MessageCircle size={16} /><span>组织审核意见：{task.organizationOpinion}</span></div>}{(canApprove || canSubmit) && <div className="detail-actions"><button className="primary-button" onClick={() => canApprove ? onApprove(task.id) : onSubmit(task.id)}>{canApprove ? <><Check size={16} />审核通过</> : <><Upload size={16} />提交至组织审核</>}</button></div>}</section></div>;
}

function OrganizationWorkbenchPage({ tasks, onApprove, onReject }) {
  const [type, setType] = useState("all");
  const [selectedId, setSelectedId] = useState("");
  const candidates = tasks.filter((task) => [TASK_STAGES.ORG_PENDING, TASK_STAGES.APPROVED, TASK_STAGES.REJECTED].includes(task.stage) && (type === "all" || (type === "material" ? task.type === "四大维度材料上报" : task.type !== "四大维度材料上报")));
  const selected = candidates.find((task) => task.id === selectedId) || candidates[0];
  const countByType = (name) => tasks.filter((task) => (name === "material" ? task.type === "四大维度材料上报" : name === "temporary" ? task.type !== "四大维度材料上报" : true) && [TASK_STAGES.ORG_PENDING, TASK_STAGES.APPROVED, TASK_STAGES.REJECTED].includes(task.stage)).length;
  return <div className="page-wrap workbench-page"><PageHeading title="工作台" subtitle="组织审核员处理支部提交的材料与临时任务" /><section className="workbench-split panel-card"><aside className="workbench-filter"><div className="workbench-filter-title"><Filter size={15} />筛选任务类型</div><button className={type === "all" ? "selected" : ""} onClick={() => { setType("all"); setSelectedId(""); }}>全部任务<span>{countByType("all")}</span></button><button className={type === "material" ? "selected" : ""} onClick={() => { setType("material"); setSelectedId(""); }}>四大维度材料上报<span>{countByType("material")}</span></button><button className={type === "temporary" ? "selected" : ""} onClick={() => { setType("temporary"); setSelectedId(""); }}>临时任务<span>{countByType("temporary")}</span></button><div className="filter-tip"><Clock3 size={14} />待审核任务优先展示，已处理任务保留在列表供追溯。</div></aside><div className="workbench-list"><div className="workbench-list-head"><div><h2>待审核任务</h2><span>共 {candidates.length} 条，点击查看详情</span></div><Tag tone="red">组织审核</Tag></div>{candidates.length ? candidates.map((task) => <button className={"workbench-list-item " + (selected?.id === task.id ? "is-active" : "")} key={task.id} onClick={() => setSelectedId(task.id)}><div className={"task-list-icon " + (task.type === "四大维度材料上报" ? "red" : "blue")}>{task.type === "四大维度材料上报" ? <FilePenLine size={16} /> : <ClipboardList size={16} />}</div><div><strong>{task.title}</strong><span>{task.submission?.branch || "—"} · {task.submission?.submittedAt || "—"}</span></div><Tag tone={task.stage === TASK_STAGES.ORG_PENDING ? "orange" : task.stage === TASK_STAGES.APPROVED ? "green" : "neutral"}>{task.stage === TASK_STAGES.ORG_PENDING ? "待审核" : task.stage === TASK_STAGES.APPROVED ? "已通过" : "已驳回"}</Tag></button>) : <EmptyState title="暂无匹配任务" description="切换筛选条件后重试" />}</div><div className="workbench-detail">{selected ? <OrganizationTaskDetail task={selected} onApprove={onApprove} onReject={onReject} /> : <EmptyState title="选择任务查看详情" description="左侧选择一条任务" />}</div></section></div>;
}

function OrganizationTaskDetail({ task, onApprove, onReject }) {
  const [opinion, setOpinion] = useState("请补充填报内容");
  const [confirmReject, setConfirmReject] = useState(false);
  const isMaterial = task.type === "四大维度材料上报";
  const handleDownload = (name) => window.alert("原型模拟下载：" + name);
  return <div className="org-detail"><div className="org-detail-head"><div><div className="eyebrow">{task.type}</div><h2>{task.title}</h2></div><Tag tone={task.stage === TASK_STAGES.ORG_PENDING ? "orange" : task.stage === TASK_STAGES.APPROVED ? "green" : "neutral"}>{task.stage === TASK_STAGES.ORG_PENDING ? "待组织审核" : task.stage === TASK_STAGES.APPROVED ? "已通过" : "已驳回"}</Tag></div><TaskMeta task={task} /><div className="detail-divider" />{isMaterial ? <div className="material-review-note"><FilePenLine size={22} /><h3>四大维度材料上报</h3><p>沿用原有材料审核规则。单个明细项有一次上传即视为本季度该明细完成，支持多次上传；本工作台仅保留原审核入口说明。</p><div className="mini-detail-grid">{MATERIAL_DETAIL_ITEMS.map((detail) => <div key={detail}><span>{detail}</span><strong>{task.materialUploads?.[detail]?.count || 0} 次上传</strong></div>)}</div></div> : <><div className="detail-section"><h3>支部填报内容</h3><div className="submission-meta"><span>党支部：{task.submission?.branch || "—"}</span><span>提交人：{task.submission?.submitter || "—"}</span><span>提交时间：{task.submission?.submittedAt || "—"}</span></div><div className="submission-content">{task.submission?.content || "暂无填报内容"}</div></div><div className="detail-section"><div className="section-heading"><h3>附件</h3><span className="muted">支持在线下载</span></div><AttachmentList fileNames={task.submission?.fileNames || []} onDownload={handleDownload} /></div></>}{task.organizationOpinion && task.stage === TASK_STAGES.REJECTED && <div className="opinion-box"><MessageCircle size={16} /><span>最近驳回意见：{task.organizationOpinion}</span></div>}{task.stage === TASK_STAGES.ORG_PENDING && <div className="org-action-bar">{confirmReject ? <div className="reject-form"><label>驳回意见<input value={opinion} onChange={(event) => setOpinion(event.target.value)} /></label><button className="plain-button compact" onClick={() => setConfirmReject(false)}>取消</button><button className="danger-button compact" onClick={() => { onReject(task.id, opinion); setConfirmReject(false); }}>确认驳回</button></div> : <><button className="plain-button danger-outline" onClick={() => setConfirmReject(true)}>驳回</button><button className="primary-button" onClick={() => onApprove(task.id)}><Check size={16} />提交通过</button></>}</div>}</div>;
}

function TaskManagementDetailPage({ task, onBack }) {
  const [selectedDetails, setSelectedDetails] = useState([]);
  const [preview, setPreview] = useState(null);
  const [exportError, setExportError] = useState("");
  const submissions = task.branchSubmissions?.length ? task.branchSubmissions : task.submission ? [task.submission] : [];
  const toggleDetail = (detail) => setSelectedDetails((current) => current.includes(detail) ? current.filter((item) => item !== detail) : [...current, detail]);
  const exportTask = () => { try { setExportError(""); setPreview(buildExportPreview(task, selectedDetails)); } catch (error) { setPreview(null); setExportError(error.message); } };
  const download = (name) => window.alert("原型模拟下载：" + name);
  return <div className="page-wrap detail-page"><PageHeading title="任务详情" subtitle="查看任务配置、各支部填报信息与导出预览" action={<button className="plain-button" onClick={onBack}><ArrowLeft size={16} />返回任务管理</button>} /><section className="panel-card detail-card"><div className="detail-header"><div><div className="eyebrow">{task.type}</div><h2>{task.title}</h2></div><Tag tone="blue">{task.nature === "scheduled" ? "定时任务" : "临时任务"}</Tag></div><TaskMeta task={task} /><div className="detail-divider" /><div className="detail-section"><h3>任务说明</h3><Description value={task.description} /></div><div className="detail-section"><div className="section-heading"><div><h3>支部填报信息</h3><span className="muted">可查看各支部填报时间、内容和附件</span></div><span className="result-count">{submissions.length} 个支部</span></div>{submissions.length ? <div className="submission-table"><table><thead><tr><th>党支部</th><th>提交人</th><th>填报时间</th><th>填报内容</th><th>附件</th></tr></thead><tbody>{submissions.map((submission) => <tr key={submission.branch + submission.submittedAt}><td>{submission.branch}</td><td>{submission.submitter}</td><td>{submission.submittedAt}</td><td><span className="content-cell">{submission.content}</span></td><td><AttachmentList fileNames={submission.fileNames} onDownload={download} /></td></tr>)}</tbody></table></div> : <EmptyState title="暂无支部填报" description="报送员提交后会在此展示" />}</div><div className="detail-section export-section"><div className="section-heading"><div><h3>一键导出</h3><span className="muted">统一模拟生成 ZIP 压缩包，Excel 字段固定为任务名称、发布时间、结束时间、提交人、提交时间、填报内容</span></div><button className="primary-button" onClick={exportTask}><Download size={16} />一键导出</button></div>{task.type === "四大维度材料上报" && <div className="detail-selector"><div><strong>导出明细项</strong><span>明细项来自字典配置，可按后续维护灵活调整</span></div><div className="detail-checks">{MATERIAL_DETAIL_ITEMS.map((detail) => <label key={detail}><input type="checkbox" checked={selectedDetails.includes(detail)} onChange={() => toggleDetail(detail)} />{detail}</label>)}</div></div>}{exportError && <div className="field-error export-error">{exportError}</div>}{preview && <ExportPreview preview={preview} />}</div></section></div>;
}

function ExportPreview({ preview }) {
  return <div className="zip-preview"><div className="zip-preview-head"><div><strong>{preview.archiveName}</strong><span>{preview.note}</span></div><Tag tone="green">原型模拟</Tag></div><div className="zip-tree">{preview.entries.map((entry) => <div className={"zip-entry " + (entry.type === "attachment" ? "attachment" : "")} key={entry.path}>{entry.type === "attachment" ? <Paperclip size={14} /> : <FileText size={14} />}<span>{entry.path}</span></div>)}</div>{preview.selectedDetails?.length > 0 && <div className="selected-summary">已选择明细：{preview.selectedDetails.join("、")}</div>}</div>;
}

function TemporaryTaskDetail({ task, onBack, onSubmit }) {
  return <TemporaryTaskForm task={task} title="临时任务填报" subtitle="当前窗口填报，提交后进入支部审核工作台" onBack={onBack} onSubmit={onSubmit} />;
}

function TemporaryTaskPopup({ task, onSubmit }) {
  return <div className="popup-page"><TemporaryTaskForm task={task} title="临时任务在线填报" subtitle="独立窗口填报，提交后将通过审核流程流转" onSubmit={onSubmit} isPopup /></div>;
}

function TemporaryTaskForm({ task, title, subtitle, onBack, onSubmit, isPopup = false }) {
  const [content, setContent] = useState("");
  const [fileNames, setFileNames] = useState([]);
  const [error, setError] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const handleFiles = (event) => { setFileNames(Array.from(event.target.files || [], (file) => file.name)); setError(""); };
  const submit = () => {
    const validation = validateTaskSubmission(task, task.requiresFile ? fileNames[0] : "");
    if (!validation.valid) { setError(validation.errors.file); return; }
    if (!content.trim()) { setError("请填写任务内容后再提交"); return; }
    onSubmit({ content: content.trim(), fileNames, submitter: "张伟", branch: "党支部一" });
    setSubmitted(true);
  };
  if (submitted) return <div className="popup-success panel-card"><div className="success-icon"><CheckCircle2 size={30} /></div><h2>提交成功</h2><p>任务已进入支部审核工作台，报送员端状态更新为“审核中”。</p>{isPopup ? <button className="primary-button" onClick={() => window.close()}>关闭窗口</button> : <button className="primary-button" onClick={onBack}>返回上报信息</button>}</div>;
  return <div className="page-wrap detail-page"><PageHeading title={title} subtitle={subtitle} action={onBack ? <button className="plain-button" onClick={onBack}><ArrowLeft size={16} />返回上报信息</button> : null} /><section className="panel-card detail-card"><div className="detail-header"><div><div className="eyebrow">{task.type} · 临时任务</div><h2>{task.title}</h2></div><Tag tone="blue">待处理</Tag></div><TaskMeta task={task} /><div className="detail-divider" /><div className="detail-section"><h3>任务说明</h3><Description value={task.description} /></div><div className="detail-section"><h3>填报内容</h3><textarea className="task-content-input" value={content} onChange={(event) => { setContent(event.target.value); setError(""); }} placeholder="请输入本次任务的填报内容" rows="7" /></div>{task.requiresFile && <div className="detail-section upload-section"><div className="upload-title"><h3>附件上传</h3><span>允许类型：{task.fileTypes.join("、")}</span></div><label className="upload-box"><input type="file" multiple onChange={handleFiles} accept={task.fileTypes.map((type) => ({ PDF: ".pdf", Word: ".doc,.docx", Excel: ".xls,.xlsx", 图片: "image/*", 压缩包: ".zip,.rar,.7z" }[type] || "")).join(",")} /><UploadCloud size={26} /><strong>{fileNames.length ? fileNames.join("、") : "点击或拖拽上传附件"}</strong><span>{task.fileTypes.join("、")} 格式，可多选</span></label></div>}{error && <div className="submit-error standalone-error">{error}</div>}<div className="detail-actions"><button className="primary-button" onClick={submit}><Check size={16} />提交填报</button></div></section></div>;
}

function MaterialsEntryPage({ task, onBack, onUpload }) {
  const completion = getMaterialCompletion(task);
  const uploads = task.materialUploads || {};
  return <div className="page-wrap"><PageHeading title="四大维度材料上报" subtitle="定期定时任务支持多次上传，单个明细项有一次上传即视为本季度完成" action={<button className="plain-button" onClick={onBack}><ArrowLeft size={16} />返回上报信息</button>} /><section className="panel-card materials-card"><div className="materials-summary"><div><div className="eyebrow">{task.title}</div><h2>本季度材料明细</h2><p>窗口：{task.window?.start} 至 {task.window?.end} · 已完成 {completion.completed.length}/{completion.total} 项</p></div><Tag tone={completion.complete ? "green" : "orange"}>{completion.complete ? "本季度已完成" : "待补充明细"}</Tag></div><div className="material-detail-list">{MATERIAL_DETAIL_ITEMS.map((detail) => { const record = uploads[detail] || { count: 0, files: [] }; const complete = record.count >= 1; return <div className="material-detail-row" key={detail}><div className={"material-status " + (complete ? "complete" : "pending")}>{complete ? <Check size={16} /> : <Clock3 size={16} />}</div><div className="material-detail-main"><strong>{detail}</strong><span>{complete ? "已完成 · " + record.count + " 次上传" : "尚未上传"}</span>{record.files?.length > 0 && <small>{record.files.map((file) => file.name).join("、")}</small>}</div><label className="upload-inline"><input type="file" onChange={(event) => { const fileName = event.target.files?.[0]?.name; if (fileName) onUpload(task.id, detail, fileName); }} accept={task.fileTypes.map((type) => ({ PDF: ".pdf", Word: ".doc,.docx", Excel: ".xls,.xlsx", 图片: "image/*", 压缩包: ".zip,.rar,.7z" }[type] || "")).join(",")} /><Upload size={14} />{complete ? "再次上传" : "上传材料"}</label></div>; })}</div><div className="materials-note"><UploadCloud size={16} /><span>上传次数只用于完成判定与导出记录；如需补充材料，可在同一明细下再次上传。</span></div></section></div>;
}

function HomePage({ tasks, onOpen, role }) {
  const pending = tasks.filter((task) => task.status === "pending");
  const completed = tasks.filter((task) => task.status === "completed");
  return <div className="page-wrap workbench-page"><PageHeading title="首页" subtitle={"欢迎回来，" + getRoleLabel(role)} /><div className="metric-grid"><div className="metric-card"><div className="metric-icon red"><ListTodo size={19} /></div><div><span>待办事项</span><strong>{pending.length}</strong></div></div><div className="metric-card"><div className="metric-icon blue"><CheckCircle2 size={19} /></div><div><span>已完成任务</span><strong>{completed.length}</strong></div></div><div className="metric-card"><div className="metric-icon orange"><Users size={19} /></div><div><span>本组织任务</span><strong>{tasks.length}</strong></div></div></div><section className="panel-card todo-card"><div className="card-title-row"><div><h2>待办事项</h2><span>当前处于任务时间窗且尚未提交的任务</span></div><Tag tone="red">{pending.length} 项待处理</Tag></div>{pending.length ? <div className="todo-list">{pending.map((task) => <button className="todo-item" key={task.id} onClick={() => onOpen(task)}><div className={"todo-icon " + (task.type === "四大维度材料上报" ? "materials" : "task")}>{task.type === "四大维度材料上报" ? <FilePenLine size={17} /> : <ListTodo size={17} />}</div><div className="todo-main"><strong>{task.title}</strong><span>{task.description}</span></div><div className="todo-meta"><Tag tone={task.type === "四大维度材料上报" ? "red" : "blue"}>{task.type === "四大维度材料上报" ? "材料上报" : "临时任务"}</Tag><span>{task.nature === "scheduled" ? cycleLabel(task.cycle) + " · " + task.window?.end : "截止 " + task.endAt}</span></div><ChevronRight className="todo-arrow" size={17} /></button>)}</div> : <EmptyState title="待办已清空" description="当前没有需要处理的任务" />}</section><div className="workbench-note"><Clock3 size={16} /><span>任务只在时间窗开始后出现在首页待办；临时任务提交后进入审核中，四大维度材料任务继续由既有入口处理。</span></div></div>;
}

function WarningPoolPage({ role, overdueItems, onScore }) {
  const [filters, setFilters] = useState({ taskType: "", branch: "", taskName: "" });
  const [applied, setApplied] = useState(filters);
  const [page, setPage] = useState(1);
  const filterRows = (items) => items.filter((item) => (!applied.taskType || item.taskType === applied.taskType) && (!applied.branch || item.branch.includes(applied.branch)) && (!applied.taskName || item.taskName.includes(applied.taskName)));
  const filteredItems = filterRows(overdueItems);
  const rows = filteredItems.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  return <div className="page-wrap"><PageHeading title="红黄牌预警池" subtitle="面向全部人员开放，展示支部风险预警与组织管理待执行事项" /><div className="warning-grid"><section className="warning-card red-warning"><div className="warning-card-head"><div className="warning-icon"><AlertCircle size={20} /></div><div><span>红牌支部</span><strong>1 个</strong></div></div><p>暂定规则：连续两季度排名倒数第一</p><small>规则后续可调整</small></section><section className="warning-card yellow-warning"><div className="warning-card-head"><div className="warning-icon"><AlertCircle size={20} /></div><div><span>黄牌支部</span><strong>5 个</strong></div></div><p>暂定规则：连续两季度排名倒数前五</p><small>规则后续可调整</small></section></div><section className="panel-card warning-list-card"><div className="card-title-row"><div><h2>当前预警支部</h2><span>系统按季度排名自动统计，示例数据仅用于演示</span></div><Tag tone="orange">自动统计</Tag></div><div className="warning-branch-list"><div className="warning-branch-row"><Tag tone="red">红牌</Tag><strong>党支部三</strong><span>连续两季度排名倒数第一</span><small>最近统计：2026 Q2</small></div><div className="warning-branch-row"><Tag tone="orange">黄牌</Tag><strong>党支部二</strong><span>连续两季度排名倒数前五</span><small>最近统计：2026 Q2</small></div><div className="warning-branch-row"><Tag tone="orange">黄牌</Tag><strong>党支部五</strong><span>连续两季度排名倒数前五</span><small>最近统计：2026 Q2</small></div></div></section>{role === "admin" && <section className="panel-card table-card deduction-card"><div className="card-title-row"><div><h2>逾期上报待执行扣分</h2><span>仅组织管理员可见；已逾期但仍未上报的数据不进入该列表</span></div><Tag tone="red">组织管理员专属</Tag></div><section className="filter-card inline-filter"><div className="filter-row"><label className="filter-field"><span>任务类型</span><select value={filters.taskType} onChange={(event) => setFilters({ ...filters, taskType: event.target.value })}><option value="">全部</option><option>四大维度材料上报</option><option>临时任务</option></select></label><label className="filter-field"><span>任务名称</span><input value={filters.taskName} onChange={(event) => setFilters({ ...filters, taskName: event.target.value })} placeholder="请输入任务名称" /></label><label className="filter-field"><span>对应党支部</span><input value={filters.branch} onChange={(event) => setFilters({ ...filters, branch: event.target.value })} placeholder="请输入党支部" /></label><div className="filter-actions"><button className="primary-button compact" onClick={() => { setApplied({ ...filters }); setPage(1); }}><Search size={15} />查询</button><button className="plain-button compact" onClick={() => { const next = { taskType: "", branch: "", taskName: "" }; setFilters(next); setApplied(next); setPage(1); }}><RotateCcw size={15} />重置</button></div></div></section><div className="table-scroll"><table><thead><tr><th>任务类型</th><th>任务名称</th><th>任务内容</th><th>对应党支部</th><th>任务开始时间</th><th>任务结束时间</th><th>上报人</th><th>上报时间</th><th>扣分</th></tr></thead><tbody>{rows.length ? rows.map((item) => <DeductionRow key={item.id} item={item} onScore={onScore} />) : <tr><td colSpan="9"><EmptyState title="暂无逾期待执行扣分" description="逾期未上报数据不在此展示" /></td></tr>}</tbody></table></div><Pagination page={page} total={filteredItems.length} onChange={setPage} /><div className="notification-note"><AlertCircle size={15} /><span>逾期未上报需向组织管理员、报送员及对应支部书记推送通知，通知方式待定。</span></div></section>}</div>;
}

function DeductionRow({ item, onScore }) {
  const [score, setScore] = useState(item.score || "");
  return <tr><td>{item.taskType}</td><td>{item.taskName}</td><td><span className="ellipsis" title={item.content}>{item.content}</span></td><td>{item.branch}</td><td>{item.startAt}</td><td>{item.endAt}</td><td>{item.submitter}</td><td>{item.submittedAt}</td><td><div className="score-cell">{item.deducted ? <Tag tone="green">已扣 {item.score} 分</Tag> : <><input className="score-input" type="number" min="0" value={score} onChange={(event) => setScore(event.target.value)} placeholder="分值" /><button className="link-button" disabled={!score} onClick={() => onScore(item.id, score)}>提交</button></>}</div></td></tr>;
}

function LegacyPage({ title, role, view }) {
  const description = view === "materials" ? "四大维度材料上报入口保留原有页面逻辑，本轮仅从报送员任务跳转至该入口。" : "本次原型只新增任务与审核流程，原有页面保持现状。";
  return <div className="page-wrap"><PageHeading title={title} subtitle={"当前身份：" + getRoleLabel(role)} /><section className="panel-card legacy-card"><Building2 size={28} /><h2>{title}</h2><p>{description}</p></section></div>;
}

function EmptyState({ title, description }) {
  return <div className="empty-state"><div className="empty-icon"><ClipboardList size={22} /></div><strong>{title}</strong><span>{description}</span></div>;
}

export { App };
