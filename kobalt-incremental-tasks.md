Kobalt's incremental task algorithm is not based on timestamps but on checksums.

You make a task incremental by declaring it @IncrementalTask instead of @Task. The only other difference is that instead of returning a `TaskResult`, incremental tasks return a `IncrementalTaskInfo`. This class contains three fields:

- A task closure, which is what your effective task: `(Project) -> TaskResult`
- An input checksum (`String`)
- An output checksum (`String`)

These checksums are numbers that each task calculates for their input and output. For example, the "compile" task calculates an MD5 checksum of all the source files. Similarly, the output checksum is for produced artifacts, e.g. checksum of `.class` files or `.jar` files, etc...

The advantage of checksums is that they take care of all the scenarios that would cause that task to run:

- A file was modified
- A file was added
- A file was removed

Also, checksums are generic and not necessarily tied to files. For example, a Kobalt task might perform some network operations and return a checksum based on a network result to avoid performing a more expensive operation (e.g. don't download a file from a server if it hasn't changed).

Internally, Kobalt maintains information about all the checksums and tasks that it has seen in a file `.kobalt/build-info.json`. Whenever an incremental task is about to run, Kobalt compares its input and output checksums to the ones from the previous run and if any differs, that task is run. Otherwise, it's skipped.


