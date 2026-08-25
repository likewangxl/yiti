<template>
  <section class="page bp-crud" v-bp-overflow-tooltip>
    <header class="page-head"><PageTitle /><span>本人认领客户，按未触达/已触达分类</span></header>
    <el-tabs v-model="tab"><el-tab-pane label="未触达" name="untouched" /><el-tab-pane label="已触达" name="touched" /></el-tabs>
    <el-table :data="filteredRows" v-loading="loading" border stripe>
      <el-table-column prop="custNo" label="客户编号" min-width="145" />
      <el-table-column prop="custName" label="客户名称" min-width="170" show-overflow-tooltip />
      <el-table-column prop="unifiedCreditCode" label="统一社会信用代码" min-width="180" />
      <el-table-column prop="contactPerson" label="联系人" width="100" />
      <el-table-column prop="contactMobile" label="联系电话" width="130" />
      <el-table-column prop="claimTime" label="认领时间" width="170"><template #default="{ row }">{{ fmt(row.claimTime) }}</template></el-table-column>
      <el-table-column label="最近触达" width="120"><template #default="{ row }"><el-tag v-if="row.latestTaskStatus" :type="taskTagType(row.latestTaskStatus)">{{ taskStatusLabel(row.latestTaskStatus) }}</el-tag><span v-else>-</span></template></el-table-column>
      <el-table-column label="操作" width="170" fixed="right" class-name="operation-cell">
        <template #default="{ row }">
          <el-button v-if="!row.latestTaskId" link type="primary" @click="openStart(row, false)">发起触达</el-button>
          <el-button v-else link type="primary" @click="viewTask(row)">任务详情</el-button>
          <el-button v-if="['SUCCESS','CANCELLED'].includes(row.latestTaskStatus)" link type="warning" @click="openStart(row, true)">再次触达</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager"><el-pagination background layout="total, sizes, prev, pager, next" :total="total"
      v-model:current-page="pageNo" v-model:page-size="pageSize" :page-sizes="[10,20,50]" @change="load" /></div>

    <el-dialog v-model="startDlg.show" :title="startDlg.followUp ? '再次发起触达' : '发起首次触达'" width="480px">
      <el-form label-width="100px">
        <el-form-item label="客户名称">{{ startDlg.row?.custName }}</el-form-item>
        <el-form-item label="计划完成时间"><el-date-picker v-model="startDlg.planFinishTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item>
        <el-form-item v-if="startDlg.followUp" label="再次触达原因" required><el-input v-model="startDlg.reason" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="startDlg.show=false">取消</el-button><el-button type="primary" :loading="startDlg.saving" @click="submitStart">确认发起</el-button></template>
    </el-dialog>
    <TouchTaskDetailDialog v-model="detail.show" :task-id="detail.taskId" :allow-write="true" @changed="load" />
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import TouchTaskDetailDialog from '@/components/TouchTaskDetailDialog.vue';
import { listClaimedCustomers, startFirstTouch, startFollowUpTouch } from '@/api/customerMarketing';
import { taskStatusLabel, taskTagType } from '@/utils/touchViewModel';

const tab=ref('untouched'), rows=ref([]), total=ref(0), loading=ref(false), pageNo=ref(1), pageSize=ref(20);
const detail=reactive({show:false,taskId:''});
const startDlg=reactive({show:false,row:null,followUp:false,planFinishTime:'',reason:'',saving:false});
const filteredRows=computed(()=>rows.value.filter(r=>tab.value==='untouched' ? !r.latestTaskId : !!r.latestTaskId));
const fmt=v=>v?String(v).replace('T',' ').slice(0,19):'-';
async function load(){loading.value=true;try{const r=await listClaimedCustomers({pageNo:pageNo.value,pageSize:pageSize.value});rows.value=r?.records||[];total.value=r?.total||0;}finally{loading.value=false;}}
function openStart(row,followUp){Object.assign(startDlg,{show:true,row,followUp,planFinishTime:'',reason:'',saving:false});}
async function submitStart(){if(startDlg.followUp&&!startDlg.reason.trim())return ElMessage.warning('请填写再次触达原因');startDlg.saving=true;try{const data={planFinishTime:startDlg.planFinishTime||undefined};const task=startDlg.followUp?await startFollowUpTouch(startDlg.row.claimId,{...data,reason:startDlg.reason.trim()}):await startFirstTouch(startDlg.row.claimId,data);ElMessage.success('触达任务已发起');startDlg.show=false;await load();if(task?.id){detail.taskId=task.id;detail.show=true;}}finally{startDlg.saving=false;}}
function viewTask(row){detail.taskId=row.latestTaskId;detail.show=true;}
load();
</script>

<style scoped lang="scss">
.page-head{display:flex;align-items:baseline;gap:12px;margin-bottom:8px;h1{font-size:18px;margin:0}span{font-size:12px;color:#909399}}.pager{display:flex;justify-content:flex-end;margin-top:14px}
</style>
