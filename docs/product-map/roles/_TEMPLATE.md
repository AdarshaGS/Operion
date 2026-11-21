---
# ROLE PAGE HEADER. A script will read this block, so keep the keys and the format exactly.
# Copy this file to docs/product-map/roles/<role-key>.md and fill in every key.
# Use "none" where a key does not apply. Do not delete keys.

role: <Exact role name as seeded, e.g. Principal>          # must match the template role name, or "none" if this is not a role
role_key: <kebab-case-file-name, e.g. principal>
kind: <role | person-profile | portal-role | platform>      # role = org-defined permission bundle; person-profile = no login/role of its own (e.g. Driver); portal-role = Guardian; platform = platform admin
session: <admin | portal | platform>                        # which shell the person lands in
person_type: <staff | guardian | student | driver-profile | platform-admin>
designations: [<HR job titles that usually get this role, e.g. Principal>]   # designations are NOT linked to roles in the data; this is guidance only
permissions: [<PERMISSION_CODE>, ...]                       # full final list, exact codes. Copy from the seed migrations, never from memory
scoped_by: <whole-org | own-sections | own-children | none>
how_created: <starter-template | portal-invite | manual | platform>
requires_steps: [<step keys from docs/product-map/steps/>]  # setup that must exist before this role works
ui_routes: [<e.g. /settings/roles>]                         # where an admin sees or edits this role
source: <migration files + line numbers the permission list was taken from, e.g. school/V1__school_baseline.sql:3101, V28, V35>
verified_on: <YYYY-MM-DD>                                   # date someone last compared this page with the source
verified_by_running_app: <yes | no>                         # "no" means written from source files only
---

# <Role name>

> One sentence: what this person does in the school, in plain words.

## What this person does
<3-6 bullets of their day-to-day tasks in the product. Use the product's own screen names.>

## Who gets this role
- **Typical people / designations:** <e.g. Principal, Vice Principal>
- **Role vs designation:** <State clearly that the designation is the HR job title and the role is what controls access. They are set in different places.>
- **How many people usually hold it:** <e.g. one per school, several>

## How to create the login
1. <Step, with the exact screen and URL, e.g. Settings → Access & security → Roles.>
2. <Next step.>
3. <How the person receives access, e.g. staff invite, portal invite, or created by platform admin.>

State anything that blocks this, e.g. a resigned or terminated staff member cannot be given access.

## Permissions granted
Group by module. Give each code a plain meaning **only if you have confirmed it in the code**. Otherwise leave the meaning as "unverified".

| Module | Permission code | What it allows | Confirmed in |
|---|---|---|---|
| <academic> | `<CODE>` | <plain meaning or "unverified"> | <file:line> |

Total permissions: <N> (the list in the header must match this count).

## What they see in the app
<Menu items and pages this person can open. Mark clearly if this comes from the code or from running the app.>

## What they cannot do
<Things people assume this role can do but it can't. Name the missing permission code for each.>

## Data scope
<Does this person see the whole school, only their own sections, or only their own children? Name the rule and where it is enforced (file:line).>

## Configuration that must exist first
Link each to its step page.
- [ ] <Step, e.g. Academic year created> → `steps/<key>.md`
- [ ] <Step, e.g. Starter template applied (otherwise this role does not exist yet)>

## Symptoms when it's missing
| What the person sees | Likely cause | Fix |
|---|---|---|
| <e.g. Menu item missing> | <e.g. role lacks CODE> | <e.g. add CODE in Settings → Roles> |

Use the go-live check labels from `onboarding/readiness/` where they apply.

## Common mistakes
- <Mistake and how to avoid it.>

## Known gaps (not in the product yet)
<Only things the audit or code confirms are missing for this role. Say "none known" if none. Link the audit ID if there is one, e.g. AUTH-04.>

## Screenshots
<Path to screenshots, or "none yet". Do not describe screens you have not seen.>

---
Maintenance: when the seed migrations, the Roles screen or the checklist change, update this page and set `verified_on`. Do not copy permission lists by hand from another page.
