package server;

import java.awt.event.ActionListener;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.*;

/**
 * LookUp server class that has several purposes: 1) Accepts socket connections from clients joining
 * application so that information can be sent back and forth. 2) Connects by sockets to all other
 * LookUp servers so that paxos algorithms can be carried out in order to keep all LookUp severs
 * up-to-date with the same state in case a proposer LookUp server fails and another one must be
 * used. Part of this set up process is contacting the Registry server which handles instructing
 * LookUp servers to connect by socket with new LookUp servers. 3) Acts as one of the paxos roles
 * (proposer, acceptor, or learner), but also always acts as a learner so that it receives all
 * transactions that must occur. 4) Connects to all chatroom servers with the purpose of a) sending
 * heartbeats to make sure chatroom is still alive and handling the case that it is not b) receiving
 * information from chatroom server about messages sent in chatroom and who has left the chatroom.
 */
public class LookUpServer {

  public int myServerID;
  public ServerSocket serverSocket;
  public ConcurrentHashMap<String,String> usernamePasswordStore;
  public ConcurrentHashMap<String,Integer> usernamePortStore;
  public ConcurrentHashMap<String,ChatroomInfo> chatNameChatroomInfoStore;
  public ConcurrentHashMap<String,String> loggedInUsersAndPasswords;
  public int nextChatroomID = 0;
  public String groupIPPrefix = "239.0.0."; // we will use multicast IPs in range 239.0.0.0-239.0.0.255
  public int nextGroupIPLastDigit = 0;
  public ConcurrentHashMap<BufferedReader,BufferedWriter> acceptorLookUpServersReadersWriters = new ConcurrentHashMap<>();
  public ConcurrentHashMap<BufferedReader,BufferedWriter> proposerLookUpServersReadersWriters = new ConcurrentHashMap<>();
  public ConcurrentHashMap<BufferedReader,BufferedWriter> learnerLookUpServersReadersWriters = new ConcurrentHashMap<>();
  public ServerSocket serverSocketForOtherPaxosServersToConnectTo;
  public String addressForOtherPaxosServersToConnectTo;
  public int portForOtherPaxosServersToConnectTo;
  public String myPaxosRole;
  public ConcurrentHashMap<String,BufferedWriter> usernameToSocketWriters;

  // chatroom and heartbeat vars
  public ConcurrentHashMap<String,ChatroomInfo> hostUsernameToChatroomInfos;
  public ConcurrentHashMap<String,Timer> hostUsernameToHearbeatTimer;

  // Acceptor variables
  public long maxPromisedProposalNumber = -1;
  public long maxAcceptedProposalNumber = -1;
  public String maxAcceptedProposalTransaction = null;
  public int numPromisedAcceptors = 0;

  // proposer variables
  public long largestPromiseNum = -1;
  public String largestPromiseTransaction = null;
  public int numAcceptedAcceptors = 0;
  public boolean reachedMajorityPromises = false;
  public boolean reachedMajorityAcceptances = false;

