<!--
  定价审批查询 —— 历史数据查询（只读）
  数据源：AMAS_PRICE_APPROVAL，按申请时间倒序。
  后端：GET /api/reports/amas-price-approvals （AmasPriceApprovalController.list，已取消数据范围控制）
  查询：客户名称 / 申请人姓名 模糊。
  列表行已含全字段（custInfo/necessExplain/files），“申请资料”弹窗与“操作-下载”直接用行内数据，无需再查详情。
-->
<template>
  <div class="price-approval">
    <div class="page-h">
      <PageTitle />
      <span class="desc">AMAS 定价审批数据查询，按申请时间倒序</span>
    </div>

    <!-- 顶部查询项 -->
    <el-form :model="q" inline class="filter-form" @submit.prevent>
      <el-form-item label="客户名称">
        <el-input v-model="q.custName" placeholder="模糊匹配" clearable style="width:180px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="申请人姓名">
        <el-input v-model="q.applyFullname" placeholder="模糊匹配" clearable style="width:150px" @keyup.enter="onSearch" />
      </el-form-item>
      <el-form-item label="审批状态">
        <el-select v-model="q.apprStatus" placeholder="全部" clearable style="width:130px">
          <el-option label="待审批" value="0" />
          <el-option label="已通过" value="1" />
          <el-option label="未通过" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="申请时间">
        <el-date-picker
          v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
          start-placeholder="起" end-placeholder="止" style="width:240px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" v-loading="loading" border stripe size="default" empty-text="暂无数据">
      <el-table-column type="index" label="序号" width="56" fixed="left" />
      <el-table-column label="申请人" min-width="120" fixed="left">
        <template #default="{ row }">
          <div class="main">{{ row.applyFullname || row.applyUsername || '-' }}</div>
          <div class="sub" v-if="row.applyUsername">{{ row.applyUsername }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="applyTime" label="申请时间" width="160" :formatter="dash" />
      <el-table-column label="客户" min-width="160">
        <template #default="{ row }">
          <div class="main">{{ row.custName || '-' }}</div>
          <div class="sub" v-if="row.custId">{{ row.custId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="STATUS_TAG[row.apprStatus] || 'info'" size="small">
            {{ APPR_STATUS[row.apprStatus] || row.apprStatus || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="money" label="金额" width="110" align="right" :formatter="dash" />
      <el-table-column prop="years" label="存续时间(月)" width="110" align="right" :formatter="dash" />
      <el-table-column prop="businCate" label="业务类别" width="120" show-overflow-tooltip :formatter="dash" />
      <el-table-column prop="businType" label="业务类型" width="120" show-overflow-tooltip :formatter="dash" />
      <el-table-column prop="executeRate" label="执行利率" width="100" align="right" :formatter="dash" />
      <el-table-column prop="slidScale" label="浮动利率(BP)" width="120" align="right" :formatter="dash" />
      <el-table-column prop="localRate" label="当地同业利率" width="120" align="right" :formatter="dash" />
      <el-table-column prop="promiseAmount" width="150" align="right" :formatter="dash">
        <template #header>承诺存款新增金额<br />(年日均:万元)</template>
      </el-table-column>
      <el-table-column prop="promiseTime" width="160" align="right" :formatter="dash">
        <template #header>承诺完成时间<br />(1~12选择 单位:月)</template>
      </el-table-column>
      <el-table-column label="对公/零售" width="100">
        <template #default="{ row }">{{ OR_RETAIL[row.businOrRetail] || row.businOrRetail || '-' }}</template>
      </el-table-column>
      <el-table-column prop="currency" label="币种" width="90" :formatter="dash" />
      <el-table-column label="申请资料" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openMaterial(row)">查看</el-button>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="downloadRow(row)">下载</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        background layout="total, prev, pager, next, sizes"
        :total="total" :current-page="page.pageNo" :page-size="page.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="onPage" @size-change="onSize" />
    </div>

    <!-- 申请资料子页面 -->
    <el-dialog v-model="material.show" title="申请资料" width="640px" append-to-body>
      <el-descriptions :column="1" border label-width="150px">
        <el-descriptions-item label="客户背景资料">{{ material.row.custInfo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="原因必要性说明">{{ material.row.necessExplain || '-' }}</el-descriptions-item>
        <el-descriptions-item label="附件">
          <template v-if="attachments(material.row.files).length">
            <a
              v-for="(f, i) in attachments(material.row.files)" :key="i"
              class="att-link" @click="downloadAttachment(f)">{{ f.name }}</a>
          </template>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listPriceApprovals } from '@/api/report';

const APPR_STATUS = { '0': '待审批', '1': '已通过', '2': '未通过' };
const STATUS_TAG = { '0': 'warning', '1': 'success', '2': 'danger' };
// 对公/零售：数字 1=对公、2=零售（兼容历史 CORP/RETAIL 字面值）
const OR_RETAIL = { '1': '对公', '2': '零售', CORP: '对公', RETAIL: '零售' };

// 空值统一显示 “-”
const dash = (row, col, v) => (v === null || v === undefined || v === '' ? '-' : v);

// ── 申请资料弹窗 ──────────────────────────────────────────────
const material = reactive({ show: false, row: {} });
function openMaterial(row) {
  material.row = row || {};
  material.show = true;
}

// FILES 字段为不定格式字符串：按 , ; | 及换行拆成多个附件引用；name 取末段路径/文件名
function attachments(files) {
  if (!files || typeof files !== 'string') return [];
  return files
    .split(/[,;|\n\r]+/)
    .map(s => s.trim())
    .filter(Boolean)
    .map(ref => ({ ref, name: ref.split(/[\\/]/).pop() || ref }));
}

// 附件下载：构造临时 <a download> 触发；ref 为 URL/路径原样作为 href（无专用下载端点）
function triggerDownload(ref, name) {
  const a = document.createElement('a');
  a.href = ref;
  a.download = name || '';
  a.target = '_blank';
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  a.remove();
}
function downloadAttachment(f) {
  if (!f?.ref) { ElMessage.warning('附件地址为空'); return; }
  triggerDownload(f.ref, f.name);
}
// 操作-下载：下载该行所有附件，无附件时提示
function downloadRow(row) {
  const list = attachments(row?.files);
  if (!list.length) { ElMessage.warning('该记录无附件'); return; }
  list.forEach(f => triggerDownload(f.ref, f.name));
}

const q = reactive({ custName: '', applyFullname: '', apprStatus: '' });
const dateRange = ref([]);
const page = reactive({ pageNo: 1, pageSize: 20 });
const rows = ref([]);
const total = ref(0);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    const params = {
      pageNo: page.pageNo, pageSize: page.pageSize,
      custName: q.custName || undefined,
      applyFullname: q.applyFullname || undefined,
      apprStatus: q.apprStatus || undefined,
      applyTimeStart: dateRange.value?.[0] ? dateRange.value[0] + ' 00:00:00' : undefined,
      applyTimeEnd: dateRange.value?.[1] ? dateRange.value[1] + ' 23:59:59' : undefined,
    };
    const r = await listPriceApprovals(params);
    rows.value = r?.records || [];
    total.value = r?.total || 0;
  } finally {
    loading.value = false;
  }
}
function onSearch() { page.pageNo = 1; load(); }
function onReset() {
  q.custName = ''; q.applyFullname = ''; q.apprStatus = '';
  dateRange.value = []; page.pageNo = 1; load();
}
function onPage(p) { page.pageNo = p; load(); }
function onSize(s) { page.pageSize = s; page.pageNo = 1; load(); }

onMounted(load);
</script>

<style lang="scss" scoped>
.price-approval { padding: 4px 2px; }
.page-h { margin-bottom: 12px;
  h1 { font-size: 18px; margin: 0; display: inline-block; }
  .desc { font-size: 12px; color: #909399; margin-left: 12px; }
}
.filter-form { margin-bottom: 8px; }
.main { color: #303133; }
.sub { color: #909399; font-size: 12px; }
.link-cell { cursor: pointer; display: block; }
.link-cell .main { color: #409eff; }
.link-cell:hover .main { text-decoration: underline; }
.att-link { color: #409eff; cursor: pointer; display: inline-block; margin-right: 14px; }
.att-link:hover { text-decoration: underline; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
