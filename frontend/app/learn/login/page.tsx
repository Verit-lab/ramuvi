import { redirect } from "next/navigation";

type LegacyLoginParams = { error?: string | string[]; connected?: string | string[] };

/** Historical link only. /learn owns login and account management. */
export default async function LegacyStudentLogin({ searchParams }: {
  searchParams: Promise<LegacyLoginParams>;
}) {
  const params = await searchParams;
  const query = new URLSearchParams({ account: "1" });
  if (params.error === "oidc") query.set("error", "oidc");
  if (params.connected === "1") query.set("connected", "1");
  redirect(`/learn${query.size ? `?${query.toString()}` : ""}`);
}
