# Developer instructions

## Testing
- Always run `./gradlew tasks --no-daemon` to verify Gradle configuration
- Run `./gradlew test --no-daemon` and make sure tests pass before committing

## Code style
- Kotlin code uses 4 space indentation and standard idiomatic style.
- Use Jetpack Compose for UI components.
- Favor coroutines and flows for async operations.

## Documentation
- Place any additional docs in the `docs/` directory using AsciiDoc (`.adoc`).

## Python reference
Python reference implementation for working with my lidar device csn be found at: https://gibbard.me/lidar/

