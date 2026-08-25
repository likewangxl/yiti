import { useMemo, useState } from "react";
import {
  IconAlertCircle as AlertCircle,
  IconArrowLeft as ArrowLeft,
  IconBuilding as Building2,
  IconCheck as Check,
  IconCircleCheck as CheckCircle2,
  IconChevronLeft as ChevronLeft,
  IconChevronRight as ChevronRight,
  IconClipboardCheck as ClipboardCheck,
  IconClipboardList as ClipboardList,
  IconClock as Clock3,
  IconDownload as Download,
  IconExternalLink as ExternalLink,
  IconFileCheck as FileCheck2,
  IconFilePencil as FilePenLine,
  IconFileText as FileText,
  IconHome as Home,
  IconLayoutDashboard as LayoutDashboard,
  IconLink as Link2,
  IconList as ListTodo,
  IconPlus as Plus,
  IconRefresh as RotateCcw,
  IconSearch as Search,
  IconSettings as Settings,
  IconStar as Star,
  IconTrophy as Trophy,
  IconX as X,
  IconCloudUpload as UploadCloud,
  IconUsers as Users,
} from "@tabler/icons-react";
import {
  CYCLES,
  FILE_TYPES,
  TASK_TYPES,
  calculateWindow,
  completeTask,
  cycleLabel,
  formatWindow,
  getEmployeeTasks,
  linkifyDescription,
  publishTask,
  validateTaskDraft,
  validateTaskSubmission,
} from "./task-domain.js";

const PAGE_SIZE = 5;
const PEOPLE = [
  { value: "EMP001", label: "EMP001 · 张伟" },
  { value: "EMP002", label: "EMP002 · 李娜" },
  { value: "EMP003", label: "EMP003 · 王强" },
  { value: "EMP004", label: "EMP004 · 赵敏" },
];

