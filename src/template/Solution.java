package template;

import java.util.HashMap;
import java.util.Map;

import logist.simulation.Vehicle;

public class Solution {

	Map<TaskAugmented, Integer> time;
	Map<TaskAugmented, TaskAugmented> nextTask;
	Map<Vehicle, TaskAugmented> firstTask;
	Map<TaskAugmented, Vehicle> vehicle;
	
	
	public Solution(Map<TaskAugmented, Integer> time, Map<TaskAugmented, TaskAugmented> nextTask, Map<TaskAugmented, Vehicle> vehicle, Map<Vehicle, TaskAugmented> firstTask ) {
		this.time = new HashMap<TaskAugmented, Integer>(time);
		this.nextTask = new HashMap<TaskAugmented, TaskAugmented>(nextTask);
		this.vehicle = new HashMap<TaskAugmented, Vehicle> (vehicle);
		this.firstTask = new HashMap<Vehicle, TaskAugmented>(firstTask);
	}
	
	public Solution(Solution s) {
		this.time = new HashMap<TaskAugmented, Integer>(s.time);
		this.nextTask = new HashMap<TaskAugmented, TaskAugmented>(s.nextTask);
		this.vehicle = new HashMap<TaskAugmented, Vehicle> (s.vehicle);
		this.firstTask = new HashMap<Vehicle, TaskAugmented>(s.firstTask);
	}

}
