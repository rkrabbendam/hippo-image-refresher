Hippo image refresher
=================

This JCR runner refreshes existing image sets in the repository, using
image files from somewhere on disk. The location of the archive of
images is hardcoded for now. It (re)generates all formats in the image
set. These formats are also hard coded now.

- change image set formats and image source folder in the code

Local development
build & run
> mvn clean compile exec:java

rerun (faster)
> mvn -o -q compile exec:java

Build distributable application
build
> mvn clean package appassembler:assemble

- copy runner.properties to target/hippo-image-runner-0.0.1-SNAPSHOT/bin
- change/check username and password in runner.properties
- change query and image set definition in runner.properties
- compress the assembled directory and distribute

run app
cd bin
chmod +x jcr-runner
./jcr-runner
