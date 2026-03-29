package main_bot;

import battlecode.common.*;

public class TowerMoney extends Tower {
    int level;

    public TowerMoney(RobotController rc, int level){
        super(rc, level);
    }

    @Override
    public void run() throws GameActionException {
        super.run();
        
        Clock.yield();
    }
}