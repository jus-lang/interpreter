package org.jus.interpreter.state;

import org.jus.interpreter.runtime.Value;
import java.util.*;

public class StateManager {
    private String currentState;
    private Map<String, Map<String, TransitionFunction>> transitions;
    private Set<String> validStates;

    public StateManager() {
        this.transitions = new HashMap<>();
        this.validStates = new HashSet<>();
    }

    public void addState(String stateName) {
        validStates.add(stateName);
        transitions.putIfAbsent(stateName, new HashMap<>());
    }

    public void addTransition(String fromState, String event, TransitionFunction transition) {
        if (!validStates.contains(fromState)) {
            throw new RuntimeException("Invalid state: " + fromState);
        }
        transitions.get(fromState).put(event, transition);
    }

    public void setState(String state) {
        if (!validStates.contains(state)) {
            throw new RuntimeException("Invalid state: " + state);
        }
        this.currentState = state;
    }

    public String getCurrentState() {
        return currentState;
    }

    public String handleEvent(String event, Value eventData) {
        Map<String, TransitionFunction> stateTransitions = transitions.get(currentState);
        if (stateTransitions == null || !stateTransitions.containsKey(event)) {
            throw new RuntimeException("No transition defined for state '" + currentState + 
                                    "' and event '" + event + "'");
        }

        TransitionFunction transition = stateTransitions.get(event);
        String newState = transition.execute(eventData);
        
        if (!validStates.contains(newState)) {
            throw new RuntimeException("Transition returned invalid state: " + newState);
        }

        currentState = newState;
        return currentState;
    }
}