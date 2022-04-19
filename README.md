# CS6650 Chatroom Project

## Pre-compiling Instructions
If you do not have Maven installed on your machine, please either 1) consult this webpage https://maven.apache.org/download.cgi or 2) use homebrew and run the following in your terminal:
```
brew install maven
```

## Compiling Instructions
Since this is a Maven project, you should use the terminal to cd into the root directory of this project (6650_chatroom_project), and run the following command to create an executable JAR file:
```
mvn package
```
Now, dependencies should have been installed and an executable JAR file should have been created in the target directory. This JAR file's argument should be either "client" or "server". While still in the root directory (6650_chatroom_project), you can create the server as by running the following command:
```
java -jar target/6650_chatroom_project-1.0-SNAPSHOT.jar server
```
The syntax for running a client is as follows:
```
java -jar target/6650_chatroom_project-1.0-SNAPSHOT.jar client
```

