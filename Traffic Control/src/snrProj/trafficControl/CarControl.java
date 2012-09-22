package snrProj.trafficControl;

import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;
import java.util.Vector;

/**
 * @author jfischer
 * 
 */
public class CarControl implements Runnable {
    public static final int LANE0_SPEED = 90; // Default Lane Speeds
    public static final int LANE1_SPEED = 80;
    public static final int LANE2_SPEED = 70;
    private static final double REQ_CREATE_TIME = .7; // Time before checking to create another car
    private static final double AVG_CREATE_TIME = 1.2; // Average time between creating cars (after req)
    private static final int UPDATE_BTWN_COLLISION = 10; // Every 10th update, check collision
    private static final int UPDATE_BTWN_RESORT = 0; // Every update, resort Car Vector
    private static final double MIN_FOLLOW_DIST = 30; // Distance required to keep between (Feet)
    private static final double MAX_FOLLOW_DIST = 150; // Distance to stop trying to speed up (Feet)
    private static final double MIN_BEHIND_CHANGE_DIST = 30;
    private static final int MIN_AHEAD_CHANGE_DIST = 20;
    
    private static Vector<Car> carArray = new Vector<Car>(); // Create Synched Getters/Setters
    private static Vector<Car> priorityArray = new Vector<Car>();
    
    private static boolean collision = false;
    
    // 4 persistent values for createCars()
    private static int carnum = 0;
    private static int laneFull0 = 0;
    private static int laneFull1 = 0;
    private static int laneFull2 = 0;
    
    /**
     * Update all of the cars currently in the simulation
     */
    public void run() {
        
        Logger.printLog("Update Cars Thread Started", Logger.INFO);
        
        int j = 0;
        int k = 0;
        
        // This section will, every UPDATE_BTWN_RESORT, resort the carArray, O(n*log(n)) by x position
        if (k >= UPDATE_BTWN_RESORT) {
            // We want to compare based on X Position
            Collections.sort(carArray, xPosCompare);
            
            // For the Priority Vector, sort based on change lane priority
            Collections.sort(priorityArray, priorityCompare);
            Logger.printLog("Arrays Resorted", Logger.TRACE);
            
            k = 0;
        }
        // Update the position of every car
        for (int i = 0; i < getPriorityArray().size(); i++) {
            // Set the amount of time between updates, and update the position
            // of the car
            double deltaSeconds;
            if (Main.debugMode == Main.DEBUG_MODE_OFF) {
                // Get the last update on this car's time
                long lastNano = getPriorityArray().elementAt(i).getLastUpdatedNano();
                
                // Determine the time that has passed since updates
                double deltaNano = Math.abs(System.nanoTime()) - lastNano;
                
                // Get the current time
                long currentNano = (long) (deltaNano + lastNano);
                
                // Determine the time that has passed since updates in seconds
                deltaSeconds = deltaNano / 1000000000.0;
                
                // Tell the car the time of the current update
                getPriorityArray().elementAt(i).setLastUpdatedNano(currentNano);
                
                // Reduce the required arrival time by the time that has passed
                double tempReqArrival = getPriorityArray().elementAt(i).getReqArrival();
                tempReqArrival -= deltaSeconds;
                getPriorityArray().elementAt(i).setReqArrival(tempReqArrival);
            } else {
                // fixed timescale in debug mode (1/(number of updates per second))
                deltaSeconds = 1.0 / Main.UPDATE_RATE;
                double tempReqArrival = getPriorityArray().elementAt(i).getReqArrival();
                tempReqArrival -= deltaSeconds;
                getPriorityArray().elementAt(i).setReqArrival(tempReqArrival);
            }
            
            aiLoop(getPriorityArray().elementAt(i), deltaSeconds);
            getPriorityArray().elementAt(i).updatePosition(deltaSeconds, false);
        }
        // Check collision on every car
        if (j == UPDATE_BTWN_COLLISION) {
            collisionLoop();
            j = 0;
        }
        j++;
        k++;
        createCars();
    }
    
