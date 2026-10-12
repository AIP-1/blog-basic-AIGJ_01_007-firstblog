import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/useAuth'
import FormField from '../components/FormField'

/** 로그인 (API 4.2). 실패 이유는 구분하지 않고 서버 메시지를 그대로 보여줘요 (NF-06) */
export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const change = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      await login(form.email, form.password)
      navigate(safeRedirect(searchParams.get('redirect')), { replace: true })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-sm rounded-lg border border-gray-200 bg-white p-6">
      <h1 className="mb-6 text-xl font-bold">로그인</h1>
      <form onSubmit={submit} className="space-y-4">
        <FormField label="이메일" name="email" type="email" value={form.email} onChange={change} required autoComplete="email" />
        <FormField label="비밀번호" name="password" type="password" value={form.password} onChange={change} required autoComplete="current-password" />
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-indigo-600 py-2 font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
        >
          {submitting ? '로그인 중…' : '로그인'}
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-gray-600">
        아직 회원이 아니신가요?{' '}
        <Link to="/signup" className="text-indigo-600 hover:underline">
          회원가입
        </Link>
      </p>
    </div>
  )
}

/**
 * 우리 사이트 안의 주소로만 돌아가요 (오픈 리다이렉트 방지).
 * "//evil.com"뿐 아니라 "/\t/evil.com"처럼 제어 문자·역슬래시를 섞어 브라우저가 외부 주소로 해석하게 만드는 경우도 막으려고,
 * 실제 URL로 해석해서 우리 출처(origin)인지 확인하고 경로 부분만 써요.
 */
function safeRedirect(redirect) {
  // 제어 문자(탭·줄바꿈 등)를 일부러 찾는 정규식이에요
  // eslint-disable-next-line no-control-regex
  if (!redirect || !redirect.startsWith('/') || /[\u0000-\u001f\\]/.test(redirect)) return '/'
  try {
    const url = new URL(redirect, window.location.origin)
    return url.origin === window.location.origin ? url.pathname + url.search + url.hash : '/'
  } catch {
    return '/'
  }
}
