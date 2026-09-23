import { useState, type FormEvent } from 'react';
import { createRoot } from 'react-dom/client';
import { auth, type User } from './auth';
import './style.css';
import { Portfolio } from './Portfolio';

function App() {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [user, setUser] = useState<User | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  async function run(action: () => Promise<void>) {
    setBusy(true); setError(''); setNotice('');
    try { await action(); }
    catch (e) { setError(e instanceof Error ? e.message : '요청을 완료하지 못했습니다.'); }
    finally { setBusy(false); }
  }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const email = String(data.get('email')).trim();
    const password = String(data.get('password'));
    void run(async () => {
      if (mode === 'register') {
        await auth.register(email, password); setMode('login'); setNotice('가입이 완료되었습니다. 로그인해 주세요.');
      } else setUser(await auth.login(email, password));
    });
  }
  return <main>
    <header><a className="brand" href="/">AI <strong>PB</strong><span>PERSONAL FINANCE PARTNER</span></a><span className="badge">PORTFOLIO DEMO</span></header>
    <div className={user ? 'layout account-layout' : 'layout'}><section className="intro">
      <p className="eyebrow">나를 이해하는 자산관리의 시작</p>
      <h1>좋은 상담은<br/><em>좋은 준비</em>에서.</h1>
      <p className="description">흩어진 자산과 막연한 목표를 정리하고,<br/>나에게 필요한 질문을 함께 찾아갑니다.</p>
      <ol><li><b>01</b><div>내 자산 이해하기<small>자산과 투자성향을 한눈에</small></div></li><li><b>02</b><div>목표 구체화하기<small>계획을 세우기 위한 시뮬레이션</small></div></li><li><b>03</b><div>상담 준비하기<small>근거를 확인하며 질문 정리</small></div></li></ol>
      <p className="planned">투자성향 진단·자산 관리를 이용할 수 있습니다. 목표·AI 상담은 개발 예정입니다.</p>
    </section><section className="card" aria-label={user ? '내 계정' : '인증'}>
      {user ? <>
        <p className="eyebrow">MY ACCOUNT</p><h2>반갑습니다.</h2><p className="muted">로그인이 완료되었습니다.</p>
        <dl><dt>이메일</dt><dd>{user.email}</dd><dt>계정 권한</dt><dd>{({ USER: '일반 사용자', PB: '프라이빗 뱅커', ADMIN: '관리자' })[user.role]}</dd></dl>
        <button disabled={busy} onClick={() => void run(async () => { setUser(await auth.me()); setNotice('사용자 정보를 확인했습니다.'); })}>내 정보 확인</button>
        {user.role !== 'USER' && <button className="secondary" disabled={busy} onClick={() => void run(async () => { await auth.me(user.role === 'ADMIN' ? '/admin/me' : '/pb/me'); setNotice('서버에서 역할별 접근 권한을 확인했습니다.'); })}>담당 역할 권한 확인</button>}
        <button className="text-button" disabled={busy} onClick={() => void run(async () => { await auth.logout(); setUser(null); })}>로그아웃</button>
      </> : <>
        <p className="eyebrow">WELCOME TO AI PB</p><h2>{mode === 'login' ? '다시 만나 반갑습니다.' : '새로운 시작을 함께해요.'}</h2>
        <p className="muted">{mode === 'login' ? '계정에 로그인하고 상담 준비를 시작하세요.' : '데모를 위한 가상 정보만 입력해 주세요.'}</p>
        <form onSubmit={submit}>
          <label htmlFor="email">이메일</label><input id="email" name="email" type="email" autoComplete="username" placeholder="you@example.com" required maxLength={254} disabled={busy}/>
          <label htmlFor="password">비밀번호</label><input id="password" name="password" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={mode === 'register' ? 10 : 1} maxLength={72} required disabled={busy}/>
          <button disabled={busy}>{busy ? '처리 중…' : mode === 'login' ? '로그인' : '회원가입'}</button>
        </form>
        <button className="text-button" disabled={busy} onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); setNotice(''); }}>{mode === 'login' ? '처음이신가요? 회원가입' : '이미 계정이 있으신가요? 로그인'}</button>
        <p className="session-note">로그인 정보는 현재 페이지에서만 유지됩니다.<br/>새로고침하면 다시 로그인해야 합니다.</p>
      </>}
      {error && <p role="alert" className="error">{error}</p>}{notice && <p role="status" className="notice">{notice}</p>}
    </section></div>
    {user && <Portfolio key={user.id}/>}
    <footer>AI PB는 가상 데이터를 사용하는 학습용 데모입니다. 실제 계좌·금융상품과 연동되지 않으며 투자 권유나 자문을 제공하지 않습니다.</footer>
  </main>;
}

createRoot(document.getElementById('root')!).render(<App/>);
