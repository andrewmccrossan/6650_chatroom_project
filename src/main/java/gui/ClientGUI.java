package gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.swing.*;

import client.Client;

public class ClientGUI {

  public Client client;
  public JFrame frame;
  public JPanel panel;
  public ArrayList<Component> componentsOnPanel;
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

  public JLabel joinChatLabel;
  public JTextField joinChatField;
  public JButton joinChatButton;
  public JLabel createChatLabel;
  public JTextField createChatField;
  public JButton createChatButton;
  public JButton logoutButton;

  public String chatroomName;
  public JLabel chatroomLabel;
  public JTextArea chatroomTextArea;
  public JScrollPane chatroomScrollPane;
  public JTextField chatroomNewMessageField;
  public JButton chatroomNewMessageButton;
  public JSeparator chatroomSeparator;
  public JLabel roomMembersLabel;
  public JTextArea roomMembersTextArea;
  public JScrollPane roomMembersScrollPane;
  public JButton backToChatSelectionButton;


  public class LoginButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // send login username and password
      String response = client.attemptLogin(loginUsername.getText(), loginPassword.getText());
      System.out.println("Login response: " + response);
      // change GUI to let user choose chatroom or create one
      if (response.equalsIgnoreCase("success")) {
        openChatSelectionScreen();
      } else {
        openToastLabel("Incorrect username/password!");
      }
    }
  }

  public class RegisterButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // send register username and password
      String response = client.attemptRegister(registerUsername.getText(), registerPassword.getText());
      System.out.println("Register response: " + response);
      if (response.equalsIgnoreCase("success")) {
        openChatSelectionScreen();
      } else {
        openToastLabel("Already existing username!");
      }
    }
  }

  public void openToastLabel(String message) {
    toastLabel = new JLabel(message);
    toastLabel.setForeground(Color.red);
    panel.add(toastLabel);
    frame.pack();
    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        panel.remove(toastLabel);
        frame.pack();
      }
    };
    Timer timer = new Timer(2000, listener);
    timer.setRepeats(false);
    timer.start();
  }

  public class JoinChatButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      System.out.println("TRYING TO JOIN CHATROOM");
