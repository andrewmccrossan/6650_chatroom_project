package gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;

public class ToastMessage extends JFrame {
  JWindow window;

  public ToastMessage(String message, int xAxis, int yAxis) {
    window = new JWindow();
    window.setBackground(new Color(0,0,0,0));
    JPanel panel = new JPanel() {
      public void paintComponent(Graphics graphics) {
        int width = graphics.getFontMetrics().stringWidth(message);
        int height = graphics.getFontMetrics().getHeight();
        graphics.setColor(Color.red);
        graphics.fillRect(10, 10, width + 30, height + 10);
        graphics.setColor(Color.red);
        graphics.drawRect(10, 10, width + 30, height + 10);
        graphics.setColor(new Color(255, 255, 255, 240));
        graphics.drawString(message, 25, 27);
        int alpha = 250;
        for (int i = 0; i < 4; i++) {
          alpha -= 60;
          graphics.setColor(new Color(0, 0, 0, alpha));
          graphics.drawRect(10 - i, 10 - i, width + 30 + i * 2, height + 10 + i * 2);
        }
      }
    };
    window.add(panel);
    window.setLocation(xAxis, yAxis);
    window.setSize(300, 100);
  }

  public void display() {
    try {
      window.setOpacity(1);
      window.setVisible(true);
      ActionListener listener  = new ActionListener() {
        public void actionPerformed(ActionEvent event){
          for (double d = 1.0; d > 0.2; d -= 0.1) {
            double timeDiff = d;
            ActionListener innerListener = new ActionListener() {
              @Override
              public void actionPerformed(ActionEvent e) {
                window.setOpacity((float) timeDiff);
              }
            };
            Timer timer = new Timer(100, innerListener);
            timer.setRepeats(false);
            timer.start();
          }
//          window.setVisible(false);
        }
      };
      Timer timer = new Timer(2000, listener);
      timer.setRepeats(false);
      timer.start();
      window.setVisible(false);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
