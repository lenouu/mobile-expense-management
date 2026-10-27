import Constants from 'expo-constants';

/**
 * Where the app finds the backend, resolved at runtime.
 *
 * The rule that makes this work with no configuration: while you are developing, Expo already
 * knows the address of the machine running it - that is how your phone downloaded the JS
 * bundle. `Constants.expoConfig.hostUri` is that address (`192.168.1.25:8081`), so we reuse its
 * host and swap in the backend's port. Restart `expo start` on a different network and the API
 * URL follows automatically; nothing is hardcoded and there is no `.env` to keep in sync.
 *
 * Resolution order:
 *
 * 1. `EXPO_PUBLIC_API_URL` - an explicit override always wins. Use it when Metro and the
 *    backend are not on the same host, or when you need a public URL.
 * 2. The host Expo is being served from, on the backend's port.
 * 3. `localhost` - correct for the web build and for an Android emulator, where the host is
 *    reachable under that name.
 *
 * Caveats worth knowing:
 *
 * - On a physical phone, `localhost` means the phone itself, which is why step 2 exists. If you
 *   start Expo in **tunnel** mode the host becomes an `exp.direct` domain, which cannot reach a
 *   backend on your LAN, so this falls back to step 3 and you should set `EXPO_PUBLIC_API_URL`
 *   to your machine's address instead.
 * - A production build has no Expo host at all, so set `EXPO_PUBLIC_API_URL` as a build-time
 *   environment variable before you ship one.
 */

/** From backend `application.properties`: `server.port`. */
const API_PORT = 8083;

const API_PATH = '/api';

/** An IPv4 literal, optionally with the port Expo appends. */
const HOST_AND_OPTIONAL_PORT = /^(\d{1,3}(?:\.\d{1,3}){3})(?::\d+)?$/;

/**
 * The address Expo is serving this app from, or null when there isn't one.
 *
 * `hostUri` is the modern field; `debuggerHost` is the older Expo Go equivalent, kept as a
 * fallback so this keeps working if the manifest shape changes again.
 */
function devServerHost(): string | null {
  const expoConfigHost = Constants.expoConfig?.hostUri;
  const expoGoHost = Constants.expoGoConfig?.debuggerHost;
  const raw = expoConfigHost ?? expoGoHost;
  if (!raw) return null;

  const match = HOST_AND_OPTIONAL_PORT.exec(raw.trim());
  return match ? match[1] : null;
}

/** The API base URL, e.g. `http://192.168.1.25:8083/api`. */
export function resolveApiBaseUrl(): string {
  const override = process.env.EXPO_PUBLIC_API_URL;
  if (override) return override.replace(/\/+$/, '');

  const host = devServerHost();
  if (host) return `http://${host}:${API_PORT}${API_PATH}`;

  return `http://localhost:${API_PORT}${API_PATH}`;
}
