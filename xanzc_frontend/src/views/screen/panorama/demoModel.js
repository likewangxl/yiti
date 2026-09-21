/**
 * 本地视觉演示模型。
 *
 * 这里的数字和坐标只为开发态视觉验收服务，运行态不得 import 本文件。每条
 * 机构记录都带有 demoOnly 标识；坐标使用 GCJ-02 近似值，不能解释为真实网点
 * 位置或业务数据。
 */

const CITY_FIXTURES = Object.freeze([
  { code: '610100', name: '西安市', center: [108.9402, 34.3416], deposit: 412.82, loan: 310.10, customers: 61.12, revenue: 10.22, rate: 90.2, branchCount: 12 },
  { code: '610200', name: '铜川市', center: [109.067, 35.071], deposit: 54.16, loan: 40.80, customers: 8.42, revenue: 1.32, rate: 82.1, branchCount: 4 },
  { code: '610300', name: '宝鸡市', center: [107.144, 34.369], deposit: 105.37, loan: 80.20, customers: 14.36, revenue: 2.68, rate: 88.4, branchCount: 3 },
  { code: '610400', name: '咸阳市', center: [108.708, 34.329], deposit: 144.21, loan: 107.50, customers: 20.31, revenue: 3.54, rate: 87.6, branchCount: 4 },
  { code: '610500', name: '渭南市', center: [109.510, 34.499], deposit: 126.80, loan: 95.20, customers: 18.27, revenue: 3.16, rate: 86.8, branchCount: 3 },
  { code: '610600', name: '延安市', center: [109.490, 36.596], deposit: 85.42, loan: 65.78, customers: 12.65, revenue: 2.14, rate: 84.2, branchCount: 3 },
  { code: '610700', name: '汉中市', center: [107.028, 33.077], deposit: 93.13, loan: 72.42, customers: 13.71, revenue: 2.33, rate: 85.9, branchCount: 4 },
  { code: '610800', name: '榆林市', center: [109.734, 38.285], deposit: 128.44, loan: 96.60, customers: 17.96, revenue: 3.05, rate: 87.3, branchCount: 4 },
  { code: '610900', name: '安康市', center: [109.029, 32.684], deposit: 73.17, loan: 53.10, customers: 10.42, revenue: 1.54, rate: 83.5, branchCount: 3 },
  { code: '611000', name: '商洛市', center: [109.940, 33.870], deposit: 62.90, loan: 46.65, customers: 9.02, revenue: 2.70, rate: 81.9, branchCount: 3 }
]);

const TREND_MONTHS = Object.freeze(['2026-04', '2026-05', '2026-06', '2026-07', '2026-08', '2026-09']);
const TREND_FACTORS = Object.freeze([0.914, 0.931, 0.948, 0.966, 0.982, 1]);
// 独立于贷款/手工测试收入示意因子的存款余额序列；首月净增相对声明的上月基期计算。
const DEPOSIT_BASELINE = 1270.50;
const DEPOSIT_BALANCE_TREND = Object.freeze([1268.12, 1261.40, 1277.86, 1293.08, 1288.30, 1286.42]);
// 独立的月内平均余额演示值，不从月末趋势点平均推导。
const DEPOSIT_AVERAGE_FIXTURE = 1287.26;

function round(value, digits = 2) {
  const scale = 10 ** digits;
  return Math.round((Number(value) + Number.EPSILON) * scale) / scale;
}

function splitTotal(total, count) {
  const size = Math.max(1, Number(count) || 1);
  const part = round(Number(total) / size);
  const values = Array.from({ length: size }, () => part);
  values[size - 1] = round(Number(total) - values.slice(0, -1).reduce((sum, value) => sum + value, 0));
  return values;
}

function trendFor(metrics) {
  return TREND_MONTHS.map((date, index) => ({
    date,
    deposit: round(metrics.deposit * TREND_FACTORS[index]),
    loan: round(metrics.loan * TREND_FACTORS[index]),
    revenue: round(metrics.revenue * TREND_FACTORS[index])
  }));
}

function depositIncreaseAt(index) {
  const previous = index === 0 ? DEPOSIT_BASELINE : DEPOSIT_BALANCE_TREND[index - 1];
  return round(DEPOSIT_BALANCE_TREND[index] - previous);
}