    /**
     * Run the AI Loop on the car currently being updated
     * 
     * @param thisCar
     *            The car to run the AI Loop on
     * @param deltaSeconds
     *            The amount of time since the last update
     */
    private static void aiLoop(Car thisCar, double deltaSeconds) {
        Logger.printLog("AI executing on Car " + thisCar, Logger.DEBUG);
        thisCar.carCanChangeLanes = true;
        
        // If the car is changing lanes, we don't want to run the AI Loop
        // If the car has just been created we don't want to run the AI Loop
        // If the car has just changed lanes, we don't want to run the AI Loop
        if (thisCar.getNewLane() != -1 || thisCar.getTimeWaitBeforeChange() <= 3.0) {
            thisCar.setTimeWaitBeforeChange(thisCar.getTimeWaitBeforeChange() + deltaSeconds);
            thisCar.carCanChangeLanes = false;
            return;
        }
        
        // Determine the current time of arrival based on lane
        double currentEta = thisCar.getEstArrival(thisCar.lane);
        double lengthToTravel = thisCar.getLengthToTravel();
        double reqAvgSpeed = ((lengthToTravel / 5280) / (thisCar.getReqArrival() / 60 / 60));
        double arrivalDiff = thisCar.getReqArrival() - currentEta;
        
        Logger.printLog("   Car " + thisCar.getCarNum() + " length to travel " + lengthToTravel + " currentETA "
                + currentEta + " reqAvgSpeed: " + reqAvgSpeed + " arrival diff " + arrivalDiff + " secs", Logger.TRACE);
        
        /*
         * Enter the AI Loop. 1. Check for the time time will reach it's destination vs. time it needs to reach it. 2.
         * Set a priority for the car to change lanes, if necessary. 3a. The car needs to change lanes, check the lane's
         * closest car behind and in front of this car. 3b. If the car doesn't want to change check if it can so that
         * another car could move into the lane 4a. If the lane is clear, change. If not, don't.
         */
        // The car will reach the destination before the required time
        if (arrivalDiff >= 0.0) {
            int tempPriority = 0;
            // Check if the car can go slower in a lane and reach destination on time
            switch (thisCar.lane) {
                case 0:
                    // If the car can switch to a slower lane and reach on time, set priority
                    if (reqAvgSpeed <= 80) {
                        thisCar.setShouldChangeTo(1);
                        thisCar.setChangePriority(-1);
                        Logger.printLog(
                                "   Car " + thisCar.getCarNum() + " priority -1" + " to change to a lower lane",
                                Logger.TRACE);
                    } else {
                        // The car shouldn't switch to a lower lane, set priority to stay
                        thisCar.setShouldChangeTo(0);
                        tempPriority = (int) Math.round(reqAvgSpeed - LANE1_SPEED);
                        thisCar.setChangePriority(tempPriority);
                        Logger.printLog("   Car " + thisCar.getCarNum() + " priority " + tempPriority
                                + " to stay in this lane", Logger.TRACE);
                    }
                    break;
                
                case 1:
                    if (reqAvgSpeed <= 70) {
                        thisCar.setShouldChangeTo(2);
                        tempPriority = -1;
                        thisCar.setChangePriority(tempPriority);
                        Logger.printLog("   Car " + thisCar.getCarNum() + " priority " + tempPriority
                                + " to change to a lower lane", Logger.TRACE);
                        
                    } else {
                        thisCar.setShouldChangeTo(1);
                        tempPriority = (int) Math.round(reqAvgSpeed - LANE1_SPEED);
                        thisCar.setChangePriority(tempPriority);
                        Logger.printLog("   Car " + thisCar.getCarNum() + " priority " + tempPriority
                                + " to stay in this lane", Logger.TRACE);
                    }
                    break;
                
                case 2:
                    thisCar.setShouldChangeTo(2);
                    thisCar.setChangePriority(-1);
                    Logger.printLog("   Car " + thisCar.getCarNum() + " priority -1" + " to change to a lower lane",
                            Logger.TRACE);
                    
                    break;
            }
            
        } else {
            // The car will not reach the destination before the required time
            int tempPriority = 0;
            // Tell the car which lanes to change to and set an Priority based on
            // the difference between required speed and current speed.
            switch (thisCar.lane) {
                case 0:
                    thisCar.setShouldChangeTo(0);
                    tempPriority = (int) Math.round(reqAvgSpeed - LANE0_SPEED);
                    thisCar.setChangePriority(tempPriority);
                    Logger.printLog("   Car " + thisCar.getCarNum() + " priority " + tempPriority
                            + " to move up a lane", Logger.TRACE);
                    break;
                
                case 1:
                    thisCar.setShouldChangeTo(0);
                    tempPriority = (int) Math.round(reqAvgSpeed - LANE1_SPEED);
                    thisCar.setChangePriority(tempPriority);
                    Logger.printLog("   Car " + thisCar.getCarNum() + " priority " + tempPriority
                            + " to move up a lane", Logger.TRACE);
                    break;
                
                case 2:
                    thisCar.setShouldChangeTo(1);
                    tempPriority = (int) Math.round(reqAvgSpeed - LANE2_SPEED);
                    thisCar.setChangePriority(tempPriority);
                    Logger.printLog("   Car " + thisCar.getCarNum() + " priority " + tempPriority
                            + " to stay in this lane, and it will be late", Logger.TRACE);
                    break;
            }
            // TODO: Changing to a faster lane should always take priority, unless
            // changing to a slower lane opens up a spot for the faster car to change to
        }
        
        // The car has to change lanes at some point, look for openings
        if (thisCar.getShouldChangeTo() != thisCar.lane) {
            Logger.printLog("   Car " + thisCar + " has to change lane at some point", Logger.TRACE);
            
            // We have received a command from a higher priority car
            if (thisCar.recMsg.isActive()) {
                Logger.printLog("   Car " + thisCar.getCarNum() + " has received a message", Logger.TRACE);
                // TODO: Follow the command
                
                // No command to follow
            } else {
                // If the car can change lanes
                if (canCarChange(thisCar.getShouldChangeTo(), thisCar)) {
                    // We will be changing set carCanChange to false (only matters for this one UpdateCars())
                    thisCar.carCanChangeLanes = false;
                    thisCar.setNewLane(thisCar.getShouldChangeTo());
                    Logger.printLog("   Car " + thisCar + " changing to lane " + thisCar.getShouldChangeTo(),
                            Logger.DEBUG);
                    // If the car can't change lanes, try to message other cars
                } else {
                    
                }
            }
            
            // TODO: No change required, check if it is okay to change in case other cars want it to
        } else {
            Logger.printLog("   Car " + thisCar.getCarNum()
                    + " does not have to change lanes, currently exiting AI Loop", Logger.TRACE);
            
            // Set carCanChange to false if necessary in here
            
        }
    }
    
