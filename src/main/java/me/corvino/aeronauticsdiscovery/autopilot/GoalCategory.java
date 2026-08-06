package me.corvino.aeronauticsdiscovery.autopilot;

public enum GoalCategory {
    /** Primary path of the craft: straight line, circle, hover */
    FLIGHT_PATH,
    /** Vertical constraints (altitude floor / ceiling) */
    ALTITUDE,
    /** Environmental avoidance (obstacles, terrain) */
    OBSTACLE,
    /** Dynamic terrain clearance (pitch to avoid rising ground / ceilings)*/
    TERRAIN
}
