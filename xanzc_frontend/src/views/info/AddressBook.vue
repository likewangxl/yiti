<template>
  <main class="bp-crud ab-page" aria-labelledby="address-book-title">
    <header class="page-h">
      <PageTitle id="address-book-title"><span class="sub">模糊搜索、60 天未更新提醒，负责产品会反向更新产品库</span></PageTitle>
      <div class="actions action-group" role="group" aria-label="通讯录操作">
        <el-button @click="downloadTemplate">下载模板</el-button>
        <el-upload
          ref="importUploaderRef"
          :auto-upload="false"
          :show-file-list="false"
          :disabled="importing"
          accept=".xlsx,.xls"
          :on-change="onImportPick"
          style="display:inline-block"
        >
          <el-button aria-label="导入通讯录文件" :loading="importing" :disabled="importing">导入</el-button>
        </el-upload>
        <el-button @click="exportData">导出</el-button>
      </div>
    </header>

    <section class="card-section filter-bar" aria-label="通讯录筛选">
      <el-form class="filter-form" :inline="true" aria-label="通讯录筛选">
        <el-form-item label="搜索">
          <el-input
            v-model="filters.keyword"
            aria-label="按姓名工号或联系方式筛选"
            placeholder="姓名 / 工号 / 联系方式"
            clearable
            style="width:220px"
            @keyup.enter="reload"
            @clear="reload"
          />
        </el-form-item>
        <el-form-item label="组织节点">
          <el-tree-select
            v-model="filters.orgCode"
            aria-label="按组织节点筛选"
            :data="orgTree"
            check-strictly
            clearable
            filterable
            :filter-node-method="orgFilter"
            :props="{ label: 'name', value: 'code', children: 'children' }"
            placeholder="选择 / 输入机构编号或名称"
            style="width:240px"
            @change="reload"
          />
        </el-form-item>
        <el-form-item label="岗位">
          <el-input
            v-model="filters.position"
            aria-label="按岗位筛选"
            placeholder="全部"
            clearable
            style="width:150px"
            @keyup.enter="reload"
            @clear="reload"
          />
        </el-form-item>
        <el-form-item label="负责产品">
          <el-select v-model="filters.productId" aria-label="按负责产品筛选" placeholder="全部" clearable filterable style="width:180px">
            <el-option v-for="p in products" :key="p.id" :value="p.id" :label="p.productName" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="filters.stale60">仅显示 60 天未更新</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section
      class="card-section data-panel"
      aria-label="通讯录列表"
      aria-describedby="address-book-state"
      :aria-busy="loading ? 'true' : 'false'"
    >
      <div class="toolbar">
        <div>
          <h2 id="address-book-heading" class="section-title">通讯录列表</h2>
          <p class="hint">联系方式、自我描述和负责产品可在编辑抽屉中维护。</p>
        </div>
        <p id="address-book-state" class="table-state" role="status" aria-live="polite">
          {{ loading ? '通讯录列表加载中' : viewRows.length ? `当前页展示 ${viewRows.length} 名员工` : '暂无员工数据' }}
        </p>
      </div>

      <el-table
        :data="viewRows"
        v-loading="loading"
        empty-text="暂无员工数据"
        aria-labelledby="address-book-heading"
        aria-describedby="address-book-state"
      >
        <el-table-column label="姓名" width="150">
          <template #default="{row}">
            <span class="avatar" aria-hidden="true">{{ (row.empName || '?').charAt(0) }}</span>
            <span class="name">{{ row.empName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="empId" label="工号" width="100" />
        <el-table-column prop="orgName" label="组织节点" min-width="160" show-overflow-tooltip />
        <el-table-column label="岗位" width="120">
          <template #default="{row}"><el-tag effect="plain">{{ row.positionDesc || row.position || '-' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="联系方式" min-width="200">
          <template #default="{row}">
            <div>{{ row.mobile || '—' }}</div>
            <div class="muted">{{ row.email || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="负责产品" min-width="200">
          <template #default="{row}">
            <template v-if="rowProducts(row).length">
              <el-tag v-for="p in rowProducts(row)" :key="p.id" class="prod-tag" effect="plain" type="info">{{ p.productName }}</el-tag>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{row}">
            {{ fmtDate(row.updatedTime) }}
            <el-tag v-if="isStale(row.updatedTime)" class="tag-warning" effect="plain" size="small">60天未更新</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <nav class="pager" aria-label="通讯录列表分页">
        <el-pagination
          v-model:current-page="pgNo"
          v-model:page-size="pgSize"
          :page-sizes="[10,20,50]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next"
          @change="reload"
        />
      </nav>
    </section>

    <el-drawer v-model="drawer" class="bp-crud-dialog" :title="`编辑通讯录 · ${cur?.empName || ''}`" size="840px">
      <div v-if="cur" class="drawer-body">
        <el-alert
          class="drawer-alert"
          type="info"
          :closable="false"
          show-icon
          title="勾选“负责产品”将自动反向更新产品资料库的“产品负责人”字段。"
        />
        <h2 class="sec-title">基本信息（只读）</h2>
        <el-descriptions :column="2" border size="small" class="ro-info">
          <el-descriptions-item label="工号">{{ cur.empId }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ cur.empName }}</el-descriptions-item>
          <el-descriptions-item label="组织节点">{{ cur.orgName }}</el-descriptions-item>
          <el-descriptions-item label="岗位">{{ cur.positionDesc || cur.position || '-' }}</el-descriptions-item>
        </el-descriptions>

        <h2 class="sec-title">联系方式（可编辑）</h2>
        <el-form :model="ef" label-width="60px">
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="电话"><el-input v-model="ef.mobile" aria-label="联系电话" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="邮箱"><el-input v-model="ef.email" aria-label="联系邮箱" /></el-form-item></el-col>
          </el-row>
        </el-form>

        <h2 class="sec-title">负责产品（多选）</h2>
        <el-checkbox-group v-model="ef.responsibleProductIds" class="prod-checks" aria-label="负责产品">
          <el-checkbox v-for="p in products" :key="p.id" :value="p.id" border>{{ p.productName }}</el-checkbox>
        </el-checkbox-group>

        <h2 class="sec-title">自我描述</h2>
        <el-input
          v-model="ef.selfDesc"
          aria-label="自我描述"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="个人专长等"
        />
      </div>
      <template #footer>
        <el-button :disabled="saving" @click="drawer = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </main>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fmtDateTime } from '@/utils/datetime';
import { pageEmployees, updateEmployee, importEmployeesFile } from '@/api/employees';
import { supportAvailableProducts } from '@/api/products';
import { getOrgTree } from '@/api/orgs';

const loading = ref(false);
const saving = ref(false);
const importing = ref(false);
const importUploaderRef = ref(null);
const rows = ref([]);
const total = ref(0);
const pgNo = ref(1);
const pgSize = ref(20);
const products = ref([]);
const orgTree = ref([]);
const filters = ref({ keyword: '', orgCode: '', position: '', productId: '', stale60: false });

const fmtDate = (v) => fmtDateTime(v);
function isStale(v) {
  if (!v) return false;
  const d = new Date(v);
  if (isNaN(d.getTime())) return false;
  return (Date.now() - d.getTime()) > 60 * 24 * 3600 * 1000;
}

const productById = computed(() => {
  const map = {};
  for (const product of products.value) map[product.id] = product;
  return map;
});

function rowProducts(row) {
  if (row.responsibleProducts && row.responsibleProducts.length) return row.responsibleProducts;
  return (row.responsibleProductIds || [])
    .map(id => productById.value[id] || { id, productName: id })
    .filter(Boolean);
}

function resetFilters() {
  filters.value = { keyword: '', orgCode: '', position: '', productId: '', stale60: false };
  pgNo.value = 1;
  reload();
}

const viewRows = computed(() => {
  let list = rows.value;
  if (filters.value.productId) {
    list = list.filter(row => (row.responsibleProductIds || []).includes(filters.value.productId));
  }
  if (filters.value.stale60) {
    list = list.filter(row => isStale(row.updatedTime));
  }
  return list;
});

async function reload() {
  loading.value = true;
  try {
    const r = await pageEmployees({
      keyword: filters.value.keyword || undefined,
      orgCode: filters.value.orgCode || undefined,
      position: filters.value.position || undefined,
      pageNo: pgNo.value,
      pageSize: pgSize.value
    });
    rows.value = Array.isArray(r) ? r : (r?.records || []);
    total.value = Array.isArray(r) ? r.length : (r?.total ?? 0);
  } catch { rows.value = []; total.value = 0; } finally { loading.value = false; }
}

async function loadRefs() {
  try { products.value = await supportAvailableProducts() || []; } catch { products.value = []; }
  try { orgTree.value = await getOrgTree() || []; } catch { orgTree.value = []; }
}

function orgFilter(value, data) {
  if (!value) return true;
  const keyword = String(value).toLowerCase();
  return (data.name || '').toLowerCase().includes(keyword) || (data.code || '').toLowerCase().includes(keyword);
}

function downloadTemplate() {
  window.open('/api/employees/template', '_blank');
}

function exportData() {
  const p = new URLSearchParams();
  if (filters.value.keyword) p.append('keyword', filters.value.keyword);
  if (filters.value.orgCode) p.append('orgCode', filters.value.orgCode);
  if (filters.value.position) p.append('position', filters.value.position);
  window.open('/api/employees/export?' + p.toString(), '_blank');
}

async function onImportPick(file) {
  if (importing.value || !file?.raw) return;
  importing.value = true;
  try {
    const count = await importEmployeesFile(file.raw);
    ElMessage.success(`导入成功 ${count ?? ''} 条`);
    reload();
  } catch (e) {
    ElMessageBox.alert(e?.message || '导入失败', '导入失败', { type: 'error', confirmButtonText: '知道了' });
  } finally {
    importing.value = false;
    importUploaderRef.value?.clearFiles();
  }
}

const drawer = ref(false);
const cur = ref(null);
const ef = ref({ mobile: '', email: '', selfDesc: '', responsibleProductIds: [] });

function openEdit(row) {
  cur.value = row;
  ef.value = {
    mobile: row.mobile || '',
    email: row.email || '',
    selfDesc: row.selfDesc || '',
    responsibleProductIds: [...(row.responsibleProductIds || [])]
  };
  drawer.value = true;
}

async function save() {
  if (saving.value || !cur.value) return;
  saving.value = true;
  try {
    await updateEmployee(cur.value.empId, {
      mobile: ef.value.mobile,
      email: ef.value.email,
      position: cur.value.position,
      selfDesc: ef.value.selfDesc,
      responsibleProductIds: ef.value.responsibleProductIds
    });
    ElMessage.success('已保存');
    drawer.value = false;
    reload();
  } catch (e) { ElMessage.error(e?.message || '保存失败'); } finally { saving.value = false; }
}

onMounted(() => { loadRefs(); reload(); });
</script>

<style scoped>
.avatar { align-items: center; background: var(--color-brand-700); color: var(--color-surface); display: inline-flex; font-size: 12px; height: 26px; justify-content: center; margin-right: var(--space-2); vertical-align: middle; width: 26px; }
.name { vertical-align: middle; }
.muted { color: var(--color-text-muted); font-size: 12px; line-height: 18px; }
.prod-tag { margin: 0 var(--space-1) var(--space-1) 0; }
.drawer-alert { margin-bottom: var(--space-4); }
.sec-title { color: var(--color-text-strong); font-size: 14px; font-weight: 600; line-height: 22px; margin: var(--space-4) 0 var(--space-2); }
.ro-info { margin-bottom: var(--space-1); }
.ro-info :deep(.el-descriptions__table) { table-layout: fixed; width: 100%; }
.ro-info :deep(.el-descriptions__label) { width: 90px; }
.prod-checks { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.prod-checks :deep(.el-checkbox) { margin-right: 0; }
</style>
