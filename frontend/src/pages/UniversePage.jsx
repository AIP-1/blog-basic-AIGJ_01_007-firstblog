import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { getBlogHome, getGraph } from '../api/blogs'
import { getTopics } from '../api/posts'
import { useAuth } from '../auth/useAuth'
import Timelapse from '../components/Timelapse'
import UniverseCanvas from '../components/UniverseCanvas'
import { useAsync } from '../hooks/useAsync'
import { TOPIC_COLORS } from '../utils/topicColors'
import NotFoundPage from './NotFoundPage'

/** 기록 우주 /@아이디/universe (GR-01 ~ GR-04): 글은 별, [[링크]]와 같은 태그는 선 */
export default function UniversePage() {
  const { handle } = useParams()
  const username = handle.startsWith('@') ? handle.slice(1) : null
  const { user } = useAuth()
  const navigate = useNavigate()
  const home = useAsync(() => getBlogHome(username), `${username}|${user?.id}`)
  const graph = useAsync(() => getGraph(username), `${username}|${user?.id}`)
  const topics = useAsync(() => getTopics(), 'topics')
  const [query, setQuery] = useState('')
  const [focusId, setFocusId] = useState(null)
  const [until, setUntil] = useState(null) // 타임랩스 (GR-05)

  const matches = useMemo(() => {
    const q = query.trim().toLowerCase()
    return q && graph.data ? graph.data.nodes.filter((n) => n.title.toLowerCase().includes(q)).map((n) => n.id) : []
  }, [query, graph.data])

  if (!username) return <NotFoundPage />
  if (home.status === 404 || graph.status === 404) return <NotFoundPage message="존재하지 않는 블로그예요" />
  if (home.loading || graph.loading) return <p className="py-16 text-center text-gray-500">우주를 불러오는 중…</p>
  if (graph.error) return <p className="py-16 text-center text-red-600">{graph.error}</p>

  const { nodes, edges, truncated } = graph.data
  const linkCount = edges.filter((e) => e.type === 'LINK').length
  const usedTopics = (topics.data ?? []).filter((t) => nodes.some((n) => n.topic === t.slug))
  const open = (node) => navigate(`/@${username}/posts/${node.id}`)
  const ranked = [...nodes].filter((n) => !n.locked).sort((a, b) => b.linkCount - a.linkCount || b.likeCount - a.likeCount)

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <Link to={`/@${username}`} className="text-sm text-gray-500 hover:text-indigo-600">← {home.data.name}</Link>
          <h1 className="mt-1 text-2xl font-bold">🌌 {home.data.owner.nickname}의 기록 우주</h1>
          <p className="mt-1 text-sm text-gray-500">
            기록 {nodes.length}개 · 연결 {linkCount}개{truncated && ' · 최근 300개만 보여요'}
          </p>
        </div>
        {nodes.length > 1 && (
          <form onSubmit={(e) => { e.preventDefault(); if (matches[0]) setFocusId(matches[0]) }} className="flex gap-2">
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="별 찾기 (글 제목)" aria-label="별 찾기"
              className="w-44 rounded-md border border-gray-300 px-3 py-1.5 text-sm sm:w-56" />
            <button type="submit" className="rounded-md bg-indigo-600 px-3 py-1.5 text-sm text-white">찾기</button>
          </form>
        )}
      </header>

      {nodes.length < 2 ? (
        <div className="rounded-2xl bg-slate-900 px-6 py-20 text-center text-slate-200">
          <p className="text-4xl">✨</p>
          <p className="mt-4">기록이 쌓이면 우주가 시작돼요.</p>
          <p className="mt-1 text-sm text-slate-400">글을 쓰고 [[로 다른 글을 연결해 보세요.</p>
        </div>
      ) : (
        <>
          <UniverseCanvas graph={graph.data} onSelect={open} highlightIds={matches} focusId={focusId} visibleUntil={until} />
          <Timelapse times={nodes.map((n) => Number(new Date(n.writtenAt)))} onChange={setUntil} />
        </>
      )}

      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-600">
        <span className="flex items-center gap-1"><span className="inline-block h-0.5 w-5 bg-indigo-300" /> [[링크]]</span>
        <span className="flex items-center gap-1"><span className="inline-block w-5 border-t border-dashed border-slate-400" /> 같은 태그</span>
        <span className="flex items-center gap-1"><span className="inline-block h-2.5 w-2.5 rounded-full bg-violet-500 opacity-60" /> 봉인된 타임캡슐</span>
        {usedTopics.map((t) => (
          <span key={t.slug} className="flex items-center gap-1">
            <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ background: TOPIC_COLORS[t.slug] }} /> {t.name}
          </span>
        ))}
        <span className="text-gray-400">끌어서 이동 · 휠/두 손가락으로 확대 · 별을 누르면 글로 가요</span>
      </div>

      {ranked.length > 0 && (
        <details className="rounded-lg border border-gray-200 bg-white p-4 text-sm">
          <summary className="cursor-pointer font-medium">목록으로 보기 (연결 많은 순)</summary>
          <ol className="mt-3 space-y-1">
            {ranked.map((n) => (
              <li key={n.id}>
                <Link to={`/@${username}/posts/${n.id}`} className="hover:text-indigo-600">{n.title}</Link>
                <span className="ml-2 text-xs text-gray-400">연결 {n.linkCount} · ♥ {n.likeCount}</span>
              </li>
            ))}
          </ol>
        </details>
      )}
    </div>
  )
}
