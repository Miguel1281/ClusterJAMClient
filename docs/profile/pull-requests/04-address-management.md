Complete Profile's Android integration with real owned address CRUD and explicit
default selection. Extend the existing ProfileApiService/repository, DTO/domain
mapping, manual ViewModelFactories and the same NavHost. Reuse AuthForm/AuthField,
StateFlow and SavedStateHandle for list/form state and owned draft restoration.
No new dependency, token store, navigation system or backend change.

Use collection ETags for creation and item ETags for edits/default/deletion.
Read current item details before deletion confirmation and default selection;
refresh the whole book after confirmed writes without optimistic default changes.
Keep postal codes as text. A request-scoped Gson adapter explicitly serializes
interiorNumber=null when cleared, preserving Profile PATCH omission behavior.
Validate lengths, country and UUIDs; expose only the owned routes with bearer.
Never replay POST/PATCH or conditional DELETE, retaining Auth's previous
unconditional DELETE behavior.

Preserve drafts on failure and require fresh reads/review after revision conflicts
or uncertain writes. Uncertain creation shows the actual current book before
another user-triggered submission. Confirm deletion/discard; clear owned state
on logout/account changes. Addresses remain optional for onboarding. Add delivery
and final reports, endpoint/configuration and compilation documentation.

Validation: local assembleDebug passed with no automated test task. Existing
Profile CI compiles debug/release while skipping automated tests. No device,
visual or live-backend validation is claimed. See docs/profile/delivery-04.md
and docs/profile/final-report.md for file lists, executed commands and scope.
Preserve the two pre-existing local IDE/network changes outside this PR.
This is the last planned implementation branch; squash merge requires user authorization.
