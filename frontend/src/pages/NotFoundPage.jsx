import { Link } from 'react-router'

export default function NotFoundPage({ message = '존재하지 않는 페이지예요' }) {
  return (
    <section className="py-16 text-center">
      <h1 className="text-2xl font-bold">{message}</h1>
      <Link to="/" className="mt-4 inline-block text-indigo-600 hover:underline">
        메인으로 가기
      </Link>
    </section>
  )
}
