package server;

public class Transaction {

  public int id;
  public String operationType;
  public String val;

  public Transaction(String operationType, String val) {
    this.operationType = operationType;
    this.val = val;
  }
}
