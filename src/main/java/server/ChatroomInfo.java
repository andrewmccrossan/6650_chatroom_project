package server;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
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
  public Map<String, String> allMessages;
//  public ConcurrentHashMap<String,String> allMessages; // sender username - message pairs

  public ChatroomInfo() {
    this.members = new ArrayList<>();
    this.allMessages = Collections.synchronizedMap(new LinkedHashMap<>());
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
      int randomIndex = new Random().nextInt(members.size());
      return members.get(randomIndex);
    }
  }

  public void putMessage(String senderUsername, String message) {
    this.allMessages.put(senderUsername, message);
  }
}
