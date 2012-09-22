package snrProj.trafficControl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * 
 */

/**
 * @author jfischer
 * 
 */
public class Main {
    
    static double endPoints[] = new double[6]; // holds the ending values of
                                               // each road segment
    public static final int DEBUG_MODE_ON = 1;
    public static final int DEBUG_MODE_OFF = 0;
    public static final int UPDATE_RATE = 60; // Updates per second
    public static int debugMode = DEBUG_MODE_ON;
    
    /**
     * @param args
     */
    public static void main(String[] args) {
        
        // Change console output to write to a file instead of the command console,
        // change to false to go back to console output
        if (true) {
            try {
                PrintStream out = new PrintStream(new FileOutputStream("consoleOutput.txt"));
                System.setOut(out);
            } catch (FileNotFoundException fife) {
                fife.printStackTrace();
            }
        }
        
        Logger.printLog("Program Starts", Logger.INFO);
        
        try {
            // Set System Look & Feel (more natural for file chooser etc)
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Modify Look & Feel to use default system look
            System.err.println("Error setting system look and feel, using default");
        }
        
        retrieveFile(); // Get a file
        Logger.printLog("File Imported, Moving to Car Control", Logger.DEBUG);
        
        // Set up the Car Controller AI System to run UPDATE_RATE per second
        ScheduledExecutorService carTimer = Executors.newScheduledThreadPool(1);
        carTimer.scheduleAtFixedRate(new CarControl(), 10, (long) (1000 / UPDATE_RATE), TimeUnit.MILLISECONDS);
        
        // Create the GUI interface
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    Logger.printLog("Starting GUI", Logger.DEBUG);
                    new Gui();
                }
            });
        } catch (Exception e) {
            Logger.printLog("Error Starting GUI", Logger.DEBUG);
            System.exit(-1);
        }
        
    }
    
    /**
     * Open a .road file through the use of a JFileChooser and filter
     */
    private static void retrieveFile() {
        boolean cancelled = true;
        Logger.printLog("Retrieve a File", Logger.DEBUG);
        
        JFrame chooserWindow = new JFrame(); // Need a frame to work the window
        File roadFile = new File("default.road");
        JFileChooser chooser = new JFileChooser("./"); // look in the root folder
        
        chooser.setDialogTitle("Open the Road File");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        // use a custom filter to look for .road files
        chooser.setFileFilter(new RoadFileFilter());
        
        while (cancelled) {
            int returnVal = chooser.showOpenDialog(chooserWindow); // a file is chosen
            if (returnVal == JFileChooser.APPROVE_OPTION) { // The user chose a file
                cancelled = false;
                roadFile = chooser.getSelectedFile();
                try {
                    int i = 0;
                    Logger.printLog("Scanning .road file input", Logger.TRACE);
                    Scanner input = new Scanner(roadFile); // create a scanner to read data
                    while (input.hasNext()) {
                        endPoints[i] = input.nextDouble(); // as long as there's more data, pull it
                        i++;
                    }
                    input.close(); // close the file i/o
                } catch (Exception e) {
                    System.err.println("Error opening/reading from file");
                    System.exit(-1);
                }
            } else if (returnVal == JFileChooser.CANCEL_OPTION) {
                System.exit(-1);
                
            }
            Logger.printLog("Done Retrieving File", Logger.DEBUG);
        }
    }
}