const menuItems = [
  { key: "workbench", label: "工作台", Icon: Home },
  { key: "materials", label: "四大维度材料上报", Icon: FilePenLine },
  { key: "records", label: "上报记录", Icon: FileText },
  { key: "review", label: "支部审核工作台", Icon: ClipboardCheck },
  { key: "dashboard", label: "全局数据驾驶舱", Icon: LayoutDashboard },
  { key: "warning", label: "红黄牌预警池", Icon: AlertCircle },
  { key: "immersive", label: "沉浸式审核工作台", Icon: FileCheck2 },
  { key: "archive", label: "年度考核归档", Icon: Trophy },
  { key: "export", label: "数据导出", Icon: Download },
  { key: "org", label: "党组织管理", Icon: Settings },
  { key: "mapping", label: "用户党组织映射", Icon: Link2 },
  { key: "management", label: "任务管理", Icon: ClipboardList, isNew: true },
  { key: "processing", label: "任务处理", Icon: ListTodo, isNew: true },
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

function seedTasks() {
  const today = new Date();
  const activeStart = shiftDate(today, -2);
  const activeEnd = shiftDate(today, 7);
  const monthStart = new Date(today.getFullYear(), today.getMonth(), 1);
  const monthEnd = new Date(today.getFullYear(), today.getMonth() + 1, 0);
  const quarterStart = new Date(today.getFullYear(), Math.floor(today.getMonth() / 3) * 3, 1);
  const materialWindowStart = isoDate(today);
  const materialWindowEnd = isoDate(monthEnd);
  const materialDuration = monthEnd.getDate() - today.getDate() + 1;
  return [
    {
      id: "task-101",
      title: "党史专题学习与心得提交",
      description: "请完成本月党史专题学习，并在任务详情中提交学习心得。参考资料：https://example.com/party-history",
      type: "党建学习",
      nature: "temporary",
      cycle: "",
      duration: null,
      window: null,
      startAt: activeStart,
      endAt: activeEnd,
      object: "all",
      objectLabel: "本组织全员",
      requiresFile: false,
      fileTypes: [],
      publishedAt: shiftDate(today, -4),
      status: "published",
      route: "task-detail",
      employeeStatus: "pending",
    },
    {
      id: "task-102",
      title: "本月四大维度材料上报",
      description: "请按现有上报要求完成本月材料整理。本任务继续由现有四大维度材料上报页面处理。",
      type: "四大维度材料上报",
      nature: "scheduled",
      cycle: "monthly-end",
      duration: materialDuration,
      window: { start: materialWindowStart, end: materialWindowEnd },
      startAt: "",
      endAt: "",
      object: "all",
      objectLabel: "本组织全员",
      requiresFile: true,
      fileTypes: ["PDF", "Word", "Excel"],
      publishedAt: shiftDate(today, -6),
      status: "published",
      route: "materials-entry",
      employeeStatus: "pending",
    },
    {
      id: "task-103",
      title: "支部通知确认",
      description: "请阅读通知并完成确认。",
      type: "通知确认",
      nature: "scheduled",
      cycle: "monthly-start",
      duration: 5,
      window: { start: isoDate(monthStart), end: shiftDate(monthStart, 4) },
      startAt: "",
      endAt: "",
      object: "all",
      objectLabel: "本组织全员",
      requiresFile: false,
      fileTypes: [],
      publishedAt: shiftDate(monthStart, -2),
      status: "published",
      route: "task-detail",
      employeeStatus: "completed",
    },
    {
      id: "task-104",
      title: "八月整改反馈收集",
      description: "请反馈整改进展，必要时附上佐证材料。",
      type: "整改反馈",
      nature: "temporary",
      cycle: "",
      duration: null,
      window: null,
      startAt: shiftDate(today, -18),
      endAt: shiftDate(today, -10),
      object: "all",
      objectLabel: "本组织全员",
      requiresFile: true,
      fileTypes: ["PDF", "图片"],
      publishedAt: shiftDate(today, -20),
      status: "published",
      route: "task-detail",
      employeeStatus: "completed",
    },
    {
      id: "task-105",
      title: "季度党员教育培训报名",
      description: "请在截止时间前完成培训报名。",
      type: "党建学习",
      nature: "scheduled",
      cycle: "quarterly-start",
      duration: 7,
      window: { start: isoDate(quarterStart), end: shiftDate(quarterStart, 6) },
      startAt: "",
      endAt: "",
      object: "all",
      objectLabel: "本组织全员",
      requiresFile: false,
      fileTypes: [],
      publishedAt: shiftDate(quarterStart, -6),
      status: "published",
      route: "task-detail",
      employeeStatus: "pending",
    },
    {
      id: "task-106",
      title: "专项工作提醒",
      description: "请关注工作安排并及时完成确认。",
      type: "其他",
      nature: "temporary",
      cycle: "",
      duration: null,
      window: null,
      startAt: shiftDate(today, -42),
      endAt: shiftDate(today, -35),
      object: "all",
      objectLabel: "本组织全员",
      requiresFile: false,
      fileTypes: [],
      publishedAt: shiftDate(today, -44),
      status: "published",
      route: "task-detail",
      employeeStatus: "completed",
    },
    {
      id: "task-107",
      title: "党支部年度计划补充说明",
      description: "请补充年度计划相关说明。",
      type: "整改反馈",
      nature: "temporary",
      cycle: "",
      duration: null,
      window: null,
      startAt: shiftDate(today, -50),
      endAt: shiftDate(today, -43),
      object: "all",
      objectLabel: "本组织全员",
      requiresFile: false,
      fileTypes: [],
      publishedAt: shiftDate(today, -52),
      status: "published",
      route: "task-detail",
      employeeStatus: "completed",
    },
  ];
}

function IconText({ Icon, children, size = 15 }) {
  return <span className="icon-text"><Icon size={size} strokeWidth={1.8} />{children}</span>;
}

function Tag({ tone = "neutral", children }) {
  return <span className={`tag tag-${tone}`}>{children}</span>;
}

function FieldError({ children }) {
  return children ? <div className="field-error">{children}</div> : null;
}

function Pagination({ page, total, onChange }) {
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  return (
    <div className="pagination">
      <span className="pagination-total">共 {total} 条</span>
      <button aria-label="上一页" className="page-button" disabled={page <= 1} onClick={() => onChange(page - 1)}><ChevronLeft size={15} /></button>
      {Array.from({ length: pages }, (_, index) => index + 1).map((value) => (
        <button key={value} className={`page-number ${page === value ? "is-active" : ""}`} onClick={() => onChange(value)}>{value}</button>
      ))}
      <button aria-label="下一页" className="page-button" disabled={page >= pages} onClick={() => onChange(page + 1)}><ChevronRight size={15} /></button>
    </div>
  );
}

function App() {
  const [role, setRole] = useState("admin");
  const [view, setView] = useState("management");
  const [tasks, setTasks] = useState(() => seedTasks());
  const [selectedTask, setSelectedTask] = useState(null);
  const [detailReadonly, setDetailReadonly] = useState(false);
  const [notice, setNotice] = useState("");

  const go = (nextView) => {
    setNotice("");
    setSelectedTask(null);
    setDetailReadonly(false);
    setView(nextView);
  };

  const openTask = (task, readonly = false) => {
    setSelectedTask(task);
    setDetailReadonly(readonly);
    setNotice("");
    setView(task.route === "materials-entry" ? "materials-entry" : "detail");
  };

  const finishTask = (taskId) => {
    setTasks((current) => completeTask(current, taskId));
    setNotice("任务已提交，已从工作台待办中移除");
    setView("processing");
    setSelectedTask(null);
  };

  const handlePublished = (draft) => {
    try {
      setTasks((current) => publishTask(current, draft));
      setNotice("任务发布成功");
      setView("management");
    } catch (error) {
      setNotice(error.message);
    }
  };

  const currentTitle = menuItems.find((item) => item.key === view)?.label;
  const employeeCount = getEmployeeTasks(tasks, new Date());

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark"><Star size={19} strokeWidth={1.8} /></span><span>红色引擎</span></div>
        <nav className="side-nav" aria-label="主导航">
          {menuItems.map(({ key, label, Icon, isNew }) => (
            <button key={key} className={`side-nav-item ${view === key ? "is-active" : ""}`} onClick={() => go(key)}>
              <Icon size={16} strokeWidth={1.8} />
              <span>{label}</span>
              {isNew && <span className="new-mark">新增</span>}
            </button>
          ))}
        </nav>
        <div className="sidebar-foot">红色引擎工作平台<br /><span>原型演示环境</span></div>
      </aside>

      <section className="main-area">
        <header className="topbar">
          <div className="system-title">红色引擎工程管理系统</div>
          <div className="topbar-right">
            <span className="prototype-label">原型演示</span>
            <label className="role-switch"><span>演示身份</span><select value={role} onChange={(event) => { setRole(event.target.value); go(event.target.value === "admin" ? "management" : "workbench"); }} aria-label="演示身份"><option value="admin">组织管理员</option><option value="employee">一线员工</option></select></label>
            <button className="logout-button">退出</button>
          </div>
        </header>

        <main className="content-area">
          {notice && <div className="notice success"><CheckCircle2 size={17} />{notice}<button aria-label="关闭提示" onClick={() => setNotice("")}><X size={16} /></button></div>}
          {view === "management" && <TaskManagementPage tasks={tasks} onAdd={() => go("new")} onView={(task) => openTask(task, true)} />}
          {view === "new" && <NewTaskPage onBack={() => go("management")} onPublish={handlePublished} />}
          {view === "processing" && <TaskProcessingPage tasks={tasks} onOpen={openTask} />}
          {view === "workbench" && <WorkbenchPage tasks={employeeCount} onOpen={openTask} role={role} />}
          {view === "detail" && selectedTask && <TaskDetailPage task={selectedTask} readonly={detailReadonly} onBack={() => go(role === "admin" ? "management" : "processing")} onComplete={finishTask} />}
          {view === "materials-entry" && selectedTask && <MaterialsEntryPage task={selectedTask} onBack={() => go("workbench")} />}
          {["materials", "records", "review", "dashboard", "warning", "immersive", "archive", "export", "org", "mapping"].includes(view) && <LegacyPage title={currentTitle} />}
        </main>
      </section>
    </div>
  );
}

