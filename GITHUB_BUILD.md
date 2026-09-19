# Build on GitHub Actions

1. Create a new GitHub repository.
2. Upload the contents of this folder to the repository root.
3. Commit/push to `main`.
4. Open **Actions**.
5. Select **Build GuruVision APK**.
6. Press **Run workflow**.
7. Open the completed workflow run.
8. Under **Artifacts**, download `GuruVision-debug-apk`.
9. Extract it and install `app-debug.apk` on Android.

The workflow generates the Gradle wrapper on the GitHub runner, so the wrapper
does not need to be included in the repository.

If GitHub blocks workflow execution for a newly uploaded repository, open the
repository's **Actions** page and enable workflows, then run it again.
