package gui;

import java.awt.*;
import java.util.ArrayList;

import javax.swing.*;

import server.ChatroomServer;

public class ChatroomServerGUI {

//  public Client client;
  public ChatroomServer chatroomServer;
  public JFrame frame;
  public JPanel panel;
  public ArrayList<Component> componentsOnPanel;
  public JButton loginButton;
  public JTextField loginUsername;
  public JTextField loginPassword;
  public JLabel chatroomServerTitleLabel;
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
  public JTextArea chatroomServerTextArea;
  public JScrollPane chatroomServerScrollPane;

  public ChatroomServerGUI(ChatroomServer chatroomServer) {
    this.chatroomServer = chatroomServer;
    this.frame = new JFrame();
    this.panel = new JPanel();
    this.componentsOnPanel = new ArrayList<>();
    openChatroomServerScreen();
  }

  public void openChatroomServerScreen() {
    this.removeAllComponents();
    panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 10, 30));
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    this.chatroomServerTitleLabel = new JLabel(this.chatroomServer.chatroomName + " Chatroom Server");
    this.chatroomServerTitleLabel.setFont(new Font("Serif", Font.BOLD, 26));
    this.chatroomServerTextArea = new JTextArea(30, 30);
    this.chatroomServerScrollPane = new JScrollPane(this.chatroomServerTextArea);
    this.chatroomServerTextArea.setEditable(false);
    new SmartScroller(this.chatroomServerScrollPane);
    addComponentToPanel(this.chatroomServerTitleLabel);
    addComponentToPanel(this.chatroomServerScrollPane);
    frame.add(panel, BorderLayout.CENTER);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setTitle(this.chatroomServer.chatroomName + " Chatroom Server");
    frame.pack();
    frame.setVisible(true);
  }

  public void addComponentToPanel(Component component) {
    this.componentsOnPanel.add(component);
    this.panel.add(component);
  }

  public void displayNewMessage(String sender, String message) {
    this.chatroomServerTextArea.append(sender + " sent message: \"" + message + "\"\n");
  }

  public void removeAllComponents() {
    this.componentsOnPanel.forEach((component -> {
      this.panel.remove(component);
    }));
  }

  public void removeFrame() {
    this.frame.dispose();
  }
}