function PageHeading({ title, subtitle, action }) {
  return <div className="page-heading"><div><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>{action}</div>;
}

function TaskManagementPage({ tasks, onAdd, onView }) {
  const [filters, setFilters] = useState({ title: "", nature: "", type: "", cycle: "" });
  const [applied, setApplied] = useState(filters);
  const [page, setPage] = useState(1);
  const filtered = useMemo(() => tasks.filter((task) => task.status === "published" && (!applied.title || task.title.includes(applied.title)) && (!applied.nature || task.nature === applied.nature) && (!applied.type || task.type === applied.type) && (!applied.cycle || task.cycle === applied.cycle)), [tasks, applied]);
  const rows = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const query = () => { setPage(1); setApplied({ ...filters }); };
  const reset = () => { const next = { title: "", nature: "", type: "", cycle: "" }; setFilters(next); setApplied(next); setPage(1); };
  return (
    <div className="page-wrap">
      <PageHeading title="任务管理" subtitle="组织管理员可发布并查看本组织已发布任务" action={<button className="primary-button" onClick={onAdd}><Plus size={16} />新增任务</button>} />
      <section className="filter-card panel-card">
        <div className="filter-row">
          <label className="filter-field"><span>任务标题</span><input value={filters.title} onChange={(event) => setFilters({ ...filters, title: event.target.value })} placeholder="请输入任务标题" /></label>
          <label className="filter-field"><span>任务性质</span><select value={filters.nature} onChange={(event) => setFilters({ ...filters, nature: event.target.value })}><option value="">全部</option><option value="scheduled">定时任务</option><option value="temporary">临时任务</option></select></label>
          <label className="filter-field"><span>任务类型</span><select value={filters.type} onChange={(event) => setFilters({ ...filters, type: event.target.value })}><option value="">全部</option>{TASK_TYPES.map((type) => <option key={type}>{type}</option>)}</select></label>
          <label className="filter-field"><span>周期</span><select value={filters.cycle} onChange={(event) => setFilters({ ...filters, cycle: event.target.value })}><option value="">全部</option>{CYCLES.map((cycle) => <option key={cycle.value} value={cycle.value}>{cycle.label}</option>)}</select></label>
          <div className="filter-actions"><button className="primary-button compact" onClick={query}><Search size={15} />查询</button><button className="plain-button compact" onClick={reset}><RotateCcw size={15} />重置</button></div>
        </div>
      </section>
      <section className="panel-card table-card">
        <div className="card-title-row"><div><h2>已发布任务</h2><span>仅展示已发布任务，发布后按窗口生成员工任务实例</span></div><span className="result-count">共 {filtered.length} 条</span></div>
        <div className="table-scroll"><table><thead><tr><th>任务标题</th><th>任务类型</th><th>任务性质</th><th>周期 / 时间窗</th><th>任务对象</th><th>文件要求</th><th>发布时间</th><th>操作</th></tr></thead><tbody>{rows.length ? rows.map((task) => <tr key={task.id}><td><div className="title-cell"><span className="task-dot" />{task.title}</div></td><td><Tag tone={task.type === "四大维度材料上报" ? "red" : "blue"}>{task.type}</Tag></td><td>{task.nature === "scheduled" ? "定时任务" : "临时任务"}</td><td><span className="window-cell">{formatWindow(task)}</span></td><td>{task.objectLabel}</td><td>{task.requiresFile ? <Tag tone="orange">需上传 · {task.fileTypes.join("、")}</Tag> : <span className="muted">无需上传</span>}</td><td>{task.publishedAt}</td><td><button className="link-button" onClick={() => onView(task)}>查看</button></td></tr>) : <tr><td colSpan="8"><EmptyState title="暂无匹配任务" description="调整查询条件或新增一条任务" /></td></tr>}</tbody></table></div>
        <Pagination page={page} total={filtered.length} onChange={setPage} />
      </section>
    </div>
  );
}

