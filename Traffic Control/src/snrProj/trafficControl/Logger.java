/**
 * 
 */
package snrProj.trafficControl;

import java.sql.Timestamp;
import java.util.Date;

/**
 * @author jfischer
 * 
 */
public class Logger {
    public static final int TRACE = 1;
    public static final int DEBUG = 2;
    public static final int INFO = 3;
    public static final int OFF = 4;
    
    private static int logLevel = OFF;
    
    public Logger() {
        
    }
    
    public static void printLog(String log, int level) {
        if (level >= logLevel) {
            if (level == 1) {
                CurrentTime.getTime();
                System.out.println("TRACE LOG: " + log);
            } else if (level == 2) {
                CurrentTime.getTime();
                System.out.println("DEBUG LOG: " + log);
            } else if (level == 3) {
                CurrentTime.getTime();
                System.out.println("INFO LOG: " + log);
            }
        }
    }
    
    public static class CurrentTime {
        
        public static void getTime() {
            Date date = new Date();
            System.out.print("[" + new Timestamp(date.getTime()) + "]: ");
        }
    }
}
