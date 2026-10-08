import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { errorMessage, fieldErrorsOf } from '../api/client'
import { uploadImage } from '../api/images'
import { changePassword, updateBlog, updateProfile, withdraw } from '../api/users'
import { useAuth } from '../auth/useAuth'
import FormField from '../components/FormField'

const Card = ({ title, children }) => (
  <section className="rounded-lg border border-gray-200 bg-white p-5">
    <h2 className="mb-4 font-semibold">{title}</h2>
    {children}
  </section>
)

/** 마이페이지 (U-04 ~ U-06): 프로필, 블로그 정보, 비밀번호, 탈퇴 */
export default function SettingsPage() {
  const { user, reloadMe, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold">설정</h1>
        <Link to="/settings/categories" className="text-sm text-indigo-600 hover:underline">
          카테고리 관리 →
        </Link>
      </div>
      <ProfileForm user={user} onSaved={reloadMe} />
      <BlogForm user={user} onSaved={reloadMe} />
      <PasswordForm />
      <WithdrawForm
        onDone={() => {
          logout()
          navigate('/', { replace: true })
        }}
      />
    </div>
  )
}

function useSubmit() {
  const [state, setState] = useState({ busy: false, message: '', error: '', fieldErrors: {} })
  const submit = async (action, successMessage) => {
    setState({ busy: true, message: '', error: '', fieldErrors: {} })
    try {
      await action()
      setState({ busy: false, message: successMessage, error: '', fieldErrors: {} })
      return true
    } catch (err) {
      const fieldErrors = fieldErrorsOf(err)
      setState({ busy: false, message: '', error: Object.keys(fieldErrors).length ? '' : errorMessage(err), fieldErrors })
      return false
    }
  }
  return [state, submit]
}

function Feedback({ state }) {
  if (state.message) return <p className="text-sm text-green-600">{state.message}</p>
  if (state.error) return <p className="text-sm text-red-600">{state.error}</p>
  return null
}

function ProfileForm({ user, onSaved }) {
  const [form, setForm] = useState({ nickname: user.nickname, bio: user.bio ?? '', profileImageUrl: user.profileImageUrl })
  const [state, submit] = useSubmit()
  const fileInput = useRef(null)

  const pickImage = async (file) => {
    if (!file) return
    await submit(async () => {
      const url = await uploadImage(file)
      setForm((f) => ({ ...f, profileImageUrl: url }))
    }, '사진을 올렸어요. [프로필 저장]을 눌러야 반영돼요.')
  }

  return (
    <Card title="프로필">
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          submit(async () => {
            await updateProfile({ ...form, bio: form.bio.trim() || null })
            await onSaved()
          }, '프로필을 저장했어요.')
        }}
      >
        <div className="flex items-center gap-4">
          {form.profileImageUrl ? (
            <img src={form.profileImageUrl} alt="프로필 사진" className="h-16 w-16 rounded-full object-cover" />
          ) : (
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-indigo-100 text-xl font-bold text-indigo-600">
              {form.nickname.slice(0, 1)}
            </div>
          )}
          <button type="button" onClick={() => fileInput.current.click()} className="rounded-md border border-gray-300 px-3 py-1.5 text-sm">
            사진 바꾸기
          </button>
          {form.profileImageUrl && (
            <button type="button" onClick={() => setForm({ ...form, profileImageUrl: null })} className="text-sm text-gray-500 hover:text-red-600">
              사진 지우기
            </button>
          )}
          <input ref={fileInput} type="file" accept="image/jpeg,image/png,image/gif,image/webp" hidden aria-label="프로필 사진 파일"
            onChange={(e) => pickImage(e.target.files[0])} />
        </div>
        <FormField label="닉네임" name="nickname" value={form.nickname} maxLength={20} required error={state.fieldErrors.nickname}
          onChange={(e) => setForm({ ...form, nickname: e.target.value })} />
        <FormField label="한 줄 소개" name="bio" value={form.bio} maxLength={100} error={state.fieldErrors.bio}
          onChange={(e) => setForm({ ...form, bio: e.target.value })} />
        <Feedback state={state} />
        <button type="submit" disabled={state.busy} className="rounded-md bg-indigo-600 px-4 py-2 text-sm text-white disabled:opacity-50">
          프로필 저장
        </button>
      </form>
    </Card>
  )
}

