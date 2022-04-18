package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class LookUpServer {

  public ServerSocket serverSocket;
  public ConcurrentHashMap<String,String> usernamePasswordStore;

  public LookUpServer(int port, long serverID) {
    try {
      this.serverSocket = new ServerSocket(port);
      this.usernamePasswordStore = new ConcurrentHashMap<>();
      this.usernamePasswordStore.put("admin", "password");
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

    public ClientSocketHandler(Socket socket) {
      this.clientSocket = socket;
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