function NewTaskPage({ onBack, onPublish }) {
  const today = isoDate(new Date());
  const [draft, setDraft] = useState({ nature: "scheduled", type: "党建学习", title: "", description: "", object: "all", people: [], cycle: "monthly-end", duration: 5, startAt: today, endAt: shiftDate(new Date(), 7), requiresFile: false, fileTypes: [] });
  const [errors, setErrors] = useState({});
  const preview = useMemo(() => {
    if (draft.nature !== "scheduled" || !draft.cycle || !draft.duration) return null;
    try { return calculateWindow(draft.cycle, Number(draft.duration), new Date()); } catch (error) { return { error: error.message }; }
  }, [draft.nature, draft.cycle, draft.duration]);
  const change = (key, value) => {
    setDraft((current) => ({ ...current, [key]: value }));
    setErrors((current) => {
      const next = { ...current };
      delete next[key];
      if (key === "people") delete next.object;
      if (key === "requiresFile" && !value) delete next.fileTypes;
      return next;
    });
  };
  const submit = () => {
    const result = validateTaskDraft(draft);
    setErrors(result.errors);
    if (result.valid) onPublish({ ...draft, objectLabel: draft.object === "all" ? "本组织全员" : `指定 ${draft.people.length} 人` });
  };
  const toggleFileType = (type) => change("fileTypes", draft.fileTypes.includes(type) ? draft.fileTypes.filter((item) => item !== type) : [...draft.fileTypes, type]);
  return (
    <div className="page-wrap form-page">
      <PageHeading title="新增任务" subtitle="发布后从下一个完整周期或指定时间窗开始生成员工待办" action={<button className="plain-button" onClick={onBack}><ArrowLeft size={16} />返回任务管理</button>} />
      <section className="panel-card form-card">
        <div className="form-section-title"><span>基础信息</span><small>带 <em>*</em> 字段为必填项</small></div>
        <div className="form-grid">
          <label className="form-field required"><span>任务性质</span><div className="segmented"><button className={draft.nature === "scheduled" ? "selected" : ""} onClick={() => change("nature", "scheduled")}>定时任务</button><button className={draft.nature === "temporary" ? "selected" : ""} onClick={() => change("nature", "temporary")}>临时任务</button></div><FieldError>{errors.nature}</FieldError></label>
          <label className="form-field required"><span>任务类型</span><select value={draft.type} onChange={(event) => change("type", event.target.value)}><option value="">请选择任务类型</option>{TASK_TYPES.map((type) => <option key={type}>{type}</option>)}</select><FieldError>{errors.type}</FieldError></label>
          <label className="form-field required full"><span>任务标题</span><input value={draft.title} onChange={(event) => change("title", event.target.value)} placeholder="请输入任务标题" maxLength="60" /><div className="input-count">{draft.title.length}/60</div><FieldError>{errors.title}</FieldError></label>
          <label className="form-field required full"><span>任务说明</span><textarea value={draft.description} onChange={(event) => change("description", event.target.value)} placeholder="请输入任务说明，可粘贴网页链接，处理任务时将展示为可点击链接" rows="4" /><FieldError>{errors.description}</FieldError></label>
          <fieldset className="form-field required full"><legend>任务对象</legend><div className="object-options"><label><input type="radio" checked={draft.object === "all"} onChange={() => change("object", "all")} />本组织全员</label><label><input type="radio" checked={draft.object === "specified"} onChange={() => change("object", "specified")} />指定人员</label></div>{draft.object === "specified" && <select className="people-select" multiple value={draft.people} onChange={(event) => change("people", Array.from(event.target.selectedOptions, (option) => option.value))}>{PEOPLE.map((person) => <option key={person.value} value={person.value}>{person.label}</option>)}</select>}<FieldError>{errors.object || (draft.object === "specified" && draft.people.length === 0 ? "请选择至少一名人员" : "")}</FieldError></fieldset>
        </div>
        <div className="form-divider" />
        <div className="form-section-title"><span>{draft.nature === "scheduled" ? "定时规则" : "临时时间"}</span><small>{draft.nature === "scheduled" ? "按下一个完整周期生效" : "临时任务必须补充开始与截止时间"}</small></div>
        {draft.nature === "scheduled" ? <div className="form-grid schedule-grid"><label className="form-field required"><span>周期</span><select value={draft.cycle} onChange={(event) => change("cycle", event.target.value)}><option value="">请选择周期</option>{CYCLES.map((cycle) => <option key={cycle.value} value={cycle.value}>{cycle.label}</option>)}</select><FieldError>{errors.cycle}</FieldError></label><label className="form-field required"><span>持续天数</span><div className="unit-input"><input type="number" min="1" value={draft.duration} onChange={(event) => change("duration", event.target.value === "" ? "" : Number(event.target.value))} /><span>天</span></div><FieldError>{errors.duration}</FieldError></label><div className={`window-preview ${preview?.error ? "has-error" : ""}`}><div className="preview-icon"><Clock3 size={17} /></div><div><span>下一窗口自动预览</span><strong>{preview?.error || (preview ? `${preview.start} 至 ${preview.end}` : "选择周期和持续天数后预览")}</strong><small>自然日含首尾，发布后从下一个完整周期生效</small></div></div></div> : <div className="form-grid schedule-grid"><label className="form-field required"><span>开始时间</span><input type="date" value={draft.startAt} onChange={(event) => change("startAt", event.target.value)} /><FieldError>{errors.startAt}</FieldError></label><label className="form-field required"><span>截止时间</span><input type="date" value={draft.endAt} onChange={(event) => change("endAt", event.target.value)} /><FieldError>{errors.endAt}</FieldError></label><div className="window-preview"><div className="preview-icon"><Clock3 size={17} /></div><div><span>临时任务时间窗</span><strong>{draft.startAt || "—"} 至 {draft.endAt || "—"}</strong><small>仅在时间窗内生成员工首页待办</small></div></div></div>}
        <div className="form-divider" />
        <div className="form-section-title"><span>附件要求</span><small>可按任务需要开启文件上传</small></div>
        <div className="form-grid attachment-grid"><div className="form-field"><span>是否要求上传文件</span><div className="segmented"><button className={!draft.requiresFile ? "selected" : ""} onClick={() => change("requiresFile", false)}>否</button><button className={draft.requiresFile ? "selected" : ""} onClick={() => change("requiresFile", true)}>是</button></div></div>{draft.requiresFile && <fieldset className="form-field required"><legend>允许文件类型</legend><div className="checkbox-options">{FILE_TYPES.map((type) => <label key={type}><input type="checkbox" checked={draft.fileTypes.includes(type)} onChange={() => toggleFileType(type)} />{type}</label>)}</div><FieldError>{errors.fileTypes}</FieldError></fieldset>}</div>
        <div className="form-footer"><button className="plain-button" onClick={onBack}>取消</button><button className="primary-button" onClick={submit}><Check size={16} />发布任务</button></div>
      </section>
    </div>
  );
}

