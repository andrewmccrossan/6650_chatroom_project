package server;

import java.net.InetAddress;
import java.util.ArrayList;

/**
 * Class for holding all the information about a chatroom. This includes the ID, name, server socket
 * port and address, groupIP for multicasting, username of host client, current members in chatroom,
 * ordered list of message sender usernames, and ordered list of the contents of the messages sent.
 */
public class ChatroomInfo {
  public int ID;
  public String name;
  public int port;
  public String groupIP;
  public InetAddress inetAddress;
  public String hostUsername;
  public ArrayList<String> members;
  public ArrayList<String> messageSenders;
  public ArrayList<String> messageContents;

  /**
   * Constructor for chatroomInfo object that initializes the arraylists for the current members,
   * sender usernames, and contents of messages sent.
   */
  public ChatroomInfo() {
    this.members = new ArrayList<>();
    this.messageSenders = new ArrayList<>();
    this.messageContents = new ArrayList<>();
  }

  /**
   * Set ID of chatroom
   * @param ID
   */
  public void setID(int ID) {
    this.ID = ID;
  }

  /**
   * Set name of chatroom
   * @param name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Set the port that the chatroom server socket is on
   * @param port
   */
  public void setPort(int port) {
    this.port = port;
  }

  /**
   * Set the groupIP that the chatroom multicasts messages to
   * @param groupIP
   */
  public void setGroupIP(String groupIP) {
    this.groupIP = groupIP;
  }

  /**
   * Set the address that the chatroom server socket is on
   * @param inetAddress
   */
  public void setInetAddress(InetAddress inetAddress) {
    this.inetAddress = inetAddress;
  }

  /**
   * Set the username of the host client
   * @param hostUsername
   */
  public void setHostUsername(String hostUsername) {
    this.hostUsername = hostUsername;
  }

  /**
   * Put a member into the list of users in the chatroom.
   * @param member
   */
  public void putMember(String member) {
    this.members.add(member);
  }

  /**
   * Remove a member user from the list of users in the chatroom
   * @param member
   */
  public void removeMember(String member) {
    this.members.remove(member);
  }

  /**
   * Get a new host for the newly recreated chatroom. The new host is the oldest member.
   * @return a new host for the newly recreated chatroom.
   */
  public String getNewHost() {
    if (members.size() == 0) {
      return null;
    } else {
      return members.get(0);
    }
  }

  /**
   * Put the username of the sender of a message into the sender list, and put the message contents
   * into the list of message contents, effectively storing the message in memory.
   * @param senderUsername
   * @param message
   */
  public void putMessage(String senderUsername, String message) {
    this.messageSenders.add(senderUsername);
    this.messageContents.add(message);
  }

  /**
   * Get all of the messages that have been sent to this chatroom. This includes getting the sender
   * username and the corresponding message for every message.
   * @return a string that contains all of the messages sent and their corresponding senders
   */
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
