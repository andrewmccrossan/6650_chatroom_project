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

  public Client(long clientID) {
    this.clientID = clientID;
    this.username = null;
    this.serverPorts = new int[]{ 10000, 49152, 49153, 49154, 49155 };
    try {
      // set up socket to LookUp server
      // TODO - anonymize hostname and ports
      Socket socket = new Socket("localhost", 10000);
//      socket.setSoTimeout(90000);

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
//      tcpClient.clientLogger.logger.severe(IClient.currTime() + e.getMessage());
    }
  }

  public void setClientGUI(ClientGUI clientGUI) {
    this.clientGUI = clientGUI;
  }

  public String contactServer(String message) {
    return null;
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
    // TODO - send this to the host of the current chatroom
    message = "message" + this.username + "@#@" + message;
    try {
      this.chatRoomServerWriter.write(message);
      this.chatRoomServerWriter.newLine();
      this.chatRoomServerWriter.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }

//    this.hostedChatroomServer.multicastMessage(message);
    System.out.println("Trying to send message: " + message);
    return "success";
  }

  private class MulticastMessageReceiver implements Runnable {
    private final byte[] buffer = new byte[256];

    public void run() {
      try {
        MulticastSocket multicastSocket = new MulticastSocket(4446);
        multicastSocket.joinGroup(groupIP);
        while (true) {
          DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
          multicastSocket.receive(packet);
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
        multicastSocket.leaveGroup(groupIP);
        multicastSocket.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
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
        int newID = Integer.parseInt(responseArray[1]);
        String groupIP = responseArray[2];
        this.groupIP = InetAddress.getByName(groupIP);
        this.hostedChatroomServer = new ChatroomServer(newID, this, groupIP);
        System.out.println("sag a");
        this.chatroomServerPort = this.hostedChatroomServer.portForClients;
        System.out.println("triple a");
        this.chatRoomServerAddress = InetAddress.getByName("localhost"); // TODO - get address dynamically somehow
        connectSocketToChatroomServer();
        MulticastMessageReceiver multicastMessageReceiver = new MulticastMessageReceiver();
        new Thread(multicastMessageReceiver).start();
        System.out.println("before responsearray return in attemptCreateChat");
        return responseArray[0];
      } else { // case where response is "exists"
        return responseArray[0];
      }
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public void connectSocketToChatroomServer() {
    try {
      System.out.println("a");
      this.socketConnectedToChatroomServer = new Socket("localhost", this.chatroomServerPort); // TODO - adddress
//      socket.setSoTimeout(90000);

      System.out.println("b");
      BufferedWriter writer = new BufferedWriter(
              new OutputStreamWriter(socketConnectedToChatroomServer.getOutputStream()));
      BufferedReader reader = new BufferedReader(
              new InputStreamReader(socketConnectedToChatroomServer.getInputStream()));
      System.out.println("c");
      this.chatRoomServerReader = reader;
      this.chatRoomServerWriter = writer;
      System.out.println("connectSocketToChatroom after writer");
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
//      IServer proposer1 = (IServer) Naming.lookup("rmi://" + hostname + "/ProposerService1");
//      IServer proposer2 = (IServer) Naming.lookup("rmi://" + hostname + "/ProposerService2");
//      client.addStorer(proposer1);
//      client.addStorer(proposer2);
//      client.setStorer(proposer1);

    // Automatically have client pre-populate server's data store and test it with CRUD.
//      client.automatedPopulating();

    // User can continuously send message to server, get response, and send more messages
//      client.manualConversation();
  }
}
