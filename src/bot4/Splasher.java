package bot4;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Splasher extends Robot {
    private MapLocation ruinTarget = null;

    public Splasher(RobotController rc) {
        super(rc);
    }

    @Override
    protected void runUnitTurn() throws GameActionException {
        if (ruinTarget != null && rc.canSenseLocation(ruinTarget)) {
            if (!rc.senseMapInfo(ruinTarget).hasRuin()) {
                ruinTarget = null;
            }
        }

        if (ruinTarget == null) {
            ruinTarget = findNearestRuinTarget();
        }

        if (ruinTarget != null) {
            moveTowardRuinWorkTile(ruinTarget);
            if (rc.canAttack(ruinTarget)) {
                rc.attack(ruinTarget);
            }
            return;
        }

        moveRandomly();
    }
}
