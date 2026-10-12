import { forceCollide, forceLink, forceManyBody, forceSimulation, forceX, forceY } from 'd3-force'
import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { NO_TOPIC_COLOR, TOPIC_COLORS } from '../utils/topicColors'
const LOCKED = '#8b5cf6'

const radiusOf = (n) => (n.locked ? 5 : Math.min(18, 4 + Math.sqrt((n.linkCount ?? 0) * 4 + (n.likeCount ?? 0)) * 2))

/**
 * 기록 그래프 캔버스 (GR-02 ~ GR-04): d3-force가 자리를 계산하고 Canvas에 직접 그려요.
 * 끌기(빈 곳: 화면 이동, 별: 별 이동), 휠·두 손가락으로 확대, 별을 누르면 onSelect(node).
 * focusId가 바뀌면 그 별로 화면을 옮겨요. highlightIds는 검색 결과 강조예요.
 */
export default function UniverseCanvas({ graph, onSelect, highlightIds, focusId }) {
  const wrapRef = useRef(null)
  const canvasRef = useRef(null)
  const stateRef = useRef(null) // 매 프레임 쓰는 값 (렌더링과 무관해서 ref에 둬요)
  const [hover, setHover] = useState(null) // { node, x, y }
  const select = useEffectEvent((node) => onSelect(node))

  // 시뮬레이션과 입력 처리: 그래프가 바뀔 때만 새로 만들어요
  useEffect(() => {
    const canvas = canvasRef.current
    const wrap = wrapRef.current
    const ctx = canvas.getContext('2d')
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

    const nodes = graph.nodes.map((n) => ({ ...n, r: radiusOf(n) }))
    const byId = new Map(nodes.map((n) => [n.id, n]))
    const links = graph.edges
      .filter((e) => byId.has(e.source) && byId.has(e.target))
      .map((e) => ({ ...e, source: e.source, target: e.target }))

    const s = {
      nodes, links, byId, view: { x: 0, y: 0, k: 1 }, width: 0, height: 0, dpr: window.devicePixelRatio || 1,
      highlight: new Set(), hovered: null, userMoved: false, pointers: new Map(), drag: null, pinch: null, raf: 0, start: performance.now(),
      dust: Array.from({ length: 120 }, () => ({ x: Math.random(), y: Math.random(), r: Math.random() * 1.2, a: Math.random() })),
    }
    stateRef.current = s

    const sim = forceSimulation(nodes)
      .force('link', forceLink(links).id((d) => d.id)
        .distance((l) => (l.type === 'LINK' ? 90 : 140))
        .strength((l) => (l.type === 'LINK' ? 0.7 : Math.min(0.4, 0.08 * (l.weight ?? 1)))))
      .force('charge', forceManyBody().strength(-320))
      .force('x', forceX(0).strength(0.04))
      .force('y', forceY(0).strength(0.04))
      .force('collide', forceCollide((d) => d.r + 14))
    if (reduceMotion) {
      sim.stop()
      sim.tick(300)
    }

    const resize = () => {
      const rect = wrap.getBoundingClientRect()
      s.width = rect.width
      s.height = rect.height
      canvas.width = rect.width * s.dpr
      canvas.height = rect.height * s.dpr
      if (!s.view.placed) Object.assign(s.view, { x: rect.width / 2, y: rect.height / 2, placed: true })
    }
    resize()
    const observer = new ResizeObserver(resize)
    observer.observe(wrap)

    const toWorld = (sx, sy) => ({ x: (sx - s.view.x) / s.view.k, y: (sy - s.view.y) / s.view.k })
    const nodeAt = (sx, sy) => {
      const p = toWorld(sx, sy)
      let best = null
      for (const n of nodes) {
        const d = Math.hypot(n.x - p.x, n.y - p.y)
        if (d <= n.r + 6 / s.view.k && (!best || d < best.d)) best = { n, d }
      }
      return best?.n ?? null
    }
    const local = (e) => {
      const rect = canvas.getBoundingClientRect()
      return { x: e.clientX - rect.left, y: e.clientY - rect.top }
    }
    const zoomAt = (sx, sy, factor) => {
      const k = Math.min(4, Math.max(0.25, s.view.k * factor))
      const p = toWorld(sx, sy)
      s.view.k = k
      s.view.x = sx - p.x * k
      s.view.y = sy - p.y * k
    }

    const onDown = (e) => {
      s.userMoved = true // 직접 움직이기 시작하면 자동 맞춤을 멈춰요
      canvas.setPointerCapture(e.pointerId)
      const p = local(e)
      s.pointers.set(e.pointerId, p)
      if (s.pointers.size === 2) {
        const [a, b] = [...s.pointers.values()]
        s.pinch = { dist: Math.hypot(a.x - b.x, a.y - b.y) }
        s.drag = null
        return
      }
      const node = nodeAt(p.x, p.y)
      s.drag = { node, startX: p.x, startY: p.y, viewX: s.view.x, viewY: s.view.y, moved: false }
      if (node) {
        node.fx = node.x
        node.fy = node.y
        if (!reduceMotion) sim.alphaTarget(0.2).restart()
      }
    }
    const onMove = (e) => {
      const p = local(e)
      if (s.pointers.has(e.pointerId)) s.pointers.set(e.pointerId, p)
      if (s.pinch && s.pointers.size === 2) {
        const [a, b] = [...s.pointers.values()]
        const dist = Math.hypot(a.x - b.x, a.y - b.y)
        zoomAt((a.x + b.x) / 2, (a.y + b.y) / 2, dist / s.pinch.dist)
        s.pinch.dist = dist
        return
      }
      if (s.drag) {
        const dx = p.x - s.drag.startX
        const dy = p.y - s.drag.startY
        if (Math.hypot(dx, dy) > 4) s.drag.moved = true
        if (s.drag.node) {
          const w = toWorld(p.x, p.y)
          s.drag.node.fx = w.x
          s.drag.node.fy = w.y
          if (reduceMotion) Object.assign(s.drag.node, { x: w.x, y: w.y })
        } else {
          s.view.x = s.drag.viewX + dx
          s.view.y = s.drag.viewY + dy
        }
        return
      }
      const node = e.pointerType === 'mouse' ? nodeAt(p.x, p.y) : null
      if (node !== s.hovered) {
        s.hovered = node
        canvas.style.cursor = node ? 'pointer' : 'grab'
        setHover(node ? { node, x: Math.min(p.x + 14, s.width - 220), y: p.y + 14 } : null)
      }
    }
    const onUp = (e) => {
      s.pointers.delete(e.pointerId)
      if (s.pointers.size < 2) s.pinch = null
      const drag = s.drag
      s.drag = null
      if (!drag) return
      if (drag.node) {
        drag.node.fx = null
        drag.node.fy = null
        sim.alphaTarget(0)
        if (!drag.moved) select(drag.node)
      }
    }
    const onWheel = (e) => {
      e.preventDefault()
      s.userMoved = true
      const p = local(e)
      zoomAt(p.x, p.y, Math.exp(-e.deltaY * 0.0015))
    }
    canvas.addEventListener('pointerdown', onDown)
    canvas.addEventListener('pointermove', onMove)
    canvas.addEventListener('pointerup', onUp)
    canvas.addEventListener('pointercancel', onUp)
    canvas.addEventListener('wheel', onWheel, { passive: false })

    // 자동 맞춤: 사용자가 움직이기 전까지 모든 별이 화면에 들어오게 부드럽게 확대·이동해요
    const fitView = () => {
      if (s.userMoved || !nodes.length || !s.width) return
      let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity
      for (const n of nodes) {
        minX = Math.min(minX, n.x - n.r); maxX = Math.max(maxX, n.x + n.r)
        minY = Math.min(minY, n.y - n.r); maxY = Math.max(maxY, n.y + n.r + 18)
      }
      const pad = 48
      const k = Math.min(2, Math.max(0.25, Math.min((s.width - pad * 2) / (maxX - minX || 1), (s.height - pad * 2) / (maxY - minY || 1))))
      const tx = s.width / 2 - ((minX + maxX) / 2) * k
      const ty = s.height / 2 - ((minY + maxY) / 2) * k
      s.view.k += (k - s.view.k) * 0.08
      s.view.x += (tx - s.view.x) * 0.08
      s.view.y += (ty - s.view.y) * 0.08
    }

    const draw = (time) => {
      fitView()
      const { width, height, dpr, view } = s
      const t = (time - s.start) / 1000
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      ctx.clearRect(0, 0, width, height)
      for (const d of s.dust) {   // 배경의 먼 별 (반짝임)
        ctx.globalAlpha = 0.15 + 0.25 * Math.abs(Math.sin(t * 0.6 + d.a * 10))
        ctx.fillStyle = '#fff'
        ctx.beginPath()
        ctx.arc(d.x * width, d.y * height, d.r, 0, Math.PI * 2)
        ctx.fill()
      }
      ctx.globalAlpha = 1
      ctx.setTransform(dpr * view.k, 0, 0, dpr * view.k, dpr * view.x, dpr * view.y)

      for (const l of links) {   // 선: 태그(흐린 점선) → 링크(밝은 실선 + 화살표)
        const a = l.source, b = l.target
        if (l.type === 'TAG') {
          ctx.setLineDash([3, 4])
          ctx.strokeStyle = `rgba(148,163,184,${Math.min(0.5, 0.12 + 0.08 * (l.weight ?? 1))})`
          ctx.lineWidth = 1 / view.k
        } else {
          ctx.setLineDash([])
          ctx.strokeStyle = 'rgba(165,180,252,0.85)'
          ctx.lineWidth = 1.6 / view.k
        }
        ctx.beginPath()
        ctx.moveTo(a.x, a.y)
        ctx.lineTo(b.x, b.y)
        ctx.stroke()
        if (l.type === 'LINK') {
          const angle = Math.atan2(b.y - a.y, b.x - a.x)
          const tx = b.x - Math.cos(angle) * (b.r + 3), ty = b.y - Math.sin(angle) * (b.r + 3)
          const size = 6 / Math.sqrt(view.k)
          ctx.fillStyle = 'rgba(165,180,252,0.95)'
          ctx.beginPath()
          ctx.moveTo(tx, ty)
          ctx.lineTo(tx - size * Math.cos(angle - 0.45), ty - size * Math.sin(angle - 0.45))
          ctx.lineTo(tx - size * Math.cos(angle + 0.45), ty - size * Math.sin(angle + 0.45))
          ctx.fill()
        }
      }
      ctx.setLineDash([])

      const labels = []
      for (const n of nodes) {   // 별: 토픽 색 + 빛번짐, 잠긴 캡슐은 보라색으로 천천히 깜빡여요
        const color = n.locked ? LOCKED : TOPIC_COLORS[n.topic] ?? NO_TOPIC_COLOR
        ctx.globalAlpha = n.locked ? 0.35 + 0.35 * Math.abs(Math.sin(t * 1.5 + n.id)) : 1
        ctx.shadowColor = color
        ctx.shadowBlur = (n.locked ? 8 : 14) * view.k
        ctx.fillStyle = color
        ctx.beginPath()
        ctx.arc(n.x, n.y, n.r, 0, Math.PI * 2)
        ctx.fill()
        ctx.shadowBlur = 0
        ctx.globalAlpha = 1
        if (s.highlight.has(n.id) || n === s.hovered) {
          ctx.strokeStyle = '#fff'
          ctx.lineWidth = 2 / view.k
          ctx.beginPath()
          ctx.arc(n.x, n.y, n.r + 4 / view.k, 0, Math.PI * 2)
          ctx.stroke()
        }
        labels.push(n)
      }

      // 이름: 강조된 별 → 큰 별 순으로, 앞서 그린 이름과 겹치면 건너뛰어요 (확대하면 더 많이 보여요)
      ctx.font = `${12 / view.k}px Pretendard, system-ui, sans-serif`
      ctx.textAlign = 'center'
      const placed = []
      const priority = (n) => (s.highlight.has(n.id) || n === s.hovered ? 1000 : 0) + n.r
      for (const n of labels.sort((a, b) => priority(b) - priority(a))) {
        const text = (n.locked ? '🔒 ' : '') + (n.title.length > 16 ? `${n.title.slice(0, 16)}…` : n.title)
        const w = ctx.measureText(text).width, h = 14 / view.k
        const box = { x: n.x - w / 2, y: n.y + n.r + 4 / view.k, w, h }
        if (placed.some((p) => box.x < p.x + p.w && box.x + box.w > p.x && box.y < p.y + p.h && box.y + box.h > p.y)) continue
        placed.push(box)
        ctx.fillStyle = s.highlight.has(n.id) ? '#fff' : n.locked ? 'rgba(196,181,253,0.8)' : 'rgba(226,232,240,0.85)'
        ctx.fillText(text, n.x, box.y + h * 0.8)
      }
      canvas.dataset.settled = String(reduceMotion || sim.alpha() < 0.05)
      s.raf = requestAnimationFrame(draw)
    }
    s.raf = requestAnimationFrame(draw)

    // 화면 점검(헤드리스 Chrome)용: 별의 화면 좌표
    canvas.__universe = { screenOf: (id) => { const n = byId.get(id); return n && { x: n.x * s.view.k + s.view.x, y: n.y * s.view.k + s.view.y } } }

    return () => {
      cancelAnimationFrame(s.raf)
      sim.stop()
      observer.disconnect()
      canvas.removeEventListener('pointerdown', onDown)
      canvas.removeEventListener('pointermove', onMove)
      canvas.removeEventListener('pointerup', onUp)
      canvas.removeEventListener('pointercancel', onUp)
      canvas.removeEventListener('wheel', onWheel)
    }
  }, [graph])

  // 검색 강조
  useEffect(() => {
    if (stateRef.current) stateRef.current.highlight = new Set(highlightIds)
  }, [highlightIds])

  // 검색에서 고른 별로 화면 옮기기 (조금 확대)
  useEffect(() => {
    const s = stateRef.current
    const node = s?.byId.get(focusId)
    if (!node) return
    s.userMoved = true
    s.view.k = Math.max(s.view.k, 1.4)
    s.view.x = s.width / 2 - node.x * s.view.k
    s.view.y = s.height / 2 - node.y * s.view.k
  }, [focusId])

  return (
    <div ref={wrapRef} className="relative h-[480px] overflow-hidden rounded-2xl bg-[radial-gradient(ellipse_at_center,#1e1b4b_0%,#0f172a_55%,#020617_100%)] sm:h-[620px]">
      <canvas
        ref={canvasRef}
        data-nodes={graph.nodes.length}
        aria-label={`기록 우주: 글 ${graph.nodes.length}개, 연결 ${graph.edges.length}개`}
        role="img"
        className="h-full w-full touch-none cursor-grab"
      />
      {hover && (
        <div className="pointer-events-none absolute z-10 max-w-64 rounded-md bg-white/95 px-3 py-2 text-xs text-gray-800 shadow-lg"
          style={{ left: Math.max(0, hover.x), top: hover.y }}>
          <p className="font-semibold">{hover.node.locked ? '🔒 ' : ''}{hover.node.title}</p>
          {hover.node.locked ? (
            <p className="mt-0.5 text-violet-700">봉인된 타임캡슐</p>
          ) : (
            <p className="mt-0.5 text-gray-500">
              연결 {hover.node.linkCount} · ♥ {hover.node.likeCount}
              {hover.node.tags?.length > 0 && ` · ${hover.node.tags.map((t) => `#${t}`).join(' ')}`}
            </p>
          )}
        </div>
      )}
    </div>
  )
}
