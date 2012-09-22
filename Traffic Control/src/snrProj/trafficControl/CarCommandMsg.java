package snrProj.trafficControl;
/**
 * 
 */

/**
 * @author jfischer
 *
 */
public class CarCommandMsg {
	public static final int COMMAND_CHANGE_LANE_DOWN = 1;
	public static final int COMMAND_CHANGE_LANE_UP = 2;
	public static final int COMMAND_SLOW_DOWN = 3;
	public static final int COMMAND_SPEED_UP = 4;
	public static final int COMMAND_ACK = 5;
	public static final int COMMAND_NAK = 6;
	
	private boolean active;
	private int fromCarArrayNum;
	private int fromPriorityQueueNum;
	private int fromPriority;
	private int commandType;
	
	public CarCommandMsg() {
		this.active = false;
		this.fromCarArrayNum = -1;
		this.fromPriorityQueueNum = -1;
		this.fromPriority = -1;
		this.commandType = -1;
	}
	
	/**
	 * @param fromCarArrayNum - The number in the XPos Array of the car it was sent from
	 * @param fromPriorityQueueNum - The number in the Priority Array of the car it was sent from
	 * @param fromPriority - The priority of the car it was sent from
	 * @param commandType - The command sent
	 */
	public CarCommandMsg(int fromCarArrayNum, int fromPriorityQueueNum, int fromPriority, int commandType) {
		this.active = true;
		this.fromCarArrayNum = fromCarArrayNum;
		this.fromPriorityQueueNum = fromPriorityQueueNum;
		this.fromPriority = fromPriority;
		this.commandType = commandType;
	}
	
	public synchronized int getFromCarArrayNum() {
		return fromCarArrayNum;
	}

	public synchronized void setFromCarArrayNum(int fromCarArrayNum) {
		this.fromCarArrayNum = fromCarArrayNum;
	}
	
	public synchronized int getFromPriorityQueueNum() {
		return fromPriorityQueueNum;
	}

	public synchronized void setFromPriorityQueueNum(int fromPriorityQueueNum) {
		this.fromPriorityQueueNum = fromPriorityQueueNum;
	}
	
	public synchronized int getFromPriority() {
		return fromPriority;
	}

	public synchronized void setFromPriority(int fromPriority) {
		this.fromPriority = fromPriority;
	}
	
	public synchronized int getCommandType() {
		return commandType;
	}

	public synchronized void setCommandType(int commandType) {
		this.commandType = commandType;
	}
	
	public synchronized boolean isActive() {
		return active;
	}

	public synchronized void setActive(boolean active) {
		this.active = active;
	}

}
