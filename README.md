# cloudstore

This is going to be for a general cloudstore CLI command for Hadoop

Initally it'll be the S3A diagnostics entry point, designed to work with Hadoop 2.7+

Why? Sometimes things fail. When that happens, we want to know what the client side settings are.

Ideally, in future: do more with WriteOperationsHelper ops going to the FS at a lower level of HTTP verbs rather than the FS wrapper.  
