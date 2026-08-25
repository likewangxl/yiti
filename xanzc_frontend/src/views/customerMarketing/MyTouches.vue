<template>
  <section class="page bp-crud" v-bp-overflow-tooltip>
    <header class="page-head"><PageTitle /><span>仅展示当前登录员工本人负责的触达任务</span></header>
    <el-form inline @submit.prevent>
      <el-form-item label="任务编号"><el-input v-model="q.keyword" clearable @keyup.enter="search" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="q.status" clearable placeholder="全部" style="width:140px"><el-option v-for="s in statuses" :key="s.value" :label="s.label" :value="s.value" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
    </el-form>
    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="taskNo" label="任务编号" min-width="190" />
      <el-table-column prop="custId" label="客户ID" min-width="180" show-overflow-tooltip />
      <el-table-column label="任务类型" width="110"><template #default="{row}">{{ row.taskType==='FIRST_TOUCH'?'首次触达':'再次触达' }}</template></el-table-column>
      <el-table-column label="状态" width="105"><template #default="{row}"><el-tag :type="taskTagType(row.taskStatus)">{{taskStatusLabel(row.taskStatus)}}</el-tag></template></el-table-column>
      <el-table-column label="SLA" width="90"><template #default="{row}"><el-tag :type="slaTagType(row.slaStatus)">{{slaLabel(row.slaStatus)}}</el-tag></template></el-table-column>
      <el-table-column prop="planFinishTime" label="计划完成" width="170"><template #default="{row}">{{fmt(row.planFinishTime)}}</template></el-table-column>
      <el-table-column prop="createdTime" label="创建时间" width="170"><template #default="{row}">{{fmt(row.createdTime)}}</template></el-table-column>
      <el-table-column label="操作" width="100" fixed="right" class-name="operation-cell"><template #default="{row}"><el-button link type="primary" @click="open(row)">{{['PENDING','IN_PROGRESS'].includes(row.taskStatus)?'办理':'详情'}}</el-button></template></el-table-column>
    </el-table>
    <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="total" v-model:current-page="pageNo" v-model:page-size="pageSize" :page-sizes="[10,20,50]" @change="load" /></div>
    <TouchTaskDetailDialog v-model="detail.show" :task-id="detail.taskId" :allow-write="true" @changed="load" />
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue';
import TouchTaskDetailDialog from '@/components/TouchTaskDetailDialog.vue';
import { listMyTouchTasks } from '@/api/customerMarketing';
import { slaLabel,slaTagType,taskStatusLabel,taskTagType } from '@/utils/touchViewModel';
const statuses=[{label:'待办理',value:'PENDING'},{label:'办理中',value:'IN_PROGRESS'},{label:'已完成',value:'SUCCESS'},{label:'已取消',value:'CANCELLED'}];
const q=reactive({keyword:'',status:''}),rows=ref([]),total=ref(0),loading=ref(false),pageNo=ref(1),pageSize=ref(20),detail=reactive({show:false,taskId:''});
const fmt=v=>v?String(v).replace('T',' ').slice(0,19):'-';
async function load(){loading.value=true;try{const r=await listMyTouchTasks({keyword:q.keyword||undefined,status:q.status||undefined,pageNo:pageNo.value,pageSize:pageSize.value});rows.value=r?.records||[];total.value=r?.total||0;}finally{loading.value=false;}}
function search(){pageNo.value=1;load()}function reset(){q.keyword='';q.status='';search()}function open(row){detail.taskId=row.id;detail.show=true}load();
</script>

<style scoped lang="scss">.page-head{display:flex;align-items:baseline;gap:12px;margin-bottom:14px;h1{font-size:18px;margin:0}span{font-size:12px;color:#909399}}.pager{display:flex;justify-content:flex-end;margin-top:14px}</style>
