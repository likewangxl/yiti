import { describe, expect, it } from 'vitest';
import {
  MIGRATION_STATUS,
  applyLegacyMigrationToEditorDraft,
  previewLegacyMigration,
  rollbackLegacyMigration
} from '../migration/legacyPresentationMigration';
import { createPresentationEditorSession } from '../editor/presentationEditorModel';
import { validateDisplayConfig } from '../contract/displayContract';

function legacyPackage(overrides = {}) {
  return {
    schemaVersion: 2,
    canvasStyle: {
      presentation: { type: 'CODE', template: 'branch-overview-v1' },
      metricLabels: { deposit: '自定义存款' }
    },
    components: [
      {
        id: 'old-deposit-node',
        component: 'ChartWidget',
        innerType: 'METRIC_CARD',
        blockId: 41,
        propValue: { bindingKey: 'deposit' }
      },
      {
        id: 'old-unknown-node',
        component: 'ChartWidget',
        innerType: 'METRIC_CARD',
        blockId: 42,
        propValue: { bindingKey: 'not-in-contract' },
        title: '存款余额（不能用于推断）'
      },
      {
        id: 'old-no-snapshot',
        component: 'ChartWidget',
        innerType: 'METRIC_CARD',
        blockId: 43,
        propValue: { bindingKey: 'loan' }
      },
      {
        id: 'old-custom-sql',
        component: 'ChartWidget',
        innerType: 'METRIC_CARD',
        blockId: 44,
        propValue: { bindingKey: 'revenue' }
      }
    ],
    bindSnapshots: {
      41: {
        componentType: 'METRIC_CARD',
        bind: {
          dsId: 77,
          metricCode: 'M_DEP',
          metricName: '存款余额',
          fields: { value: 'deposit_raw' },
          units: { value: 'YUAN' },
          dimension: 'ORG'
        }
      },
      44: {
        componentType: 'METRIC_CARD',
        bind: {
          dsId: 78,
          sourceKind: 'CUSTOM_SQL',
          fields: { value: 'revenue_raw' },
          units: { value: 'YUAN' }
        }
      }
    },
    ...overrides
  };
}

