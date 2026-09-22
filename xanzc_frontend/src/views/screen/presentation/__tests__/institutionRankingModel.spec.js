import { describe, expect, it } from 'vitest';
import { buildInstitutionRankingModel } from '../model/institutionRankingModel';

const metricConfig = [
  { metricKey: 'deposit', field: 'deposit', label: '存款余额', unit: '亿元', direction: 'DESC' },
  { metricKey: 'increase', field: 'increase', label: '较上月净增', unit: '亿元', direction: 'ASC' }
];

const directory = codes => codes.map(orgCode => ({
  orgCode,
  orgName: `机构${orgCode}`,
  active: true,
  authorized: true
}));

describe('institutionRankingModel', () => {
  it.each([0, 1, 7, 23, 200])('保留全部授权机构，不因旧TOP10限制截断（%i家）', expectedCount => {
    const institutions = directory(Array.from({ length: expectedCount }, (_, index) => `ORG-${index + 1}`));
    const rows = institutions.map((item, index) => ({ orgCode: item.orgCode, deposit: index }));
    const result = buildInstitutionRankingModel({ institutions, rows, rankingMetrics: metricConfig });

    expect(result.expected).toHaveLength(expectedCount);
    expect(result.received).toHaveLength(expectedCount);
    expect(result.rankable).toHaveLength(expectedCount);
    expect(result.rows).toHaveLength(expectedCount);
    expect(result.rows.some(item => item.orgCode === `ORG-${expectedCount}`)).toBe(expectedCount > 0);
  });

  it('按声明方向排序，合法0和负数参与排名，并使用竞赛排名', () => {
    const result = buildInstitutionRankingModel({
      institutions: directory(['A', 'B', 'C', 'D']),
      rows: [
        { orgCode: 'A', deposit: 0 },
        { orgCode: 'B', deposit: -2 },
        { orgCode: 'C', deposit: 10 },
        { orgCode: 'D', deposit: 10 }
      ],
      rankingMetrics: [metricConfig[0]]
    });

    expect(result.rankable.map(item => item.orgCode)).toEqual(['C', 'D', 'A', 'B']);
    expect(result.rankable.map(item => item.rank)).toEqual([1, 1, 3, 4]);
    expect(result.rankable.map(item => item.value)).toEqual([10, 10, 0, -2]);
  });

  it('并列时以orgCode作稳定次序，缺数机构单列未参与', () => {
    const result = buildInstitutionRankingModel({
      institutions: directory(['B', 'A', 'C', 'D']),
      rows: [
        { orgCode: 'B', deposit: 5 },
        { orgCode: 'A', deposit: 5 },
        { orgCode: 'C', deposit: null }
      ],
      rankingMetrics: [metricConfig[0]]
    });

    expect(result.rankable.map(item => item.orgCode)).toEqual(['A', 'B']);
    expect(result.rankable.map(item => item.rank)).toEqual([1, 1]);
    expect(result.missing.map(item => item.orgCode)).toEqual(['C', 'D']);
    expect(result.missing.every(item => item.state === 'MISSING')).toBe(true);
    expect(result.summary).toContain('已获得 3/4 家');
    expect(result.complete).toBe(false);
  });

  it('重复机构拒绝进入榜单并标记问题，未授权返回也不污染覆盖率', () => {
    const result = buildInstitutionRankingModel({
      institutions: directory(['A', 'B']),
      rows: [
        { orgCode: 'A', deposit: 1 },
        { orgCode: 'A', deposit: 2 },
        { orgCode: 'B', deposit: 0 },
        { orgCode: 'OUTSIDE', deposit: 99 }
      ],
      rankingMetrics: [metricConfig[0]]
    });

    expect(result.rankable.map(item => item.orgCode)).toEqual(['B']);
    expect(result.missing.map(item => item.orgCode)).toContain('A');
    expect(result.issues.map(item => item.code)).toEqual(expect.arrayContaining(['DUPLICATE_ORG', 'UNAUTHORIZED_ORG']));
    expect(result.received).toHaveLength(1);
    expect(result.incomplete).toBe(true);
  });

  it('仅存在目录外返回行时也不能宣称授权目录完整覆盖', () => {
    const result = buildInstitutionRankingModel({
      institutions: directory(['A', 'B']),
      rows: [
        { orgCode: 'A', deposit: 1 },
        { orgCode: 'B', deposit: 2 },
        { orgCode: 'OUTSIDE', deposit: 99 }
      ],
      rankingMetrics: [metricConfig[0]]
    });

    expect(result.receivedCount).toBe(2);
    expect(result.expectedCount).toBe(2);
    expect(result.incomplete).toBe(true);
    expect(result.summary).toContain('数据不完整');
    expect(result.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'UNAUTHORIZED_ORG', orgCode: 'OUTSIDE' })
    ]));
  });

  it('排名返回行缺少orgCode时拒绝该行并标记返回不完整', () => {
    const result = buildInstitutionRankingModel({
      institutions: directory(['A', 'B']),
      rows: [
        { orgCode: 'A', deposit: 1 },
        { orgCode: 'B', deposit: 2 },
        { deposit: 99 }
      ],
      rankingMetrics: [metricConfig[0]]
    });

    expect(result.rankable.map(item => item.orgCode)).toEqual(['B', 'A']);
    expect(result.incomplete).toBe(true);
    expect(result.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'MISSING_ORG_CODE' })
    ]));
  });

  it('缺少指标来源时不伪造总分榜，并保留指标候选', () => {
    const result = buildInstitutionRankingModel({
      institutions: directory(['A', 'B']),
      rows: [{ orgCode: 'A', deposit: 1 }, { orgCode: 'B', deposit: 2 }],
      rankingMetrics: [
        { metricKey: 'score', field: 'score', label: '综合得分', unit: '分', direction: 'DESC' }
      ]
    });

    expect(result.rankable).toHaveLength(0);
    expect(result.missing).toHaveLength(2);
    expect(result.metric.available).toBe(false);
    expect(result.issues.map(item => item.code)).toContain('NO_METRIC_VALUES');
  });

  it('支持配置声明的多指标并为每个指标独立计算覆盖与排名', () => {
    const result = buildInstitutionRankingModel({
      institutions: directory(['A', 'B']),
      rows: [{ orgCode: 'A', deposit: 1, increase: -3 }, { orgCode: 'B', deposit: 2, increase: 4 }],
      rankingMetrics: metricConfig
    });

    expect(result.metrics).toHaveLength(2);
    expect(result.metrics[0].rankable.map(item => item.orgCode)).toEqual(['B', 'A']);
    expect(result.metrics[1].rankable.map(item => item.orgCode)).toEqual(['A', 'B']);
    expect(result.activeMetricKey).toBe('deposit');
  });

  it('授权目录缺少orgCode时拒绝该记录，不生成row-N身份并标记目录不完整', () => {
    const result = buildInstitutionRankingModel({
      institutions: [{ orgName: '无编码机构', active: true, authorized: true }],
      rows: [{ orgCode: 'row-0', deposit: 99 }],
      rankingMetrics: [metricConfig[0]]
    });

    expect(result.expected).toEqual([]);
    expect(result.rows).toEqual([]);
    expect(result.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'ORG_CODE_MISSING' })
    ]));
    expect(result.incomplete).toBe(true);
    expect(result.expected.concat(result.rows).some(item => item.orgCode === 'row-0')).toBe(false);
  });

  it('没有指标配置时，目录异常也不能回退为完整状态', () => {
    const result = buildInstitutionRankingModel({
      institutions: [{ orgName: '无编码机构', active: true, authorized: true }],
      rows: []
    });

    expect(result.incomplete).toBe(true);
    expect(result.complete).toBe(false);
    expect(result.summary).toContain('数据不完整');
  });

  it('调用方标明sourceAuthorized时，无逐行flags可沿用服务端授权目录，显式false仍排除', () => {
    const institutions = [
      { orgCode: 'NO_FLAGS', orgName: '未确认', },
      { orgCode: 'INACTIVE', orgName: '已停用', active: false, authorized: true },
      { orgCode: 'UNAUTHORIZED', orgName: '未授权', active: true, authorized: false },
      { orgCode: 'OK', orgName: '有效', active: true, authorized: true }
    ];
    const result = buildInstitutionRankingModel({
      institutions,
      rows: institutions.map(item => ({ orgCode: item.orgCode, deposit: 1 })),
      sourceAuthorized: true,
      rankingMetrics: [metricConfig[0]]
    });

    expect(result.expected.map(item => item.orgCode)).toEqual(['NO_FLAGS', 'OK']);
    expect(result.rankable.map(item => item.orgCode)).toEqual(['NO_FLAGS', 'OK']);
    expect(result.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'INACTIVE', orgCode: 'INACTIVE' }),
      expect.objectContaining({ code: 'UNAUTHORIZED', orgCode: 'UNAUTHORIZED' })
    ]));
    expect(result.incomplete).toBe(true);
  });

  it('authorizedDirectory本身表示服务端已授权目录，无逐行flags可排名', () => {
    const result = buildInstitutionRankingModel({
      authorizedDirectory: [{ orgCode: 'A', orgName: '甲' }, { orgCode: 'B', orgName: '乙' }],
      rows: [{ orgCode: 'A', deposit: 1 }, { orgCode: 'B', deposit: 2 }],
      rankingMetrics: [metricConfig[0]]
    });

    expect(result.rankable.map(item => item.orgCode)).toEqual(['B', 'A']);
    expect(result.incomplete).toBe(false);
  });

  it('没有服务端授权上下文且目录flags缺失时仍fail-close，不猜测放行', () => {
    const result = buildInstitutionRankingModel({
      institutions: [{ orgCode: 'A', orgName: '甲' }],
      rows: [{ orgCode: 'A', deposit: 1 }],
      rankingMetrics: [metricConfig[0]]
    });

    expect(result.rankable).toEqual([]);
    expect(result.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'DIRECTORY_STATUS_UNCONFIRMED', orgCode: 'A' })
    ]));
  });

  it.each([
    ['limit', { limit: 2 }, 'RESULT_LIMIT_REACHED'],
    ['hasMore', { hasMore: true }, 'RESULT_HAS_MORE'],
    ['truncated', { truncated: true }, 'RESULT_TRUNCATED']
  ])('返回达到%s声明时必须标记指标不完整', (_label, responseMeta, issueCode) => {
    const result = buildInstitutionRankingModel({
      institutions: [
        { orgCode: 'A', orgName: '甲', active: true, authorized: true },
        { orgCode: 'B', orgName: '乙', active: true, authorized: true }
      ],
      rows: [{ orgCode: 'A', deposit: 1 }, { orgCode: 'B', deposit: 2 }],
      responseMeta,
      rankingMetrics: [metricConfig[0]]
    });

    expect(result.metric.incomplete).toBe(true);
    expect(result.complete).toBe(false);
    expect(result.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: issueCode })
    ]));
    expect(result.summary).toContain('不完整');
  });

  it('支持返回对象直接声明limit/hasMore/truncated并保持不完整标记', () => {
    const result = buildInstitutionRankingModel({
      institutions: [
        { orgCode: 'A', orgName: '甲', active: true, authorized: true },
        { orgCode: 'B', orgName: '乙', active: true, authorized: true }
      ],
      rows: [{ orgCode: 'A', deposit: 1 }, { orgCode: 'B', deposit: 2 }],
      limit: 2,
      hasMore: false,
      truncated: false,
      rankingMetrics: [metricConfig[0]]
    });

    expect(result.metric.coverage.limitReached).toBe(true);
    expect(result.metric.incomplete).toBe(true);
    expect(result.issues).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'RESULT_LIMIT_REACHED' })
    ]));
  });

  it('合并响应元数据与顶层limit，不能因部分元数据对象而丢失截断提示', () => {
    const result = buildInstitutionRankingModel({
      institutions: [
        { orgCode: 'A', orgName: '甲', active: true, authorized: true },
        { orgCode: 'B', orgName: '乙', active: true, authorized: true }
      ],
      rows: [{ orgCode: 'A', deposit: 1 }, { orgCode: 'B', deposit: 2 }],
      responseMeta: { hasMore: false },
      limit: 2,
      rankingMetrics: [metricConfig[0]]
    });

    expect(result.metric.coverage.limitReached).toBe(true);
    expect(result.metric.incomplete).toBe(true);
  });

  it('识别顶层coverage中的截断声明', () => {
    const result = buildInstitutionRankingModel({
      institutions: [{ orgCode: 'A', orgName: '甲', active: true, authorized: true }],
      rows: [{ orgCode: 'A', deposit: 1 }],
      coverage: { truncated: true },
      rankingMetrics: [metricConfig[0]]
    });

    expect(result.metric.coverage.truncated).toBe(true);
    expect(result.metric.incomplete).toBe(true);
  });
});
