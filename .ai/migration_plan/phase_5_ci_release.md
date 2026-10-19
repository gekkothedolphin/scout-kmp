# Phase 5 — CI & Multiplatform Release

## 🎯 Goal

Update the continuous integration pipeline and publishing scripts so the new
`scout-kmp/` standalone project (all modules: `core`, `androidApp`, `iosApp`,
`inspector`, `validator`, etc. as ported through prior phases) build, test, and publish
correctly across all approved targets, with Maven Central artifacts correctly
reflecting the new multiplatform structure (e.g. `-jvm`, `-iosarm64` klib variants where
applicable) under a **new Maven coordinate group**, `dev.gekko.scout` (confirmed 2026-08-02, see
the decisions table in `.ai/README.md`; the original `com.yandex.scout` group is never
reused, since `scout/` itself is never touched or re-released). CI continues to
build and test the original `scout/` project unchanged, as its own independent job,
purely to keep the frozen reference baseline provably buildable.

## 📍 Starting state (read before starting)

- `.ai/migration_plan/phase_4_tooling_jvm.md` — must be fully checked off; all
  `scout-kmp/` modules must build locally via `./gradlew build` (run from inside
  `scout-kmp/`) before wiring CI around them.
- `.github/` directory — existing CI workflow configuration; add new job(s) for
  `scout-kmp/`, keep the existing `scout/` job(s) unmodified and still green.
- `scout/publish.gradle` — **read-only reference**; current JVM-only publishing
  configuration for the original project, never edited. `scout-kmp/` needs its own,
  new `publish.gradle.kts` (or per-module `maven-publish` configuration) written from
  scratch to handle multiplatform publication (`maven-publish` plugin behavior differs
  for KMP modules — multiple publications per module target).
- `scout/release-all.sh`, `scout/release-base.sh`, `scout/release-compiled.sh`,
  `scout/release-plugin.sh` — **read-only reference**, never edited; `scout-kmp/` gets
  its own new release scripts (naming/structure may mirror these for consistency, but
  are separate files) reflecting whichever modules Phase 4 actually created there.
- `scout/versions.gradle` — **read-only reference** for the original `scout_version`
  field; `scout-kmp/`'s own version is a fresh decision (confirm with the user), not
  required to match or increment the original.
- `.ai/context_project.md` — full accumulated record of every decision made in prior
  phases; this phase must not contradict any of them.

## 📋 Checklist (Definition of Done)

- [ ] Maven Central namespace verified. Publishing under `dev.gekko.scout` requires
      proving ownership of the `gekko.dev` domain (DNS TXT record on the Central
      Portal). If the domain is not owned, stop and ask the user (the GitHub-based
      alternative is `io.github.gekkothedolphin`). Ideally this is settled long before
      this phase, because the group also fixes the Kotlin package name; see the open
      questions in `.ai/README.md`.
- [ ] CI workflow(s) under `.github/` build and test all Gradle targets introduced in
      Phase 2/3 for `scout-kmp/` (at minimum `jvm`; plus
      `iosX64`/`iosSimulatorArm64`/`androidTarget` etc. if approved), on appropriate CI
      runners (macOS runner required for iOS targets), as a **new, separate job** from
      the existing `scout/` CI job (which keeps running unmodified). A minimal
      `scout-kmp` job already exists from Phase 2 Step 2.1; this item extends it to
      every module and target.
- [ ] A new `scout-kmp/publish.gradle.kts` (or equivalent) correctly produces and signs
      multiplatform publications for `scout-kmp/core` (root publication + per-target
      publications) under the new Maven coordinate group, verified against a local Maven
      repository dry run before touching real credentials/Maven Central.
- [ ] New release scripts for `scout-kmp/` (naming as agreed with the user, e.g.
      mirroring `scout/`'s `release-*.sh` naming for familiarity) are created to invoke
      the correct Gradle tasks for multiplatform publishing and are manually dry-run at
      least once against a local/staging repository. The original `scout/release-*.sh`
      scripts are never edited.
- [ ] `scout-kmp`'s own version number/scheme is agreed with the user and applied
      consistently across all its modules — this is a fresh decision, independent of
      `scout/`'s `scout_version` (which stays frozen at whatever value it already has).
- [ ] A new `scout-kmp/README.md` documents the new multiplatform artifact
      coordinates/usage snippets (e.g. how a consumer adds the dependency for JVM vs iOS
      vs Android). The root repository `README.md` is updated to point to both `scout/`
      (original, JVM-only, frozen) and `scout-kmp/` (new, multiplatform, active
      development) with a clear explanation of the relationship between them.
- [ ] A full dry-run release (to a local repository or a test/staging Maven repo, never
      directly to production Maven Central) is executed for `scout-kmp/` and its
      artifacts inspected manually before this phase is marked complete.
- [ ] `CONTRIBUTING.md` is updated if the contribution/build workflow changed as a
      result of the KMP migration (e.g. new required SDKs — Xcode, Android SDK; and the
      fact that there are now two Gradle projects, `scout/` and `scout-kmp/`, in one
      repository).

## ⚠️ Anti-patterns (forbidden on this phase)

- Do **not** edit any file under `scout/` (the original) in this phase, or any phase —
  it is the permanently frozen reference baseline, including its own publishing
  scripts, CI job, and version number.
- Do **not** publish to the real Maven Central repository as part of routine agent
  work — every publish action in this phase must target a local or explicitly
  designated staging repository unless the user explicitly requests and supervises a
  production release.
- Do **not** reuse the `com.yandex.scout` Maven group for `scout-kmp/` publications
  without explicit user agreement — this is a new fork under new active development,
  publishing under the original group without permission/coordination risks confusion
  with upstream Yandex releases.
- Do **not** change `scout_version` (the original, in `scout/versions.gradle`) at all —
  `scout-kmp/`'s version is an independent, fresh decision.
- Do **not** remove CI jobs for the pre-existing JVM-only `scout/` project — it must
  continue to be verified by CI, unchanged, alongside the new `scout-kmp/` job.
- Do **not** hardcode signing credentials, API tokens, or other secrets into any
  workflow file or script — reference them via CI secret management only.
- Do **not** consider this phase (and the whole migration) complete while any prior
  phase's checklist has unchecked items.
