<template>
  <div class="ab-page">
    <div class="page-h">
      <h1>分行通讯录 <span class="sub">模糊搜索 · 60 天未更新提醒 · 勾选产品反向更新产品库</span></h1>
    </div>

    <div class="card-section">
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="搜索">
          <el-input v-model="filters.keyword" placeholder="姓名 / 工号 / 联系方式" clearable style="width:220px"
                    @keyup.enter="reload" @clear="reload" />
        </el-form-item>
        <el-form-item label="组织节点">
          <el-tree-select v-model="filters.orgCode" :data="orgTree" check-strictly clearable
                          :props="{ label: 'name', value: 'code', children: 'children' }"
                          placeholder="全部" style="width:200px" @change="reload" />
        </el-form-item>
        <el-form-item label="岗位">
          <el-input v-model="filters.position" placeholder="全部" clearable style="width:150px"
                    @keyup.enter="reload" @clear="reload" />
        </el-form-item>
        <el-form-item label="负责产品">
          <el-select v-model="filters.productId" placeholder="全部" clearable filterable style="width:180px">
            <el-option v-for="p in products" :key="p.id" :value="p.id" :label="p.productName" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="filters.stale60">仅显示 60 天未更新</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="reload">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-section">
      <el-table :data="viewRows" v-loading="loading" empty-text="暂无员工">
        <el-table-column label="姓名" width="150">
          <template #default="{row}">
            <span class="avatar">{{ (row.empName || '?').charAt(0) }}</span>
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
            <template v-if="row.responsibleProducts && row.responsibleProducts.length">
              <el-tag v-for="p in row.responsibleProducts" :key="p.id" class="prod-tag" effect="plain" type="info">{{ p.productName }}</el-tag>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{row}">
            {{ fmtDate(row.updatedTime) }}
            <el-tag v-if="isStale(row.updatedTime)" type="warning" effect="plain" size="small">60天未更新</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="pgNo" v-model:page-size="pgSize" :page-sizes="[10,20,50]"
                       :total="total" background layout="total, sizes, prev, pager, next" @change="reload" />
      </div>
    </div>

    <!-- 编辑抽屉 -->
    <el-drawer v-model="drawer" :title="`编辑通讯录 · ${cur?.empName || ''}`" size="520px">
      <div v-if="cur" class="drawer-body">
        <el-alert type="info" :closable="false" show-icon
                  title="勾选「负责产品」将自动反向更新产品资料库的「产品负责人」字段。" style="margin-bottom:16px" />
        <div class="sec-title">基本信息（只读）</div>
        <el-descriptions :column="2" border size="small" class="ro-info">
          <el-descriptions-item label="工号">{{ cur.empId }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ cur.empName }}</el-descriptions-item>
          <el-descriptions-item label="组织节点">{{ cur.orgName }}</el-descriptions-item>
          <el-descriptions-item label="岗位">{{ cur.positionDesc || cur.position || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="sec-title">联系方式（可编辑）</div>
        <el-form :model="ef" label-width="60px">
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="电话"><el-input v-model="ef.mobile" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="邮箱"><el-input v-model="ef.email" /></el-form-item></el-col>
          </el-row>
        </el-form>

        <div class="sec-title">负责产品（多选）</div>
        <el-checkbox-group v-model="ef.responsibleProductIds" class="prod-checks">
          <el-checkbox v-for="p in products" :key="p.id" :value="p.id" border>{{ p.productName }}</el-checkbox>
        </el-checkbox-group>

        <div class="sec-title">自我描述</div>
        <el-input v-model="ef.selfDesc" type="textarea" :rows="4" maxlength="500" show-word-limit
                  placeholder="个人专长等" />
      </div>
      <template #footer>
        <el-button @click="drawer = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { fmtDateTime } from '@/utils/datetime';
import { pageEmployees, updateEmployee } from '@/api/employees';
import { supportAvailableProducts } from '@/api/products';
import { getOrgTree } from '@/api/orgs';

const loading = ref(false);
const saving = ref(false);
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

// 客户端附加过滤（负责产品 / 60天未更新）—— 后端查询参不含这两项
const viewRows = computed(() => {
  let list = rows.value;
  if (filters.value.productId) {
    list = list.filter(r => (r.responsibleProductIds || []).includes(filters.value.productId));
  }
  if (filters.value.stale60) {
    list = list.filter(r => isStale(r.updatedTime));
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
      pageNo: pgNo.value, pageSize: pgSize.value
    });
    rows.value = Array.isArray(r) ? r : (r?.records || []);
    total.value = Array.isArray(r) ? r.length : (r?.total ?? 0);
  } catch { rows.value = []; total.value = 0; } finally { loading.value = false; }
}

async function loadRefs() {
  try { products.value = await supportAvailableProducts() || []; } catch { products.value = []; }
  try { orgTree.value = await getOrgTree() || []; } catch { orgTree.value = []; }
}

// ---- 编辑抽屉 ----
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
  saving.value = true;
  try {
    await updateEmployee(cur.value.empId, {
      mobile: ef.value.mobile,
      email: ef.value.email,
      position: cur.value.position, // 岗位只读，原值回传
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
.ab-page { }
.page-h h1 { font-size: 18px; margin: 0; }
.page-h .sub { font-size: 12px; color: #909399; font-weight: normal; margin-left: 8px; }
.filter-bar { margin: 0; }
.avatar { display: inline-flex; width: 26px; height: 26px; border-radius: 50%; background: var(--el-color-primary); color: #fff; align-items: center; justify-content: center; font-size: 12px; margin-right: 8px; vertical-align: middle; }
.name { vertical-align: middle; }
.muted { color: #909399; font-size: 12px; }
.prod-tag { margin: 0 4px 4px 0; }
.pager { margin-top: 12px; text-align: right; }
.sec-title { font-size: 13px; font-weight: 600; color: #303133; margin: 16px 0 10px; }
.ro-info { margin-bottom: 4px; }
.prod-checks { display: flex; flex-wrap: wrap; gap: 8px; }
.prod-checks :deep(.el-checkbox) { margin-right: 0; }
</style>
