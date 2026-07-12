<template>
  <div class="dsn-page">
    <div class="page-h">
      <h1>大屏设计器</h1>
      <div class="actions">
        <el-select v-model="curId" placeholder="选择大屏" style="width:220px" @change="loadScreen">
          <el-option v-for="s in screens" :key="s.id" :label="`${s.screenName} (${s.viewLevel})`" :value="s.id" />
        </el-select>
        <el-button @click="openCreateScreen">+ 新建屏</el-button>
        <el-button v-if="model && model.viewLevel === 'PROVINCE'" @click="pointDlg.show = true">地图点位</el-button>
        <el-button v-if="model" @click="previewFull">全屏预览</el-button>
        <el-button v-if="model" type="primary" :loading="saving" @click="onSave">保 存</el-button>
      </div>
    </div>

    <div v-if="model" class="dsn-cols">
      <!-- 左：结构树 -->
      <div class="card-section dsn-tree">
        <div v-for="region in editableRegions" :key="region" class="dsn-region">
          <div class="dsn-region-h">
            <b>{{ REGION_LABELS[region] }}</b>
            <el-button link size="small" @click="addRow(region)">+ 行</el-button>
          </div>
          <div v-for="(row, ri) in rowsOf(region)" :key="ri" class="dsn-row-item">
            <div class="dsn-row-h">
              <span>第 {{ row.rowNo }} 行 · 高</span>
              <el-input-number v-model="row.blocks[0].heightPct" :min="1" :max="100" size="small"
                               style="width:90px" @change="syncRowHeight(row)" />%
              <el-button link size="small" @click="moveRow(region, ri, -1)">↑</el-button>
              <el-button link size="small" @click="moveRow(region, ri, 1)">↓</el-button>
              <el-button link size="small" @click="addBlock(region, row.rowNo)">+块</el-button>
              <el-button link type="danger" size="small" @click="removeRow(region, row.rowNo)">删行</el-button>
            </div>
            <div v-for="b in row.blocks" :key="b._key" class="dsn-block-item"
                 :class="{ on: selected === b }" @click="selected = b">
              <span>{{ TYPE_LABELS[b.componentType] }}</span>
              <span class="dsn-b-title">{{ styleOf(b).title || '未命名' }}</span>
              <span>宽<el-input-number v-model="b.widthPct" :min="1" :max="100" size="small"
                                       style="width:80px" @click.stop />%</span>
              <el-button link type="danger" size="small" @click.stop="removeBlock(b)">✕</el-button>
            </div>
          </div>
        </div>
        <el-alert v-if="model.viewLevel === 'PROVINCE'" type="info" :closable="false"
                  title="省级屏中心区固定为陕西地图，仅左右两侧可配置" />
      </div>

      <!-- 中：实时预览（复用运行时渲染引擎，所见即所得） -->
      <div class="card-section dsn-preview-wrap">
        <div class="dsn-preview screen-root" ref="previewRef">
          <div class="scr-stage" :style="{ transform: `translate(-50%, -50%) scale(${previewScale})` }">
            <div class="scr-header"><span class="scr-title">{{ model.screenName }}</span></div>
            <div class="scr-body">
              <ScreenRenderer :screen="model" :blocks="renderBlocks" :map-points="points"
                              :context="previewCtx" :key="renderKey" />
            </div>
          </div>
        </div>
        <el-form inline size="small" class="dsn-ctx">
          <el-form-item label="预览 orgCode"><el-input v-model="previewCtx.orgCode" style="width:130px" /></el-form-item>
          <el-form-item label="预览 empId"><el-input v-model="previewCtx.empId" style="width:130px" /></el-form-item>
          <el-button size="small" @click="renderKey++">刷新预览</el-button>
        </el-form>
      </div>

      <!-- 右：属性面板 -->
      <div class="card-section dsn-props">
        <template v-if="selected">
          <el-form label-position="top" size="default">
            <el-form-item label="组件类型">
              <el-select v-model="selected.componentType" style="width:100%" @change="ensureTimeseriesBinding(selected)">
                <el-option v-for="(l, t) in TYPE_LABELS" :key="t" :label="l" :value="t" />
              </el-select>
            </el-form-item>
            <el-form-item label="数据源（按组件类型联动过滤）">
              <el-select :model-value="bindOf(selected).dsId" style="width:100%"
                         @update:model-value="v => patchBind(selected, { dsId: v })">
                <el-option v-for="d in dsOptions" :key="d.id"
                           :label="`${d.dsName}【${d.dsType === 'TIMESERIES' ? '时序' : '单值'}】`" :value="d.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="周期">
              <el-select :model-value="bindOf(selected).period || 'LATEST'" style="width:100%"
                         @update:model-value="v => patchBind(selected, { period: v })">
                <el-option v-for="p in ['LATEST','LAST_10D','LAST_1M','LAST_6M_EOM']" :key="p" :label="p" :value="p" />
              </el-select>
            </el-form-item>
            <el-form-item label="数据项（卡片/折线；一行一个：列名|显示名）">
              <el-input :model-value="itemsText(selected)" type="textarea" :rows="3"
                        @update:model-value="v => setItemsText(selected, v)" />
            </el-form-item>
            <el-form-item label="名称列 / 数值列（排名/饼图/流程）">
              <div style="display:flex;gap:6px">
                <el-input :model-value="bindOf(selected).nameCol" placeholder="nameCol"
                          @update:model-value="v => patchBind(selected, { nameCol: v })" />
                <el-input :model-value="bindOf(selected).valueCol" placeholder="valueCol"
                          @update:model-value="v => patchBind(selected, { valueCol: v })" />
              </div>
            </el-form-item>
            <el-form-item label="标题 / 单位 / 小数位 / 刷新秒">
              <div style="display:flex;gap:6px">
                <el-input :model-value="styleOf(selected).title" placeholder="标题"
                          @update:model-value="v => patchStyle(selected, { title: v })" />
                <el-input :model-value="styleOf(selected).unit" placeholder="单位" style="width:90px"
                          @update:model-value="v => patchStyle(selected, { unit: v })" />
                <el-input-number :model-value="styleOf(selected).decimals ?? 2" :min="0" :max="6"
                                 @update:model-value="v => patchStyle(selected, { decimals: v })" />
                <el-input-number :model-value="styleOf(selected).refreshSec ?? 60" :min="0" :max="3600"
                                 @update:model-value="v => patchStyle(selected, { refreshSec: v })" />
              </div>
            </el-form-item>
            <el-divider>钻取与跳转</el-divider>
            <el-form-item>
              <el-switch :model-value="drillOf(selected).drillEnabled" active-text="区块内趋势钻取（仅时序数据源）"
                         @update:model-value="v => patchDrill(selected, { drillEnabled: v })" />
            </el-form-item>
            <el-form-item v-if="drillOf(selected).drillEnabled" label="钻取周期">
              <el-select :model-value="drillOf(selected).drillPeriods || []" multiple style="width:100%"
                         @update:model-value="v => patchDrill(selected, { drillPeriods: v })">
                <el-option v-for="p in ['LAST_10D','LAST_1M','LAST_6M_EOM']" :key="p" :label="p" :value="p" />
              </el-select>
            </el-form-item>
            <el-form-item label="点击跳转目标屏（留空不跳转）">
              <el-select :model-value="drillOf(selected).jump?.targetScreenCode" clearable style="width:100%"
                         @update:model-value="v => patchJump(selected, v)">
                <el-option v-for="s in screens" :key="s.screenCode" :label="s.screenName" :value="s.screenCode" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="drillOf(selected).jump" label='跳转参数映射 JSON（如 {"orgCode":"$col:org_code"}）'>
              <el-input :model-value="JSON.stringify(drillOf(selected).jump?.params || {})" type="textarea" :rows="2"
                        @update:model-value="v => patchJumpParams(selected, v)" />
            </el-form-item>
          </el-form>
        </template>
        <el-empty v-else description="点击左侧区块编辑属性" />
      </div>
    </div>

    <!-- 新建屏 -->
    <el-dialog v-model="createDlg.show" title="新建大屏" width="420px">
      <el-form label-position="top">
        <el-form-item label="名称" required><el-input v-model="createDlg.name" /></el-form-item>
        <el-form-item label="视角" required>
          <el-radio-group v-model="createDlg.viewLevel">
            <el-radio label="PROVINCE">省分行总览</el-radio>
            <el-radio label="BRANCH">支行详情</el-radio>
            <el-radio label="PERSON">个人详情</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDlg.show = false">取消</el-button>
        <el-button type="primary" @click="onCreateScreen">创建</el-button>
      </template>
    </el-dialog>

    <!-- 地图点位管理（PROVINCE） -->
    <el-dialog v-model="pointDlg.show" title="地图支行点位" width="760px">
      <el-button size="small" @click="points.push({ orgCode: '', orgName: '', lng: 108.9, lat: 34.2, targetScreenCode: 'SCR_BRANCH', status: 'ACTIVE' })">+ 点位</el-button>
      <el-table :data="points" size="small" max-height="380">
        <el-table-column label="机构号" width="130">
          <template #default="{ row }"><el-input v-model="row.orgCode" size="small" /></template>
        </el-table-column>
        <el-table-column label="名称" width="150">
          <template #default="{ row }"><el-input v-model="row.orgName" size="small" /></template>
        </el-table-column>
        <el-table-column label="经度" width="120">
          <template #default="{ row }"><el-input-number v-model="row.lng" :precision="6" :controls="false" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="纬度" width="120">
          <template #default="{ row }"><el-input-number v-model="row.lat" :precision="6" :controls="false" size="small" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="目标屏" width="130">
          <template #default="{ row }"><el-input v-model="row.targetScreenCode" size="small" /></template>
        </el-table-column>
        <el-table-column label="" width="60">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="points.splice($index, 1)">✕</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="pointDlg.show = false">关闭</el-button>
        <el-button type="primary" @click="onSavePoints">保存点位</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  listScreens, getScreen, saveScreen, listMapPoints, saveMapPoints, listScreenDatasources
} from '@/api/screen';
import ScreenRenderer from '../components/ScreenRenderer.vue';

