---
role: Owner
role_key: owner
kind: role
session: admin
person_type: staff
designations: [none]   # the Owner is an access flag on a login, not a job title
permissions: [ALL (not a list: the Owner skips the permission check)]
scoped_by: whole-org
how_created: platform   # created with the organisation when the platform admin provisions it
requires_steps: [none]
ui_routes: [/members, /settings/members, /settings/roles]
source: organisation/DefaultRoles.java:8-10; organisation/OrganisationService.java:132-144 (admin membership, owner=true); authorization/PermissionChecker.java:23-39; authorization/Role.java:106-115
verified_on: 2026-11-11
verified_by_running_app: no
---

# Owner (super user)

> The first login of a school. It can do everything in the organisation and cannot be locked out.

## What this person does
- Logs in first, with the credentials the platform admin issued.
- Runs the setup (organisation details, academic year, starter template, members and roles).
- Creates the other roles and gives people access.
- Can open every screen and use every action.

## Who gets this role
- **Typical people:** the school's administrator or owner who receives the platform credentials.
- **Role vs designation:** Owner is neither a role you pick from the template nor an HR designation. It is a flag on the first member (`owner = true`). A role named "Owner" also exists in the school and holds every permission in the catalogue.
- **How many people:** one at creation. Whether a second Owner can be created through the UI is NOT verified.

## How to create the login
1. The platform admin creates the organisation and supplies the admin account (`OrganisationService.java:132-144`).
2. The system seeds the "Owner" role with every permission in the catalogue and gives the admin an active membership with `owner = true`.
3. The school logs in with those credentials. No invite step is needed.

## Permissions granted
The Owner has no permission list to maintain. `PermissionChecker.holdsAny` returns true for any active Owner membership before it looks at permission codes (`PermissionChecker.java:23-31`). A permission added to the catalogue later therefore applies to existing Owners with no data change.

The seeded "Owner" role also holds every catalogue permission at the time of provisioning (`OrganisationService.java:180-184`). The catalogue has 112 codes by the seed migrations (school `V1` plus later migrations; derived from source, not from a live database).

| Module | Permission code | What it allows | Confirmed in |
|---|---|---|---|
| all | every code | everything | `PermissionChecker.java:23-31` |

Total permissions: all (112 codes in the catalogue by the migrations).

Another role can get the same blanket access through `ALL_FUNCTIONS` (`PermissionChecker.java:35-38`).

## What they see in the app
Every menu group and every Settings section, as far as the code goes. Settings sections for modules switched off for the organisation are hidden for everyone, including the Owner (`settingsSections.tsx`). Not confirmed by running the app.

## What they cannot do
- **Revoke the Owner role or remove the owner flag** through the normal member screen. Revoking is guarded (`MembershipService.java:114-127`, `Role.java:106-115`).
- **Pass on more than they hold** is not a limit for the Owner. The rule that a caller can only hand out what they hold applies to everyone else (`PermissionChecker.holdsAll`).
- **Platform actions** (creating organisations, platform billing) belong to the platform admin, a separate login on a separate plane.
- **Use the portal** is not their normal route. The admin shell is for owners or non-portal roles (`PortalIdentityService.java:49-51`).

## Data scope
Whole organisation. Not limited to a campus, class or section. Per-campus limits for an Owner were NOT checked.

## Configuration that must exist first
Nothing. The Owner exists the moment the organisation does. Other roles and setup steps start from here.
- [ ] Setup steps → `steps/` (not written yet)

## Symptoms when it's missing
| What the person sees | Likely cause | Fix |
|---|---|---|
| Cannot log in on first use | Credentials from the platform admin were not issued or were mistyped | Platform admin re-issues credentials |
| Setup screens are empty | Starter template not applied yet | Settings → School setup template (`/settings/school-template`) |
| Someone else cannot see a screen the Owner can | That person's role lacks the permission, the Owner bypass does not apply to them | Edit the role in Settings → Roles |

## Common mistakes
- Giving daily staff the Owner login instead of a role. There is no audit separation between people sharing it.
- Assuming the Owner role listed on the Roles screen is what gives the power. The `owner` flag on the membership is what bypasses checks.
- Granting `ALL_FUNCTIONS` to a custom role as a shortcut. It gives blanket access like the Owner.

## Known gaps (not in the product yet)
- Platform admin login lockout is open (SEC-H5, `docs/stabilisation/BUG_LIST.md`). This affects the platform login, not the Owner.
- Second-owner handling and Owner transfer: not checked.

## Screenshots
none yet

---
Maintenance: when the seed migrations, the Roles screen or the checklist change, update this page and set `verified_on`. Do not copy permission lists by hand from another page.
