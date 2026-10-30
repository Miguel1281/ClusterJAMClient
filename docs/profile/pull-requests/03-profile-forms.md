Add a shared onboarding/contact-edit form using the existing AuthForm/AuthField,
manual ViewModelFactories, StateFlow and SavedStateHandle patterns. Load actual
Profile values, validate authorized fields and send one PATCH with the exact
server ETag. Keep the draft on failure, show the actual response and confirmation,
and continue only when the server confirms COMPLETED. Login email/identity and
onboarding status are never editable.

Integrate a profile gate and form routes in the existing NavHost. Recheck after
authentication/restoration: provisioning uses the existing bounded read retries,
PENDING opens onboarding and COMPLETED enables catalog. Preserve an owned draft
across recreation/session rechecking; clear it on logout/owner changes/discard.
Save only contact draft/owner/stage, never credentials, ETags or a completion flag.
Block duplicate submissions and confirm discarding dirty fields on Back.

Conflicts and uncertain saves require a fresh read, display server values and
explicit draft reconciliation before another user-triggered save. Respect
Retry-After and never automatically replay PATCH. Publish real GET/PATCH snapshots
back into the shared profile view after leaving the form.

Validation is compilation only, with no automated tests as requested. See
docs/profile/delivery-03.md for actual results and the complete changed-file list.
No backend, dependency, existing test or pre-existing local IDE/network change.
Address management remains the last separate branch. Squash merge requires user
authorization.
