<template>
  <main
    class="page bp-crud touch-limit-page"
    v-bp-overflow-tooltip
    aria-labelledby="touch-limit-page-title"
    :aria-busy="loading ? 'true' : 'false'"
  >
    <header class="page-head">
      <div>
        <PageTitle id="touch-limit-page-title" title="客户触达周期管理" />
        <span>按客户标签维护企业触达周期和次数上限，未配置时默认按月 5 次计算。</span>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>

    <el-form inline class="filter-form" aria-label="客户触达周期筛选" @submit.prevent="search">
      <el-form-item label="标签名称">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="请输入标签名称"
          aria-label="按标签名称搜索"
          @keyup.enter="search"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <section class="card-section data-panel" aria-label="客户触达周期规则列表">
      <el-table
        :data="rows"
        border
        stripe
        v-loading="loading"
        empty-text="暂无客户标签"
        aria-label="客户触达周期规则列表"
      >
        <el-table-column prop="tagName" label="标签名" min-width="180" show-overflow-tooltip />
        <el-table-column prop="tagStatus" label="标签状态" width="120">
          <template #default="{ row }">
            <el-tag :type="tagStatusType(row.tagStatus)">{{ tagStatusLabel(row.tagStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approvalStatus" label="审核状态" width="120">
          <template #default="{ row }">
            <el-tag :type="approvalStatusType(row.approvalStatus)">{{ approvalStatusLabel(row.approvalStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="周期" width="140">
          <template #default="{ row }">{{ cycleUnitLabel(row.cycleUnit) }}</template>
        </el-table-column>
        <el-table-column label="上限" width="140">
          <template #default="{ row }">{{ row.maxTouches }} 次</template>
        </el-table-column>
        <el-table-column label="操作" width="96" fixed="right" class-name="operation-cell">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">修改</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager" aria-label="客户触达周期分页">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="onPageSizeChange"
        />
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      title="修改客户触达周期"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form label-position="top" class="dialog-form" @submit.prevent="onSubmit">
        <el-form-item label="标签名称">
          <el-input :model-value="form.tagName" disabled />
        </el-form-item>
        <el-form-item label="触达周期" required>
          <el-select v-model="form.cycleUnit" style="width: 100%" aria-label="触达周期">
            <el-option
              v-for="option in CYCLE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="触达次数上限" required>
          <el-input-number
            v-model="form.maxTouches"
            :min="1"
            :max="9999"
            :precision="0"
            :step="1"
            controls-position="right"
            style="width: 180px"
            aria-label="触达次数上限"
          />
          <span class="unit-hint">次</span>
        </el-form-item>
        <p class="form-hint">次数上限取值范围为 1-9999，保存后供后续触达校验使用，本阶段不执行触达拦截。</p>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { listTouchLimitRules, updateTouchLimitRule } from '@/api/customerMarketing';

const DEFAULT_CYCLE_UNIT = 'MONTH';
const DEFAULT_MAX_TOUCHES = 5;
const CYCLE_OPTIONS = [
  { value: 'DAY', label: '日' },
  { value: 'WEEK', label: '周' },
  { value: 'MONTH', label: '月' },
  { value: 'QUARTER', label: '季' },
  { value: 'YEAR', label: '年' },
];
const CYCLE_UNITS = new Set(CYCLE_OPTIONS.map((option) => option.value));
const CYCLE_LABELS = Object.fromEntries(CYCLE_OPTIONS.map((option) => [option.value, option.label]));

const query = reactive({ keyword: '', pageNo: 1, pageSize: 20 });
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const loadError = ref('');
const dialogVisible = ref(false);
const saving = ref(false);
const form = reactive({
  tagId: '',
  tagName: '',
  cycleUnit: DEFAULT_CYCLE_UNIT,
  maxTouches: DEFAULT_MAX_TOUCHES,
});

function normalizeMaxTouches(value) {
  const number = Number(value);
  return Number.isInteger(number) && number >= 1 && number <= 9999 ? number : DEFAULT_MAX_TOUCHES;
}

function normalizeRow(row = {}) {
  const cycleUnit = CYCLE_UNITS.has(row.cycleUnit) ? row.cycleUnit : DEFAULT_CYCLE_UNIT;
  return {
    ...row,
    tagId: row.tagId ?? row.id,
    tagName: row.tagName || '-',
    tagStatus: row.tagStatus ?? row.status ?? '',
    approvalStatus: row.approvalStatus ?? '',
    cycleUnit,
    maxTouches: normalizeMaxTouches(row.maxTouches),
  };
}

function tagStatusLabel(value) {
  const status = value;
  if (status === 'ACTIVE' || status === 1 || status === '1') return '启用';
  if (status === 'DISABLED' || status === 0 || status === '0') return '禁用';
  return status || '-';
}

function tagStatusType(value) {
  const status = value;
  if (status === 'ACTIVE' || status === 1 || status === '1') return 'success';
  if (status === 'DISABLED' || status === 0 || status === '0') return 'info';
  return '';
}

function approvalStatusLabel(value) {
  return {
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已退回',
  }[value] || value || '-';
}

function approvalStatusType(value) {
  return { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[value] || '';
}

function cycleUnitLabel(value) {
  return CYCLE_LABELS[value] || value || '-';
}

async function load() {
  loading.value = true;
  loadError.value = '';
  try {
    const result = await listTouchLimitRules({
      keyword: query.keyword.trim() || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize,
    });
    rows.value = (Array.isArray(result) ? result : result?.records || []).map(normalizeRow);
    total.value = Array.isArray(result) ? rows.value.length : Number(result?.total ?? 0);
  } catch (error) {
    rows.value = [];
    total.value = 0;
    loadError.value = error?.message || '触达周期规则加载失败';
    ElMessage.error(loadError.value);
  } finally {
    loading.value = false;
  }
}

function search() {
  query.pageNo = 1;
  load();
}

function reset() {
  query.keyword = '';
  query.pageNo = 1;
  load();
}

function onPageSizeChange() {
  query.pageNo = 1;
  load();
}

function openEdit(row) {
  const normalized = normalizeRow(row);
  Object.assign(form, {
    tagId: normalized.tagId,
    tagName: normalized.tagName,
    cycleUnit: normalized.cycleUnit,
    maxTouches: normalized.maxTouches,
  });
  dialogVisible.value = true;
}

function validateForm() {
  if (!form.tagId) return '缺少客户标签标识';
  if (!CYCLE_UNITS.has(form.cycleUnit)) return '触达周期必须为日、周、月、季或年';
  const maxTouches = Number(form.maxTouches);
  if (!Number.isInteger(maxTouches) || maxTouches < 1 || maxTouches > 9999) {
    return '触达次数上限必须为 1-9999 的整数';
  }
  return '';
}

async function onSubmit() {
  if (saving.value) return;
  const validationMessage = validateForm();
  if (validationMessage) {
    ElMessage.warning(validationMessage);
    return;
  }
  saving.value = true;
  try {
    await updateTouchLimitRule(form.tagId, {
      cycleUnit: form.cycleUnit,
      maxTouches: Number(form.maxTouches),
    });
    ElMessage.success('客户触达周期已更新');
    dialogVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(error?.message || '客户触达周期更新失败');
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<style scoped lang="scss">
.page-head {
  align-items: flex-start;
  display: flex;
  justify-content: space-between;
  margin-bottom: 14px;
}

.page-head h1 {
  margin: 0;
}

.page-head span {
  color: #909399;
  display: block;
  font-size: 12px;
  margin-top: 4px;
}

.filter-form {
  margin-bottom: 14px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.dialog-form {
  margin-top: 4px;
}

.unit-hint {
  color: #909399;
  margin-left: 8px;
}

.form-hint {
  color: #909399;
  font-size: 12px;
  margin: 6px 0 0;
}
</style>