function templateFixture(template) {
  const metric = (bindingKey, blockId, field = 'value', unit = 'YUAN') => ({
    component: { id: `old-${bindingKey}`, component: 'ChartWidget', innerType: 'METRIC_CARD', blockId,
      propValue: { bindingKey } },
    bind: { dsId: 8000 + blockId, fields: { value: field }, units: { value: unit }, sourceKind: 'WIDE_TABLE' }
  });
  const detail = (bindingKey, blockId, fields, units) => ({
    component: { id: `old-${bindingKey}`, component: 'ChartWidget', innerType: 'TABLE_LIST', blockId,
      propValue: { bindingKey } },
    bind: { dsId: 8000 + blockId, fields, units, sourceKind: 'WIDE_TABLE' }
  });
  const trend = (bindingKey, blockId, fields, units) => ({
    component: { id: `old-${bindingKey}`, component: 'ChartWidget', innerType: 'LINE_TREND', blockId,
      propValue: { bindingKey } },
    bind: { dsId: 8000 + blockId, fields, units, sourceKind: 'WIDE_TABLE' }
  });
  const composition = (bindingKey, blockId) => ({
    component: { id: `old-${bindingKey}`, component: 'ChartWidget', innerType: 'PIE_SHARE', blockId,
      propValue: { bindingKey } },
    bind: { dsId: 8000 + blockId, fields: { corporate: 'corp', retail: 'retail' },
      units: { corporate: 'YUAN', retail: 'YUAN' }, sourceKind: 'WIDE_TABLE' }
  });
  const ranking = (bindingKey, blockId, metric = 'value') => ({
    component: { id: `old-${bindingKey}`, component: 'ChartWidget', innerType: 'RANK_LIST', blockId,
      propValue: { bindingKey } },
    bind: { dsId: 8000 + blockId, fields: { orgCode: 'org_code', name: 'org_name', [metric]: metric },
      units: { [metric]: 'YUAN' }, direction: 'DESC', sourceKind: 'WIDE_TABLE' }
  });
  const branchIdentity = (bindingKey, blockId, metric = 'deposit') => detail(bindingKey, blockId,
    { orgCode: 'org_code', orgName: 'org_name', [metric]: metric }, { [metric]: 'YUAN' });

  const entries = template === 'branch-overview-v1'
    ? [
      metric('deposit', 1), metric('depositIncrease', 2, 'increase'), metric('depositAverage', 3, 'average'),
      metric('loan', 4, 'loan'), metric('customers', 5, 'customers', 'COUNT'), metric('revenue', 6, 'revenue'),
      metric('rate', 7, 'rate', 'PERCENT'), trend('trend', 8, { date: 'data_date', deposit: 'deposit' }, { deposit: 'YUAN' }),
      composition('composition', 9), ranking('ranking', 10),
      detail('attention', 11, { label: 'label', count: 'count' }, { count: 'COUNT' }), branchIdentity('branches', 12),
      trend('branchTrend', 13, { date: 'data_date', deposit: 'deposit' }, { deposit: 'YUAN' }),
      detail('citySummary', 14, { cityCode: 'city_code', deposit: 'deposit' }, { deposit: 'YUAN' }),
      metric('loanRate', 15, 'loan_rate', 'PERCENT')
    ]
    : template === 'corporate-overview-v1'
      ? [
        metric('corpDeposit', 21), metric('corpDepositAverage', 22, 'average'), metric('corpLoan', 23, 'loan'),
        metric('corpRevenue', 24, 'revenue'), metric('corpCustomers', 25, 'customers', 'COUNT'), metric('corpNplRate', 26, 'npl', 'PERCENT'),
        trend('corpTrend', 27, { date: 'data_date', deposit: 'deposit' }, { deposit: 'YUAN' }),
        detail('corpSegments', 28, { name: 'segment', customers: 'customers', loan: 'loan' }, { customers: 'COUNT', loan: 'YUAN' }),
        ranking('corpRanking', 29, 'deposit'), detail('corpAttention', 30, { label: 'label', count: 'count' }, { count: 'COUNT' }),
        detail('corpTargets', 31, { name: 'target_name', actual: 'actual', target: 'target' }, { actual: 'YUAN', target: 'YUAN' }),
        branchIdentity('branches', 32)
      ]
      : [
        metric('retailAum', 41, 'aum'), metric('retailDeposit', 42, 'deposit'), metric('retailDepositAverage', 43, 'average'),
        metric('retailRevenue', 44, 'revenue'), metric('retailValueCustomers', 45, 'customers', 'COUNT'), metric('retailLoan', 46, 'loan'),
        metric('retailNplRate', 47, 'npl', 'PERCENT'), trend('retailTrend', 48, { date: 'data_date', deposit: 'deposit' }, { deposit: 'YUAN' }),
        detail('retailSegments', 49, { name: 'segment', customers: 'customers', aum: 'aum' }, { customers: 'COUNT', aum: 'YUAN' }),
        ranking('retailRanking', 50, 'deposit'), detail('retailAttention', 51, { label: 'label', count: 'count' }, { count: 'COUNT' }),
        detail('retailTargets', 52, { name: 'target_name', actual: 'actual', target: 'target' }, { actual: 'YUAN', target: 'YUAN' }),
        branchIdentity('branches', 53)
      ];
  return {
    canvasStyle: { presentation: { type: 'CODE', template } },
    components: entries.map(item => item.component),
    blocks: entries.map(item => ({ id: item.component.blockId, componentType: item.component.innerType, bindJson: JSON.stringify(item.bind) }))
  };
}

