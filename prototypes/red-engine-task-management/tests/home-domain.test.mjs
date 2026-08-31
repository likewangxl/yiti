import test from "node:test";
import assert from "node:assert/strict";

import {
  getBranchRankings,
  getHomeModel,
  getInstitutionMetrics,
} from "../src/home-domain.js";

test("组织角色首页提供完整的支部排名，并按排名升序返回", () => {
  const model = getHomeModel("admin");

  assert.equal(model.mode, "organization");
  assert.equal(model.showBranchRanking, true);
  assert.ok(Array.isArray(model.branchRankings));
  assert.ok(model.branchRankings.length >= 5);
  assert.deepEqual(Object.keys(model.branchRankings[0]).sort(), ["branchName", "rank", "score"]);
  assert.deepEqual(model.branchRankings.map((item) => item.rank), [1, 2, 3, 4, 5]);
  assert.equal("totalScore" in model, false);
  assert.equal("overallRank" in model, false);
});

test("组织审核员与组织管理员共用支部排名首页数据", () => {
  assert.deepEqual(getHomeModel("orgReviewer").branchRankings, getBranchRankings());
  assert.equal(getHomeModel("orgReviewer").mode, "organization");
});

test("报送员和支部书记首页展示所在机构得分与排名，不包含全员达标率", () => {
  for (const role of ["reporter", "branchSecretary"]) {
    const model = getHomeModel(role);
    const metrics = getInstitutionMetrics(role);

    assert.equal(model.mode, "institution");
    assert.equal(model.showInstitutionMetrics, true);
    assert.deepEqual(Object.keys(metrics).sort(), ["rank", "score"]);
    assert.equal(typeof metrics.score, "number");
    assert.equal(typeof metrics.rank, "string");
    assert.equal("allStaffRate" in model, false);
    assert.equal("allStaffQualificationRate" in model, false);
  }
});
