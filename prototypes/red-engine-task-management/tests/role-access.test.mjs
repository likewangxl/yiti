import test from "node:test";
import assert from "node:assert/strict";

import {
  ROLE_OPTIONS,
  getDefaultView,
  getVisibleMenuKeys,
} from "../src/role-access.js";

test("支部审核员合并为支部书记，演示身份中不再保留支部审核员", () => {
  const labels = ROLE_OPTIONS.map((role) => role.label);

  assert.equal(labels.includes("支部书记"), true);
  assert.equal(labels.includes("支部审核员"), false);
});

test("支部书记只进入支部审核工作台", () => {
  assert.deepEqual(getVisibleMenuKeys("branchSecretary"), ["review"]);
  assert.equal(getDefaultView("branchSecretary"), "review");

  for (const role of ["admin", "employee", "orgReviewer"]) {
    assert.equal(getVisibleMenuKeys(role).includes("review"), false);
  }
});

test("原沉浸式审核工作台改为工作台且只对组织审核员开放", () => {
  assert.deepEqual(getVisibleMenuKeys("orgReviewer"), ["workbench"]);
  assert.equal(getDefaultView("orgReviewer"), "workbench");

  for (const role of ["admin", "employee", "branchSecretary"]) {
    assert.equal(getVisibleMenuKeys(role).includes("workbench"), false);
  }
});

test("一线员工首页待办入口与审核工作台分离", () => {
  assert.equal(getDefaultView("employee"), "home");
  assert.equal(getVisibleMenuKeys("employee").includes("home"), true);
  assert.equal(getVisibleMenuKeys("employee").includes("review"), false);
});
