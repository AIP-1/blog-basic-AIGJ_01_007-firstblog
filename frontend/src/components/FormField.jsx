/** 라벨 + 입력칸 + 아래 안내/에러 문구. 회원가입, 로그인, 설정 화면이 함께 써요 */
export default function FormField({ label, error, hint, hintTone = 'gray', ...inputProps }) {
  const hintColor = { gray: 'text-gray-500', green: 'text-green-600', red: 'text-red-600' }[hintTone]
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-gray-700">{label}</span>
      <input
        {...inputProps}
        className={`w-full rounded-md border px-3 py-2 text-sm focus:outline-none ${
          error ? 'border-red-400 focus:border-red-500' : 'border-gray-300 focus:border-indigo-500'
        }`}
      />
      {error ? (
        <span className="mt-1 block text-xs text-red-600">{error}</span>
      ) : (
        hint && <span className={`mt-1 block text-xs ${hintColor}`}>{hint}</span>
      )}
    </label>
  )
}
