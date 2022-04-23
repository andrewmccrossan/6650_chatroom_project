//package server;
//
///**
// * Class for response for the prepare request given to acceptors by proposers. Acceptors bundle their
// * response (promise) into this class.
// */
//public class PrepareResponse {
//  public long proposalNum;
//  public long maxAcceptedProposalNumber;
//  public Transaction maxAcceptedProposalTransaction;
//
//  /**
//   * Constructor for prepare response (promise) for acceptors to return to proposers upon a prepare
//   * request.
//   * @param proposalNum
//   * @param maxAcceptedProposalNumber
//   * @param maxAcceptedProposalTransaction
//   */
//  public PrepareResponse(String response) {
//    String[] responseArray = response.split("@#@");
//    this.proposalNum = responseArray[1];
//    this.maxAcceptedProposalNumber = responseArray[2];
//    this.maxAcceptedProposalTransaction = responseArray[2];
//  }
//}
