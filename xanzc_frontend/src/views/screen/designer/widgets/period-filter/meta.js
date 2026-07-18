// 全屏周期过滤器（spec 2026-07-17 §5.3）:运行时把选中周期写入 screen 级 globalPeriod,
// 联动所有 TIMESERIES 区块;每屏最多 1 个(后端 ScreenCanvasServiceImpl 保存/发布双侧校验 RPT-43006)
export default {
  component: 'PeriodFilter', label: '周期过滤器', group: 'material', icon: '⏱',
  defaultStyle: { top: 88, left: 1440, width: 400, height: 44 },
  defaultProps: { periods: ['LATEST', 'LAST_10D', 'LAST_1M', 'LAST_6M_EOM'], defaultPeriod: 'LATEST' }
};
