package template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import logist.simulation.Vehicle;

public class Solution2 {
    
    private Map<Vehicle, List<TaskAugmented>> plan;
    
    public Solution2(Map<Vehicle, List<TaskAugmented>> plan) {
        Map<Vehicle, List<TaskAugmented>> copy = new HashMap<Vehicle, List<TaskAugmented>>();
        
        for (Entry<Vehicle, List<TaskAugmented>> entry : plan.entrySet()) {
            copy.put(entry.getKey(), new LinkedList<TaskAugmented>(entry.getValue()));
        }
        
        this.plan = copy;
    }
    
    public Solution2(Solution2 that) {
        this(that.plan);
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Solution2) {
            Solution2 that = (Solution2) o;
            return this.plan.equals(that.plan);
        }
        
        return false;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(plan);
    }
    
    public List<TaskAugmented> get(Vehicle v) {
        return plan.get(v);
    }
    
    public void put(Vehicle v, List<TaskAugmented> l) {
        plan.put(v, l);
    }
    
    public void add(Vehicle v, TaskAugmented t) {
        plan.get(v).add(t);
    }
    
    public void add(Vehicle v, int i, TaskAugmented t) {
        plan.get(v).add(i, t);
    }
    
    public void remove(Vehicle v, TaskAugmented t) {
        plan.get(v).remove(t);
    }
    
    public int nbVehicle() {
        return plan.size();
    }
    
    public Set<Entry<Vehicle, List<TaskAugmented>>> entries() {
        return plan.entrySet();
    }
    
    public List<Vehicle> vehicles() {
        List<Vehicle> vehicles = new ArrayList<Vehicle>();
        
        for (Entry<Vehicle, List<TaskAugmented>> e : entries()) {
            vehicles.add(e.getKey());
        }
        
        return vehicles;
    }
    
    @Override
    public String toString() {
        String s = "";

        for (Entry<Vehicle, List<TaskAugmented>> e : plan.entrySet()) {
            s += e.getKey().name() + " : " + e.getValue() + "\n";
        }
        
        return s;
    }

}
