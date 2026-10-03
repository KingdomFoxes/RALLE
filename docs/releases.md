# Publishing RALLE

Publishing a GitHub release in `KingdomFoxes/RALLE` triggers
`.github/workflows/release.yml`. It builds and tests the tagged code with Java 21,
checks the production JAR, attaches it to that GitHub release, and uploads the
same bytes to Modrinth. The GitHub release title and Markdown description become
the Modrinth version name and changelog.

`Build and test` also runs on pushes and pull requests, without publishing. Wait
for this check to pass on the commit you intend to tag. Build configuration must
be committed: local Git `skip-worktree` overrides are not present on CI runners.

## One-time account setup

1. Open the [RALLE Modrinth project](https://modrinth.com/project/ralle) while
   signed in as an owner/member with upload permission. Create/finish the project
   if needed.
2. Open [GitHub Actions variables](https://github.com/KingdomFoxes/RALLE/settings/variables/actions).
   The repository variable `MODRINTH_PROJECT_ID` is set to `ralle`, which the API
   accepts as the project slug. If the slug changes, replace this value with the
   permanent project ID from its `...` menu -> **Copy ID**.
3. In [Modrinth account settings](https://modrinth.com/settings/pats), create a
   personal access token with **Create versions** permission. Also allow reading
   projects/versions if access to a draft project requires it. The account must
   have upload permission on RALLE. Store it directly in
   [GitHub Actions secrets](https://github.com/KingdomFoxes/RALLE/settings/secrets/actions)
   as a repository secret named `MODRINTH_TOKEN`. Do not commit the token or put
   it in the mod configuration.
4. Commit/push the workflow, `scripts/`, and this documentation before tagging
   the first automated release. GitHub Actions must be enabled, with the official
   `actions/checkout`, `actions/setup-java`, `actions/upload-artifact`, and
   `actions/download-artifact` actions allowed. Publishing uses the automatically
   supplied `GITHUB_TOKEN` with `contents: write`; no personal GitHub token is needed.

The project ID is public metadata; the token is secret. No token or publishing
tooling is included in the mod JAR. Build and publishing use separate jobs so
Gradle never receives the Modrinth publishing token.

An unavailable project may return 404 to public API requests. A draft/private
project can be inspected using the owner's token, but players' update checks need the
project and versions to be publicly available. Uploading a version does not
replace Modrinth's project creation/approval process. GitHub releases inherit
their repository's visibility. This automation does
not change repository visibility.

## Each update

1. Set `mod_version` in `gradle.properties`, for example `0.1.9`, and commit/push
   the release code after the relevant in-game checks.
2. [Create a GitHub release](https://github.com/KingdomFoxes/RALLE/releases/new),
   choose a new tag such as `v0.1.9` pointing at that commit, write the release
   title/changelog, and publish it. The leading `v` is optional; the remaining
   tag must exactly match `mod_version` in the tagged commit. Normally no manual
   JAR upload is needed.
3. Watch **Actions -> Publish release**. When it succeeds, both sites have the
   production JAR. Fabric/Minecraft compatibility comes from the tagged code.
   Fabric API is listed as required, owo-lib as embedded, and Wynntils as optional.

Mark alpha/beta/RC tags as a GitHub **pre-release**. `-alpha` versions are uploaded
as Modrinth alpha; other GitHub pre-releases are beta; normal releases are release.
GitHub drafts, ordinary commits, and tag pushes alone do not publish to Modrinth.

If GitHub **immutable releases** are enabled, attach the production JAR with the
exact filename `ralle-<mod_version>.jar` to the draft **before** publishing. GitHub
locks attachments when an immutable release is published. The workflow then uses
that already-attached JAR for Modrinth, after verifying its identity/version and
rejecting local-backend or source JARs.

## Failures and retries

Correct missing secrets/variables or service errors and use **Re-run all jobs**
on the release's workflow run. A rerun preserves an existing GitHub JAR and uses
it as the canonical file, avoiding differences from rebuilt ZIP timestamps.
An existing listed Modrinth version with the same primary JAR hash, loader,
Minecraft version, and release channel is skipped. A conflicting version fails
without replacing or duplicating it. A failed/ambiguous upload is not retried in
a loop; rerunning checks whether it already succeeded.

The two services do not publish atomically. If Modrinth fails after GitHub's JAR
was attached, that attachment stays in place for the retry. Publishing requires
the Modrinth settings first; missing settings stop before attaching files.
Changelog edits, deletion, or channel changes after a successful publication are
not synchronized automatically; manage those separately on each site.

To fix a build/tag mismatch, publish a corrected release referencing the correct
commit. Rerunning an old tag always builds that tag's code, not the newest branch.
Tags predating this workflow cannot run it.

## Local verification

Run `python -m unittest discover -s scripts -p 'test_release.py'` for release
validation, production-JAR rejection checks, duplicate/conflict handling, and
multipart upload formatting. Run the project's usual Java 21 `gradlew build`
checks before releasing. The workflow does both automatically.

References: [GitHub release triggers](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#release),
[Modrinth version API](https://docs.modrinth.com/api/operations/createversion/).
