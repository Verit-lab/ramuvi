import { secureId } from "./secure-id";
import type { CreateScoreEventInput } from "./score-api";

export type PendingScoreBatch = {
  fingerprint: string;
  inputs: CreateScoreEventInput[];
};

/** Stable UUID per logical XP event, including retries after lost HTTP responses. */
export function prepareScoreBatch(
  inputs: CreateScoreEventInput[],
  pending: PendingScoreBatch | null,
): PendingScoreBatch {
  const fingerprint = JSON.stringify(inputs.map(({ requestId: _ignored, ...command }) => command));
  if (pending) {
    if (pending.fingerprint !== fingerprint) {
      throw new Error(
        "Há um lote de XP com resultado incerto. Repita os mesmos alunos e bônus ou confira o histórico antes de iniciar outro lançamento.",
      );
    }
    return pending;
  }
  return {
    fingerprint,
    inputs: inputs.map((input) => ({ ...input, requestId: input.requestId ?? secureId() })),
  };
}
