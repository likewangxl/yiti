/**
 * 支行经营总览的展示配置入口。
 *
 * 已配置的 displaySchemaVersion=1 组件原样保留，只补上支行页必须的
 * 中央指标区和右侧员工排名区。没有可用配置时使用明确的空数据配置，
 * 所有数值仍由运行模型提供，展示层不会生成经营数据。
 */

const FALLBACK_COMPONENTS = Object.freeze([
  { componentId: 'business-retail-deposit-balance', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 0, visible: true, dataRefs: [{ metricCode: 'retailDeposit', metricName: '零售存款余额', unit: 'HUNDRED_MILLION' }], content: { mainField: 'value' } },
  { componentId: 'business-retail-deposit-rate', componentType: 'COMPLETION', layoutRegion: 'HEADER', order: 1, visible: true, dataRefs: [{ metricCode: 'retailDepositRate', metricName: '零售存款完成率', unit: 'PERCENT' }], content: { mainField: 'value' }, format: { displayUnit: 'PERCENT', decimals: 2 } },
  { componentId: 'business-retail-loan-balance', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 2, visible: true, dataRefs: [{ metricCode: 'retailLoan', metricName: '零售贷款余额', unit: 'HUNDRED_MILLION' }], content: { mainField: 'value' } },
  { componentId: 'business-retail-loan-rate', componentType: 'COMPLETION', layoutRegion: 'HEADER', order: 3, visible: true, dataRefs: [{ metricCode: 'retailLoanRate', metricName: '零售贷款完成率', unit: 'PERCENT' }], content: { mainField: 'value' }, format: { displayUnit: 'PERCENT', decimals: 2 } },
  { componentId: 'business-corp-deposit-balance', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 4, visible: true, dataRefs: [{ metricCode: 'corpDeposit', metricName: '对公存款余额', unit: 'HUNDRED_MILLION' }], content: { mainField: 'value' } },
  { componentId: 'business-corp-deposit-rate', componentType: 'COMPLETION', layoutRegion: 'HEADER', order: 5, visible: true, dataRefs: [{ metricCode: 'corpDepositRate', metricName: '对公存款完成率', unit: 'PERCENT' }], content: { mainField: 'value' }, format: { displayUnit: 'PERCENT', decimals: 2 } },
  { componentId: 'business-corp-loan-balance', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 6, visible: true, dataRefs: [{ metricCode: 'corpLoan', metricName: '对公贷款余额', unit: 'HUNDRED_MILLION' }], content: { mainField: 'value' } },
  { componentId: 'business-corp-loan-rate', componentType: 'COMPLETION', layoutRegion: 'HEADER', order: 7, visible: true, dataRefs: [{ metricCode: 'corpLoanRate', metricName: '对公贷款完成率', unit: 'PERCENT' }], content: { mainField: 'value' }, format: { displayUnit: 'PERCENT', decimals: 2 } },
  { componentId: 'business-revenue-operating', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 8, visible: true, dataRefs: [{ metricCode: 'revenue', metricName: '营业收入', unit: 'HUNDRED_MILLION' }], content: { mainField: 'value' } },
  { componentId: 'business-revenue-fee', componentType: 'METRIC_CARD', layoutRegion: 'HEADER', order: 9, visible: true, dataRefs: [{ metricCode: 'intermediaryIncome', metricName: '中间业务收入', unit: 'HUNDRED_MILLION' }], content: { mainField: 'value' } },
  { componentId: 'branch-composition', componentType: 'COMPOSITION_TABS', layoutRegion: 'LEFT', order: 0, visible: true, dataRefs: [], content: {
    tabs: [
      { tabKey: 'deposit', label: '存款', corporateField: 'corporate', retailField: 'retail', totalField: 'total', unit: 'HUNDRED_MILLION' },
      { tabKey: 'loan', label: '贷款', corporateField: 'corporate', retailField: 'retail', totalField: 'total', unit: 'HUNDRED_MILLION' }
    ]
  } },
  { componentId: 'branch-incomplete-achievement', componentType: 'MAP', layoutRegion: 'CENTER', order: 0, visible: true, content: {} },
  { componentId: 'branch-employee-ranking', componentType: 'RANKING', layoutRegion: 'RIGHT', order: 0, visible: true, content: { rankingMetrics: [] } }
]);

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function presentationOf(source) {
  if (!isObject(source)) return {};
  if (isObject(source.displayPresentation)) return presentationOf(source.displayPresentation);
  if (source.displaySchemaVersion !== undefined || source.display) return source;
  if (isObject(source.presentation)) return presentationOf(source.presentation);
  if (isObject(source.canvasStyle?.presentation)) return presentationOf(source.canvasStyle.presentation);
  if (isObject(source.renderPackage?.canvasStyle?.presentation)) return presentationOf(source.renderPackage.canvasStyle.presentation);
  return {};
}

function visibleComponents(presentation) {
  return Array.isArray(presentation?.display?.components)
    ? presentation.display.components.filter(component => isObject(component) && component.visible !== false)
    : [];
}

function firstOfType(components, componentType, region) {
  return components.find(component => component.componentType === componentType
    && (!region || component.layoutRegion === region)) || null;
}

function cloneComponent(component) {
  return { ...component, dataRefs: Array.isArray(component.dataRefs) ? component.dataRefs.map(ref => ({ ...ref })) : component.dataRefs };
}

/** 取得支行页最终使用的 displaySchemaVersion=1 配置。 */
export function buildBranchOverviewPresentation(source) {
  const presentation = presentationOf(source);
  const configured = presentation.displaySchemaVersion === 1 && presentation.template === 'branch-overview-v1';
  if (!configured) {
    return { displaySchemaVersion: 1, type: 'CODE', template: 'branch-overview-v1', display: { components: FALLBACK_COMPONENTS.map(cloneComponent) } };
  }

  const components = visibleComponents(presentation).map(cloneComponent);
  if (!firstOfType(components, 'MAP', 'CENTER')) components.push(cloneComponent(FALLBACK_COMPONENTS.find(item => item.componentId === 'branch-incomplete-achievement')));
  if (!firstOfType(components, 'RANKING', 'RIGHT')) components.push(cloneComponent(FALLBACK_COMPONENTS.find(item => item.componentId === 'branch-employee-ranking')));
  return {
    ...presentation,
    display: { ...(isObject(presentation.display) ? presentation.display : {}), components }
  };
}

export function presentationIsConfigured(source) {
  const presentation = presentationOf(source);
  return presentation.displaySchemaVersion === 1 && presentation.template === 'branch-overview-v1';
}

export { FALLBACK_COMPONENTS };
