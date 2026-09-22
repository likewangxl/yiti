const ref = (blockId, metricCode, metricName, unit = 'YUAN') => ({
  blockId, role: 'PRIMARY', metricCode, metricName, unit, dimension: 'ORG'
});

const base = (componentId, componentType, blockId, overrides = {}) => ({
  componentId,
  componentType,
  layoutRegion: 'LEFT',
  order: 0,
  visible: true,
  text: { titleMode: 'AUTO', title: '' },
  format: { displayUnit: 'AUTO', decimals: 2, thousandsSeparator: true, negativeStyle: 'SIGNED', emptyText: '—' },
  content: { mainField: 'value', subFields: [], series: [], columns: [], tabs: [], rankingMetrics: [] },
  interaction: { action: 'NONE' },
  dataRefs: [ref(blockId, `METRIC_${blockId}`, `指标${blockId}`)],
  ...overrides
});

export const legacyPresentation = Object.freeze({ type: 'CODE', template: 'branch-overview-v1' });

export const validDisplayConfig = Object.freeze({
  schemaVersion: 2,
  presentation: {
    type: 'CODE',
    template: 'branch-overview-v1',
    displaySchemaVersion: 1,
    institutionRules: {
      allowedOperatingLevels: ['PRIMARY'],
      allowedOrgNatures: ['SECONDARY_BRANCH']
    },
    display: {
      components: [
        base('deposit-card', 'METRIC_CARD', 11, {
          visible: false,
          text: { titleMode: 'CUSTOM', title: '自定义存款' },
          format: { displayUnit: 'YUAN', decimals: 0, thousandsSeparator: true, negativeStyle: 'SIGNED', emptyText: '—' }
        }),
        base('completion-card', 'COMPLETION', 12, {
          format: { displayUnit: 'PERCENT', decimals: 2 },
          dataRefs: [ref(12, 'RATE_01', '目标完成率', 'PERCENT')]
        }),
        base('trend-main', 'TREND', 13, {
          content: { mainField: '', subFields: [], series: [
            { seriesKey: 'deposit', field: 'deposit', label: '存款', unit: 'YUAN' },
            { seriesKey: 'loan', field: 'loan', label: '贷款', unit: 'YUAN' }
          ], columns: [], tabs: [], rankingMetrics: [] }
        }),
        base('composition-main', 'COMPOSITION_TABS', 14, {
          content: { mainField: '', subFields: [], series: [], columns: [], rankingMetrics: [], tabs: [
            { tabKey: 'deposit', label: '存款', corporateField: 'corporate', retailField: 'retail', totalField: 'total', unit: 'YUAN' }
          ] }
        }),
        base('ranking-main', 'RANKING', 15, {
          content: { mainField: '', subFields: [], series: [], columns: [], tabs: [], rankingMetrics: [
            { metricKey: 'deposit', field: 'deposit', label: '存款', unit: 'YUAN', direction: 'DESC' }
          ] }
        }),
        base('province-map', 'MAP', 16, {
          layoutRegion: 'CENTER',
          interaction: { action: 'OPEN_CITY' }
        }),
        base('detail-main', 'DETAIL_TABLE', 17, {
          content: { mainField: '', subFields: [], series: [], tabs: [], rankingMetrics: [], columns: [
            { columnKey: 'orgName', field: 'org_name', label: '机构', unit: 'AUTO', visible: true }
          ] }
        })
      ]
    }
  }
});

export const invalidDuplicateConfig = Object.freeze({
  type: 'CODE',
  template: 'branch-overview-v1',
  displaySchemaVersion: 1,
  institutionRules: {
    allowedOperatingLevels: ['PRIMARY'],
    allowedOrgNatures: ['SECONDARY_BRANCH']
  },
  display: {
    components: [
      {
        ...base('same-id', 'METRIC_CARD', 21),
        unknownConfig: true,
        format: { displayUnit: 'PERCENT', decimals: 2 },
        interaction: { action: 'OPEN_URL', target: 'https://example.invalid' },
        dataRefs: [ref(21, 'AMOUNT', '金额', 'YUAN'), ref(21, 'AMOUNT', '金额', 'YUAN')]
      },
      base('same-id', 'METRIC_CARD', 22)
    ]
  }
});
