package snrProj.trafficControl;

/**
 * Parts of timer code adapted from http://download.oracle.com/javase/tutorial/uiswing/misc/timer.html
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Timer;

/**
 * @author jfischer
 * 
 */
public class Gui {
    private static final int FRAMERATE = 60;
    public static final int TOTAL_ROAD_SEGMENTS = 5;
    
    // GUI screen components
    private static JFrame mainWindow = null;
    private static JPanel drawPane = null;
    private static JScrollPane scrollArea = null;
    private static JViewport viewArea = null;
    
    // Miscellaneous Vars
    private static Timer updateTimer = null;
    private static boolean timerUpdated = false;
    
    // Public values for road/car functions
    public static double roadLength[] = new double[TOTAL_ROAD_SEGMENTS]; // Stores
                                                                         // length
                                                                         // of
                                                                         // each
                                                                         // segment
                                                                         // of
                                                                         // road
    public static int xVals[] = new int[5281]; // Stores entire mile of road
                                               // points
    public static int yVals[] = new int[5281];
    public static float xScrollValue;
    public static float yScrollValue;
    
    /**
     * Constructor
     */
    // TODO: Get array of cars here
    public Gui() {
        // Create the main window
        mainWindow = new JFrame("Main Window - Traffic Controller");
        
        // Create the panel
        drawPane = new drawPane();
        viewArea = new JViewport();
        drawPane.setPreferredSize(new Dimension(7000, 7000));
        viewArea.setView(drawPane);
        scrollArea = new JScrollPane(viewArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        viewArea.setDoubleBuffered(true); // Double buffer for smoother draws,
                                          // remove if too cumbersome
        viewArea.setScrollMode(JViewport.BLIT_SCROLL_MODE); // Maybe change to
                                                            // SIMPLE if
                                                            // necessary
        
        // Set the layout for the window
        mainWindow.setPreferredSize(new Dimension(800, 600));
        mainWindow.add(scrollArea, BorderLayout.CENTER);
        
        // Finalize and display the Road GUI
        mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainWindow.setSize(mainWindow.getPreferredSize());
        mainWindow.pack();
        mainWindow.setVisible(true);
        
        Logger.printLog("Starting GUI Update Loop", Logger.DEBUG);
        updateLoop();
        Logger.printLog("Starting Road Creation", Logger.DEBUG);
        road();
    }
    
    public void road() {
        
        double xList[] = new double[1001];
        double yList[] = new double[1001];
        
        double point1y;
        double point2y;
        double point3y = 0;
        double point4y = 0;
        int a = 0;
        
        for (int i = 0; i < 5; i++) {
            int point1x = 0 + 1056 * i;
            int point2x = 352 + 1056 * i;
            int point3x = 704 + 1056 * i;
            int point4x = 1056 + 1056 * i;
            if (i == 0) {
                point1y = 400;
                point2y = 400 + (Main.endPoints[0]) / 2;
                ;
            } else {
                point1y = 400 + (Main.endPoints[i - 1]);
                point2y = (point3y - 400) + point4y;
            }
            point3y = 400 + (Main.endPoints[i]) / 2;
            point4y = 400 + (Main.endPoints[i]);
            
            int increment = 0;
            for (double t = 0.00; t < 1.001; t = t + .001) { // Break the 1/5
                                                             // mile into
                                                             // 1000 pieces
                double xValue = Math.pow((1 - t), 3) * point1x + 3 * Math.pow((1 - t), 2) * t * point2x + 3 * (1 - t)
                        * Math.pow(t, 2) * point3x + Math.pow(t, 3) * point4x;
                double yValue = Math.pow((1 - t), 3) * point1y + 3 * Math.pow((1 - t), 2) * t * point2y + 3 * (1 - t)
                        * Math.pow(t, 2) * point3y + Math.pow(t, 3) * point4y;
                xList[increment] = xValue;
                yList[increment] = yValue;
                xVals[a] = (int) xValue;
                yVals[a] = (int) yValue;
                a++;
                increment++;
            }
            double totalLength = 0.00;
            for (int z = 0; z < 1000; z++) {
                totalLength = totalLength
                        + Math.sqrt(Math.pow((xList[z + 1] - xList[z]), 2) + Math.pow((yList[z + 1] - yList[z]), 2));
            }
            roadLength[i] = totalLength;
            point1x = point1x + 1056;
            point2x = point2x + 1056;
            point3x = point3x + 1056;
            point4x = point4x + 1056;
        }
        Logger.printLog("Road Created", Logger.DEBUG);
    }
    
    private void updateLoop() {
        updateTimer = new Timer((1000 / FRAMERATE), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // What to do when the timer goes off.
                viewArea.repaint();
                setTimerUpdated(true);
                xScrollValue = viewArea.getX();
                yScrollValue = viewArea.getY();
            }
        });
        updateTimer.setRepeats(true);
        Logger.printLog("GUI Event Checking Update Loop starting", Logger.DEBUG);
        updateTimer.start();
    }
    
    public static boolean isTimerUpdated() {
        return timerUpdated;
    }
    
    public static void setTimerUpdated(boolean timerUpdated) {
        Gui.timerUpdated = timerUpdated;
    }
}

class drawPane extends JPanel {
    private static final long serialVersionUID = 1L;
    
    @Override
    protected void paintComponent(Graphics graphic) {
        super.paintComponent(graphic); // This is an overridden JPanel method in
                                       // this class
        Graphics2D g2d = (Graphics2D) graphic;
        drawRoad(g2d);
        drawCars(g2d);
    }
    
    private void drawCars(Graphics2D g2d) {
        if (Gui.isTimerUpdated()) {
            for (int i = 0; i < CarControl.getCarArray().size(); i++) {
                
                AffineTransform Tx = new AffineTransform();
                Tx.setToTranslation(CarControl.getCarArray().elementAt(i).getCar().getCenterX() + Gui.xScrollValue,
                        CarControl.getCarArray().elementAt(i).getCar().getCenterY() + Gui.yScrollValue);
                Tx.rotate(CarControl.getCarArray().elementAt(i).angle);
                Tx.translate(-CarControl.getCarArray().elementAt(i).xPos, -CarControl.getCarArray().elementAt(i).yPos);
                g2d.setTransform(Tx);
                if (CarControl.getCarArray().elementAt(i).getNewLane() != -1) {
                    g2d.setColor(Color.blue);
                } else {
                    g2d.setColor(Color.black);
                }
                g2d.fill(CarControl.getCarArray().elementAt(i).getCar());
            }
        }
    }
    
    private void drawRoad(Graphics2D g2d) {
        for (int i = 0; i < 5004; i++) {
            for (int lanes = 0; lanes < 4; lanes++) {
                g2d.drawLine(Gui.xVals[i], (Gui.yVals[i]) + lanes * 12, Gui.xVals[i + 1], (Gui.yVals[i + 1]) + lanes
                        * 12); // rewrite
                               // to
                               // use
                               // polyline
            }
        }
        g2d.drawLine(1056, 0, 1056, 1000); // draws line to show 1/5 mile
                                           // segments on screen
        g2d.drawLine(2112, 0, 2112, 1000);
        g2d.drawLine(3168, 0, 3168, 1000);
        g2d.drawLine(4224, 0, 4224, 1000);
        g2d.drawLine(5280, 0, 5280, 1000);
    }
}