function TaskProcessingPage({ tasks, onOpen }) {
  const [filters, setFilters] = useState({ title: "", nature: "", status: "", cycle: "" });
  const [applied, setApplied] = useState(filters);
  const [page, setPage] = useState(1);
  const employeeTasks = getEmployeeTasks(tasks, new Date());
  const filtered = useMemo(() => employeeTasks.filter((task) => (!applied.title || task.title.includes(applied.title)) && (!applied.nature || task.nature === applied.nature) && (!applied.status || (task.status || "pending") === applied.status) && (!applied.cycle || task.cycle === applied.cycle)), [employeeTasks, applied]);
  const rows = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const reset = () => { const next = { title: "", nature: "", status: "", cycle: "" }; setFilters(next); setApplied(next); setPage(1); };
  return <div className="page-wrap"><PageHeading title="任务处理" subtitle="查看当前时间窗内收到的任务，提交后从工作台待办中移除" /><section className="filter-card panel-card"><div className="filter-row"><label className="filter-field"><span>任务标题</span><input value={filters.title} onChange={(event) => setFilters({ ...filters, title: event.target.value })} placeholder="请输入任务标题" /></label><label className="filter-field"><span>任务性质</span><select value={filters.nature} onChange={(event) => setFilters({ ...filters, nature: event.target.value })}><option value="">全部</option><option value="scheduled">定时任务</option><option value="temporary">临时任务</option></select></label><label className="filter-field"><span>任务状态</span><select value={filters.status} onChange={(event) => setFilters({ ...filters, status: event.target.value })}><option value="">全部</option><option value="pending">待处理</option><option value="completed">已完成</option></select></label><label className="filter-field"><span>周期</span><select value={filters.cycle} onChange={(event) => setFilters({ ...filters, cycle: event.target.value })}><option value="">全部</option>{CYCLES.map((cycle) => <option key={cycle.value} value={cycle.value}>{cycle.label}</option>)}</select></label><div className="filter-actions"><button className="primary-button compact" onClick={() => { setApplied({ ...filters }); setPage(1); }}><Search size={15} />查询</button><button className="plain-button compact" onClick={reset}><RotateCcw size={15} />重置</button></div></div></section><section className="panel-card table-card"><div className="card-title-row"><div><h2>我的任务</h2><span>任务标题可进入任务详情，四大维度材料任务将进入现有上报入口</span></div><span className="result-count">共 {filtered.length} 条</span></div><div className="table-scroll"><table><thead><tr><th>任务标题</th><th>任务说明</th><th>任务性质</th><th>周期 / 时间窗</th><th>截止时间</th><th>状态</th><th>操作</th></tr></thead><tbody>{rows.length ? rows.map((task) => <tr key={task.id}><td><button className="title-link" onClick={() => onOpen(task)}>{task.title}<ExternalLink size={13} /></button></td><td><span className="ellipsis" title={task.description}>{task.description}</span></td><td>{task.nature === "scheduled" ? "定时任务" : "临时任务"}</td><td>{task.nature === "scheduled" ? `${cycleLabel(task.cycle)} · ${task.window?.start} 至 ${task.window?.end}` : "临时任务"}</td><td>{task.nature === "scheduled" ? task.window?.end : task.endAt}</td><td><Tag tone={task.status === "completed" ? "green" : "blue"}>{task.status === "completed" ? "已完成" : "待处理"}</Tag></td><td><button className="link-button" onClick={() => onOpen(task)}>{task.status === "completed" ? "查看" : "处理"}</button></td></tr>) : <tr><td colSpan="7"><EmptyState title="暂无当前任务" description="当前时间窗内没有待处理任务" /></td></tr>}</tbody></table></div><Pagination page={page} total={filtered.length} onChange={setPage} /></section></div>;
}