  /**
   * Constructor for the LookUp server class which initializes several attributes. It then does the
   * following: 1) Start a thread for accepting client socket connections from new clients joining the
   * application. 2) create a server socket that accepts connections from other LookUp servers so that
   * all LookUp servers can contact one another for the paxos algorithm as replicated servers. 3) contact
   * registry server to tell registry server the address/port for its server socket as well as its
   * paxos role so that the registry server can instruct other LookUp servers to connect a socket to
   * this server socket.
   * @param port
   * @param serverID
   * @param registryAddress
   * @param registryPort
   * @param myPaxosRole
   */
  public LookUpServer(int port, int serverID, String registryAddress, int registryPort, String myPaxosRole) {
    try {
      this.myServerID = serverID;
      this.serverSocket = new ServerSocket(port);
      this.loggedInUsersAndPasswords = new ConcurrentHashMap<>();
      this.usernamePasswordStore = new ConcurrentHashMap<>();
      this.usernamePasswordStore.put("admin", "password");
      this.usernamePortStore = new ConcurrentHashMap<>();
      this.chatNameChatroomInfoStore = new ConcurrentHashMap<>();
      this.hostUsernameToChatroomInfos = new ConcurrentHashMap<>();
      this.usernameToSocketWriters = new ConcurrentHashMap<>();
      this.hostUsernameToHearbeatTimer = new ConcurrentHashMap<>();
      new Thread(new LoginWaiter()).start();
      this.myPaxosRole = myPaxosRole;
      createServerSocketForOtherPaxosServersToConnectTo();
      registerWithRegisterServer(registryAddress, registryPort);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Create a server socket for other LookUp servers to connect to so they can carry out the paxos
   * algorithm to agree on transactions to keep replicated servers up to date.
   */
  public void createServerSocketForOtherPaxosServersToConnectTo() {
    try {
      // passing the argument 0 allows server socket to dynamically find an open port
      this.serverSocketForOtherPaxosServersToConnectTo = new ServerSocket(0);
      this.portForOtherPaxosServersToConnectTo = this.serverSocketForOtherPaxosServersToConnectTo.getLocalPort();
      this.addressForOtherPaxosServersToConnectTo = this.serverSocketForOtherPaxosServersToConnectTo.getInetAddress().getHostAddress();
      NewPaxosServerConnector newPaxosServerConnector = new NewPaxosServerConnector();
      new Thread(newPaxosServerConnector).start();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Class that can be executed by a thread, which accepts socket connections from other LookUp
   * servers and then starts a new thread to listen for new messages and then handles them as
   * necessary. Messages received are related to carrying out the paxos algorithm.
   */
  private class NewPaxosServerConnector implements Runnable {
    @Override
    public void run() {
      while (true) {
        try {
          Socket newPaxosServerSocket = serverSocketForOtherPaxosServersToConnectTo.accept();
          PaxosSocketMessageReceiver paxosSocketMessageReceiver = new PaxosSocketMessageReceiver(newPaxosServerSocket, null, null);
          new Thread(paxosSocketMessageReceiver).start();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  /**
   * Prepare for the next paxos round by resetting paxos-related state variables. This was the
   * approved method by professor in piazza for how to prepare a new round of paxos.
   */
  public void prepareForNextPaxosRound() {
    this.maxPromisedProposalNumber = -1;
    this.maxAcceptedProposalNumber = -1;
    this.maxAcceptedProposalTransaction = null;
    this.numPromisedAcceptors = 0;
    this.largestPromiseNum = -1;
    this.largestPromiseTransaction = null;
    this.numAcceptedAcceptors = 0;
    this.reachedMajorityPromises = false;
    this.reachedMajorityAcceptances = false;
  }

  /**
   * Carry out a login transaction by logging in the given user with their username and password.
   * @param transactionInfo
   */
  public void doLoginTransaction(String[] transactionInfo) {
    String username = cleanString(transactionInfo[1]);
    String password = cleanString(transactionInfo[2]);
    loggedInUsersAndPasswords.put(username, password);
  }

  /**
   * Carry out a logout transaction by logging out the given user based on their username.
   * @param transactionInfo
   */
  public void doLogoutTransaction(String[] transactionInfo) {
    String username = cleanString(transactionInfo[1]);
    loggedInUsersAndPasswords.remove(username);
  }

  /**
   * Carry out a register transaction by registering the given user with their username and password
   * and logging them in.
   * @param transactionInfo
   */
  public void doRegisterTransaction(String[] transactionInfo) {
    String username = cleanString(transactionInfo[1]);
    String password = cleanString(transactionInfo[2]);
    usernamePasswordStore.put(username, password);
    loggedInUsersAndPasswords.put(username, password);
  }

  /**
   * Clean given string by removing unnecessary paxos-related keywords.
   * @param givenString
   * @return cleaned string
   */
  public String cleanString(String givenString) {
    givenString = givenString.replace("educate", "");
    givenString = givenString.replace("accept", "");
    givenString = givenString.replace("propose", "");
    givenString = givenString.replace("promise", "");
    givenString = givenString.replace("acceptResponse", "");
    return givenString;
  }

  /**
   * Carry out a create chat transaction by creating a new chatroomInfo objet and setting the ID,
   * host username, chat name, groupIP, address for new members to connect to chatroom server, and
   * adding the host user as a member. Then add the chatroomInfo object to collection of chatroomInfos.
   * @param transactionInfo
   */
  public void doCreateChatTransaction(String[] transactionInfo) {
    String chatName = cleanString(transactionInfo[1]);
    String username = cleanString(transactionInfo[2]);
    InetAddress address = null;
    try {
      String hostname = cleanString(transactionInfo[3]);
      address = InetAddress.getByName(hostname);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    ChatroomInfo newChatroomInfo = new ChatroomInfo();
    int newID = nextChatroomID;
    newChatroomInfo.setID(newID);
    nextChatroomID++;
    newChatroomInfo.setHostUsername(username);
    newChatroomInfo.setName(chatName);
    int newGroupIPIndex = nextGroupIPLastDigit;
    newChatroomInfo.setGroupIP(groupIPPrefix + newGroupIPIndex);
    nextGroupIPLastDigit++;
    newChatroomInfo.setInetAddress(address);
    newChatroomInfo.putMember(username);
    chatNameChatroomInfoStore.put(chatName, newChatroomInfo);
  }

  /**
   * Carry out update chat conenction port transaction by setting the port of the chatroomInfo object
   * associated with the given chat name. This is the port that a new member connects by socket to
   * the chatroom server with.
   * @param transactionInfo
   */
  public void doUpdateChatConnectionPortTransaction(String[] transactionInfo) {
    int newPort = Integer.parseInt(cleanString(transactionInfo[1]));
    String chatroomName = cleanString(transactionInfo[2]);
    ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(chatroomName);
    if (chatroomInfo != null) {
      chatroomInfo.setPort(newPort);
    }
  }

  /**
   * Carry out a join chat transaction by putting a user of the given username into the ordered list of
   * members belonging to the chatroomInfo associated with the given chat name.
   * @param transactionInfo
   */
  public void doJoinChatTransaction(String[] transactionInfo) {
    String chatName = cleanString(transactionInfo[1]);
    String username = cleanString(transactionInfo[2]);
    ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(chatName);
    chatroomInfo.putMember(username);
  }

  /**
   * Carry out a message sent transaction by putting a sender username and message contents into
   * their respective ordered lists in the chatroomInfo object associated with the given chat name.
   * @param transactionInfo
   */
  public void doMessageSentTransaction(String[] transactionInfo) {
    String senderUsername = cleanString(transactionInfo[1]);
    String messageSent = cleanString(transactionInfo[2]);
    String chatName = cleanString(transactionInfo[3]);
    ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(chatName);
    chatroomInfo.putMessage(senderUsername, messageSent);
  }

  /**
   * Carry out a chatroom logout transaction by removing the user from the list of members in the
   * chatroomInfo object associated with the given chat name and logging the user out.
   * @param transactionInfo
   */
  public void doChatroomLogoutTransaction(String[] transactionInfo) {
    String leaverUsername = cleanString(transactionInfo[1]);
    String chatName = cleanString(transactionInfo[2]);
    ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(chatName);
    chatroomInfo.removeMember(leaverUsername);
    loggedInUsersAndPasswords.remove(leaverUsername);
  }

  /**
   * Carry out a back to chat selection screen transaction by removing the given user from the
   * chatroomInfo object associated with the given chat name.
   * @param transactionInfo
   */
  public void doBackToChatSelectionTransaction(String[] transactionInfo) {
    String leaverUsername = cleanString(transactionInfo[1]);
    String chatName = cleanString(transactionInfo[2]);
    ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(chatName);
    chatroomInfo.removeMember(leaverUsername);
  }

  /**
   * A class that can be executed by a thread, which receives messages from other LookUp servers for
   * the purpose of carrying out the paxos algorithm.
   */
  private class PaxosSocketMessageReceiver implements Runnable {

    public Socket socketToAnotherPaxosLookUpServer;
    public BufferedReader readerToAnotherPaxosLookUpServer;
    public BufferedWriter writerToAnotherPaxosLookUpServer;

    /**
     * Constructor for paxos socket message receiver that sets the socket, buffered reader, and
     * buffered writer in state.
     * @param socket
     * @param existingReader
     * @param existingWriter
     */
    public PaxosSocketMessageReceiver(Socket socket, BufferedReader existingReader, BufferedWriter existingWriter) {
      this.socketToAnotherPaxosLookUpServer = socket;
      this.readerToAnotherPaxosLookUpServer = null;
      this.writerToAnotherPaxosLookUpServer = null;
      if (existingReader != null) {
        this.readerToAnotherPaxosLookUpServer = existingReader;
      }
      if (existingWriter != null) {
        this.writerToAnotherPaxosLookUpServer = existingWriter;
      }
    }

    /**
     * Handle a prepare message from another LookUp server by checking the proposal number and not
     * promising if the maximum promised proposal number is greater than the proposal number. Also
     * do not accept if it is a duplicate proposal number. If ok, send proposal number, max accepted
     * proposal number, and max accepted proposal transaction along with promise.
     * @param messageArray
     */
    public void handlePrepare(String[] messageArray) {
      long proposalNum = Long.parseLong(messageArray[1]);
      if (maxPromisedProposalNumber >= proposalNum) {
        // deny the prepare request by not replying
      } else {
        maxPromisedProposalNumber = proposalNum;
        String promise = "promise@#@" + proposalNum + "@#@" + maxAcceptedProposalNumber + "@#@" + maxAcceptedProposalTransaction;
        try {
          writerToAnotherPaxosLookUpServer.write(promise);
          writerToAnotherPaxosLookUpServer.newLine();
          writerToAnotherPaxosLookUpServer.flush();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    /**
     * Handle a promise message from an acceptor by checking if the given maximum accepted proposal
     * number is larger than the largest promised proposal number, and updating the largest promised
     * proposal number and largest promised transaction if so. Then, check if a majority of acceptors
     * have promised and if so, send accept requests to all acceptors.
     * @param messageArray
     */
    public void handlePromise(String[] messageArray) {
      long proposalNum = Long.parseLong(messageArray[1]);
      int givenMaxAcceptedProposalNumber = Integer.parseInt(messageArray[2]);
      String givenMaxAcceptedProposalTransaction = messageArray[3];
      if (givenMaxAcceptedProposalNumber > largestPromiseNum) {
        largestPromiseNum = givenMaxAcceptedProposalNumber;
        largestPromiseTransaction = givenMaxAcceptedProposalTransaction;
      }
      numPromisedAcceptors++;
      if (acceptorLookUpServersReadersWriters.size() / 2 <= numPromisedAcceptors && !reachedMajorityPromises) {
        reachedMajorityPromises = true;
        for (Map.Entry acceptor : acceptorLookUpServersReadersWriters.entrySet()) {
          BufferedWriter acceptorWriter = (BufferedWriter) acceptor.getValue();
          try {
            acceptorWriter.write("accept@#@" + proposalNum + "@#@" + largestPromiseTransaction);
            acceptorWriter.newLine();
            acceptorWriter.flush();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }

    /**
     * Handle accept request from proposer. If the proposal number is smaller than the max promised
     * proposal number then ignore, but otherwise, update the max accepted proposal number, the max
     * accepted proposal transaction, and the max promised proposal number and respond to the
     * proposer with an acceptance and proposal number and transaction.
     * @param messageArray
     */
    public void handleAccept(String[] messageArray) {
      long proposalNum = Long.parseLong(messageArray[1]);
      String givenTransaction = messageArray[2];
      if (maxPromisedProposalNumber <= proposalNum) {
        maxAcceptedProposalNumber = proposalNum;
        maxAcceptedProposalTransaction = givenTransaction;
        maxPromisedProposalNumber = proposalNum;
        try {
          this.writerToAnotherPaxosLookUpServer.write("acceptResponse@#@" + proposalNum + "@#@" + givenTransaction);
          this.writerToAnotherPaxosLookUpServer.newLine();
          this.writerToAnotherPaxosLookUpServer.flush();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    /**
     * Handle an acceptance from an acceptor. Keep track of the number of acceptors that have
     * accepted. If a majority has then notify all the learners (proposers and acceptors are also
     * learners) pf what transaction to carry out.
     * @param messageArray
     */
    public void handleAcceptResponse(String[] messageArray) {
      long proposalNum = Long.parseLong(messageArray[1]);
      String givenTransaction = messageArray[2];
      numAcceptedAcceptors++;
      if (acceptorLookUpServersReadersWriters.size() / 2 <= numAcceptedAcceptors && !reachedMajorityAcceptances) {
        reachedMajorityAcceptances = true;
        // motify all acceptors of what transaction they should carry out,
        for (Map.Entry acceptor : acceptorLookUpServersReadersWriters.entrySet()) {
          BufferedWriter acceptorWriter = (BufferedWriter) acceptor.getValue();
          try {
            acceptorWriter.write("educate@#@" + largestPromiseTransaction);
            acceptorWriter.newLine();
            acceptorWriter.flush();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
        // notify all proposers of what transaction they should carry out.
        for (Map.Entry proposer : proposerLookUpServersReadersWriters.entrySet()) {
          BufferedWriter proposerWriter = (BufferedWriter) proposer.getValue();
          try {
            proposerWriter.write("educate@#@" + largestPromiseTransaction);
            proposerWriter.newLine();
            proposerWriter.flush();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
        // notify all learners of what transaction they should carry out.
        for (Map.Entry learner : learnerLookUpServersReadersWriters.entrySet()) {
          BufferedWriter learnerWriter = (BufferedWriter) learner.getValue();
          try {
            learnerWriter.write("educate@#@" + largestPromiseTransaction);
            learnerWriter.newLine();
            learnerWriter.flush();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
        prepareForNextPaxosRound();
      }
    }

    /**
     * Handle an education request from proposers, for which this server will carry out the given
     * transaction. There are multiple types of transactions that can be carried out to keep the
     * servers in sync.
     * @param messageArray
     */
    public void handleEducate(String[] messageArray) {
      String transaction = messageArray[1];
      String[] transactionInfo = transaction.split("&%%");
      if (transactionInfo != null && transactionInfo.length > 1) {
        String transactionType = transactionInfo[0];
        prepareForNextPaxosRound();
        // given the type of transaction, have the appropriate handler take care of it.
        if (transactionType.equalsIgnoreCase("login")) {
          doLoginTransaction(transactionInfo);
        } else if (transactionType.equalsIgnoreCase("register")) {
          doRegisterTransaction(transactionInfo);
        } else if (transactionType.equalsIgnoreCase("logout")) {
          doLogoutTransaction(transactionInfo);
        } else if (transactionType.equalsIgnoreCase("createChat")) {
          doCreateChatTransaction(transactionInfo);
        } else if (transactionType.equalsIgnoreCase("updateChatConnectionPort")) {
          doUpdateChatConnectionPortTransaction(transactionInfo);
        } else if (transactionType.equalsIgnoreCase("joinChat")) {
          doJoinChatTransaction(transactionInfo);
        } else if (transactionType.equalsIgnoreCase("messageSent")) {
          doMessageSentTransaction(transactionInfo);
        } else if (transactionType.equalsIgnoreCase("chatroomLogout")) {
          doChatroomLogoutTransaction(transactionInfo);
        } else if (transactionType.equalsIgnoreCase("backToChatSelection")) {
          doBackToChatSelectionTransaction(transactionInfo);
        }
      }
    }

    /**
     * Set up the buffered reader and buffered writer to another LookUp server for the purposes of
     * communicating to carry out the paxos algorithm. Continuously listen to the reader and handle
     * each type of message (usually paxos requests, but can also be notifying this LookUp server of
     * the other LookUp server's paxos role.
     */
    @Override
    public void run() {
      try {
        // set up the buffered reader and writer to another LookUp server.
        if (this.readerToAnotherPaxosLookUpServer == null && this.writerToAnotherPaxosLookUpServer == null) {
          this.readerToAnotherPaxosLookUpServer = new BufferedReader(
                  new InputStreamReader(this.socketToAnotherPaxosLookUpServer.getInputStream()));
          this.writerToAnotherPaxosLookUpServer = new BufferedWriter(
                  new OutputStreamWriter(this.socketToAnotherPaxosLookUpServer.getOutputStream()));
        }
        // continuously read in messages from another LookUp server.
        while (true) {
          String line = this.readerToAnotherPaxosLookUpServer.readLine();
          if (line == null) {
            System.out.println("A CONNECTION DROPPED BETWEEN LOOKUP SERVERS");
            break;
          }
          String[] messageArray = line.split("@#@");
          if (line.length() == 0 || messageArray[1] == null) {
            continue;
          }
          // if the other LookUp server tells this server their role, then keep track of it appropriately.
          if (messageArray[0].equalsIgnoreCase("tellMyRole")) {
            String otherServerPaxosRole = messageArray[1];
            if (otherServerPaxosRole.equalsIgnoreCase("acceptor")) {
              acceptorLookUpServersReadersWriters.put(readerToAnotherPaxosLookUpServer, writerToAnotherPaxosLookUpServer);
            } else if (otherServerPaxosRole.equalsIgnoreCase("proposer")) {
              proposerLookUpServersReadersWriters.put(readerToAnotherPaxosLookUpServer, writerToAnotherPaxosLookUpServer);
            } else { // learner
              learnerLookUpServersReadersWriters.put(readerToAnotherPaxosLookUpServer, writerToAnotherPaxosLookUpServer);
            }
          } else if (messageArray[0].equalsIgnoreCase("prepare")) {
            this.handlePrepare(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("promise")) {
            this.handlePromise(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("accept")) {
            handleAccept(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("acceptResponse")) {
            handleAcceptResponse(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("educate")) {
            handleEducate(messageArray);
          } else {
            System.out.println("A message of unknown type " + line + " was received.");
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Connect to the registry server and open a thread to handle communicating with it.
   * @param registryAddress
   * @param registryPort
   */
  public void registerWithRegisterServer(String registryAddress, int registryPort) {
    try {
      Socket socket = new Socket(registryAddress, registryPort);
      RegistryServerListener registryServerListener = new RegistryServerListener(socket);
      new Thread(registryServerListener).start();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * A class that can be executed by a thread, which connects a socket to the registry server, sends
   * the registry server its address/port for its serverSocket that other LookUp servers can connect
   * to as well as send the registry server its paxos role so that it can be stored and used to inform
   * other LookUp servers of its role. Then, this executable class will listen for messages from
   * the registry server about new LookUp servers that this LookUp server can connect to. Then, we
   * connect a socket to that new LookUp server and start a new thread to handle communicating with
   * it.
   */
  private class RegistryServerListener implements Runnable {
    private Socket registrySocket;
    private BufferedReader registryReader;
    private BufferedWriter registryWriter;

    /**
     * Constructor for the registry server listener that sets the socket connected to teh registry
     * in state.
     * @param socket
     */
    public RegistryServerListener(Socket socket) {
      this.registrySocket = socket;
    }

    /**
     * Connect a socket to the registry server. Send the registry server its address/port for its
     * serverSocket that other LookUp servers can connect to as well as send the registry server its
     * paxos role so that it can be stored and used to inform other LookUp servers of its role.
     * Then, listen for messages from the registry server about new LookUp servers that this LookUp
     * server can connect to. Then, connect a socket to that new LookUp server and start a new
     * thread to handle communicating with it.
     */
    @Override
    public void run() {
      try {
        this.registryReader = new BufferedReader(
                new InputStreamReader(this.registrySocket.getInputStream()));
        this.registryWriter = new BufferedWriter(
                new OutputStreamWriter(this.registrySocket.getOutputStream()));
        // send address/port to registry server that other LookUp servers can connect to this one on.
        // Also send the paxos role of this LookUp server.
        this.registryWriter.write(addressForOtherPaxosServersToConnectTo + "@#@" + portForOtherPaxosServersToConnectTo + "@#@" + myPaxosRole);
        this.registryWriter.newLine();
        this.registryWriter.flush();
      } catch (IOException e) {
        e.printStackTrace();
      }
      // continuously listen for messages from the registry server to connect a socket to a new LookUp server.
      while (true) {
        try {
          String line = this.registryReader.readLine();
          String[] messageArray = line.split("@#@");
          if (messageArray[0].equalsIgnoreCase("startConnection")) {
            // start thread to connect socket to this new paxosServer
            String newServerAddress = messageArray[1];
            int newServerPort = Integer.parseInt(messageArray[2]);
            String paxosRole = messageArray[3];
            Socket newServerSocket = new Socket(newServerAddress, newServerPort);
            // Start a new thread to handle communication with new LookUp server.
            InstigateSocketConnectionToOtherPaxosServer instigateSocketConnectionToOtherPaxosServer
                    = new InstigateSocketConnectionToOtherPaxosServer(newServerSocket, paxosRole);
            new Thread(instigateSocketConnectionToOtherPaxosServer).start();
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  /**
   * 
   */
  private class InstigateSocketConnectionToOtherPaxosServer implements Runnable {

    private Socket otherPaxosServerSocket;
    private String paxosRole;
    private BufferedReader readerForOtherPaxosServerSocket;
    private BufferedWriter writerForOtherPaxosServerSocket;

    public InstigateSocketConnectionToOtherPaxosServer(Socket newServerSocket, String paxosRole) {
      this.otherPaxosServerSocket = newServerSocket;
      this.paxosRole = paxosRole;
      try {
        this.readerForOtherPaxosServerSocket = new BufferedReader(
                new InputStreamReader(this.otherPaxosServerSocket.getInputStream()));
        this.writerForOtherPaxosServerSocket = new BufferedWriter(
                new OutputStreamWriter(this.otherPaxosServerSocket.getOutputStream()));
        if (this.paxosRole.equalsIgnoreCase("acceptor")) {
          acceptorLookUpServersReadersWriters.put(this.readerForOtherPaxosServerSocket, this.writerForOtherPaxosServerSocket);
        } else if (this.paxosRole.equalsIgnoreCase("proposer")) {
          proposerLookUpServersReadersWriters.put(this.readerForOtherPaxosServerSocket, this.writerForOtherPaxosServerSocket);
        } else { // learner
          learnerLookUpServersReadersWriters.put(this.readerForOtherPaxosServerSocket, this.writerForOtherPaxosServerSocket);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    @Override
    public void run() {
      try {
        this.writerForOtherPaxosServerSocket.write("tellMyRole@#@" + myPaxosRole);
        this.writerForOtherPaxosServerSocket.newLine();
        this.writerForOtherPaxosServerSocket.flush();
        // start listener so that reader can read messages from the other server.
        PaxosSocketMessageReceiver paxosSocketMessageReceiver = new PaxosSocketMessageReceiver(this.otherPaxosServerSocket,
                this.readerForOtherPaxosServerSocket, this.writerForOtherPaxosServerSocket);
        new Thread(paxosSocketMessageReceiver).start();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private class LoginWaiter implements Runnable {
    @Override
    public void run() {
      while (true) {
        Socket clientSocket = null;
        try {
          clientSocket = serverSocket.accept();
          ClientSocketHandler clientSocketHandler = new ClientSocketHandler(clientSocket);
          new Thread(clientSocketHandler).start();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private class ClientSocketHandler implements Runnable {
    private String clientUsername;
    private Socket clientSocket;
    private InetAddress clientAddress;
    private int clientPort;
    private BufferedWriter clientWriter;
    private BufferedReader clientReader;

    private BufferedWriter heartbeatWriter;
    private BufferedReader heartbeatReader;

    public ClientSocketHandler(Socket socket) {
      this.clientSocket = socket;
      this.clientAddress = this.clientSocket.getInetAddress();
      this.clientPort = this.clientSocket.getPort();
    }

    private void startPaxos(String transactionInfo) {
      long proposalNum = new Date().getTime();
      largestPromiseTransaction = transactionInfo;
      // Prepare acceptors by asking to reply with promise.
      for (Map.Entry acceptor : acceptorLookUpServersReadersWriters.entrySet()) {
        try {
          BufferedWriter acceptorWriter = (BufferedWriter) acceptor.getValue();
          acceptorWriter.write("prepare@#@" + proposalNum);
          acceptorWriter.newLine();
          acceptorWriter.flush();
        } catch (IOException ie) {
          ie.printStackTrace();
          // replace purposely failed acceptors
//              acceptorIDsToReplace.add(acceptorID);
        }
      }
    }

    private String handleLogin(String[] accountInfo) {
      String username = accountInfo[1];
      String password = accountInfo[2];
      if (usernamePasswordStore.get(username) != null
              && usernamePasswordStore.get(username).equalsIgnoreCase(password)
              && !loggedInUsersAndPasswords.containsKey(username)) {
        startPaxos("login&%%" + username + "&%%" + password);
        loggedInUsersAndPasswords.put(username, password);
        this.clientUsername = username;
        usernameToSocketWriters.put(username, this.clientWriter);
        return "success";
      } else if (usernamePasswordStore.get(username) == null || !usernamePasswordStore.get(username).equalsIgnoreCase(password)){
        return "incorrect";
      } else {
        return "alreadyLoggedIn";
      }
    }

    private String handleRegister(String[] accountInfo) {
      String username = accountInfo[1];
      String password = accountInfo[2];
      if (usernamePasswordStore.containsKey(username)) {
        return "exists";
      } else {
        // start paxos so other servers are updated
        startPaxos("register&%%" + username + "&%%" + password);
        this.clientUsername = username;
        usernameToSocketWriters.put(username, this.clientWriter);
        usernamePasswordStore.put(username, password);
        loggedInUsersAndPasswords.put(username, password);
        return "success";
      }
    }

    private String handleChatSelectionLogout(String[] accountInfo) {
      String username = accountInfo[1];
      startPaxos("logout&%%" + username);
      loggedInUsersAndPasswords.remove(username);
      return "success";
    }

    private String handleReCreateChat(String[] chatRequest) {
      String chatName = chatRequest[1];
      String username = chatRequest[2];
      this.clientUsername = username;
      if (!chatNameChatroomInfoStore.containsKey(chatName)) {
        System.out.println("In handleReCreateChat and chatname " + chatName + " not in chatNameChatroomInfoStore");
        return "exists";
      } else {
        // start paxos so other servers are updated
        // TODO - need to get proper ports and addresses etc to other lookupservers.
        startPaxos("reCreateChat&%%" + chatName + "&%%" + username + "&%%" + this.clientAddress.getHostAddress());
        ChatroomInfo updatingChatroomInfo = chatNameChatroomInfoStore.get(chatName);
        updatingChatroomInfo.setHostUsername(username);
        updatingChatroomInfo.setInetAddress(this.clientAddress);
        hostUsernameToChatroomInfos.put(username, updatingChatroomInfo);
        int reUsedID = updatingChatroomInfo.ID;
        String reUsedGroupIP = updatingChatroomInfo.groupIP;
        String heartbeatAddress = null;
        int heartbeatPort = 0;
        try {
          ServerSocket heartbeatServerSocket = new ServerSocket(0);
          heartbeatAddress = heartbeatServerSocket.getInetAddress().getHostAddress();
          heartbeatPort = heartbeatServerSocket.getLocalPort();
          new Thread(new ChatroomHeartbeat(heartbeatServerSocket)).start();
        } catch (IOException e) {
          e.printStackTrace();
        }
        System.out.println("Recreated chatroom with name " + chatName + " hosted by " + username);
        return "success@#@" + reUsedID + "@#@" + reUsedGroupIP + "@#@" + heartbeatAddress + "@#@" + heartbeatPort;
      }
    }

    private String handleCreateChat(String[] chatRequest) {
      String chatName = chatRequest[1];
      String username = chatRequest[2];
      this.clientUsername = username;
      if (chatNameChatroomInfoStore.containsKey(chatName)) {
        return "exists";
      } else {
        // start paxos so other servers are updated
        startPaxos("createChat&%%" + chatName + "&%%" + username + "&%%" + this.clientAddress.getHostAddress());
        ChatroomInfo newChatroomInfo = new ChatroomInfo();
        int newID = nextChatroomID;
        newChatroomInfo.setID(newID);
        nextChatroomID++;
        newChatroomInfo.setHostUsername(username);
        newChatroomInfo.setName(chatName);
        int newGroupIPIndex = nextGroupIPLastDigit;
        newChatroomInfo.setGroupIP(groupIPPrefix + newGroupIPIndex);
        nextGroupIPLastDigit++;
        newChatroomInfo.setInetAddress(this.clientAddress);
        newChatroomInfo.putMember(username);
        chatNameChatroomInfoStore.put(chatName, newChatroomInfo);
        hostUsernameToChatroomInfos.put(username, newChatroomInfo);
        String heartbeatAddress = null;
        int heartbeatPort = 0;
        try {
          ServerSocket heartbeatServerSocket = new ServerSocket(0);
          heartbeatAddress = heartbeatServerSocket.getInetAddress().getHostAddress();
          heartbeatPort = heartbeatServerSocket.getLocalPort();

          new Thread(new ChatroomHeartbeat(heartbeatServerSocket)).start();
        } catch (IOException e) {
          e.printStackTrace();
        }
        System.out.println("Created chatroom with name " + chatName + " hosted by " + username);
        return "success@#@" + newID + "@#@" + groupIPPrefix + newGroupIPIndex+ "@#@" + heartbeatAddress + "@#@" + heartbeatPort;
      }
    }

    private class ChatroomHeartbeat implements Runnable {

      public ServerSocket heartbeatServerSocket;
      public Timer heartbeatTimer;

      public ChatroomHeartbeat(ServerSocket heartbeatSocket) {
        this.heartbeatServerSocket = heartbeatSocket;
      }

      @Override
      public void run() {
        try {
          Socket heartbeatSocket = this.heartbeatServerSocket.accept();
          heartbeatWriter = new BufferedWriter(new OutputStreamWriter(heartbeatSocket.getOutputStream()));
          heartbeatReader = new BufferedReader(new InputStreamReader(heartbeatSocket.getInputStream()));
          new Thread(new ChatroomListener(heartbeatWriter,heartbeatReader)).start();
        } catch (IOException e) {
          e.printStackTrace();
        }
        ActionListener listener = event -> {
          try {
            heartbeatWriter.write("heartbeat");
            heartbeatWriter.newLine();
            heartbeatWriter.flush();
          } catch (IOException e) {
            // TODO - this is the case that the chatroom host failed.
            hostUsernameToHearbeatTimer.remove(clientUsername);
            this.heartbeatTimer.stop();
            loggedInUsersAndPasswords.remove(clientUsername);
            System.out.println("Host has left");
            // need to force a client to make a new chatroom server. Maybe have hashmap of client
            // usernames to readers/writers.
            ChatroomInfo chatroomInfo = hostUsernameToChatroomInfos.get(clientUsername);
            chatroomInfo.removeMember(clientUsername);
            String newHost = chatroomInfo.getNewHost();
            if (newHost == null) {
              // this is the case that the host was the last member of the chatroom before they
              // exited, so we do not need to reboot the chatroom.
              hostUsernameToChatroomInfos.remove(clientUsername);
              chatNameChatroomInfoStore.remove(chatroomInfo.name);
            } else {
              // TODO - First, we change the chatroomInfo attribute "awaitingReassignment" to true. Then, the
              // LookUpServer multicasts to members with message that includes the name of the new
              // host and the name of the chatroom they should create. The member that is named in the
              // message will call createChat with the given chatname. When they send that message to
              // the lookup server, the lookup server's handleCreateChat should have the code to check
              // if the awaitingReassignment is true on the chatnameInfo that matches that chatname (if it exists).
              // When the attribute is true, you just update the hostUsername (and InetAddress maybe?).
              // Then I need to figure out how the server gui gets an array of all the messages to post.
              // Also, each client member has to get a new address and port to connect to to send messages
              // to the chatroom server.

              try {
                DatagramSocket datagramSocket = new DatagramSocket();
                String message = "chatkey125" + "$@newHost@$@#@" + newHost;
                byte[] buffer = message.getBytes();
                String groupAddress = chatroomInfo.groupIP;
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(groupAddress), 4446);
                datagramSocket.send(packet);
                datagramSocket.close();
              } catch (SocketException se) {
                e.printStackTrace();
              } catch (IOException ie) {
                e.printStackTrace();
              }
            }
          }
        };
        this.heartbeatTimer = new Timer(500, listener);
        this.heartbeatTimer.setRepeats(true);
        this.heartbeatTimer.start();
        hostUsernameToHearbeatTimer.put(clientUsername, this.heartbeatTimer);
        while (true) {
          // TODO - handle this case more cleanly so that host leaving turns this off.
        }
      }
    }

    private class ChatroomListener implements Runnable {

      private BufferedWriter chatroomWriter;
      private BufferedReader chatroomReader;

      public ChatroomListener(BufferedWriter writer, BufferedReader reader) {
        this.chatroomWriter = writer;
        this.chatroomReader = reader;
      }

      @Override
      public void run() {
        boolean isAlive = true;
        while (isAlive) {
          String line = null;
          try {
            line = this.chatroomReader.readLine();
            if (line == null) {
              break;
            }
            String[] messageArray = line.split("@#@");
            if (messageArray[0].equalsIgnoreCase("messageSent")) {
              String senderUsername = messageArray[1];
              String messageSent = messageArray[2];
              ChatroomInfo chatroomInfo = hostUsernameToChatroomInfos.get(clientUsername);
              chatroomInfo.putMessage(senderUsername, messageSent);
              String transaction = "messageSent&%%" + messageArray[1] + "&%%" + messageArray[2] + "&%%" + chatroomInfo.name;
              startPaxos(transaction);
            } else if (messageArray[0].equalsIgnoreCase("chatroomLogout")) {
              String leaverUsername = messageArray[1];
              ChatroomInfo chatroomInfo = hostUsernameToChatroomInfos.get(clientUsername);
              chatroomInfo.removeMember(leaverUsername);
              loggedInUsersAndPasswords.remove(leaverUsername);
              String transaction = "chatroomLogout&%%" + messageArray[1] + "&%%" + chatroomInfo.name;
              startPaxos(transaction);
            } else if (messageArray[0].equalsIgnoreCase("hostChatroomLogout")) {
              hostUsernameToHearbeatTimer.get(clientUsername).stop();
              hostUsernameToHearbeatTimer.remove(clientUsername);
              loggedInUsersAndPasswords.remove(clientUsername);
              System.out.println("Host has left");
              // need to force a client to make a new chatroom server.
              ChatroomInfo chatroomInfo = hostUsernameToChatroomInfos.get(clientUsername);
              chatroomInfo.removeMember(clientUsername);
              this.chatroomWriter.write("removeGUI");
              this.chatroomWriter.newLine();
              this.chatroomWriter.flush();
              String newHost = chatroomInfo.getNewHost();
//              chatroomInfo.setAwaitingReassignment(true);
              if (newHost != null) {
                // this is the case that there is another member in the chatroom, so we need to
                // reboot the chatroom.
                try {
                  DatagramSocket datagramSocket = new DatagramSocket();
                  String message = "chatkey125" + "$@newHost@$@#@" + newHost;
                  byte[] buffer = message.getBytes();
                  String groupAddress = chatroomInfo.groupIP;
                  DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(groupAddress), 4446);
                  datagramSocket.send(packet);
                  datagramSocket.close();
                } catch (SocketException se) {
                  se.printStackTrace();
                } catch (IOException ie) {
                  ie.printStackTrace();
                }
              } else {
                // this is the case that no one is left in the chatroom.
                hostUsernameToChatroomInfos.remove(clientUsername);
                chatNameChatroomInfoStore.remove(chatroomInfo.name);
              }
            } else if (messageArray[0].equalsIgnoreCase("backToChatSelection")) {
              String leaverUsername = messageArray[1];
              ChatroomInfo chatroomInfo = hostUsernameToChatroomInfos.get(clientUsername);
              chatroomInfo.removeMember(leaverUsername);
              String transaction = "backToChatSelection&%%" + messageArray[1] + "&%%" + chatroomInfo.name;
              startPaxos(transaction);
            } else if (messageArray[0].equalsIgnoreCase("hostBackToChatSelection")) {
              hostUsernameToHearbeatTimer.get(clientUsername).stop();
              hostUsernameToHearbeatTimer.remove(clientUsername);
              System.out.println("Host has left");
              // need to force a client to make a new chatroom server.
              ChatroomInfo chatroomInfo = hostUsernameToChatroomInfos.get(clientUsername);
              chatroomInfo.removeMember(clientUsername);
              this.chatroomWriter.write("removeGUI");
              this.chatroomWriter.newLine();
              this.chatroomWriter.flush();
              String newHost = chatroomInfo.getNewHost();
//              chatroomInfo.setAwaitingReassignment(true);
              if (newHost != null) {
                // this is the case that there is another member in the chatroom, so we need to
                // reboot the chatroom.
                try {
                  DatagramSocket datagramSocket = new DatagramSocket();
                  String message = "chatkey125" + "$@newHost@$@#@" + newHost;
                  byte[] buffer = message.getBytes();
                  String groupAddress = chatroomInfo.groupIP;
                  DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(groupAddress), 4446);
                  datagramSocket.send(packet);
                  datagramSocket.close();
                } catch (SocketException se) {
                  se.printStackTrace();
                } catch (IOException ie) {
                  ie.printStackTrace();
                }
              } else {
                // this is the case that no one is left in the chatroom.
                hostUsernameToChatroomInfos.remove(clientUsername);
                chatNameChatroomInfoStore.remove(chatroomInfo.name);
              }
            } else if (messageArray[0].equalsIgnoreCase("addedASCII")) {
              // TODO - maybe add this portion
            }
          } catch (IOException e) {
            isAlive = false;
            e.printStackTrace();
          }
        }
      }
    }

    private String handleUpdateChatConnectionPort(String[] updatePortMessage) {
      int newPort = Integer.parseInt(updatePortMessage[1]);
      String chatroomName = updatePortMessage[2];
      // start paxos so other servers are updated
      startPaxos("updateChatConnectionPort&%%" + newPort + "&%%" + chatroomName);
      ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(chatroomName);
      chatroomInfo.setPort(newPort);
      return "success";
    }

    private String handleNotifyMembersOfRecreation(String[] notifyMessage) {
      String newServerAddress = notifyMessage[1];
      int newServerPort = Integer.parseInt(notifyMessage[2]);
      String chatName = notifyMessage[3];
      String newHost = notifyMessage[4];
      try {
        DatagramSocket datagramSocket = new DatagramSocket();
        String message = "chatkey125" + "$@notifyRecreation@$@#@" + newHost + "@#@" + newServerAddress + "@#@" + newServerPort;
        byte[] buffer = message.getBytes();
        ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(chatName);
        String groupAddress = chatroomInfo.groupIP;
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(groupAddress), 4446);
        datagramSocket.send(packet);
        datagramSocket.close();
      } catch (SocketException se) {
        se.printStackTrace();
      } catch (IOException ie) {
        ie.printStackTrace();
      }
      return "success";
    }

    private String handleGetAllChatroomMessages(String[] messageInfo) {
      String givenChatname = messageInfo[1];
      ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(givenChatname);
      String allSentMessages = chatroomInfo.getAllMessages();
      System.out.println("allSentMessages: " + allSentMessages);
      return allSentMessages;
    }

    private String handleJoinChat(String[] chatRequest) {
      String chatName = chatRequest[1];
      String username = chatRequest[2];
      if (chatNameChatroomInfoStore.containsKey(chatName)) {
        // start paxos so other servers are updated
        startPaxos("joinChat&%%" + chatName + "&%%" + username);
        ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(chatName);
        String address = chatroomInfo.inetAddress.getHostAddress();
        int port = chatroomInfo.port;
        String groupIP = chatroomInfo.groupIP;
        chatroomInfo.putMember(username);
        System.out.println(username + "joined chatroom called " + chatName);
        return "success@#@" + address + "@#@" + port + "@#@" + groupIP;
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

    private void logOutUser() {
      if (clientUsername != null && !hostUsernameToChatroomInfos.containsKey(clientUsername)) {
        // If logged in, log them out.
        System.out.println("THE NON-HOST CLIENT WAS KILLED");
        loggedInUsersAndPasswords.remove(clientUsername);
        // If part of a room, remove them from the room.
        for (Map.Entry chatNameChatroomInfo : chatNameChatroomInfoStore.entrySet()) {
          ChatroomInfo chatroomInfo = (ChatroomInfo) chatNameChatroomInfo.getValue();
          chatroomInfo.removeMember(clientUsername);
        }
      }
      try {
        this.clientSocket.close();
      } catch (IOException e) {
        System.out.println("Previous socket closing successful.");
      }
    }

    public void run() {
      BufferedReader reader = null;
      BufferedWriter writer = null;
      try {
        reader = new BufferedReader(
                new InputStreamReader(this.clientSocket.getInputStream()));
        writer = new BufferedWriter(
                new OutputStreamWriter(this.clientSocket.getOutputStream()));
        this.clientReader = reader;
        this.clientWriter = writer;
      } catch (IOException e) {
        e.printStackTrace();
      }
      while (true) {
        try {
          String line = reader.readLine();
          if (line == null) {
            // This is the case that the client disconnected completely.
            // The case of a Host client failing is handled elsewhere in the ChatroomHeartbeat.
            logOutUser();
            break;
          }
          String[] messageArray = line.split("@#@");
          String response = null;
          if (messageArray[0].equalsIgnoreCase("login")) {
            response = handleLogin(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("register")) {
            response = handleRegister(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("chatSelectionLogout")) {
            response = handleChatSelectionLogout(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("createChat")) {
            response = handleCreateChat(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("reCreateChat")) {
            response = handleReCreateChat(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("notifyMembersOfRecreation")) {
            response = handleNotifyMembersOfRecreation(messageArray);
          } else if (messageArray[0].equalsIgnoreCase("getAllChatroomMessages")) {
            response = handleGetAllChatroomMessages(messageArray);
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
          logOutUser();
          break;
        }
      }
    }
  }

  public static void main(String[] args) {
    int lookUpPort0 = 10000;
    int lookUpPort1 = 53333;
    System.out.println("Server is running...");
    RegistryServer registryServer = new RegistryServer();
    String registryAddress = registryServer.getRegistryAddress();
    int registryPort = registryServer.getRegistryPort();
    LookUpServer paxosLookUpServer0 = new LookUpServer(lookUpPort0, 0, registryAddress, registryPort, "proposer");
    LookUpServer paxosLookUpServer1 = new LookUpServer(lookUpPort1, 1, registryAddress, registryPort, "proposer");
    LookUpServer paxosLookUpServer2 = new LookUpServer(0, 2, registryAddress, registryPort, "acceptor");
    LookUpServer paxosLookUpServer3 = new LookUpServer(0, 3, registryAddress, registryPort, "acceptor");
    LookUpServer paxosLookUpServer4 = new LookUpServer(0, 4, registryAddress, registryPort, "acceptor");
    LookUpServer paxosLookUpServer5 = new LookUpServer(0, 5, registryAddress, registryPort, "acceptor");
    LookUpServer paxosLookUpServer6 = new LookUpServer(0, 6, registryAddress, registryPort, "acceptor");
    LookUpServer paxosLookUpServer7 = new LookUpServer(0, 7, registryAddress, registryPort, "acceptor");
    LookUpServer paxosLookUpServer8 = new LookUpServer(0, 8, registryAddress, registryPort, "learner");
    LookUpServer paxosLookUpServer9 = new LookUpServer(0, 9, registryAddress, registryPort, "learner");
    LookUpServer paxosLookUpServer10 = new LookUpServer(0, 10, registryAddress, registryPort, "learner");
  }
}
