package template;

//the list of imports
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

import logist.LogistSettings;
import logist.agent.Agent;
import logist.behavior.CentralizedBehavior;
import logist.config.Parsers;
import logist.plan.Action;
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
    
    private static Random random = new Random(/*12*/);
    private static int numberIterations = 50;
    private static double probability = 0.9d;

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

        Solution s = selectInitialSolution(vehicles, tasks);

        List<Plan> plans = planFromSolution(finalSolution(s, numberIterations, timeout_plan), vehicles);

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
    
/*----------------------------------------------------- BEGINNING OF OUR IMPLEMENTATION ---------------------------------------------------------- */
    
    /**
     * Creates a <code>Plan</code> of the Logist library from a solution
     */
    private static List<Plan> planFromSolution(Solution finalS, List<Vehicle> vehicles) {
        List<Plan> plans = new ArrayList<Plan>();
        
        System.out.println(finalS);
        double cost = 0d;
        
        for (Vehicle v : vehicles) {
            double vCost = cost(finalS, v);
            System.out.println("cost for " + v.name() + ": " + vCost);
            cost += vCost;
            
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
        
        System.out.println("TOTAL: " + cost);
        System.out.println();
        
        return plans;
    }
    
    /**
     * Creates the initial solution by putting every task in the biggest vehicle given. Every task will be picked up and delivered before the next
     */
    private static Solution selectInitialSolution(List<Vehicle> vehicles, TaskSet tasks) {
        Vehicle biggest = null;
        double bestCapacity = 0;
        
        Map<Vehicle, List<TaskAugmented>> plan = new HashMap<Vehicle, List<TaskAugmented>>();
        
        // find biggest
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
        
        return new Solution(plan);
    }
    
    /**
     * Alternate initial solution where we put tasks in every vehicle
     */
    private static Solution selectInitialSolutionBis(List<Vehicle> vehicles, TaskSet tasks) {
        int vehicle = 0;
        
        Map<Vehicle, List<TaskAugmented>> plan = new HashMap<Vehicle, List<TaskAugmented>>();
        // init
        for(Vehicle v : vehicles) {
            plan.put(v, new LinkedList<TaskAugmented>());
        }
        
        for (Task task : tasks) {
            TaskAugmented tp = new TaskAugmented(task, true);
            TaskAugmented td = new TaskAugmented(task, false);
            
            while (task.weight > vehicles.get(vehicle).capacity()) {
                vehicle = (vehicle + 1) % vehicles.size();
            }
            
            plan.get(vehicles.get(vehicle)).add(tp);
            plan.get(vehicles.get(vehicle)).add(td);
            
            vehicle = (vehicle + 1) % vehicles.size();
        }
        
        return new Solution(plan);
    }
    
    /**
     * Does <code>iter</code> iterations of <code>chooseNeighbors</code> to find a suboptimal solution
     */
    private static Solution finalSolution(Solution initS, int iter, long timeoutPlan) {
        Solution returnS = initS;
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < iter; ++i) {
            // We subtract 100 ms from the timeoutPlan so that we do not realise too late we've taken too much time
            if (System.currentTimeMillis() - start > timeoutPlan - 100) {
                return returnS;
            }
            returnS = chooseNeighbors(returnS, probability);            
        }
        
        return returnS;
    }
    
    /**
     * Computed the best solution from vehicle changes and order changes, return
     * this best solution according to probability
     */
    private static Solution chooseNeighbors(Solution s, double pickProb) {
        List<Vehicle> nonEmptyVehicles = new ArrayList<Vehicle>();
        for (Vehicle v2 : s.vehicles()) {
            if (!s.get(v2).isEmpty()) {
                nonEmptyVehicles.add(v2);
            }
        }
        
        // get random vehicle that's not empty
        Vehicle v = nonEmptyVehicles.get(random.nextInt(nonEmptyVehicles.size()));

        List<TaskAugmented> vTasks = s.get(v);
        Task t = vTasks.get(random.nextInt(vTasks.size())).task(); // the task that will be passed to other vehicles and changed in order

        List<Solution> changedVehicleList = changeVehicle(s, v, t);

        List<Solution> changedOrderList = changeOrder(s, v, t);

        List<Solution> changedEverythingList = new ArrayList<Solution>();
        changedEverythingList.addAll(changedVehicleList);
        changedEverythingList.addAll(changedOrderList);

        Solution best = getBest(changedEverythingList);

        return random.nextDouble() < pickProb ? best : s;
    }
    
    /**
     * Selects the best solution amongst the given ones regarding the cost of a solution
     */
    private static Solution getBest(List<Solution> sList) {
        double bestCost = Double.POSITIVE_INFINITY;
        double bestMaxLength = Double.POSITIVE_INFINITY;
        Solution bestS = null;
        
        for (Solution s : sList) {
            double cost = 0;
            int maxLength = 0;
            for (Vehicle v : s.vehicles()) {
                cost += cost(s, v);
                maxLength = Integer.max(maxLength, s.get(v).size());
            }
            
            if (cost < bestCost || (cost == bestCost && maxLength < bestMaxLength)) {
                bestCost = cost;
                bestMaxLength = maxLength;
                bestS = s;
            }
        }
        
        return bestS;
    }
     
    /**
     * Cost of a solution for one vehicle
     */
    private static double cost(Solution s, Vehicle v) {
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
     * Creates derivated solutions from <code>s</code> by putting random task of <code>v</code> to other vehicles
     */
    private static List<Solution> changeVehicle(Solution s, Vehicle v, Task t) {
        List<Solution> sList = new ArrayList<Solution>();
        if (s.get(v).isEmpty()) {
            return sList;
        }
                
        TaskAugmented tp = new TaskAugmented(t, true); // a pick up
        TaskAugmented td = new TaskAugmented(t, false); // equivalent delivery
        
        for (Vehicle v2 : s.vehicles()) {
            if (!v.equals(v2)) {
                if (v2.capacity() > tp.task().weight) {
                    Solution newS = new Solution(s);
                    
                    newS.remove(v, tp);
                    newS.remove(v, td);
                    
                    newS.add(v2, 0, tp);
                    newS.add(v2, 1, td);
                    
                    sList.add(newS);
                }
            }
        }
        
        return sList;
    }
    
    /**
     * Creates derivated solutions from <code>s</code> by changing the order of task <code>t</code> in vehicle <code>v</code>
     */
    private static List<Solution> changeOrder(Solution s, Vehicle v, Task t) {
        List<Solution> sList = new ArrayList<Solution>();
        Solution newS = new Solution(s);

        TaskAugmented tp = new TaskAugmented(t, true);
        TaskAugmented td = new TaskAugmented(t, false);
        
        newS.remove(v, tp);
        newS.remove(v, td);
        
        if (newS.get(v).isEmpty()) {
            return Arrays.asList(new Solution(s));
        }
        
        List<List<Integer>> indicesList = new ArrayList<List<Integer>>();
        
        int min = 0;
        int max = 0;
        
        int weightAcceptable = v.capacity();
        
        boolean commited = false;
        
        for (int i = 0; i < newS.get(v).size(); ++i) {
            TaskAugmented curr = newS.get(v).get(i);
            
            // finish
            if ((weightAcceptable < t.weight && !commited) || i == (newS.get(v).size() - 1)) {
                max = i + 1;
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
                    Solution newNewS = new Solution(newS);
                    
                    newNewS.add(v, i, tp);
                    newNewS.add(v, j + 1, td);   
                    
                    sList.add(newNewS);
                }
            }
        }
        
        return sList;
    }
    
}


