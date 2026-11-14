import Constants from 'expo-constants';

/**
 * Where the app finds the backend, resolved at runtime but decided at build time.
 *
 * While developing, Expo already knows the address of the machine running it - that is how the
 * phone downloaded the JS bundle. `Constants.expoConfig.hostUri` is that address
 * (`192.168.1.25:8081`), so its host is reused with the backend's port. Restart `expo start` on
 * another network and the API URL follows; nothing to configure.
 *
 * A release build has no dev server, so it reads a baked-in address instead:
 * `PRODUCTION_API_URL` below, or `EXPO_PUBLIC_API_URL` if that was set for the build. That is
 * the whole contract between the distributed APK and the VPS - one HTTPS origin.
 *
 * Resolution order:
 *
 * 1. `EXPO_PUBLIC_API_URL` - an explicit override, inlined by Metro at build time.
 * 2. The host Expo is being served from, on the backend's port (development only).
 * 3. The production address, for every build that has no dev host.
 *
 * Caveats worth knowing:
 *
 * - On a physical phone, `localhost` means the phone itself, which is why a release build must
 *   never fall back to it - step 3 replaced exactly that bug.
 * - Starting Expo in **tunnel** mode makes the host an `exp.direct` domain, which cannot reach a
 *   backend on your LAN; set `EXPO_PUBLIC_API_URL` to your machine's address for that session.
 */

/** From backend `application.properties`: `server.port`. */

const API_PORT = 8083;

const API_PATH = '/api';

/**
 * Where the app talks to when there is no dev server - which is every release build.
 *
 * ---------------------------------------------------------------------------
 *  SET THIS TO YOUR VPS BEFORE BUILDING THE APK
 * ---------------------------------------------------------------------------
 * Either edit the literal below, or set `EXPO_PUBLIC_API_URL` for the build (it wins over
 * this). Both are read at BUILD time and baked into the bundle, so changing them means
 * building again - not restarting the app.
 *
 * Use HTTPS, and use a hostname rather than a bare IP:
 *
 *   - Android release builds refuse cleartext HTTP by default, so `http://1.2.3.4:8083`
 *     fails with "Cannot reach the server" even though the backend is up.
 *   - A hostname is what a certificate can be issued for, and it survives moving the VPS.
 *
 * The `/api` suffix is optional - it is appended if you leave it off.
 */
const PRODUCTION_API_URL = 'https://CHANGE_ME_TO_YOUR_VPS_HOST/api';

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

/**
 * One shape for every source: no trailing slash, and the `/api` prefix exactly once.
 *
 * A URL pasted with a trailing slash, or without `/api`, is the difference between working
 * and a 404 on every call - so it is fixed here rather than left to the caller.
 */
function normalise(url: string): string {
  const trimmed = url.trim().replace(/\/+$/, '');
  if (!trimmed) return '';

  return trimmed.toLowerCase().endsWith(API_PATH) ? trimmed : `${trimmed}${API_PATH}`;
}

/**
 * The API base URL, e.g. `http://192.168.1.25:8083/api` in development and
 * `https://api.example.com/api` in a release build.
 *
 * Resolution order:
 *
 * 1. `EXPO_PUBLIC_API_URL` - an explicit override, baked in at build time.
 * 2. The host Expo is being served from, on the backend's port. This is what makes a phone
 *    on the same Wi-Fi work with no configuration while developing.
 * 3. The production address above. A release build has no dev host, so this is the only
 *    route it has - and it no longer falls back to `localhost`, which on a phone means the
 *    phone itself and never the VPS.
 */
export function resolveApiBaseUrl(): string {
  const override = process.env.EXPO_PUBLIC_API_URL;
  if (override) return normalise(override);

  const host = devServerHost();
  if (host) return `http://${host}:${API_PORT}${API_PATH}`;

  return normalise(PRODUCTION_API_URL);
}
