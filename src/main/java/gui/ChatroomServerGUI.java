package gui;

import java.awt.*;
import java.util.ArrayList;

import javax.swing.*;

import logger.ProgLogger;
import server.ChatroomServer;

/**
 * Class for displaying a Java Swing GUI window for the ChatroomServer to display the entire history
 * of the chatroom's messages.
 */
public class ChatroomServerGUI {

  public ChatroomServer chatroomServer;
  public ProgLogger chatroomLogger;
  public JFrame frame;
  public JPanel panel;
  public ArrayList<Component> componentsOnPanel;
  public JLabel chatroomServerTitleLabel;
  public JTextArea chatroomServerTextArea;
  public JScrollPane chatroomServerScrollPane;

  /**
   * Constructor for the Java Swing chatroom server GUI. Constructor creates a new frame and panel,
   * sets the associated chatroomServer, and opens the chatroom server screen that shows a log of
   * the history of all messages for this named chatroom.
   * @param chatroomServer
   */
  public ChatroomServerGUI(ChatroomServer chatroomServer) {
    this.chatroomServer = chatroomServer;
    this.chatroomLogger = chatroomServer.chatroomLogger;
    chatroomLogger.logger.info("Created chatroomServerGUI");
    this.frame = new JFrame();
    this.panel = new JPanel();
    this.componentsOnPanel = new ArrayList<>();
    openChatroomServerScreen();
  }

  /**
   * Open screen that displays the name of the chatroom, name of host, and the history of all messages
   */
  public void openChatroomServerScreen() {
    this.removeAllComponents();
    panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 10, 30));
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    this.chatroomServerTitleLabel = new JLabel(this.chatroomServer.chatroomName
            + " Chatroom with host " + this.chatroomServer.hostClient.username);
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
    chatroomLogger.logger.info("Opened chatroom server screen");
  }

  /**
   * Add a component to the panel.
   * @param component
   */
  public void addComponentToPanel(Component component) {
    this.componentsOnPanel.add(component);
    this.panel.add(component);
  }

  /**
   * Display a new message, showing both the sender's username and their message.
   * @param sender
   * @param message
   */
  public void displayNewMessage(String sender, String message) {
    this.chatroomServerTextArea.append(sender + " sent message: \"" + message + "\"\n");
    chatroomLogger.logger.info("Displayed new message: " + message + " from sender: " + sender);
  }

  /**
   * Remove all components from the panel.
   */
  public void removeAllComponents() {
    this.componentsOnPanel.forEach((component -> {
      this.panel.remove(component);
    }));
  }

  /**
   * Dispose of frame. This removes the window from the host user's screen.
   */
  public void removeFrame() {
    this.frame.dispose();
    chatroomLogger.logger.info("Removed chatroom server GUI screen");
  }
}
