package template;

//the list of imports
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import logist.LogistSettings;
import logist.agent.Agent;
import logist.behavior.CentralizedBehavior;
import logist.config.Parsers;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 *
 */
@SuppressWarnings("unused")
public class CentralizedTemplate implements CentralizedBehavior {

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private long timeout_setup;
    private long timeout_plan;
    
    private static Random random = new Random();

    @Override
    public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config\\settings_default.xml");
        } catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }

        // the setup method cannot last more than timeout_setup milliseconds
        timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
        // the plan method cannot execute more than timeout_plan milliseconds
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);

        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();
        
        Solution2 s = selectInitialSolution(vehicles, tasks);

        List<Plan> plans = plans(finalSolution(s, 10), vehicles);

        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in " + duration + " milliseconds.");

        return plans;
    }

    private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
        City current = vehicle.getCurrentCity();
        Plan plan = new Plan(current);

        for (Task task : tasks) {
            // move: current city => pickup location
            for (City city : current.pathTo(task.pickupCity)) {
                plan.appendMove(city);
            }

            plan.appendPickup(task);

            // move: pickup location => delivery location
            for (City city : task.path()) {
                plan.appendMove(city);
            }

            plan.appendDelivery(task);

            // set current city
            current = task.deliveryCity;
        }
        return plan;
    }
    
