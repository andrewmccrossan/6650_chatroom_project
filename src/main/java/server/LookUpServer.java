package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LookUpServer {

  public ServerSocket serverSocket;
  public ConcurrentHashMap<String,String> usernamePasswordStore;
  public ConcurrentHashMap<String,Integer> usernamePortStore;
  public ConcurrentHashMap<String,ChatroomInfo> chatNameChatroomInfoStore;
  public int nextChatroomID = 0;
  public String groupIPPrefix = "239.0.0."; // we will use multicast IPs in range 239.0.0.0-239.0.0.255
  public int nextGroupIPLastDigit = 0;
//  public ConcurrentHashMap<Integer,InstigateConnectionToOtherLookUpServerHandler> otherLookUpServerSocketConnectionHandlers;
//  public ConcurrentHashMap<Integer,Socket> otherLookUpServerSockets;

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
        int newGroupIPIndex = nextGroupIPLastDigit;
//        newChatroomInfo.setGroupIP(groupIPs[newGroupIPIndex]);
        newChatroomInfo.setGroupIP(groupIPPrefix + newGroupIPIndex);
        nextGroupIPLastDigit++;
        newChatroomInfo.setInetAddress(this.clientAddress);
        newChatroomInfo.putMember(username);
        chatNameChatroomInfoStore.put(chatName, newChatroomInfo);
        System.out.println("Created chatroom with name " + chatName + " hosted by " + username);
        return "success " + newID + " " + groupIPPrefix + newGroupIPIndex;
      }
    }

    private String handleUpdateChatConnectionPort(String[] updatePortMessage) {
      int newPort = Integer.parseInt(updatePortMessage[1]);
      String chatroomName = updatePortMessage[2];
      ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(chatroomName);
      chatroomInfo.setPort(newPort);
      return "success";
    }

    private String handleJoinChat(String[] chatRequest) {
      String chatName = chatRequest[1];
      String username = chatRequest[2];
      if (chatNameChatroomInfoStore.containsKey(chatName)) {
        ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(chatName);
        String address = chatroomInfo.inetAddress.getHostAddress();
        int port = chatroomInfo.port;
        String groupIP = chatroomInfo.groupIP;
        chatroomInfo.putMember(username);
        System.out.println(username + " joined chatroom called " + chatName);
        return "success " + address + " " + port + " " + groupIP;
      } else {
        return "nonexistent";
      }
    }

    private String handleGetUsersInChatroom(String[] usersRequest) {
      String chatName = usersRequest[1];
      if (chatNameChatroomInfoStore.containsKey(chatName)) {
        ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(chatName);
        ArrayList<String> members = chatroomInfo.members;
        StringBuilder membersString = new StringBuilder("START");
        for (String member : members) {
          membersString.append("@#@").append(member);
        }
        return membersString.toString();
      } else {
        return "nonexistent";
      }
    }

    private String handleGetNumUsersInChatrooms() {
      StringBuilder response = new StringBuilder();
      for (Map.Entry nameInfoPair : chatNameChatroomInfoStore.entrySet()) {
        String name = (String) nameInfoPair.getKey();
        ChatroomInfo info = (ChatroomInfo) nameInfoPair.getValue();
        response.append(name).append("%&%").append(info.members.size()).append("@&@");
      }
      return response.toString();
    }

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
      while (true) {
        try {
          String line = reader.readLine();
          if (line == null) {
            // This is the case that the client disconnected completely
            // TODO - handle the situation from LookUpServer's point of view when a client process quits
            // TODO - although, maybe the ChatroomServer should just handle this entirely by contacting the LookUpServer
            this.clientSocket.close();
            break;
          }
          String[] messageArray = line.split(" ");
          String response = null;
          if (messageArray[0].equalsIgnoreCase("login")) {
            response = handleLogin(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("register")) {
            response = handleRegister(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("createChat")) {
            response = handleCreateChat(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("updateChatConnectionPort")) {
            response = handleUpdateChatConnectionPort(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("joinChat")) {
            response = handleJoinChat(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("getMembers")) {
            response = handleGetUsersInChatroom(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("getNumUsers")) {
            response = handleGetNumUsersInChatrooms();
          } else {
            response = "invalidRequestType";
          }
          writer.write(response);
          writer.newLine();
          writer.flush();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public void doTransaction(Transaction transaction) {
    // TODO - there will be a lot of logic here to parse a transaction that will change...
    // TODO - ...the state of the server. This is because info is passed through sockets...
    // TODO - ...as strings.

  }

//  public void instigateConnectionToOtherLookUpServer(InetAddress otherAddress, int otherPort, String paxosRole, int otherID) {
//    InstigateConnectionToOtherLookUpServerHandler instigateConnectionToOtherLookUpServerHandler = new InstigateConnectionToOtherLookUpServerHandler(otherAddress, otherPort, paxosRole, otherID);
//    this.otherLookUpServerSocketConnectionHandlers.put(otherID, instigateConnectionToOtherLookUpServerHandler);
//    new Thread(instigateConnectionToOtherLookUpServerHandler).start();
//  }
//
//  private class InstigateConnectionToOtherLookUpServerHandler implements Runnable {
//    private Socket paxosSocket;
//    private final InetAddress socketAddress;
//    private final int paxosPort;
//    private BufferedReader paxosReader;
//    private BufferedWriter paxosWriter;
//    private final String paxosRole;
//    private final int otherServerID;
//
//    public InstigateConnectionToOtherLookUpServerHandler(InetAddress otherAddress, int otherPort, String paxosRole, int otherID) {
//      this.socketAddress = otherAddress;
//      this.paxosPort = otherPort;
//      this.paxosRole = paxosRole;
//      this.otherServerID = otherID;
//    }
//
//    public void run() {
//      Socket socket = null;
//      BufferedReader reader = null;
//      BufferedWriter writer = null;
//      try {
//        socket = new Socket(this.socketAddress, this.paxosPort);
////        socket = new Socket("localhost", 10000);
//        writer = new BufferedWriter(
//                new OutputStreamWriter(socket.getOutputStream()));
//        reader = new BufferedReader(
//                new InputStreamReader(socket.getInputStream()));
//      } catch (IOException e) {
//        e.printStackTrace();
//      }
//      this.paxosSocket = socket;
//      this.paxosReader = reader;
//      this.paxosWriter = writer;
//
//      while (true) {
//
//      }
//    }
//
//    public void sendPaxosMessage(String message) {
//
//    }
//
//    private void startListening() {
//      new Thread(new Runnable() {
//        @Override
//        public void run() {
//          String line = null;
//          try {
//            line = paxosReader.readLine();
//            String[] messageArray = line.split(" ");
//            System.out.println("  ----- Recieved New Message -----\n" + line);
//            // parse line and handle logic
//            if (messageArray[0].equalsIgnoreCase("prepare")) {
//              System.out.println("Prepare request received");
//            } else if (messageArray[0].equalsIgnoreCase("accept")) {
//              System.out.println("Accept request received");
//            } else if (messageArray[0].equalsIgnoreCase("promise")) {
//              System.out.println("Promise received");
//            }
//          } catch (IOException e) {
//            e.printStackTrace();
//          }
//        }
//      }).start();
//    }
//  }

  public static void main(String[] args) {
    int port0 = 10000;
    int port1 = 49152;
    //TODO - registry address and port hardcoded as well
    if (args.length == 1) {
      try {
        port0 = Integer.parseInt(args[0]);
      } catch (Exception e) {
      }
    }
    System.out.println("Server is running...");
    LookUpServer lookUpServer0 = new LookUpServer(port0, 0);
//    PaxosLookUp paxosLookUpServer0 = new PaxosLookUp(port1, 1);
//    LookUpServer lookUpServer1 = new LookUpServer(49152, 1);
//    LookUpServer lookUpServer2 = new LookUpServer(49153, 2);
//    LookUpServer lookUpServer3 = new LookUpServer(49154, 3);
//    LookUpServer lookUpServer4 = new LookUpServer(49155, 4);
  }
}
