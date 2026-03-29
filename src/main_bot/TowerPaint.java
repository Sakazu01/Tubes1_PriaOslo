package main_bot;

import battlecode.common.*;

public class TowerPaint extends Tower {
    int level;

    public TowerPaint(RobotController rc, int level){
        super(rc, level);
    }

    @Override
    public void run() throws GameActionException {
        super.run();

        Clock.yield();
    }
}
