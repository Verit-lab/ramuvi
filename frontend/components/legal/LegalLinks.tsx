import Link from "next/link";
import { LEGAL_ROUTES } from "@/lib/brand";

type Props = {
  className?: string;
  /** Accessible name of the landmark; omit inside an existing navigation. */
  label?: string;
};

/**
 * The single discreet line of legal links shared by the landing page, the
 * legal documents and the login screens. One component so the names and
 * targets stay identical everywhere (and in the e2e audits).
 */
export default function LegalLinks({ className, label = "Documentos legais" }: Props) {
  return (
    <nav className={className} aria-label={label}>
      <Link href={LEGAL_ROUTES.terms} prefetch={false}>Termos de Uso</Link>
      <span aria-hidden="true"> · </span>
      <Link href={LEGAL_ROUTES.privacy} prefetch={false}>Política de Privacidade</Link>
    </nav>
  );
}
