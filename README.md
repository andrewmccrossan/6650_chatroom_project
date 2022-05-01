# CS6650 Chatroom Project

## Pre-compiling Instructions
If you do not have Maven installed on your machine, please either 1) consult this webpage https://maven.apache.org/download.cgi or 2) use homebrew and run the following in your terminal:
```
brew install maven
```

## Compiling / Running Instructions
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

## Operation Instructions

***
***
***
## Testing
Please watch demo video for display of more examples of application use cases.

### This section shows a user logging in successfully. They use the default account with username "admin" and password "password". Shown in order from top to bottom are client GUI before/after, client log, and LookUpServer log.
![image_1_clientgui_before](readme_images/image_1_clientgui_before-min.png)
***
![image_1_clientgui_after](readme_images/image_1_clientgui_after-min.png)
***
![image_1_clientlog](readme_images/image_1_clientlog-min.png)
***
![image_1_lookUplog](readme_images/image_1_lookUplog-min.png)
***
***

### This section shows a user attempting to log in with an invalid username/password. Shown in order from top to bottom are client GUI before/after, client log, and LookUpServer log.
![image_2_clientgui_before](readme_images/image_2_clientgui_before-min.png)
***
![image_2_clientgui_after](readme_images/image_2_clientgui_after-min.png)
***
![image_2_clientlog](readme_images/image_2_clientlog-min.png)
***
![image_2_lookUplog](readme_images/image_2_lookUplog-min.png)
***
***

### This section shows a user attempting to log into an account that is already logged in. Shown in order from top to bottom are client GUI before/after, client log, and LookUpServer log.
![image_3_clientgui_before](readme_images/image_3_clientgui_before-min.png)
***
![image_3_clientgui_after](readme_images/image_3_clientgui_after-min.png)
***
![image_3_clientlog](readme_images/image_3_clientlog-min.png)
***
![image_3_lookUplog](readme_images/image_3_lookUplog-min.png)
***
***

### This section shows a user registering an account successfully. Shown in order from top to bottom are client GUI before/after, client log, and LookUpServer log.
![image_4_clientgui_before](readme_images/image_4_clientgui_before-min.png)
***
![image_4_clientgui_after](readme_images/image_4_clientgui_after-min.png)
***
![image_4_clientlog](readme_images/image_4_clientlog-min.png)
***
![image_4_lookUplog](readme_images/image_4_lookUplog-min.png)
***
***

### This section shows a user attempting to register an account that uses an existing username. Shown in order from top to bottom are client GUI before/after, client log, and LookUpServer log.
![image_5_clientgui_before](readme_images/image_5_clientgui_before-min.png)
***
![image_5_clientgui_after](readme_images/image_5_clientgui_after-min.png)
***
![image_5_clientlog](readme_images/image_5_clientlog-min.png)
***
![image_5_lookUplog](readme_images/image_5_lookUplog-min.png)
***
***


### This section shows a user creating a chatroom successfully. Shown in order from top to bottom are client GUI before/after (after includes Chatroom server GUI too), client log, LookUpServer log, and chatroom server log.
![image_6_clientgui_before](readme_images/image_6_clientgui_before-min.png)
***
![image_6_clientgui_after](readme_images/image_6_clientgui_after-min.png)
***
![image_6_clientlog](readme_images/image_6_clientlog-min.png)
***
![image_6_lookUplog](readme_images/image_6_lookUplog-min.png)
***
![image_6_chatroomServerlog](readme_images/image_6_chatroomServerlog-min.png)
***
***

### This section shows a user attempting to create a chatroom with a name that already exists. Shown in order from top to bottom are client GUI before/after, client log, and LookUpServer log.
![image_7_clientgui_before](readme_images/image_7_clientgui_before-min.png)
***
![image_7_clientgui_after](readme_images/image_7_clientgui_after-min.png)
***
![image_7_clientlog](readme_images/image_7_clientlog-min.png)
***
![image_7_lookUplog](readme_images/image_7_lookUplog-min.png)
***
***

### This section shows a user joining a chatroom successfully. Shown in order from top to bottom are client GUI before/after, client log, and LookUpServer log.
![image_8_clientgui_before](readme_images/image_8_clientgui_before-min.png)
***
![image_8_clientgui_after](readme_images/image_8_clientgui_after-min.png)
***
![image_8_clientlog](readme_images/image_8_clientlog-min.png)
***
![image_8_lookUplog](readme_images/image_8_lookUplog-min.png)
***
***

### This section shows a user attempting to join a chatroom with a chatroom name that does not exist. Shown in order from top to bottom are client GUI before/after, client log, and LookUpServer log.
![image_9_clientgui_before](readme_images/image_9_clientgui_before-min.png)
***
![image_9_clientgui_after](readme_images/image_9_clientgui_after-min.png)
***
![image_9_clientlog](readme_images/image_9_clientlog-min.png)
***
![image_9_lookUplog](readme_images/image_9_lookUplog-min.png)
***
***

