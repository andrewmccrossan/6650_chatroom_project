package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class LookUpServer {

  public ServerSocket serverSocket;
  public ConcurrentHashMap<String,String> usernamePasswordStore;
  public ConcurrentHashMap<String,Integer> usernamePortStore;
  public ConcurrentHashMap<String,ChatroomInfo> chatNameChatroomInfoStore;
  public int nextChatroomID = 0;
  public String[] groupIPs = new String[]{ "239.0.0.0", "239.0.0.1", "239.0.0.2", "239.0.0.3",
          "239.0.0.4", "239.0.0.5", "239.0.0.6", "239.0.0.7", "239.0.0.8", "239.0.0.9" };
  public int nextGroupIPIndex = 0;

  public LookUpServer(int port, long serverID) {
    try {
      this.serverSocket = new ServerSocket(port);
      this.usernamePasswordStore = new ConcurrentHashMap<>();
      this.usernamePasswordStore.put("admin", "password");
      this.usernamePortStore = new ConcurrentHashMap<>();
      this.chatNameChatroomInfoStore = new ConcurrentHashMap<>();
      waitForLogin();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void waitForLogin() throws IOException {
    while (true) {
      Socket clientSocket = this.serverSocket.accept();
//      socket.setSoTimeout(999000);
      ClientSocketHandler clientSocketHandler = new ClientSocketHandler(clientSocket);
      new Thread(clientSocketHandler).start();
    }
  }

  private class ClientSocketHandler implements Runnable {
    private final Socket clientSocket;
    private final InetAddress clientAddress;
    private final int clientPort;

    public ClientSocketHandler(Socket socket) {
      this.clientSocket = socket;
      this.clientAddress = this.clientSocket.getInetAddress();
      this.clientPort = this.clientSocket.getPort();
    }

    private String handleLogin(String[] accountInfo) {
      String username = accountInfo[1];
      String password = accountInfo[2];
      if (usernamePasswordStore.get(username) != null && usernamePasswordStore.get(username).equalsIgnoreCase(password)) {
        return "success";
      } else {
        return "failure";
      }
    }

    private String handleRegister(String[] accountInfo) {
      String username = accountInfo[1];
      String password = accountInfo[2];
      if (usernamePasswordStore.containsKey(username)) {
        return "exists";
      } else {
        usernamePasswordStore.put(username, password);
        return "success";
      }
    }

    private String handleCreateChat(String[] chatRequest) {
      String chatName = chatRequest[1];
      String username = chatRequest[2];
      if (chatNameChatroomInfoStore.containsKey(chatName)) {
        return "exists";
      } else {
        ChatroomInfo newChatroomInfo = new ChatroomInfo();
        int newID = nextChatroomID;
        newChatroomInfo.setID(newID);
        nextChatroomID++;
        newChatroomInfo.setHostUsername(username);
        newChatroomInfo.setName(chatName);
        newChatroomInfo.setPort(this.clientPort);
        int newGroupIPIndex = nextGroupIPIndex;
        newChatroomInfo.setGroupIP(groupIPs[newGroupIPIndex]); // TODO - only woks for first 10
        nextGroupIPIndex++;
        newChatroomInfo.setInetAddress(this.clientAddress);
        newChatroomInfo.putMember(username);
        chatNameChatroomInfoStore.put(chatName, newChatroomInfo);
        System.out.println("Created chatroom with name " + chatName + " hosted by " + username);
        return "success " + newID + " " + groupIPs[newGroupIPIndex];
      }
    }

    private String handleJoinChat(String[] chatRequest) {
      String chatName = chatRequest[1];
      String username = chatRequest[2];
      if (chatNameChatroomInfoStore.containsKey(chatName)) {
        ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(chatName);
        String address = chatroomInfo.inetAddress.getHostAddress();
        int port = chatroomInfo.port;
        chatroomInfo.putMember(username);
        System.out.println(username + " joined chatroom called " + chatName);
        return "success " + address + " " + port;
      } else {
        return "nonexistent";
      }
    }

    public void run() {
      System.out.println("New thread created...");
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
      while (true) {
        try {
          String line = reader.readLine();
          String[] messageArray = line.split(" ");
          String response = null;
          if (messageArray[0].equalsIgnoreCase("login")) {
            response = handleLogin(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("register")) {
            response = handleRegister(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("createChat")) {
            response = handleCreateChat(messageArray);
          } else {
            response = "invalidRequestType";
          }
          writer.write(response);
          writer.newLine();
          writer.flush();
          System.out.println("Sent response back to client with response " + response);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static void main(String[] args) {
    int port = 10000;
    if (args.length == 1) {
      try {
        port = Integer.parseInt(args[0]);
      } catch (Exception e) {
      }
    }
    System.out.println("Server is running...");
    LookUpServer lookUpServer0 = new LookUpServer(port, 0);
//    LookUpServer lookUpServer1 = new LookUpServer(49152, 1);
//    LookUpServer lookUpServer2 = new LookUpServer(49153, 2);
//    LookUpServer lookUpServer3 = new LookUpServer(49154, 3);
//    LookUpServer lookUpServer4 = new LookUpServer(49155, 4);
  }
}
