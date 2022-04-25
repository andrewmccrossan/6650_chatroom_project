package server;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class ChatroomInfo {
  public int ID;
  public String name;
  public int port;
  public String groupIP;
  public InetAddress inetAddress;
  public String hostUsername;
  public ArrayList<String> members;
//  public Map<String, String> allMessages;
  public ArrayList<String> messageSenders;
  public ArrayList<String> messageContents;
//  public boolean awaitingReassignment;
  public String multicastAddress;
//  public ConcurrentHashMap<String,String> allMessages; // sender username - message pairs

  public ChatroomInfo() {
    this.members = new ArrayList<>();
//    this.allMessages = Collections.synchronizedMap(new LinkedHashMap<>());
    this.messageSenders = new ArrayList<>();
    this.messageContents = new ArrayList<>();
//    this.awaitingReassignment = false;
  }

  public void setID(int ID) {
    this.ID = ID;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public void setGroupIP(String groupIP) {
    this.groupIP = groupIP;
  }

  public void setInetAddress(InetAddress inetAddress) {
    this.inetAddress = inetAddress;
  }

  public void setHostUsername(String hostUsername) {
    this.hostUsername = hostUsername;
  }

  public void putMember(String member) {
    this.members.add(member);
  }

  public void removeMember(String member) {
    this.members.remove(member);
  }

  public String getNewHost() {
    if (members.size() == 0) {
      return null;
    } else {
//      int randomIndex = new Random().nextInt(members.size());
//      return members.get(randomIndex);
      return members.get(0);
    }
  }

  public void putMessage(String senderUsername, String message) {
    this.messageSenders.add(senderUsername);
    this.messageContents.add(message);
//    this.allMessages.put(senderUsername, message);
  }

//  public void setAwaitingReassignment(boolean state) {
//    this.awaitingReassignment = state;
//  }

  public String getAllMessages() {
    StringBuilder allSentMessages = new StringBuilder();
    int numOfMessages = this.messageContents.size();
    System.out.println("size of contents: " + numOfMessages);
    for (int i = 0; i < numOfMessages; i++) {
      String sender = this.messageSenders.get(i);
      String contents = this.messageContents.get(i);
      allSentMessages.append(sender).append("@#@").append(contents).append("~##~");
    }
    return allSentMessages.toString();
  }
}
