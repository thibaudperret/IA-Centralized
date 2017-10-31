package template;

import java.util.Objects;

import logist.task.Task;

public class TaskAugmented {

	Task task;
	boolean isPickup;
	
	public TaskAugmented(Task task, boolean isPickup) {
		this.isPickup = isPickup;
		this.task = task;
	}
	
	public boolean isPickup() {
		return isPickup;
	}
	
	public boolean isDeliver() {
		return !isPickup;
	}
	
	public Task task() {
		return task;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o instanceof TaskAugmented) {
			TaskAugmented that = (TaskAugmented)o;
			return ( (that.isPickup == this.isPickup) && (that.task.equals(this.task)));
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(task, isPickup);
	}
}
