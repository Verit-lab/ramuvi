import { apiFetch } from "./auth-api";
import { apiRequest } from "./http/client";
import { HttpApiError } from "./http/errors";

// v0.12.2 I3a (ADR-0076 D7): teacher side of Library sharing. The token is returned ONLY by
// create; listings never carry it. The QR is requested by POST so the token never sits in a URL.

export type SharePermission = "VIEW" | "SAVE";
export type ShareStatus = "ACTIVE" | "EXPIRED" | "REVOKED";

export type IssuedShare = {
  id: string;
  permission: SharePermission;
  label: string | null;
  createdAt: string;
  expiresAt: string;
  revision: number;
  token: string;
  path: string;
};

export type ShareSummary = {
  id: string;
  permission: SharePermission;
  label: string | null;
  createdAt: string;
  expiresAt: string;
  revokedAt: string | null;
  status: ShareStatus;
  revision: number;
  viewCount: number;
  saveCount: number;
  lastUsedAt: string | null;
};

export const SHARE_VALIDITY_DAYS = [1, 7, 14, 30, 90] as const;

export function shareUrl(origin: string, path: string): string {
  return `${origin.replace(/\/+$/, "")}${path}`;
}

export function permissionLabel(permission: SharePermission): string {
  return permission === "SAVE" ? "Permitir salvar na Minha Biblioteca" : "Somente visualizar";
}

export function statusLabel(status: ShareStatus): string {
  return status === "ACTIVE" ? "Ativo" : status === "EXPIRED" ? "Expirado" : "Revogado";
}

export function createLibraryShare(resourceId: string, input: { permission: SharePermission; validDays: number; label?: string | null }) {
  return apiRequest<IssuedShare>(`/api/library/resources/${encodeURIComponent(resourceId)}/shares`, {
    method: "POST",
    body: JSON.stringify({ permission: input.permission, validDays: input.validDays, label: input.label?.trim() || null }),
  }, { errorMessage: "Não foi possível criar o link de compartilhamento." });
}

export function listLibraryShares(resourceId: string) {
  return apiRequest<ShareSummary[]>(`/api/library/resources/${encodeURIComponent(resourceId)}/shares`, undefined, {
    errorMessage: "Não foi possível carregar os links deste material.",
  });
}

export function revokeLibraryShare(shareId: string) {
  return apiRequest<void>(`/api/library/shares/${encodeURIComponent(shareId)}/revoke`, { method: "POST", body: "{}" }, {
    errorMessage: "Não foi possível revogar o link.",
  });
}

export async function fetchLibraryShareQr(baseUrl: string, token: string): Promise<Blob> {
  const response = await apiFetch("/api/library/shares/qr", {
    method: "POST",
    body: JSON.stringify({ baseUrl, token, size: 320 }),
  });
  if (!response.ok) throw new HttpApiError("Não foi possível gerar o QR Code.", response.status);
  return response.blob();
}
