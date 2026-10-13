import { useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { getUniverse } from '../api/blogs'
import Timelapse from '../components/Timelapse'
import UniverseCanvas from '../components/UniverseCanvas'
import { useAsync } from '../hooks/useAsync'
import { GALAXY_COLORS } from '../utils/topicColors'

/** 서비스 전체 우주 /universe (GR-06): 모든 블로그의 공개 글. 블로그마다 색이 다른 은하로 모이고, 블로그 사이 [[링크]]가 다리처럼 이어져요 */
export default function GlobalUniversePage() {
  const navigate = useNavigate()
  const { data, error, loading } = useAsync(() => getUniverse(), 'universe')
  const [query, setQuery] = useState('')
  const [focusId, setFocusId] = useState(null)
  const canvasRef = useRef(null) // 타임랩스는 캔버스에 직접 알려줘요 (GR-05)

  // 글이 많은 블로그부터 색을 정해요
  const galaxies = useMemo(() => {
    if (!data) return []
    const counts = new Map()
    for (const n of data.nodes) counts.set(n.username, { username: n.username, nickname: n.nickname, count: (counts.get(n.username)?.count ?? 0) + 1 })
    return [...counts.values()].sort((a, b) => b.count - a.count).map((g, i) => ({ ...g, color: GALAXY_COLORS[i % GALAXY_COLORS.length] }))
  }, [data])
  const colorByUser = useMemo(() => new Map(galaxies.map((g) => [g.username, g.color])), [galaxies])
  const times = useMemo(() => (data?.nodes ?? []).map((n) => Number(new Date(n.writtenAt))), [data])
  // 블로그를 잇는 연결: 별 id → 블로그 지도를 한 번 만들어서 세요 (별마다 전체를 찾지 않게)
  const crossLinks = useMemo(() => {
    if (!data) return 0
    const owner = new Map(data.nodes.map((n) => [n.id, n.username]))
    return data.edges.filter((e) => e.type === 'LINK' && owner.get(e.source) !== owner.get(e.target)).length
  }, [data])
  const matches = useMemo(() => {
    const q = query.trim().toLowerCase()
    return q && data ? data.nodes.filter((n) => n.title.toLowerCase().includes(q)).map((n) => n.id) : []
  }, [query, data])

  if (loading) return <p className="py-16 text-center text-gray-500">우주를 불러오는 중…</p>
  if (error) return <p className="py-16 text-center text-red-600">{error}</p>


  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">🌌 OwnLog 우주</h1>
          <p className="mt-1 text-sm text-gray-500">
            블로그 {galaxies.length}개 · 기록 {data.nodes.length}개 · 블로그를 잇는 연결 {crossLinks}개{data.truncated && ' · 최근 500개만 보여요'}
          </p>
        </div>
        {data.nodes.length > 1 && (
          <form onSubmit={(e) => { e.preventDefault(); if (matches[0]) setFocusId((f) => ({ id: matches[0], n: (f?.n ?? 0) + 1 })) }} className="flex gap-2">
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="별 찾기 (글 제목)" aria-label="별 찾기"
              className="w-44 rounded-md border border-gray-300 px-3 py-1.5 text-sm sm:w-56" />
            <button type="submit" className="rounded-md bg-indigo-600 px-3 py-1.5 text-sm text-white">찾기</button>
          </form>
        )}
      </header>

      {data.nodes.length < 2 ? (
        <div className="rounded-2xl bg-slate-900 px-6 py-20 text-center text-slate-200">
          <p className="text-4xl">✨</p>
          <p className="mt-4">기록이 쌓이면 우주가 시작돼요.</p>
        </div>
      ) : (
        <>
          <UniverseCanvas graph={data} highlightIds={matches} focusId={focusId} ref={canvasRef}
            onSelect={(node) => navigate(`/@${node.username}/posts/${node.id}`)}
            colorOf={(node) => colorByUser.get(node.username)} groupOf={(node) => node.username} />
          <Timelapse times={times} onChange={(until) => canvasRef.current?.setUntil(until)} />
        </>
      )}

      <section aria-label="은하 목록" className="flex flex-wrap gap-2 text-sm">
        {galaxies.map((g) => (
          <Link key={g.username} to={`/@${g.username}/universe`}
            className="flex items-center gap-2 rounded-full border border-gray-200 bg-white px-3 py-1 hover:border-indigo-300">
            <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ background: g.color }} />
            {g.nickname} <span className="text-gray-400">{g.count}</span>
          </Link>
        ))}
      </section>
      <p className="text-xs text-gray-400">은하(블로그)를 누르면 그 블로그의 우주로 가요 · 밝은 실선은 [[링크]], 점선은 관련 있는 글(진할수록 더 관련 있어요)이에요</p>
    </div>
  )
}