const REGION_LABELS = { LEFT: '左侧区域', MAIN: '中心区域', RIGHT: '右侧区域' };
const TYPE_LABELS = {
  METRIC_CARD: '数值指标卡片', LINE_TREND: '折线趋势图', PIE_SHARE: '饼状占比图',
  RANK_LIST: '排名列表', FLOW_STATUS: '流程状态概览'
};

const screens = ref([]);
const datasources = ref([]);
const points = ref([]);
const curId = ref(null);
const model = ref(null);      // { id, screenCode, screenName, viewLevel, status, blocks: [] }
const selected = ref(null);
const saving = ref(false);
const renderKey = ref(0);
const previewScale = ref(0.32);
const previewRef = ref(null);
const previewCtx = reactive({ orgCode: '', empId: '' });
const createDlg = reactive({ show: false, name: '', viewLevel: 'BRANCH' });
const pointDlg = reactive({ show: false });
let blockKeySeq = 1;

// PROVINCE 屏 MAIN 区锁定（需求 3.1：中心固定地图）
const editableRegions = computed(() =>
  model.value?.viewLevel === 'PROVINCE' ? ['LEFT', 'RIGHT'] : ['LEFT', 'MAIN', 'RIGHT']);

// 联动过滤：趋势类组件 / 已开钻取 → 仅时序数据源（需求 4.3.2）
const dsOptions = computed(() => {
  if (!selected.value) return datasources.value;
  const needTs = selected.value.componentType === 'LINE_TREND' || drillOf(selected.value).drillEnabled;
  return needTs ? datasources.value.filter(d => d.dsType === 'TIMESERIES') : datasources.value;
});

