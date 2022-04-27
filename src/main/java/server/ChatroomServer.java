package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import client.Client;
import gui.ChatroomServerGUI;

/**
 * Chatroom server class that handles all the logic for a chatroom, including receiving messages and
 * notifications from clients in the chatroom and multicasting them out to all members, receiving
 * heartbeats from a LookUp server, and sending any necessary info to the LookUp server to back-up
 * information.
 */
public class ChatroomServer {

  public int ID;
  public String chatroomName;
  public Client hostClient;
  public ChatroomServerGUI chatroomServerGUI;
  public ServerSocket serverSocketForClients;
  public int portForClients;
  public InetAddress group;
  public DatagramSocket datagramSocketForMulticast;
  public byte[] buffer;

  // heartbeat vars for keeping in contact with LookUpServer
  public String heartbeatAddress;
  public int heartbeatPort;
  public Socket heartbeatSocket;
  public BufferedReader heartbeatReader;
  public BufferedWriter heartbeatWriter;

  /**
   * Constructor for chatroom server that initializes state variables, creates a chatroom server GUI,
   * sets up a server socket for clients to connect to, and connects a socket to the LookUp server to
   * receive heartbeats and send information.
   * @param ID
   * @param hostClient
   * @param groupIP
   * @param chatroomName
   * @param heartbeatAddress
   * @param heartbeatPort
   */
  public ChatroomServer(int ID, Client hostClient, String groupIP, String chatroomName,
                        String heartbeatAddress, int heartbeatPort) {
    this.ID = ID;
    this.chatroomName = chatroomName;
    this.hostClient = hostClient;
    // create the GUI that will display the history of all messages
    this.chatroomServerGUI = new ChatroomServerGUI(this);
    this.heartbeatAddress = heartbeatAddress;
    this.heartbeatPort = heartbeatPort;
    // set the group IP that this chatroom server will publish messages to
    try {
      this.group = InetAddress.getByName(groupIP);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    // create a server socket for clients joining chatroom to connect to
    try {
      this.serverSocketForClients = new ServerSocket(0);
      this.portForClients = this.serverSocketForClients.getLocalPort();
      NewUserConnector newUserConnector = new NewUserConnector();
      new Thread(newUserConnector).start();
    } catch (IOException e) {
      e.printStackTrace();
    }
    // connect to LookUp server to receive heartbeats and send any chatroom information
    try {
      this.heartbeatSocket = new Socket(heartbeatAddress, heartbeatPort);
      System.out.println("Connected to lookup for heartbeat");
      HeartbeatHandler heartbeatHandler = new HeartbeatHandler(this.heartbeatSocket);
      new Thread(heartbeatHandler).start();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Class to be executed by a thread that will accept socket connections from clients joining the
   * chatroom. Then it will create a new thread for that client socket.
   */
  public class NewUserConnector implements Runnable {
    @Override
    public void run() {
      while (true) {
        Socket clientSocket = null;
        try {
          clientSocket = serverSocketForClients.accept();
        } catch (IOException e) {
          e.printStackTrace();
        }
        ClientSocketHandler clientSocketHandler = new ClientSocketHandler(clientSocket);
        new Thread(clientSocketHandler).start();
      }
    }
  }

  /**
   * Class to be executed by a thread that will listen for heartbeats from a LookUp server and
   * handle the case that the message is to remove the chatroom server GUI (delete window).
   */
  public class HeartbeatHandler implements Runnable {

    public Socket socket;

    /**
     * Constructor for hearbeat handler which sets the socket that is connected to a LookUp server
     * @param socket
     */
    public HeartbeatHandler(Socket socket) {
      this.socket = socket;
    }

    /**
     * Sets the buffered reader and buffered writer for the socket that is connected to a LookUp
     * server. Then continuously listens to the socket and handles messages. If it is a heartbeat,
     * then ignore it because the LookUp server is only checking that messages successfully get sent,
     * but if the message is "removeGUI" then remove the chatroom server GUI.
     */
    @Override
    public void run() {
      try {
        heartbeatReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        heartbeatWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
      } catch (IOException e) {
        e.printStackTrace();
      }
      while (true) {
        try {
          String line = heartbeatReader.readLine();
          String[] messageArray = line.split("@#@");
          if (messageArray[0].equalsIgnoreCase("heartbeat")) {
            // don't do anything since the LookUp server knows if this message didn't go through
          } else if (messageArray[0].equalsIgnoreCase("removeGUI")) {
            // remove the chatroom server GUI window
            chatroomServerGUI.removeFrame();
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  /**
   * Display on the chatroom server GUI all of the given messages, including the sender username and
   * the message content.
   * @param oldMessages
   */
  public void replenishLogDisplay(String[] oldMessages) {
    for (String fullMessage : oldMessages) {
      String[] messageInfo = fullMessage.split("@#@");
      String sender = messageInfo[0];
      String actualMessage = messageInfo[1];
      chatroomServerGUI.displayNewMessage(sender, actualMessage);
    }
  }

  /**
   * Multicast the given message to the known groupIP so that all member clients of the chatroom
   * receive the message.
   * @param message
   */
  public void multicastMessage(String message) {
    try {
      datagramSocketForMulticast = new DatagramSocket();
      message = "chatkey125" + message;
      buffer = message.getBytes();
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, 4446);
      datagramSocketForMulticast.send(packet);
      datagramSocketForMulticast.close();
    } catch (SocketException se) {
      se.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  /**
   * Class to be executed by a thread that handles messages from a client through a socket.
   */
  private class ClientSocketHandler implements Runnable {
    private final Socket clientSocket;
    private final InetAddress clientAddress;
    private final int clientPort;

    /**
     * Constructor for client socket handler that sets the client socket, client address, and
     * client port attributes.
     * @param socket
     */
    public ClientSocketHandler(Socket socket) {
      this.clientSocket = socket;
      this.clientAddress = this.clientSocket.getInetAddress();
      this.clientPort = this.clientSocket.getPort();
    }

    /**
     * Handle the case of a sender sending a message through the socket by multicasting the message
     * to all members of the chatroom and sending the message to a LookUp server to back up the info.
     * Also display the message on the chatroom server GUI.
     * @param sender
     * @param actualMessage
     */
    private void handleMessage(String sender, String actualMessage) {
      // multicast to all connected clients
      String message = sender + "@#@" + actualMessage;
      multicastMessage(message);
      try {
        System.out.println("In handleMessage sending: " + sender + ": " + actualMessage);
        heartbeatWriter.write("messageSent@#@" + sender + "@#@" + actualMessage);
        heartbeatWriter.newLine();
        heartbeatWriter.flush();
      } catch (IOException e) {
        e.printStackTrace();
      }
      chatroomServerGUI.displayNewMessage(sender, actualMessage);
    }

    /**
     * Handle the case that user with the given username is logging out while in this chatroom. If
     * the leaving user is the host then tell a LookUp server this so it can pick a new host and
     * recreate a chatroom server with that host client. If the leaving user is not the host, then
     * tell a LookUp server so that they can log the user out and remove them from the chatroom.
     * @param leaverUsername
     */
    private void handleChatroomLogout(String leaverUsername) {
      if (leaverUsername.equalsIgnoreCase(hostClient.username)) {
        // case that the leaving user is the host client
        try {
          System.out.println("In handleChatroomLogout for myself the host " + leaverUsername);
          heartbeatWriter.write("hostChatroomLogout" + "@#@" + leaverUsername);
          heartbeatWriter.newLine();
          heartbeatWriter.flush();
        } catch (IOException e) {
          e.printStackTrace();
        }
      } else {
        // case that the leaving user is not the host client
        try {
          System.out.println("In handleChatroomLogout sending: " + leaverUsername);
          heartbeatWriter.write("chatroomLogout@#@" + leaverUsername);
          heartbeatWriter.newLine();
          heartbeatWriter.flush();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    /**
     * Handle the case that a user with the given username is clicking the button to go back to the
     * chat selection screen while they are in this chatroom. If the leaving user is the host then
     * tell a LookUp server this so it can pick a new host and recreate a chatroom server with that
     * host client. If the leaving usr is not the host then tell a LookUp server so that it can
     * remove them from the chatroom.
     * @param leaverUsername
     */
    private void handleBackToChatSelection(String leaverUsername) {
      if (leaverUsername.equalsIgnoreCase(hostClient.username)) {
        // case that the leaving user is the host client
        try {
          System.out.println("In handleBackToChatSelection sending: " + leaverUsername);
          heartbeatWriter.write("hostBackToChatSelection@#@" + leaverUsername);
          heartbeatWriter.newLine();
          heartbeatWriter.flush();
        } catch (IOException e) {
          e.printStackTrace();
        }
      } else {
        // case that the leaving user is not the host client
        try {
          System.out.println("In handleBackToChatSelection sending: " + leaverUsername);
          heartbeatWriter.write("backToChatSelection@#@" + leaverUsername);
          heartbeatWriter.newLine();
          heartbeatWriter.flush();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    /**
     * Set up the buffered reader and buffered writer for a client that has joined chatroom and
     * connected its socket to the chatroom server. Then listen for messages from the client and
     * handle them as necessary with helper handler functions.
     */
    public void run() {
      BufferedReader reader = null;
      BufferedWriter writer = null;
      try {
        reader = new BufferedReader(
                new InputStreamReader(this.clientSocket.getInputStream()));
        writer = new BufferedWriter(
                new OutputStreamWriter(this.clientSocket.getOutputStream()));
      } catch (IOException e) {
        e.printStackTrace();
      }
      // continuously listen to client's socket for messages
      while (true) {
        try {
          String line = reader.readLine();
          // in case of client exiting, close socket.
          if (line == null) {
            this.clientSocket.close();
            break;
          }
          String[] messageArray = line.split("@#@");
          if (messageArray[0].equalsIgnoreCase("message")) {
            handleMessage(messageArray[1], messageArray[2]);
          } else if (messageArray[0].equalsIgnoreCase("chatroomLogout")) {
            handleChatroomLogout(messageArray[1]);
          } else if (messageArray[0].equalsIgnoreCase("backToChatSelection")) {
            handleBackToChatSelection(messageArray[1]);
          } else {
            System.out.println("invalidRequestType in chatroomserver: " + messageArray[0]);
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }
}
