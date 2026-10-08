import { Link, useSearchParams } from 'react-router'

const WINDOW = 5 // 현재 페이지 주변으로 보여줄 번호 수

/** 페이지 번호 (S-04). 다른 쿼리(탭, 토픽, 검색어)는 그대로 두고 page만 바꿔요 */
export default function Pagination({ page, totalPages }) {
  const [searchParams] = useSearchParams()
  if (totalPages <= 1) return null

  const hrefOf = (p) => {
    const next = new URLSearchParams(searchParams)
    if (p === 1) next.delete('page') // 1페이지는 주소를 깔끔하게
    else next.set('page', String(p))
    const query = next.toString()
    return query ? `?${query}` : '?'
  }

  const end = Math.min(totalPages, Math.max(page - 2, 1) + WINDOW - 1)
  const start = Math.max(1, end - WINDOW + 1)
  const pages = Array.from({ length: end - start + 1 }, (_, i) => start + i)

  const base = 'rounded-md px-3 py-1.5 text-sm'
  return (
    <nav aria-label="페이지" className="mt-8 flex flex-wrap items-center justify-center gap-1">
      {page > 1 && (
        <Link to={hrefOf(page - 1)} className={`${base} text-gray-600 hover:bg-gray-100`}>
          ‹ 이전
        </Link>
      )}
      {pages.map((p) => (
        <Link
          key={p}
          to={hrefOf(p)}
          aria-current={p === page ? 'page' : undefined}
          className={`${base} ${p === page ? 'bg-indigo-600 font-semibold text-white' : 'text-gray-700 hover:bg-gray-100'}`}
        >
          {p}
        </Link>
      ))}
      {page < totalPages && (
        <Link to={hrefOf(page + 1)} className={`${base} text-gray-600 hover:bg-gray-100`}>
          다음 ›
        </Link>
      )}
    </nav>
  )
}
