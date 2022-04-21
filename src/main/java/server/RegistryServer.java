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

public class RegistryServer {

  ServerSocket serverSocket;
  public ArrayList<PaxosServerInfo> paxosServerInfos;

  public RegistryServer(int port) {
    try {
      this.paxosServerInfos = new ArrayList<>();
      this.serverSocket = new ServerSocket(port);
      waitForNewServers();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void waitForNewServers() throws IOException {
    while (true) {
      Socket newServerSocket = this.serverSocket.accept();
      NewServerSocketHandler newServerSocketHandler = new NewServerSocketHandler(newServerSocket);
      new Thread(newServerSocketHandler).start();
    }
  }

  private class NewServerSocketHandler implements Runnable {
    private final Socket newServerSocket;
    private String newServerAddress;
    private int newServerPort;
    private String newServerPaxosRole;
    private BufferedReader newServerReader;
    private BufferedWriter newServerWriter;

    public NewServerSocketHandler(Socket newServerSocket) {
      this.newServerSocket = newServerSocket;
    }

    public void run() {
      try {
        this.newServerReader = new BufferedReader(
                new InputStreamReader(this.newServerSocket.getInputStream()));
        this.newServerWriter = new BufferedWriter(
                new OutputStreamWriter(this.newServerSocket.getOutputStream()));
        PaxosServerInfo newPaxosServerInfo = new PaxosServerInfo(this.newServerAddress,
                this.newServerPort, this.newServerSocket, this.newServerWriter);
        paxosServerInfos.add(newPaxosServerInfo);
        System.out.println("Registry server accepted new server connection");
        String line = this.newServerReader.readLine();
        String[] messageArray = line.split("@#@");
        this.newServerAddress = messageArray[0];
        this.newServerPort = Integer.parseInt(messageArray[1]);
        this.newServerPaxosRole = messageArray[2];
        System.out.println("in registry receiving connection with paxosRole: " + this.newServerPaxosRole);
        // Tell each server that has already connected to make a socket connection to the new server
        for (PaxosServerInfo paxosServerInfo : paxosServerInfos) {
          paxosServerInfo.writer.write("startConnection@#@" + this.newServerAddress + "@#@" + this.newServerPort + "@#@" + this.newServerPaxosRole);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public class PaxosServerInfo {
    public Socket registryPaxosServer;
    public String addressForPaxosServers;
    public int portForPaxosServers;
    public BufferedWriter writer;

    public PaxosServerInfo(String addressForPaxosServers, int portForPaxosServers, Socket registryPaxosServer,
                       BufferedWriter writer) {
      this.addressForPaxosServers = addressForPaxosServers;
      this.portForPaxosServers = portForPaxosServers;
      this.registryPaxosServer = registryPaxosServer;
      this.writer = writer;
    }
  }
}
