import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthClient } from './auth';

const user = { id: '1', email: 'user@aipb.demo', role: 'USER' };
const tokens = { accessToken: 'access-1', refreshToken: 'refresh-1', user };
const response = (body: object, status = 200) => new Response(JSON.stringify(body), { status });
afterEach(() => vi.unstubAllGlobals());

describe('authentication client', () => {
  it('replays an asset update with its original body and renewed token', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(response(tokens))
      .mockResolvedValueOnce(response({}, 401))
      .mockResolvedValueOnce(response({ ...tokens, accessToken: 'access-2' }))
      .mockResolvedValueOnce(response({ id: 'asset', version: 1 }));
    vi.stubGlobal('fetch', fetch);
    const auth = new AuthClient(); await auth.login(user.email, 'password');
    const body = JSON.stringify({ name: '가상 예금', type: 'DEPOSIT', amount: 100, version: 0 });
    await expect(auth.request('/assets/asset', { method: 'PUT', body })).resolves.toEqual({ id: 'asset', version: 1 });
    expect(fetch.mock.calls[3][1]).toMatchObject({ method: 'PUT', body, headers: { Authorization: 'Bearer access-2' } });
  });
  it('handles empty profile and delete responses without parsing JSON', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(response(tokens)).mockImplementation(() => Promise.resolve(new Response(null, { status: 204 })));
    vi.stubGlobal('fetch', fetch);
    const auth = new AuthClient(); await auth.login(user.email, 'password');
    await expect(auth.request('/investment-profile')).resolves.toBeUndefined();
    await expect(auth.request('/assets/asset?version=0', { method: 'DELETE' })).resolves.toBeUndefined();
  });
  it('surfaces concurrent-edit conflicts without retrying mutations', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(response(tokens)).mockResolvedValueOnce(response({}, 409));
    vi.stubGlobal('fetch', fetch);
    const auth = new AuthClient(); await auth.login(user.email, 'password');
    await expect(auth.request('/assets/asset?version=0', { method: 'DELETE' })).rejects.toThrow('내용이 변경');
    expect(fetch).toHaveBeenCalledTimes(2);
  });
  it('retries a protected request after token rotation', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(response(tokens))
      .mockResolvedValueOnce(response({}, 401))
      .mockResolvedValueOnce(response({ ...tokens, accessToken: 'access-2', refreshToken: 'refresh-2' }))
      .mockResolvedValueOnce(response(user));
    vi.stubGlobal('fetch', fetch);
    const auth = new AuthClient();
    await auth.login(user.email, 'password');
    expect(await auth.me()).toEqual(user);
    expect(fetch.mock.calls[2][0]).toContain('/api/auth/refresh');
    expect(fetch.mock.calls[3][1].headers.Authorization).toBe('Bearer access-2');
  });
  it('rejects an expired session without an infinite retry', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(response(tokens)).mockResolvedValue(response({}, 401));
    vi.stubGlobal('fetch', fetch);
    const auth = new AuthClient(); await auth.login(user.email, 'password');
    await expect(auth.me()).rejects.toThrow('세션이 만료');
    expect(fetch).toHaveBeenCalledTimes(3);
  });
  it('does not refresh on permission denial', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(response(tokens)).mockResolvedValueOnce(response({}, 403));
    vi.stubGlobal('fetch', fetch);
    const auth = new AuthClient(); await auth.login(user.email, 'password');
    await expect(auth.me('/admin/me')).rejects.toThrow('권한');
    expect(fetch).toHaveBeenCalledTimes(2);
  });
  it('shares one refresh between simultaneous protected requests', async () => {
    let release!: (r: Response) => void;
    const pending = new Promise<Response>(resolve => { release = resolve; });
    const fetch = vi.fn().mockResolvedValueOnce(response(tokens))
      .mockResolvedValueOnce(response({}, 401)).mockResolvedValueOnce(response({}, 401))
      .mockReturnValueOnce(pending).mockImplementation(() => Promise.resolve(response(user)));
    vi.stubGlobal('fetch', fetch);
    const auth = new AuthClient(); await auth.login(user.email, 'password');
    const first = auth.me(); const second = auth.me();
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(4));
    release(response({ ...tokens, accessToken: 'access-2' }));
    await Promise.all([first, second]);
    expect(fetch.mock.calls.filter(call => String(call[0]).endsWith('/refresh'))).toHaveLength(1);
  });
  it('waits for refresh before revoking the current session', async () => {
    let release!: (r: Response) => void;
    const fetch = vi.fn().mockResolvedValueOnce(response(tokens))
      .mockResolvedValueOnce(response({}, 401))
      .mockReturnValueOnce(new Promise<Response>(resolve => { release = resolve; }))
      .mockImplementation(() => Promise.resolve(response(user)));
    vi.stubGlobal('fetch', fetch);
    const auth = new AuthClient(); await auth.login(user.email, 'password');
    const reading = auth.me();
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(3));
    const loggingOut = auth.logout();
    release(response({ ...tokens, refreshToken: 'refresh-2' }));
    await Promise.all([reading, loggingOut]);
    const call = fetch.mock.calls.find(call => String(call[0]).endsWith('/logout'));
    expect(JSON.parse(call![1].body).refreshToken).toBe('refresh-2');
  });
});
