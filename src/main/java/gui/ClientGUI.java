package gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.*;

import client.Client;

/**
 * Class to handle all of the visual aspects of the client's application. A GUI is created and
 * maintained using Java Swing. This GUI handles login/register, chat selection, and chatroom content.
 */
public class ClientGUI {

  // Vars for construction / login / register
  public Client client;
  public JFrame frame;
  public JPanel panel;
  public ArrayList<Component> componentsOnPanel;
  public String myUsername;
  public JButton loginButton;
  public JTextField loginUsername;
  public JTextField loginPassword;
  public JLabel loginTitleLabel;
  public JLabel registerTitleLabel;
  public JLabel loginUsernameLabel;
  public JLabel loginPasswordLabel;
  public JSeparator separator;
  public JTextField registerUsername;
  public JTextField registerPassword;
  public JButton registerButton;
  public JLabel registerUsernameLabel;
  public JLabel registerPasswordLabel;
  public JLabel toastLabel;

  // Vars for Chat selection screen
  public JLabel joinChatLabel;
  public JTextField joinChatField;
  public JButton joinChatButton;
  public JLabel createChatLabel;
  public JTextField createChatField;
  public JButton createChatButton;
  public JLabel allChatroomNamesLabel;
  public JLabel allChatroomMembersLabel;
  public JTextArea allChatroomNamesTextArea;
  public JScrollPane allChatroomNamesScrollPane;
  public JTextArea allChatroomMembersTextArea;
  public JScrollPane allChatroomMembersScrollPane;
  public JButton logoutButton;

  // Vars for Chatroom screen
  public String chatroomName;
  public JLabel chatroomLabel;
  public JTextArea chatroomTextArea;
  public JScrollPane chatroomScrollPane;
  public SmartScroller chatroomSmartScroller;
  public JTextField chatroomNewMessageField;
  public JButton chatroomNewMessageButton;
  public JSeparator chatroomSeparator;
  public JLabel roomMembersLabel;
  public JTextArea roomMembersTextArea;
  public JScrollPane roomMembersScrollPane;
  public JButton getUsersInChatroomButton;
  public JButton backToChatSelectionButton;

  /**
   * Constructor for Client GUI that handles all of the visual aspects of the GUI using Java Swing.
   * This GUI handles all login/register, chat selection, and chatroom. All ChatroomServer GUI logic
   * is in the ChatroomServerGUI.
   * @param client
   */
  public ClientGUI(Client client) {
    this.client = client;
    this.frame = new JFrame();
    this.panel = new JPanel();
    this.componentsOnPanel = new ArrayList<>();
    openLoginRegisterScreen();
  }