// ----------------------------------------------------- BEGINNING OF OUR IMPLEMENTATION ----------------------------------------------------------
    
    private static List<Plan> plans(Solution2 finalS, List<Vehicle> vehicles) {
        List<Plan> plans = new ArrayList<Plan>();
        
        for (Vehicle v : vehicles) {
            City previous = v.getCurrentCity();
            Plan plan = new Plan(previous);
            
            for (TaskAugmented t : finalS.get(v)) {
                for (City c : previous.pathTo(t.city())) {
                    plan.append(new Action.Move(c));
                }
                
                if (t.isPickup()) {
                    plan.append(new Action.Pickup(t.task()));
                } else {
                    plan.append(new Action.Delivery(t.task()));
                }
                
                previous = t.city();
            }
            
            plans.add(plan);
        }
        
        return plans;
    }
    
    /**
     * Creates the initial solution by putting every task in the biggest vehicle given. Every task will be picked up and delivered before the next
     */
    private static Solution2 selectInitialSolution(List<Vehicle> vehicles, TaskSet tasks) {
        Vehicle biggest = null;
        double bestCapacity = 0;
        
        Map<Vehicle, List<TaskAugmented>> plan = new HashMap<Vehicle, List<TaskAugmented>>();
        
        for(Vehicle v : vehicles) {
            plan.put(v, new LinkedList<TaskAugmented>());
            
            double currentCapacity = v.capacity();
            if (currentCapacity > bestCapacity) {
                biggest = v;
                bestCapacity = currentCapacity;
            }
        }
        
        // put in biggest vehicle
        for (Task task : tasks) {
            TaskAugmented tp = new TaskAugmented(task, true);
            TaskAugmented td = new TaskAugmented(task, false);
            
            if (task.weight > bestCapacity) {
                return null;
            }

            plan.get(biggest).add(tp);
            plan.get(biggest).add(td);            
        }
        
        return new Solution2(plan);
    }
    
    private static Solution2 finalSolution(Solution2 initS, int iter) {
        Solution2 returnS = initS;
        
        for (int i = 0; i < iter; ++i) {
            returnS = chooseNeighbors(returnS, 0.3);
        }
        
        return returnS;
    }
    
    /**
     *  Computed the best solution from vehicle changes and order changes, return this best solutoin according to probability
     */
    private static Solution2 chooseNeighbors(Solution2 s, double pickProb) {
        Vehicle v; 
        
        do {
            v = s.vehicles().get(random.nextInt(s.vehicles().size()));
        } while (s.get(v).isEmpty());
        
        List<Solution2> changedVehicleList = changeVehicle(s, v);      
          
        List<TaskAugmented> vTasks = s.get(v);        
        Task t = vTasks.get(random.nextInt(vTasks.size())).task();
        
        List<Solution2> changedEverythingList = changeOrder(s, v, t);
        changedEverythingList.addAll(changedVehicleList);
        
        Solution2 best = getBest(changedEverythingList);
        
        return random.nextDouble() < pickProb ? best : s;
    }
    
    /**
     * Selects the best solution amongst the given ones regarding the cost of a solution
     */
    private static Solution2 getBest(List<Solution2> sList) {
        double bestCost = Double.POSITIVE_INFINITY;
        Solution2 bestS = null;
        
        for (Solution2 s : sList) {
            double cost = 0;
            for (Entry<Vehicle, List<TaskAugmented>> e : s.entries()) {
                cost +=  cost(s, e.getKey());
            }
            
            if (cost < bestCost) {
                bestCost = cost;
                bestS = s;
            }
        }
        
        return bestS;
    }
     
    /**
     * Cost of a solution for one vehicle
     */
    private static double cost(Solution2 s, Vehicle v) {
        double cost = 0;
        
        if (s.get(v).isEmpty()) {
            return cost;
        }

        cost += v.getCurrentCity().distanceTo(s.get(v).get(0).city());
                
        for (int i = 0; i < s.get(v).size() - 1; ++i) {
            cost += s.get(v).get(i).city().distanceTo(s.get(v).get(i + 1).city());
        }
        
        return cost;
    }
    
    /**
     * Creates a derivated solution from <code>s</code> by putting first task of <code>v1</code> to <code>v2</code>
     */
    private static List<Solution2> changeVehicle(Solution2 s, Vehicle v) {
        List<Solution2> sList = new ArrayList<Solution2>();
        if (s.get(v).isEmpty()) {
            return sList;
        }
                
        TaskAugmented tp = s.get(v).get(0); // supposed to be a pick up
        TaskAugmented td = new TaskAugmented(tp.task(), false);
        
        for (Vehicle v2 : s.vehicles()) {
            if (!v.equals(v2)) {
                if (v2.capacity() > tp.task().weight) {
                    Solution2 newS = new Solution2(s);
                    
                    newS.remove(v, tp);
                    newS.remove(v, td);
                    
                    newS.add(v2, tp);
                    newS.add(v2, td);
                    
                    sList.add(newS);
                }
            }
        }
        
        return sList;
    }
    
    private static List<Solution2> changeOrder(Solution2 s, Vehicle v, Task t) {
        List<Solution2> sList = new ArrayList<Solution2>();
        Solution2 newS = new Solution2(s);

        TaskAugmented tp = new TaskAugmented(t, true);
        TaskAugmented td = new TaskAugmented(t, false);
        
        newS.remove(v, tp);
        newS.remove(v, td);
        
        List<List<Integer>> indicesList = new ArrayList<List<Integer>>();
        
        int min = 0;
        int max = 0;
        
        int weightAcceptable = v.capacity();
        
        boolean commited = false;
        
        for (int i = 0; i < newS.get(v).size(); ++i) {
            TaskAugmented curr = newS.get(v).get(i);
            
            // finish
            if ((weightAcceptable < t.weight && !commited) || i == (newS.get(v).size() - 1)) {
                max = i;
                indicesList.add(Arrays.asList(min, max));
                commited = true;
            }
            
            if (weightAcceptable >= t.weight && commited && i != (newS.get(v).size() - 1)) {
                min = i;
                commited = false;
            }
            
            if (curr.isPickup()) {
                weightAcceptable -= curr.task().weight;
            } else {
                weightAcceptable += curr.task().weight;
            }
        }
        
        for (List<Integer> minMax : indicesList) {
            for (int i = minMax.get(0); i <= minMax.get(1); ++i) {
                for (int j = i; j <= minMax.get(1); ++j) {
                    Solution2 newNewS = new Solution2(newS);
                    
                    newNewS.add(v, i, tp);
                    newNewS.add(v, j + 1, td);   
                    
                    sList.add(newNewS);
                }
            }
        }
        
        return sList;
    }
    
    /*public Solution SelectInitialSolution(List<Vehicle> vehicles, List<Task> tasks) {
    	
    	Vehicle biggest = null;
    	double bestCapacity = 0;
    	
    	for(Vehicle v : vehicles) {
    		double currentCapacity = v.capacity();
    		if (currentCapacity > bestCapacity) {
    			biggest = v;
    			bestCapacity = currentCapacity;
    		}
    	}
    	
    	Map<TaskAugmented, Integer>       time      = new HashMap<TaskAugmented, Integer>();
    	Map<TaskAugmented, TaskAugmented> nextTask  = new HashMap<TaskAugmented, TaskAugmented>();
    	Map<Vehicle, TaskAugmented>       firstTask = new HashMap<Vehicle, TaskAugmented>();
    	Map<TaskAugmented, Vehicle>       vehicle   = new HashMap<TaskAugmented, Vehicle>();
    	
    	
    	TaskAugmented previous = null;
    	
    	for(int i = 0; i < tasks.size(); ++i) {
    		
    		Task task = tasks.get(i);
    		TaskAugmented tp = new TaskAugmented(task, true);
    		TaskAugmented td = new TaskAugmented(task, false);
    		
    		if(previous == null) {
    			firstTask.put(biggest, tp);
    		} else {
    			nextTask.put(previous, td);
    		}
    		
    		previous = td;
    		
    		
    		
    		nextTask.put(tp, td);
    		
    		if(task.weight > bestCapacity) {
    			return null;
    		}
    		
    		vehicle.put(tp, biggest);
    		vehicle.put(td, biggest);
    		
    		time.put(tp, 2*i);
    		time.put(td, 2*i + 1);
    		
    	}
    	
    	return new Solution(time, nextTask, vehicle, firstTask);
    }
    
    
    public List<Solution> chooseNeighbours(Solution s) {
    	return null; 
    }
    
    //transfers a task from v1 to v2 (as first task) or returns null if it was not possible to transfer a task
    public Solution changeVehicle(Solution s, Vehicle v1, Vehicle v2) {
    	
    	Solution newS = new Solution(s);
    	
    	TaskAugmented p =  s.firstTask.get(v1);
    	TaskAugmented d = new TaskAugmented(p.task, false);
    	
    	while(p != null && p.task.weight>v2.capacity()) {
    		p =  s.nextTask.get(p);
        	d = new TaskAugmented(p.task, false);        	
    	}
    	
    	if(p == null) {
    		newS = null;
    	} else {
    		newS = removeTask(v1, newS, p);
    		newS = removeTask(v1, newS, d);
    		newS = insertBothAfter(v2, newS, p, d, null);
    	}
    	
    	return newS;
    }
    
    
//    public Solution changeTaskOrder(Solution s, Vehicle v1) {
//    	
//    	TaskAugmented toChange = s.
//    	
//    	do {
//    		
//    		List<TaskAugmented> insertp = new ArrayList<TaskAugmented>();
//    		List<TaskAugmented> insertd = new ArrayList<TaskAugmented>();
//    		
//    		
//
//    		
//    	} while();
//    	
//    	return null;
//    }
//    
 // ----------------------- helper functions ----------------------------------------
    
    //updates time t for all tasks after 'from', including 'from', so it becomes t + toSum
    public Solution updateTime(Vehicle v, Solution s, TaskAugmented from, int toSum) {
    	Solution newS = new Solution(s);
    	newS.time.put(from, s.time.get(from) + toSum);
    	TaskAugmented t = s.nextTask.get(from);
    	while(t != null) {
    			newS.time.put(t, s.time.get(t) + toSum);
    		
    	}
    	return newS;
    }
    
    //removes a given task from a vehicle
    public Solution removeTask(Vehicle v, Solution s, TaskAugmented toRemove) {
    	
    	Solution newS = new Solution(s);
    	TaskAugmented t = newS.firstTask.get(v);
    	
    	while(!t.equals(toRemove) && !(t == null)) {
    		t = s.nextTask.get(t);
    	}
    		
    	if(t == null) {
    		return null;
    	}
    	newS.firstTask.put(v, newS.nextTask.get(t));
    	newS.vehicle.remove(t);
    	newS = updateTime(v, newS, newS.nextTask.get(t), -1);
    	
    	return newS;
    }
    	
    
    
    
    // inserts a Task into a nextTasks array. Inserts right after 'mark', or at the beginning if 'mark' == null
    public Solution insertAfter(Vehicle v, Solution s, TaskAugmented toInsert, TaskAugmented mark) {
    	Solution newS = new Solution(s);
    	if(mark == null) {
    		TaskAugmented first = s.firstTask.get(v);
    		newS.nextTask.put(toInsert, first);
    		newS.firstTask.put(v, toInsert);
    		newS.time.put(toInsert, 1);
    		newS = updateTime(v, newS, first, 1);
    	} else {
    		TaskAugmented next = s.nextTask.get(mark);
    		newS.time.put(toInsert, s.time.get(mark) + 1);
    		newS.nextTask.put(toInsert, next);
    		newS.nextTask.put(mark, toInsert);
    		newS = updateTime(v, newS, next, 1); 
    	}
    	
    	newS.vehicle.put(toInsert, v);
    	return newS;
    }
    
    //same as insertAfter but inserts 2 tasks (typically pickup and deliver) in a row
    public Solution insertBothAfter(Vehicle v, Solution s, TaskAugmented toInsert1, TaskAugmented toInsert2,  TaskAugmented mark) {
    	
    	Solution newS = new Solution(s);
    	
    	if(mark == null) {
    		TaskAugmented first = s.firstTask.get(v);
    		newS.nextTask.put(toInsert2, first);
    		newS.nextTask.put(toInsert1, toInsert2);
    		newS.firstTask.put(v, toInsert1);
    		newS.time.put(toInsert1, 1);
    		newS.time.put(toInsert2, 2);
    		newS = updateTime(v, newS, first, 2);
    	} else {
    		TaskAugmented next = s.nextTask.get(mark);
    		newS.time.put(toInsert1, s.time.get(mark) + 1);
    		newS.time.put(toInsert2, s.time.get(mark) + 2);
    		newS.nextTask.put(toInsert1, toInsert2);
    		newS.nextTask.put(toInsert2, next);
    		newS.nextTask.put(mark, toInsert1);
    		newS = updateTime(v, newS, next, 2); 
    	}
    	
    	 
		newS.vehicle.put(toInsert1, v);
		newS.vehicle.put(toInsert2, v);
    	return newS;
    }*/
    
}