function BlogForm({ user, onSaved }) {
  const [form, setForm] = useState({ name: user.blog.name, description: user.blog.description ?? '' })
  const [state, submit] = useSubmit()
  return (
    <Card title="블로그">
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          submit(async () => {
            await updateBlog(user.blog.id, { name: form.name, description: form.description.trim() || null })
            await onSaved()
          }, '블로그 정보를 저장했어요.')
        }}
      >
        <FormField label="블로그 이름" name="blogName" value={form.name} maxLength={50} required error={state.fieldErrors.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })} />
        <FormField label="블로그 소개" name="description" value={form.description} maxLength={200} error={state.fieldErrors.description}
          onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <Feedback state={state} />
        <button type="submit" disabled={state.busy} className="rounded-md bg-indigo-600 px-4 py-2 text-sm text-white disabled:opacity-50">
          블로그 저장
        </button>
      </form>
    </Card>
  )
}

function PasswordForm() {
  const EMPTY = { currentPassword: '', newPassword: '', newPasswordConfirm: '' }
  const [form, setForm] = useState(EMPTY)
  const [state, submit] = useSubmit()
  const change = (e) => setForm({ ...form, [e.target.name]: e.target.value })
  return (
    <Card title="비밀번호 변경">
      <form
        className="space-y-4"
        onSubmit={async (e) => {
          e.preventDefault()
          if (await submit(() => changePassword(form), '비밀번호를 바꿨어요.')) setForm(EMPTY)
        }}
      >
        <FormField label="현재 비밀번호" name="currentPassword" type="password" value={form.currentPassword} onChange={change} required
          autoComplete="current-password" error={state.fieldErrors.currentPassword} />
        <FormField label="새 비밀번호" name="newPassword" type="password" value={form.newPassword} onChange={change} required
          autoComplete="new-password" hint="8~64자, 영문과 숫자를 포함해 주세요." error={state.fieldErrors.newPassword} />
        <FormField label="새 비밀번호 확인" name="newPasswordConfirm" type="password" value={form.newPasswordConfirm} onChange={change} required
          autoComplete="new-password" error={state.fieldErrors.newPasswordConfirm} />
        <Feedback state={state} />
        <button type="submit" disabled={state.busy} className="rounded-md bg-indigo-600 px-4 py-2 text-sm text-white disabled:opacity-50">
          비밀번호 변경
        </button>
      </form>
    </Card>
  )
}

function WithdrawForm({ onDone }) {
  const [password, setPassword] = useState('')
  const [state, submit] = useSubmit()
  return (
    <Card title="회원 탈퇴">
      <p className="mb-4 text-sm text-gray-600">
        탈퇴하면 내 블로그와 글이 모두 삭제되고 되돌릴 수 없어요. 다른 사람 글에 단 댓글은 &quot;탈퇴한 사용자&quot;로 남아요.
      </p>
      <form
        className="space-y-4"
        onSubmit={async (e) => {
          e.preventDefault()
          if (!window.confirm('정말 탈퇴할까요? 되돌릴 수 없어요.')) return
          if (await submit(() => withdraw(password), '')) onDone()
        }}
      >
        <FormField label="비밀번호 확인" name="withdrawPassword" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
          required autoComplete="current-password" />
        <Feedback state={state} />
        <button type="submit" disabled={state.busy} className="rounded-md bg-red-600 px-4 py-2 text-sm text-white disabled:opacity-50">
          탈퇴하기
        </button>
      </form>
    </Card>
  )
}
