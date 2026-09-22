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

describe('旧经营大屏配置迁移', () => {
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
