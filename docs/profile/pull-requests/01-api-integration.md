Add Profile's GET/PATCH API and repository using the existing Auth client
patterns: Retrofit/Gson DTOs, domain models, Result-based suspend operations,
IO execution, error mapping and manual AppContainer injection. Preserve nullable
legacy fields and use only the server's onboarding status. Carry the exact
quoted ETag into If-Match; omit unchanged fields and never replay PATCH.

Reuse the same SessionAccess/Auth repository for protected requests and token
renewal. Explicitly permit Profile's configured origin and profile route in
the existing authorization boundary. Add profileBaseUrl/PROFILE_BASE_URL using
Auth's URL configuration pattern, falling back to the shared gateway and
retaining HTTPS requirements for release. No new dependency or token store.

Respect the user's explicit no-tests instruction: Profile branches and their
profile-scoped squash commits compile debug/release in CI without executing
automated tests. Existing tests are unchanged, and other changes retain the
previous workflow. Local assembleDebug passed; formatting was applied only to
this branch's source files. Compilation does not claim backend/device validation.

This PR prepares the HTTP/domain layer only. Screens, onboarding/editing,
addresses and navigation follow in separate branches after authorized integration.
See docs/profile/implementation-plan.md and docs/profile/delivery-01.md for the
analysis, complete file list, actual commands and scope. Preserve the existing
local IDE/debug-network modifications outside this PR. Backend and original
project documents remain unchanged. Squash merge requires user authorization.
