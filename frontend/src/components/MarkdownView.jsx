import ReactMarkdown from 'react-markdown'
import rehypeSanitize from 'rehype-sanitize'

/**
 * 마크다운 본문 표시. rehype-sanitize로 스크립트·위험한 속성을 제거해요 (XSS 방지, 요구사항 5장 보안).
 * 토큰을 localStorage에 두기로 한 결정(8장)의 전제 조건이에요.
 */
export default function MarkdownView({ content }) {
  return (
    <div className="markdown">
      <ReactMarkdown rehypePlugins={[rehypeSanitize]}>{content ?? ''}</ReactMarkdown>
    </div>
  )
}
