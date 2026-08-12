<template>
  <CommonAttr :element="element">
    <el-form-item label="地图模式">
      <el-radio-group v-model="config.mode" @change="changeMode">
        <el-radio-button label="SHAANXI_LEGACY">陕西兼容地图</el-radio-button>
        <el-radio-button label="XIAN_COMPOSITE">西安复合经营地图</el-radio-button>
      </el-radio-group>
    </el-form-item>
    <template v-if="config.schemaVersion === 2">
      <el-form-item label="本地筛选">
        <span class="hint">城市 610100 · 经营等级 PRIMARY · 真实 GCJ-02 坐标</span>
      </el-form-item>
      <el-form-item label="异地节点">
        <div class="satellite-list">
          <div v-for="anchor in anchors" :key="anchor" class="satellite-row">
            <span class="anchor-label">{{ anchorLabels[anchor] }}</span>
            <el-input :model-value="nodeByAnchor(anchor).orgCode" disabled />
            <el-input v-model="nodeByAnchor(anchor).targetScreenCode" placeholder="目标屏" @change="sync" />
          </div>
        </div>
      </el-form-item>
      <el-form-item label="地图声明">
        <el-input v-model="config.disclaimer" disabled />
        <span class="hint">声明固定展示，不可隐藏</span>
      </el-form-item>
    </template>
  </CommonAttr>
</template>
<script setup>
// MapCenter 属性面板：v1 仅保留陕西旧点位；v2 只保存模式、锚点机构编码与目标屏，
// 真实经纬度由机构画像运行时读取，禁止在画布 JSON 内手填坐标。
import { reactive } from 'vue';
import CommonAttr from '@/views/screen/designer/panels/CommonAttr.vue';
import { MAP_ANCHORS, FIXED_SATELLITE_ORG_CODES, normalizeMapConfig, COMPOSITE_DISCLAIMER } from '@/utils/screenScope';
import { useScreenDesignerStore } from '@/stores/screenDesigner';

const props = defineProps({ element: { type: Object, required: true } });
const store = useScreenDesignerStore();
if (!props.element.propValue) props.element.propValue = {};
const config = reactive(normalizeMapConfig(props.element.propValue));
const anchors = MAP_ANCHORS;
const anchorLabels = { LEFT: '宝鸡/左', RIGHT: '渭南/右', TOP: '咸阳/上', FAR_TOP: '榆林/远上' };
if (config.schemaVersion === 2) config.disclaimer = COMPOSITE_DISCLAIMER;

function nodeByAnchor(anchor) {
  let node = config.satelliteNodes.find(item => item.anchor === anchor);
  if (!node) {
    node = { orgCode: FIXED_SATELLITE_ORG_CODES[anchor] || '', anchor, targetScreenCode: 'SCR_BRANCH' };
    config.satelliteNodes.push(node);
  }
  // 本期四个异地节点是已确认的固定业务映射；只允许管理员调整其目标详情屏。
  node.orgCode = FIXED_SATELLITE_ORG_CODES[anchor] || '';
  return node;
}
function sync() {
  props.element.propValue = JSON.parse(JSON.stringify(config));
  store.pushSnapshotDebounced();
}
function changeMode(mode) {
  if (mode === 'XIAN_COMPOSITE') {
    config.schemaVersion = 2;
    config.baseRegion = 'XIAN_OUTLINE';
    config.disclaimer = COMPOSITE_DISCLAIMER;
    for (const anchor of anchors) nodeByAnchor(anchor);
  } else {
    config.schemaVersion = 1;
    config.mode = 'SHAANXI_LEGACY';
  }
  sync();
}
</script>
<style scoped>
.hint { color: #7d9bc9; font-size: 12px; line-height: 1.5; }
.satellite-list { width: 100%; }
.satellite-row { display: grid; grid-template-columns: 54px 1fr 1fr; gap: 4px; margin-bottom: 5px; }
.anchor-label { color: #9bb6df; font-size: 11px; line-height: 28px; }
</style>