const renderBlocks = computed(() => (model.value?.blocks || []).map(b => ({ ...b })));

// ==== JSON 三列的读写助手（区块上保存字符串，编辑面板上操作对象） ====
function parseJ(s, fb = {}) { try { return s ? JSON.parse(s) : fb; } catch { return fb; } }
function bindOf(b) { return parseJ(b.bindJson); }
function styleOf(b) { return parseJ(b.styleJson); }
function drillOf(b) { return parseJ(b.drillJson); }
function patchBind(b, patch) { b.bindJson = JSON.stringify({ ...bindOf(b), ...patch }); renderKey.value++; }
function patchStyle(b, patch) { b.styleJson = JSON.stringify({ ...styleOf(b), ...patch }); renderKey.value++; }
function patchDrill(b, patch) {
  b.drillJson = JSON.stringify({ ...drillOf(b), ...patch });
  renderKey.value++;
  // 钻取开关置为开启时，联动校验数据源是否时序型（需求 4.3.2）
  if (patch.drillEnabled === true) ensureTimeseriesBinding(b);
}
// 组件类型切为 LINE_TREND、或钻取开启时要求时序型数据源；当前绑定的数据源若非时序型则清空并提示
function ensureTimeseriesBinding(b) {
  const needTs = b.componentType === 'LINE_TREND' || drillOf(b).drillEnabled;
  if (!needTs) return;
  const bind = bindOf(b);
  if (!bind.dsId) return;
  const ds = datasources.value.find(d => d.id === bind.dsId);
  if (ds && ds.dsType !== 'TIMESERIES') {
    patchBind(b, { dsId: null });
    ElMessage.warning('已清空数据源：该组件/钻取要求时序型数据源');
  }
}
function patchJump(b, code) {
  const d = drillOf(b);
  if (!code) delete d.jump;
  else d.jump = { targetScreenCode: code, params: d.jump?.params || {} };
  b.drillJson = JSON.stringify(d);
}
function patchJumpParams(b, text) {
  try {
    const d = drillOf(b);
    if (d.jump) { d.jump.params = JSON.parse(text || '{}'); b.drillJson = JSON.stringify(d); }
  } catch { /* 输入中容忍非法 JSON */ }
}
function itemsText(b) { return (bindOf(b).items || []).map(i => `${i.col}|${i.label || i.col}`).join('\n'); }
function setItemsText(b, v) {
  const items = String(v || '').split('\n').map(s => s.trim()).filter(Boolean)
    .map(line => { const [col, label] = line.split('|'); return { col: col.trim(), label: (label || col).trim() }; });
  patchBind(b, { items });
}

