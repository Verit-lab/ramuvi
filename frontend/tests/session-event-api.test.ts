import { readTeacherAppSource } from "./support/teacher-source";
import assert from "node:assert/strict";
import test, { afterEach } from "node:test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  fetchSessionEvents,
  fetchSessionEventsBySession,
} from "../lib/session-event-api";

const originalFetch = globalThis.fetch;
const timelineSource = readFileSync(
  resolve(process.cwd(), "components/SessionTimeline.tsx"),
  "utf8",
);
const arenaSource = readTeacherAppSource();

afterEach(() => {
  globalThis.fetch = originalFetch;
});

test("loads the classroom operational timeline with a bounded limit", async () => {
  globalThis.fetch = async (input, init) => {
    assert.equal(
      String(input),
      "http://localhost:8080/api/session-events?classroomId=classroom-a&limit=500",
    );
    assert.equal(init?.credentials, "include");

    return new Response(JSON.stringify([
      {
        id: "event-a",
        sequenceNo: 42,
        sessionId: "session-a",
        classroomId: "classroom-a",
        sessionTitle: "Aula 01",
        eventType: "POLL_OPENED",
        actor: "TEACHER",
        summary: "Votação aberta: Qual caminho seguir?",
        payload: { optionCount: 2 },
        occurredAt: "2026-09-09T12:00:00Z",
      },
    ]), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };

  const events = await fetchSessionEvents("classroom-a", 9999);
  assert.equal(events.length, 1);
  assert.equal(events[0]?.eventType, "POLL_OPENED");
  assert.equal(events[0]?.sequenceNo, 42);
});

test("loads one session timeline in chronological endpoint order", async () => {
  globalThis.fetch = async (input) => {
    assert.equal(
      String(input),
      "http://localhost:8080/api/sessions/session-a/events",
    );
    return new Response(JSON.stringify([]), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };

  assert.deepEqual(await fetchSessionEventsBySession("session-a"), []);
});

test("merges classroom operational events and XP in one history with safe filters", () => {
  const history = readFileSync(resolve(process.cwd(), "components/teacher/HistoryView.tsx"), "utf8");
  assert.match(arenaSource, /<HistoryView/);
  assert.match(history, /mergeClassroomHistory\(events, operational, studentsById\)/);
  assert.match(history, /filterClassroomHistory\(entries, filter, query\)/);
  assert.match(history, /"all", label: "Tudo"/);
  assert.match(history, /"xp", label: "XP dos alunos"/);
  assert.match(history, /"actions", label: "Ações e dinâmicas"/);
  assert.match(history, /onReverse\(entry.score.id\)/);
});

test("timeline exposes actor context without announcing the whole historical list", () => {
  assert.match(timelineSource, /TEACHER: "Professor"/);
  assert.match(timelineSource, /SYSTEM: "Sistema"/);
  assert.doesNotMatch(timelineSource, /className=\{styles\.timeline\} aria-live=/);
});
