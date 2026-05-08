<template>
  <div>
    <div class="page-h">
      <h1>通知中心</h1>
      <span class="desc">WORKFLOW / SYSTEM / BUSINESS</span>
      <div class="actions"><el-button>全部标记为已读</el-button></div>
    </div>

    <div class="card-section">
      <el-form :inline="true" size="default">
        <el-form-item label="通知类型"><el-select v-model="f.type" style="width:160px"><el-option value="" label="全部" /></el-select></el-form-item>
        <el-form-item label="已读状态"><el-select v-model="f.read" style="width:120px"><el-option value="" label="全部" /></el-select></el-form-item>
        <el-form-item label="关键字"><el-input v-model="f.kw" style="width:200px" placeholder="标题或摘要" /></el-form-item>
        <el-form-item label="时间范围"><el-date-picker v-model="f.t" type="daterange" style="width:280px" /></el-form-item>
      </el-form>
    </div>

    <div class="card-section">
      <el-tabs v-model="tab">
        <el-tab-pane :label="`全部 ${total}`" name="all" />
        <el-tab-pane :label="`未读 ${unreadCount}`" name="unread" />
        <el-tab-pane :label="`已读 ${total - unreadCount}`" name="read" />
      </el-tabs>
      <el-table :data="filteredRows" size="default" empty-text="暂无通知">
        <el-table-column label="标题" min-width="280">
          <template #default="{row}">
            <span v-if="!isRead(row)" class="dot" />
            {{ row.title || '—' }}
            <div v-if="row.summary" style="color:#6B7280;font-size:11px;margin-top:2px">{{ row.summary }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{row}"><el-tag :class="typeCls(row.bizType ?? row.type)" effect="plain">{{ row.bizType ?? row.type ?? '-' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="关联业务ID" width="200" show-overflow-tooltip>
          <template #default="{row}">{{ row.bizId ?? row.no ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="生成时间" width="170">
          <template #default="{row}">{{ row.sentTime ?? row.time ?? '' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :class="isRead(row) ? 'tag-info' : 'tag-warning'" effect="plain">{{ isRead(row) ? '已读' : '未读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default><el-button link type="primary" size="small">跳转</el-button></template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;padding:12px 0">
        <el-pagination layout="prev, pager, next" :total="total" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { sysNotifications } from '@/mock';
import { listNotifications } from '@/api/system';

const rows = ref(sysNotifications);
const tab = ref('all');
const f = reactive({ type: '', read: '', kw: '', t: null });

// mock 用 {read:bool}，后端用 {readStatus:'READ'|'UNREAD'}，统一一个判定函数
const isRead = (row) => row.readStatus ? row.readStatus === 'READ' : !!row.read;
const total = computed(() => rows.value.length);
const unreadCount = computed(() => rows.value.filter(r => !isRead(r)).length);
const filteredRows = computed(() => {
  if (tab.value === 'unread') return rows.value.filter(r => !isRead(r));
  if (tab.value === 'read')   return rows.value.filter(r => isRead(r));
  return rows.value;
});

onMounted(async () => {
  try {
    const r = await listNotifications();
    // 真后端表当前为空，rows 留 mock 数据让 UI 还有内容；rows.length>0 才覆盖
    if (Array.isArray(r) && r.length) rows.value = r;
  } catch {}
});

const typeCls = (t) => ({
  WORKFLOW: 'tag-info', SYSTEM: 'tag-success', BUSINESS: 'tag-warning',
  LEAD: 'tag-info', LOAN: 'tag-warning', CUSTOMER: 'tag-info'
}[t] || 'tag-info');
</script>

<style lang="scss" scoped>
.dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: $danger; margin-right: 8px; vertical-align: middle; }
</style>
