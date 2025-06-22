# Developer instructions

## Tooling
- If you detect warnings or issues with the tooling (like gradle), provide recommendations or try to fix it if it's safe.

## Testing
- Always run `./gradlew tasks --no-daemon` to verify Gradle configuration
- Run `./gradlew test --no-daemon` and make sure tests pass before committing

## Dependencies
- Always mention and explain why new dependency is added
- In `docs/dependencies.adoc` mention each dependency and why it is used
- Use latest version of dependencies unless there is a reason to, if so, provide explanation

## Code style
- Kotlin code uses 4 space indentation and standard idiomatic style.
- Use Jetpack Compose for UI components.
- Favor coroutines and flows for async operations.

## Documentation
- Place any additional docs in the `docs/` directory using AsciiDoc (`.adoc`).
- For complicated methods and classes provide appropriate kdoc, with examples if needed to better understand usage.

