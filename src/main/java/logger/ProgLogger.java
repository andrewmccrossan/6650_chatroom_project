package logger;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

/**
 * Logger for using built in logger with milliseconds timing.
 */
public class ProgLogger {
  public Logger logger;
  FileHandler handler;

  /**
   * Constructor for logger that uses built in logger with milliseconds timing.
   * @param filename
   * @throws SecurityException
   * @throws IOException
   */
  public ProgLogger(String filename) throws SecurityException, IOException {
    File logFile = new File(filename);
    if (!logFile.exists()) {
      logFile.createNewFile();
    }
    handler = new FileHandler(filename, true);
    logger = Logger.getLogger(filename);
    logger.addHandler(handler);
    MilliLogFormatter logFormatter = new MilliLogFormatter();
    handler.setFormatter(logFormatter);
  }
}
