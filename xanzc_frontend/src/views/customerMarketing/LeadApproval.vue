<template>
  <section class="page bp-crud" v-bp-overflow-tooltip>
    <header class="page-head">
      <div><PageTitle /><span>待审批与审批记录独立于线索录入，审批动作统一走工作流中心。</span></div>
      <el-button v-if="tab === 'HISTORY'" :loading="exporting" @click="download">导出审批记录</el-button>
    </header>

    <el-tabs v-model="tab" @tab-change="changeTab"><el-tab-pane label="待审批" name="PENDING" /><el-tab-pane label="审批记录" name="HISTORY" /></el-tabs>
    <el-card shadow="never" class="filter-card">
      <el-form inline @submit.prevent>
        <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="线索编号 / 客户名称 / 发起人" @keyup.enter="search" /></el-form-item>
        <el-form-item v-if="tab === 'HISTORY'" label="审批结果"><el-select v-model="query.result" clearable placeholder="全部" style="width:140px"><el-option label="已通过" value="APPROVED" /><el-option label="已驳回" value="REJECTED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-table :data="visibleRows" v-loading="loading" border stripe>
      <el-table-column label="线索编号" min-width="190"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">{{ row.bizId || leadId(row) }}</el-button></template></el-table-column>
      <el-table-column prop="title" label="流程标题" min-width="190" show-overflow-tooltip />
      <el-table-column label="发起人" min-width="145"><template #default="{ row }">{{ row.startUserName || row.startUserEmpNo || row.startUser || '-' }}<span v-if="row.startUserEmpNo && row.startUserName">（{{ row.startUserEmpNo }}）</span></template></el-table-column>
      <el-table-column label="发起机构" min-width="150"><template #default="{ row }">{{ row.startOrgName || row.startOrgId || '-' }}</template></el-table-column>
      <el-table-column prop="taskName" label="审批节点" min-width="145" />
      <el-table-column label="到达节点时间" min-width="165"><template #default="{ row }">{{ formatTime(row.taskCreateTime || row.startTime) }}</template></el-table-column>
      <el-table-column v-if="tab === 'PENDING'" label="SLA" width="85"><template #default="{ row }"><el-tag :type="slaType(row.slaStatus)">{{ slaLabel(row.slaStatus) }}</el-tag></template></el-table-column>
      <el-table-column v-else label="审批结果" width="100"><template #default="{ row }"><el-tag :type="resultType(row.approvalResult || row.processStatus)">{{ resultLabel(row.approvalResult || row.processStatus) }}</el-tag></template></el-table-column>
      <el-table-column v-if="tab === 'HISTORY'" prop="opinion" label="审批意见" min-width="170" show-overflow-tooltip />
      <el-table-column v-if="tab === 'HISTORY'" label="办理时间" min-width="165"><template #default="{ row }">{{ formatTime(row.completeTime) }}</template></el-table-column>
      <el-table-column label="操作" :width="tab === 'PENDING' ? 180 : 90" fixed="right" class-name="operation-cell"><template #default="{ row }">
        <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        <template v-if="tab === 'PENDING'">
          <el-button link type="success" @click="approve(row)">通过</el-button>
          <el-button link type="danger" @click="reject(row)">驳回</el-button>
        </template>
      </template></el-table-column>
    </el-table>
    <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="total" v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" :page-sizes="[10,20,50,100]" @change="load" /></div>

    <LeadDetailDrawer v-model="detailVisible" :lead="selected" :loading="detailLoading" />
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import LeadDetailDrawer from '@/components/LeadDetailDrawer.vue';
import { exportLeadApprovals, getLeadApproval, listLeadApprovals } from '@/api/customerMarketing';
import { approveTask, rejectTask } from '@/api/workflow';

const tab=ref('PENDING'), query=reactive({keyword:'',result:'',pageNo:1,pageSize:20});
const rows=ref([]),total=ref(0),loading=ref(false),exporting=ref(false);
const detailVisible=ref(false),detailLoading=ref(false),selected=ref(null);
const visibleRows=computed(()=>query.result?rows.value.filter(row=>resultLabel(row.approvalResult||row.processStatus)===(query.result==='APPROVED'?'已通过':'已驳回')):rows.value);
const leadId=row=>row.bizId||String(row.businessKey||'').replace(/^LEAD:/,'');
const formatTime=value=>value?String(value).replace('T',' ').slice(0,19):'-';
const slaLabel=value=>({GREEN:'正常',BLUE:'正常',YELLOW:'临期',RED:'超时'}[value]||value||'-');
const slaType=value=>({GREEN:'success',BLUE:'',YELLOW:'warning',RED:'danger'}[value]||'info');
const resultLabel=value=>({APPROVE:'已通过',APPROVED:'已通过',COMPLETED:'已通过',REJECT:'已驳回',REJECTED:'已驳回',CANCELLED:'已驳回',RUNNING:'审批中'}[value]||value||'-');
const resultType=value=>({APPROVE:'success',APPROVED:'success',COMPLETED:'success',REJECT:'danger',REJECTED:'danger',CANCELLED:'danger',RUNNING:'warning'}[value]||'info');

async function load(){loading.value=true;try{const result=await listLeadApprovals({tab:tab.value,keyword:query.keyword||undefined,pageNo:query.pageNo,pageSize:query.pageSize});rows.value=result?.records||[];total.value=result?.total||0;}finally{loading.value=false;}}
function search(){query.pageNo=1;load();}function reset(){query.keyword='';query.result='';search();}function changeTab(){query.pageNo=1;query.result='';load();}
async function openDetail(row){detailVisible.value=true;detailLoading.value=true;selected.value=null;try{selected.value=await getLeadApproval(leadId(row));}finally{detailLoading.value=false;}}
async function approve(row){try{const {value}=await ElMessageBox.prompt('可填写审批意见','通过线索审批',{inputValue:'同意',confirmButtonText:'确认通过',cancelButtonText:'取消'});await approveTask(row.taskId,value||'同意');ElMessage.success('线索审批已通过');load();}catch(error){if(error!=='cancel'&&error!=='close'&&error?.message)throw error;}}
async function reject(row){try{const {value}=await ElMessageBox.prompt('请输入驳回原因','驳回线索审批',{inputValidator:value=>!!value?.trim()||'驳回原因不能为空',confirmButtonText:'确认驳回',cancelButtonText:'取消'});await rejectTask(row.taskId,value.trim());ElMessage.success('线索审批已驳回');load();}catch(error){if(error!=='cancel'&&error!=='close'&&error?.message)throw error;}}
async function download(){exporting.value=true;try{const blob=await exportLeadApprovals({keyword:query.keyword||undefined,result:query.result||undefined});const url=URL.createObjectURL(blob);const anchor=document.createElement('a');anchor.href=url;anchor.download=`线索审批记录_${Date.now()}.xlsx`;anchor.click();URL.revokeObjectURL(url);ElMessage.success('审批记录导出已开始');}finally{exporting.value=false;}}
load();
</script>

<style scoped lang="scss">
.page-head{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:4px}.page-head h1{margin:0;font-size:18px}.page-head span{display:block;margin-top:4px;color:#909399;font-size:12px}.filter-card{margin-bottom:14px}.filter-card :deep(.el-card__body){padding-bottom:2px}.pager{display:flex;justify-content:flex-end;margin-top:14px}@media(max-width:650px){.page-head{gap:12px}}
</style>
