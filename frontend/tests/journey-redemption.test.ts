import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const journeys = readFileSync(resolve(process.cwd(), "app/learn/journeys/page.tsx"), "utf8");
const studyApi = readFileSync(resolve(process.cwd(), "lib/study-api.ts"), "utf8");
const manager = readFileSync(resolve(process.cwd(), "components/JourneyManager.tsx"), "utf8");
const joinFlow = readFileSync(resolve(process.cwd(), "components/StudentJoinFlow.tsx"), "utf8");

test("student can add a journey with a one-time redemption code", () => {
  assert.match(journeys, /Adicionar com código/);
  assert.match(journeys, /redeemJourneyCode/);
  assert.match(studyApi, /\/api\/learn\/journeys\/redeem/);
});

test("ADR-0075: a journey code never bootstraps a study profile from typed identity", () => {
  assert.doesNotMatch(studyApi, /\/api\/learn\/journeys\/redeem\/bootstrap/);
  assert.doesNotMatch(journeys, /bootstrapJourneyRedemption/);
  assert.doesNotMatch(journeys, /Matrícula, e-mail ou nome completo/);
  assert.match(journeys, /Entre com sua conta para estudar/);
  assert.match(journeys, /\/learn\?account=1/);
});

test("join routes journey codes to the study redemption flow", () => {
  assert.match(joinFlow, /normalized\.startsWith\("J-"\)/);
  assert.match(joinFlow, /\/learn\/journeys\?redeem=/);
});

test("teacher controls journey code without coupling it to publication", () => {
  assert.match(manager, /Gerar código/);
  assert.match(manager, /Regenerar/);
  assert.match(manager, /Desativar/);
  assert.match(manager, /resgate\(s\)/);
});


test("journey redemption API interpolates the real UUID instead of a literal template expression", () => {
  const api = readFileSync(resolve(process.cwd(), "lib/journey-api.ts"), "utf8");
  assert.match(api, /\/api\/journeys\/\$\{encodeURIComponent\(id\)\}\/redemption/);
  assert.doesNotMatch(api, /\\\$\{encodeURIComponent\(id\)\}/);
});
