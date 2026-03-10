package bot1;

import battlecode.common.*;

public class Entity {
    RobotController rc;
    int count;

    Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };
    
    public Entity(RobotController rc){
        this.rc = rc;
        this.count = 1;
    }

    public void run() throws GameActionException {
        count++;
        
        Clock.yield();
    }
}