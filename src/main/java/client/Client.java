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
import logger.ProgLogger;
import server.ChatroomServer;

/**
 * Client class that handles all logic for a user in this multi-chatroom application. A client
 * connects to a LookUpServer which holds information about existing chatrooms that the client can
 * connect to. A client can login/register an account, join/create a chatroom, and send messages and
 * receive messages while in a chatroom.
 */
public class Client {

  long clientID;
  ProgLogger logger;
  String proposerLookUphostname;
  ClientGUI clientGUI;
  int[] proposerLookUpPorts;
  int unusedLookUpPort;
  Socket socket;
  BufferedReader reader;
  BufferedWriter writer;
  public String username;

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

  /**
   * Constructor for the Client class. This initializes several pieces of information kept in state,
   * like the client ID, the host address and ports of proposer LookUp servers that the client can try
   * to connect to, the socket and BufferedReader and BufferedWriter for a successful LookUp server
   * connection, etc.
   * @param clientID
   */
  public Client(long clientID, ProgLogger logger) {
    this.clientID = clientID;
    this.logger = logger;
    this.username = null;
    this.proposerLookUphostname = "localhost";
    // THe ports for the proposer LookUp servers' sockets that are waiting to accept a connection.
    this.proposerLookUpPorts = new int[]{ 10000, 53333 };
    this.mostRecentChatroomName = null;
    this.multicastMessageReceiverThread = null;
    // set up socket to LookUp server by trying to connect to a proposer LookUp socket until one
    // successfully connects.
    for (int i = 0; i < this.proposerLookUpPorts.length; i++) {
      try {
        Socket socket = new Socket(this.proposerLookUphostname, this.proposerLookUpPorts[i]);
        this.logger.logger.info("Connected to LookUp server socket");
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream()));
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        // keep the reader and writer in state so that they may be used by threads in this class to
        // either listen to the LookUp server socket or send messages to it.
        this.socket = socket;
        this.reader = reader;
        this.writer = writer;
        // set the unusedLookUpPort to the other port in case this one fails
        this.unusedLookUpPort = proposerLookUpPorts[Math.abs(i-1)];
        break;
      } catch (IOException e) {
        logger.logger.warning("Unable to connect to LookUp server");
      }
    }
  }

  /**
   * Set the client GUI attribute.
   * @param clientGUI
   */
  public void setClientGUI(ClientGUI clientGUI) {
    this.clientGUI = clientGUI;
  }

  /**
   * Attempt to log the user in with the given username and password. This involves sending the info
   * to the LookUp server to see if the username and password match a real account that is not already
   * logged in. If the socket fails then we attempt to contact another LookUp server to verify login
   * info. This is an example of fault tolerance.
   * @param username
   * @param password
   * @return a string response for if login was successful, if user is already logged in, or unsuccessful.
   */
  public String attemptLogin(String username, String password) {
    try {
      this.username = username;
      this.writer.write("login@#@" + username + "@#@" + password);
      this.writer.newLine();
      this.writer.flush();
      return this.reader.readLine();
    } catch (IOException e) {
      // use another server
      useAnotherServer();
      return attemptLogin(username, password);
    }
  }

  /**
   * In the case that the current socket connected to a LookUp server has failed, then this method
   * attempts to connect to another LookUp server and set its buffered reader and writer attributes
   * to the new ones.
   */
  public void useAnotherServer() {
    // attempt to connect to another proposer
    Socket socket = null;
    try {
      logger.logger.info("Attempting to use another LookUp server");
      socket = new Socket(proposerLookUphostname, this.unusedLookUpPort);
      BufferedWriter writer = new BufferedWriter(
              new OutputStreamWriter(socket.getOutputStream()));
      BufferedReader reader = new BufferedReader(
              new InputStreamReader(socket.getInputStream()));
      this.socket = socket;
      this.reader = reader;
      this.writer = writer;
    } catch (IOException e) {
      logger.logger.severe("Unable to use another LookUp server");
    }
  }

  /**
   * Attempt to register and log in the user with the given username and password. This involves sending the info
   * to the LookUp server to see if the username matches a real account already. If not, then the username
   * and password are saved for this user and they are logged in. If the socket fails then we attempt
   * to contact another LookUp server to verify register info. This is an example of fault tolerance.
   * @param username
   * @param password
   * @return a string response for if register was successful, or if a user by that username exists.
   */
  public String attemptRegister(String username, String password) {
    try {
      this.username = username;
      this.writer.write("register@#@" + username + "@#@" + password);
      this.writer.newLine();
      this.writer.flush();
      return this.reader.readLine();
    } catch (IOException e) {
      // use another server
      useAnotherServer();
      return attemptRegister(username, password);
    }
  }

  /**
   * Attempt to logout while on the chat selection screen. This involves sending a message to the
   * LookUp server to logout the user. If the socket fails then we attempt to contact another
   * LookUp server to verify logout. This is an example of fault tolerance.
   * @return a string response corresponding to if the user was removed successfully.
   */
  public String attemptChatSelectionLogout() {
    try {
      this.writer.write("chatSelectionLogout@#@" + this.username);
      this.writer.newLine();
      this.writer.flush();
      return this.reader.readLine();
    } catch (IOException e) {
      // use another server
      useAnotherServer();
      return attemptChatSelectionLogout();
    }
  }

  /**
   * Attempt to logout while in a chatroom. This involves sending a message to the LookUp server to
   * logout the user. If the client is a host then the LookUp server will coordinate choosing a new
   * host for the chatroom if there are users left.
   * @return a string response corresponding to if the user was removed successfully.
   */
  public String attemptChatroomLogout() {
    try {
      this.chatRoomServerWriter.write("chatroomLogout@#@" + this.username);
      this.chatRoomServerWriter.newLine();
      this.chatRoomServerWriter.flush();
      return "success";
    } catch (IOException e) {
      return "unsuccessful";
    }
  }

  /**
   * Attempt to leave a chatroom and go back to chat selection screen. This involves sending a message to the LookUp server to
   * remove the user from the chatroom. If the client is a host then the LookUp server will coordinate choosing a new
   * host for the chatroom if there are users left.
   * @return a string response corresponding to if the user was removed successfully.
   */
  public String attemptBackToChatSelection() {
    try {
      this.chatRoomServerWriter.write("backToChatSelection@#@" + this.username);
      this.chatRoomServerWriter.newLine();
      this.chatRoomServerWriter.flush();
      return "success";
    } catch (IOException e) {
      return "unsuccessful";
    }
  }

  /**
   * Send a new chatroom message to the Chatroom server so that it can multicast the message to all
   * users in the chatroom.
   * @param message
   * @return a string response corresponding to if the message was sent successfully
   */
  public String sendNewChatroomMessage(String message) {
    message = "message" + "@#@" + this.username + "@#@" + message;
    try {
      this.chatRoomServerWriter.write(message);
      this.chatRoomServerWriter.newLine();
      this.chatRoomServerWriter.flush();
    } catch (IOException e) {
      logger.logger.warning("Unable to send new chatroom message");
    }
    return "success";
  }

  /**
   * Class that is to be executed by a thread and is used for receiving multicast messages. These
   * messages are multicast out by the Chatroom server, and the messages are received by any user in
   * the chatroom. This receivers is usually used for receiving chatroom messages, but it is also used to
   * receive info from the LookUp server in the case that a host client leaves their chatroom and the
   * LookUp server must decide on who is the next host.
   */
  private class MulticastMessageReceiver implements Runnable {
    private final byte[] buffer = new byte[256];
    private MulticastSocket multicastSocket;
    boolean isAlive;

    /**
     * Run the multicast message receiver. This has a multicast socket join a certain group IP and
     * listen for messages and handle them as necessary.
     */
    public void run() {
      try {
        MulticastSocket multicastSocket = new MulticastSocket(4446);
        logger.logger.info("Connected to multicast socket.");
        this.multicastSocket = multicastSocket;
        multicastSocket.joinGroup(groupIP);
        logger.logger.info("Joined multicast group " + groupIP);
        this.isAlive = true;
        // continuously listen to this multicast
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
          // in the case that another process publishes messages to the same group IP, we give a key
          // at the beginning of every message that corresponds to this application.
          } else if (receivedMessage.substring(0, 10).equalsIgnoreCase("chatkey125")) {
            receivedMessage = receivedMessage.substring(10);
            String[] fullMessage = receivedMessage.split("@#@");
            // this is the case that the LookUp server is notifying everyone in the meeting that a new
            // host has been chosen.
            if (fullMessage[0].equalsIgnoreCase("$@newHost@$")) {
              String newHost = fullMessage[1];
              logger.logger.info("Received notification that new host " + newHost + " was chosen");
              if (username.equalsIgnoreCase(newHost)) {
                attemptReCreateChat(mostRecentChatroomName);
              }
            // this is case that LookUp server is notifying clients in a chatroom of the new address
            // and port of host chatroom server to connect to.
            } else if (fullMessage[0].equalsIgnoreCase("$@notifyRecreation@$")) {
              String newServerHost = fullMessage[1];
              String newServerAddress = fullMessage[2];
              logger.logger.info("Received notification that chatroom is being recreated");
              if (!username.equalsIgnoreCase(newServerHost)) {
                // Documented bug in JDK that InetAddress.getByName() does not function properly
                // with 127.0.0.1 since it does not recognize it as localhost without modifying local
                // environ files.
                if (newServerAddress.contains("127.0.0.1")) {
                  newServerAddress = "localhost";
                }
                chatRoomServerAddress = InetAddress.getByName(newServerAddress);
                chatroomServerPort = Integer.parseInt(fullMessage[3]);
                // set up socket to connect to chatroom server to be able to send messages and logout notifications
                connectSocketToChatroomServer();
              }
            // this is the case of receiving a text message from the chatroom server that was sent by
            // a user in the chatroom.
            } else {
              String sender = fullMessage[0];
              String actualMessage = fullMessage[1];
              clientGUI.displayNewMessage(sender, actualMessage);
            }
          }
        }
      } catch (IOException e) {
        logger.logger.severe("Multicast socket connection failed");
      }
    }

    /**
     * Close the multicast socket when user leaves the chatroom.
     */
    public void turnOff() {
      isAlive = false;
      try {
        multicastSocket.leaveGroup(groupIP);
      } catch (IOException e) {
        logger.logger.warning("Could not leave multicast group");
      }
      multicastSocket.close();
    }
  }

  /**
   * Attempt to recreate a chatroom server when this client is leaving a chatroom. The LookUp server
   * is contacted to recreate the chat by the given name with the given username as the host. Given
   * success from LookUp server, create a new chatroom server and tell LookUp server the address and
   * port of the chatroom server socket that other members can connect to.
   * @param chatName
   * @return a string response corresponding to if new chatroom server was successfully created.
   */
  public String attemptReCreateChat(String chatName) {
    try {
      this.writer.write("reCreateChat@#@" + chatName + "@#@" + username);
      this.writer.newLine();
      this.writer.flush();
      String response = this.reader.readLine();
      logger.logger.info("Recreating chatroom after last host left");
      String[] responseArray = response.split("@#@");
      if (responseArray[0].equalsIgnoreCase("success")) {
        int reUsedID = Integer.parseInt(responseArray[1]);
        String groupIP = responseArray[2];
        String heartbeatAddress = responseArray[3];
        int heartbeatPort = Integer.parseInt(responseArray[4]);
        this.groupIP = InetAddress.getByName(groupIP);
        // Create a chatroom server that will use the groupID to publish multicast messages, and use
        // the address and port given by LookUp server to connect to LookUp server for heatbeat messages
        // and other communication.
        this.hostedChatroomServer = new ChatroomServer(reUsedID, this, groupIP, chatName, heartbeatAddress, heartbeatPort);

        this.chatroomServerPort = this.hostedChatroomServer.portForClients;
        // tell LookUpServer what port this chatroom server is listening for new user connections on
        this.writer.write("updateChatConnectionPort@#@" + this.chatroomServerPort + "@#@" + chatName);
        this.writer.newLine();
        this.writer.flush();
        String updateResponse = this.reader.readLine();
        logger.logger.info("Updated chatroom server connection port");
        if (updateResponse.equalsIgnoreCase("success")) {
          // Tell the LookUp server what address and port this recreated chatroom is listening for members
          // on so that the LookUp server can send this information to the old members.
          this.writer.write("notifyMembersOfRecreation@#@" + this.chatRoomServerAddress + "@#@" + this.chatroomServerPort + "@#@" + chatName + "@#@" + username);
          this.writer.newLine();
          this.writer.flush();
          String notifyResponse = this.reader.readLine();
          logger.logger.info("Notified all other members of chatroom of chatroom recreation");
          // Get the entire history of messages for this chatroom from the LookUp server.
          this.writer.write("getAllChatroomMessages@#@" + chatName);
          this.writer.newLine();
          this.writer.flush();
          String chatroomMessages = this.reader.readLine();
          logger.logger.info("Received history of all chatroom messages");
          if (chatroomMessages.length() > 0) {
            String[] splitUpChatroomMessages = chatroomMessages.split("~##~");
            this.hostedChatroomServer.replenishLogDisplay(splitUpChatroomMessages);
          }
        }
        this.chatRoomServerAddress = InetAddress.getByName("localhost");
        // set up socket to connect to chatroom server to be able to send messages and logout notifications
        connectSocketToChatroomServer();
        return responseArray[0];
      } else { // case where response is "non-existent"
        return responseArray[0];
      }
    } catch (IOException e) {
      // use another server
      useAnotherServer();
      return attemptReCreateChat(chatName);
    }
  }

  /**
   * Attempt to create a new chatroom with the given chat name. Tell the LookUp server the name of the
   * chatroom this client would like to create. If chatroom name already exists then return "exists",
   * but if it does not exist then create a new chatroom server object with the group IP that it
   * should multicast messages to and the address/port that it should connect to so that the LookUp
   * server can send heartbeat messages to the chatroom server and the chatroom server can send messages
   * to the LookUp server so that the LookUp server can maintain state about the server.
   * @param chatName
   * @return a string corresponding to if creating the chat was successful or not.
   */
  public String attemptCreateChat(String chatName) {
    try {
      this.writer.write("createChat@#@" + chatName + "@#@" + username);
      this.writer.newLine();
      this.writer.flush();
      String response = this.reader.readLine();
      logger.logger.info("Create new chat " + chatName);
      String[] responseArray = response.split("@#@");
      if (responseArray[0].equalsIgnoreCase("success")) {
        this.mostRecentChatroomName = chatName;
        if (this.currentMulticastMessageReceiver != null) {
          this.currentMulticastMessageReceiver.turnOff();
        }
        int newID = Integer.parseInt(responseArray[1]);
        String groupIP = responseArray[2];
        String heartbeatAddress = responseArray[3];
        int heartbeatPort = Integer.parseInt(responseArray[4]);
        this.groupIP = InetAddress.getByName(groupIP);
        this.hostedChatroomServer = new ChatroomServer(newID, this, groupIP, chatName, heartbeatAddress, heartbeatPort);
        this.chatroomServerPort = this.hostedChatroomServer.portForClients;

        // tell LookUpServer what port this chatroom server is listening for new user connections on
        this.writer.write("updateChatConnectionPort@#@" + this.chatroomServerPort + "@#@" + chatName);
        this.writer.newLine();
        this.writer.flush();
        String updateResponse = this.reader.readLine();
        this.chatRoomServerAddress = InetAddress.getByName("localhost");
        // set up socket to connect to chatroom server to be able to send messages and logout notifications
        connectSocketToChatroomServer();

        // set up a thread for this client to receive multicast messages on from the new chatroom server
        MulticastMessageReceiver multicastMessageReceiver = new MulticastMessageReceiver();
        this.currentMulticastMessageReceiver = multicastMessageReceiver;
        this.multicastMessageReceiverThread = new Thread(multicastMessageReceiver);
        this.multicastMessageReceiverThread.start();
        return responseArray[0];
      } else { // case where response is "exists"
        return responseArray[0];
      }
    } catch (IOException e) {
      // use another server
      useAnotherServer();
      return attemptCreateChat(chatName);
    }
  }

  /**
   * Attempt to get the number of users in the chatroom that this client is currently in. Contact
   * LookUp server to get this information.
   * @return an arraylist of the strings pairings of the chatname and number of users
   */
  public ArrayList<String[]> attemptGetNumUsersInChatrooms() {
    try {
      this.writer.write("getNumUsers@#@");
      this.writer.newLine();
      this.writer.flush();
      String response = this.reader.readLine();
      logger.logger.info("Received number of users in each chatroom");
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
      // use another server
      useAnotherServer();
      return attemptGetNumUsersInChatrooms();
    }
  }

  /**
   * Attempt to join a chat of the given name. Contact the LookUp server with the name of the chatroom
   * and the client's name.
   * @param chatName
   * @return string response corresponding to if the chatroom was joined successfully.
   */
  public String attemptJoinChat(String chatName) {
    try {
      this.writer.write("joinChat@#@" + chatName + "@#@" + username);
      this.writer.newLine();
      this.writer.flush();
      String response = this.reader.readLine();
      String[] responseArray = response.split("@#@");
      if (responseArray[0].equalsIgnoreCase("success")) {
        // if this client was listening to another multicast socket then that should be turned off
        logger.logger.info("Successfully joined new chatroom");
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
          // set up socket to connect to chatroom server to be able to send messages and logout notifications
          connectSocketToChatroomServer();
        }
        this.mostRecentChatroomName = chatName;

        // create a new multicast message receiver to listen to the multicast socket associated with
        // the new chatroom.
        MulticastMessageReceiver multicastMessageReceiver = new MulticastMessageReceiver();
        this.currentMulticastMessageReceiver = multicastMessageReceiver;
        new Thread(multicastMessageReceiver).start();
        return responseArray[0];
      } else { // case where response is "nonexistent"
        return responseArray[0];
      }
    } catch (IOException e) {
      // use another server
      useAnotherServer();
      return attemptJoinChat(chatName);
    }
  }

  /**
   * Attempt to get an arraylist of the usernames of the users in the chatroom given.
   * @param chatName
   * @return an arraylist of the usernames of the users in the chatroom given
   */
  public ArrayList<String> attemptGetUsersInChatroom(String chatName) {
    try {
      this.writer.write("getMembers@#@" + chatName);
      this.writer.newLine();
      this.writer.flush();
      String response = this.reader.readLine();
      logger.logger.info("Received users in current chatroom");
      if (response.equalsIgnoreCase("nonexistent")) {
        return new ArrayList<>();
      }
      String[] responseArray = response.split("@#@");
      ArrayList<String> members = new ArrayList<>();
      for (int i = 1; i < responseArray.length; i++) {
        members.add(responseArray[i]);
      }
      return members;
    } catch (IOException e) {
      // use another server
      useAnotherServer();
      return attemptGetUsersInChatroom(chatName);
    }
  }

  /**
   * Connect to the server socket on the chatroom server.
   */
  public void connectSocketToChatroomServer() {
    try {
      this.socketConnectedToChatroomServer = new Socket(this.chatRoomServerAddress, this.chatroomServerPort);

      BufferedWriter writer = new BufferedWriter(
              new OutputStreamWriter(socketConnectedToChatroomServer.getOutputStream()));
      BufferedReader reader = new BufferedReader(
              new InputStreamReader(socketConnectedToChatroomServer.getInputStream()));
      this.chatRoomServerReader = reader;
      this.chatRoomServerWriter = writer;
      logger.logger.info("Connected to chatroom server socket");
    } catch (IOException e) {
      logger.logger.warning("Could not connect to chatroom server socket");
    }
  }

  /**
   * Driver for client that creates a new client and a new clientGUI and sets the clientGUI attribute
   * in the client.
   * @param args
   */
  public static void main(String[] args) {
    // Create the ClientGUI
    long clientID = new Date().getTime();
    ProgLogger logger = null;
    try {
      logger = new ProgLogger("client_" + clientID + "_log.txt");
    } catch (IOException e) {
      System.out.println("Could not set up logger for client.");
    }
    Client client = new Client(clientID, logger);
    ClientGUI clientGUI = new ClientGUI(client, logger);
    client.setClientGUI(clientGUI);
  }
}
