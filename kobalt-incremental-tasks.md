Kobalt's incremental task algorithm is not based on timestamps but on checksums.

You make a task incremental by declaring it `@IncrementalTask` instead of `@Task`. The only other difference is that instead of returning a `TaskResult`, incremental tasks return an `IncrementalTaskInfo`:

```
class IncrementalTaskInfo(
    val inputChecksum: String?,
    val outputChecksum: String?,
    val task: (Project) -> TaskResult)
```

This class contains three fields:

- A task closure, which is your effective task: `(Project) -> TaskResult`
- An input checksum (`String?`)
- An output checksum (`String?`)

These checksums are numbers that each task calculates for their input and output. For example, the `"compile"` task calculates an MD5 checksum of all the source files. Similarly, the output checksum is for produced artifacts, e.g. checksum of `.class` files or `.jar` files, etc...

Example of an incremental task:

```
    @IncrementalTask(name = JvmCompilerPlugin.TASK_COMPILE, description = "Compile the project")
    fun taskCompile(project: Project) : IncrementalTaskInfo {
        val inputChecksum = Md5.toMd5Directories(project.sourceDirectories.map {
            File(project.directory, it)
        })
        return IncrementalTaskInfo(
                inputChecksum = inputChecksum,
                outputChecksum = "1",
                task = { project -> doTaskCompile(project) }
        )
    }
```

The advantage of checksums is that they take care of all the scenarios that would cause that task to run:

- A file was modified
- A file was added
- A file was removed

The output checksum covers the case where the input is unchanged but the output files were deleted or modified. If 
both the input and output checksums match the previous run, it's extremely likely that the task has nothing to do.

Another advantage of checksums is that they are generic and not necessarily tied to files. For example, a Kobalt task might perform some network operations and return a checksum based on a network result to avoid performing a more expensive operation (e.g. don't download a file from a server if it hasn't changed).

Internally, Kobalt maintains information about all the checksums and tasks that it has seen in a file `.kobalt/build-info.json`. Whenever an incremental task is about to run, Kobalt compares its input and output checksums to the ones from the previous run and if any differs, that task is run. Otherwise, it's skipped.

Example timings for Kobalt:

| Task | First run | Second run |
| ---- | --------- | ---------- |
|  kobalt-wrapper:compile | 627 ms | 22 ms |
|  kobalt-wrapper:assemble | 9 ms | 9 ms |
|  kobalt-plugin-api:compile | 10983 ms | 54 ms |
|  kobalt-plugin-api:assemble | 1763 ms | 154 ms |
|  kobalt:compile | 11758 ms | 11 ms |
|  kobalt:assemble | 42333 ms | 2130 ms |
| | 70 seconds | 2 seconds |

Android (u2020):

| Task | First run | Second run |
| ---- | --------- | ---------- |
|  u2020:generateRInternalDebug | 33025 ms | 1652 ms |
|  u2020:compileInternalDebug | 23 ms | 24 ms |
|  u2020:retrolambdaInternalDebug | 234 ms | 255 ms |
|  u2020:generateDexInternalDebug | 2 ms | 2 ms |
|  u2020:signApkInternalDebug | 449 ms | 394 ms |
|  u2020:assembleInternalDebug | 0 ms | 0 ms |
| | 33 seconds | 2 seconds |







