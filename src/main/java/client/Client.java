package client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import gui.ClientGUI;
import server.ChatroomInfo;
import server.ChatroomServer;
import server.LookUpServer;

public class Client {

  long clientID;
  ClientGUI clientGUI;
  int[] serverPorts;
  Socket socket;
  BufferedReader reader;
  BufferedWriter writer;
  String username;
  ChatroomServer hostedChatroomServer;
  InetAddress groupIP;
  int chatroomServerPort;
  Socket socketConnectedToChatroomServer;
  InetAddress chatRoomServerAddress;
  BufferedReader chatRoomServerReader;
  BufferedWriter chatRoomServerWriter;
  String mostRecentChatroomName;
  Thread multicastMessageReceiverThread;
  MulticastMessageReceiver currentMulticastMessageReceiver;

  public Client(long clientID) {
    this.clientID = clientID;
    this.username = null;
    this.serverPorts = new int[]{ 10000, 49152, 49153, 49154, 49155 };
    this.mostRecentChatroomName = null;
    this.multicastMessageReceiverThread = null;
    try {
      // set up socket to LookUp server
      // TODO - anonymize hostname and ports
      Socket socket = new Socket("localhost", 10000);
      BufferedWriter writer = new BufferedWriter(
              new OutputStreamWriter(socket.getOutputStream()));
      BufferedReader reader = new BufferedReader(
              new InputStreamReader(socket.getInputStream()));
      this.socket = socket;
      this.reader = reader;
      this.writer = writer;

//      reader.close();
//      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void setClientGUI(ClientGUI clientGUI) {
    this.clientGUI = clientGUI;
  }

  public String attemptLogin(String username, String password) {
    try {
      this.username = username;
      this.writer.write("login " + username + " " + password);
      this.writer.newLine();
      this.writer.flush();
      return this.reader.readLine();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public String attemptRegister(String username, String password) {
    try {
      this.username = username;
      this.writer.write("register " + username + " " + password);
      this.writer.newLine();
      this.writer.flush();
      return this.reader.readLine();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public String sendNewChatroomMessage(String message) {
    message = "message" + this.username + "@#@" + message;
    try {
      this.chatRoomServerWriter.write(message);
      this.chatRoomServerWriter.newLine();
      this.chatRoomServerWriter.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return "success";
  }

  private class MulticastMessageReceiver implements Runnable {
    private final byte[] buffer = new byte[256];
    private MulticastSocket multicastSocket;
    boolean isAlive;

    public void run() {
      try {
        MulticastSocket multicastSocket = new MulticastSocket(4446);
        this.multicastSocket = multicastSocket;
        multicastSocket.joinGroup(groupIP);
        this.isAlive = true;
        while (isAlive) {
          DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
          try {
            multicastSocket.receive(packet);
          } catch (IOException ioe) {
            continue;
          }
          String receivedMessage = new String(packet.getData(), 0, packet.getLength());
          if (receivedMessage.equalsIgnoreCase("stopMulticast")) {
            break;
          } else if (receivedMessage.substring(0, 10).equalsIgnoreCase("chatkey125")) {
            receivedMessage = receivedMessage.substring(10);
            String[] fullMessage = receivedMessage.split("@#@");
            String sender = fullMessage[0];
            String actualMessage = fullMessage[1];
            clientGUI.displayNewMessage(sender, actualMessage);
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    public void turnOff() {
      isAlive = false;
      try {
        multicastSocket.leaveGroup(groupIP);
      } catch (IOException e) {
        e.printStackTrace();
      }
      multicastSocket.close();
    }
  }

  public String attemptCreateChat(String chatName) {
    try {
      this.writer.write("createChat " + chatName + " " + username);
      this.writer.newLine();
      this.writer.flush();
      String response = this.reader.readLine();
      String[] responseArray = response.split(" ");
      if (responseArray[0].equalsIgnoreCase("success")) {
        this.mostRecentChatroomName = chatName;
        if (this.currentMulticastMessageReceiver != null) {
          this.currentMulticastMessageReceiver.turnOff();
        }
        int newID = Integer.parseInt(responseArray[1]);
        String groupIP = responseArray[2];
        this.groupIP = InetAddress.getByName(groupIP);
        this.hostedChatroomServer = new ChatroomServer(newID, this, groupIP, chatName);
        this.chatroomServerPort = this.hostedChatroomServer.portForClients;
        // tell LookUpServer what port this chatroom server is listening for new user connections on
        this.writer.write("updateChatConnectionPort " + this.chatroomServerPort + " " + chatName);
        this.writer.newLine();
        this.writer.flush();
        String updateResponse = this.reader.readLine();
        this.chatRoomServerAddress = InetAddress.getByName("localhost"); // TODO - get address dynamically somehow (or maybe not since it will always be on localhost
        connectSocketToChatroomServer();
        MulticastMessageReceiver multicastMessageReceiver = new MulticastMessageReceiver();
        this.currentMulticastMessageReceiver = multicastMessageReceiver;
        this.multicastMessageReceiverThread = new Thread(multicastMessageReceiver);
        this.multicastMessageReceiverThread.start();
        return responseArray[0];
      } else { // case where response is "exists"
        return responseArray[0];
      }
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public ArrayList<String[]> attemptGetNumUsersInChatrooms() {
    try {
      this.writer.write("getNumUsers ");
      this.writer.newLine();
      this.writer.flush();
      String response = this.reader.readLine();
      if (response.equalsIgnoreCase("")) {
        return new ArrayList<>();
      }
      String[] responseArray = response.split("@&@");
      ArrayList<String[]> namesNumbers = new ArrayList<>();
      for (int i = 0; i < responseArray.length; i++) {
        namesNumbers.add(responseArray[i].split("%&%"));
      }
      return namesNumbers;
    } catch (IOException e) {
      e.printStackTrace();
      return new ArrayList<>();
    }
  }

  public String attemptJoinChat(String chatName) {
    try {
      this.writer.write("joinChat " + chatName + " " + username);
      this.writer.newLine();
      this.writer.flush();
      String response = this.reader.readLine();
      String[] responseArray = response.split(" ");
      if (responseArray[0].equalsIgnoreCase("success")) {
        if (this.currentMulticastMessageReceiver != null) {
          this.currentMulticastMessageReceiver.turnOff();
        }
        String socketAddress = responseArray[1];
        String socketPort = responseArray[2];
        String groupIP = responseArray[3];
        this.groupIP = InetAddress.getByName(groupIP);
        this.chatroomServerPort = Integer.parseInt(socketPort);
        this.chatRoomServerAddress = InetAddress.getByName(socketAddress);
        if (!chatName.equals(this.mostRecentChatroomName)) {
          connectSocketToChatroomServer();
        }
        this.mostRecentChatroomName = chatName;
        MulticastMessageReceiver multicastMessageReceiver = new MulticastMessageReceiver();
        this.currentMulticastMessageReceiver = multicastMessageReceiver;
        new Thread(multicastMessageReceiver).start();
        return responseArray[0];
      } else { // case where response is "nonexistent"
        return responseArray[0];
      }
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public ArrayList<String> attemptGetUsersInChatroom(String chatName) {
    try {
      this.writer.write("getMembers " + chatName);
      this.writer.newLine();
      this.writer.flush();
      String response = this.reader.readLine();
      if (response.equalsIgnoreCase("nonexistent")) {
        // log here that chatroom name does not exist.
        return new ArrayList<>();
      }
      String[] responseArray = response.split("@#@");
      ArrayList<String> members = new ArrayList<>();
      for (int i = 1; i < responseArray.length; i++) {
        members.add(responseArray[i]);
      }
      return members;
    } catch (IOException e) {
      e.printStackTrace();
      return new ArrayList<>();
    }
  }

  public void connectSocketToChatroomServer() {
    try {
      this.socketConnectedToChatroomServer = new Socket(this.chatRoomServerAddress, this.chatroomServerPort);

      BufferedWriter writer = new BufferedWriter(
              new OutputStreamWriter(socketConnectedToChatroomServer.getOutputStream()));
      BufferedReader reader = new BufferedReader(
              new InputStreamReader(socketConnectedToChatroomServer.getInputStream()));
      this.chatRoomServerReader = reader;
      this.chatRoomServerWriter = writer;
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Return current time to the millisecond precision.
   * @return String
   */
  public static String currTime() {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    Date now = new Date();
    String strDate = sdf.format(now);
    return strDate + " : ";
  }

  /**
   * Driver for client that looks up Storer object, an initiates pre-populating data store
   * and then gives control to user to submit operations.
   * @param args
   */
  public static void main(String[] args) {
    String hostname = "localhost";
//    if (args.length != 1) {
//      System.out.println(Client.currTime() + "Using localhost.");
//    } else {
//      hostname = args[0];
//      System.out.println(Client.currTime() + "Using " + hostname);
//    }

    // Create the ClientGUI
    long clientID = new Date().getTime();
    Client client = new Client(clientID);
    ClientGUI clientGUI = new ClientGUI(client);
    client.setClientGUI(clientGUI);
  }
}
