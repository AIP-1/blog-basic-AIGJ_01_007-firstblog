import { Link } from 'react-router'
import { useAuth } from '../auth/useAuth'

/** 한 기능 카드: 무엇인지, 어떻게 쓰는지(단계), 바로 해 보기 링크 */
function Feature({ icon, title, summary, steps, tryLink, tryLabel, accent = false }) {
  return (
    <article className={`flex flex-col rounded-xl border p-5 shadow-sm ${accent ? 'border-indigo-200 bg-gradient-to-br from-white to-indigo-50/60' : 'border-gray-200 bg-white'}`}>
      <h3 className="text-lg font-bold">
        <span aria-hidden="true">{icon}</span> {title}
      </h3>
      <p className="mt-1 text-sm text-gray-600">{summary}</p>
      <ol className="mt-3 list-decimal space-y-1 pl-5 text-sm text-gray-700">
        {steps.map((step) => <li key={step}>{step}</li>)}
      </ol>
      {tryLink && (
        <Link to={tryLink} className="mt-4 self-start rounded-full bg-indigo-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-700">
          {tryLabel} →
        </Link>
      )}
    </article>
  )
}

/**
 * 기능 가이드 /guide: OwnLog에서 할 수 있는 일을 한 화면에 모아 보여주고, 바로 해 볼 수 있는 화면으로 연결해요.
 * 처음 온 사람·베타 테스터·발표 청중이 "무엇을 눌러 봐야 하는지" 알 수 있게 하는 게 목표예요.
 */
