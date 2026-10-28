import { describe, expect, it } from "vitest";
import {
  buildTrendOption,
  filterHistory,
  getStableColor,
  metricColors,
  normalizeRange,
  orgShare,
  progressRate,
  finite,
} from "../personalAnalytics";

describe("personalAnalytics", () => {
  it("filters 7d history without inventing points and keeps gaps null", () => {
    const result = filterHistory(
      [
        { date: "2026-09-20", value: 10 },
        { date: "2026-09-22", value: 12 },
      ],
      "7d",
      "2026-09-22",
    );
    expect(result.map((point) => point.value)).toEqual([
      null,
      null,
      null,
      null,
      10,
      null,
      12,
    ]);
  });
  it("uses stable configured/fallback colors and growth percent for mixed units", () => {
    expect(getStableColor("x", "#123456")).toBe("#123456");
    expect(getStableColor("x")).toBe(getStableColor("x"));
    expect(
      buildTrendOption(
        [
          {
            metricCode: "a",
            metricName: "A",
            unit: "户",
            color: "#f00",
            history: [
              { date: "2026-01-01", value: 10 },
              { date: "2026-01-02", value: 20 },
            ],
          },
        ],
        "7d",
        "2026-01-02",
      ).series[0].data.slice(-2),
    ).toEqual([0, 100]);
  });
  it("does not calculate organization share when unit/date are inconsistent", () => {
    expect(
      orgShare({
        currentValue: 20,
        orgValue: 100,
        unit: "户",
        orgUnit: "万元",
        dataDate: "2026-01-01",
        orgDataDate: "2026-01-01",
      }),
    ).toBeNull();
    expect(
      orgShare({
        currentValue: 20,
        orgValue: 100,
        unit: "户",
        orgUnit: "户",
        dataDate: "2026-01-01",
        orgDataDate: "2026-01-01",
      }),
    ).toBe(20);
  });
  it("keeps unknown target denominator and allows achievement above 100", () => {
    expect(progressRate({ currentValue: 20, targetValue: 0 })).toBeNull();
    expect(progressRate({ currentValue: 120, targetValue: 100 })).toBe(120);
    expect(normalizeRange("bad")).toBe("7d");
  });
  it("strictly rejects blank values, invalid calendar dates and invalid end date", () => {
    expect(finite("  ")).toBeNull();
    expect(filterHistory([{ date: "2026-02-30", value: 1 }], "7d")).toEqual([]);
    expect(filterHistory([], "7d", "bad")).toEqual([]);
  });
  it("uses shared month buckets and actual values for selected metric", () => {
    const option = buildTrendOption(
      [
        { metricCode: "empty", metricName: "空", history: [] },
        {
          metricCode: "x",
          metricName: "X",
          history: [
            { date: "2026-01-20", value: 8 },
            { date: "2026-02-28", value: 10 },
          ],
        },
      ],
      "6m",
      "2026-02-28",
      "actual",
    );
    expect(option.xAxis.data).toContain("2026-01");
    expect(option.series[0].data).toHaveLength(option.xAxis.data.length);
    expect(option.series[1].data.slice(-2)).toEqual([8, 10]);
    expect(option.yAxis.name).toBe("实际值");
  });
  it("requires non-empty valid org units and dates, and allocates unique fallback colors", () => {
    expect(
      orgShare({
        currentValue: 1,
        orgValue: 2,
        unit: "",
        orgUnit: "",
        dataDate: "2026-01-01",
        orgDataDate: "2026-01-01",
      }),
    ).toBeNull();
    expect(
      orgShare({
        currentValue: 1,
        orgValue: 2,
        unit: "户",
        orgUnit: "户",
        dataDate: "2026-02-30",
        orgDataDate: "2026-02-30",
      }),
    ).toBeNull();
    expect(
      new Set(
        metricColors([
          { metricCode: "a" },
          { metricCode: "b" },
          { metricCode: "c" },
        ]),
      ).size,
    ).toBe(3);
  });
});
