const ORGANIZATION_ROLES = new Set(["admin", "orgReviewer"]);

const INSTITUTION_METRICS = Object.freeze({
  reporter: Object.freeze({ score: 92.5, rank: "3/12" }),
  branchSecretary: Object.freeze({ score: 89.8, rank: "5/12" }),
});

const BRANCH_RANKINGS = Object.freeze([
  Object.freeze({ branchName: "党支部一", score: 96.5, rank: 1 }),
  Object.freeze({ branchName: "党支部二", score: 94.0, rank: 2 }),
  Object.freeze({ branchName: "党支部三", score: 92.5, rank: 3 }),
  Object.freeze({ branchName: "党支部四", score: 89.7, rank: 4 }),
  Object.freeze({ branchName: "党支部五", score: 87.2, rank: 5 }),
]);

export function getHomeMode(role) {
  return ORGANIZATION_ROLES.has(role) ? "organization" : "institution";
}

export function getInstitutionMetrics(role) {
  const metrics = INSTITUTION_METRICS[role] || INSTITUTION_METRICS.reporter;
  return { ...metrics };
}

export function getBranchRankings() {
  return BRANCH_RANKINGS
    .map((item) => ({ ...item }))
    .sort((left, right) => left.rank - right.rank);
}

export function getHomeModel(role) {
  const mode = getHomeMode(role);
  if (mode === "institution") {
    return {
      mode,
      showInstitutionMetrics: true,
      institutionMetrics: getInstitutionMetrics(role),
    };
  }
  return {
    mode,
    showBranchRanking: true,
    branchRankings: getBranchRankings(),
  };
}
