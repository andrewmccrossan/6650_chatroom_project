package client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.Socket;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.Date;

import gui.LoginGUI;

public class Client {

  long clientID;
  LoginGUI loginGUI;
  int[] serverPorts;
  Socket socket;
  BufferedReader reader;
  BufferedWriter writer;


  public Client(long clientID) {
    this.clientID = clientID;
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

  public void setLoginGUI(LoginGUI loginGUI) {
    this.loginGUI = loginGUI;
  }

  public String contactServer(String message) {
    return null;
  }

  public String attemptLogin(String username, String password) {
    try {
      this.writer.write("login " + username + " " + password);
      this.writer.newLine();
      this.writer.flush();
      String response = this.reader.readLine();
      return response;
//      if (response.equalsIgnoreCase("success")) {
//        return "SUCCESSFUL LOGIN";
//      } else if (response.equalsIgnoreCase("failure")) {
//        return "FAILED LOGIN";
//      } else {
//        return "unrecognized login response";
//      }
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public String attemptRegister(String username, String password) {
    try {
      this.writer.write("register " + username + " " + password);
      this.writer.newLine();
      this.writer.flush();
      String response = this.reader.readLine();
      return response;
//      if (response.equalsIgnoreCase("success")) {
//        return "SUCCESSFUL REGISTER";
//      } else if (response.equalsIgnoreCase("exists")) {
//        return "EXISTING REGISTER";
//      } else {
//        return "unrecognized register response";
//      }
    } catch (IOException e) {
      e.printStackTrace();
      return null;
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

    // Create the loginGUI
    long clientID = new Date().getTime();
    Client client = new Client(clientID);
    LoginGUI loginGUI = new LoginGUI(client);
    client.setLoginGUI(loginGUI);
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
