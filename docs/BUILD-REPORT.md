# Build and static validation report

Prepared 2026-10-13.

- All YAML resources parsed successfully with PyYAML 6.0.3.
- Java source was checked for obvious syntax diagnostics with the local `javac` parser pass; no syntax-like diagnostics were reported.
- A full compile was **not** completed: the current environment has OpenJDK 21 and no Maven installation, while this project intentionally targets Java 25 and requires Paper/Vault dependencies.
- No JAR was produced or runtime-tested in this environment.

Run `mvn -U clean verify` with JDK 25 on a development machine and test on a backed-up staging server before deployment.
