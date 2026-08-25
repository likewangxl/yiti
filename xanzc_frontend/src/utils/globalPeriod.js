// 大屏「全屏周期过滤器」联动纯函数（spec 2026-07-17 §5.3，vitest 全覆盖）
//
// 联动数据流：ScreenView provide 响应式 globalPeriod(默认 null=不干预) → PeriodFilter 切换时写入 →
// BlockContainer watch 到变化后按本文件判定是否用 globalPeriod 覆盖自身 bind.period 重新取数。
// 判定/解析逻辑全部收敛在此（组件内零业务分支），设计器画布未 provide 该键 → 静态展示不联动。
import { TIME_PARAM_PRESETS } from './dsConfig';

/** provide/inject 键（ScreenView 提供、PeriodFilter/BlockContainer 消费，常量共用防字符串漂移） */
export const GLOBAL_PERIOD_INJECT_KEY = 'screenGlobalPeriod';

/** 周期模板中文文案（与 DrillTrend 钻取 tab 口径一致） */
export const PERIOD_LABELS = { LATEST: '最新', LAST_10D: '近10天', LAST_1M: '近1个月', LAST_6M_EOM: '近6个月末' };

/**
 * 时序专属图表类型：后端 RPT-43005（组件数据源不匹配）保证 needTimeseries 图表只能绑
 * TIMESERIES 数据源 → 旧区块 bind 快照缺 dsType 时可按组件类型兜底判定为时序区块。
 */
export const TIMESERIES_ONLY_COMPONENT_TYPES = ['LINE_TREND', 'AREA_STACK', 'SPARKLINE_CARD'];

/** 周期合法性：仅接受预设周期模板字符串（防脏值透传后端触发 RPT-43011） */
export function isValidGlobalPeriod(period) {
  return typeof period === 'string' && TIME_PARAM_PRESETS.includes(period);
}

/**
 * 区块是否绑定时序（TIMESERIES）数据源：
 * 1) bind.dsType 显式快照优先（设计器选择数据源时落盘）——显式 SINGLE 即拒绝，不再看组件类型；
 * 2) 快照缺失（历史区块）时按时序专属图表类型兜底；其余未知情况按非时序处理（fail-safe，
 *    宁可不联动也不给 SINGLE 数据源发时序周期）。
 */
export function isTimeseriesBlock(bind, componentType) {
  const dsType = bind && bind.dsType;
  if (dsType) return dsType === 'TIMESERIES';
  return TIMESERIES_ONLY_COMPONENT_TYPES.includes(componentType);
}

/**
 * 组件是否应响应全局周期联动：
 * globalPeriod 合法 且 组件未声明 propValue.ignoreGlobalPeriod 豁免 且 区块绑定时序数据源。
 */
export function shouldApplyGlobalPeriod({ globalPeriod, bind, propValue, componentType }) {
  if (!isValidGlobalPeriod(globalPeriod)) return false;
  if (propValue && propValue.ignoreGlobalPeriod === true) return false;
  return isTimeseriesBlock(bind, componentType);
}

/** 区块最终取数周期：应联动 → globalPeriod 覆盖；否则自身 bind.period（缺省 LATEST，与既有取数缺省一致） */
export function resolveBlockPeriod({ globalPeriod, bind, propValue, componentType }) {
  if (shouldApplyGlobalPeriod({ globalPeriod, bind, propValue, componentType })) return globalPeriod;
  return (bind && bind.period) || 'LATEST';
}

/**
 * PeriodFilter propValue 规整（读时兼容，脏配置不崩）：
 * 可选周期集合剔除非法项、去重保序，空集/全非法回退全量预设；
 * 默认选中项必须落在规整后的集合内，否则校正为集合第一项。
 */
export function normalizePeriodOptions(propValue) {
  const raw = Array.isArray(propValue && propValue.periods) ? propValue.periods : [];
  const valid = [...new Set(raw.filter(isValidGlobalPeriod))];
  const periods = valid.length ? valid : [...TIME_PARAM_PRESETS];
  const def = propValue && periods.includes(propValue.defaultPeriod)
    ? propValue.defaultPeriod
    : periods[0];
  return { periods, defaultPeriod: def };
}