    /**
     * Check all the cars for a collision
     */
    private static void collisionLoop() {
        Logger.printLog("Checking Collision", Logger.TRACE);
        for (int i = 0; i < getCarArray().size(); i++) {
            // We check collision here
            if (getCarArray().size() > 1) {
                int l = i + 1;
                
                while (l < getCarArray().size() && l < (i + 4)) {
                    collision = checkCollision(getCarArray().elementAt(i).getCar(), getCarArray().elementAt(l).getCar());
                    
                    if (collision) {
                        try {
                            throw new Exception();
                        } catch (Exception e) {
                            Logger.printLog("COLLISION, Car: " + i + " and Car " + l, Logger.DEBUG);
                        }
                        
                    }
                    
                    if ((l + 1) >= getCarArray().size()) break;
                    l++;
                }
            }
        }
    }
    
    /**
     * Check two individual cars for a collision
     * 
     * @param car1
     *            - First car to check
     * @param car2
     *            - Second car to check
     * @return Whether or not the cars are colliding
     */
    public static boolean checkCollision(Rectangle2D car1, Rectangle2D car2) {
        double car1Center = car1.getCenterX();
        double car2Center = car2.getCenterX();
        
        double car1Length = car1.getWidth();
        double car2Length = car2.getWidth();
        
        if (Math.abs(car2Center - car1Center) > (car1Length + car2Length)) {
            return false;
        } else {
            if (car2.intersects(car1.getBounds2D())) {
                return true;
            } else {
                return false;
            }
        }
    }
    
    /**
     * Check if cars need to be created, and create them if it has occurred randomly
     */
    private static void createCars() {
        Random randNum = new Random();
        int chanceToRun = 0;
        
        // if lane was filled in last .7 seconds, do not fill
        if (laneFull0 == (REQ_CREATE_TIME * Main.UPDATE_RATE)) {
            // Chance car will be created. 1/150 chance per update
            chanceToRun = randNum.nextInt((int) (AVG_CREATE_TIME * Main.UPDATE_RATE));
            if (chanceToRun == 1) {
                Logger.printLog("Car Created in Lane " + 0 + " Car Num " + carnum, Logger.TRACE);
                new Car(carnum, 0, 0, 90);
                carnum++;
                laneFull0 = 0;
            }
            // if this triggers, lane was filled within last 1.5 seconds
        } else {
            laneFull0++;
        }
        
        if (laneFull1 == (REQ_CREATE_TIME * Main.UPDATE_RATE)) {
            chanceToRun = randNum.nextInt((int) (AVG_CREATE_TIME * Main.UPDATE_RATE));
            if (chanceToRun == 1) {
                Logger.printLog("Car Created in Lane " + 1 + " Car Num " + carnum, Logger.TRACE);
                new Car(carnum, 0, 1, 80);
                carnum++;
                laneFull1 = 0;
            }
        } else {
            laneFull1++;
        }
        
        if (laneFull2 == (REQ_CREATE_TIME * Main.UPDATE_RATE)) {
            chanceToRun = randNum.nextInt((int) (AVG_CREATE_TIME * Main.UPDATE_RATE));
            if (chanceToRun == 1) {
                Logger.printLog("Car Created in Lane " + 2 + " Car Num " + carnum, Logger.TRACE);
                new Car(carnum, 0, 2, 70);
                carnum++;
                laneFull2 = 0;
            }
        } else {
            laneFull2++;
        }
    }
    
