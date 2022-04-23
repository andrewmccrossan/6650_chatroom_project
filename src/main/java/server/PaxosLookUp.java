//package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

//public class PaxosLookUp extends LookUpServer {
//
//  public ConcurrentHashMap<BufferedReader,BufferedWriter> acceptorLookUpServersReadersWriters;
//  public ConcurrentHashMap<BufferedReader,BufferedWriter> proposerLookUpServersReadersWriters;
//  public ConcurrentHashMap<BufferedReader,BufferedWriter> learnerLookUpServersReadersWriters;
//  public ServerSocket serverSocketForOtherPaxosServersToConnectTo;
//  public String addressForOtherPaxosServersToConnectTo;
//  public int portForOtherPaxosServersToConnectTo;
//  public String myPaxosRole;

//  public PaxosLookUp(int port, int serverID, String registryAddress, int registryPort, String myPaxosRole) {
//    super(port, serverID);
//    this.acceptorLookUpServersReadersWriters = new ConcurrentHashMap<>();
//    this.proposerLookUpServersReadersWriters = new ConcurrentHashMap<>();
//    this.learnerLookUpServersReadersWriters = new ConcurrentHashMap<>();
//    this.myPaxosRole = myPaxosRole;
//    createServerSocketForOtherPaxosServersToConnectTo();
//    registerWithRegisterServer(registryAddress, registryPort);
//  }

//  public void createServerSocketForOtherPaxosServersToConnectTo() {
//    try {
//      this.serverSocketForOtherPaxosServersToConnectTo = new ServerSocket(0);
//      this.portForOtherPaxosServersToConnectTo = this.serverSocketForOtherPaxosServersToConnectTo.getLocalPort();
//      this.addressForOtherPaxosServersToConnectTo = this.serverSocketForOtherPaxosServersToConnectTo.getInetAddress().getHostAddress();
//      NewPaxosServerConnector newPaxosServerConnector = new NewPaxosServerConnector();
//      new Thread(newPaxosServerConnector).start();
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
//
//  }

//  private class NewPaxosServerConnector implements Runnable {
//    @Override
//    public void run() {
//      while (true) {
//        try {
//          Socket newPaxosServerSocket = serverSocketForOtherPaxosServersToConnectTo.accept();
//          PaxosSocketMessageReceiver paxosSocketMessageReceiver = new PaxosSocketMessageReceiver(newPaxosServerSocket);
//          new Thread(paxosSocketMessageReceiver).start();
//        } catch (IOException e) {
//          e.printStackTrace();
//        }
//
//      }
//    }
//  }

//  private class PaxosSocketMessageReceiver implements Runnable {
//
//    public Socket socketToAnotherPaxosLookUpServer;
//    public BufferedReader readerToAnotherPaxosLookUpServer;
//    public BufferedWriter writerToAnotherPaxosLookUpServer;
//
//    public PaxosSocketMessageReceiver(Socket socket) {
//      this.socketToAnotherPaxosLookUpServer = socket;
//    }
//
//    @Override
//    public void run() {
//      try {
//        this.readerToAnotherPaxosLookUpServer = new BufferedReader(
//                new InputStreamReader(this.socketToAnotherPaxosLookUpServer.getInputStream()));
//        this.writerToAnotherPaxosLookUpServer = new BufferedWriter(
//                new OutputStreamWriter(this.socketToAnotherPaxosLookUpServer.getOutputStream()));
//
//        while (true) {
//          String line = this.readerToAnotherPaxosLookUpServer.readLine();
//          if (line == null) {
//            // TODO - handle case that connection broke since other LookUpServer failed.
//            System.out.println("A CONNECTION DROPPED BETWEEN LOOKUP SERVERS");
//            break;
//          }
//          String[] messageArray = line.split("@#@");
//          if (messageArray[0].equalsIgnoreCase("tellMyRole")) {
//            String otherServerPaxosRole = messageArray[1];
//            if (otherServerPaxosRole.equalsIgnoreCase("acceptor")) {
//              acceptorLookUpServersReadersWriters.put(readerToAnotherPaxosLookUpServer, writerToAnotherPaxosLookUpServer);
//            } else if (otherServerPaxosRole.equalsIgnoreCase("proposer")) {
//              proposerLookUpServersReadersWriters.put(readerToAnotherPaxosLookUpServer, writerToAnotherPaxosLookUpServer);
//            } else { // learner
//              learnerLookUpServersReadersWriters.put(readerToAnotherPaxosLookUpServer, writerToAnotherPaxosLookUpServer);
//            }
//          } else if (messageArray[0].equalsIgnoreCase("educate")) {
//            // TODO - handle case of educate
//          } else {
//            System.out.println("A message other than tellMyRole was sent.");
//          }
//        }
//      } catch (IOException e) {
//        e.printStackTrace();
//      }
//    }
//  }

