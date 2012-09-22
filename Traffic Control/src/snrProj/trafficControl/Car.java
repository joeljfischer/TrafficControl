package snrProj.trafficControl;

import java.awt.geom.Rectangle2D;

/**
 *  Some timer code adapted from http://java.sun.com/products/jfc/tsc/articles/timer/
 */

/**
 * @author jfischer
 * 
 *         This really should have a much better physics simulator with true forces. For now, we will use a basic
 *         physics simulator assuming acceleration and braking without dealing with forces and resistances. For now, we
 *         also assume instant acceleration.
 */
public class Car {
	private static double WAIT_AFTER_LANE_CHANGE = 1.5; //0.0 min - 3.0 max
	private boolean firstRun = true;
	
    private double speed; // Miles per Hour
    private double acc; // Feet per Second per Second (0-60 in Seconds, so
                        // 60mph/s)
    private long carNum;
    private double length; // Ft.
    private double width; // Ft.
    public double angle; // Where the car is pointing (degree) 0 degree ->,
                         // counter-clockwise
    public int lane;
    public double xPos; // Ft.
    public double yPos;
    private long lastUpdatedNano;
    private Rectangle2D car = null;
    
    // Movement values
    private int segment = 0; // Use to compute if car has moved to new road segment
    private int segmentNew = 0;
    public double percenttraveled = 0;
    public double xPrev;
    public double yPrev;
    
    // lane change values
    private double totalPercentChanged = 0;
    private int newLane = -1;
    
    // Time of arrival values
    private double reqArrival = 0; // Seconds to needed arrival
    private double lengthTrip = 0; // Total distance to travel on the trip
    private double lengthToTravel = 0; // Distance remaining
    
    // AI Variables (prevents multiple calculations)
    private double timeWaitBeforeChange = 0.0; //Used for preventing lane switches when it shouldn't
    private int shouldChangeTo = -1; // Lane car wants to change to
    private int changePriority = 0; // Higher = greater, lower = less
    public CarCommandMsg recMsg = null; //received message
    public CarCommandMsg sendMsg = null; //sent message
    public boolean carCanChangeLanes = false; //car can switch lanes
    
    /**
     * Constructor
     * 
     * @param carNum A car index number used for reference (possibly unnecessary)
     * @param xPos The starting X Position
     * @param lane The starting lane
     * @param startSpeed The starting speed of the car (acceleration is 0)
     * @param startAngle The starting angle of the car (0 is X axis)
     */
    public Car(long carNum, double xPos, int lane, double startSpeed) {
        // TODO: Initiate a random generation of a car.
        // For now it will only create a static type of car (2012 Ford Focus via
        // Bing.com)
        
        /*
         * this.maxAcc = 8; //Actual Focus 2012 is 7.6s, assume 11s 0-88feet per second makes 8 f/s/s this.maxBrake =
         * 19; //Lower than the lowest I could find. In f/s/s. this.maxSpeed = 138; //mph
         */
        
        this.length = 14.875; // Length in ft.
        this.width = 5.9888; // Width in ft.
        this.speed = startSpeed;
        this.carNum = carNum;
        this.xPos = xPos;
        this.lane = lane;
        this.yPos = 400 + lane * 12;
        this.reqArrival = detReqArrival();
        this.lastUpdatedNano = Math.abs(System.nanoTime());
        this.recMsg = new CarCommandMsg();
        this.sendMsg = new CarCommandMsg();
        
        // Create the visual car representation
        this.car = new Rectangle2D.Double(xPos, yPos, length, width);
        // Add this object into the car array
        CarControl.getPriorityArray().add(this);
        CarControl.getCarArray().add(0, this);
        Logger.printLog("Car " + this.carNum + " added to XPos and Priority Arrays, Rect created", Logger.DEBUG);
    }
    
    public void destroy() {
        this.car = null;
        CarControl.getCarArray().remove(this);
        CarControl.getPriorityArray().remove(this);
        Logger.printLog("Car " + this.carNum + " destroyed", Logger.DEBUG);
    }
    
