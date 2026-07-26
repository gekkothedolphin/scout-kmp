# Phase 5 — CI & Multiplatform Release

## 🎯 Goal

Update the continuous integration pipeline and publishing scripts so the newly
multiplatform `scout/core` (and any platform apps from Phase 3) build, test, and
publish correctly across all approved targets, with Maven Central artifacts correctly
reflecting the new multiplatform structure (e.g. `-jvm`, `-iosarm64` klib variants where
applicable) under the existing `com.yandex.scout` group or its Phase-1-approved
replacement.

## 📍 Starting state (read before starting)

- `.ai/migration_plan/phase_4_tooling_jvm.md` — must be fully checked off; all modules
  must build locally via `./gradlew build` before wiring CI around them.
- `.github/` directory — existing CI workflow configuration to inspect and update.
- `scout/publish.gradle` — current publishing configuration applied by every module
  (`apply from: '../../publish.gradle'`); must be extended to handle multiplatform
  publication (`maven-publish` plugin behavior differs for KMP modules — multiple
  publications per module target).
- `scout/release-all.sh`, `scout/release-base.sh`, `scout/release-compiled.sh`,
  `scout/release-plugin.sh` — existing release scripts; confirm which ones remain
  relevant after Phase 4's `compiled-keys` decision.
- `scout/versions.gradle` — `scout_version` field; confirm the version-bump policy
  (e.g. new major/minor version to signal the multiplatform breaking change) with the
  user before this phase changes it.
- `.ai/context_project.md` — full accumulated record of every decision made in prior
  phases; this phase must not contradict any of them.

## 📋 Checklist (Definition of Done)

- [ ] CI workflow(s) under `.github/` build and test all Gradle targets introduced in
      Phase 2/3 (at minimum `jvm`; plus `iosX64`/`iosSimulatorArm64`/`androidTarget` etc.
      if approved), on appropriate CI runners (macOS runner required for iOS targets).
- [ ] `publish.gradle` correctly produces and signs multiplatform publications for
      `scout/core` (root publication + per-target publications), verified against a
      local Maven repository dry run before touching real credentials/Maven Central.
- [ ] Release scripts (`release-all.sh` etc., as retained per Phase 4's decision) are
      updated to invoke the correct Gradle tasks for multiplatform publishing and are
      manually dry-run at least once against a local/staging repository.
- [ ] `scout_version` bump strategy is agreed with the user and applied consistently
      across all modules sharing `versions.gradle`.
- [ ] Root and module-level `README.md` files are updated to document the new
      multiplatform artifact coordinates/usage snippets (e.g. how a consumer adds the
      dependency for JVM vs iOS vs Android), superseding the JVM-only `Setup` section
      currently in the root `README.md`.
- [ ] A full dry-run release (to a local repository or a test/staging Maven repo, never
      directly to production Maven Central) is executed and its artifacts inspected
      manually before this phase is marked complete.
- [ ] `CONTRIBUTING.md` is updated if the contribution/build workflow changed as a
      result of the KMP migration (e.g. new required SDKs — Xcode, Android SDK).

## ⚠️ Anti-patterns (forbidden on this phase)

- Do **not** publish to the real Maven Central repository as part of routine agent
  work — every publish action in this phase must target a local or explicitly
  designated staging repository unless the user explicitly requests and supervises a
  production release.
- Do **not** change `scout_version` without explicit user agreement on the versioning
  scheme (this is a public, previously-published library — semantic versioning
  expectations of existing consumers matter).
- Do **not** remove CI jobs for the pre-existing JVM-only modules
  (`inspector`/`validator`/`classgraph-collector`) — they must continue to be verified
  by CI even though they are not part of the multiplatform surface.
- Do **not** hardcode signing credentials, API tokens, or other secrets into any
  workflow file or script — reference them via CI secret management only.
- Do **not** consider this phase (and the whole migration) complete while any prior
  phase's checklist has unchecked items.