function depositGrowthPercentAt(index) {
  const previous = index === 0 ? DEPOSIT_BASELINE : DEPOSIT_BALANCE_TREND[index - 1];
  return round((DEPOSIT_BALANCE_TREND[index] - previous) / previous * 100, 1);
}

function cityKpis(city) {
  return [
    { key: 'deposit', label: '存款余额', value: city.deposit, unit: '亿元', change: 4.8 },
    { key: 'loan', label: '贷款余额', value: city.loan, unit: '亿元', change: 3.9 },
    { key: 'customers', label: '营销有效归属客户数', value: city.customers, unit: '万户', change: 2.6 },
    { key: 'rate', label: '目标完成率', value: city.rate, unit: '%', change: null }
  ];
}

function coordinatesFor(city, index) {
  // 西安前五个点刻意放在近邻范围，便于验收聚合点；其余城市使用城市中心附近示意点。
  if (city.code === '610100') {
    const close = [
      [108.9400, 34.3400], [108.9405, 34.3404], [108.9410, 34.3408],
      [108.9416, 34.3412], [108.9421, 34.3416]
    ];
    if (close[index]) return close[index];
    const ring = index - close.length + 1;
    return [round(city.center[0] + 0.018 * ring, 6), round(city.center[1] + 0.012 * (ring % 3), 6)];
  }
  const offset = index - ((city.branchCount - 1) / 2);
  return [round(city.center[0] + offset * 0.008, 6), round(city.center[1] + offset * 0.006, 6)];
}

function isMissingCoordinate(cityCode, index) {
  return (cityCode === '610200' && index === 3)
    || (cityCode === '610900' && index === 1)
    || (cityCode === '611000' && index === 2);
}

function createInstitutions() {
  const institutions = [];
  for (const city of CITY_FIXTURES) {
    const deposits = splitTotal(city.deposit, city.branchCount);
    const loans = splitTotal(city.loan, city.branchCount);
    const customers = splitTotal(city.customers, city.branchCount);
    const revenues = splitTotal(city.revenue, city.branchCount);
    for (let index = 0; index < city.branchCount; index += 1) {
      const missingCoordinate = isMissingCoordinate(city.code, index);
      const metrics = {
        deposit: deposits[index],
        loan: loans[index],
        customers: customers[index],
        revenue: revenues[index],
        target: null,
        rate: round(city.rate - (index % 3) * 1.1, 1)
      };
      const coordinate = missingCoordinate ? null : coordinatesFor(city, index);
      institutions.push({
        orgCode: `DEMO-${city.code}-${String(index + 1).padStart(2, '0')}`,
        orgName: `${city.name}${index + 1}号支行`,
        cityCode: city.code,
        cityName: city.name,
        parentOrgCode: `DEMO-CITY-${city.code}`,
        lng: coordinate?.[0] ?? null,
        lat: coordinate?.[1] ?? null,
        coordSys: 'GCJ02',
        located: !missingCoordinate,
        demoOnly: true,
        demo: true,
        isDemo: true,
        metrics,
        trend: trendFor(metrics),
        attention: index === 0 || metrics.rate < 84
          ? [{ label: metrics.rate < 84 ? '目标进度偏慢' : '重点客户跟进', count: index + 1 }]
          : []
      });
    }
  }
  return institutions;
}

function aggregateTrend(cities, key) {
  return TREND_MONTHS.map((date, index) => ({
    date,
    [key]: round(cities.reduce((sum, city) => sum + Number(city[key]) * TREND_FACTORS[index], 0))
  }));
}

function createCitySummaries(institutions) {
  return Object.fromEntries(CITY_FIXTURES.map(city => {
    const cityInstitutions = institutions.filter(item => item.cityCode === city.code);
    return [city.code, {
      cityCode: city.code,
      cityName: city.name,
      title: `${city.name} · 支行经营全景`,
      dataDate: '2026-09-06',
      // 汇总直接来自本市支行唯一子集；没有把城市父节点再放入 institutions。
      aggregation: 'exclusive-child-branches',
      branchCodes: cityInstitutions.map(item => item.orgCode),
      kpis: cityKpis(city),
      trend: [
        ...aggregateTrend([city], 'deposit'),
        ...aggregateTrend([city], 'loan')
      ].reduce((rows, row) => {
        const current = rows.find(item => item.date === row.date);
        if (current) Object.assign(current, row);
        else rows.push({ ...row });
        return rows;
      }, [])
    }];
  }));
}