    /**
     * @param deltaSeconds - The amount of time that has passed since the last update
     * @param override - In override mode, we are changing lanes or speeds
     */
    public void updatePosition(double deltaSeconds, boolean override) {
        maintainSpeed(deltaSeconds);
        
        this.speed += this.acc;
        
        double distance = mphToFps(speed) * deltaSeconds; // distance traveled
                                                          // since last update
        
        // x points used to calculate the road are dependent on the segment
        int point1x = 0 + 1056 * this.segment;
        int point2x = 352 + 1056 * this.segment;
        int point3x = 704 + 1056 * this.segment;
        int point4x = 1056 + 1056 * this.segment;
        
        // y points are based on bezier curve formula
        double point3y = 400 + (Main.endPoints[this.segment]) / 2;
        double point4y = 400 + (Main.endPoints[this.segment]);
        double point1y;
        double point2y;
        
        // percenttraveled is a sum of the percents the car travels on a segment
        // during each update
        double newPercenttraveled = distance / Gui.roadLength[this.segment];
        percenttraveled = percenttraveled + newPercenttraveled;
        
        // the y points in the first segment need special code since point2y is
        // not based on a previous segment
        if (this.segment == 0) {
            point1y = 400;
            point2y = 400 + (Main.endPoints[0]) / 2;
        } else {
            point1y = 400 + (Main.endPoints[this.segment - 1]);
            point2y = ((Main.endPoints[this.segment - 1]) / 2) + (Main.endPoints[this.segment - 1] + 400); // prevPoint3y +
                                                                                                 // prevPoint4y + 400
        }
        
        // main formula to determine a car's x and y position formula uses the
        // percent of the way traveled on the current segment to determine what
        // the positions are
        this.xPos = Math.abs(Math.pow((1 - this.percenttraveled), 3)) * point1x + 3
                * Math.pow((1 - this.percenttraveled), 2) * this.percenttraveled * point2x + 3
                * Math.abs((1 - this.percenttraveled)) * Math.pow(this.percenttraveled, 2) * point3x
                + Math.abs(Math.pow(this.percenttraveled, 3)) * point4x;
        this.yPos = Math.abs(Math.pow((1 - this.percenttraveled), 3)) * point1y + 3
                * Math.pow((1 - this.percenttraveled), 2) * this.percenttraveled * point2y + 3
                * Math.abs((1 - this.percenttraveled)) * Math.pow(this.percenttraveled, 2) * point3y
                + Math.abs(Math.pow(this.percenttraveled, 3)) * point4y;
        
        // Checks to see if the previous code moved the car into a new segment
        this.segment = (int) ((this.xPos) / 1056);
        
        // Fix overlap that occurs when car changes road segments
        if (this.segment != segmentNew) {
            int point1xNew = 0 + 1056 * this.segment;
            int point2xNew = 352 + 1056 * this.segment;
            int point3xNew = 704 + 1056 * this.segment;
            int point4xNew = 1056 + 1056 * this.segment;
            double point1yNew = 400 + (Main.endPoints[this.segment - 1]);
            double point2yNew = ((Main.endPoints[this.segment - 1]) / 2) + (Main.endPoints[this.segment - 1] + 400);
            double point3yNew = 400 + (Main.endPoints[this.segment]) / 2;
            double point4yNew = 400 + (Main.endPoints[this.segment]);
            
            // distance = the total distance traveled during the update - the
            // distance the car traveled on the segment it just crossed out of
            double tempDistance = Math.sqrt(Math.pow((point1xNew - xPrev), 2)
                    + Math.pow((point1yNew - yPrev), 2));
            distance = distance - tempDistance;
            if (distance < 0)
            {
            	distance = 1;
            }
            
            // calculates the percent traveled on the new segment, if it reaches
            // the end of the road, destroy the car
            try {
                this.percenttraveled = distance / Gui.roadLength[this.segment];
            } catch (ArrayIndexOutOfBoundsException aioobe) {
                this.destroy();
                return;
            }
            
            // recalculate x and y positions using the new segment and the new
            // percent traveled
            this.xPos = Math.abs(Math.pow((1 - this.percenttraveled), 3)) * point1xNew + 3
                    * Math.pow((1 - this.percenttraveled), 2) * this.percenttraveled * point2xNew + 3
                    * Math.abs((1 - this.percenttraveled)) * Math.pow(this.percenttraveled, 2) * point3xNew
                    + Math.abs(Math.pow(this.percenttraveled, 3)) * point4xNew;
            this.yPos = Math.abs(Math.pow((1 - this.percenttraveled), 3)) * point1yNew + 3
                    * Math.pow((1 - this.percenttraveled), 2) * this.percenttraveled * point2yNew + 3
                    * Math.abs((1 - this.percenttraveled)) * Math.pow(this.percenttraveled, 2) * point3yNew
                    + Math.abs(Math.pow(this.percenttraveled, 3)) * point4yNew;
        }
        this.segmentNew = this.segment;
        
        // Uses the lane to set the car into the proper position
        this.yPos += this.lane * 12;
        
        if (this.getNewLane() != -1) {
            this.changeLane(this.getNewLane(), deltaSeconds);
        }
        
        // Orient car
        // Calculate car orientation in radians
        if (firstRun == true)
        {
        	this.angle = 0.0;
        	firstRun = false;
        }
        else
        {
        	this.angle = Math.atan2((this.yPos - yPrev), (this.xPos - xPrev));
        }
        
        
        // Move the car to the proper position onscreen by setting it's Java2D rectangle
        this.car.setRect(this.xPos, this.yPos, this.length, this.width);
        
        // Values to calculate angle
        xPrev = this.xPos;
        yPrev = this.yPos;
        Logger.printLog("Car " + this.carNum + ", Speed: " + this.speed + ", Acc: " + this.acc + ", Dist Travelled "
                + distance + " delta secs: " + deltaSeconds, Logger.DEBUG);
    }
    
