import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, errorMessage, fieldErrorsOf } from '../api/client'
import { useAuth } from '../auth/useAuth'
import FormField from '../components/FormField'

const USERNAME_HINT = {
  null: { text: '사용할 수 있는 아이디예요.', tone: 'green' },
  DUPLICATE: { text: '이미 사용 중인 아이디예요.', tone: 'red' },
  RESERVED: { text: '사용할 수 없는 아이디예요.', tone: 'red' },
  INVALID_FORMAT: { text: '영문 소문자, 숫자, _ 3~20자로 입력해 주세요.', tone: 'red' },
}

/** 회원가입 (API 4.1, 4.3). 성공하면 바로 로그인해요 */
export default function SignupPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', password: '', passwordConfirm: '', username: '', nickname: '' })
  const [usernameCheck, setUsernameCheck] = useState(null) // { username, available, reason }
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const change = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value })
    setFieldErrors({ ...fieldErrors, [e.target.name]: undefined })
  }

  // 아이디 실시간 확인: 입력이 0.4초 멈추면 한 번만 물어봐요 (디바운스)
  useEffect(() => {
    if (!form.username) return
    const username = form.username
    const timer = setTimeout(async () => {
      try {
        const { data } = await api.get('/auth/check-username', { params: { username } })
        setUsernameCheck({ username, ...data }) // 어떤 아이디를 확인한 결과인지 함께 저장해요
      } catch {
        setUsernameCheck(null)
      }
    }, 400)
    return () => clearTimeout(timer) // 0.4초 안에 또 입력하면 이전 예약을 취소해요
  }, [form.username])

  const submit = async (e) => {
    e.preventDefault()
    setSubmitting(true)
    setError('')
    setFieldErrors({})
    try {
      await api.post('/auth/signup', form)
      await login(form.email, form.password) // 가입 후 자동 로그인 (API 4.1)
      navigate('/', { replace: true })
    } catch (err) {
      const errors = fieldErrorsOf(err)
      setFieldErrors(errors)
      if (Object.keys(errors).length === 0) setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  // 지금 입력한 아이디의 결과일 때만 보여줘요 (늦게 도착한 예전 응답이 섞이지 않게)
  const currentCheck = usernameCheck?.username === form.username ? usernameCheck : null
  const usernameHint = currentCheck ? USERNAME_HINT[currentCheck.reason] : null

  return (
    <div className="mx-auto max-w-sm rounded-lg border border-gray-200 bg-white p-6">
      <h1 className="mb-6 text-xl font-bold">회원가입</h1>
      <form onSubmit={submit} className="space-y-4">
        <FormField label="이메일" name="email" type="email" value={form.email} onChange={change} error={fieldErrors.email} required autoComplete="email" />
        <FormField
          label="아이디 (블로그 주소)"
          name="username"
          value={form.username}
          onChange={change}
          error={fieldErrors.username}
          hint={usernameHint ? usernameHint.text : form.username && `ownlog.java21.net/@${form.username}`}
          hintTone={usernameHint?.tone}
          required
          maxLength={20}
          autoComplete="username"
        />
        <FormField label="닉네임" name="nickname" value={form.nickname} onChange={change} error={fieldErrors.nickname} required maxLength={20} />
        <FormField
          label="비밀번호"
          name="password"
          type="password"
          value={form.password}
          onChange={change}
          error={fieldErrors.password}
          hint="8~64자, 영문과 숫자를 포함해 주세요."
          required
          autoComplete="new-password"
        />
        <FormField
          label="비밀번호 확인"
          name="passwordConfirm"
          type="password"
          value={form.passwordConfirm}
          onChange={change}
          error={fieldErrors.passwordConfirm}
          required
          autoComplete="new-password"
        />
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-indigo-600 py-2 font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
        >
          {submitting ? '가입하는 중…' : '가입하기'}
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-gray-600">
        이미 회원이신가요?{' '}
        <Link to="/login" className="text-indigo-600 hover:underline">
          로그인
        </Link>
      </p>
    </div>
  )
}