function WorkbenchPage({ tasks, onOpen, role }) {
  const pending = tasks.filter((task) => task.status === "pending");
  const completed = tasks.filter((task) => task.status === "completed");
  return <div className="page-wrap workbench-page"><PageHeading title="工作台" subtitle={`欢迎回来，${role === "admin" ? "组织管理员" : "一线员工"}`} /><div className="metric-grid"><div className="metric-card"><div className="metric-icon red"><ListTodo size={19} /></div><div><span>待办事项</span><strong>{pending.length}</strong></div></div><div className="metric-card"><div className="metric-icon blue"><CheckCircle2 size={19} /></div><div><span>已完成任务</span><strong>{completed.length}</strong></div></div><div className="metric-card"><div className="metric-icon orange"><Users size={19} /></div><div><span>本组织任务</span><strong>{tasks.length}</strong></div></div></div><section className="panel-card todo-card"><div className="card-title-row"><div><h2>待办事项</h2><span>当前处于任务时间窗且尚未提交的任务</span></div><Tag tone="red">{pending.length} 项待处理</Tag></div>{pending.length ? <div className="todo-list">{pending.map((task) => <button className="todo-item" key={task.id} onClick={() => onOpen(task)}><div className={`todo-icon ${task.route === "materials-entry" ? "materials" : "task"}`}>{task.route === "materials-entry" ? <FilePenLine size={17} /> : <ListTodo size={17} />}</div><div className="todo-main"><strong>{task.title}</strong><span>{task.description}</span></div><div className="todo-meta"><Tag tone={task.route === "materials-entry" ? "red" : "blue"}>{task.route === "materials-entry" ? "材料上报入口" : task.type}</Tag><span>{task.nature === "scheduled" ? `${cycleLabel(task.cycle)} · ${task.window?.end}` : `截止 ${task.endAt}`}</span></div><ChevronRight className="todo-arrow" size={17} /></button>)}</div> : <EmptyState title="待办已清空" description="当前没有需要处理的任务" />}</section><div className="workbench-note"><Clock3 size={16} /><span>任务只在时间窗开始后出现在首页待办，提交后立即移除；四大维度材料任务继续由现有材料上报入口处理。</span></div></div>;
}

