import { useEffect, useState, type FormEvent } from 'react';
import { auth } from './auth';

const types = { CASH: '현금·입출금', DEPOSIT: '예·적금', STOCK: '주식', FUND: '펀드', BOND: '채권', PENSION: '연금', OTHER: '기타', DEBT: '부채' };
const levels: Record<string, string> = { CONSERVATIVE: '안정형', CAUTIOUS: '안정추구형', BALANCED: '위험중립형', GROWTH: '적극투자형', AGGRESSIVE: '공격투자형' };
type Asset = { id: string; name: string; type: keyof typeof types; amount: number; version: number };
type Assets = { items: Asset[]; totalAssets: number; totalDebt: number; netAssets: number };
type Profile = { score: number; riskLevel: string; assessedAt: string; expiresAt: string; expired: boolean };
type Questionnaire = { version: string; questions: { id: string; title: string; options: string[] }[] };
const won = (amount: number) => `${amount.toLocaleString('ko-KR')}원`;

export function Portfolio() {
  const [assets, setAssets] = useState<Assets | null>(null);
  const [profile, setProfile] = useState<Profile>();
  const [questionnaire, setQuestionnaire] = useState<Questionnaire>();
  const [answers, setAnswers] = useState<Record<string, number>>({});
  const [survey, setSurvey] = useState(false);
  const [editing, setEditing] = useState<Asset | null>(null);
  const [deleting, setDeleting] = useState<string | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  async function load() {
    const [a, p, q] = await Promise.all([
      auth.request<Assets>('/assets'), auth.request<Profile | undefined>('/investment-profile'),
      auth.request<Questionnaire>('/investment-profile/questionnaire'),
    ]);
    setAssets(a); setProfile(p); setQuestionnaire(q);
  }
  useEffect(() => {
    let active = true;
    Promise.all([auth.request<Assets>('/assets'), auth.request<Profile | undefined>('/investment-profile'), auth.request<Questionnaire>('/investment-profile/questionnaire')])
      .then(([a, p, q]) => { if (active) { setAssets(a); setProfile(p); setQuestionnaire(q); } })
      .catch(e => { if (active) setError(e instanceof Error ? e.message : '불러오지 못했습니다.'); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, []);
  async function run(action: () => Promise<void>) {
    setBusy(true); setError(''); setNotice('');
    try { await action(); }
    catch (e) { setError(e instanceof Error ? e.message : '요청을 완료하지 못했습니다.'); }
    finally { setBusy(false); }
  }
  function saveAsset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const body = { name: String(data.get('name')).trim(), type: data.get('type'), amount: data.get('amount'), ...(editing ? { version: editing.version } : {}) };
    void run(async () => {
      await auth.request(editing ? `/assets/${editing.id}` : '/assets', { method: editing ? 'PUT' : 'POST', body: JSON.stringify(body) });
      setEditing(null); form.reset(); setNotice('자산을 저장했습니다.'); await load();
    });
  }
  return <section className="portfolio" aria-label="투자성향과 자산 관리" aria-busy={busy}>
    <div className="section-heading"><div><p className="eyebrow">MY FINANCIAL PICTURE</p><h2>나의 자산, 나의 방향</h2></div>
      <button className="secondary compact" disabled={busy} onClick={() => void run(async () => { await load(); setEditing(null); setDeleting(null); setAnswers({}); })}>새로고침</button></div>
    {error && <p role="alert" className="error">{error}</p>}{notice && <p role="status" className="notice">{notice}</p>}
    {busy && !assets && <p role="status">정보를 불러오는 중입니다…</p>}
    <div className="portfolio-grid"><section className="card profile-card"><h2>투자성향 진단</h2>
      <p className="muted">5문항으로 생각을 정리하는 학습용 진단입니다. 금융기관의 적합성 평가나 상품 추천에 사용할 수 없습니다.</p>
      {profile ? <div className="profile-result"><strong>{levels[profile.riskLevel]}</strong><span>{profile.score} / 25점</span>
        <p className="muted">진단일 {new Date(profile.assessedAt).toLocaleDateString('ko-KR')} · 유효기간 {new Date(profile.expiresAt).toLocaleDateString('ko-KR')}</p>
        {profile.expired && <p className="error">진단이 만료되었습니다. 다시 진단해 주세요.</p>}</div>
        : assets && <p className="empty-state">아직 진단 결과가 없습니다. 첫 진단을 시작해 보세요.</p>}
      <button disabled={busy || !questionnaire} onClick={() => { setSurvey(!survey); setAnswers({}); }}>{survey ? '진단 닫기' : profile ? '다시 진단하기' : '진단 시작하기'}</button>
      {survey && questionnaire && <form onSubmit={event => {
        event.preventDefault(); void run(async () => {
          const result = await auth.request<Profile>('/investment-profile', { method: 'POST', body: JSON.stringify({ questionnaireVersion: questionnaire.version, answers: questionnaire.questions.map(q => answers[q.id]) }) });
          setProfile(result); setSurvey(false); setAnswers({}); setNotice('진단 결과를 저장했습니다.');
        });
      }}>{questionnaire.questions.map((q, index) => <fieldset disabled={busy} key={q.id}><legend>{index + 1}. {q.title}</legend>
        {q.options.map((option, i) => <label className="radio-option" key={option}><input type="radio" name={q.id} required checked={answers[q.id] === i + 1} onChange={() => setAnswers({ ...answers, [q.id]: i + 1 })}/>{option}</label>)}
      </fieldset>)}<button disabled={busy || questionnaire.questions.some(q => !answers[q.id])}>진단 결과 저장</button></form>}
    </section><section className="card assets-card"><h2>자산 관리</h2><p className="muted">가상 자산만 입력해 주세요. 모든 금액은 원화(KRW) 기준입니다.</p>
      {assets && <><div className="totals"><div><small>총자산</small><strong>{won(assets.totalAssets)}</strong></div><div><small>총부채</small><strong>{won(assets.totalDebt)}</strong></div><div><small>순자산</small><strong>{won(assets.netAssets)}</strong></div></div>
        {assets.items.length === 0 ? <p className="empty-state">등록된 자산이 없습니다. 아래에서 첫 자산을 추가하세요.</p> : <ul className="asset-list">{assets.items.map(asset => <li key={asset.id}>
          <div className="asset-description"><strong>{asset.name}</strong><small>{types[asset.type]} · {won(asset.amount)}</small></div>
          <div className="asset-actions">{deleting === asset.id ? <><span>삭제할까요?</span><button disabled={busy} onClick={() => void run(async () => { await auth.request(`/assets/${asset.id}?version=${asset.version}`, { method: 'DELETE' }); setDeleting(null); if (editing?.id === asset.id) setEditing(null); setNotice('자산을 삭제했습니다.'); await load(); })}>삭제 확인</button><button disabled={busy} onClick={() => setDeleting(null)}>취소</button></>
            : <><button disabled={busy} onClick={() => { setEditing(asset); setDeleting(null); }}>수정<span className="sr-only"> {asset.name}</span></button><button disabled={busy} onClick={() => setDeleting(asset.id)}>삭제<span className="sr-only"> {asset.name}</span></button></>}</div>
        </li>)}</ul>}</>}
      <form key={editing ? `${editing.id}-${editing.version}` : 'new'} onSubmit={saveAsset}><h3>{editing ? '자산 수정' : '자산 추가'}</h3>
        <label htmlFor="asset-name">자산 이름</label><input id="asset-name" name="name" required maxLength={80} defaultValue={editing?.name ?? ''} placeholder="가상 정기예금" disabled={busy || !assets}/>
        <label htmlFor="asset-type">분류</label><select id="asset-type" name="type" defaultValue={editing?.type ?? 'CASH'} disabled={busy || !assets}>{Object.entries(types).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
        <label htmlFor="asset-amount">금액 (원)</label><input id="asset-amount" name="amount" type="number" min="1" max="999999999999" step="1" required defaultValue={editing?.amount ?? ''} disabled={busy || !assets}/>
        <button disabled={busy || !assets}>{editing ? '수정 저장' : '자산 추가'}</button>{editing && <button type="button" className="text-button" disabled={busy} onClick={() => setEditing(null)}>수정 취소</button>}
      </form></section></div>
  </section>;
}
