import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const read = (file: string) => readFileSync(resolve(process.cwd(), file), "utf8");

test("o professor controla convite por turma e compartilha link para Learn", () => {
  const teacher = read("components/teacher/ClassroomManagementPage.tsx");
  assert.match(teacher, /setClassroomSelfEnrollment/);
  assert.match(teacher, /Permitir ingresso pelo código da turma/);
  assert.match(teacher, /classCode/);
  assert.match(teacher, /Copiar link para alunos/);
});

test("a entrada do aluno preserva o vínculo e mantém convite individual como fallback", () => {
  const student = read("components/student/StudentAccountAccess.tsx");
  assert.match(student, /joinStudentClassroom/);
  assert.match(student, /ramuvi:pending-class-code/);
  assert.match(student, /Entrar na turma/);
  assert.match(student, /Tenho um convite individual do professor/);
});

test("o código da turma é consumido por POST autenticado, jamais por URL de API", () => {
  const api = read("lib/student-oidc-api.ts");
  assert.match(api, /apiFetch\("\/api\/student-account\/classrooms\/join"/);
  assert.match(api, /method: "POST"/);
  assert.match(api, /body: JSON\.stringify\(\{ code: normalized \}\)/);
});
