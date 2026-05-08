<template>
  <div>
    <div class="page-h">
      <h1>指标库</h1>
      <span class="desc">三级层级树 · SQL/Groovy 计算配置 · 试运行 · 版本</span>
      <div class="actions">
        <el-button>📥 导入指标</el-button>
        <el-button type="primary">+ 新增指标</el-button>
      </div>
    </div>

    <div class="metrics-layout">
      <!-- 左：指标层级 -->
      <div class="card-section tree-col">
        <div class="section-title">指标层级</div>
        <el-tree
          :data="tree"
          node-key="id"
          :default-expanded-keys="['biz','deposit']"
          :expand-on-click-node="false"
          @node-click="onPick"
          highlight-current
          :current-node-key="picked"
        >
          <template #default="{ node, data }">
            <span>{{ data.children ? '📁' : '📊' }} {{ node.label }}</span>
          </template>
        </el-tree>
      </div>

      <!-- 右：指标详情 -->
      <div class="card-section detail-col">
        <div class="card-h">
          <div class="title">指标详情 · {{ detail.name }}</div>
          <el-tag class="tag-success" effect="plain">{{ detail.status }}</el-tag>
          <span class="ver">{{ detail.version }}</span>
        </div>
        <el-descriptions :column="2" border size="default" class="meta">
          <el-descriptions-item label="编码">{{ detail.code }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ detail.cate }}</el-descriptions-item>
          <el-descriptions-item label="计算方式"><el-tag class="tag-info" effect="plain">{{ detail.type }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="数据源">{{ detail.dataSource }}</el-descriptions-item>
        </el-descriptions>

        <div class="block-h">SQL 表达式</div>
        <pre class="sql">{{ detail.sql }}</pre>

        <div class="block-h">槽位 (Slot) 声明</div>
        <el-table :data="detail.slots" size="small">
          <el-table-column prop="name" label="槽位名" width="180" />
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column label="是否必填" width="120">
            <template #default="{row}">{{ row.required ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column prop="desc" label="说明" />
        </el-table>

        <div class="acts">
          <el-button type="primary">编辑</el-button>
          <el-button>▶ 试运行</el-button>
          <el-button>查看版本历史</el-button>
          <el-button>停用</el-button>
        </div>

        <div class="block-h">同分类指标</div>
        <el-table :data="detail.same" size="small">
          <el-table-column prop="code" label="编码" width="100" />
          <el-table-column prop="name" label="名称" />
          <el-table-column label="计算" width="80"><template #default="{row}"><el-tag class="tag-info" effect="plain">{{ row.type }}</el-tag></template></el-table-column>
          <el-table-column label="状态" width="100"><template #default="{row}"><el-tag class="tag-success" effect="plain">{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column prop="updated" label="更新" width="120" />
          <el-table-column label="操作" width="80"><template #default><el-button link type="primary" size="small">编辑</el-button></template></el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { perfMetricsTree, perfMetricDetail } from '@/mock';
import { getMetricsTree, getMetricDetail } from '@/api/perf';
const tree = ref(perfMetricsTree);
const picked = ref('M0002');
const detail = ref(perfMetricDetail.M0002);
async function onPick(node) {
  if (!node.id) return;
  picked.value = node.id;
  try { const r = await getMetricDetail(node.id); if (r) detail.value = r; } catch {}
}
onMounted(async () => {
  try { const r = await getMetricsTree(); if (r) tree.value = r; } catch {}
  try { const r = await getMetricDetail('M0002'); if (r) detail.value = r; } catch {}
});
</script>

<style lang="scss" scoped>
.metrics-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 12px;
}
.tree-col, .detail-col { padding: 16px 20px; }
.card-h { display: flex; align-items: center; gap: 10px; padding: 0 0 12px 0; border-bottom: 1px solid $border-1; margin-bottom: 14px;
  .title { font-size: 15px; font-weight: 600; }
  .ver { color: $text-3; font-size: 12px; margin-left: auto; }
}
.meta { margin-bottom: 18px; }
.block-h { font-size: 13px; font-weight: 600; margin: 18px 0 10px; color: $text-1; }
.sql {
  background: #1e293b; color: #f1f5f9; border-radius: 4px; padding: 12px 14px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12.5px;
  line-height: 1.6; white-space: pre; overflow: auto;
}
.acts { margin: 16px 0; display: flex; gap: 8px; }
</style>
