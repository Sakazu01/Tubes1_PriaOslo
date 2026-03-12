package bot1;

import battlecode.common.*;

public class Mopper extends Robot {
    public Mopper(RobotController rc){
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        super.run();

        Clock.yield();
    }
}