### This section shows a user clicking Logout while in chatroom selection screen. Shown in order from top to bottom are client GUI before/after, client log, and LookUpServer log.
![image_10_clientgui_before](readme_images/image_10_clientgui_before-min.png)
***
![image_10_clientgui_after](readme_images/image_10_clientgui_after-min.png)
***
![image_10_clientlog](readme_images/image_10_clientlog-min.png)
***
![image_10_lookUplog](readme_images/image_10_lookUplog-min.png)
***
***


### This section shows a user sending a message to a chatroom while other user is also in the chatroom to receive it. Shown in order from top to bottom are client / chatroomServer GUIs before/after, client logs, and LookUpServer log.
![image_11_clientgui_before](readme_images/image_11_clientgui_before-min.png)
***
![image_11_clientgui_after](readme_images/image_11_clientgui_after-min.png)
***
![image_11_clientlog](readme_images/image_11_clientlog-min.png)
***
![image_11_lookUplog](readme_images/image_11_lookUplog-min.png)
***
***

### This section shows a user updating the users currently in the chatroom. Shown in order from top to bottom are client GUI before/after, client log, and LookUpServer log.
![image_12_clientgui_before](readme_images/image_12_clientgui_before-min.png)
***
![image_12_clientgui_after](readme_images/image_12_clientgui_after-min.png)
***
![image_12_clientlog](readme_images/image_12_clientlog-min.png)
***
![image_12_lookUplog](readme_images/image_12_lookUplog-min.png)
***
***

### This section shows a non-host user clicking back to chat selection screen. Shown in order from top to bottom are client GUI before/after, client log, chatroom server log, and LookUpServer log.
![image_13_clientgui_before](readme_images/image_13_clientgui_before-min.png)
***
![image_13_clientgui_after](readme_images/image_13_clientgui_after-min.png)
***
![image_13_clientlog](readme_images/image_13_clientlog-min.png)
***
![image_13_lookUplog](readme_images/image_13_lookUplog-min.png)
***
***

### This section shows a host user clicking back to chat selection screen. Shown in order from top to bottom are client / chatroomServers GUIs before/after, host client log, non-host client log, chatroom server log, and LookUpServer log.
![image_14_clientgui_before](readme_images/image_14_clientgui_before-min.png)
***
![image_14_clientgui_after](readme_images/image_14_clientgui_after-min.png)
***
![image_14_clientlog](readme_images/image_14_clientHostlog-min.png)
***
![image_14_clientlog](readme_images/image_14_clientNonHostlog-min.png)
***
![image_14_lookUplog](readme_images/image_14_chatroomServerlog-min.png)
***
![image_14_lookUplog](readme_images/image_14_lookUplog-min.png)
***
***

### This section shows a host user exiting out of its window, and thus stopping the process that BOTH its client AND the chatroomServer are running on. Shown in order from top to bottom are client / chatroomServer GUIs before/after, host client log, non-host client log, chatroom server log, and LookUpServer log.
![image_15_clientgui_before](readme_images/image_15_clientgui_before-min.png)
***
![image_15_clientgui_after](readme_images/image_15_clientgui_after-min.png)
***
![image_15_clientlog](readme_images/image_15_clientNonHostlog-min.png)
***
![image_15_lookUplog](readme_images/image_15_chatroomServerlog-min.png)
***
![image_15_lookUplog](readme_images/image_15_lookUplog-min.png)
***
***


### This section shows two users, where they are each in their own chatroom that they host. When they send messages, the messages only go to their own chatroom. Shown in order from top to bottom are client / chatroomServer GUIs before/after, client Jonathan log, client Mary log, chatroom server Jonathan log, chatroom server Mary log, and LookUpServer log.
![image_16_clientgui_before](readme_images/image_16_clientgui_before-min.png)
***
![image_16_clientgui_after](readme_images/image_16_clientgui_after-min.png)
***
![image_16_clientlog](readme_images/image_16_clientJonathanlog-min.png)
***
![image_16_clientgui_after](readme_images/image_16_clientMarylog-min.png)
***
![image_16_clientlog](readme_images/image_16_chatroomServerJonathanlog-min.png)
***
![image_16_lookUplog](readme_images/image_16_chatroomServerMarylog-min.png)
***
![image_16_lookUplog](readme_images/image_16_lookUplog-min.png)
***
***

### This section shows a PAXOS round being undertaken when a user logs into the account with username "admin". Shown in order from top to bottom are proposer LookUpServer log, acceptor LookUpServer log, and learner LookUpServer log.
![image_17_clientlog](readme_images/image_17_proposerLookUplog-min.png)
***
![image_17_lookUplog](readme_images/image_17_acceptorLookUplog-min.png)
***
![image_17_lookUplog](readme_images/image_17_learnerLookUplog-min.png)
***
***
