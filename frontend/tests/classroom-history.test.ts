import assert from "node:assert/strict";
import test from "node:test";
import { mergeClassroomHistory, filterClassroomHistory } from "../lib/classroom-history";
import type { ScoreEvent } from "../lib/types";
import type { SessionEvent } from "../lib/session-event-api";

const xp: ScoreEvent[] = [
  { id: "x1", classroomId: "a", studentId: "s", points: 10, category: "BONUS", description: "Participação", createdAt: "2026-10-25T12:00:00Z" },
  { id: "x2", classroomId: "a", studentId: "s", points: -10, category: "BONUS", description: "Estorno", createdAt: "2026-10-25T12:04:00Z", reversalOf: "x1" },
];
const action: SessionEvent[] = [{
  id: "y1", classroomId: "a", sequenceNo: 1, sessionId: "live", sessionTitle: "Aula 4",
  eventType: "POLL_OPENED", actor: "TEACHER", summary: "Votação aberta",
  payload: {}, occurredAt: "2026-10-25T12:02:00Z",
}];

test("one ordered timeline retains separate XP and operational provenance", () => {
  const rows = mergeClassroomHistory(xp, action, new Map([["s", "Ana"]]));
  assert.deepEqual(rows.map((row) => row.key), ["xp:x2", "action:y1", "xp:x1"]);
  assert.equal(rows[0].kind, "xp");
  assert.equal(rows[1].kind, "actions");
  assert.equal(rows[0].category, "Reversão");
  assert.equal(rows[1].category, "Dinâmica");
  assert.equal(rows[2].title, "Ana");
});

test("XP and actions filters operate on the same data, with one search", () => {
  const rows = mergeClassroomHistory(xp, action, new Map([["s", "Ana"]]));
  assert.equal(filterClassroomHistory(rows, "all", "").length, 3);
  assert.deepEqual(filterClassroomHistory(rows, "xp", "").map((row) => row.key), ["xp:x2", "xp:x1"]);
  assert.deepEqual(filterClassroomHistory(rows, "actions", "votação").map((row) => row.key), ["action:y1"]);
  assert.deepEqual(filterClassroomHistory(rows, "all", "ANA").map((row) => row.key), ["xp:x2", "xp:x1"]);
});

test("no artificial XP is inferred from conduction events", () => {
  const rows = mergeClassroomHistory([], action, new Map());
  assert.equal(rows.length, 1);
  assert.equal(rows[0].kind, "actions");
  assert.equal(filterClassroomHistory(rows, "xp", "").length, 0);
});
