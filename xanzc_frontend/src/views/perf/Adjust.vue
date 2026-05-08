<template>
  <div>
    <div class="page-h">
      <h1>业绩调整</h1>
      <span class="desc">比例之和 = 100% · 单行 ≥ 1% · 同一维度不重复</span>
      <div class="actions"><el-button type="primary">+ 新建调整申请</el-button></div>
    </div>

    <el-alert type="info" :closable="false" title="线下调整后将触发 KPI 历史回算。本表演示当前在途的调整申请。" style="margin-bottom:12px" />

    <div class="card-section adjust-form">
      <div class="card-h">
        <div class="title">调整申请 · 大客户存款分配</div>
        <el-tag class="tag-warning" effect="plain">编辑中</el-tag>
      </div>

      <el-form :inline="false" label-position="top" size="default">
        <el-row :gutter="16">
          <el-col :span="6"><el-form-item label="客户"><el-input v-model="form.cust" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="指标"><el-input v-model="form.metric" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="调整周期"><el-input v-model="form.cycle" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="总金额"><el-input v-model="form.amount"><template #append>万</template></el-input></el-form-item></el-col>
        </el-row>
      </el-form>

      <el-table :data="form.allocs" size="default" style="margin-top:8px">
        <el-table-column label="员工" width="140">
          <template #default="{row}">
            <el-select v-model="row.emp" size="small" style="width:120px">
              <el-option value="员工张三" label="员工张三" />
              <el-option value="员工李四" label="员工李四" />
              <el-option value="员工孙七" label="员工孙七" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="org" label="机构" width="120" />
        <el-table-column label="承担比例 %" width="140">
          <template #default="{row}"><el-input-number v-model="row.pct" :min="0" :max="100" size="small" /></template>
        </el-table-column>
        <el-table-column label="分配金额" width="120" align="right">
          <template #default="{row}">{{ Math.round(form.amount * row.pct / 100) }} 万</template>
        </el-table-column>
        <el-table-column label="说明" min-width="200">
          <template #default="{row}"><el-input v-model="row.remark" size="small" /></template>
        </el-table-column>
        <el-table-column label="操作" width="80"><template #default><el-button link type="danger" size="small">删除</el-button></template></el-table-column>
      </el-table>

      <div class="form-foot">
        <el-button>+ 添加分配人</el-button>
        <span class="total" :class="{ ok: total === 100 }">合计：{{ total }}% {{ total === 100 ? '✓' : '' }}</span>
      </div>

      <div class="form-acts">
        <el-button>保存草稿</el-button>
        <el-button type="primary">提交审批</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, computed } from 'vue';
const form = reactive({
  cust: '客户N（重点客户）', metric: '存款日均增量', cycle: '2026Q2', amount: 3200,
  allocs: [
    { emp: '员工张三', org: '南山支行', pct: 60, remark: '主要客户经理' },
    { emp: '员工李四', org: '福田支行', pct: 30, remark: '协同营销' },
    { emp: '员工孙七', org: '罗湖支行', pct: 10, remark: '关系维护' }
  ]
});
const total = computed(() => form.allocs.reduce((s, r) => s + (Number(r.pct) || 0), 0));
</script>

<style lang="scss" scoped>
.adjust-form { padding: 16px 20px; }
.card-h { display: flex; align-items: center; gap: 12px; padding: 0 0 12px; border-bottom: 1px solid $border-1; margin-bottom: 14px;
  .title { font-size: 15px; font-weight: 600; }
}
.form-foot { margin-top: 12px; display: flex; align-items: center;
  .total { margin-left: auto; color: $danger; font-weight: 500; &.ok { color: $success; } }
}
.form-acts { display: flex; gap: 8px; justify-content: flex-end; margin-top: 16px; }
</style>
