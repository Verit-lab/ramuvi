import test from "node:test";
import assert from "node:assert/strict";
import { prepareScoreBatch } from "../lib/score-batch-idempotency";
import type { CreateScoreEventInput } from "../lib/score-api";

const input = (studentId: string, points = 10): CreateScoreEventInput => ({
  classroomId: "c1", studentId, points, category: "SUBMISSION",
  description: "Entrega: SQL", source: "ACTIVITY", activityId: "activity-1",
});

test("new batch gets a unique idempotency UUID per event", () => {
  const batch = prepareScoreBatch([input("a"), input("b")], null);
  assert.equal(batch.inputs.length, 2);
  assert.match(batch.inputs[0].requestId ?? "", /^[a-f\d-]{36}$/i);
  assert.notEqual(batch.inputs[0].requestId, batch.inputs[1].requestId);
});

test("retry uses original keys after an uncertain response", () => {
  const initial = prepareScoreBatch([input("a"), input("b")], null);
  const retry = prepareScoreBatch([input("a"), input("b")], initial);
  assert.equal(retry, initial);
  assert.deepEqual(retry.inputs, initial.inputs);
});

test("a different payload cannot replace an unresolved batch", () => {
  const initial = prepareScoreBatch([input("a")], null);
  assert.throws(() => prepareScoreBatch([input("a", 100)], initial), /resultado incerto/);
  assert.throws(() => prepareScoreBatch([input("b")], initial), /resultado incerto/);
});

test("after reconciliation, a new batch gets new keys", () => {
  const initial = prepareScoreBatch([input("a")], null);
  const next = prepareScoreBatch([input("a")], null);
  assert.notEqual(next.inputs[0].requestId, initial.inputs[0].requestId);
});
