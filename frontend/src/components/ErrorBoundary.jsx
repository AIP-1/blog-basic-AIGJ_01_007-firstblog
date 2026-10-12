import { Component } from 'react'

const RELOAD_KEY = 'ownlog.chunkReloadAt'

/** 새로 배포되면 예전 화면 파일(해시 이름)이 사라져서, 열어 둔 화면이 지연 로딩할 때 실패해요 */
function isChunkLoadError(error) {
  return /dynamically imported module|Importing a module script failed|error loading dynamically imported module/i.test(
    String(error?.message ?? error),
  )
}

/**
 * 화면 하나에서 렌더링 오류가 나도 앱 전체가 하얗게 사라지지 않게 막아요.
 * 배포 직후 화면 파일을 못 불러온 경우는 새 버전을 받도록 한 번만 새로고침해요 (1분 안에 또 나면 안내만 해요).
 */
export default class ErrorBoundary extends Component {
  state = { error: null }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error) {
    if (!isChunkLoadError(error)) return
    try {
      const last = Number(sessionStorage.getItem(RELOAD_KEY) ?? 0)
      if (Date.now() - last > 60_000) {
        sessionStorage.setItem(RELOAD_KEY, String(Date.now()))
        window.location.reload()
      }
    } catch {
      // 저장소를 못 쓰면 새로고침하지 않고 안내만 보여줘요 (무한 새로고침 방지)
    }
  }

  render() {
    if (!this.state.error) return this.props.children
    const updated = isChunkLoadError(this.state.error)
    return (
      <div className="py-16 text-center">
        <p className="text-4xl">{updated ? '✨' : '😵'}</p>
        <p className="mt-4 font-semibold">{updated ? '새 버전이 배포됐어요.' : '화면을 그리다가 문제가 생겼어요.'}</p>
        <p className="mt-1 text-sm text-gray-500">새로고침하면 다시 볼 수 있어요.</p>
        <button type="button" onClick={() => window.location.reload()}
          className="mt-6 rounded-md bg-indigo-600 px-4 py-2 text-sm text-white">
          새로고침
        </button>
      </div>
    )
  }
}
