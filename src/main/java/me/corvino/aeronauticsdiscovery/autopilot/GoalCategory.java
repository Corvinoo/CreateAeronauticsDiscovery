package me.corvino.aeronauticsdiscovery.autopilot;

public enum GoalCategory {
    /** Primary path of the craft: straight line, circle, hover */
    FLIGHT_PATH,
    /** Vertical constraints (altitude floor / ceiling) */
    ALTITUDE,
    /** Environmental avoidance (obstacles, terrain); may combine with any other category. */
    OBSTACLE
}
