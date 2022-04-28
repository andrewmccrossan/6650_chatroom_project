package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import logger.ProgLogger;

/**
 * Registry server class that orchestrates connecting all of the LookUp servers by sockets. The
 * registry server listens for LookUp servers to connect to it, and then tells all of the already
 * connected LookUp servers to instigate connecting sockets with the new LookUp server. The new
 * LookUp server tells the registry server what address and port its ServerSocket is waiting to accept
 * connections on, and this info is what the registry server gives to the old LookUp servers for them to
 * connect to the new LookUp server.
 */
public class RegistryServer {

  ProgLogger registryLogger;
  ServerSocket serverSocket;
  public List paxosServerInfos;
  public int myPort;
  public String myAddress;

  /**
   * Constructor for registry server that creates an arraylist of LookUp server infos (paxos servers),
   * creates a server socket for new LookUp servers to connect to, and creates a thread to listen for
   * new LookUp servers connecting to this server socket.
   */
  public RegistryServer() {
    try {
      this.paxosServerInfos = Collections.synchronizedList(new ArrayList());
      this.registryLogger = new ProgLogger("registryServer_log.txt");
      // port for ServerSocket is dynamically allocated.
      this.serverSocket = new ServerSocket(0);
      this.myPort = this.serverSocket.getLocalPort();
      this.myAddress = this.serverSocket.getInetAddress().getHostAddress();
      new Thread(new NewServerWaiter()).start();
      registryLogger.logger.info("Set up server socket for LookUp servers to connect to");
    } catch (IOException e) {
      registryLogger.logger.warning("Could not set up server socket for LookUp servers to connect to");
    }
  }

  /**
   * Class that is to be executed by a thread and is used for waiting on LookUp servers to connect
   * to the registry server's serverSocket. Then, a new thread is created to listen for and handle
   * messages coming into the new socket.
   */
  private class NewServerWaiter implements Runnable {
    @Override
    public void run() {
      while (true) {
        Socket newServerSocket = null;
        try {
          newServerSocket = serverSocket.accept();
          registryLogger.logger.info("New LookUp server socket connection accepted");
          NewServerSocketHandler newServerSocketHandler = new NewServerSocketHandler(newServerSocket);
          new Thread(newServerSocketHandler).start();
        } catch (IOException e) {
          registryLogger.logger.warning("Could not accept LookUp server socket connection");
        }
      }
    }
  }

  /**
   * Get the address that the ServerSocket for this registry server is waiting for connections on.
   * @return the address that the ServerSocket is waiting on
   */
  public String getRegistryAddress() {
    return this.myAddress;
  }

  /**
   * Get the port that the ServerSocket for this registry server is waiting for connections on.
   * @return the port that the ServerSocket is waiting on
   */
  public int getRegistryPort() {
    return this.myPort;
  }

  /**
   * Class that is to be executed by a thread and is used for listening to a socket connected to
   * a specific LookUp server and handling the messages sent to it, which are the address/port that
   * the new LookUp server are waiting for other LookUp servers to connect with it on and the Paxos
   * role of the new LookUp server (proposer, acceptor, or learner).
   */
  private class NewServerSocketHandler implements Runnable {
    private final Socket newServerSocket;
    private String newServerAddress;
    private int newServerPort;
    private String newServerPaxosRole;
    private BufferedReader newServerReader;
    private BufferedWriter newServerWriter;

    /**
     * Constructor for executable class that handles new LookUp servers connecting to the registry
     * server by a socket. Constructor initializes server socket to given server socket.
     * @param newServerSocket
     */
    public NewServerSocketHandler(Socket newServerSocket) {
      this.newServerSocket = newServerSocket;
    }

    /**
     * Execute the class in a thread which reads in the address/port of the new LookUp server's
     * serverSocket and reads in the Paxos role of the new LookUp server. Then, it tells each of the
     * LookUp servers that have already connected to the registry server to instigate connecting a socket
     * to this new LookUp server.
     */
    public void run() {
      try {
        this.newServerReader = new BufferedReader(
                new InputStreamReader(this.newServerSocket.getInputStream()));
        this.newServerWriter = new BufferedWriter(
                new OutputStreamWriter(this.newServerSocket.getOutputStream()));
//        PaxosServerInfo newPaxosServerInfo = new PaxosServerInfo(this.newServerAddress,
//                this.newServerPort, this.newServerSocket, this.newServerWriter);
        String line = this.newServerReader.readLine();
        String[] messageArray = line.split("@#@");
        this.newServerAddress = messageArray[0];
        this.newServerPort = Integer.parseInt(messageArray[1]);
        this.newServerPaxosRole = messageArray[2];
        PaxosServerInfo newPaxosServerInfo = new PaxosServerInfo(this.newServerAddress,
                this.newServerPort, this.newServerSocket, this.newServerWriter);
        // Tell each server that has already connected to make a socket connection to the new server
        synchronized (paxosServerInfos) {
          Iterator i = paxosServerInfos.iterator();
          while (i.hasNext()) {
            PaxosServerInfo info = (PaxosServerInfo) i.next();
            info.writer.write("startConnection@#@" + this.newServerAddress + "@#@"
                    + this.newServerPort + "@#@" + this.newServerPaxosRole);
            info.writer.newLine();
            info.writer.flush();
          }
        }
        paxosServerInfos.add(newPaxosServerInfo);
        registryLogger.logger.info("Instructed old LookUp servers to instigate socket connection to new LookUp server");
      } catch (IOException e) {
        registryLogger.logger.warning("Could not receive address/port/role info from new LookUp" +
                " server and relay to old LookUp servers");
      }
    }
  }

  /**
   * Small class that holds the information for LookUp servers (Paxos servers) that have already
   * connected to the LookUp server. This includes their connected socket, their address/port for
   * other LookUp servers to connect to, and the bufferedWriter for the LookUp server.
   */
  public class PaxosServerInfo {
    public Socket registryPaxosServer;
    public String addressForPaxosServers;
    public int portForPaxosServers;
    public BufferedWriter writer;

    /**
     * Constructor for the Paxos Server Info object that sets the connected socket for the LookUp
     * server, their address/port for other LookUp servers to connect to, and the bufferedWriter
     * for the LookUp server.
     * @param addressForPaxosServers
     * @param portForPaxosServers
     * @param registryPaxosServer
     * @param writer
     */
    public PaxosServerInfo(String addressForPaxosServers, int portForPaxosServers, Socket registryPaxosServer,
                       BufferedWriter writer) {
      this.addressForPaxosServers = addressForPaxosServers;
      this.portForPaxosServers = portForPaxosServers;
      this.registryPaxosServer = registryPaxosServer;
      this.writer = writer;
      registryLogger.logger.info("Created new PaxosServerInfo for LookUp server with socket" +
              " at address " + addressForPaxosServers + " and port " + portForPaxosServers);
    }
  }
}
