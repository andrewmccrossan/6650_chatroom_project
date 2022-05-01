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

import logger.ProgLogger;

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
  public ProgLogger logger;
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
      this.logger = new ProgLogger("lookUpServer_" + serverID + "_log.txt");
      this.serverSocket = new ServerSocket(port);
      logger.logger.info("Created server socket for clients to connect to");
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
      System.out.println("Failed to properly set up LookUp server, including logger");
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
      logger.logger.info("Created server socket at port "
              + portForOtherPaxosServersToConnectTo + " for other LookUp servers to connect to");
      NewPaxosServerConnector newPaxosServerConnector = new NewPaxosServerConnector();
      new Thread(newPaxosServerConnector).start();
    } catch (IOException e) {
      logger.logger.warning("Could not connect server socket for other LookUp servers to connect to");
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
          logger.logger.info("Accepted socket connection from other LookUp server");
          PaxosSocketMessageReceiver paxosSocketMessageReceiver = new PaxosSocketMessageReceiver(newPaxosServerSocket, null, null);
          new Thread(paxosSocketMessageReceiver).start();
        } catch (IOException e) {
          logger.logger.warning("Could no accept socket connection from other LookUp server");
        }
      }
    }
  }

  /**
   * Prepare for the next paxos round by resetting paxos-related state variables. This was the
   * approved method by professor in piazza for how to prepare a new round of paxos.
   */
  public void prepareForNextPaxosRound() {
    logger.logger.info("Resetting variables for next Paxos round");
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
    for (Map.Entry chatNameChatroomInfo : chatNameChatroomInfoStore.entrySet()) {
      ChatroomInfo chatroomInfo = (ChatroomInfo) chatNameChatroomInfo.getValue();
      chatroomInfo.removeMember(username);
    }
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
      logger.logger.warning("Could not get InetAddress from hostname");
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
   * Carry out a recreate chatroom transaction by updating the host user and adding the host user and
   * updated chatroomInfo to collection of pairings.
   * @param transactionInfo
   */
  public void doRecreateChatTransaction(String[] transactionInfo) {
    String chatName = cleanString(transactionInfo[1]);
    String hostUser = cleanString(transactionInfo[2]);
    String newHostAddress = cleanString(transactionInfo[3]);
    ChatroomInfo updatingChatroomInfo = chatNameChatroomInfoStore.get(chatName);
    if (updatingChatroomInfo != null) {
      updatingChatroomInfo.setHostUsername(hostUser);
      try {
        updatingChatroomInfo.setInetAddress(InetAddress.getByName(newHostAddress));
      } catch (UnknownHostException e) {
        logger.logger.warning("Host address was unrecognized");
      }
      hostUsernameToChatroomInfos.put(hostUser, updatingChatroomInfo);
    }
  }

  /**
   * Carry out chatroom server host chatroom logout transaction by logging out host and removing
   * them from members collection in chatroomInfo.
   * @param transactionInfo
   */
  public void doChatroomServerHostChatroomLogoutTransaction(String[] transactionInfo) {
    String oldHostUser = cleanString(transactionInfo[1]);
    loggedInUsersAndPasswords.remove(oldHostUser);
    ChatroomInfo chatroomInfo = hostUsernameToChatroomInfos.get(oldHostUser);
    if (chatroomInfo != null) {
      chatroomInfo.removeMember(oldHostUser);
    }
  }

  /**
   * Carry out a transaction for chatroom server host going back to the chat selection screen by
   * removing the host from members collection in chatroomInfo.
   * @param transactionInfo
   */
  public void doChatroomServerHostBackToChatSelectionTransaction(String[] transactionInfo) {
    String oldHostUser = cleanString(transactionInfo[1]);
    ChatroomInfo chatroomInfo = hostUsernameToChatroomInfos.get(oldHostUser);
    if (chatroomInfo != null) {
      chatroomInfo.removeMember(oldHostUser);
    }
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
        logger.logger.info("Promising for proposal number " + proposalNum);
        maxPromisedProposalNumber = proposalNum;
        String promise = "promise@#@" + proposalNum + "@#@" + maxAcceptedProposalNumber + "@#@" + maxAcceptedProposalTransaction;
        try {
          writerToAnotherPaxosLookUpServer.write(promise);
          writerToAnotherPaxosLookUpServer.newLine();
          writerToAnotherPaxosLookUpServer.flush();
        } catch (IOException e) {
          logger.logger.warning("Could not notify proposer of promise");
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
      long givenMaxAcceptedProposalNumber = Long.parseLong(messageArray[2]);
      String givenMaxAcceptedProposalTransaction = messageArray[3];
      if (givenMaxAcceptedProposalNumber > largestPromiseNum) {
        largestPromiseNum = givenMaxAcceptedProposalNumber;
        largestPromiseTransaction = givenMaxAcceptedProposalTransaction;
      }
      numPromisedAcceptors++;
      if (acceptorLookUpServersReadersWriters.size() / 2 <= numPromisedAcceptors && !reachedMajorityPromises) {
        reachedMajorityPromises = true;
        logger.logger.info("Majority of promises reached so requesting acceptors to accept");
        for (Map.Entry acceptor : acceptorLookUpServersReadersWriters.entrySet()) {
          BufferedWriter acceptorWriter = (BufferedWriter) acceptor.getValue();
          try {
            acceptorWriter.write("accept@#@" + proposalNum + "@#@" + largestPromiseTransaction);
            acceptorWriter.newLine();
            acceptorWriter.flush();
          } catch (IOException e) {
            logger.logger.warning("Could not request acceptance from acceptor");
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
        logger.logger.info("Accepting proposal number " + proposalNum);
        try {
          this.writerToAnotherPaxosLookUpServer.write("acceptResponse@#@" + proposalNum + "@#@" + givenTransaction);
          this.writerToAnotherPaxosLookUpServer.newLine();
          this.writerToAnotherPaxosLookUpServer.flush();
        } catch (IOException e) {
          logger.logger.warning("Could not notify proposer of acceptance");
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
        logger.logger.info("Majority of acceptances reached so notifying learners");
        // motify all acceptors of what transaction they should carry out,
        for (Map.Entry acceptor : acceptorLookUpServersReadersWriters.entrySet()) {
          BufferedWriter acceptorWriter = (BufferedWriter) acceptor.getValue();
          try {
            acceptorWriter.write("educate@#@" + largestPromiseTransaction);
            acceptorWriter.newLine();
            acceptorWriter.flush();
          } catch (IOException e) {
            logger.logger.warning("Could not notify acceptors of transaction");
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
            logger.logger.warning("Could not notify proposers of transaction");
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
            logger.logger.warning("Could not notify learners of transaction");
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
        logger.logger.info("Received education request for " + transactionType);
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
        } else if (transactionType.equalsIgnoreCase("reCreateChat")) {
          doRecreateChatTransaction(transactionInfo);
        } else if (transactionType.equalsIgnoreCase("chatroomServerHostChatroomLogout")) {
          doChatroomServerHostChatroomLogoutTransaction(transactionInfo);
        } else if (transactionType.equalsIgnoreCase("chatroomServerHostBackToChatSelection")) {
          doChatroomServerHostBackToChatSelectionTransaction(transactionInfo);
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
            break;
          }
          String[] messageArray = line.split("@#@");
          if (line.length() == 0 || messageArray[1] == null) {
            continue;
          }
          // if the other LookUp server tells this server their role, then keep track of it appropriately.
          if (messageArray[0].equalsIgnoreCase("tellMyRole")) {
            String otherServerPaxosRole = messageArray[1];
            logger.logger.info("Received tellMyRole notification from " + otherServerPaxosRole);
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
            logger.logger.warning("A message of unknown type " + line + " was received.");
          }
        }
      } catch (IOException e) {
        logger.logger.warning("Socket for other LookUp server could not connect/receive messages");
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
      logger.logger.warning("Could not connect socket to registry server");
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
        logger.logger.warning("Could not contact registry server");
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
            logger.logger.info("Connected socket to LookUp server on port " + newServerPort);
            // Start a new thread to handle communication with new LookUp server.
            InstigateSocketConnectionToOtherPaxosServer instigateSocketConnectionToOtherPaxosServer
                    = new InstigateSocketConnectionToOtherPaxosServer(newServerSocket, paxosRole);
            new Thread(instigateSocketConnectionToOtherPaxosServer).start();
          }
        } catch (IOException e) {
          logger.logger.warning("Could not receive message fro registry server");
        }
      }
    }
  }

  /**
   * Class that can be executed by a thread, which sets up the buffered reader and writer for the
   * socket connected to a new LookUp server. Based on the LookUp server's role in paxos, adds it to
   * the correct concurrent hash map of writers for that role. Then tells the new LookUp server your
   * own paxos role and starts a new thread for listening for messages from the LookUp server.
   */
  private class InstigateSocketConnectionToOtherPaxosServer implements Runnable {

    private Socket otherPaxosServerSocket;
    private String paxosRole;
    private BufferedReader readerForOtherPaxosServerSocket;
    private BufferedWriter writerForOtherPaxosServerSocket;

    /**
     * Constructor for the socket connection instigator to another paxos LookUp server. Sets up the
     * buffered reader and writer for the socket connected to a new LookUp server. Based on the LookUp
     * server's role in paxos, adds it to the correct concurrent hash map of writers for that role.
     * @param newServerSocket
     * @param paxosRole
     */
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
        logger.logger.warning("Could not create buffered reader and writer for "
                + paxosRole + " Lookup server");
      }
    }

    /**
     * Tell the new LookUp server what your paxos role is, then start a new thread to listen for and
     * handle messages from this server.
     */
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
        logger.logger.warning("Could not notify other LookUp server of my paxos role");
      }
    }
  }

  /**
   * Class that can be executed by a thread, which waits to receive socket connections from a client,
   * and then starts a thread for listening for and handling messages from the client socket.
   */
  private class LoginWaiter implements Runnable {
    @Override
    public void run() {
      while (true) {
        Socket clientSocket = null;
        try {
          clientSocket = serverSocket.accept();
          logger.logger.info("Accepted client socket connection");
          ClientSocketHandler clientSocketHandler = new ClientSocketHandler(clientSocket);
          new Thread(clientSocketHandler).start();
        } catch (IOException e) {
          logger.logger.warning("Could not accept client socket connection");
        }
      }
    }
  }

  /**
   * Class that can be executed by a thread, which listens for and handles messages from a client.
   */
  private class ClientSocketHandler implements Runnable {
    private String clientUsername;
    private Socket clientSocket;
    private InetAddress clientAddress;
    private int clientPort;
    private BufferedWriter clientWriter;
    private BufferedReader clientReader;

    private BufferedWriter heartbeatWriter;
    private BufferedReader heartbeatReader;

    /**
     * Constructor for the client socket handler, which sets the socket and the address/port for
     * that client.
     * @param socket
     */
    public ClientSocketHandler(Socket socket) {
      this.clientSocket = socket;
      this.clientAddress = this.clientSocket.getInetAddress();
      this.clientPort = this.clientSocket.getPort();
    }

    /**
     * Start a round of the paxos algorithm so that LookUp servers can get consensus on the
     * transaction they will all carry out to stay up-to-date as replicas. The start of the algorithm
     * is to set the proposal number (these are always monotonically increasing) and transaction
     * and send out prepare requests to all of the acceptor LookUp servers. The acceptors' responses
     * are received elsewhere (where all socket messages are received and handled in helper functions).
     * @param transactionInfo
     */
    private void startPaxos(String transactionInfo) {
      // get a proposal number based on the current time.
      long proposalNum = new Date().getTime();
      largestPromiseTransaction = transactionInfo;
      logger.logger.info("Starting paxos round for proposal number " + proposalNum);
      // Prepare acceptors by asking to reply with promise.
      for (Map.Entry acceptor : acceptorLookUpServersReadersWriters.entrySet()) {
        try {
          BufferedWriter acceptorWriter = (BufferedWriter) acceptor.getValue();
          acceptorWriter.write("prepare@#@" + proposalNum);
          acceptorWriter.newLine();
          acceptorWriter.flush();
        } catch (IOException ie) {
          logger.logger.warning("Could not send prepare request for proposal number " + proposalNum);
        }
      }
    }

    /**
     * Handle the case of a login request from a client by checking if the username has an associated
     * account, if the password is correct for that username, and if that user is already logged in.
     * If acceptable, carry out paxos round and log the user in.
     * @param accountInfo
     * @return a string message corresponding to if login was successful, username/password was
     *          incorrect, or user was already logged in.
     */
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
        logger.logger.info("Successfully logged in user " + username);
        return "success";
      } else if (usernamePasswordStore.get(username) == null || !usernamePasswordStore.get(username).equalsIgnoreCase(password)){
        logger.logger.info("Login username/password incorrect");
        return "incorrect";
      } else {
        logger.logger.info("Login request for " + username + " who is already logged in");
        return "alreadyLoggedIn";
      }
    }

    /**
     * Handle register request from client by checking if a user with that username already exists.
     * If not, carry out paxos round and then add user and log them in.
     * @param accountInfo
     * @return a string corresponding to a successful register or if a user with that username
     *          already exists.
     */
    private String handleRegister(String[] accountInfo) {
      String username = accountInfo[1];
      String password = accountInfo[2];
      if (usernamePasswordStore.containsKey(username)) {
        logger.logger.info("Register request for existing username " + username);
        return "exists";
      } else {
        // start paxos so other servers are updated
        startPaxos("register&%%" + username + "&%%" + password);
        this.clientUsername = username;
        usernameToSocketWriters.put(username, this.clientWriter);
        usernamePasswordStore.put(username, password);
        loggedInUsersAndPasswords.put(username, password);
        logger.logger.info("Registered user " + username);
        return "success";
      }
    }

    /**
     * Handle a chat selection logout from a client that occurs when the client clicks logout while
     * they are on the chat selection screen. Start a paxos round and then log them out.
     * @param accountInfo
     * @return success when user is logged out.
     */
    private String handleChatSelectionLogout(String[] accountInfo) {
      String username = accountInfo[1];
      startPaxos("logout&%%" + username);
      loggedInUsersAndPasswords.remove(username);
      logger.logger.info("Logged out user " + username + " who was on chat selection screen");
      return "success";
    }

    /**
     * Handle a recreate chat request from a new host client when a chatroom is being recreated after
     * the original host leaves. This involves carrying out paxos round, setting the host information
     * for the chatroomInfo, and setting up a socket for the chatroom server to connect to to receive
     * heartbeats and send information to the LookUp server.
     * @param chatRequest
     * @return a string corresponding to if chat name is non-existent or if chat was successfully
     *          recreated.
     */
    private String handleReCreateChat(String[] chatRequest) {
      String chatName = chatRequest[1];
      String username = chatRequest[2];
      this.clientUsername = username;
      if (!chatNameChatroomInfoStore.containsKey(chatName)) {
        logger.logger.warning("Chatroom name non-existent and cannot be recreated");
        return "non-existent";
      } else {
        // start paxos so other servers are updated
        startPaxos("reCreateChat&%%" + chatName + "&%%" + username + "&%%" + this.clientAddress.getHostAddress());
        // set appropriate host-related data in chatroomInfo
        ChatroomInfo updatingChatroomInfo = chatNameChatroomInfoStore.get(chatName);
        updatingChatroomInfo.setHostUsername(username);
        updatingChatroomInfo.setInetAddress(this.clientAddress);
        hostUsernameToChatroomInfos.put(username, updatingChatroomInfo);
        int reUsedID = updatingChatroomInfo.ID;
        String reUsedGroupIP = updatingChatroomInfo.groupIP;
        String heartbeatAddress = null;
        int heartbeatPort = 0;
        // start a thread to connect and listen to a chatroom server socket for getting chatroom info
        // and sending heartbeats.
        try {
          ServerSocket heartbeatServerSocket = new ServerSocket(0);
          heartbeatAddress = heartbeatServerSocket.getInetAddress().getHostAddress();
          heartbeatPort = heartbeatServerSocket.getLocalPort();
          new Thread(new ChatroomHeartbeat(heartbeatServerSocket)).start();
        } catch (IOException e) {
          logger.logger.warning("Could not create server socket for recreated chatroom server to connect to");
        }
        logger.logger.info("Recreated chatroom with name " + chatName + " hosted by " + username);
        return "success@#@" + reUsedID + "@#@" + reUsedGroupIP + "@#@" + heartbeatAddress + "@#@" + heartbeatPort;
      }
    }

    /**
     * Handle a create chat request from a client that is trying to create a chat with a certain
     * name. If the chatname already exists then notify client that it exists. Else, start a paxos
     * round, and create a new chatroomInfo with ID, host name, chat name, group IP for multicast,
     * host socket address and port. Set up a socket for the chatroom server to connect to so that
     * info about the chatroom can be received and heartbeats can be sent.
     * @param chatRequest
     * @return a string corresponding to if chatroom already exists or if chat was created successfully.
     */
    private String handleCreateChat(String[] chatRequest) {
      String chatName = chatRequest[1];
      String username = chatRequest[2];
      this.clientUsername = username;
      // if chatroom by that name already exists, then notify client
      if (chatNameChatroomInfoStore.containsKey(chatName)) {
        logger.logger.info("Request to create chatroom with an existing name " + chatName);
        return "exists";
      } else {
        // start paxos so other servers are updated
        startPaxos("createChat&%%" + chatName + "&%%" + username + "&%%" + this.clientAddress.getHostAddress());
        // create new chatroomInfo and set up all applicable details
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
        // start a thread to connect and listen to a chatroom server socket for getting chatroom info
        // and sending heartbeats.
        try {
          ServerSocket heartbeatServerSocket = new ServerSocket(0);
          heartbeatAddress = heartbeatServerSocket.getInetAddress().getHostAddress();
          heartbeatPort = heartbeatServerSocket.getLocalPort();
          new Thread(new ChatroomHeartbeat(heartbeatServerSocket)).start();
        } catch (IOException e) {
          logger.logger.warning("Could not create server socket for recreated chatroom server to connect to");
        }
        logger.logger.info("Created chatroom with name " + chatName + " hosted by " + username);
        return "success@#@" + newID + "@#@" + groupIPPrefix + newGroupIPIndex+ "@#@" + heartbeatAddress + "@#@" + heartbeatPort;
      }
    }

    /**
     * Class that can be executed by a thread, which accepts socket connections from chatroom servers
     * and then starts a new thread to listen for messages from the chatroom server up its status. It
     * also writes consistent heartbeats to the chatroom server to check that it is still alive, and
     * handles the case that the chatroom server died by checking if there are still members in the
     * room and having a chatroom server recreated in that case.
     */
    private class ChatroomHeartbeat implements Runnable {

      public ServerSocket heartbeatServerSocket;
      public Timer heartbeatTimer;

      /**
       * Constructor for chatroom heartbeat that sets the server socket that chatroom servers can
       * connect to.
       * @param heartbeatSocket
       */
      public ChatroomHeartbeat(ServerSocket heartbeatSocket) {
        this.heartbeatServerSocket = heartbeatSocket;
      }

      /**
       * Accept a socket connection from a chatroom server and start a thread for listening to
       * messages from that socket which will contain information about the state of the chatroom.
       * Also send regular heartbeats to the chatroom server to make sure that it is still alive, and
       * handle the case that is fails by choosing a new host if anyone is left and having them
       * recreate a chatroom server.
       */
      @Override
      public void run() {
        try {
          Socket heartbeatSocket = this.heartbeatServerSocket.accept();
          heartbeatWriter = new BufferedWriter(new OutputStreamWriter(heartbeatSocket.getOutputStream()));
          heartbeatReader = new BufferedReader(new InputStreamReader(heartbeatSocket.getInputStream()));
          new Thread(new ChatroomListener(heartbeatWriter,heartbeatReader)).start();
        } catch (IOException e) {
          logger.logger.warning("Could not accept socket connection from chatroom server");
        }
        // create an action listener that sends heartbeats to the chatroom server to check if it is
        // alive. This listener is called by a repeating timer.
        ActionListener listener = event -> {
          try {
            heartbeatWriter.write("heartbeat");
            heartbeatWriter.newLine();
            heartbeatWriter.flush();
          } catch (IOException e) {
            // this is the case that the chatroom host failed.
            hostUsernameToHearbeatTimer.remove(clientUsername);
            // stop heartbeat timer
            this.heartbeatTimer.stop();
            loggedInUsersAndPasswords.remove(clientUsername);
            logger.logger.info("Host " + clientUsername + " has left");
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
              logger.logger.info("Chatroom " + chatroomInfo.name + " deleted since host was last member");
            } else {
              // LookUpServer multicasts to members with message that includes the name of the new
              // host and the name of the chatroom they should create. The member that is named in the
              // message will recreate chatroom with the given chatname.
              try {
                DatagramSocket datagramSocket = new DatagramSocket();
                String message = "chatkey125" + "$@newHost@$@#@" + newHost;
                byte[] buffer = message.getBytes();
                String groupAddress = chatroomInfo.groupIP;
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(groupAddress), 4446);
                datagramSocket.send(packet);
                datagramSocket.close();
              } catch (IOException se) {
                logger.logger.warning("Could not multicast notification of new host " + newHost);
              }
            }
          }
        };
        // start timer that will send a regular heartbeat.
        this.heartbeatTimer = new Timer(500, listener);
        this.heartbeatTimer.setRepeats(true);
        this.heartbeatTimer.start();
        logger.logger.info("Started heartbeat timer for chatroom");
        hostUsernameToHearbeatTimer.put(clientUsername, this.heartbeatTimer);
        // keep heartbeat timer alive
        while (true) {}
      }
    }

    /**
     * Class that can be executed by a thread, which listens for messages from a chatroom server,
     * which will be about the state of the chatroom, and handles these messages.
     */
    private class ChatroomListener implements Runnable {

      private BufferedWriter chatroomWriter;
      private BufferedReader chatroomReader;

      /**
       * Set the buffered writer and reader for the chatroom server socket.
       * @param writer
       * @param reader
       */
      public ChatroomListener(BufferedWriter writer, BufferedReader reader) {
        this.chatroomWriter = writer;
        this.chatroomReader = reader;
      }

      /**
       * Add the new message sent in chatroom to the history of messages sent in chatroomInfo and
       * start paxos round prior to update all servers.
       * @param messageArray
       */
      public void handleChatroomServerMessageSent(String[] messageArray) {
        String senderUsername = messageArray[1];
        String messageSent = messageArray[2];
        ChatroomInfo chatroomInfo = hostUsernameToChatroomInfos.get(clientUsername);
        logger.logger.info("In chatroom " + chatroomInfo.name + " user " + senderUsername + " sent message: " + messageSent);
        String transaction = "messageSent&%%" + messageArray[1] + "&%%" + messageArray[2] + "&%%" + chatroomInfo.name;
        startPaxos(transaction);
        chatroomInfo.putMessage(senderUsername, messageSent);
      }

      /**
       * Logout user and remove client member from list of members in chatroomInfo and start paxos
       * round prior to update all servers.
       * @param messageArray
       */
      public void handleChatroomServerChatroomLogout(String[] messageArray) {
        String leaverUsername = messageArray[1];
        ChatroomInfo chatroomInfo = hostUsernameToChatroomInfos.get(clientUsername);
        String transaction = "chatroomLogout&%%" + messageArray[1] + "&%%" + chatroomInfo.name;
        logger.logger.info("User " + leaverUsername + " logged out while in " + chatroomInfo.name);
        startPaxos(transaction);
        chatroomInfo.removeMember(leaverUsername);
        loggedInUsersAndPasswords.remove(leaverUsername);
      }

      /**
       * When host has left chatroom by clicking logut, stop the heartbeat timer for the chatroom
       * server because that runs on same process as client, log out user, and force a new host to
       * create a new chatroom server if there are members left in chatroom. If no members left in
       * chatroom then simply remove chatroom from existence. Start paxos round prior to update
       * all servers.
       * @param messageArray
       * @throws IOException
       */
      public void handleChatroomServerHostChatroomLogout(String[] messageArray) throws IOException {
        // stop the heartbeat timer since chatroom server is leaving and log user out.
        hostUsernameToHearbeatTimer.get(clientUsername).stop();
        hostUsernameToHearbeatTimer.remove(clientUsername);
        loggedInUsersAndPasswords.remove(clientUsername);
        ChatroomInfo chatroomInfo = hostUsernameToChatroomInfos.get(clientUsername);
        startPaxos("chatroomServerHostChatroomLogout&%%" + clientUsername);
        logger.logger.info("Host " + clientUsername + " has logged out while in chatroom " + chatroomInfo.name);
        // remove the host user from list of members in chatroomInfo and tell chatroom server to
        // remove its GUI
        chatroomInfo.removeMember(clientUsername);
        this.chatroomWriter.write("removeGUI");
        this.chatroomWriter.newLine();
        this.chatroomWriter.flush();
        String newHost = chatroomInfo.getNewHost();
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
          } catch (IOException se) {
            logger.logger.warning("Could not send multicast to notify members of new host " + newHost);
          }
        } else {
          // this is the case that no one is left in the chatroom.
          hostUsernameToChatroomInfos.remove(clientUsername);
          chatNameChatroomInfoStore.remove(chatroomInfo.name);
          logger.logger.info("No one left in chatroom " + chatroomInfo.name);
        }
      }

      /**
       * Remove client member from list of members in chatroomInfo and start paxos round prior to
       * update all servers.
       * @param messageArray
       */
      public void handleChatroomServerBackToChatSelection(String[] messageArray) {
        String leaverUsername = messageArray[1];
        ChatroomInfo chatroomInfo = hostUsernameToChatroomInfos.get(clientUsername);
        String transaction = "backToChatSelection&%%" + messageArray[1] + "&%%" + chatroomInfo.name;
        startPaxos(transaction);
        chatroomInfo.removeMember(leaverUsername);
        logger.logger.info("User " + leaverUsername + " went back to chat selection screen");
      }

      /**
       * When host has left chatroom by clicking back to chat selection, stop the heartbeat timer
       * for the chatroom server because that runs on same process as client and force a new host to
       * create a new chatroom server if there are members left in chatroom. If no members left in
       * chatroom then simply remove chatroom from existence. Start paxos round prior to update all
       * servers.
       * @param messageArray
       * @throws IOException
       */
      public void handleChatroomServerHostBackToChatSelection(String[] messageArray) throws IOException {
        // stop the heartbeat timer since chatroom server is leaving.
        hostUsernameToHearbeatTimer.get(clientUsername).stop();
        hostUsernameToHearbeatTimer.remove(clientUsername);
        startPaxos("chatroomServerHostBackToChatSelection&%%" + clientUsername);
        // remove the host user from list of members in chatroomInfo and tell chatroom server to
        // remove its GUI
        ChatroomInfo chatroomInfo = hostUsernameToChatroomInfos.get(clientUsername);
        logger.logger.info("Host " + clientUsername + " has gone back to chat selection screen" +
                " while in chatroom " + chatroomInfo.name);
        chatroomInfo.removeMember(clientUsername);
        this.chatroomWriter.write("removeGUI");
        this.chatroomWriter.newLine();
        this.chatroomWriter.flush();
        String newHost = chatroomInfo.getNewHost();
        if (newHost != null) {
          // this is the case that there is another member in the chatroom, so reboot the chatroom.
          try {
            DatagramSocket datagramSocket = new DatagramSocket();
            String message = "chatkey125" + "$@newHost@$@#@" + newHost;
            byte[] buffer = message.getBytes();
            String groupAddress = chatroomInfo.groupIP;
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(groupAddress), 4446);
            datagramSocket.send(packet);
            datagramSocket.close();
          } catch (IOException se) {
            logger.logger.warning("Could not send multicast to notify members of new host " + newHost);
          }
        } else {
          // this is the case that no one is left in the chatroom.
          hostUsernameToChatroomInfos.remove(clientUsername);
          chatNameChatroomInfoStore.remove(chatroomInfo.name);
          logger.logger.info("No one left in chatroom " + chatroomInfo.name);
        }
      }

      /**
       * Listen for messages coming from chatroom server socket and handle as necessary.
       */
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
              handleChatroomServerMessageSent(messageArray);
            } else if (messageArray[0].equalsIgnoreCase("chatroomLogout")) {
              handleChatroomServerChatroomLogout(messageArray);
            } else if (messageArray[0].equalsIgnoreCase("hostChatroomLogout")) {
              handleChatroomServerHostChatroomLogout(messageArray);
            } else if (messageArray[0].equalsIgnoreCase("backToChatSelection")) {
              handleChatroomServerBackToChatSelection(messageArray);
            } else if (messageArray[0].equalsIgnoreCase("hostBackToChatSelection")) {
              handleChatroomServerHostBackToChatSelection(messageArray);
            }
          } catch (IOException e) {
            isAlive = false;
            logger.logger.info("Disconnected from socket since chatroom server host left");
          }
        }
      }
    }

    /**
     * Update the port that new users will connect to a chatroom server socket through.
     * @param updatePortMessage
     * @return string indicating success of adding port
     */
    private String handleUpdateChatConnectionPort(String[] updatePortMessage) {
      int newPort = Integer.parseInt(updatePortMessage[1]);
      String chatroomName = updatePortMessage[2];
      // start paxos so other servers are updated
      startPaxos("updateChatConnectionPort&%%" + newPort + "&%%" + chatroomName);
      ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(chatroomName);
      chatroomInfo.setPort(newPort);
      logger.logger.info("Updated chatroom server socket port to " + newPort + " for " + chatroomName);
      return "success";
    }

    /**
     * Notify through multicast the members remaining in a chatroom that a new chatroom server was
     * made and who the new host is and what address/port they can connect to the new chatroom server on.
     * @param notifyMessage
     * @return a string indicating the success of notifying members
     */
    private String handleNotifyMembersOfRecreation(String[] notifyMessage) {
      String newServerAddress = notifyMessage[1];
      int newServerPort = Integer.parseInt(notifyMessage[2]);
      String chatName = notifyMessage[3];
      String newHost = notifyMessage[4];
      // send multicast message to all remaining members of chatroom of who new host is and what
      // address/port to connect to new chatroom server on.
      try {
        DatagramSocket datagramSocket = new DatagramSocket();
        String message = "chatkey125" + "$@notifyRecreation@$@#@" + newHost + "@#@" + newServerAddress + "@#@" + newServerPort;
        byte[] buffer = message.getBytes();
        ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(chatName);
        String groupAddress = chatroomInfo.groupIP;
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(groupAddress), 4446);
        datagramSocket.send(packet);
        datagramSocket.close();
        logger.logger.info("Notified members of recreation of chatroom " + chatroomInfo.name
                + " with new host " + newHost);
      } catch (IOException se) {
        logger.logger.warning("Could not multicast notification of recreation of chatroom");
      }
      return "success";
    }

    /**
     * Get the entire history of chatroom messages from a chatroom.
     * @param messageInfo
     * @return a string with the entire history of chatroom messages from a chatroom
     */
    private String handleGetAllChatroomMessages(String[] messageInfo) {
      String givenChatname = messageInfo[1];
      ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(givenChatname);
      String allSentMessages = chatroomInfo.getAllMessages();
      logger.logger.info("Got history of all messages for chatroom " + givenChatname);
      return allSentMessages;
    }

    /**
     * Add a user by the given username to the chatroom if the chatroom exists. Give the user the
     * address/port that they can connect to the chatroom server on as well as the group IP so that
     * they can receive multicast messages from the chatroom server.
     * @param chatRequest
     * @return a string either indicating that the chatroom does not exist or indicating a successful
     *          addition as well as the address/port of the chatroom server socket and group IP
     *          for multicasts
     */
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
        logger.logger.info("User " + username + " joined chatroom " + chatName);
        return "success@#@" + address + "@#@" + port + "@#@" + groupIP;
      } else {
        logger.logger.info("Chatroom " + chatName + " was attempted to join but does not exist");
        return "nonexistent";
      }
    }

    /**
     * Get the usernames in the given chatroom.
     * @param usersRequest
     * @return a string either indicating the chatroom does not exist or indicating all of the
     *          usernames in a chatroom
     */
    private String handleGetUsersInChatroom(String[] usersRequest) {
      String chatName = usersRequest[1];
      if (chatNameChatroomInfoStore.containsKey(chatName)) {
        ChatroomInfo chatroomInfo = chatNameChatroomInfoStore.get(chatName);
        ArrayList<String> members = chatroomInfo.members;
        StringBuilder membersString = new StringBuilder("START");
        for (String member : members) {
          membersString.append("@#@").append(member);
        }
        logger.logger.info("Handled request to get users in chat " + chatName);
        return membersString.toString();
      } else {
        logger.logger.info("Request to get users in nonexistent chat " + chatName);
        return "nonexistent";
      }
    }

    /**
     * Get the number of users in each chatroom.
     * @return a string indicating the number of users in each chatroom
     */
    private String handleGetNumUsersInChatrooms() {
      StringBuilder response = new StringBuilder();
      for (Map.Entry nameInfoPair : chatNameChatroomInfoStore.entrySet()) {
        String name = (String) nameInfoPair.getKey();
        ChatroomInfo info = (ChatroomInfo) nameInfoPair.getValue();
        response.append(name).append("%&%").append(info.members.size()).append("@&@");
      }
      logger.logger.info("Handled request to get number of users in all chatrooms");
      return response.toString();
    }

    /**
     * Log out user associated with this thread's socket, and remove them from any chatroom they are
     * in. Finally, close their socket.
     */
    private void logOutUser() {
      if (clientUsername != null && !hostUsernameToChatroomInfos.containsKey(clientUsername)) {
        // If logged in, log them out.
        startPaxos("logout&%%" + clientUsername);
        loggedInUsersAndPasswords.remove(clientUsername);
        // If part of a room, remove them from the room.
        for (Map.Entry chatNameChatroomInfo : chatNameChatroomInfoStore.entrySet()) {
          ChatroomInfo chatroomInfo = (ChatroomInfo) chatNameChatroomInfo.getValue();
          chatroomInfo.removeMember(clientUsername);
        }
        logger.logger.info("Logged out user " + clientUsername);
      }
      try {
        this.clientSocket.close();
      } catch (IOException e) {
        logger.logger.info("Socket closed previously successfully.");
      }
    }

    /**
     * Set the buffered reader and writer for the connected client socket, then listen for all
     * messages coming in from the client and handle them as necessary with helper functions. Log out
     * user if client disconnects.
     */
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
        logger.logger.warning("Could not create buffered writer and reader for client socket");
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
          logOutUser();
          break;
        }
      }
    }
  }

  /**
   * Driver function for creating the registry server and multiple LookUp servers with different
   * paxos roles. All non-proposers dynamically find open ports to set up their server sockets for
   * clients.
   * @param args
   */
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
