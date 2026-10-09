# FinFlow — mobile app (Expo / React Native)

The client for the expense-management backend in `../backend`. It runs in **Expo Go** on a
phone, in a simulator, or in a browser.

## Running it

```bash
npm install
npx expo start          # then scan the QR code with Expo Go
```

The backend must be running too (see `../backend`). **Nothing else to configure** — the app finds
your machine on its own.

### How the app finds the backend

While you are developing, Expo already knows the address of the machine serving your app: that
is how your phone downloaded the JS bundle. `src/constants/api.ts` reads that address
(`Constants.expoConfig.hostUri`, e.g. `192.168.1.25:8081`) and reuses its host with the backend's
port. Restart Expo on a different network and the API URL follows — no `.env`, no hardcoded IP.

The port comes from `backend/src/main/resources/application.properties` (`server.port=8083`). If
you change it there, change `API_PORT` in `src/constants/api.ts` to match.

**Only override it when the automatic address cannot work** — set `EXPO_PUBLIC_API_URL` in the
environment before `npx expo start`:

```bash
# Expo in tunnel mode, or the backend on a different machine
EXPO_PUBLIC_API_URL=https://api.example.com/api npx expo start
```

Fallback order: `EXPO_PUBLIC_API_URL` → the Expo host on port 8083 → `http://localhost:8083/api`
(used by the web build and Android emulators; also the result in `--tunnel` mode, where the Expo
host is an `exp.direct` domain that cannot reach your LAN, so set the variable instead).

## Structure

```
src/
├── app/          # Expo Router routes only — every file here is a URL
│   ├── _layout.tsx        # providers + root stack
│   ├── (tabs)/            # the signed-in shell: index (dashboard), finances, admin
│   └── auth/              # sign-up / sign-in, pushed from anywhere
├── screens/      # the actual screen components, one folder per area
├── features/     # vertical slices: each owns its API calls, hooks and private components
│   ├── auth/     #   register + login, password rules, sign-up form state
│   ├── finances/ #   expenses, incomes, categories
│   └── admin/    #   platform health, error logs, default categories
├── components/   # the shared design system (ui/) and app-wide pieces
├── providers/    # React context providers (session)
├── hooks/        # shared hooks (theme, colour scheme)
├── utils/        # pure helpers (error messages)
├── types/        # global types
└── constants/    # design tokens and API configuration
```

Two rules keep this readable:

- **A file in `src/app/` is a route, not a screen.** It re-exports from `src/screens/`, so the
  navigation tree stays a one-page description of the app's URLs.
- **A feature does not reach into another feature's `components/`.** Shared UI moves up into
  `src/components/ui/`; shared API plumbing lives in `features/auth/lib/api-client.ts`, which
  everything else imports.

## Design tokens

Colours, spacing, radii and text sizes come from the Figma file and live in
`src/constants/theme.ts`. Screens should use `ThemedText`/`ThemedView` and the `ui/` components
rather than raw hex values or font sizes, so a rebrand is a one-file change.

## Screens

| Route             | Screen                                    |
| ----------------- | ----------------------------------------- |
| `/`               | Dashboard (placeholder until reporting lands) |
| `/finances`       | Expenses, incomes and categories          |
| `/admin`          | Platform health, error logs, defaults     |
| `/auth/sign-up`   | Create account — matches the Figma design |
| `/auth/sign-in`   | Placeholder; the real form is next        |

## Notes

- Sessions are held in memory by `src/providers/session-provider.tsx`. Add `expo-secure-store`
  there to persist them across restarts; no screen has to change.
- `npm run reset-project` no longer exists — the Expo starter content it used to restore has
  been deleted on purpose.
