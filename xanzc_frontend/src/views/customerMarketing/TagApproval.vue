<template>
  <section class="page bp-crud" v-bp-overflow-tooltip>
    <header class="page-head"><PageTitle /><span>审核客户标签管理中提交的标签，通过后才可打标。</span></header>
    <el-tabs v-model="tab" @tab-change="load"><el-tab-pane label="待审核" name="PENDING" /><el-tab-pane label="审核记录" name="HISTORY" /></el-tabs>
    <el-form inline @submit.prevent><el-form-item label="关键词"><el-input v-model="keyword" clearable placeholder="标签名称 / 提交人" /></el-form-item><el-form-item><el-button type="primary" @click="load">查询</el-button><el-button @click="keyword='';load()">重置</el-button></el-form-item></el-form>
    <el-table :data="visibleRows" v-loading="loading" border stripe>
      <el-table-column prop="tagName" label="标签名称" min-width="160" />
      <el-table-column label="类型" width="100"><template #default="{ row }">{{ row.tagType==='CERTIFICATION'?'认定类':'项目类' }}</template></el-table-column>
      <el-table-column prop="description" label="标签说明" min-width="230" show-overflow-tooltip />
      <el-table-column prop="tagPriority" label="优先级" width="80" />
      <el-table-column label="提交人" min-width="150"><template #default="{ row }">{{ personLabel(row) }}</template></el-table-column>
      <el-table-column label="提交时间" min-width="165"><template #default="{ row }">{{ formatTime(row.createdTime) }}</template></el-table-column>
      <el-table-column label="审核状态" width="105"><template #default="{ row }"><el-tag :type="statusType(row.approvalStatus)">{{ statusLabel(row.approvalStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="190" fixed="right" class-name="operation-cell"><template #default="{ row }">
        <template v-if="row.approvalStatus==='PENDING'"><el-button link type="success" @click="approve(row)">通过</el-button><el-button link type="danger" @click="reject(row)">退回</el-button></template>
        <el-button v-if="row.approvalStatus!=='PENDING'" link type="primary" @click="selected=row;detailVisible=true">详情</el-button>
      </template></el-table-column>
    </el-table>
    <el-drawer v-model="detailVisible" title="客户标签审核详情" size="560px">
      <el-alert v-if="selected?.approvalStatus==='REJECTED'" :title="`退回原因：${selected.rejectReason||'-'}`" type="error" :closable="false" show-icon />
      <el-descriptions v-if="selected" :column="1" border class="detail">
        <el-descriptions-item label="标签名称">{{ selected.tagName }}</el-descriptions-item>
        <el-descriptions-item label="标签说明">{{ selected.description||'-' }}</el-descriptions-item><el-descriptions-item label="优先级">{{ selected.tagPriority }}</el-descriptions-item>
        <el-descriptions-item label="失效日期">{{ selected.expiresAt||'长期有效' }}</el-descriptions-item><el-descriptions-item label="提交信息">{{ personLabel(selected) }} · {{ formatTime(selected.createdTime) }}</el-descriptions-item>
        <el-descriptions-item label="审核信息">{{ reviewerLabel(selected) }} · {{ formatTime(selected.reviewedTime) }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { approveTag, listTags, rejectTag } from '@/api/customerMarketing';
const tab=ref('PENDING'), keyword=ref(''), rows=ref([]), loading=ref(false), detailVisible=ref(false), selected=ref(null);
const visibleRows=computed(()=>rows.value.filter(r=>{const statusOk=tab.value==='PENDING'?r.approvalStatus==='PENDING':['APPROVED','REJECTED'].includes(r.approvalStatus);const k=keyword.value.trim();return statusOk&&(!k||`${r.tagName}${r.createdByName||''}${r.createdBy||''}${r.description||''}`.includes(k));}));
const statusLabel=v=>({PENDING:'待审核',APPROVED:'已通过',REJECTED:'已退回'}[v]||v);const statusType=v=>({PENDING:'warning',APPROVED:'success',REJECTED:'danger'}[v]||'info');
const personLabel=row=>row?.createdByName?`${row.createdByName}(${row.createdBy||'-'})`:(row?.createdBy||'-');
const reviewerLabel=row=>row?.reviewedByName?`${row.reviewedByName}(${row.reviewedBy||'-'})`:(row?.reviewedBy||'-');
const formatTime=value=>value?String(value).replace('T',' ').slice(0,19):'-';
async function load(){loading.value=true;try{const r=await listTags({approvalStatus:tab.value,pageNo:1,pageSize:100});rows.value=r?.records||[];}finally{loading.value=false;}}
async function approve(row){await ElMessageBox.confirm(`确认通过标签“${row.tagName}”？`,'标签审核',{type:'warning'});await approveTag(row.id);ElMessage.success('审核通过，标签已启用');load();}
async function reject(row){try{const {value}=await ElMessageBox.prompt('请输入明确的退回原因','退回标签',{inputValidator:v=>!!v?.trim()||'退回原因不能为空'});await rejectTag(row.id,value);ElMessage.success('标签已退回');load();}catch(e){if(e!=='cancel'&&e!=='close'&&e?.message)throw e;}}
load();
</script>

<style scoped lang="scss">.page-head{display:flex;align-items:baseline;gap:12px;margin-bottom:10px}.page-head h1{font-size:18px;margin:0}.page-head span{font-size:12px;color:#909399}.detail{margin-top:14px}</style>
