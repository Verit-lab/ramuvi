import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";

test("reingresso de aluno reconhecido usa vínculo do dispositivo e não novo código obrigatório", () => {
  const join = readFileSync("components/StudentJoinFlow.tsx", "utf8");
  const live = readFileSync("app/learn/live/page.tsx", "utf8");
  assert.match(join, /studyDeviceClaims\(\)\.length > 0/);
  assert.match(join, /!embedded && !session && hasRememberedClassroom && !code\.trim\(\)/);
  assert.match(join, /href="\/learn"/);
  assert.match(join, /href="\/learn\/live"/);
  assert.match(live, /Nenhuma aula ao vivo encontrada neste momento/);
  assert.match(live, /href="\/learn\/journeys"/);
  assert.match(join, /joinRememberedDevice\(session\.code, rememberedToken\)/);
  assert.match(join, /joinSession\(session\.code, identity\.trim\(\), rememberDevice\)/);
});
