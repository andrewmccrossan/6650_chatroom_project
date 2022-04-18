package logger;

import java.text.SimpleDateFormat;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.Date;

/**
 * Formatter for logger to include milliseconds in log.
 */
public class MilliLogFormatter extends Formatter {

  /**
   * Format logger to have milliseconds at beginning of line.
   *
   * @param logRecord
   */
  @Override
  public String format(LogRecord logRecord) {
    SimpleDateFormat simpleFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    Date now = new Date();
    String dateStr = simpleFormat.format(now);
    return String.format("%s : %s, Message: %s\n",
            dateStr, logRecord.getLevel(), logRecord.getMessage());
  }
}
