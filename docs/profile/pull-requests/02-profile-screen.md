Connect the existing Profile tab to the real GET /api/v1/profiles/me repository.
Add a StateFlow ViewModel using the Auth presentation pattern and the existing
manual ViewModelFactories/AppContainer. Scope it to the navigation destination
and stop work/clear contact state when hidden or when the verified owner changes.
Discard late responses and mismatched owner IDs.

Show real name, phone, contact email and server-confirmed PENDING/COMPLETED,
separately from the existing Auth identity/security actions. Distinguish
PROFILE_NOT_READY from an incomplete existing profile: retry only that 404,
at most three times after 1/2/4 seconds, then offer explicit retry. Other
failures do not poll. Reuse StatusMessage, theme, spacing and Auth's local
message mapping. No mock contact data, token store or alternate navigation.

Validation is compilation only, with no automated tests as explicitly requested.
See docs/profile/delivery-02.md for the actual result and complete file list.
The existing compile-only Profile CI remains in effect. This PR does not add
onboarding/edit forms, addresses or a startup gate; those follow after authorized
integration. Preserve the pre-existing IDE/debug-network changes outside the PR.
Squash merge requires explicit user authorization.
