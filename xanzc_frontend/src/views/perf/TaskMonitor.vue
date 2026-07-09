<template>
  <div>
    <div class="page-h">
      <h1>任务监控 <span class="sub">指标重算任务执行状态 / 进度 / 错误原因</span></h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
      </div>
    </div>

    <!-- 过滤区：状态 / 数据日期范围 / 指标关键字 / 发起人 -->
    <div class="card-section">
      <el-form :inline="true" size="default">
        <el-form-item label="执行状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:160px">
            <el-option v-for="(v, k) in STATUS_MAP" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据日期">
          <el-date-picker v-model="query.dateRange" type="daterange" value-format="YYYY-MM-DD"
            range-separator="至" start-placeholder="起始日期" end-placeholder="结束日期"
            clearable style="width:260px" />
        </el-form-item>
        <el-form-item label="指标">
          <el-input v-model="query.taskKey" clearable placeholder="指标编码" style="width:180px"
            @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="发起人">
          <el-input v-model="query.startedBy" clearable placeholder="工号" style="width:160px"
            @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-section table">
      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无任务记录">
        <el-table-column label="任务类型" width="110">
          <template #default>指标重算</template>
        </el-table-column>
        <el-table-column label="子名称" min-width="260" show-overflow-tooltip>
          <template #default="{row}">
            <code class="mono">{{ row.taskKey || '-' }}</code>
            <span v-if="row.taskKeyName" class="sub-name"> · {{ row.taskKeyName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="执行状态" width="110">
          <template #default="{row}">
            <el-tag :class="statusCls(row.status)" effect="plain" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="170">
          <template #default="{row}">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="170">
          <template #default="{row}">{{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="发起人" width="150">
          <template #default="{row}">
            <template v-if="row.startedBy">
              <div>{{ row.startedByName || row.startedBy }}</div>
              <div v-if="row.startedByName" style="color:#909399;font-size:12px;">{{ row.startedBy }}</div>
            </template>
            <template v-else>-</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="reload"
          @size-change="() => { pageNo = 1; reload(); }"
        />
      </div>
    </div>

    <!-- 详情抽屉：全部字段 + 错误全文 + 结果预览 / 参数 -->
    <el-drawer v-model="detail.show" :title="`任务详情 · ${detail.row?.taskKey || ''}`" size="52%" :destroy-on-close="true">
      <template v-if="detail.row">
        <el-descriptions :column="1" border size="default">
          <el-descriptions-item label="任务ID">{{ detail.row.id || '-' }}</el-descriptions-item>
          <el-descriptions-item label="任务类型">{{ detail.row.taskType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="触发来源">{{ detail.row.triggerType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="子名称">
            {{ detail.row.taskKey || '-' }}<span v-if="detail.row.taskKeyName"> · {{ detail.row.taskKeyName }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="数据日期">{{ detail.row.dataDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="数据版本">{{ detail.row.dataVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="执行状态">
            <el-tag :class="statusCls(detail.row.status)" effect="plain" size="small">{{ statusLabel(detail.row.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ fmtTime(detail.row.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ fmtTime(detail.row.endTime) }}</el-descriptions-item>
          <el-descriptions-item label="发起人">
            <template v-if="detail.row.startedBy">
              {{ detail.row.startedByName || '' }}（{{ detail.row.startedBy }}）
            </template>
            <template v-else>-</template>
          </el-descriptions-item>
        </el-descriptions>

        <div class="d-block">
          <div class="d-title">错误原因</div>
          <pre v-if="detail.row.errorMsg" class="err-text">{{ detail.row.errorMsg }}</pre>
          <div v-else class="d-empty">无</div>
        </div>

        <div class="d-block">
          <div class="d-title">结果预览</div>
          <pre v-if="detail.row.resultPreviewJson" class="json-text">{{ fmtJson(detail.row.resultPreviewJson) }}</pre>
          <div v-else class="d-empty">无</div>
        </div>

        <div class="d-block">
          <div class="d-title">执行参数</div>
          <pre v-if="detail.row.paramsJson" class="json-text">{{ fmtJson(detail.row.paramsJson) }}</pre>
          <div v-else class="d-empty">无</div>
        </div>
      </template>
      <template #footer>
        <el-button @click="detail.show = false">关闭</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { listRunTasks } from '@/api/perf';

// 状态字典 + badge 配色（与项目 tag-* 全局类一致）
const STATUS_MAP = {
  PENDING: { label: '待执行', cls: 'tag-info' },
  RUNNING: { label: '执行中', cls: 'tag-warning' },
  SUCCESS: { label: '完成', cls: 'tag-success' },
  PARTIAL_FAILED: { label: '部分失败', cls: 'tag-warning' },
  FAILED: { label: '失败', cls: 'tag-danger' }
};
const statusCls = (s) => STATUS_MAP[s]?.cls || 'tag-info';
const statusLabel = (s) => STATUS_MAP[s]?.label || s || '-';

// 时间格式化：null → '-'；ISO 串统一 "YYYY-MM-DD HH:mm:ss"
function fmtTime(t) {
  if (!t) return '-';
  return String(t).replace('T', ' ').slice(0, 19);
}
// JSON 串美化；非法 JSON 原样展示
function fmtJson(s) {
  if (!s) return '';
  try { return JSON.stringify(typeof s === 'string' ? JSON.parse(s) : s, null, 2); } catch { return String(s); }
}

// 过滤条件
const query = reactive({ status: '', dateRange: [], taskKey: '', startedBy: '' });

// 列表 + 分页
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const pageNo = ref(1);
const pageSize = ref(20);

async function reload() {
  loading.value = true;
  try {
    // 固定过滤：仅指标重算任务（task_type=METRIC_RUN, trigger_type=RECALC）
    const [from, to] = query.dateRange || [];
    const r = await listRunTasks({
      taskType: 'METRIC_RUN',
      triggerType: 'RECALC',
      status: query.status || undefined,
      taskKey: query.taskKey?.trim() || undefined,
      startedBy: query.startedBy?.trim() || undefined,
      dataDateFrom: from || undefined,
      dataDateTo: to || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value
    });
    const arr = r?.records || (Array.isArray(r) ? r : []);
    rows.value = arr;
    total.value = r?.total ?? arr.length;
  } catch { /* call 内部已提示 */ } finally { loading.value = false; }
}

function onSearch() { pageNo.value = 1; reload(); }
function onReset() {
  query.status = '';
  query.dateRange = [];
  query.taskKey = '';
  query.startedBy = '';
  pageNo.value = 1;
  reload();
}

onMounted(reload);

// 详情抽屉
const detail = reactive({ show: false, row: null });
function openDetail(row) {
  detail.row = row;
  detail.show = true;
}
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
.table { padding: 0; padding-bottom: 12px; }
.pager { display: flex; justify-content: flex-end; padding: 12px 14px; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; background: $bg-soft; padding: 2px 6px; border-radius: 3px; }
.sub-name { color: $text-2; font-size: 13px; }

.d-block { margin-top: 20px; }
.d-title { font-size: 14px; font-weight: 600; color: $text-1; margin-bottom: 8px; }
.d-empty { color: $text-3; font-size: 13px; }
.err-text {
  background: #fef2f2; border: 1px solid #fca5a5; padding: 10px 12px;
  border-radius: 4px; font-family: ui-monospace, monospace; font-size: 12px;
  white-space: pre-wrap; word-break: break-all; color: #991b1b; margin: 0; max-height: 320px; overflow: auto;
}
.json-text {
  background: $bg-soft; border: 1px solid $border-1; padding: 10px 12px;
  border-radius: 4px; font-family: ui-monospace, monospace; font-size: 12px;
  white-space: pre-wrap; word-break: break-all; color: $text-1; margin: 0; max-height: 320px; overflow: auto;
}
</style>