export default function GuidePage() {
  const { user } = useAuth()
  const writeLink = user ? '/write' : '/login?redirect=%2Fwrite'

  return (
    <div className="space-y-10">
      <section className="bg-cosmos rounded-2xl px-6 py-10 text-white shadow-lg sm:px-10">
        <p className="text-sm tracking-widest text-indigo-200">OWNLOG · 사용 가이드</p>
        <h1 className="mt-2 text-3xl font-extrabold">OwnLog에서 할 수 있는 일</h1>
        <p className="mt-3 max-w-2xl text-sm leading-6 text-indigo-100/90 sm:text-base">
          OwnLog는 누구나 자기 블로그를 갖는 멀티유저 블로그예요. 글을 쓰고 나누는 기본 기능 위에,
          기록이 <strong className="text-white">시간</strong>과 <strong className="text-white">연결</strong>로 자라는
          <strong className="text-white"> 기록 유니버스</strong>가 있어요.
        </p>
        <div className="mt-5 rounded-lg bg-white/10 px-4 py-3 text-sm text-indigo-50">
          <p className="font-semibold">⏱ 3분 둘러보기</p>
          <p className="mt-1">🌌 우주에서 ▶ 타임랩스 → 글쓰기에서 <code>[[</code> 입력 → ⏳ 1분 뒤 열리는 타임캡슐 → 💌 받은 캡슐</p>
        </div>
      </section>

      <section aria-labelledby="start" className="space-y-3">
        <h2 id="start" className="text-xl font-bold">🚀 시작하기</h2>
        <div className="grid gap-4 md:grid-cols-2">
          <Feature icon="✍️" title="가입하면 내 블로그가 생겨요"
            summary="이메일과 아이디로 가입하면 바로 /@아이디 주소의 블로그가 만들어져요."
            steps={['오른쪽 위 회원가입 (아이디는 입력하는 동안 바로 확인해 줘요)', '설정에서 프로필 사진·닉네임, 블로그 이름·소개를 바꿔요', '카테고리 관리에서 카테고리를 만들고 순서·공개 범위를 정해요']}
            tryLink={user ? '/settings' : '/signup'} tryLabel={user ? '설정 열기' : '회원가입'} />
          <Feature icon="🧪" title="가입 없이 테스터 계정으로"
            summary="여러 사람이 함께 쓰는 체험용 계정이에요. 글·연결·받은 타임캡슐이 미리 들어 있어요."
            steps={['로그인: tester@demo.ownlog / tester1234', '비밀번호 변경과 탈퇴만 피해 주세요 (다른 테스터가 못 들어와요)', '써 보고 불편한 점을 알려 주시면 큰 도움이 돼요']}
            tryLink="/@tester" tryLabel="테스터 블로그 보기" />
        </div>
      </section>

      <section aria-labelledby="universe" className="space-y-3">
        <h2 id="universe" className="text-xl font-bold">✨ 기록 유니버스 (OwnLog만의 기능)</h2>
        <div className="grid gap-4 md:grid-cols-2">
          <Feature accent icon="⏳" title="타임캡슐"
            summary="지금 쓴 글을 정한 시각까지 봉인했다가 그때 공개해요. 미래의 나에게 보내는 기록이에요."
            steps={['글쓰기 아래 "⏳ 타임캡슐로 보내기"를 켜고 열리는 시각을 정해요 (1분 뒤 ~ 10년 뒤)', '봉인 중에는 다른 사람에게 제목과 남은 시간만 보여요. 나는 볼 수 있지만 고칠 수는 없어요', '시각이 되면 새로고침하지 않아도 화면에서 열리고, 메인 "최근 열린 타임캡슐"에 나와요']}
            tryLink={writeLink} tryLabel="1분 뒤 열리는 캡슐 만들기" />
          <Feature accent icon="💌" title="받는 사람이 있는 타임캡슐"
            summary="다른 회원에게 보내는 편지 같은 캡슐이에요. 보낸 사람과 받는 사람 둘만 볼 수 있어요."
            steps={['타임캡슐 상자에서 "💌 받는 사람"에 상대 아이디를 적어요', '받는 사람은 "💌 받은 캡슐"에서 남은 시간을 보고, 열리면 읽을 수 있어요', '그 밖의 사람에게는 존재하지 않는 글이에요']}
            tryLink={user ? '/capsules' : '/login?redirect=%2Fcapsules'} tryLabel="받은 캡슐 보기" />
          <Feature accent icon="🔗" title="글 연결 [[ ]]과 백링크"
            summary="글과 글을 이어서 생각의 흐름을 남겨요. 연결된 글 아래에는 '이 글을 언급한 글'이 자동으로 모여요."
            steps={['글쓰기 본문에서 [[ 를 입력하거나 "🔗 글 연결" 버튼을 눌러요', '제목을 입력해서 내 글이나 다른 사람의 공개 글을 골라요 (↑↓ Enter)', '읽을 때 🔗 링크에 마우스를 올리면 미리보기 카드가 떠요. 비공개 글은 제목도 보이지 않아요']}
            tryLink={writeLink} tryLabel="글 연결해 보기" />
          <Feature accent icon="🌌" title="기록 우주와 타임랩스"
            summary="글은 별, 연결과 같은 태그는 선이에요. 내 기록이 하나의 우주처럼 보여요."
            steps={['블로그 홈의 "🌌 기록 우주" 또는 헤더의 "🌌 우주"(모든 블로그)를 열어요', '끌어서 이동, 휠·두 손가락으로 확대, 별을 누르면 그 글로 가요', '▶ 타임랩스를 누르면 첫 기록부터 별이 하나씩 태어나요']}
            tryLink="/universe" tryLabel="OwnLog 우주 열기" />
          <Feature accent icon="🌱" title="기록 잔디"
            summary="블로그 홈에 1년 동안의 기록이 잔디로 쌓이고, 연속으로 기록한 날 수를 보여줘요."
            steps={['글을 쓴 날 칸이 진해져요 (타임캡슐은 봉인한 날)', '칸을 누르면 그날 쓴 글만 모아 봐요', '🔥 연속 기록을 이어 가 보세요']}
            tryLink={user ? `/@${user.username}` : '/@codewave'} tryLabel="잔디 보기" />
        </div>
      </section>

      <section aria-labelledby="basic" className="space-y-3">
        <h2 id="basic" className="text-xl font-bold">📚 기본 기능</h2>
        <div className="grid gap-4 md:grid-cols-2">
          <Feature icon="📝" title="글쓰기"
            summary="마크다운 편집기로 쓰고, 미리보기를 보면서 다듬어요."
            steps={['이미지·썸네일 올리기 (5MB, jpg·png·gif·webp), 썸네일이 없으면 본문 첫 이미지를 써요', '카테고리·토픽·태그(최대 10개)·공개 범위를 골라요', '"임시저장"으로 쓰다 만 글을 남기고, 임시저장 목록에서 이어 써요']}
            tryLink={writeLink} tryLabel="글쓰기" />
          <Feature icon="💬" title="읽고 반응하기"
            summary="마음에 드는 글에 반응을 남겨요."
            steps={['댓글과 답글 (수정하면 "(수정됨)"이 붙어요)', '♡ 좋아요, 북마크(헤더 "북마크"에서 모아 봐요)', '문제가 있는 글·댓글은 신고할 수 있어요']}
            tryLink="/" tryLabel="글 둘러보기" />
          <Feature icon="🔍" title="둘러보기"
            summary="새 글과 인기 글, 관심 주제의 글을 찾아요."
            steps={['메인의 최신·인기(최근 7일 좋아요 순) 탭', '토픽 칩(IT·여행·요리 …)으로 주제별 보기', '헤더 검색(제목·본문), 태그를 누르면 같은 태그의 글']}
            tryLink="/" tryLabel="메인으로" />
          <Feature icon="🔒" title="공개 범위"
            summary="글마다, 카테고리마다 공개 범위를 정할 수 있어요."
            steps={['비공개 글·비공개 카테고리의 글은 나만 봐요 (목록·검색·우주 어디에도 안 나와요)', '비공개 카테고리를 지우면 그 안의 글은 비공개로 옮겨져요', '다른 사람이 링크해도 볼 수 없는 글은 "🔒 볼 수 없는 글"로만 보여요']} />
        </div>
      </section>

      <section aria-labelledby="faq" className="space-y-3">
        <h2 id="faq" className="text-xl font-bold">❓ 자주 묻는 것</h2>
        <dl className="divide-y divide-gray-200 rounded-xl border border-gray-200 bg-white text-sm">
          {[
            ['타임캡슐을 열기 전에 고칠 수 있나요?', '아니요. 봉인한 뒤에는 열릴 때까지 수정할 수 없어요 (삭제는 할 수 있어요). 열린 뒤에는 일반 글처럼 고칠 수 있어요.'],
            ['받는 사람 캡슐을 다른 사람이 볼 수 있나요?', '아니요. 보낸 사람과 받는 사람 둘만 볼 수 있고, 항상 비공개예요.'],
            ['로그인이 풀렸어요.', '로그인은 2시간 동안 유지돼요. 다시 로그인하면 보던 화면으로 돌아가요.'],
            ['휴대폰에서도 되나요?', '네. 모든 화면이 휴대폰 크기에 맞춰져 있어요. 기록 우주는 두 손가락으로 확대할 수 있어요.'],
          ].map(([q, a]) => (
            <div key={q} className="px-5 py-4">
              <dt className="font-semibold">Q. {q}</dt>
              <dd className="mt-1 text-gray-600">{a}</dd>
            </div>
          ))}
        </dl>
      </section>
    </div>
  )
}
