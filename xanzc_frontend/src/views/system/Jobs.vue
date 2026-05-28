<template>
  <div>
    <div class="page-h">
      <h1>任务调度</h1>
      <div class="actions">
        <el-input v-model="keyword" clearable placeholder="关键字搜索" style="width:220px" @keyup.enter="onSearch" />
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="reload">刷新</el-button>
      </div>
    </div>

    <div class="card-section table">
      <el-table :data="rows" size="default" empty-text="暂无任务" v-loading="loading">
        <el-table-column label="任务 Key" min-width="240">
          <template #default="{row}"><code class="mono">{{ row.jobKey }}</code></template>
        </el-table-column>
        <el-table-column prop="jobName" label="任务名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="Cron" width="160">
          <template #default="{row}"><code class="mono">{{ row.cronExpr }}</code></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :class="statusCls(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="允许手动" width="100" align="center">
          <template #default="{row}">{{ row.allowManualTrigger ? '✓' : '—' }}</template>
        </el-table-column>
        <el-table-column prop="lastRunTime" label="上次执行" width="170" :formatter="fmtDateTimeCol" />
        <el-table-column prop="nextFireTime" label="下次执行" width="170" :formatter="fmtDateTimeCol" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openLogs(row)">日志</el-button> |
            <el-button v-if="row.status === 'ACTIVE'" link type="primary" size="small" @click="onPause(row)">暂停</el-button>
            <el-button v-else link type="primary" size="small" @click="onResume(row)">恢复</el-button> |
            <el-button v-if="row.allowManualTrigger" link type="warning" size="small" @click="onTrigger(row)">触发</el-button>
            <span v-else class="disabled-op">触发</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="reload"
          @size-change="() => { pageNo = 1; reload(); }"
        />
      </div>
    </div>

    <!-- 日志弹窗 -->
    <el-dialog v-model="logDlg.show" :title="`执行日志 · ${logDlg.jobName}`" width="900px" top="5vh" @closed="logDlg.rows = []">
      <el-table :data="logDlg.rows" size="default" empty-text="暂无日志" v-loading="logDlg.loading">
        <el-table-column label="触发类型" width="100">
          <template #default="{row}">
            <el-tag :class="row.triggerType === 'MANUAL' ? 'tag-warning' : 'tag-info'" effect="plain" size="small">
              {{ row.triggerType === 'MANUAL' ? '手动' : 'CRON' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="170" :formatter="fmtDateTimeCol" />
        <el-table-column prop="endTime" label="结束时间" width="170" :formatter="fmtDateTimeCol" />
        <el-table-column label="耗时(ms)" width="100" align="right">
          <template #default="{row}">{{ row.durationMs != null ? row.durationMs : '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag
              :class="{ SUCCESS: 'tag-success', FAILED: 'tag-danger', RUNNING: 'tag-warning' }[row.status] || 'tag-info'"
              effect="plain" size="small"
            >{{ row.status || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip>
          <template #default="{row}">{{ row.errorMsg || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="120">
          <template #default="{row}">{{ row.operatorEmpId || '—' }}</template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="logDlg.pageNo"
          v-model:page-size="logDlg.pageSize"
          :total="logDlg.total"
          :page-sizes="[20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadLogs"
          @size-change="() => { logDlg.pageNo = 1; loadLogs(); }"
        />
      </div>
      <template #footer>
        <el-button @click="logDlg.show = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fmtDateTimeCol } from '@/utils/datetime';
import { listJobs, pauseJob, resumeJob, triggerJob, listJobLogs } from '@/api/system';

// === 状态辅助 ===
const statusCls = (s) => ({ ACTIVE: 'tag-success', PAUSED: 'tag-warning', DISABLED: 'tag-danger' }[s] || 'tag-info');
const statusLabel = (s) => ({ ACTIVE: '运行中', PAUSED: '已暂停', DISABLED: '已禁用' }[s] || s || '-');

// === 任务列表 ===
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const pageNo = ref(1);
const pageSize = ref(20);
const keyword = ref('');

async function reload() {
  loading.value = true;
  try {
    const r = await listJobs({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined
    });
    const arr = r?.records || (Array.isArray(r) ? r : []);
    rows.value = arr;
    total.value = r?.total ?? arr.length;
  } catch {} finally { loading.value = false; }
}

function onSearch() {
  pageNo.value = 1;
  reload();
}

onMounted(reload);

// === 操作 ===
async function onPause(row) {
  try { await pauseJob(row.id); ElMessage.success('已暂停'); reload(); } catch {}
}
async function onResume(row) {
  try { await resumeJob(row.id); ElMessage.success('已恢复'); reload(); } catch {}
}
async function onTrigger(row) {
  let reason = '';
  try {
    const { value } = await ElMessageBox.prompt(
      `手动触发任务 <b>${row.jobKey}</b> 属高危操作，请填写触发原因：`,
      '手动触发确认',
      {
        dangerouslyUseHTMLString: true,
        inputPlaceholder: '请输入触发原因（必填）',
        inputValidator: (v) => v && v.trim() ? true : '原因不能为空',
        confirmButtonText: '确认触发',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );
    reason = value.trim();
  } catch { return; }
  try { await triggerJob(row.id, reason); ElMessage.success('已触发'); } catch {}
}

// === 日志弹窗 ===
const logDlg = reactive({
  show: false,
  jobId: null,
  jobName: '',
  rows: [],
  total: 0,
  loading: false,
  pageNo: 1,
  pageSize: 20
});

async function openLogs(row) {
  logDlg.jobId = row.id;
  logDlg.jobName = row.jobName || row.jobKey || '';
  logDlg.pageNo = 1;
  logDlg.total = 0;
  logDlg.rows = [];
  logDlg.show = true;
  await loadLogs();
}

async function loadLogs() {
  if (!logDlg.jobId) return;
  logDlg.loading = true;
  try {
    const r = await listJobLogs(logDlg.jobId, {
      pageNo: logDlg.pageNo,
      pageSize: logDlg.pageSize
    });
    const arr = r?.records || (Array.isArray(r) ? r : []);
    logDlg.rows = arr;
    logDlg.total = r?.total ?? arr.length;
  } catch {} finally { logDlg.loading = false; }
}
</script>

<style lang="scss" scoped>
.table { padding: 0; padding-bottom: 12px; }
.pager { display: flex; justify-content: flex-end; padding: 12px 14px; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; background: $bg-soft; padding: 2px 6px; border-radius: 3px; }
.disabled-op { color: #9CA3AF; font-size: 12px; }
</style>