    /**
     * @param toLane - The lane to move to
     * @param deltaSeconds - The amount of time that has passed since the last update
     */
    public void changeLane(int toLane, double deltaSeconds) {
        // We want a 3 second change
        int temp;
        this.totalPercentChanged += (1.0 / ((1 / deltaSeconds) * 3.0));
        if (toLane < this.lane) {
            temp = -1;
        } else {
            temp = 1;
        }
        
        this.yPos += ((double) temp * this.totalPercentChanged * 12.0);
        
        Logger.printLog("Car " + this.carNum + " changing lane from " + this.lane + " to " + toLane + " Percent ch: "
                + this.totalPercentChanged, Logger.TRACE);
        
        // We have finished changing lanes
        if (this.totalPercentChanged >= 1) {
        	this.setTimeWaitBeforeChange(3 - WAIT_AFTER_LANE_CHANGE); //don't allow a car to change right after changing
            this.setNewLane(-1);
            this.totalPercentChanged = 0;
            this.lane = toLane;
            
            Logger.printLog("Car " + this.carNum + " finished changing lanes to " + this.lane, Logger.DEBUG);
        }
    }
    
    /**
     * @param lane - The lane which you want to maintain the speed of.
     * 
     * This function will accelerate to or decelerate to the speed the car should be going while maintaining
     * a minimum distance from the next car.
     */
    private void maintainSpeed(double deltaSeconds) {
        int targetSpeed;
        int tempLane = -1;
        // If we're changing lanes, we want to speed up or slow down
        // appropriately
        if (this.getNewLane() != -1) {
            tempLane = this.getNewLane();
        } else {
            tempLane = this.lane;
        }
        
        switch (tempLane) {
            case 0:
                targetSpeed = CarControl.LANE0_SPEED; // mph
                break;
            case 1:
                targetSpeed = CarControl.LANE1_SPEED;
                break;
            case 2:
                targetSpeed = CarControl.LANE2_SPEED;
                break;
            default:
                targetSpeed = 0;
        }
        double speedDiff = targetSpeed - this.speed;
        
        // This setup will take 2.48242 seconds to go from 10 mph under required
        // to required
        if (speedDiff >= 10) {
            this.acc = fpsToMphs(8.0, deltaSeconds);
        } else if (speedDiff >= 7 && speedDiff < 10) {
            this.acc = fpsToMphs(7.0, deltaSeconds);
        } else if (speedDiff >= 5 && speedDiff < 7) {
            this.acc = fpsToMphs(6.7, deltaSeconds);
        } else if (speedDiff >= 3 && speedDiff < 5) {
            this.acc = fpsToMphs(6.0, deltaSeconds);
        } else if (speedDiff >= 1 && speedDiff < 3) {
            this.acc = fpsToMphs(5.0, deltaSeconds);
        } else if (speedDiff > 0.2 && speedDiff < 1) {
            this.acc = fpsToMphs(3.0, deltaSeconds);
        } else if (speedDiff > 0.05 && speedDiff <= 0.2) {
            this.acc = fpsToMphs(0.05, deltaSeconds);
        } else if (speedDiff > 0.005 && speedDiff <= 0.05) {
            this.acc = fpsToMphs(0.007, deltaSeconds);
        }
        
        // This setup will take 1.2 seconds to go from 10 mph over required to
        // required.
        if (speedDiff <= -10) {
            this.acc = fpsToMphs(-17.0, deltaSeconds);
        } else if (speedDiff <= -7 && speedDiff >= -4) {
            this.acc = fpsToMphs(-13.0, deltaSeconds);
        } else if (speedDiff <= -4 && speedDiff >= -2) {
            this.acc = fpsToMphs(-7.0, deltaSeconds);
        } else if (speedDiff < 0.2 && speedDiff >= -2) {
            this.acc = fpsToMphs(-3.5, deltaSeconds);
        } else if (speedDiff <= -0.2 && speedDiff >= -0.05) {
            this.acc = fpsToMphs(-0.05, deltaSeconds);
        } else if (speedDiff <= -0.05 && speedDiff >= -0.005) {
            this.acc = fpsToMphs(-0.007, deltaSeconds);
        }
        
        // TODO: Emergency section, we're coming up on a car WAY too fast and WAY too
        // close, possibly by detecting distance to a car in front? We'd need to hold on
        // a fast deceleration (such as -18.0) for a longer period, rather than a gradual
        // decline in speed.
        // TODO: Emergency section cont'd, rapid acceleration if possible, to help
        // the car behind that is slowing?
    }
    
