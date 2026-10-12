import ReactMarkdown from 'react-markdown'
import { Link } from 'react-router'
import rehypeSanitize from 'rehype-sanitize'
import remarkPostLinks, { LOCKED_PREFIX } from '../utils/remarkPostLinks'

/** 본문 링크: [[글]] 링크는 화면 안에서 이동하고, 볼 수 없는 글은 회색 글자로 보여줘요 (LK-02) */
function MarkdownLink({ href = '', children, title }) {
  if (href.startsWith(LOCKED_PREFIX)) {
    return (
      <span className="rounded bg-gray-100 px-1 text-gray-400 no-underline" title="볼 수 없는 글이에요">
        🔒 볼 수 없는 글
      </span>
    )
  }
  if (href.startsWith('/@')) {
    return (
      <Link to={href} title={title} className="post-link">
        🔗 {children}
      </Link>
    )
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
        components={{ a: MarkdownLink }}
      >
        {content ?? ''}
      </ReactMarkdown>
    </div>
  )
}
