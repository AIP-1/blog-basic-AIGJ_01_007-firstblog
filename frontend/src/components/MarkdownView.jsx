import { useRef, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import { Link } from 'react-router'
import rehypeSanitize from 'rehype-sanitize'
import { formatDate } from '../utils/format'
import remarkPostLinks, { LOCKED_PREFIX } from '../utils/remarkPostLinks'

/** [[글]] 링크 미리보기 카드 (LK-06): 마우스를 올리거나 키보드로 포커스하면 잠깐 뒤에 떠요 */
function PostLink({ href, title, target, children }) {
  const [open, setOpen] = useState(false)
  const timer = useRef(null)
  const show = () => {
    clearTimeout(timer.current)
    timer.current = setTimeout(() => setOpen(true), 250)
  }
  const hide = () => {
    clearTimeout(timer.current)
    timer.current = setTimeout(() => setOpen(false), 120)
  }

  return (
    <span className="relative inline-block" onMouseEnter={show} onMouseLeave={hide} onFocus={show} onBlur={hide}>
      <Link to={href} title={title} className="post-link">
        🔗 {children}
      </Link>
      {open && target && (
        <span role="tooltip"
          className="absolute left-0 top-full z-30 mt-1 block w-72 overflow-hidden rounded-lg border border-gray-200 bg-white text-left text-sm font-normal text-gray-800 no-underline shadow-xl">
          {target.thumbnailUrl && <img src={target.thumbnailUrl} alt="" className="block h-28 w-full object-cover" />}
          <span className="block space-y-1 p-3">
            <span className="block font-semibold text-gray-900">{target.title}</span>
            <span className="block text-xs text-gray-500">
              {target.nickname} · {formatDate(target.publishedAt)}
            </span>
            {target.summary && <span className="line-clamp-3 block text-gray-600">{target.summary}</span>}
          </span>
        </span>
      )}
    </span>
  )
}

/** 본문 링크: [[글]] 링크는 화면 안에서 이동(미리보기 카드), 볼 수 없는 글은 회색 글자로 보여줘요 (LK-02) */
function MarkdownLink({ href = '', children, title, linkedPosts }) {
  if (href.startsWith(LOCKED_PREFIX)) {
    return (
      <span className="rounded bg-gray-100 px-1 text-gray-400 no-underline" title="볼 수 없는 글이에요">
        🔒 볼 수 없는 글
      </span>
    )
  }
  if (href.startsWith('/@')) {
    const id = href.match(/\/posts\/(\d+)$/)?.[1]
    return <PostLink href={href} title={title} target={id && linkedPosts?.[id]}>{children}</PostLink>
  }
  return (
    <a href={href} title={title} target="_blank" rel="noopener noreferrer nofollow">
      {children}
    </a>
  )
}

/**
 * 마크다운 본문 표시. rehype-sanitize로 스크립트·위험한 속성을 제거해요 (XSS 방지, 요구사항 5장 보안).
 * 토큰을 localStorage에 두기로 한 결정(8장)의 전제 조건이에요.
 * linkedPosts: 글 상세 응답의 [[글]] 링크 대상 정보 (API 6.5)
 */
export default function MarkdownView({ content, linkedPosts }) {
  return (
    <div className="markdown">
      <ReactMarkdown
        remarkPlugins={[[remarkPostLinks, { linkedPosts }]]}
        rehypePlugins={[rehypeSanitize]}
        components={{ a: (props) => <MarkdownLink {...props} linkedPosts={linkedPosts} /> }}
      >
        {content ?? ''}
      </ReactMarkdown>
    </div>
  )
}