//  public void registerWithRegisterServer(String registryAddress, int registryPort) {
//    try {
//      Socket socket = new Socket(registryAddress, registryPort);
//      RegistryServerListener registryServerListener = new RegistryServerListener(socket);
//      new Thread(registryServerListener).start();
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
//  }

//  private class RegistryServerListener implements Runnable {
//    private Socket registrySocket;
//    private BufferedReader registryReader;
//    private BufferedWriter registryWriter;
//
//    public RegistryServerListener(Socket socket) {
//      this.registrySocket = socket;
//    }
//
//    @Override
//    public void run() {
//      try {
//        this.registryReader = new BufferedReader(
//                new InputStreamReader(this.registrySocket.getInputStream()));
//        this.registryWriter = new BufferedWriter(
//                new OutputStreamWriter(this.registrySocket.getOutputStream()));
//        this.registryWriter.write(addressForOtherPaxosServersToConnectTo + "@#@" + portForOtherPaxosServersToConnectTo + "@#@" + myPaxosRole);
//        this.registryWriter.newLine();
//        this.registryWriter.flush();
//      } catch (IOException e) {
//        e.printStackTrace();
//      }
//      while (true) {
//        try {
//          String line = this.registryReader.readLine();
//          String[] messageArray = line.split("@#@");
//          if (messageArray[0].equalsIgnoreCase("startConnection")) {
//            // start thread to connect socket to this new paxosServer
//            String newServerAddress = messageArray[1];
//            int newServerPort = Integer.parseInt(messageArray[2]);
//            String paxosRole = messageArray[3];
//            Socket newServerSocket = new Socket(newServerAddress, newServerPort);
//            InstigateSocketConnectionToOtherPaxosServer instigateSocketConnectionToOtherPaxosServer
//                    = new InstigateSocketConnectionToOtherPaxosServer(newServerSocket, paxosRole);
//            new Thread(instigateSocketConnectionToOtherPaxosServer).start();
//          }
//        } catch (IOException e) {
//          e.printStackTrace();
//        }
//      }
//    }
//  }

//  private class InstigateSocketConnectionToOtherPaxosServer implements Runnable {
//
//    private Socket otherPaxosServerSocket;
//    private String paxosRole;
//    private BufferedReader readerForOtherPaxosServerSocket;
//    private BufferedWriter writerForOtherPaxosServerSocket;
//
//    public InstigateSocketConnectionToOtherPaxosServer(Socket newServerSocket, String paxosRole) {
//      this.otherPaxosServerSocket = newServerSocket;
//      this.paxosRole = paxosRole;
//      try {
//        this.readerForOtherPaxosServerSocket = new BufferedReader(
//                new InputStreamReader(this.otherPaxosServerSocket.getInputStream()));
//        this.writerForOtherPaxosServerSocket = new BufferedWriter(
//                new OutputStreamWriter(this.otherPaxosServerSocket.getOutputStream()));
//        if (this.paxosRole.equalsIgnoreCase("acceptor")) {
//          acceptorLookUpServersReadersWriters.put(this.readerForOtherPaxosServerSocket, this.writerForOtherPaxosServerSocket);
//        } else if (this.paxosRole.equalsIgnoreCase("proposer")) {
//          proposerLookUpServersReadersWriters.put(this.readerForOtherPaxosServerSocket, this.writerForOtherPaxosServerSocket);
//        } else { // learner
//          learnerLookUpServersReadersWriters.put(this.readerForOtherPaxosServerSocket, this.writerForOtherPaxosServerSocket);
//        }
//      } catch (IOException e) {
//        e.printStackTrace();
//      }
//    }
//
//    @Override
//    public void run() {
//      try {
//        this.writerForOtherPaxosServerSocket.write("tellMyRole@#@" + myPaxosRole);
//        this.writerForOtherPaxosServerSocket.newLine();
//        this.writerForOtherPaxosServerSocket.flush();
//      } catch (IOException e) {
//        e.printStackTrace();
//      }
//    }
//  }

//  public void educate(long proposalNum, Transaction transaction) {
//    doTransaction(transaction);
//  }


//}
