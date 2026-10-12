/** OwnLog 로고: 별(기록) + 글자. 로고 모양은 public/favicon.svg와 같아요 */
export default function Logo({ className = '' }) {
  return (
    <span className={`inline-flex items-center gap-1.5 ${className}`}>
      <svg viewBox="0 0 64 64" className="h-7 w-7 shrink-0" aria-hidden="true">
        <defs>
          <radialGradient id="logo-bg" cx="50%" cy="40%" r="70%">
            <stop offset="0" stopColor="#2a1f6b" />
            <stop offset="1" stopColor="#0b1026" />
          </radialGradient>
          <linearGradient id="logo-star" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#c4b5fd" />
            <stop offset="1" stopColor="#7357f6" />
          </linearGradient>
        </defs>
        <rect width="64" height="64" rx="14" fill="url(#logo-bg)" />
        <path d="M32 10 L36.5 27.5 L54 32 L36.5 36.5 L32 54 L27.5 36.5 L10 32 L27.5 27.5 Z" fill="url(#logo-star)" />
        <circle cx="49" cy="15" r="2" fill="#e0e7ff" />
      </svg>
      <span className="text-brand-gradient text-xl font-extrabold tracking-tight">OwnLog</span>
    </span>
  )
}
