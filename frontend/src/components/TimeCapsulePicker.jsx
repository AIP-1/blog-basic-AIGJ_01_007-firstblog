import { toKstInput } from '../utils/format'

/** 다음 분 경계 + minutes분 (서버 규칙 "1분 뒤부터"를 분 단위 입력으로 항상 지키게 올림해요) */
function afterMinutes(minutes) {
  const date = new Date(Date.now() + minutes * 60_000)
  date.setSeconds(0, 0)
  date.setMinutes(date.getMinutes() + 1)
  return toKstInput(date)
}

const QUICK = [
  { label: '1분 뒤', minutes: 1 },
  { label: '1시간 뒤', minutes: 60 },
  { label: '1주 뒤', minutes: 60 * 24 * 7 },
  { label: '1년 뒤', minutes: 60 * 24 * 365 },
]

/** 글쓰기의 "타임캡슐로 보내기" (TC-01, TC-02). value: { enabled, openAt("YYYY-MM-DDTHH:mm", KST) } */
export default function TimeCapsulePicker({ value, onChange, error }) {
  return (
    <div className={`rounded-lg border p-3 text-sm ${value.enabled ? 'border-violet-300 bg-violet-50' : 'border-gray-200 bg-white'}`}>
      <label className="flex cursor-pointer items-center gap-2 font-medium">
        <input
          type="checkbox"
          checked={value.enabled}
          aria-label="타임캡슐로 보내기"
          onChange={(e) => onChange({ enabled: e.target.checked, openAt: value.openAt || afterMinutes(60 * 24 * 7) })}
        />
        ⏳ 타임캡슐로 보내기
        <span className="font-normal text-gray-500">정한 날까지 봉인했다가 그때 공개해요</span>
      </label>
      {value.enabled && (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <input
            type="datetime-local"
            value={value.openAt}
            onChange={(e) => onChange({ ...value, openAt: e.target.value })}
            aria-label="열리는 시각"
            className="rounded-md border border-gray-300 bg-white px-2 py-1"
          />
          <span className="text-gray-500">(한국 시간)</span>
          {QUICK.map((q) => (
            <button key={q.label} type="button" onClick={() => onChange({ ...value, openAt: afterMinutes(q.minutes) })}
              className="rounded-full border border-violet-200 bg-white px-3 py-1 text-violet-700 hover:bg-violet-100">
              {q.label}
            </button>
          ))}
          <p className="w-full text-xs text-violet-700">봉인한 뒤에는 열릴 때까지 수정할 수 없어요. 작성자인 나는 언제든 볼 수 있어요.</p>
          {error && <p className="w-full text-red-600">{error}</p>}
        </div>
      )}
    </div>
  )
}
