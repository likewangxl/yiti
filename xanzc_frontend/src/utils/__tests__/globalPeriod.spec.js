// 全屏周期过滤器联动纯函数测试（spec 2026-07-17 §5.3）
// 覆盖：周期合法性 / 组件是否应响应联动（dsType 快照优先 + 时序专属图表兜底 + ignoreGlobalPeriod）/
//       最终取数周期解析 / PeriodFilter propValue 规整（非法剔除、空集回退、默认项校正）
import { describe, it, expect } from 'vitest';
import { TIME_PARAM_PRESETS } from '../dsConfig';
import {
  GLOBAL_PERIOD_INJECT_KEY,
  PERIOD_LABELS,
  TIMESERIES_ONLY_COMPONENT_TYPES,
  isValidGlobalPeriod,
  isTimeseriesBlock,
  shouldApplyGlobalPeriod,
  resolveBlockPeriod,
  normalizePeriodOptions
} from '../globalPeriod';

describe('globalPeriod 周期合法性', () => {
  it('四个预设周期模板全部合法（与 dsConfig.TIME_PARAM_PRESETS 同源）', () => {
    for (const p of TIME_PARAM_PRESETS) expect(isValidGlobalPeriod(p)).toBe(true);
    expect(TIME_PARAM_PRESETS).toEqual(['LATEST', 'LAST_10D', 'LAST_1M', 'LAST_6M_EOM']);
  });
  it('null/undefined/空串/未知值/非字符串全部非法', () => {
    expect(isValidGlobalPeriod(null)).toBe(false);
    expect(isValidGlobalPeriod(undefined)).toBe(false);
    expect(isValidGlobalPeriod('')).toBe(false);
    expect(isValidGlobalPeriod('LAST_3Y')).toBe(false);
    expect(isValidGlobalPeriod(10)).toBe(false);
  });
  it('PERIOD_LABELS 覆盖全部预设且为中文文案', () => {
    for (const p of TIME_PARAM_PRESETS) expect(PERIOD_LABELS[p]).toBeTruthy();
    expect(PERIOD_LABELS.LATEST).toBe('最新');
    expect(PERIOD_LABELS.LAST_10D).toBe('近10天');
  });
  it('provide/inject 键常量导出（双端共用防字符串漂移）', () => {
    expect(GLOBAL_PERIOD_INJECT_KEY).toBe('screenGlobalPeriod');
  });
});

describe('isTimeseriesBlock 时序区块判定', () => {
  it('bind.dsType 显式 TIMESERIES → true（不依赖组件类型）', () => {
    expect(isTimeseriesBlock({ dsType: 'TIMESERIES' }, 'TABLE_LIST')).toBe(true);
  });
  it('bind.dsType 显式 SINGLE → false（显式快照优先于组件类型兜底）', () => {
    expect(isTimeseriesBlock({ dsType: 'SINGLE' }, 'LINE_TREND')).toBe(false);
  });
  it('dsType 缺失时按时序专属图表类型兜底（后端 RPT-43005 保证其只能绑 TIMESERIES）', () => {
    expect(TIMESERIES_ONLY_COMPONENT_TYPES).toEqual(['LINE_TREND', 'AREA_STACK']);
    expect(isTimeseriesBlock({}, 'LINE_TREND')).toBe(true);
    expect(isTimeseriesBlock(null, 'AREA_STACK')).toBe(true);
    expect(isTimeseriesBlock({}, 'METRIC_CARD')).toBe(false);
    expect(isTimeseriesBlock(undefined, undefined)).toBe(false);
  });
});