  /**
   * Listener for the Login button on the Login/Register screen. Checks for valid text put in
   * username and password boxes and then consults a LookUpServer to see if username/password are
   * correct. If they are incorrect, a toast message pops up indicating that, and if they are correct
   * but that user is already logged in, then a toast message pops indicating that. If username/password
   * are correct and user is not already logged in, then chat selection screen is opened and user is
   * logged in.
   */
  public class LoginButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // send login username and password
      if (loginUsername.getText().length() == 0 || loginPassword.getText().length() == 0) {
        openToastLabel("Provide username and password!");
      } else if (loginUsername.getText().contains("@#@") || loginUsername.getText().contains("%&%")
              || loginPassword.getText().contains("@#@") || loginPassword.getText().contains("%&%")) {
        // These are special reserved sequences since all communication is through sockets and
        // delineators between content must be kept unique.
        openToastLabel("Do not use special reserved sequences '@#@' or '%&%'!");
      } else {
        String response = client.attemptLogin(loginUsername.getText(), loginPassword.getText());
        // change GUI to open chat selection screen
        if (response.equalsIgnoreCase("success")) {
          myUsername = loginUsername.getText();
          openChatSelectionScreen();
        } else if (response.equalsIgnoreCase("incorrect")) {
          openToastLabel("Incorrect username/password!");
        } else {
          openToastLabel("This user already logged in!");
        }
      }
    }
  }

  /**
   * Listener for the Register button on the Login/Register screen. Checks for valid text put in
   * username and password boxes and then consults a LookUpServer to see if username is already in
   * use. If it is already in use, a toast message pops up indicating that. If username is new and
   * password is valid, then chat selection screen is opened and user is logged in.
   */
  public class RegisterButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // send register username and password
      if (registerUsername.getText().length() == 0 || registerPassword.getText().length() == 0) {
        openToastLabel("Provide username and password!");
      } else if (registerUsername.getText().contains("@#@") || registerUsername.getText().contains("%&%")
              || registerPassword.getText().contains("@#@") || registerPassword.getText().contains("%&%")) {
        // These are special reserved sequences since all communication is through sockets and
        // delineators between content must be kept unique.
        openToastLabel("Do not use special reserved sequences '@#@' or '%&%'!");
      } else {
        String response = client.attemptRegister(registerUsername.getText(), registerPassword.getText());
        if (response.equalsIgnoreCase("success")) {
          myUsername = registerUsername.getText();
          openChatSelectionScreen();
        } else {
          openToastLabel("Already existing username!");
        }
      }
    }
  }

  /**
   * Display a toast message, which in this case is a bright red label that disappears after a fixed
   * amount of time. It displays the message given.
   * @param message
   */
  public void openToastLabel(String message) {
    JLabel newToast = new JLabel(message);
    newToast.setForeground(Color.red);
    panel.add(newToast);
    frame.pack();
    // An action listener is called after a timer to remove the toast message.
    ActionListener listener = event -> {
      panel.remove(newToast);
      frame.pack();
      frame.repaint();
    };
    Timer timer = new Timer(3000, listener);
    timer.setRepeats(false);
    timer.start();
  }

  /**
   * Listener for Join Chat button. Checks if contents of associated checkbox are valid and then
   * checks if a chatroom by the given name exists. If it exists, then the chatroom screen is opened
   * and the user has joined that chat. If it does not exist, then a toast message is opened indicating
   * that it does not exist.
   */
  public class JoinChatButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      chatroomName = joinChatField.getText();
      if (chatroomName.length() == 0) {
        openToastLabel("Provide a chatroom name!");
      } else if (chatroomName.contains("@#@") || chatroomName.contains("%&%")) {
        // These are special reserved sequences since all communication is through sockets and
        // delineators between content must be kept unique.
        openToastLabel("Do not use special reserved sequences '@#@' or '%&%'!");
      } else {
        String response = client.attemptJoinChat(chatroomName);
        if (response.equalsIgnoreCase("success")) {
          openChatroomScreen();
        } else { // lookup server returns "nonexistent"
          openToastLabel("Chatroom name does not exist!");
        }
      }
    }
  }

  /**
   * Listener for Join Chat button. Checks if contents of associated checkbox are valid and then
   * checks if a chatroom by the given name exists. If it exists, then the chatroom screen is opened
   * and the user has joined that chat. If it does not exist, then a toast message is opened indicating
   * that it does not exist.
   */
  public class CreateChatButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      chatroomName = createChatField.getText();
      if (chatroomName.length() == 0) {
        openToastLabel("Provide a chatroom name!");
      } else if (chatroomName.contains("@#@") || chatroomName.contains("%&%")) {
        // These are special reserved sequences since all communication is through sockets and
        // delineators between content must be kept unique.
        openToastLabel("Do not use special reserved sequences '@#@' or '%&%'!");
      } else {
        String response = client.attemptCreateChat(chatroomName);
        if (response.equalsIgnoreCase("success")) {
          openChatroomScreen();
        } else { // when lookup server returns "exists"
          openToastLabel("A chatroom already has that name!");
        }
      }
    }
  }

  /**
   * Listener for Logout button in Chat selection screen. Notifies LookUp server that we have logged
   * out. User is not in a chatroom at this screen so the server does not need to account for a member
   * leaving a chatroom. Success causes the Login/Register screen to appear.
   */
  public class ChatSelectionLogOutButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      String response = client.attemptChatSelectionLogout();
      if (response.equalsIgnoreCase("success")) {
        openLoginRegisterScreen();
      } else {
        System.out.println("Could not log out user.");
      }
    }
  }

  /**
   * Listener for Logout button in chatroom. Notifies chatroom server that we have logged out. Chatroom
   * server handles case based on if user logging out is the host client or just a normal client.
   * Success causes the Login/Register screen to appear.
   */
  public class ChatroomLogOutButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      String response = client.attemptChatroomLogout();
      if (response.equalsIgnoreCase("success")) {
        openLoginRegisterScreen();
      } else {
        System.out.println("Could not log out user.");
      }
    }
  }

  /**
   * Listener for Send button in chatroom. This checks if the textbox has any contents to send. Also
   * checks if textbox has reserved string sequences. Opens toast messages in case there is no message
   * or a message with reserved sequences. If valid, the message is sent to the chatroom server, which
   * will multicast message to all members in chatroom.
   */
  public class ChatroomNewMessageButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      String newMessage = chatroomNewMessageField.getText();
      if (newMessage.length() == 0) {
        openToastLabel("Write a message to send!");
      } else if (newMessage.contains("@#@") || newMessage.contains("%&%") || newMessage.contains("~##~")) {
        // These are special reserved sequences since all communication is through sockets and
        // delineators between content must be kept unique.
        openToastLabel("Do not use special reserved sequences '@#@', '%&%', or '~##~'!");
      } else {
        String response = client.sendNewChatroomMessage(chatroomNewMessageField.getText());
      }
    }
  }

  /**
   * Listener for button that updates what users are in the chat.
   */
  public class GetUsersInChatroomButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      ArrayList<String> members = client.attemptGetUsersInChatroom(chatroomName);
      roomMembersTextArea.setText("");
      for (String member : members) {
        roomMembersTextArea.append(member + "\n");
      }
    }
  }

  /**
   * Listener for button that sends user back to chat selection screen. Client tells Chatroom server
   * that this user is leaving. If successful then chat selection screen is opened.
   */
  public class BackToChatSelectionButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      String response = client.attemptBackToChatSelection();
      if (response.equalsIgnoreCase("success")) {
        openChatSelectionScreen();
      } else {
        System.out.println("Could not go back to chat selection screen.");
      }
    }
  }

  /**
   * Remove all of the Swing components on panel so that a new screen be put up.
   */
  public void removeAllComponents() {
    this.componentsOnPanel.forEach((component -> {
      this.panel.remove(component);
    }));
  }

  /**
   * Add component to the list of components so that they can be tracked and removed as necessary.
   * @param component
   */
  public void addComponentToPanel(Component component) {
    this.componentsOnPanel.add(component);
    this.panel.add(component);
  }

  /**
   * Display a message and who it was sent by in the chatroom text area.
   * @param sender
   * @param message
   */
  public void displayNewMessage(String sender, String message) {
    this.chatroomTextArea.append(sender + ": " + message + "\n");
  }

  /**
   * Open the chatroom screen which includes an area that displays texts, a textbox and button to
   * send messages, a section that displays the users currently in the chatroom, a button to update
   * this section, a button to go back to the chat selection screen, and a logout button.
   */
  public void openChatroomScreen() {
    this.removeAllComponents();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    // Add messaging components to panel
    this.chatroomLabel = new JLabel("Your username: " + this.myUsername);
    this.chatroomTextArea = new JTextArea(10, 30);
    this.chatroomScrollPane = new JScrollPane(this.chatroomTextArea);
    this.chatroomTextArea.setEditable(false);
    // smart scroller ensures that display shows most recent messages by default, and it also ensures
    // that a new message will not force the screen to move to the bottom if the user has scrolled
    // up and is looking at older messages.
    new SmartScroller(this.chatroomScrollPane);
    this.chatroomNewMessageField = new JTextField(30);
    this.chatroomNewMessageButton = new JButton("Send");
    this.chatroomNewMessageButton.addActionListener(new ChatroomNewMessageButtonListener());
    this.chatroomSeparator = new JSeparator();
    addComponentToPanel(this.chatroomLabel);
    addComponentToPanel(this.chatroomScrollPane);
    addComponentToPanel(this.chatroomNewMessageField);
    addComponentToPanel(this.chatroomNewMessageButton);
    addComponentToPanel(this.chatroomSeparator);

    // Add section for what room members are in chatroom
    this.roomMembersLabel = new JLabel("Users in this chatroom:");
    this.roomMembersTextArea = new JTextArea(5, 20);
    this.roomMembersScrollPane = new JScrollPane(this.roomMembersTextArea);
    this.roomMembersTextArea.setEditable(false);
    new SmartScroller(this.roomMembersScrollPane);
    this.getUsersInChatroomButton = new JButton("Update Members In Room");
    this.getUsersInChatroomButton.addActionListener(new GetUsersInChatroomButtonListener());
    addComponentToPanel(this.roomMembersLabel);
    addComponentToPanel(this.roomMembersScrollPane);
    addComponentToPanel(this.getUsersInChatroomButton);

    // Add button to go back to menu to join another chatroom
    this.backToChatSelectionButton = new JButton("Go Back To Chatroom Selection Screen");
    this.backToChatSelectionButton.addActionListener(new BackToChatSelectionButtonListener());
    this.logoutButton = new JButton("Log Out");
    this.logoutButton.addActionListener(new ChatroomLogOutButtonListener());
    addComponentToPanel(this.backToChatSelectionButton);
    addComponentToPanel(this.logoutButton);

    frame.setTitle(this.chatroomName + " Chatroom");
    frame.pack();
  }

  /**
   * Open the screen for joining or creating a chatroom. This screen has a textbox and button for
   * joining a chatroom, a textbox and button for creating a chatroom, a display for what chatrooms
   * are live and how many people are in them, and a button for logging out.
   */
  public void openChatSelectionScreen() {
    this.removeAllComponents();
    panel.setLayout(new GridLayout(0, 2));

    // All components for joining or creating a chatroom
    joinChatLabel = new JLabel("Enter Chatroom to Join:");
    createChatLabel = new JLabel("Enter Chatroom to Create:");
    joinChatField = new JTextField(10);
    createChatField = new JTextField(10);
    joinChatButton = new JButton("Join");
    createChatButton = new JButton("Create");
    joinChatButton.addActionListener(new JoinChatButtonListener());
    createChatButton.addActionListener(new CreateChatButtonListener());

    // all components for displaying live chatrooms and how many people are in them
    allChatroomNamesLabel = new JLabel("Available Chatrooms:");
    allChatroomMembersLabel = new JLabel("Total members:");
    allChatroomNamesTextArea = new JTextArea(4, 4);
    allChatroomNamesScrollPane = new JScrollPane(allChatroomNamesTextArea);
    allChatroomNamesTextArea.setEditable(false);
    new SmartScroller(allChatroomNamesScrollPane);
    allChatroomMembersTextArea = new JTextArea(4, 4);
    allChatroomMembersScrollPane = new JScrollPane(allChatroomMembersTextArea);
    allChatroomMembersTextArea.setEditable(false);
    new SmartScroller(allChatroomMembersScrollPane);
    ArrayList<String[]> chatNameNumberPairs = this.client.attemptGetNumUsersInChatrooms();
    for (String[] chatNameNumberPair : chatNameNumberPairs) {
      String roomName = chatNameNumberPair[0];
      String numUsers = chatNameNumberPair[1];
      allChatroomNamesTextArea.append(roomName + "\n");
      allChatroomMembersTextArea.append(numUsers + "\n");
    }

    logoutButton = new JButton("Log Out");
    logoutButton.addActionListener(new ChatSelectionLogOutButtonListener());

    addComponentToPanel(joinChatLabel);
    addComponentToPanel(createChatLabel);
    addComponentToPanel(joinChatField);
    addComponentToPanel(createChatField);
    addComponentToPanel(joinChatButton);
    addComponentToPanel(createChatButton);
    addComponentToPanel(allChatroomNamesLabel);
    addComponentToPanel(allChatroomMembersLabel);
    addComponentToPanel(allChatroomNamesScrollPane);
    addComponentToPanel(allChatroomMembersScrollPane);
    addComponentToPanel(logoutButton);

    frame.setTitle("Join/Create Chatroom");
    frame.pack();
  }

  /**
   * Open screen for logging in or registering. This screen displays a login section with a username
   * textbox, a password textbox, and a button to log in. The screen also displays a register section
   * with a username textbox, a password textbox, and a button to register.
   */
  public void openLoginRegisterScreen() {
    this.removeAllComponents();

    // components for logging in
    this.loginTitleLabel = new JLabel("Login");
    this.loginTitleLabel.setFont(new Font("Serif", Font.BOLD, 26));
    this.loginUsernameLabel = new JLabel("Username:");
    this.loginUsername = new JTextField(10);
    this.loginPasswordLabel = new JLabel("Password:");
    this.loginPassword = new JTextField(10);
    this.loginButton = new JButton("Login");
    this.loginButton.addActionListener(new LoginButtonListener());

    this.separator = new JSeparator();

    // components for registering
    this.registerTitleLabel = new JLabel("Register");
    this.registerTitleLabel.setFont(new Font("Serif", Font.BOLD, 26));
    this.registerUsernameLabel = new JLabel("Username:");
    this.registerUsername = new JTextField(10);
    this.registerPasswordLabel = new JLabel("Password:");
    this.registerPassword = new JTextField(10);
    this.registerButton = new JButton("Register");
    this.registerButton.addActionListener(new RegisterButtonListener());

    panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 10, 30));
    panel.setLayout(new GridLayout(0, 1));

    addComponentToPanel(this.loginTitleLabel);
    addComponentToPanel(this.loginUsernameLabel);
    addComponentToPanel(this.loginUsername);
    addComponentToPanel(this.loginPasswordLabel);
    addComponentToPanel(this.loginPassword);
    addComponentToPanel(this.loginButton);
    addComponentToPanel(this.separator);
    addComponentToPanel(this.registerTitleLabel);
    addComponentToPanel(this.registerUsernameLabel);
    addComponentToPanel(this.registerUsername);
    addComponentToPanel(this.registerPasswordLabel);
    addComponentToPanel(this.registerPassword);
    addComponentToPanel(this.registerButton);

    frame.add(panel, BorderLayout.CENTER);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setTitle("Login/Register");
    frame.pack();
    frame.setVisible(true);
  }
}