    /**
     * Do a binary search for the car needed in the XPos array
     * 
     * @param thisCar
     *            The car to search for
     * @return The place in the XPos array of the car searched for
     */
    private static synchronized int binarySearchXPos(Car thisCar) {
        // Start with the whole array
        int first = 0;
        int upTo = getCarArray().size() - 1;
        int midPoint = (first + upTo) / 2; // Mid point of the current search area
        
        // Loop through, looking for equal x positions
        while (first < upTo) {
            midPoint = (first + upTo) / 2;
            
            // If true, we have not found the car in the Vector
            if (first > upTo) {
                return -1;
            }
            
            if (thisCar.xPos < getCarArray().elementAt(midPoint).xPos) {
                upTo = midPoint - 1; // Check first half
            } else if (thisCar.xPos > getCarArray().elementAt(midPoint).xPos) {
                first = midPoint + 1; // Check second half
            } else {
                break; // Found the Car in the Array
            }
        }
        
        // Check that the car we got equals the one we want
        if (thisCar.getCarNum() == getCarArray().elementAt(midPoint).getCarNum()) {
            return midPoint;
        }
        
        // If that wasn't it, check surrounding Don't want to go out of bounds
        if (midPoint < getCarArray().size()) {
            // Check car in front of our car to find our car
            if (thisCar.getCarNum() == getCarArray().elementAt(midPoint + 1).getCarNum()) {
                return midPoint + 1;
            }
        }
        
        // Don't want to go out of bounds
        if (midPoint > 0) {
            // Check car behind our car to find our car
            if (thisCar.getCarNum() == getCarArray().elementAt(midPoint - 1).getCarNum()) {
                return midPoint - 1;
            }
        }
        
        // Failed to find it
        return -1;
    }
    
    private static boolean canCarChange(int lane, Car thisCar) {
        int thisElement = -1;
        int carBehindElem = -1;
        int carAheadElem = -1;
        
        // Do a custom binary search to search for our own element in the XPos sorted Vector
        thisElement = binarySearchXPos(thisCar);
        
        int i = thisElement - 1;
        // Find the car in the target lane behind this one
        while (carBehindElem == -1 && i >= 0) {
            
            // If the car we're looking at is in the lane we want to move in to
            if (getCarArray().elementAt(i).lane == thisCar.getShouldChangeTo()) {
                carBehindElem = i;
                break;
            }
            i--;
            
        }
        
        // Find the car in the target lane in front of this one, start searching ahead of this car
        i = thisElement + 1;
        while (carAheadElem == -1 && i <= getCarArray().size() - 1) {
            
            // If the car is in the lane we're looking to look in to
            if (getCarArray().elementAt(i).lane == thisCar.getShouldChangeTo()) {
                // Set the element as the car ahead of the location
                carAheadElem = i;
                break;
            }
            
            i++;
        }
        
        // If there is no car behind this one, or there is but follow distance is okay
        if (carBehindElem == -1 || getCarArray().elementAt(carBehindElem).xPos - thisCar.xPos >= MIN_BEHIND_CHANGE_DIST) {
            
            // If there is no car ahead of this one, or there is but follow distance is okay
            if (carAheadElem == -1
                    || getCarArray().elementAt(carAheadElem).xPos - thisCar.xPos >= MIN_AHEAD_CHANGE_DIST) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Compare two cars in the XPos Array
     */
    private static Comparator<Car> xPosCompare = new Comparator<Car>() {
        @Override
        public int compare(Car car1, Car car2) {
            if (car1.getCar().getCenterX() > car2.getCar().getCenterX())
                return 1;
            else if (car1.getCar().getCenterX() < car2.getCar().getCenterX())
                return -1;
            else
                return 0;
        }
    };
    
    /**
     * Compare two cars in the Priority array
     */
    private static Comparator<Car> priorityCompare = new Comparator<Car>() {
        @Override
        public int compare(Car car1, Car car2) {
            // Higher Priority
            if (car1.getChangePriority() > car2.getChangePriority())
                return 1;
            // Lower Priority
            else if (car1.getChangePriority() < car2.getChangePriority())
                return -1;
            // Equal Priority
            else {
                // Farther to the right on the road
                if (car1.getCar().getCenterX() > car2.getCar().getCenterX())
                    return 1;
                // Farther to the left on the road
                else if (car1.getCar().getCenterX() < car2.getCar().getCenterX())
                    return -1;
                // Equal position on the road
                else
                    return 0;
            }
        }
    };
    
    public synchronized static Vector<Car> getCarArray() {
        try {
            return carArray;
        } catch (Exception e) {
            e.printStackTrace();
            return carArray;
        }
    }
    
    public synchronized static void setCarArray(Vector<Car> carArray) {
        CarControl.carArray = carArray;
    }
    
    public static synchronized Vector<Car> getPriorityArray() {
        return priorityArray;
    }
    
    public static synchronized void setPriorityArray(Vector<Car> priorityArray) {
        CarControl.priorityArray = priorityArray;
    }
}
