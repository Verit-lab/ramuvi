type Props = {
  /** Rendered size in CSS pixels. */
  size?: number;
  className?: string;
};

/**
 * The Ramuvi brand mark: the accent square with a dark "R", same geometry as
 * the favicon and PWA icons (public/icons/ramuvi.svg). Decorative: the visible
 * or accessible name of the surrounding link carries the brand.
 */
export default function RamuviMark({ size = 36, className }: Props) {
  return (
    <svg className={className} width={size} height={size} viewBox="0 0 512 512" aria-hidden="true" focusable="false">
      <rect width="512" height="512" rx="112" fill="var(--accent, #22b978)" />
      <path
        fill="#04110b"
        fillRule="evenodd"
        d="M158 118h120c66 0 108 36 108 92 0 40-21 69-56 82l66 102h-68l-58-94h-52v94h-60zM218 170v76h56c30 0 48-14 48-38s-18-38-48-38z"
      />
    </svg>
  );
}