describe('旧经营大屏配置迁移', () => {
  it('从画布 blocks 的 bindJson 构造可信快照，不依赖不存在的 bindSnapshots，也不猜标题', () => {
    const result = previewLegacyMigration({
      canvasStyle: { presentation: { type: 'CODE', template: 'branch-overview-v1' } },
      canvasDraftJson: JSON.stringify({ components: [{
        id: 'block-backed-deposit', component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 901,
        propValue: { bindingKey: 'deposit' }, styleJson: JSON.stringify({ title: '不能用作指标身份' })
      }] }),
      blocks: [{ id: 901, componentType: 'METRIC_CARD', bindJson: JSON.stringify({
        dsId: 7001, period: 'LATEST', sourceKind: 'WIDE_TABLE', metricCode: 'M_DEP', metricName: '存款余额',
        fields: { value: 'deposit_raw' }, units: { value: 'HUNDRED_MILLION' }, dimension: 'ORG'
      }) }]
    });

    expect(result.summary).toEqual({ migrated: 1, unresolved: 0, missingFields: 0, needsConfirmation: 0 });
    expect(result.presentation.display.components[0]).toMatchObject({
      componentType: 'METRIC_CARD',
      text: { titleMode: 'AUTO', title: '' },
      dataRefs: [expect.objectContaining({ blockId: 901, metricCode: 'M_DEP', unit: 'HUNDRED_MILLION' })]
    });
    expect(result.presentation.display.components[0].dataRefs[0]).not.toHaveProperty('title');
  });

  it('真实旧槽位映射到确定组件类型并生成可保存的完整展示草稿', () => {
    const metric = (bindingKey, blockId, unit = 'YUAN') => ({
      id: `old-${bindingKey}`, component: 'ChartWidget', innerType: 'METRIC_CARD', blockId,
      propValue: { bindingKey }
    });
    const components = [
      metric('depositIncrease', 911), metric('depositAverage', 912), metric('loanRate', 913),
      { id: 'old-city', component: 'ChartWidget', innerType: 'TABLE_LIST', blockId: 914,
        propValue: { bindingKey: 'citySummary' } },
      { id: 'old-branch-trend', component: 'ChartWidget', innerType: 'LINE_TREND', blockId: 915,
        propValue: { bindingKey: 'branchTrend' } }
    ];
    const blocks = [
      { id: 911, componentType: 'METRIC_CARD', bindJson: JSON.stringify({
        dsId: 7011, sourceKind: 'WIDE_TABLE', fields: { value: 'increase' }, units: { value: 'YUAN' }
      }) },
      { id: 912, componentType: 'METRIC_CARD', bindJson: JSON.stringify({
        dsId: 7012, sourceKind: 'WIDE_TABLE', fields: { value: 'average' }, units: { value: 'YUAN' }
      }) },
      { id: 913, componentType: 'METRIC_CARD', bindJson: JSON.stringify({
        dsId: 7013, sourceKind: 'KPI_DETAIL', fields: { value: 'loan_rate' }, units: { value: 'PERCENT' }
      }) },
      { id: 914, componentType: 'TABLE_LIST', bindJson: JSON.stringify({
        dsId: 7014, sourceKind: 'WIDE_TABLE', fields: { cityCode: 'city_code', deposit: 'deposit' },
        units: { deposit: 'YUAN' }
      }) },
      { id: 915, componentType: 'LINE_TREND', bindJson: JSON.stringify({
        dsId: 7015, sourceKind: 'WIDE_TABLE', fields: { date: 'data_date', deposit: 'deposit' },
        units: { deposit: 'YUAN' }
      }) }
    ];
    const result = previewLegacyMigration({
      canvasStyle: { presentation: { type: 'CODE', template: 'branch-overview-v1' } },
      components,
      blocks
    });

    expect(result.summary).toEqual({ migrated: 5, unresolved: 0, missingFields: 0, needsConfirmation: 0 });
    expect(result.presentation.display.components.map(item => [item.componentType, item.dataRefs[0].blockId]))
      .toEqual(expect.arrayContaining([
        ['METRIC_CARD', 911], ['METRIC_CARD', 912], ['METRIC_CARD', 913],
        ['DETAIL_TABLE', 914], ['TREND', 915]
      ]));
    expect(validateDisplayConfig(result.presentation)).toEqual([]);
  });

  it('真实旧 RANK_LIST 未写排序字段时沿用旧展示固定的降序语义', () => {
    const result = previewLegacyMigration({
      canvasStyle: { presentation: { type: 'CODE', template: 'branch-overview-v1' } },
      components: [{
        component: 'ChartWidget', innerType: 'RANK_LIST', blockId: 916,
        propValue: { bindingKey: 'ranking' }
      }],
      blocks: [{ id: 916, componentType: 'RANK_LIST', bindJson: JSON.stringify({
        dsId: 7016,
        fields: { orgCode: 'org_code', name: 'org_name', value: 'deposit_raw' },
        units: { value: 'YUAN' }
      }) }]
    });

    expect(result.summary).toEqual({ migrated: 1, unresolved: 0, missingFields: 0, needsConfirmation: 0 });
    const ranking = result.presentation.display.components.find(component => component.componentType === 'RANKING');
    const map = result.presentation.display.components.find(component => component.componentType === 'MAP');
    expect(ranking.content.rankingMetrics[0]).toMatchObject({
      field: 'deposit', unit: 'HUNDRED_MILLION', direction: 'DESC'
    });
    expect(map).toMatchObject({
      componentType: 'MAP',
      content: expect.objectContaining({ mainField: 'value' }),
      dataRefs: [expect.objectContaining({ blockId: 916, unit: 'YUAN' })]
    });
    expect(validateDisplayConfig(result.presentation)).toEqual([]);
  });

  it('仅含机构身份字段的 branches 由新协议机构规则接管，不伪造数值单位', () => {
    const result = previewLegacyMigration({
      canvasStyle: { presentation: { type: 'CODE', template: 'corporate-overview-v1' } },
      components: [{
        component: 'ChartWidget', innerType: 'TABLE_LIST', blockId: 917,
        propValue: { bindingKey: 'branches' }
      }],
      blocks: [{ id: 917, componentType: 'TABLE_LIST', bindJson: JSON.stringify({
        dsId: 7017,
        fields: { orgCode: 'org_code', orgName: 'org_name' },
        units: {}
      }) }]
    });

    expect(result.summary).toEqual({ migrated: 1, unresolved: 0, missingFields: 0, needsConfirmation: 0 });
    expect(result.migrated[0]).toMatchObject({
      bindingKey: 'branches',
      absorbedBy: 'institutionRules',
      reasons: [expect.stringMatching(/机构规则/)]
    });
    expect(result.presentation.display.components).toEqual([]);
    expect(validateDisplayConfig(result.presentation)).toEqual([]);
  });

  it.each(['branch-overview-v1', 'corporate-overview-v1', 'retail-overview-v1'])
    ('分行/对公/零售真实槽位夹具均可生成完整迁移草稿：%s', template => {
      const result = previewLegacyMigration(templateFixture(template));
      expect(result.summary.unresolved).toBe(0);
      expect(result.summary.missingFields).toBe(0);
      expect(result.summary.needsConfirmation).toBe(0);
      expect(result.presentation.display.components.length).toBeGreaterThan(0);
    });

  it('blocks.bindJson 损坏时 fail-close，不使用标题或其他字段补造绑定', () => {
    const result = previewLegacyMigration({
      canvasDraftJson: JSON.stringify({ components: [{
        id: 'broken-bind', component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 902,
        propValue: { bindingKey: 'deposit' }, styleJson: JSON.stringify({ title: '存款余额' })
      }] }),
      blocks: [{ id: 902, componentType: 'METRIC_CARD', bindJson: '{"dsId":7002,' }]
    });

    expect(result.summary).toEqual({ migrated: 0, unresolved: 0, missingFields: 1, needsConfirmation: 0 });
    expect(result.presentation.display.components).toEqual([]);
    expect(result.missingFields[0].reasons.join('；')).toMatch(/bindJson/);
  });

  it('按编码和可信绑定快照迁移，不按中文标题猜测，并显示四类状态', () => {
    const result = previewLegacyMigration(legacyPackage());

    expect(result.summary).toEqual({ migrated: 1, unresolved: 1, missingFields: 1, needsConfirmation: 1 });
    expect(result.migrated).toHaveLength(1);
    expect(result.migrated[0]).toMatchObject({
      status: MIGRATION_STATUS.MIGRATED,
      bindingKey: 'deposit',
      blockId: 41
    });
    expect(result.migrated[0].component).toMatchObject({
      componentId: 'legacy-deposit-41',
      componentType: 'METRIC_CARD',
      text: expect.objectContaining({ titleMode: 'CUSTOM', title: '自定义存款' }),
      dataRefs: [expect.objectContaining({ blockId: 41, metricCode: 'M_DEP', unit: 'YUAN' })]
    });
    expect(result.unresolved[0]).toMatchObject({
      status: MIGRATION_STATUS.UNRESOLVED,
      bindingKey: 'not-in-contract',
      legacyComponent: expect.objectContaining({ title: '存款余额（不能用于推断）' })
    });
    expect(result.missingFields[0]).toMatchObject({
      status: MIGRATION_STATUS.MISSING_FIELDS,
      bindingKey: 'loan'
    });
    expect(result.needsConfirmation[0]).toMatchObject({
      status: MIGRATION_STATUS.NEEDS_CONFIRMATION,
      bindingKey: 'revenue'
    });
    expect(result.canDeclareLossless).toBe(false);
    expect(result.unmappedItems).toHaveLength(3);
  });

  it('重复转换产生相同组件和回退快照，旧输入保持不变', () => {
    const source = legacyPackage();
    const first = previewLegacyMigration(source);
    const second = previewLegacyMigration(first.presentation);

    expect(second.presentation).toEqual(first.presentation);
    expect(second.presentation).not.toBe(first.presentation);
    expect(second.idempotent).toBe(true);
    expect(first.rollbackSource).toEqual(source);
    expect(source.components[0].propValue.bindingKey).toBe('deposit');
  });

  it('迁移出的完整组件树符合 displaySchemaVersion=1 契约', () => {
    const result = previewLegacyMigration(legacyPackage({
      components: [
        { id: 'trend', component: 'ChartWidget', innerType: 'LINE_TREND', blockId: 61,
          propValue: { bindingKey: 'trend' } },
        { id: 'composition', component: 'ChartWidget', innerType: 'PIE_SHARE', blockId: 62,
          propValue: { bindingKey: 'composition' } },
        { id: 'detail', component: 'ChartWidget', innerType: 'TABLE_LIST', blockId: 63,
          propValue: { bindingKey: 'attention' } }
      ],
      bindSnapshots: {
        61: { bind: { fields: { date: 'data_date', deposit: 'deposit_raw' }, units: { deposit: 'YUAN' } } },
        62: { bind: { fields: { corporate: 'corp_raw', retail: 'retail_raw' }, units: { corporate: 'YUAN', retail: 'YUAN' } } },
        63: { bind: { fields: { orgCode: 'org_code', value: 'amount' }, units: { orgCode: 'COUNT', value: 'YUAN' } } }
      }
    }));

    expect(result.migrated).toHaveLength(3);
    expect(validateDisplayConfig(result.presentation)).toEqual([]);
  });

  it('缺少 bindingKey、blockId、字段或单位时不生成伪造数据', () => {
    const source = legacyPackage({
      components: [
        { id: 'missing-key', component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 51, propValue: {} },
        { id: 'missing-block', component: 'ChartWidget', innerType: 'METRIC_CARD', propValue: { bindingKey: 'deposit' } },
        { id: 'missing-unit', component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: 53, propValue: { bindingKey: 'deposit' } }
      ],
      bindSnapshots: {
        53: { bind: { fields: { value: 'deposit_raw' }, units: {} } }
      }
    });
    const result = previewLegacyMigration(source);

    expect(result.summary).toEqual({ migrated: 0, unresolved: 0, missingFields: 3, needsConfirmation: 0 });
    expect(result.presentation.display.components).toEqual([]);
    expect(result.missingFields.every(item => item.component === undefined)).toBe(true);
  });

  it('接受画布 API 的 JSON 包装形态，但仍只从 bindSnapshots 取绑定身份', () => {
    const source = {
      canvasStyleJson: JSON.stringify({ presentation: { type: 'CODE', template: 'retail-overview-v1' }, metricLabels: { retailAum: '零售资产' } }),
      canvasDraftJson: JSON.stringify({ components: [{ id: 'retail', component: 'ChartWidget', innerType: 'METRIC_CARD', blockId: '71',
        propValue: { bindingKey: 'retailAum' } }] }),
      bindSnapshots: JSON.stringify({ 71: { bind: { fields: { value: 'aum_raw' }, units: { value: 'HUNDRED_MILLION_YUAN' } } } })
    };
    const result = previewLegacyMigration(source);

    expect(result.summary.migrated).toBe(1);
    expect(result.presentation.template).toBe('retail-overview-v1');
    expect(result.presentation.display.components[0]).toMatchObject({
      componentId: 'legacy-retailaum-71',
      text: { titleMode: 'CUSTOM', title: '零售资产' },
      dataRefs: [expect.objectContaining({ blockId: 71, unit: 'HUNDRED_MILLION' })]
    });
  });

  it('损坏的 components 结构进入缺字段而不是宣称无损完成', () => {
    const result = previewLegacyMigration({ components: { id: 'not-an-array' }, bindSnapshots: {} });
    expect(result.summary.missingFields).toBe(1);
    expect(result.canDeclareLossless).toBe(false);
    expect(result.presentation.display.components).toEqual([]);
  });

  it('损坏 JSON 和 requireLossless 都 fail-close，不把部分草稿当成无损迁移', () => {
    const malformed = previewLegacyMigration('{"components":');
    expect(malformed.summary.missingFields).toBe(1);
    expect(malformed.canDeclareLossless).toBe(false);

    const partial = previewLegacyMigration(legacyPackage());
    const session = createPresentationEditorSession({ type: 'CODE', template: 'branch-overview-v1' });
    expect(() => applyLegacyMigrationToEditorDraft(session, partial, { requireLossless: true }))
      .toThrow(/无损迁移/);
  });

  it('只把迁移结果应用到当前编辑器草稿，取消恢复原快照且不产生持久化副作用', () => {
    const session = createPresentationEditorSession({ type: 'CODE', template: 'branch-overview-v1' });
    const result = previewLegacyMigration(legacyPackage());
    const applied = applyLegacyMigrationToEditorDraft(session, result);

    expect(applied.dirty).toBe(true);
    expect(applied.presentation.display.components).toHaveLength(1);
    expect(applied.loadedSnapshot.display.components).toEqual([]);
    expect(applied.migrationPreview).toBeUndefined();

    const cancelled = rollbackLegacyMigration(applied);
    expect(cancelled.presentation.display.components).toEqual([]);
    expect(cancelled.dirty).toBe(false);
  });
});