const institutions = createInstitutions();
const citySummaries = createCitySummaries(institutions);

export const demoCities = CITY_FIXTURES;

export const demoModel = Object.freeze({
  demo: true,
  demoOnly: true,
  dataOrigin: 'LOCAL_DEMO_FIXTURE',
  disclaimer: '本地演示 · 非业务数据；坐标为 GCJ-02 近似示意，不代表真实网点位置。',
  title: '分行经营总览',
  dataDate: '2026-09-06',
  kpis: [
    { key: 'deposit', label: '存款余额', value: 1286.42, unit: '亿元', change: depositGrowthPercentAt(DEPOSIT_BALANCE_TREND.length - 1) },
    { key: 'loan', label: '贷款余额', value: 968.35, unit: '亿元', change: 5.4 },
    { key: 'customers', label: '营销有效归属客户数', value: 186.24, unit: '万户', change: 4.1 },
    { key: 'revenue', label: '手工测试收入', value: 32.68, unit: '亿元', change: 8.1 },
    { key: 'rate', label: '目标完成率', value: 86.5, unit: '%', change: null },
    { key: 'corporateDepositRate', label: '对公存款目标完成率', value: 93.6, unit: '%', date: '2026-09-06', change: null },
    { key: 'corporateLoanRate', label: '对公贷款目标完成率', value: 88.2, unit: '%', date: '2026-09-06', change: null },
    { key: 'corporateRevenueRate', label: '对公营业收入目标完成率', value: 91.8, unit: '%', date: '2026-09-06', change: null },
    { key: 'depositIncrease', label: '存款较上月净增', value: depositIncreaseAt(DEPOSIT_BALANCE_TREND.length - 1), unit: '亿元', change: null },
    { key: 'depositAverage', label: '存款月均余额', value: DEPOSIT_AVERAGE_FIXTURE, unit: '亿元', change: null }
  ],
  trend: TREND_MONTHS.map((date, index) => ({
    date,
    deposit: DEPOSIT_BALANCE_TREND[index],
    loan: round(968.35 * TREND_FACTORS[index]),
    revenue: round(32.68 * TREND_FACTORS[index]),
    depositIncrease: depositIncreaseAt(index)
  })),
  composition: [
    { name: '对公业务', value: 714.26, unit: '亿元' },
    { name: '零售业务', value: 572.16, unit: '亿元' }
  ],
  rankings: institutions
    .map((institution, index) => ({
      // 排名身份必须来自支行目录，不能把城市父节点伪装成支行。
      orgCode: institution.orgCode,
      name: institution.orgName,
      cityCode: institution.cityCode,
      cityName: institution.cityName,
      deposit: institution.metrics.deposit,
      increase: round(institution.metrics.deposit * (0.012 + (index % 4) * 0.006)),
      average: round(institution.metrics.deposit * (0.946 + (index % 3) * 0.009)),
      change: round(2.4 + (index % 5) * 0.7, 1)
    }))
    .sort((left, right) => right.deposit - left.deposit),
  attention: [
    { label: '客户授信调查任务在途', count: 48, orgCode: institutions[0]?.orgCode, orgName: institutions[0]?.orgName },
    { label: '营销方案等待审批', count: 18, orgCode: institutions[1]?.orgCode, orgName: institutions[1]?.orgName },
    { label: '客户回访任务即将到期', count: 6, orgCode: institutions[2]?.orgCode, orgName: institutions[2]?.orgName },
    { label: '贷后检查任务已超时', count: 3, orgCode: institutions[3]?.orgCode, orgName: institutions[3]?.orgName },
    { label: '存款目标进度偏慢待跟进', count: 4, orgCode: institutions[4]?.orgCode, orgName: institutions[4]?.orgName }
  ],
  issues: [],
  institutions,
  citySummaries,
  // 仅供需要省级父节点的演示布局使用；不参与 institutions 汇总，避免父子重复计算。
  cityNodes: CITY_FIXTURES.map(city => ({
    orgCode: `DEMO-CITY-${city.code}`,
    orgName: `${city.name}分行`,
    cityCode: city.code,
    lng: city.center[0],
    lat: city.center[1],
    coordSys: 'GCJ02',
    located: true,
    demoOnly: true,
    demo: true,
    isDemo: true,
    metrics: { deposit: city.deposit, loan: city.loan, customers: city.customers, revenue: city.revenue, rate: city.rate }
  }))
});

export default demoModel;
