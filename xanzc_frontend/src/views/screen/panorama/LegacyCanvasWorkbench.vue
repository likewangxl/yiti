<template>
  <section class="legacy-canvas-workbench" data-testid="legacy-canvas-workbench">
    <header class="legacy-canvas-workbench__header"><div><h2>组件配置</h2><p>点击缩略图中的组件，配置标题、数据来源与展示字段。</p></div><div class="legacy-canvas-workbench__actions">
      <button type="button" data-testid="legacy-canvas-cancel" :disabled="busy || !dirty" @click="cancel">取消修改</button>
      <button type="button" data-testid="legacy-canvas-preview" :disabled="busy || dirty || missingBlock" @click="$emit('preview', savePayload())">预览</button>
      <button type="button" data-testid="legacy-canvas-save" :disabled="busy || !canSave" @click="save">{{ saving ? '保存中…' : '保存草稿' }}</button>
      <button type="button" data-testid="legacy-canvas-publish" :disabled="busy || !canPublish" @click="publish">发布</button>
    </div></header>
    <p v-if="error" class="legacy-canvas-workbench__error" role="alert">{{ error }}</p>
    <div class="legacy-canvas-workbench__body">
      <section class="legacy-canvas-workbench__canvas-panel">
        <label class="legacy-canvas-workbench__node-picker">选择组件<select data-testid="legacy-node-picker" :value="selectedId" :disabled="disabled || busy" @change="selectNode($event.target.value)"><optgroup label="可见组件"><option v-for="node in visibleNavigableNodes" :key="node.id" :value="node.id">{{ nodeDisplayText(node) }} · {{ node.component }}</option></optgroup><optgroup v-if="hiddenNavigableNodes.length" label="隐藏组件"><option v-for="node in hiddenNavigableNodes" :key="node.id" :value="node.id">{{ nodeDisplayText(node) }} · {{ node.component }}</option></optgroup></select></label>
        <h3>页面缩略图</h3><p>标题实时展示 · 数据仅占位</p>
        <div class="legacy-canvas-workbench__stage" :style="stageStyle" aria-label="旧坐标画布">
          <button v-for="node in visibleNodes" :key="node.id" type="button" class="legacy-canvas-workbench__node" :class="[{ 'is-active': selectedId === node.id }, `is-${String(node.component || '').toLowerCase()}`]"
            :data-node-id="node.id" :style="nodeStyle(node)" :aria-pressed="selectedId === node.id" @click="selectNode(node.id)">
            <template v-if="node.component === 'DecorLine'"><span class="legacy-canvas-workbench__decor" :class="{vertical:node.propValue?.direction==='v'}" aria-hidden="true"></span></template>
            <template v-else-if="node.component === 'ClockWidget'"><span class="legacy-canvas-workbench__clock">— : — : —</span></template>
            <template v-else><span class="legacy-canvas-workbench__node-title">{{ nodeDisplayText(node) }}</span>
              <BranchThumbnailChart v-if="node.component==='ChartWidget' && nodeVisualKind(node)" class="legacy-canvas-workbench__node-chart" :kind="nodeVisualKind(node)" />
              <strong v-else-if="node.component==='ChartWidget'" class="legacy-canvas-workbench__value">—</strong>
              <small v-else-if="node.component==='PeriodFilter'">{{ nodeDisplayMeta(node) }}</small>
            </template>
          </button>
        </div>
        <div v-if="hiddenNodes.length" class="legacy-canvas-workbench__hidden"><strong>隐藏组件（保留，不冒充可见）</strong><button v-for="node in hiddenNodes" :key="node.id" type="button" :data-hidden-node-id="node.id" @click="selectNode(node.id)">{{ nodeTitle(node, blocks) }}</button></div>
      </section>
      <section ref="editorRef" class="legacy-canvas-workbench__editor" tabindex="-1" aria-live="polite">
        <template v-if="selectedNode">
          <h3>{{ nodeTitle(selectedNode, blocks) }}</h3><p class="legacy-canvas-workbench__type">{{ selectedNode.component }} · {{ selectedNode.innerType || '容器/素材' }}</p>
          <p v-if="selectedNode.component === 'ChartWidget' && !selectedBlock" class="legacy-canvas-workbench__error">缺少对应区块，已停止编辑和保存。</p>
          <template v-if="selectedNode.component === 'ChartWidget' && selectedBlock">
            <h4>数据来源与周期</h4>
            <label v-if="selectedNode.innerType === 'BAR_COMPARE'">排列方式<select data-testid="legacy-bar-mode" :value="prop('barMode','vertical')" :disabled="disabled || busy" @change="setProp('barMode',$event.target.value)"><option value="vertical">纵向</option><option value="horizontal">横向</option></select></label>
            <label v-if="['TABLE_LIST','RANK_LIST'].includes(selectedNode.innerType)"><input data-testid="legacy-carousel" type="checkbox" :checked="prop('carousel',false)" :disabled="disabled || busy" @change="setProp('carousel',$event.target.checked)" />自动轮播</label>
            <label>数据来源<select :value="binding.dsId || ''" :disabled="disabled || busy" @change="setBind('dsId', numberOrEmpty($event.target.value))"><option value="">请选择数据来源</option><option v-for="source in sourceOptions" :key="source.id" :data-testid="`legacy-source-${source.id}`" :value="source.id" :disabled="source.__incompatible">{{ source.dsName || source.ds_name || source.dsCode || `数据源 #${source.id}` }}{{ source.__incompatible ? '（当前来源不匹配，仅保留回显）' : '' }}</option></select></label>
            <label>统计周期<select :value="binding.period || 'LATEST'" :disabled="disabled || busy" @change="setBind('period', $event.target.value)"><option v-for="period in periods" :key="period" :value="period">{{ period }}</option></select></label>
            <label>标题<input :value="style.title || ''" :disabled="disabled || busy" @input="setStyle('title', $event.target.value)" /></label>
            <div class="legacy-canvas-workbench__row"><label v-if="selectedNode.innerType === 'METRIC_CARD'">单位兜底（元数据缺失时）<input :value="style.unit || ''" :disabled="disabled || busy" @input="setStyle('unit', $event.target.value)" /></label><label v-if="['METRIC_CARD','GAUGE','LIQUID_PROGRESS'].includes(selectedNode.innerType)">小数位<input type="number" :value="style.decimals ?? ''" :disabled="disabled || busy" @input="setStyle('decimals', numberOrEmpty($event.target.value))" /></label></div>
            <label>刷新秒数<input type="number" :value="style.refreshSec ?? ''" :disabled="disabled || busy" @input="setStyle('refreshSec', numberOrEmpty($event.target.value))" /></label>
            <label><input type="checkbox" :checked="prop('ignoreGlobalPeriod', false)" :disabled="disabled || busy" @change="setProp('ignoreGlobalPeriod', $event.target.checked)" />不跟随全屏周期</label>
            <div class="legacy-canvas-workbench__row"><label>钻取启用<input data-testid="legacy-drill-enabled" type="checkbox" :checked="effectiveDrill().drillEnabled === true" :disabled="disabled || busy" @change="setDrill('drillEnabled', $event.target.checked)" /></label><label>目标屏编码<input data-testid="legacy-drill-target" :value="effectiveDrill().jump?.targetScreenCode || ''" :disabled="disabled || busy" @input="setDrillTarget($event.target.value)" /></label></div>
            <template v-if="chartSchema.items">
              <h4>展示列（{{ selectedNode.innerType }}）</h4>
              <label v-if="chartSchema.categoryCol">分类列<input data-testid="legacy-category-col" :value="binding.categoryCol || ''" :list="`legacy-fields-${selectedNode.id}`" :disabled="disabled || busy" @input="setBind('categoryCol', $event.target.value)" /></label>
              <label v-if="chartSchema.nameCol">兼容名称列（分类列为空时使用）<input :value="binding.nameCol || ''" :list="`legacy-fields-${selectedNode.id}`" :disabled="disabled || busy || Boolean(binding.categoryCol)" @input="setBind('nameCol', $event.target.value)" /></label>
              <label v-if="chartSchema.valueCol">兼容数值列（指标列表为空时使用）<input :value="binding.valueCol || ''" :list="`legacy-fields-${selectedNode.id}`" :disabled="disabled || busy || (binding.items || []).some(item=>String(item.col || '').trim())" @input="setBind('valueCol', $event.target.value)" /></label>
              <p v-if="!(binding.items || []).length" data-testid="legacy-items-all-columns" class="legacy-canvas-workbench__readonly">{{ selectedNode.innerType === 'TABLE_LIST' ? '未指定展示列：使用数据源返回的全部列。' : selectedNode.innerType === 'RANK_LIST' ? '未指定指标列表：沿用已有数值列 valueCol。' : '尚未指定展示指标，请添加需要展示的数据列。' }}</p>
              <div v-for="(item, index) in binding.items || []" :key="`${index}`" class="legacy-canvas-workbench__item-row"><input :value="item.col" :list="`legacy-fields-${selectedNode.id}`" :disabled="disabled || busy" aria-label="数据列" @input="setItem(index, 'col', $event.target.value)" /><input :value="item.label || ''" :disabled="disabled || busy || ['LINE_TREND','AREA_STACK'].includes(selectedNode.innerType)" placeholder="显示名称" aria-label="显示名称" @input="setItem(index, 'label', $event.target.value)" /><button type="button" :disabled="disabled || busy" @click="removeItem(index)">删除</button><button type="button" :disabled="disabled || busy || index === 0" aria-label="上移" @click="moveItem(index, -1)">↑</button><button type="button" :disabled="disabled || busy || index === (binding.items || []).length - 1" aria-label="下移" @click="moveItem(index, 1)">↓</button></div>
              <p v-if="['LINE_TREND','AREA_STACK'].includes(selectedNode.innerType)" class="hint">横轴使用返回结果的首列，系列名称使用数据列名。</p>
              <button type="button" data-testid="legacy-item-add" :disabled="disabled || busy" @click="addItem">增加展示列</button>
              <datalist :id="`legacy-fields-${selectedNode.id}`"><option v-for="field in sourceFields" :key="field.col" :value="field.col">{{ field.alias || field.label || field.col }}</option></datalist>
            </template>
            <template v-else-if="chartSchema.valueField"><label>评分/完成率字段<select :value="prop('valueField', 'score')" :disabled="disabled || busy" @change="setProp('valueField', $event.target.value)"><option value="score">得分</option><option value="rate">完成率</option></select></label></template>
            <template v-else-if="chartSchema.valueCol || binding.valueCol !== undefined"><label data-testid="legacy-value-col">数值列<input :value="binding.valueCol || ''" :list="`legacy-fields-${selectedNode.id}`" :disabled="disabled || busy" @input="setBind('valueCol', $event.target.value)" /></label></template>
            <p v-else class="legacy-canvas-workbench__readonly">{{ chartSchema.note }}</p>
          </template>
          <template v-else-if="selectedNode.component === 'TitleBar'"><h4>文字样式</h4><label>文字<input data-testid="legacy-title-text" :value="prop('text', '')" :disabled="disabled || busy" @input="setProp('text', $event.target.value)" /></label><label>字号<input type="number" :value="prop('fontSize', 30)" :disabled="disabled || busy" @input="setProp('fontSize', numberOrEmpty($event.target.value))" /></label><p class="hint">标题文字使用运行页固定渐变色。</p><label>对齐<select :value="prop('align', 'center')" :disabled="disabled || busy" @change="setProp('align', $event.target.value)"><option value="left">左</option><option value="center">中</option><option value="right">右</option></select></label><label>预设<select :value="prop('preset', 'glow')" :disabled="disabled || busy" @change="setProp('preset', $event.target.value)"><option value="glow">发光</option><option value="slash">斜切</option><option value="underline">下划线</option></select></label></template>
          <template v-else-if="selectedNode.component === 'ClockWidget'"><h4>时钟格式</h4><label>格式<input :value="prop('format', '')" :disabled="disabled || busy" @input="setProp('format', $event.target.value)" /></label><label>文字颜色<input :value="prop('color', '')" :disabled="disabled || busy" @input="setProp('color', $event.target.value)" /></label><label>字号<input type="number" :value="prop('fontSize', 18)" :disabled="disabled || busy" @input="setProp('fontSize', numberOrEmpty($event.target.value))" /></label></template>
          <template v-else-if="selectedNode.component === 'PeriodFilter'"><h4>周期筛选</h4><label>默认周期<select :value="prop('defaultPeriod', 'LATEST')" :disabled="disabled || busy" @change="setProp('defaultPeriod', $event.target.value)"><option v-for="period in periods" :key="period" :value="period">{{ period }}</option></select></label><label>可用周期<input :value="(prop('periods', periods) || []).join(',')" :disabled="disabled || busy" @input="setProp('periods', $event.target.value.split(',').map(item => item.trim()).filter(Boolean))" /></label></template>
          <template v-else-if="selectedNode.component === 'DecorLine'"><h4>装饰线样式</h4><label>预设<input :value="prop('preset', 'cyan')" :disabled="disabled || busy" @input="setProp('preset', $event.target.value)" /></label><label>方向<select :value="prop('direction', 'h')" :disabled="disabled || busy" @change="setProp('direction', $event.target.value)"><option value="h">横向</option><option value="v">纵向</option></select></label><label>粗细<input type="number" :value="prop('thickness', 2)" :disabled="disabled || busy" @input="setProp('thickness', numberOrEmpty($event.target.value))" /></label><label><input type="checkbox" :checked="prop('arrow', false)" :disabled="disabled || busy" @change="setProp('arrow', $event.target.checked)" />显示箭头</label></template>
          <p v-else class="legacy-canvas-workbench__readonly">该组件类型暂未提供结构化常用属性编辑，原始配置会完整保留。</p>
          <details><summary>高级参数（原样保留）</summary><pre>{{ JSON.stringify(selectedNode, null, 2) }}</pre></details>
        </template><p v-else class="legacy-canvas-workbench__readonly">请选择左侧画布组件。</p>
      </section>
    </div>
    <el-dialog v-model="publishOpen" title="确认发布旧画布" :close-on-click-modal="false"><label>发布原因<textarea v-model="publishReason" rows="3" /></label><template #footer><button type="button" :disabled="publishing" @click="publishOpen=false">取消</button><button type="button" :disabled="publishing || !publishReason.trim()" @click="confirmPublish">确认发布</button></template></el-dialog>
  </section>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue';
