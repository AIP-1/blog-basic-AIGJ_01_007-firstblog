import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/useAuth'
import FormField from '../components/FormField'
import SocialLoginButtons from '../components/SocialLoginButtons'
import { safeRedirect } from '../utils/safeRedirect'

/** 소셜 로그인이 실패해서 돌아왔을 때(?oauthError=) 보여줄 문장 */
const OAUTH_ERRORS = {
  email_in_use: '이 이메일로 이미 가입한 계정이 있어요. 이메일과 비밀번호로 로그인해 주세요.',
  inactive: '탈퇴한 계정이에요.',
  cancelled: '소셜 로그인을 취소했어요. 다른 방법으로 로그인하거나 다시 시도해 주세요.',
  failed: '소셜 로그인을 마치지 못했어요. 다시 시도해 주세요.',
}

/** 로그인 (API 4.2). 실패 이유는 구분하지 않고 서버 메시지를 그대로 보여줘요 (NF-06) */
export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [form, setForm] = useState({ email: '', password: '' })
  const oauthError = searchParams.get('oauthError')
  const [error, setError] = useState(oauthError ? OAUTH_ERRORS[oauthError] ?? OAUTH_ERRORS.failed : '')
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
    <div className="mx-auto max-w-sm overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
      <div className="bg-cosmos px-6 py-6 text-white">
        <p className="text-xs tracking-widest text-indigo-200">✦ OWNLOG</p>
        <h1 className="mt-1 text-xl font-bold">로그인</h1>
        <p className="mt-1 text-sm text-indigo-100/80">다시 만나서 반가워요. 오늘의 기록을 이어 가요.</p>
      </div>
      <div className="p-6">
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
      <SocialLoginButtons redirect={searchParams.get('redirect') || '/'} />
      <p className="mt-4 text-center text-sm text-gray-600">
        아직 회원이 아니신가요?{' '}
        <Link to="/signup" className="text-indigo-600 hover:underline">
          회원가입
        </Link>
      </p>
      </div>
    </div>
  )
}