function TaskDetailPage({ task, readonly, onBack, onComplete }) {
  const [fileName, setFileName] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [submitError, setSubmitError] = useState("");
  const description = linkifyDescription(task.description);
  const handleFile = (event) => { setFileName(event.target.files?.[0]?.name || ""); setSubmitError(""); };
  const requestSubmit = () => {
    const validation = validateTaskSubmission(task, fileName);
    if (!validation.valid) { setSubmitError(validation.errors.file); return; }
    setSubmitError("");
    setConfirming(true);
  };
  return <div className="page-wrap detail-page"><PageHeading title={readonly ? "查看任务" : "处理任务"} action={<button className="plain-button" onClick={onBack}><ArrowLeft size={16} />返回列表</button>} /><section className="panel-card detail-card"><div className="detail-header"><div><div className="eyebrow">{task.type}</div><h2>{task.title}</h2></div><Tag tone={task.status === "completed" || task.employeeStatus === "completed" ? "green" : readonly ? "blue" : "blue"}>{task.status === "completed" || task.employeeStatus === "completed" ? "已完成" : readonly ? "已发布" : "待处理"}</Tag></div><div className="detail-meta-grid"><div><span>任务性质</span><strong>{task.nature === "scheduled" ? "定时任务" : "临时任务"}</strong></div><div><span>{task.nature === "scheduled" ? "周期" : "时间范围"}</span><strong>{task.nature === "scheduled" ? cycleLabel(task.cycle) : `${task.startAt} 至 ${task.endAt}`}</strong></div><div><span>任务对象</span><strong>{task.objectLabel}</strong></div><div><span>{task.nature === "scheduled" ? "时间窗" : "截止时间"}</span><strong>{task.nature === "scheduled" ? `${task.window?.start} 至 ${task.window?.end}` : task.endAt}</strong></div></div><div className="detail-divider" /><div className="detail-section"><h3>任务说明</h3><div className="description-content">{description.map((part, index) => part.type === "link" ? <a key={index} href={part.href} target={part.target} rel={part.rel}>{part.value}<ExternalLink size={13} /></a> : <span key={index}>{part.value}</span>)}</div></div>{task.requiresFile && <div className="detail-section upload-section"><div className="upload-title"><h3>附件上传</h3><span>允许类型：{task.fileTypes.join("、")}</span></div><label className={`upload-box ${readonly ? "is-readonly" : ""}`}><input type="file" disabled={readonly} onChange={handleFile} accept={task.fileTypes.map((type) => ({ PDF: ".pdf", Word: ".doc,.docx", Excel: ".xls,.xlsx", 图片: "image/*", 压缩包: ".zip,.rar,.7z" }[type] || "")).join(",")} /><UploadCloud size={26} /><strong>{fileName || (readonly ? "该任务要求上传文件" : "点击或拖拽上传文件")}</strong><span>{task.fileTypes.join("、")} 格式</span></label></div>}{!readonly && task.employeeStatus !== "completed" && <div className="detail-actions">{submitError && <div className="submit-error">{submitError}</div>}{confirming ? <div className="confirm-strip"><span>确认提交任务？提交后将从工作台待办中移除。</span><button className="plain-button compact" onClick={() => setConfirming(false)}>再想想</button><button className="primary-button compact" onClick={() => onComplete(task.id)}>确认提交</button></div> : <button className="primary-button" onClick={requestSubmit}><Check size={16} />提交任务</button>}</div>}</section></div>;
}

