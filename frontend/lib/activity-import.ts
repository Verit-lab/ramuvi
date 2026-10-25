import { parseQuestionPackage } from "./activity-questions";
import type { ActivityQuestion } from "./types";

export interface ImportedActivityDraft {
  title: string;
  topic: string;
  questions: ActivityQuestion[];
}

/** Local-only import: saving remains an explicit teacher action. */
export function parseActivityImport(raw: string, filename: string): {
  activity?: ImportedActivityDraft;
  errors: string[];
  warnings: string[];
} {
  if (raw.length > 1024 * 1024) return { errors: ["O arquivo JSON excede 1 MB."], warnings: [] };
  const parsed = parseQuestionPackage(raw);
  if (parsed.errors.length || !parsed.package) {
    return { errors: parsed.errors, warnings: parsed.warnings };
  }
  if (!parsed.package.questions.length) {
    return { errors: ["O arquivo não contém questões para importar."], warnings: [] };
  }
  const title = parsed.package.activity?.title?.trim() || filename.replace(/\.json$/i, "").trim() || "Nova atividade";
  const topic = parsed.package.activity?.topic?.trim() || "";
  return { activity: { title, topic, questions: parsed.package.questions }, errors: [], warnings: parsed.warnings };
}
