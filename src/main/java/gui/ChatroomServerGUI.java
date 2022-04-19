package gui;

import javax.swing.*;

import client.Client;
import server.ChatroomServer;

public class ChatroomServerGUI {

//  public Client client;
  public ChatroomServer chatroomServer;
  public JFrame frame;
  public JPanel panel;
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
  public JLabel joinChatLabel;
  public JTextField joinChatField;
  public JButton joinChatButton;
  public JLabel createChatLabel;
  public JTextField createChatField;
  public JButton createChatButton;
  public JLabel toastLabel;

  public ChatroomServerGUI(ChatroomServer chatroomServer) {
    this.chatroomServer = chatroomServer;
  }
}
