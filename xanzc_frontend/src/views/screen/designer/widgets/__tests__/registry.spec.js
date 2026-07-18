import { describe, it, expect } from 'vitest';
import { findWidget, findAttr, newComponentFromMeta, materialMetas, chartMetas } from '../index';

describe('widgets 注册表', () => {
  it('素材元数据 9 个(5 既有 + 标题条/装饰线/跑马灯 + 周期过滤器) + 图表元数据 13 个（4 占位启用 + 4 个 KPI 专属新增）', () => {
    expect(materialMetas.length).toBe(9);
    // 2026-07-17 素材装饰扩充:新素材注册进拖拽面板(与后端 COMPONENT_TYPES 白名单同步)
    for (const c of ['TitleBar', 'DecorLine', 'Marquee', 'PeriodFilter']) {
      expect(materialMetas.some(m => m.component === c)).toBe(true);
      expect(findWidget(c)).toBeTruthy();
      expect(findAttr(c)).toBeTruthy();
    }
    expect(chartMetas.length).toBe(13);
    // 本期起全部图表均已启用，不再有 enabled:false 占位
    expect(chartMetas.every(c => c.enabled !== false)).toBe(true);
    // KPI 专属图表声明 needKinds（属性面板数据源过滤 + 后端校验双侧联动）
    for (const t of ['KPI_DETAIL_TABLE', 'KPI_RADAR', 'PROGRESS_LIST']) {
      expect(chartMetas.find(c => c.innerType === t)?.needKinds).toEqual(['KPI_DETAIL']);
    }
    // LIQUID_PROGRESS 不限 source_kind
    expect(chartMetas.find(c => c.innerType === 'LIQUID_PROGRESS')?.needKinds).toBeUndefined();
  });
  it('findWidget 能取到素材/图表渲染组件', () => {
    expect(findWidget('TextLabel')).toBeTruthy();
    expect(findWidget('ChartWidget')).toBeTruthy();
    expect(findWidget('NotExist')).toBeFalsy();
  });
  it('findAttr 能取到素材/图表的属性面板组件(Produces 接口)', () => {
    expect(findAttr('TextLabel')).toBeTruthy();
    expect(findAttr('ChartWidget')).toBeTruthy();
    expect(findAttr('NotExist')).toBeFalsy();
  });
  it('newComponentFromMeta 生成带 id/style/默认 propValue 的节点', () => {
    const n = newComponentFromMeta('TextLabel');
    expect(n.id).toMatch(/^w-/);
    expect(n.component).toBe('TextLabel');
    expect(n.style.width).toBeGreaterThan(0);
    // ChartWidget 需带 innerType 与 blockId(null 待保存 upsert)
    const c = newComponentFromMeta('ChartWidget', 'METRIC_CARD');
    expect(c.innerType).toBe('METRIC_CARD');
    expect(c.blockId).toBeNull();
  });
  it('newComponentFromMeta 对 enabled:false 的占位图表类型直接拒绝(注册表层兜底门禁,防面板层漏过滤)', () => {
    // 本期全部图表已启用,注册表内无真实占位;临时注入一个伪造占位验证门禁逻辑仍然有效,用毕恢复
    const fake = { innerType: '__FAKE_DISABLED__', label: '伪造占位', enabled: false };
    chartMetas.push(fake);
    try {
      expect(() => newComponentFromMeta('ChartWidget', fake.innerType)).toThrow('该图表类型尚未启用');
    } finally {
      chartMetas.splice(chartMetas.indexOf(fake), 1);
    }
    // 已启用类型正常创建
    expect(newComponentFromMeta('ChartWidget', 'GAUGE').innerType).toBe('GAUGE');
  });
  it('MapCenter(省级屏地图,复用运行时 MapCenter.vue)已登记 findWidget/findAttr,但不进入可拖拽素材面板', () => {
    // materialMetas 驱动 ComponentPanel 的拖拽入口;MapCenter 的 props 契约是 mapPoints 数组,
    // 与素材类 element/propValue 完全不同,一期不开放拖拽创建,故意不并入 materialMetas(素材扩充后为 8 个)。
    expect(findWidget('MapCenter')).toBeTruthy();
    expect(findAttr('MapCenter')).toBeTruthy();
    expect(materialMetas.length).toBe(9);
    expect(materialMetas.some(m => m.component === 'MapCenter')).toBe(false);
  });
  it('新素材 newComponentFromMeta 生成节点携带默认 propValue(标题条预设/跑马灯速度方向)', () => {
    const t = newComponentFromMeta('TitleBar');
    expect(t.propValue.preset).toBe('glow');
    expect(t.propValue.text).toBeTruthy();
    const m = newComponentFromMeta('Marquee');
    expect(m.propValue.speed).toBe(60);
    expect(m.propValue.direction).toBe('left');
    const d = newComponentFromMeta('DecorLine');
    expect(d.propValue.direction).toBe('h');
    expect(d.propValue.preset).toBe('cyan');
  });
  it('PeriodFilter(全屏周期过滤器)默认 propValue:全量预设周期 + 默认选中 LATEST(spec §5.3)', () => {
    const f = newComponentFromMeta('PeriodFilter');
    expect(f.propValue.periods).toEqual(['LATEST', 'LAST_10D', 'LAST_1M', 'LAST_6M_EOM']);
    expect(f.propValue.defaultPeriod).toBe('LATEST');
  });
});
