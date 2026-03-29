package main_bot;

import battlecode.common.*;

public class TowerDefense extends Tower {
    int level;

    public TowerDefense(RobotController rc, int level){
        super(rc, level);
    }

    @Override
    public void run() throws GameActionException {
        super.run();
        
        Clock.yield();
    }
}
