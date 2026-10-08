import { useState } from 'react'
import { errorMessage, fieldErrorsOf } from '../api/client'
import { report } from '../api/comments'

const REASONS = [
  { value: 'SPAM', label: '스팸·홍보' },
  { value: 'ABUSE', label: '욕설·혐오' },
  { value: 'ADULT', label: '음란물' },
  { value: 'ETC', label: '기타' },
]

/** 글·댓글 신고 (R-01 ~ R-04). "기타"는 상세 사유가 필수예요 */
export default function ReportDialog({ target, onClose }) {
  const [reason, setReason] = useState('SPAM')
  const [detail, setDetail] = useState('')
  const [message, setMessage] = useState('')
  const [done, setDone] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    try {
      await report(target, reason, detail.trim())
      setDone(true)
    } catch (err) {
      setMessage(fieldErrorsOf(err).detail ?? errorMessage(err))
    }
  }

  return (
    <div className="fixed inset-0 z-20 flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
      <div className="w-full max-w-sm rounded-lg bg-white p-5" onClick={(e) => e.stopPropagation()} role="dialog">
        {done ? (
          <>
            <p className="mb-4">신고가 접수되었어요.</p>
            <button type="button" onClick={onClose} className="w-full rounded-md bg-indigo-600 py-2 text-white">
              닫기
            </button>
          </>
        ) : (
          <form onSubmit={submit} className="space-y-3">
            <h2 className="font-semibold">{target.postId ? '글 신고' : '댓글 신고'}</h2>
            {REASONS.map(({ value, label }) => (
              <label key={value} className="flex items-center gap-2 text-sm">
                <input type="radio" name="reason" value={value} checked={reason === value} onChange={() => setReason(value)} />
                {label}
              </label>
            ))}
            <textarea
              value={detail}
              onChange={(e) => setDetail(e.target.value)}
              maxLength={500}
              rows={3}
              placeholder={reason === 'ETC' ? '신고 사유를 적어 주세요 (필수)' : '상세 사유 (선택)'}
              className="w-full rounded-md border border-gray-300 p-2 text-sm"
            />
            {message && <p className="text-sm text-red-600">{message}</p>}
            <div className="flex gap-2">
              <button type="button" onClick={onClose} className="flex-1 rounded-md border border-gray-300 py-2">
                취소
              </button>
              <button type="submit" className="flex-1 rounded-md bg-red-600 py-2 text-white">
                신고
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
