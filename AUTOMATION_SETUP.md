# Upload your issues to GitHub — do it now

This package uses GitHub's own free automation feature, **GitHub Actions**.
You don't need to create any token, password, or install anything extra
on your computer to get started.

**Sprint 0 and Sprint 1 are already marked as done.** From Sprint 2 onward every sprint lasts **one week** (Sprint 2 = Week 5 … Sprint 15 = Week 18), so the Monday workflow creates exactly one sprint per week. `automation/state.json`
in this package is pre-set so the very next run starts at **Sprint 2** —
nothing from Sprint 0 or 1 will be touched or re-created.

Every issue is now fully detailed when you open it on GitHub: a summary
table (Sprint, Epic, Priority, Technology, Depends on, Effort estimate,
Assigned to) at the top, followed by the full User Story, Acceptance
Criteria, and Definition of Done, each under its own heading.

## Step 1 — Assign issues to real GitHub accounts (recommended, do this first)

GitHub can only auto-assign a real GitHub username, not a plain name. Open
`data/assignees.json` and fill in each teammate's actual GitHub username
(the part after `github.com/` in their profile URL) next to their name:

```json
{
  "Yuhnekeh Sidney Brown (Product Owner)": "their-github-username",
  "Tagne Fadhil (Scrum Master)": "their-github-username",
  "Biloa Ekassi Lena": "their-github-username",
  "Nguma Kelimbom": "their-github-username",
  "Djou Ningaye Simon": "their-github-username"
}
```

Leave any entry as `""` to leave that person's issues unassigned on GitHub
for now (the planned owner will still show inside the issue body either
way). Each person needs to already be a collaborator on the repo, or
GitHub will refuse the assignment — the script will warn you and create
the issue unassigned instead of failing outright if that happens.

You can also come back and edit this file later — it's read fresh every
time a sprint's issues are created, so update it any time before you run
the next sprint.

## Step 2 — Upload these files to your repository

Copy this whole folder's contents into the root of
`lenouu/mobile-expense-management`, keeping the exact paths:

```
.github/workflows/weekly-issues.yml
scripts/github_issues_lib.py
scripts/weekly_rollout.py
scripts/import_all_issues.py
automation/state.json
data/issues.json
data/assignees.json
AUTOMATION_SETUP.md
```

Easiest way, no Git knowledge needed: on github.com, open your repo →
**Add file → Upload files** → drag the extracted folder in → commit
directly to `main`.

## Step 3 — Turn on write permissions for Actions (one-time)

Repo → **Settings → Actions → General** → under **Workflow permissions**,
select **"Read and write permissions"** → Save.

## Step 4 — Create the next sprint's issues right now

Go to your repo's **Actions** tab → click **Weekly Issue Rollout** in the
left sidebar → click the **Run workflow** button → **Run workflow** again
to confirm.

That single click uploads **Sprint 2**'s issues (Week 5) and its milestone, each
one assigned per `data/assignees.json`. Open the **Issues** tab — they'll
be there within a few seconds, and clicking into one shows the full
summary table, user story, acceptance criteria and Definition of Done.

**To get everything today instead of one sprint a week:** click
**Run workflow** again right away (it will roll out Sprint 3), and again
for Sprint 4, and so on, through Sprint 15. It's safe to click it as many
times as you like: it always checks what already exists and never creates
the same issue twice.

From here on, the same workflow also fires automatically every Monday at
08:00 UTC and picks up wherever it left off, until all remaining sprints
are in.

## Optional — the 3 "icebox" ideas (mascot features)

US65–US67 aren't scheduled into a sprint, so the steps above skip them on
purpose. If you want them created too, you'll need Python installed
locally (this one isn't a button-click):

```
pip install requests
export GITHUB_TOKEN=ghp_xxxxxxxx        # Settings → Developer settings → Personal access tokens → generate one with "repo" scope
export GITHUB_REPOSITORY=lenouu/mobile-expense-management
python scripts/import_all_issues.py --include-icebox
```

## Changing things later

- Schedule/day: edit the `cron:` line in `.github/workflows/weekly-issues.yml` (UTC time).
- Any story's text, labels, sprint, or technology tag: edit `data/issues.json` before that sprint's issues are created.
- Who's assigned: edit `data/assignees.json` any time before the relevant sprint runs.