    private double detReqArrival() {
        int i = 0;
        double reqArrival = 0;
        double minArrival = 0;
        double maxArrival = 0;
        
        // For all active roadlength segments, add them into a total length
        while (i < Gui.TOTAL_ROAD_SEGMENTS && Gui.roadLength[i] != 0) {
            this.lengthTrip += Gui.roadLength[i]; // TODO: Adapt to allow multiple entrances/exits
            i++;
        }
        
        // Minimum arrival is total length in miles, divided by max speed, converted to seconds
        minArrival = (((this.lengthTrip / 5280) / 90) * 60) * 60;
        maxArrival = (((this.lengthTrip / 5280) / 70) * 60) * 60;
        
        // Find a random required arrival time between min and max possible
        reqArrival = minArrival + (Math.random() * (maxArrival - minArrival));
        
        Logger.printLog("Car " + this.carNum + " Required Arrival " + reqArrival + "secs", Logger.DEBUG);
        
        return reqArrival; // seconds
    }
    
    public double getEstArrival(int lane) {
        double estArrival = 0;
        // Determine the length still to be traveled (feet)
        this.lengthToTravel = this.lengthTrip * ((100 - getPercentTrip()) / 100);
        double laneSpeed = 0;
        // Determine the lane's speed
        switch (lane) {
            case 0:
                laneSpeed = CarControl.LANE0_SPEED;
                break;
            case 1:
                laneSpeed = CarControl.LANE1_SPEED;
                break;
            case 2:
                laneSpeed = CarControl.LANE2_SPEED;
                break;
            default:
                try {
                    throw new Exception();
                } catch (Exception e) {
                    System.err.println("Error, Car " + this.carNum + " lane out of bounds: " + lane);
                }
        }
        
        // Convert lengthToTravel to miles, divide by lanespeed to find estMph, convert to seconds
        estArrival = (((this.lengthToTravel / 5280) / laneSpeed) * 60) * 60;
        
        Logger.printLog("Car " + this.carNum + " length to travel " + this.lengthToTravel + " est arrival "
                + estArrival, Logger.TRACE);
        
        return estArrival; // Seconds
    }
    
    public synchronized double getPercentTrip() {
        double percentTrip = 0;
        double firstPercent = 0;
        // Find the % of all previous segments traveled
        if (this.segment == 0) {
            firstPercent = 0;
        } else {
            firstPercent = ((this.segment) / Gui.TOTAL_ROAD_SEGMENTS) * 100;
        }
        // Refactor the current percent into an overall percent, add to previous segments
        percentTrip = firstPercent + (this.percenttraveled / Gui.TOTAL_ROAD_SEGMENTS);
        
        return percentTrip;
    }
    
    /**
     * Feet per Second to Mile per Hour per Second based on time since last update
     */
    private double fpsToMphs(double fps, double deltaSeconds) {
        return fpsToMph(fps) * deltaSeconds;
    }
    
    private double fpsToMph(double fps) {
        return fps / 1.4666666667;
    }
    
    private double mphToFps(double mph) { // Calculates feet per sec.
        return mph * 1.4666666667;
    }
    
    public void setLastUpdatedNano(long lastUpdatedNano) {
        this.lastUpdatedNano = lastUpdatedNano;
    }
    
    public long getLastUpdatedNano() {
        return lastUpdatedNano;
    }
    
    public long getCarNum() {
        return carNum;
    }
    
    public Rectangle2D getCar() {
        return car;
    }
    
    public synchronized void setNewLane(int newLane) {
        this.newLane = newLane;
    }
    
    public synchronized int getNewLane() {
        return newLane;
    }
    
    public synchronized double getReqArrival() {
        return reqArrival;
    }
    
    public synchronized void setReqArrival(double reqArrival) {
        this.reqArrival = reqArrival;
    }
    
    public synchronized double getLengthTrip() {
        return lengthTrip;
    }
    
    public synchronized void setLengthTrip(double lengthTrip) {
        this.lengthTrip = lengthTrip;
    }
    
    public synchronized double getLengthToTravel() {
        return lengthToTravel;
    }
    
    public synchronized void setLengthToTravel(double lengthToTravel) {
        this.lengthToTravel = lengthToTravel;
    }
    
    public synchronized int getShouldChangeTo() {
        return shouldChangeTo;
    }
    
    public synchronized void setShouldChangeTo(int shouldChangeTo) {
        this.shouldChangeTo = shouldChangeTo;
    }
    
    public int getChangePriority() {
        return changePriority;
    }
    
    public void setChangePriority(int changePriority) {
        this.changePriority = changePriority;
    }

	public double getTimeWaitBeforeChange() {
		return timeWaitBeforeChange;
	}

	public void setTimeWaitBeforeChange(double tempTime) {
		this.timeWaitBeforeChange = tempTime;
	}
    
}