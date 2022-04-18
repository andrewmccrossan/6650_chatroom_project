package gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;

import client.Client;

public class LoginGUI {

  public Client client;
  public JFrame frame;
  public JPanel panel;
  public JButton loginButton;
  public JTextField loginUsername;
  public JTextField loginPassword;
  public JLabel loginUsernameLabel;
  public JLabel loginPasswordLabel;
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

  public class LoginButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // send login username and password
      String response = client.attemptLogin(loginUsername.getText(), loginPassword.getText());
      System.out.println("Login response: " + response);
      // change GUI to let user choose chatroom or create one
      if (response.equalsIgnoreCase("success")) {
        panel.remove(loginButton);
        panel.remove(loginUsername);
        panel.remove(loginPassword);
        panel.remove(registerUsername);
        panel.remove(registerPassword);
        panel.remove(registerButton);
        panel.remove(loginUsernameLabel);
        panel.remove(loginPasswordLabel);
        panel.remove(registerUsernameLabel);
        panel.remove(registerPasswordLabel);

        panel.setLayout(new GridLayout(0, 2));

        joinChatLabel = new JLabel("Join Chatroom:");
        createChatLabel = new JLabel("Create Chatroom:");
        joinChatField = new JTextField(10);
        createChatField = new JTextField(10);
        joinChatButton = new JButton("Join");
        createChatButton = new JButton("Create");
        joinChatButton.addActionListener(new JoinChatButtonListener());
        createChatButton.addActionListener(new CreateChatButtonListener());

        panel.add(joinChatLabel);
        panel.add(createChatLabel);
        panel.add(joinChatField);
        panel.add(createChatField);
        panel.add(joinChatButton);
        panel.add(createChatButton);
        panel.add(joinChatButton);
        panel.add(createChatButton);
        frame.setTitle("Join/Create Chatroom");
        frame.pack();
      }

    }
  }

  public class RegisterButtonListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      // send register username and password
      String response = client.attemptRegister(registerUsername.getText(), registerPassword.getText());
      System.out.println("Register response: " + response);
    }
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
//      String response = client.attemptCreateChat(joinChatField.getText());
//      System.out.println("Create chatroom response: " + response);
    }
  }

  public LoginGUI(Client client) {
    this.client = client;

    this.frame = new JFrame();
    this.panel = new JPanel();

    this.loginUsernameLabel = new JLabel("Username:");
    this.loginUsername = new JTextField(10);
    this.loginPasswordLabel = new JLabel("Password:");
    this.loginPassword = new JTextField(10);

    this.loginButton = new JButton("Login");
    this.loginButton.addActionListener(new LoginButtonListener());

    this.registerUsernameLabel = new JLabel("Username:");
    this.registerUsername = new JTextField(10);
    this.registerPasswordLabel = new JLabel("Password:");
    this.registerPassword = new JTextField(10);

    this.registerButton = new JButton("Register");
    this.registerButton.addActionListener(new RegisterButtonListener());

    panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 10, 30));
    panel.setLayout(new GridLayout(0, 1));

    panel.add(loginUsernameLabel);
    panel.add(loginUsername);
    panel.add(loginPasswordLabel);
    panel.add(loginPassword);
    panel.add(loginButton);

    panel.add(registerUsernameLabel);
    panel.add(registerUsername);
    panel.add(registerPasswordLabel);
    panel.add(registerPassword);
    panel.add(registerButton);

    frame.add(panel, BorderLayout.CENTER);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setTitle("Login/Register");
    frame.pack();
    frame.setVisible(true);
  }
}
