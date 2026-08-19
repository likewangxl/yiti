<template>
  <section class="page bp-crud" v-bp-overflow-tooltip>
    <header class="page-head"><PageTitle /><span>机构管理人员查看触达任务、日志、附件与定位</span></header>
    <el-form inline @submit.prevent>
      <el-form-item label="任务编号"><el-input v-model="q.keyword" clearable @keyup.enter="search" /></el-form-item>
      <el-form-item label="执行人工号"><el-input v-model="q.assigneeEmpId" clearable /></el-form-item>
      <el-form-item label="机构代码"><el-input v-model="q.orgId" clearable /></el-form-item>
      <el-form-item label="状态"><el-select v-model="q.status" clearable placeholder="全部" style="width:140px"><el-option v-for="s in statuses" :key="s.value" :label="s.label" :value="s.value" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button><el-button @click="download">导出</el-button></el-form-item>
    </el-form>
    <div class="cards"><el-card v-for="c in cards" :key="c.label" shadow="never"><span>{{c.label}}</span><strong>{{c.value}}</strong></el-card></div>
    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="taskNo" label="任务编号" min-width="190" />
      <el-table-column prop="custId" label="客户ID" min-width="175" show-overflow-tooltip />
      <el-table-column prop="orgId" label="机构" min-width="135" />
      <el-table-column prop="assigneeEmpId" label="执行人工号" width="120" />
      <el-table-column label="状态" width="105"><template #default="{row}"><el-tag :type="taskTagType(row.taskStatus)">{{taskStatusLabel(row.taskStatus)}}</el-tag></template></el-table-column>
      <el-table-column label="SLA" width="90"><template #default="{row}"><el-tag :type="slaTagType(row.slaStatus)">{{slaLabel(row.slaStatus)}}</el-tag></template></el-table-column>
      <el-table-column label="计划完成" width="170"><template #default="{row}">{{fmt(row.planFinishTime)}}</template></el-table-column>
      <el-table-column label="操作" width="90" fixed="right" class-name="operation-cell"><template #default="{row}"><el-button link type="primary" @click="open(row)">详情</el-button></template></el-table-column>
    </el-table>
    <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="total" v-model:current-page="pageNo" v-model:page-size="pageSize" :page-sizes="[10,20,50,100]" @change="load" /></div>
    <TouchTaskDetailDialog v-model="detail.show" :task-id="detail.taskId" :allow-write="false" />
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import TouchTaskDetailDialog from '@/components/TouchTaskDetailDialog.vue';
import { exportTouchOverview, listTouchOverview } from '@/api/customerMarketing';
import { slaLabel,slaTagType,taskStatusLabel,taskTagType } from '@/utils/touchViewModel';
const statuses=[{label:'待办理',value:'PENDING'},{label:'办理中',value:'IN_PROGRESS'},{label:'已完成',value:'SUCCESS'},{label:'已取消',value:'CANCELLED'}];
const q=reactive({keyword:'',assigneeEmpId:'',orgId:'',status:''}),rows=ref([]),total=ref(0),loading=ref(false),pageNo=ref(1),pageSize=ref(20),detail=reactive({show:false,taskId:''});
const cards=computed(()=>[{label:'当前页任务',value:rows.value.length},{label:'红灯',value:rows.value.filter(x=>x.slaStatus==='RED').length},{label:'黄灯',value:rows.value.filter(x=>x.slaStatus==='YELLOW').length},{label:'已完成',value:rows.value.filter(x=>x.taskStatus==='SUCCESS').length}]);
const params=()=>({keyword:q.keyword||undefined,assigneeEmpId:q.assigneeEmpId||undefined,orgId:q.orgId||undefined,status:q.status||undefined,pageNo:pageNo.value,pageSize:pageSize.value});
const fmt=v=>v?String(v).replace('T',' ').slice(0,19):'-';
async function load(){loading.value=true;try{const r=await listTouchOverview(params());rows.value=r?.records||[];total.value=r?.total||0;}finally{loading.value=false;}}
function search(){pageNo.value=1;load()}function reset(){Object.assign(q,{keyword:'',assigneeEmpId:'',orgId:'',status:''});search()}function open(row){detail.taskId=row.id;detail.show=true}
async function download(){const blob=await exportTouchOverview(params());const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`触达任务_${Date.now()}.csv`;a.click();URL.revokeObjectURL(url);ElMessage.success('导出已开始');}load();
</script>

<style scoped lang="scss">.page-head{display:flex;align-items:baseline;gap:12px;margin-bottom:14px;h1{font-size:18px;margin:0}span{font-size:12px;color:#909399}}.cards{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-bottom:12px}.cards :deep(.el-card__body){display:flex;justify-content:space-between;align-items:center}.cards span{color:#909399}.cards strong{font-size:22px;color:#303133}.pager{display:flex;justify-content:flex-end;margin-top:14px}@media(max-width:800px){.cards{grid-template-columns:1fr 1fr}}</style>
