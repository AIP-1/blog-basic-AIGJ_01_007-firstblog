/** 토픽 칩 (T-03): "전체" + 서비스가 정한 토픽. 좁은 화면에서는 가로로 스크롤해요 */
export default function TopicChips({ topics, selected, onSelect }) {
  const chip = (active) =>
    `shrink-0 rounded-full border px-3 py-1 text-sm ${
      active ? 'border-indigo-600 bg-indigo-600 text-white' : 'border-gray-300 bg-white text-gray-700 hover:border-indigo-400'
    }`

  return (
    <div className="flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="토픽">
      <button type="button" className={chip(!selected)} onClick={() => onSelect(null)}>
        전체
      </button>
      {topics.map((topic) => (
        <button key={topic.slug} type="button" className={chip(selected === topic.slug)} onClick={() => onSelect(topic.slug)}>
          {topic.name}
        </button>
      ))}
    </div>
  )
}
