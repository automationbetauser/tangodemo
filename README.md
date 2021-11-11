1A demo app for codestream

Checkout the files.

Execute the below command to build a docker image
> docker build -t codstream-demo-image:01 .

Execute the below command to run the built docker image
> docker run -d -p 8009:80 --name codestream-demo codestream-demo:01



