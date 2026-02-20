# GEMINI.md - Cloudstore Project

This document provides context for interacting with the `cloudstore` project.

## Project Overview

`cloudstore` is a Java-based command-line utility designed for diagnosing and interacting with cloud storage systems (like Amazon S3, Google Cloud Storage) through Apache Hadoop's filesystem APIs. It provides a suite of tools for performance testing, diagnostics, and advanced file operations that are optimized for cloud object stores.

The project is built with Apache Maven and targets Java 8. The source code is structured as a series of independent command-line tools that all share a common entry point handler.

### Key Technologies

*   **Language:** Java 8
*   **Build:** Apache Maven
*   **Core Dependencies:**
    *   Apache Hadoop (various modules: `hadoop-client`, `hadoop-common`, `hadoop-cloud-storage`)
    *   AWS SDK for Java 2.x
    *   Google Cloud Storage Connector

## Building and Running

### Building the Project

The project is built using Apache Maven. The standard command to build the project is:

```sh
mvn clean install
```

This will compile the code, run tests, and create a JAR file in the `target/` directory (e.g., `target/cloudstore-1.1.jar`).

### Running Commands

The tools are executed via the `hadoop jar` command. The general syntax is:

```sh
hadoop jar target/cloudstore-1.1.jar <command> [options] <arguments...>
```

-   `<command>`: The name of the tool to run (e.g., `list`, `dux`, `storediag`).
-   `[options]`: Common options include:
    -   `-D <key=value>`: Define a Hadoop configuration property.
    -   `-xmlfile <file>`: Load a Hadoop configuration XML file.
    -   `-verbose`: Enable verbose output.
    -   `-debug`: Enable low-level debug logging for the JVM and connectors.

**Example: Listing files in an S3 bucket**

```sh
hadoop jar target/cloudstore-1.1.jar list -limit 10 s3a://my-bucket/path/
```

## Development Conventions

### Code Structure

- The main entry point logic resides in `org.apache.hadoop.fs.store.StoreEntryPoint`.
- Each command (e.g., `list`, `dux`, `cloudup`) is implemented as a separate Java class that extends `StoreEntryPoint`. For instance, the `list` command is likely implemented in a class like `list.java` or `ListFiles.java`.
- The code is organized under the `org.apache.hadoop.fs` package, although it is not an official Apache Hadoop artifact. This is done for easier integration and to access package-private APIs.
- SLF4J us used for logging.
- 

### Configuration

The project uses the standard Hadoop `Configuration` framework. Configuration can be supplied through:
1.  Default Hadoop configuration files (`core-site.xml`, `hdfs-site.xml`, etc.).
2.  Custom XML files specified with the `-xmlfile` option.
3.  Individual properties set with the `-D` flag.

### Testing

-   Unit tests are located in `src/test/java`.
-   Tests are run as part of the `mvn clean install` build process.
-   The project uses JUnit and AssertJ for testing.
