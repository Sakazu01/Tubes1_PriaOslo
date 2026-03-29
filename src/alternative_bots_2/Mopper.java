package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Mopper extends Robot {
    private static final int LOW_PAINT_RATIO_NUM = 1;
    private static final int LOW_PAINT_RATIO_DEN = 2;

    public Mopper(RobotController rc) {
        super(rc);
    }

    @Override
    protected void runUnitTurn() throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        if (mopEnemyPaint(nearbyTiles)) {
            return;
        }

        if (transferInkToLowAlly()) {
            return;
        }

        // No painting fallback: just keep moving to scout/position.
        moveRandomly();
    }

    private boolean mopEnemyPaint(MapInfo[] nearbyTiles) throws GameActionException {
        MapLocation myLoc = rc.getLocation();


        for (Direction dir : directions) {
            if (!rc.canMopSwing(dir)) continue;
            MapLocation front = myLoc.add(dir);
            if (!rc.canSenseLocation(front)) continue;
            if (rc.senseMapInfo(front).getPaint().isEnemy()) {
                rc.mopSwing(dir);
                return true;
            }
        }

        MapLocation bestEnemyTile = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles) {
            if (!tile.getPaint().isEnemy()) continue;
            int d = myLoc.distanceSquaredTo(tile.getMapLocation());
            if (d < bestDist) {
                bestDist = d;
                bestEnemyTile = tile.getMapLocation();
            }
        }

        if (bestEnemyTile == null) return false;
        moveToward(bestEnemyTile);
        return true;
    }

    private boolean transferInkToLowAlly() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo bestAlly = null;
        int lowestRatioScore = Integer.MAX_VALUE;

        for (RobotInfo ally : allies) {
            if (ally.location.equals(rc.getLocation())) continue;
            if (ally.type.isTowerType()) continue;

            int capacity = ally.type.paintCapacity;
            if (capacity <= 0) continue;
            if (ally.paintAmount * LOW_PAINT_RATIO_DEN >= capacity * LOW_PAINT_RATIO_NUM) continue;

            // Minimize paint/capacity ratio using cross multiplication.
            int ratioScore = ally.paintAmount * 1000 / capacity;
            if (bestAlly == null || ratioScore < lowestRatioScore) {
                bestAlly = ally;
                lowestRatioScore = ratioScore;
            }
        }

        if (bestAlly == null) return false;

        int missing = Math.max(0, bestAlly.type.paintCapacity - bestAlly.paintAmount);
        int give = Math.min(missing, Math.max(0, rc.getPaint() - 40));
        give = Math.min(give, 60);

        if (give > 0 && rc.canTransferPaint(bestAlly.location, give)) {
            rc.transferPaint(bestAlly.location, give);
            return true;
        }

        moveToward(bestAlly.location);
        return true;
    }
}
