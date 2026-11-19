import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const read = (path: string) => readFileSync(resolve(process.cwd(), path), "utf8");

test("public root loads only minimal tokens, not the teacher and student UI", () => {
  const layout = read("app/layout.tsx");
  assert.match(layout, /import "\.\.\/styles\/public-base\.css"/);
  assert.doesNotMatch(layout, /globals\.css|arena-polish\.css|classroom-overview\.css|timer-panel\.css|mobile-reflow\.css/);
  const base = read("styles/public-base.css");
  for (const path of ["theme", "tokens", "base", "accessibility"]) {
    assert.ok(base.includes(`@import "./${path}.css";`));
  }
  assert.doesNotMatch(base, /arena-polish|app-ui/);
});

test("every app entry point loads the shared educational UI styles", () => {
  const appCss = read("styles/app-ui.css");
  for (const name of ["globals", "classroom-overview", "timer-panel", "mobile-reflow"]) {
    assert.ok(appCss.includes(name + ".css"), name);
  }
  for (const name of ["learn", "teach", "join", "projector"]) {
    assert.match(read(`app/${name}/layout.tsx`), /import "@\/styles\/app-ui\.css"/);
  }
});
