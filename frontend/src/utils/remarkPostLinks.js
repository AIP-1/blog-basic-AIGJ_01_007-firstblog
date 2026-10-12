/**
 * remark 플러그인: 본문의 [[글번호|표시 글자]]를 링크로 바꿔요 (LK-02).
 * - 볼 수 있는 글 → /@아이디/posts/번호 (화면 이동은 MarkdownView의 a 컴포넌트가 해요)
 * - 볼 수 없는 글 → #locked-post-번호 (회색 "볼 수 없는 글"로 그려요)
 * 마크다운 트리(AST)만 바꾸고 HTML 문자열을 만들지 않아서, 뒤에 오는 rehype-sanitize가 그대로 XSS를 막아요.
 * 코드(code, inlineCode) 안은 text 노드가 아니라서 저절로 빠져요. 서버 PostLinkParser와 같은 규칙이에요.
 */
const LINK = /\[\[(\d{1,18})\|([^\]\n]{1,100})\]\]/g

export const LOCKED_PREFIX = '#locked-post-'

export default function remarkPostLinks({ linkedPosts = {} } = {}) {
  const toNodes = (value) => {
    const nodes = []
    let last = 0
    for (const match of value.matchAll(LINK)) {
      const [whole, id, label] = match
      if (match.index > last) nodes.push({ type: 'text', value: value.slice(last, match.index) })
      const target = linkedPosts[id]
      nodes.push({
        type: 'link',
        url: target?.accessible ? `/@${target.username}/posts/${id}` : `${LOCKED_PREFIX}${id}`,
        title: target?.accessible ? target.title : null,
        children: [{ type: 'text', value: label }],
      })
      last = match.index + whole.length
    }
    if (last < value.length) nodes.push({ type: 'text', value: value.slice(last) })
    return nodes
  }

  const walk = (node) => {
    if (!node.children || node.type === 'link' || node.type === 'linkReference') return
    node.children = node.children.flatMap((child) =>
      child.type === 'text' && child.value.includes('[[') ? toNodes(child.value) : (walk(child), [child]),
    )
  }

  return (tree) => walk(tree)
}