describe('shouldApplyGlobalPeriod 联动覆盖判定', () => {
  const tsBind = { dsId: 1, period: 'LATEST', dsType: 'TIMESERIES' };
  it('globalPeriod 为 null（默认不干预）→ false', () => {
    expect(shouldApplyGlobalPeriod({ globalPeriod: null, bind: tsBind, propValue: {}, componentType: 'LINE_TREND' })).toBe(false);
  });
  it('globalPeriod 非法值 → false（防脏值透传后端 43011）', () => {
    expect(shouldApplyGlobalPeriod({ globalPeriod: 'HACK', bind: tsBind, propValue: {}, componentType: 'LINE_TREND' })).toBe(false);
  });
  it('propValue.ignoreGlobalPeriod=true → false（组件级豁免开关）', () => {
    expect(shouldApplyGlobalPeriod({
      globalPeriod: 'LAST_10D', bind: tsBind,
      propValue: { ignoreGlobalPeriod: true }, componentType: 'LINE_TREND'
    })).toBe(false);
  });
  it('TIMESERIES 数据源 + 合法周期 + 未豁免 → true（propValue 缺省视为未豁免）', () => {
    expect(shouldApplyGlobalPeriod({ globalPeriod: 'LAST_1M', bind: tsBind, propValue: null, componentType: 'LINE_TREND' })).toBe(true);
    expect(shouldApplyGlobalPeriod({ globalPeriod: 'LAST_1M', bind: tsBind, propValue: { ignoreGlobalPeriod: false }, componentType: 'LINE_TREND' })).toBe(true);
  });
  it('SINGLE 数据源不受全局周期影响', () => {
    expect(shouldApplyGlobalPeriod({
      globalPeriod: 'LAST_10D', bind: { dsId: 1, dsType: 'SINGLE' }, propValue: {}, componentType: 'METRIC_CARD'
    })).toBe(false);
  });
});

describe('resolveBlockPeriod 最终取数周期', () => {
  it('应联动时返回 globalPeriod 覆盖值', () => {
    expect(resolveBlockPeriod({
      globalPeriod: 'LAST_6M_EOM', bind: { dsId: 1, period: 'LAST_10D', dsType: 'TIMESERIES' },
      propValue: {}, componentType: 'LINE_TREND'
    })).toBe('LAST_6M_EOM');
  });
  it('不联动时返回区块自身 period', () => {
    expect(resolveBlockPeriod({
      globalPeriod: 'LAST_6M_EOM', bind: { dsId: 1, period: 'LAST_10D', dsType: 'TIMESERIES' },
      propValue: { ignoreGlobalPeriod: true }, componentType: 'LINE_TREND'
    })).toBe('LAST_10D');
  });
  it('自身 period 缺省时回退 LATEST（与 BlockContainer 既有缺省一致）', () => {
    expect(resolveBlockPeriod({ globalPeriod: null, bind: {}, propValue: {}, componentType: 'METRIC_CARD' })).toBe('LATEST');
  });
});

describe('normalizePeriodOptions PeriodFilter propValue 规整', () => {
  it('缺省 propValue → 全量预设 + 默认选中第一项', () => {
    expect(normalizePeriodOptions(null)).toEqual({ periods: [...TIME_PARAM_PRESETS], defaultPeriod: 'LATEST' });
    expect(normalizePeriodOptions({})).toEqual({ periods: [...TIME_PARAM_PRESETS], defaultPeriod: 'LATEST' });
  });
  it('非法项剔除、重复项去重且保序', () => {
    expect(normalizePeriodOptions({ periods: ['LAST_10D', 'HACK', 'LAST_10D', 'LATEST'], defaultPeriod: 'LATEST' }))
      .toEqual({ periods: ['LAST_10D', 'LATEST'], defaultPeriod: 'LATEST' });
  });
  it('periods 为空/全非法 → 回退全量预设', () => {
    expect(normalizePeriodOptions({ periods: [], defaultPeriod: 'LAST_1M' }).periods).toEqual([...TIME_PARAM_PRESETS]);
    expect(normalizePeriodOptions({ periods: ['X', 'Y'] }).periods).toEqual([...TIME_PARAM_PRESETS]);
  });
  it('defaultPeriod 非法或不在可选集合内 → 校正为集合第一项', () => {
    expect(normalizePeriodOptions({ periods: ['LAST_1M', 'LAST_6M_EOM'], defaultPeriod: 'LATEST' }).defaultPeriod).toBe('LAST_1M');
    expect(normalizePeriodOptions({ periods: ['LAST_1M'], defaultPeriod: 'HACK' }).defaultPeriod).toBe('LAST_1M');
  });
});