//      String response = client.attemptJoinChat(joinChatField.getText());
//      System.out.println("Join chatroom response: " + response);
    }
  }

  public class CreateChatButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      System.out.println("TRYING TO CREATE CHATROOM");
      chatroomName = createChatField.getText();
      String response = client.attemptCreateChat(chatroomName);
      if (response.equalsIgnoreCase("success")) {
        openChatroomScreen();
      }
      System.out.println("Create chatroom response: " + response);
    }
  }

  public class LogOutButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      openLoginRegisterScreen();
    }
  }

  public class ChatroomNewMessageButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      String response = client.sendNewChatroomMessage(chatroomNewMessageField.getText());
      // TODO - handle the response to attempt to send message
    }
  }

  public class BackToChatSelectionButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      openChatSelectionScreen();
    }
  }

  public void removeAllComponents() {
    this.componentsOnPanel.forEach((component -> {
      this.panel.remove(component);
    }));
  }

  public void addComponentToPanel(Component component) {
    this.componentsOnPanel.add(component);
    this.panel.add(component);
  }

  public void displayNewMessage(String sender, String message) {
    this.chatroomTextArea.append(sender + ": " + message + "\n");
  }

  public void openChatroomScreen() {
    this.removeAllComponents();
//    panel.setLayout(new GridLayout(0, 1));
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    // Add messaging components to panel
    this.chatroomLabel = new JLabel(this.chatroomName);
    this.chatroomTextArea = new JTextArea(10, 30);
    this.chatroomScrollPane = new JScrollPane(this.chatroomTextArea);
    this.chatroomTextArea.setEditable(false);
    this.chatroomNewMessageField = new JTextField(30);
    this.chatroomNewMessageButton = new JButton("Send");
    this.chatroomNewMessageButton.addActionListener(new ChatroomNewMessageButtonListener());
    this.chatroomSeparator = new JSeparator();
    addComponentToPanel(this.chatroomLabel);
    addComponentToPanel(this.chatroomScrollPane);
    addComponentToPanel(this.chatroomNewMessageField);
    addComponentToPanel(this.chatroomNewMessageButton);
    addComponentToPanel(this.chatroomSeparator);
    addComponentToPanel(new JSeparator());

    // Add section for what room members are in chatroom
    this.roomMembersLabel = new JLabel("Users in this chatroom:");
    this.roomMembersTextArea = new JTextArea(5, 20);
    this.roomMembersScrollPane = new JScrollPane(this.roomMembersTextArea);
    this.roomMembersTextArea.setEditable(false);
    addComponentToPanel(this.roomMembersLabel);
    addComponentToPanel(this.roomMembersScrollPane);

    // Add button to go back to menu to join another chatroom
    this.backToChatSelectionButton = new JButton("Go Back To Chatroom Selection Screen");
    this.backToChatSelectionButton.addActionListener(new BackToChatSelectionButtonListener());
    this.logoutButton = new JButton("Log Out");
    this.logoutButton.addActionListener(new LogOutButtonListener());
    addComponentToPanel(this.backToChatSelectionButton);
    addComponentToPanel(this.logoutButton);

    frame.setTitle(this.chatroomName + " Chatroom");
    frame.pack();
  }

  public void openChatSelectionScreen() {
    this.removeAllComponents();

    panel.setLayout(new GridLayout(0, 2));

    joinChatLabel = new JLabel("Join Chatroom:");
    createChatLabel = new JLabel("Create Chatroom:");
    joinChatField = new JTextField(10);
    createChatField = new JTextField(10);
    joinChatButton = new JButton("Join");
    createChatButton = new JButton("Create");
    joinChatButton.addActionListener(new JoinChatButtonListener());
    createChatButton.addActionListener(new CreateChatButtonListener());
    logoutButton = new JButton("Log Out");
    logoutButton.addActionListener(new LogOutButtonListener());

    addComponentToPanel(joinChatLabel);
    addComponentToPanel(createChatLabel);
    addComponentToPanel(joinChatField);
    addComponentToPanel(createChatField);
    addComponentToPanel(joinChatButton);
    addComponentToPanel(createChatButton);
    addComponentToPanel(logoutButton);

    frame.setTitle("Join/Create Chatroom");
    frame.pack();
  }

  public void openLoginRegisterScreen() {
    this.removeAllComponents();

    this.loginTitleLabel = new JLabel("Login");
    this.loginTitleLabel.setFont(new Font("Serif", Font.BOLD, 26));
    this.loginUsernameLabel = new JLabel("Username:");
    this.loginUsername = new JTextField(10);
    this.loginPasswordLabel = new JLabel("Password:");
    this.loginPassword = new JTextField(10);
    this.loginButton = new JButton("Login");
    this.loginButton.addActionListener(new LoginButtonListener());

    this.separator = new JSeparator();

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

  public ClientGUI(Client client) {
    this.client = client;
    this.frame = new JFrame();
    this.panel = new JPanel();
    this.componentsOnPanel = new ArrayList<>();
    openLoginRegisterScreen();

//    this.loginTitleLabel = new JLabel("Login");
//    this.loginTitleLabel.setFont(new Font("Serif", Font.BOLD, 26));
//    this.loginUsernameLabel = new JLabel("Username:");
//    this.loginUsername = new JTextField(10);
//    this.loginPasswordLabel = new JLabel("Password:");
//    this.loginPassword = new JTextField(10);
//    this.loginButton = new JButton("Login");
//    this.loginButton.addActionListener(new LoginButtonListener());
//
//    this.separator = new JSeparator();
//
//    this.registerTitleLabel = new JLabel("Register");
//    this.registerTitleLabel.setFont(new Font("Serif", Font.BOLD, 26));
//    this.registerUsernameLabel = new JLabel("Username:");
//    this.registerUsername = new JTextField(10);
//    this.registerPasswordLabel = new JLabel("Password:");
//    this.registerPassword = new JTextField(10);
//    this.registerButton = new JButton("Register");
//    this.registerButton.addActionListener(new RegisterButtonListener());
//
//    panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 10, 30));
//    panel.setLayout(new GridLayout(0, 1));
//
//    addComponentToPanel(this.loginTitleLabel);
//    addComponentToPanel(this.loginUsernameLabel);
//    addComponentToPanel(this.loginUsername);
//    addComponentToPanel(this.loginPasswordLabel);
//    addComponentToPanel(this.loginPassword);
//    addComponentToPanel(this.loginButton);
//    addComponentToPanel(this.separator);
//    addComponentToPanel(this.registerTitleLabel);
//    addComponentToPanel(this.registerUsernameLabel);
//    addComponentToPanel(this.registerUsername);
//    addComponentToPanel(this.registerPasswordLabel);
//    addComponentToPanel(this.registerPassword);
//    addComponentToPanel(this.registerButton);
//
//    frame.add(panel, BorderLayout.CENTER);
//    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//    frame.setTitle("Login/Register");
//    frame.pack();
//    frame.setVisible(true);
  }
}
