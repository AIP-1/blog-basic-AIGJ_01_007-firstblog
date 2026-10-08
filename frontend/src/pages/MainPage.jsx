import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { errorMessage } from '../api/client'
import { getMainPosts, getTopics } from '../api/posts'
import Pagination from '../components/Pagination'
import PostGrid from '../components/PostGrid'
import TopicChips from '../components/TopicChips'

const TABS = [
  { value: 'latest', label: '최신' },
  { value: 'popular', label: '인기' },
]

/** 메인 (S-01 ~ S-04, T-03). 탭·토픽·페이지는 모두 주소(쿼리)에 저장해서 새로고침해도 유지돼요 */
export default function MainPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = searchParams.get('tab') === 'popular' ? 'popular' : 'latest'
  const topic = searchParams.get('topic')
  const page = Number(searchParams.get('page')) || 1
  const key = `${tab}|${topic}|${page}`

  const [topics, setTopics] = useState([])
  const [result, setResult] = useState({ key: null, data: null, error: null })

  useEffect(() => {
    getTopics().then(setTopics).catch(() => setTopics([]))
  }, [])

  useEffect(() => {
    let ignore = false // 탭을 빠르게 바꾸면 늦게 도착한 예전 응답을 버려요
    getMainPosts({ sort: tab, topic, page })
      .then((data) => !ignore && setResult({ key, data, error: null }))
      .catch((err) => !ignore && setResult({ key, data: null, error: errorMessage(err) }))
    return () => {
      ignore = true
    }
  }, [tab, topic, page, key])

  // 탭이나 토픽을 바꾸면 page를 지워서 1페이지로 돌아가요
  const changeFilter = (name, value) => {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(name, value)
    else next.delete(name)
    next.delete('page')
    setSearchParams(next)
  }

  const loading = result.key !== key

  return (
    <section className="space-y-5">
      <div className="flex items-end gap-4 border-b border-gray-200">
        {TABS.map(({ value, label }) => (
          <button
            key={value}
            type="button"
            onClick={() => changeFilter('tab', value === 'latest' ? null : value)}
            className={`-mb-px border-b-2 px-1 pb-2 font-semibold ${
              tab === value ? 'border-indigo-600 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-800'
            }`}
          >
            {label}
          </button>
        ))}
        {tab === 'popular' && <span className="pb-2 text-xs text-gray-500">최근 7일 · 좋아요 순</span>}
      </div>

      <TopicChips topics={topics} selected={topic} onSelect={(slug) => changeFilter('topic', slug)} />

      <PostGrid loading={loading} error={result.error} posts={result.data?.content} />
      {!loading && result.data && <Pagination page={result.data.page} totalPages={result.data.totalPages} />}
    </section>
  )
}
