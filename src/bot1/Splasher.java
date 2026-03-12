package bot1;

import battlecode.common.*;

public class Splasher extends Robot {
    public Splasher(RobotController rc){
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        super.run();

        Clock.yield();
    }
}