# Sort a large file in java using External Sort

[![][license img]][license]
[![docs-badge][]][docs]
![Java CI with Maven](https://github.com/EOnyenezido/file-sorter/workflows/Java%20CI%20with%20Maven/badge.svg)

This java program uses an External Sort Algorithm to sort a large file by splitting it into sorted smaller temporary files on the disk and merging the sorted temporary files into a final sorted output file.

Useful for when memory is limited or for sorting very large files which will not fit in memory.

Usage
------
Configuration may be passed either using a `config.properties` file or as arguments on the command line.
* With configuaration file
  ```
  # File to be sorted - Required
  inputFile=sample-text-file.txt

  # File for sorted results - Required
  outputFile=sorted-file.txt

  # Temporary directory for temp files - defaults to current directory
  tmpFilesDirectory=./temp

  # Sorting order - asc or desc - defaults to asc
  order=asc

  # Wrap after X words - defaults to 100
  wordWrap=100

  # Maximum number of temporary files - defaults to 1024
  maxTempFiles=1024
  ```
  then you can run;
  ```bash
  java -jar FileSorter-1.0-SNAPSHOT.jar
  ```
* Command line arguments

  ```bash
  java -jar FileSorter-1.0-SNAPSHOT.jar --inputfile input.txt --outputfile output.txt --tmpfilesdirectory ./tmp --maxtmpfiles 1024 --order asc --wordwrap 100
  ```

[license]:https://github.com/EOnyenezido/file-sorter/blob/main/LICENSE
[license img]:https://img.shields.io/badge/License-Apache%202-blue.svg

[docs-badge]:https://img.shields.io/badge/API-docs-blue.svg?style=flat-square
[docs]:https://eonyenezido.github.io/file-sorter/	
