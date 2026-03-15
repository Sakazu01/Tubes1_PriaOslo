package bot4;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Mopper extends Robot {
    public Mopper(RobotController rc) {
        super(rc);
    }

    @Override
    protected void runUnitTurn() throws GameActionException {
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation myLoc = rc.getLocation();

        MapLocation edgeTile = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo tile : nearby) {
            if (!tile.getPaint().isAlly()) continue;
            if (!isEdgeTile(tile.getMapLocation(), nearby)) continue;
            int d = myLoc.distanceSquaredTo(tile.getMapLocation());
            if (d < bestDist) {
                bestDist = d;
                edgeTile = tile.getMapLocation();
            }
        }

        MapLocation paintTarget = null;
        int paintDist = Integer.MAX_VALUE;
        for (MapInfo tile : nearby) {
            if (tile.getPaint().isAlly()) continue;
            if (!tile.isPassable()) continue;
            int d = myLoc.distanceSquaredTo(tile.getMapLocation());
            if (d < paintDist) {
                paintDist = d;
                paintTarget = tile.getMapLocation();
            }
        }

        if (paintTarget != null && rc.canAttack(paintTarget)) {
            rc.attack(paintTarget);
            return;
        }

        if (edgeTile != null && !myLoc.equals(edgeTile)) {
            moveToward(edgeTile);
            return;
        }

        if (paintTarget != null) {
            moveToward(paintTarget);
            return;
        }

        moveRandomly();
    }
}
