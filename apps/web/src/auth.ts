export type User = { id: string; email: string; role: 'USER' | 'PB' | 'ADMIN' };
type Tokens = { accessToken: string; refreshToken: string; user: User };
const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export class AuthClient {
  // Keep credentials in memory; reloading requires a new login.
  private tokens: Tokens | null = null;
  private refreshPending: Promise<void> | null = null;

  private async post(path: string, body: object): Promise<Response> {
    return fetch(`${base}/api/auth/${path}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
    });
  }
  async register(email: string, password: string): Promise<void> {
    const response = await this.post('register', { email, password });
    if (!response.ok) throw new Error(response.status === 409 ? '이미 가입된 이메일입니다.' : '가입 정보를 확인해 주세요. 비밀번호는 10자 이상, UTF-8 72바이트 이하여야 합니다.');
  }
  async login(email: string, password: string): Promise<User> {
    const response = await this.post('login', { email, password });
    if (!response.ok) throw new Error('이메일과 비밀번호를 확인해 주세요.');
    this.tokens = await response.json() as Tokens;
    return this.tokens.user;
  }
  private async refresh(): Promise<void> {
    if (!this.tokens) throw new Error('다시 로그인해 주세요.');
    if (!this.refreshPending) {
      const current = this.tokens;
      this.refreshPending = (async () => {
        const response = await this.post('refresh', { refreshToken: current.refreshToken });
        if (!response.ok) {
          if (this.tokens === current) this.tokens = null;
          throw new Error('세션이 만료되었습니다. 다시 로그인해 주세요.');
        }
        const next = await response.json() as Tokens;
        if (this.tokens === current) this.tokens = next;
        else await this.post('logout', { refreshToken: next.refreshToken });
      })().finally(() => { this.refreshPending = null; });
    }
    return this.refreshPending;
  }
  async me(path = '/users/me'): Promise<User> {
    return this.request<User>(path);
  }
  async request<T = void>(path: string, init: RequestInit = {}): Promise<T> {
    const initialToken = this.tokens?.accessToken;
    const send = () => fetch(`${base}/api${path}`, {
      ...init, headers: { 'Content-Type': 'application/json', ...init.headers, Authorization: `Bearer ${this.tokens?.accessToken ?? ''}` },
    });
    let response = await send();
    if (response.status === 401) {
      if (this.tokens?.accessToken === initialToken) await this.refresh();
      response = await send();
    }
    if (!response.ok) {
      const messages: Record<number, string> = {
        400: '입력값을 확인해 주세요. 금액은 1원 이상, 999,999,999,999원 이하의 정수입니다.',
        401: '세션이 만료되었습니다. 로그아웃 후 다시 로그인해 주세요.',
        403: '이 화면에 접근할 권한이 없습니다.',
        404: '항목을 찾을 수 없습니다. 목록을 새로고침해 주세요.',
        409: '내용이 변경되었습니다. 새로고침 후 다시 시도해 주세요.',
      };
      throw new Error(messages[response.status] ?? '요청을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    }
    return response.status === 204 ? undefined as T : response.json() as Promise<T>;
  }
  async logout(): Promise<void> {
    if (this.refreshPending) await this.refreshPending;
    const current = this.tokens;
    if (!current) return;
    const response = await this.post('logout', { refreshToken: current.refreshToken });
    if (!response.ok) throw new Error('로그아웃을 완료하지 못했습니다. 다시 시도해 주세요.');
    if (this.tokens === current) this.tokens = null;
  }
}
export const auth = new AuthClient();
