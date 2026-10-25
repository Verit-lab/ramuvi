import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { parseActivityImport } from "../lib/activity-import";

const questions = [
  { type: "MULTIPLE_CHOICE", statement: "Qual é o resultado de SELECT 1?",
    difficulty: "EASY", points: 2, options: ["A", "B", "C", "D"].map(id => ({ id, text: "Alternativa " + id })), answer: "A" },
  { type: "OPEN", statement: "Explique a normalização dos dados.",
    difficulty: "INTERMEDIATE", points: 3, expectedAnswer: "Separar entidades e dependências." },
];

test("importa metadados e questões validadas como rascunho, nunca cria uma atividade automaticamente", () => {
  const parsed = parseActivityImport(JSON.stringify({ version: "1.0", activity: { title: "SQL", topic: "Modelagem" }, questions }), "outro.json");
  assert.deepEqual(parsed.errors, []);
  assert.equal(parsed.activity?.title, "SQL");
  assert.equal(parsed.activity?.topic, "Modelagem");
  assert.equal(parsed.activity?.questions.length, 2);
  assert.notEqual(parsed.activity?.questions[0].id, undefined);
});

test("arquivo de questões sem título usa nome; rejeita pacotes vazios ou malformados", () => {
  assert.equal(parseActivityImport(JSON.stringify({ version: "1.0", questions }), "Minha atividade.json").activity?.title, "Minha atividade");
  assert.match(parseActivityImport("not-json", "x.json").errors[0], /JSON inválido/);
  assert.match(parseActivityImport(JSON.stringify({ version: "1.0", questions: [] }), "x.json").errors[0], /não contém questões/);
  assert.match(parseActivityImport(JSON.stringify({ version: "2.0", questions }), "x.json").errors[0], /version/);
  assert.match(parseActivityImport("x".repeat(1024 * 1024 + 1), "x.json").errors[0], /1 MB/);
  const invalidAnswer = [{ ...questions[0], answer: "Z" }];
  assert.match(parseActivityImport(JSON.stringify({ version: "1.0", questions: invalidAnswer }), "x.json").errors[0], /answer/);
});

test("editor docente é página de trabalho, com importação JSON distinta da cópia entre turmas", () => {
  const source = readFileSync("components/teacher/ActivitiesView.tsx", "utf8");
  assert.match(source, /editorOpen && <section className="activity-editor-page"/);
  assert.doesNotMatch(source, /<Modal open=\{modal === "editor"/);
  assert.match(source, /Importar JSON/);
  assert.match(source, /Copiar de outra turma/);
  assert.match(source, /closeEditor/);
  assert.match(source, /editorBaseline.current/);
});