import BranchThumbnailChart from './BranchThumbnailChart.vue';
import { getScreenCanvas, publishScreenCanvas, saveScreenCanvas } from '@/api/screen';
import { buildLegacyCanvasPayload, collectLegacyNodes, legacyCanvasSnapshotsEqual, nodeBinding, nodeTitle, normalizeLegacyCanvasSnapshot, parseLegacyJson, updateLegacyNode } from './legacyCanvasEditorModel';
import { filterDatasourcesByMeta } from '@/views/screen/designer/widgets/chart-widget/dsFilter';
import { getDatasourceFieldOptions } from './bindings';

const props = defineProps({ screen: { type: Object, default: () => ({}) }, canvas: { type: Object, default: () => ({}) }, datasources: { type: Array, default: () => [] }, disabled: Boolean, canPublish: { type: Boolean, default: false } });
const emit = defineEmits(['saved', 'dirty-change', 'busy-change', 'preview']);
const baseline = ref(JSON.parse(JSON.stringify(props.canvas || {}))); const draft = ref(JSON.parse(JSON.stringify(props.canvas || {}))); const selectedId = ref(''); const editorRef = ref(null); const saving = ref(false); const publishing = ref(false); const error = ref(''); const publishOpen = ref(false); const publishReason = ref('');
const periods = ['LATEST', 'LAST_10D', 'LAST_1M', 'LAST_6M_EOM'];
const components = computed(() => Array.isArray(draft.value.components) ? draft.value.components : []); const allNodes = computed(() => collectLegacyNodes(components.value));
function flattenVisible(nodes, hidden = false, offsetLeft = 0, offsetTop = 0) { const out = []; for (const node of Array.isArray(nodes) ? nodes : []) { const hiddenHere = hidden || node.isShow === false; const style = node.style || {}; const copy = { ...node, style: { ...style, left: Number(style.left || 0) + offsetLeft, top: Number(style.top || 0) + offsetTop } }; if (!hiddenHere) { out.push(copy); if (node.component === 'Group') out.push(...flattenVisible(node.children, false, copy.style.left, copy.style.top)); } } return out; }
function collectHiddenNodes(nodes, inherited = false, output = []) { for (const node of Array.isArray(nodes) ? nodes : []) { const hidden = inherited || node.isShow === false; if (hidden) output.push(node); if (Array.isArray(node.children)) collectHiddenNodes(node.children, hidden, output); } return output; }
const visibleNodes = computed(() => flattenVisible(components.value).filter(node => node.component !== 'Group')); const hiddenNodes = computed(() => collectHiddenNodes(components.value)); const hiddenNavigableNodes = computed(() => hiddenNodes.value.filter(node => node.component !== 'Group')); const visibleNavigableNodes = computed(() => visibleNodes.value.filter(node => node.isShow !== false)); const selectedNode = computed(() => allNodes.value.find(node => node.id === selectedId.value) || null); const blocks = computed(() => draft.value.blocks || props.canvas?.blocks || []); const selectedBlock = computed(() => blocks.value.find(block => String(block.id ?? block.blockId) === String(selectedNode.value?.blockId)) || null); const binding = computed(() => { const value = nodeBinding(selectedNode.value); return value && Object.keys(value).length ? value : parseLegacyJson(selectedBlock.value?.bindJson, {}); }); const style = computed(() => { const value = parseLegacyJson(selectedNode.value?.styleJson, null); return value && Object.keys(value).length ? value : parseLegacyJson(selectedBlock.value?.styleJson, {}); }); const propsJson = computed(() => parseLegacyJson(selectedNode.value?.propValue, {})); const missingBlock = computed(() => allNodes.value.some(node => node.component === 'ChartWidget' && !blocks.value.some(block => String(block.id ?? block.blockId) === String(node.blockId)))); const dirty = computed(() => JSON.stringify(draft.value) !== JSON.stringify(baseline.value)); const busy = computed(() => saving.value || publishing.value || publishOpen.value); const canSave = computed(() => !props.disabled && !busy.value && !missingBlock.value && dirty.value && components.value.length > 0); const canPublish = computed(() => props.canPublish && !props.disabled && !busy.value && !dirty.value && !missingBlock.value && Number.isSafeInteger(draft.value.canvasVersion)); const stageStyle = computed(() => ({ aspectRatio: `${Number(draft.value.canvasStyle?.designWidth || 1920)} / ${Number(draft.value.canvasStyle?.designHeight || 1080)}` }));
const datasourceMeta = computed(() => {
  const type = selectedNode.value?.innerType || selectedBlock.value?.componentType;
  const configured = binding.value?.dsFilter;
  const meta = configured && typeof configured === 'object' ? {...configured} : {};
  if (['LINE_TREND','AREA_STACK','SPARKLINE_CARD'].includes(type) || effectiveDrill().drillEnabled === true) meta.needTimeseries = true;
  if (['KPI_RADAR','KPI_DETAIL_TABLE','PROGRESS_LIST'].includes(type)) meta.needKinds = ['KPI_DETAIL'];
  return meta;
});
const sourceOptions = computed(() => { const compatible = filterDatasourcesByMeta(props.datasources, datasourceMeta.value, props.screen); const current = props.datasources.find(source => String(source.id) === String(binding.value?.dsId)); return current && !compatible.some(source => String(source.id) === String(current.id)) ? [...compatible, { ...current, __incompatible: true }] : compatible; });
const sourceFields = computed(() => { const source = sourceOptions.value.find(item => String(item.id) === String(binding.value?.dsId)); return getDatasourceFieldOptions(source || {}); });
const chartSchema = computed(() => { const type = selectedNode.value?.innerType || selectedBlock.value?.componentType; if (['METRIC_CARD', 'LINE_TREND', 'AREA_STACK', 'TABLE_LIST'].includes(type)) return { items: true, categoryCol: false, nameCol: false, valueCol: false }; if (type === 'RANK_LIST') return { items: true, categoryCol: true, nameCol: true, valueCol: true }; if (type === 'BAR_COMPARE') return { items: true, categoryCol: true }; if (['GAUGE', 'LIQUID_PROGRESS'].includes(type)) return { items: false, valueCol: true }; if (type === 'KPI_RADAR') return { items: false, valueField: true }; if (['KPI_DETAIL_TABLE', 'PROGRESS_LIST'].includes(type)) return { items: false, note: '该 KPI 组件使用固定合同列：名称、目标值、实际值、完成率、缺口和得分；不提供任意字段编辑。' }; return { items: false, note: '该图表类型暂未提供可编辑字段合同，原绑定 JSON 会完整保留。' }; });
watch(busy, value => emit('busy-change', value));
watch(allNodes, nodes => { if (!nodes.some(node => node.id === selectedId.value)) selectedId.value = visibleNodes.value[0]?.id || nodes[0]?.id || ''; }, {immediate:true});
watch(() => props.canvas, value => { const next = JSON.parse(JSON.stringify(value || {})); baseline.value = next; draft.value = JSON.parse(JSON.stringify(next)); selectedId.value = ''; error.value = ''; announceDirty(); }, { deep: true });
function announceDirty() { emit('dirty-change', dirty.value); } function selectNode(id) { selectedId.value = id; nextTick(() => { if (window.innerWidth <= 760) { editorRef.value?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' }); editorRef.value?.focus?.({ preventScroll: true }); } }); } function updateNode(id, updater) { draft.value.components = updateLegacyNode(draft.value.components, id, updater); announceDirty(); } function mutateJson(field, key, value) { if (!selectedNode.value) return; updateNode(selectedNode.value.id, node => { let json = parseLegacyJson(node[field], null); if (!json || !Object.keys(json).length) json = field === 'bindJson' ? { ...binding.value } : field === 'styleJson' ? { ...style.value } : {}; json[key] = value; return { ...node, [field]: JSON.stringify(json) }; }); }
function setBind(key, value) { mutateJson('bindJson', key, value); } function setItem(index, key, value) { const items = (binding.value.items || []).map((item, itemIndex) => itemIndex === index ? { ...item, [key]: value } : item); updateNode(selectedNode.value.id, node => ({ ...node, bindJson: JSON.stringify({ ...binding.value, items }) })); } function setStyle(key, value) { mutateJson('styleJson', key, value); } function setProp(key, value) { updateNode(selectedNode.value.id, node => ({ ...node, propValue: { ...(node.propValue || {}), [key]: value } })); } function prop(key, fallback) { return propsJson.value[key] ?? fallback; } function numberOrEmpty(value) { return value === '' ? '' : Number(value); }
function setItems(items) { updateNode(selectedNode.value.id, node => ({ ...node, bindJson: JSON.stringify({ ...binding.value, items }) })); }
function addItem() { setItems([...(binding.value.items || []), { col: '', label: '' }]); }
function removeItem(index) { setItems((binding.value.items || []).filter((_, itemIndex) => itemIndex !== index)); }
function moveItem(index, direction) { const items = [...(binding.value.items || [])]; const target = index + direction; if (target < 0 || target >= items.length) return; [items[index], items[target]] = [items[target], items[index]]; setItems(items); }
function nodeStyle(node) { const width = Number(draft.value.canvasStyle?.designWidth || 1920); const height = Number(draft.value.canvasStyle?.designHeight || 1080); const style = node.style || {}; return { left: `${(Number(style.left || 0) / width) * 100}%`, top: `${(Number(style.top || 0) / height) * 100}%`, width: `${(Number(style.width || 0) / width) * 100}%`, height: `${(Number(style.height || 0) / height) * 100}%` }; }
function nodeDisplayText(node) { if (node.component === 'ClockWidget') return '时钟'; if (node.component === 'PeriodFilter') return '周期筛选'; if (node.component === 'DecorLine') return '装饰线'; return nodeTitle(node, blocks.value); }
function nodeDisplayMeta(node) { if (node.component === 'ClockWidget') return node.propValue?.format || '当前时间'; if (node.component === 'PeriodFilter') return node.propValue?.defaultPeriod || 'LATEST'; if (node.component === 'DecorLine') return node.propValue?.direction === 'v' ? '竖向装饰线' : '横向装饰线'; return node.component === 'ChartWidget' ? (node.innerType || '图表') : node.component; }
function nodeVisualKind(node) {
  const type=node.innerType || blocks.value.find(block=>String(block.id??block.blockId)===String(node.blockId))?.componentType;
  return {AREA_STACK:'BUSINESS_GROWTH',LINE_TREND:'BUSINESS_GROWTH',BAR_COMPARE:'INSTITUTION_RANKING',RANK_LIST:'INSTITUTION_RANKING',TABLE_LIST:'LIST',KPI_DETAIL_TABLE:'LIST',PROGRESS_LIST:'PROGRESS',GAUGE:'COMPLETION',LIQUID_PROGRESS:'COMPLETION',KPI_RADAR:'COMPOSITION_TAB'}[type] || '';
}
function savePayload() { return buildLegacyCanvasPayload({ screenId: Number(props.screen.id), canvasVersion: draft.value.canvasVersion, canvasStyle: draft.value.canvasStyle, components: draft.value.components, blocks: blocks.value }); }
/** 保存必须得到新版本及完整组件树确认，失败时保留用户草稿。 */
async function save() {
  if (!canSave.value || busy.value || props.disabled) return false;
  saving.value = true; error.value = '';
  try {
    const payload = savePayload();
    const response = await saveScreenCanvas(payload);
    const responseDraft = parseLegacyJson(response?.canvasDraftJson, null);
    if (!Number.isSafeInteger(response?.canvasVersion) || response.canvasVersion <= payload.expectedVersion || !Array.isArray(responseDraft?.components)) {
      throw new Error('服务端未确认完整保存结果，当前编辑仍保留，请重新读取核对。');
    }
    if (!legacyCanvasSnapshotsEqual({ canvasVersion: response.canvasVersion, components: responseDraft.components }, { canvasVersion: response.canvasVersion, components: payload.components })) {
      throw new Error('服务端保存响应内容与本次编辑不一致，当前编辑仍保留，请重新读取核对。');
    }
    const confirmed = await getScreenCanvas(Number(props.screen.id));
    const actual = normalizeLegacyCanvasSnapshot(confirmed);
    const expected = normalizeLegacyCanvasSnapshot({ canvasVersion: response.canvasVersion, canvasStyle: payload.canvasStyle, components: payload.components });
    if (!Array.isArray(actual.components) || actual.canvasVersion !== response.canvasVersion || !legacyCanvasSnapshotsEqual(actual, expected)) {
      throw new Error('服务端回读结果与本次保存内容不一致，当前编辑仍保留，请重新读取核对。');
    }
    // 规范化只用于比较，不能把 JSON 字符串或默认值改写回用户画布。
    draft.value = {...draft.value, components: parseLegacyJson(confirmed.canvasDraftJson, {}).components,
      canvasStyle: parseLegacyJson(confirmed.canvasStyleJson, {}), canvasVersion:actual.canvasVersion};
    baseline.value = JSON.parse(JSON.stringify(draft.value));
    announceDirty(); emit('saved', response); return true;
  } catch (e) {
    error.value = e?.code === 'RPT-43012' ? '版本冲突：当前编辑仍保留，请重新读取核对。' : e?.message || '保存失败';
    return false;
  } finally { saving.value = false; }
}
function cancel() { draft.value = JSON.parse(JSON.stringify(baseline.value)); error.value = ''; announceDirty(); }
function publish() { if (canPublish.value) { publishReason.value = ''; publishOpen.value = true; } }
async function confirmPublish() { if (props.disabled || publishing.value || saving.value || !props.canPublish || dirty.value || missingBlock.value || !Number.isSafeInteger(draft.value.canvasVersion) || !publishReason.value.trim()) { error.value = '当前版本、权限、未保存状态或发布原因不满足发布条件'; return; } publishing.value = true; emit('busy-change', true); try { const response = await publishScreenCanvas({ screenId: Number(props.screen.id), expectedVersion: draft.value.canvasVersion, reason: publishReason.value.trim() }); publishOpen.value = false; emit('saved', response); } catch (e) { error.value = e?.message || '发布失败'; } finally { publishing.value = false; emit('busy-change', false); } }
function effectiveDrill(node = selectedNode.value) { const own = parseLegacyJson(node?.drillJson, null); const block = parseLegacyJson(selectedBlock.value?.drillJson, {}); return { ...block, ...(own && Object.keys(own).length ? own : {}), jump: { ...(block.jump || {}), ...(own?.jump || {}) } }; }
function drillBase(node) { return effectiveDrill(node); }
function setDrill(key, value) { if (!selectedNode.value) return; updateNode(selectedNode.value.id, node => { const json = drillBase(node); json[key] = value; return { ...node, drillJson: JSON.stringify(json) }; }); }
function setDrillTarget(value) { if (!selectedNode.value) return; updateNode(selectedNode.value.id, node => { const json = drillBase(node); json.jump = { ...(json.jump || {}), targetScreenCode: value }; return { ...node, drillJson: JSON.stringify(json) }; }); }
defineExpose({ save, cancel, savePayload, draft });
</script>

<style scoped>
.legacy-canvas-workbench { padding:20px; color:#25344d; background:#fff; border:1px solid #e1e9f4; border-radius:6px; }.legacy-canvas-workbench__header,.legacy-canvas-workbench__body{display:flex;gap:16px}.legacy-canvas-workbench__header{justify-content:space-between;align-items:flex-start;margin-bottom:14px}.legacy-canvas-workbench h2{margin:0;font-size:21px}.legacy-canvas-workbench p{margin:6px 0;color:#8190a7;font-size:13px}.legacy-canvas-workbench__actions{display:flex;gap:8px}.legacy-canvas-workbench button{padding:7px 11px;border:1px solid #cbd8ea;border-radius:4px;background:#fff;color:#2c5ed1;cursor:pointer}.legacy-canvas-workbench button:disabled{cursor:not-allowed;opacity:.55}.legacy-canvas-workbench__canvas-panel{flex:0 0 48%;min-width:0}.legacy-canvas-workbench__editor{flex:1;min-width:0;padding:18px;border:1px solid #e1e9f4;border-radius:5px}.legacy-canvas-workbench__stage{position:relative;overflow:hidden;background:linear-gradient(145deg,#03132b,#071c3d);border:1px solid #174a7b;border-radius:5px}.legacy-canvas-workbench__node{position:absolute;box-sizing:border-box;overflow:hidden;padding:6px;text-align:left;color:#e9f7ff;background:#0b2a55;border:1px solid #28638f;font-size:11px}.legacy-canvas-workbench__node.is-active{border:2px solid #22e0ff;box-shadow:0 0 12px #20dfff66}.legacy-canvas-workbench__node small{display:block;color:#86a9c8;font-size:9px}.legacy-canvas-workbench__hidden{display:flex;flex-wrap:wrap;gap:6px;margin-top:10px;padding:9px;background:#f5f7fb;border:1px solid #dce5f1}.legacy-canvas-workbench__editor h3{margin:0;font-size:18px}.legacy-canvas-workbench__editor h4{margin:18px 0 8px;font-size:14px}.legacy-canvas-workbench__editor label{display:block;margin:8px 0;font-size:12px;font-weight:600}.legacy-canvas-workbench input,.legacy-canvas-workbench select,.legacy-canvas-workbench textarea{display:block;box-sizing:border-box;width:100%;margin-top:5px;padding:7px;border:1px solid #cbd8ea;border-radius:4px;background:#fff}.legacy-canvas-workbench__row{display:grid;grid-template-columns:1fr 1fr;gap:8px}.legacy-canvas-workbench details{margin-top:18px}.legacy-canvas-workbench pre{max-height:220px;overflow:auto;padding:8px;background:#f5f7fb;font-size:11px}.legacy-canvas-workbench__error{padding:8px;color:#a11a2b;background:#fff0f1}.legacy-canvas-workbench__readonly{padding:12px;background:#f5f7fb}.legacy-canvas-workbench__type{color:#8190a7}@media(max-width:760px){.legacy-canvas-workbench__header,.legacy-canvas-workbench__body{display:block}.legacy-canvas-workbench__actions{margin-top:10px;flex-wrap:wrap}.legacy-canvas-workbench__canvas-panel{margin-bottom:14px}.legacy-canvas-workbench__row{grid-template-columns:1fr}}
 .legacy-canvas-workbench__stage{min-height:0}.legacy-canvas-workbench__node.is-decorline{padding:0;background:transparent;border-color:transparent}
</style>

<style scoped>
.legacy-canvas-workbench__canvas-panel h3 { margin:0 0 7px; font-size:20px; }
.legacy-canvas-workbench__canvas-panel > p { margin-bottom:16px; }
.legacy-canvas-workbench__stage { container-type:inline-size; min-height:0; }
.legacy-canvas-workbench .legacy-canvas-workbench__node { display:flex; flex-direction:column; color:#e5f5ff; background:linear-gradient(145deg,#0a294f,#071a35); padding:5px; border:1px solid #285d89; }
.legacy-canvas-workbench__node-title { font-size:clamp(8px,1.6cqw,13px); font-weight:600; line-height:1.3; overflow-wrap:anywhere; flex:none; }
.legacy-canvas-workbench .legacy-canvas-workbench__node.is-titlebar { justify-content:center; text-align:center; background:#0c2d51; }
.legacy-canvas-workbench__node.is-titlebar .legacy-canvas-workbench__node-title { font-size:clamp(11px,2.5cqw,21px); }
.legacy-canvas-workbench__clock { font-size:clamp(8px,1.6cqw,12px); white-space:nowrap; }
.legacy-canvas-workbench .legacy-canvas-workbench__node-chart { flex:1; height:0; min-height:0; width:100%; }
.legacy-canvas-workbench__value { color:#effbff; padding:6px 0; font-size:clamp(14px,3.5cqw,28px); }
.legacy-canvas-workbench .legacy-canvas-workbench__node.is-decorline { padding:0; border:0; overflow:visible; background:transparent; }
.legacy-canvas-workbench__node.is-decorline::after { content:''; position:absolute; inset:-3px 0; }
.legacy-canvas-workbench__decor { display:block; width:100%; height:100%; min-height:1px; background:#34dcea; box-shadow:0 0 5px #1da7dd; }
.legacy-canvas-workbench__decor.vertical { min-width:1px; }
.legacy-canvas-workbench .legacy-canvas-workbench__node.is-active { border:1px solid #1bddfa; box-shadow:inset 0 0 0 1px #1bddfa,0 0 8px #20dfff66; }
.legacy-canvas-workbench__item-row { display:grid; grid-template-columns:minmax(0,1fr) minmax(0,1fr) auto auto auto; gap:5px; align-items:center; margin-bottom:8px; }
.legacy-canvas-workbench input[type="checkbox"] { display:inline-block; width:auto; margin-right:7px; }
.legacy-canvas-workbench__editor { overflow-wrap:anywhere; }
.legacy-canvas-workbench__editor select { min-width:0; }
.legacy-canvas-workbench [data-testid="legacy-canvas-save"] { color:#fff; border-color:#2d63dc; background:#2d63dc; }
.legacy-canvas-workbench__node-picker { display:block; margin-bottom:10px; font-weight:600; font-size:12px; }
.legacy-canvas-workbench__node-picker select { font-weight:400; }
@media(max-width:600px) { .legacy-canvas-workbench { padding:12px; } .legacy-canvas-workbench__editor { padding:12px; } .legacy-canvas-workbench__item-row { grid-template-columns:minmax(0,1fr) minmax(0,1fr); } }
</style>
