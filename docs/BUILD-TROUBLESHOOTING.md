# Build troubleshooting

## `Unsupported class file major version 69` during Maven Shade

Java 25 class files use major version 69. Maven Shade Plugin 3.6.0's bundled ASM parser may be too old to read them. The `pom.xml` now overrides the Shade plugin's ASM dependencies to version 9.9, which supports Java 25 class files.

Run from the project root with JDK 25 and Maven 3.9+:

```shell
mvn -version
mvn -U clean verify
```

Confirm that `mvn -version` reports Java 25. If Maven is running under Java 21, configure `JAVA_HOME` to point to JDK 25 and reopen the terminal. This source project has not been built in the current environment because Maven is unavailable here and the installed JDK is Java 21; verify the build on your JDK 25 machine.