// ==== 结构树操作 ====
function rowsOf(region) {
  const list = (model.value?.blocks || []).filter(b => b.region === region);
  const byRow = new Map();
  for (const b of list) {
    if (!byRow.has(b.rowNo)) byRow.set(b.rowNo, []);
    byRow.get(b.rowNo).push(b);
  }
  return [...byRow.entries()].sort((a, b) => a[0] - b[0])
    .map(([rowNo, blocks]) => ({ rowNo, blocks: blocks.sort((a, b) => a.colNo - b.colNo) }));
}
function newBlock(region, rowNo, colNo) {
  return {
    _key: `nb${blockKeySeq++}`, region, rowNo, colNo,
    widthPct: 100, heightPct: 50, componentType: 'METRIC_CARD',
    bindJson: '{}', styleJson: '{"title":"新区块","refreshSec":60}', drillJson: '{}'
  };
}
function addRow(region) {
  const maxRow = Math.max(0, ...rowsOf(region).map(r => r.rowNo));
  model.value.blocks.push(newBlock(region, maxRow + 1, 1));
}
function addBlock(region, rowNo) {
  const row = rowsOf(region).find(r => r.rowNo === rowNo);
  model.value.blocks.push(newBlock(region, rowNo, (row?.blocks.length || 0) + 1));
}
function removeBlock(b) {
  model.value.blocks = model.value.blocks.filter(x => x !== b);
  if (selected.value === b) selected.value = null;
}
function removeRow(region, rowNo) {
  // 若当前选中块属于被删行，先清空选中，避免右侧属性面板悬挂编辑已删除对象（对齐 removeBlock 的清理逻辑）
  if (selected.value && selected.value.region === region && selected.value.rowNo === rowNo) {
    selected.value = null;
  }
  model.value.blocks = model.value.blocks.filter(b => !(b.region === region && b.rowNo === rowNo));
}
function moveRow(region, ri, dir) {
  const rows = rowsOf(region);
  const j = ri + dir;
  if (j < 0 || j >= rows.length) return;
  // 交换两行的 rowNo
  const a = rows[ri].rowNo;
  const b = rows[j].rowNo;
  for (const blk of model.value.blocks) {
    if (blk.region !== region) continue;
    if (blk.rowNo === a) blk.rowNo = b;
    else if (blk.rowNo === b) blk.rowNo = a;
  }
}
function syncRowHeight(row) {
  for (const b of row.blocks) b.heightPct = row.blocks[0].heightPct;
}