function MaterialsEntryPage({ task, onBack }) {
  return <div className="page-wrap"><PageHeading title="四大维度材料上报" subtitle="该任务使用现有材料上报入口处理" action={<button className="plain-button" onClick={onBack}><ArrowLeft size={16} />返回工作台</button>} /><section className="panel-card branch-card"><div className="branch-icon"><FilePenLine size={24} /></div><Tag tone="red">四大维度材料上报</Tag><h2>{task.title}</h2><p>此类任务继续由现有四大维度材料上报页面处理，不进入通用任务详情填报流程。</p><div className="branch-task-summary"><div><span>任务周期</span><strong>{cycleLabel(task.cycle)} · {task.window?.start} 至 {task.window?.end}</strong></div><div><span>任务对象</span><strong>{task.objectLabel}</strong></div><div><span>允许上传</span><strong>{task.fileTypes.join("、")}</strong></div></div><button className="primary-button" onClick={() => alert("原有四大维度材料上报入口（原型说明，不重做原页面）")}><FilePenLine size={16} />进入现有材料上报入口</button><small className="branch-note">原型仅展示分支说明，不改动或重做原有页面。</small></section></div>;
}

function EmptyState({ title, description }) {
  return <div className="empty-state"><div className="empty-icon"><ClipboardList size={22} /></div><strong>{title}</strong><span>{description}</span></div>;
}

function LegacyPage({ title }) {
  return <div className="page-wrap"><PageHeading title={title} /><section className="panel-card legacy-card"><Building2 size={28} /><h2>{title}</h2><p>本次原型仅新增任务管理与任务处理相关页面，原有页面保持现状。</p></section></div>;
}

export { App };
