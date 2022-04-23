package server;

public class Transaction {

  public String operationType;

  // register info
  public String registerUsername;
  public String registerPassword;

  // createChat info
  public String createChatRoomName;
  public String createChatUsername;

  // updateChatConnectionPort info
  public int updateChatConnectionPort;
  public String updateChatConnectionRoomName;

  // joinChat info
  public String joinChatRoomName;
  public String joinChatUsername;

  public String val;
  public String transactionInfo;

  public Transaction(String transactionInfo) {
    this.transactionInfo = transactionInfo;
    decipherTransactionInfo();
//    this.operationType = operationType;
//    this.val = val;
  }

  public void decipherTransactionInfo() {
    String[] infoArray = this.transactionInfo.split("@#@");
    if (infoArray[0].equalsIgnoreCase("register")) {
      this.registerUsername = infoArray[1];
      this.registerPassword = infoArray[2];
    } else if (infoArray[0].equalsIgnoreCase("createChat")) {
      this.createChatRoomName = infoArray[1];
      this.createChatUsername = infoArray[2];
    } else if (infoArray[0].equalsIgnoreCase("updateChatConnectionPort")) {
      this.updateChatConnectionPort = Integer.parseInt(infoArray[1]);
      this.updateChatConnectionRoomName = infoArray[2];
    } else if (infoArray[0].equalsIgnoreCase("joinChat")) {
      this.joinChatRoomName = infoArray[1];
      this.joinChatUsername = infoArray[2];
    }
  }
}