// ==== 加载/保存 ====
async function reloadScreens() { screens.value = await listScreens(); }
async function loadScreen(id) {
  const d = await getScreen(id);
  d.blocks = (d.blocks || []).map(b => ({ ...b, _key: `db${b.id}` }));
  model.value = d;
  selected.value = null;
  renderKey.value++;
}
function openCreateScreen() { Object.assign(createDlg, { show: true, name: '', viewLevel: 'BRANCH' }); }
async function onCreateScreen() {
  if (!createDlg.name) { ElMessage.warning('名称必填'); return; }
  const id = await saveScreen({ screenName: createDlg.name, viewLevel: createDlg.viewLevel, blocks: [] });
  createDlg.show = false;
  await reloadScreens();
  curId.value = id;
  await loadScreen(id);
}
async function onSave() {
  saving.value = true;
  try {
    await saveScreen({
      id: model.value.id,
      screenCode: model.value.screenCode,
      screenName: model.value.screenName,
      viewLevel: model.value.viewLevel,
      status: model.value.status,
      blocks: model.value.blocks.map(({ _key, ...b }) => b)
    });
    ElMessage.success('已保存');
    renderKey.value++;
  } finally {
    saving.value = false;
  }
}
async function onSavePoints() {
  await saveMapPoints(points.value);
  ElMessage.success('点位已保存');
  pointDlg.show = false;
  renderKey.value++;
}
function previewFull() {
  const q = [];
  if (previewCtx.orgCode) q.push(`orgCode=${previewCtx.orgCode}`);
  if (previewCtx.empId) q.push(`empId=${previewCtx.empId}`);
  window.open(`#/screen/${model.value.screenCode}${q.length ? '?' + q.join('&') : ''}`, '_blank');
}

function fitPreview() {
  const w = previewRef.value?.clientWidth || 640;
  previewScale.value = Math.min(w / 1920, (w * 9 / 16) / 1080);
}

onMounted(async () => {
  await reloadScreens();
  datasources.value = await listScreenDatasources();
  points.value = await listMapPoints();
  if (screens.value.length) { curId.value = screens.value[0].id; await loadScreen(curId.value); }
  setTimeout(fitPreview, 0);
  window.addEventListener('resize', fitPreview);
});
// 对照 ScreenView.vue：SPA 内反复进出设计器会累积 resize 监听器（闭包持有旧 previewRef），卸载时清理
onBeforeUnmount(() => {
  window.removeEventListener('resize', fitPreview);
});
</script>

<style lang="scss" scoped>
.dsn-cols { display: grid; grid-template-columns: 320px 1fr 340px; gap: 12px; align-items: start; }
.dsn-tree { max-height: 76vh; overflow-y: auto; }
.dsn-region { margin-bottom: 10px; }
.dsn-region-h { display: flex; justify-content: space-between; align-items: center; padding: 4px 0; }
.dsn-row-item { border: 1px dashed #d0d7e2; border-radius: 6px; padding: 6px; margin-bottom: 6px; }
.dsn-row-h { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #6b7a90; flex-wrap: wrap; }
.dsn-block-item { display: flex; align-items: center; gap: 6px; font-size: 13px; padding: 4px 6px;
  border-radius: 4px; cursor: pointer; margin-top: 4px;
  &.on { background: #e8f3ff; outline: 1px solid #409eff; } }
.dsn-b-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #909db1; }
.dsn-preview-wrap { overflow: hidden; }
.dsn-preview { position: relative; width: 100%; aspect-ratio: 16 / 9; border-radius: 6px; overflow: hidden;
  // 覆盖 screen-root 的 fixed 定位，改为容器内预览
  position: relative !important; inset: auto !important; }
.dsn-ctx { margin-top: 8px; }
.dsn-props { max-height: 76vh; overflow-y: auto; }
</style>
