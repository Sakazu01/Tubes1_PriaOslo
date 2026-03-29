package alternative_bots_2;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

public class Splasher extends Robot {
    private static final int RUIN_ENGAGE_DISTANCE_SQ = 25;
    private MapLocation ruinTarget = null;

    public Splasher(RobotController rc) {
        super(rc);
    }

    @Override
    protected void runUnitTurn() throws GameActionException {
        if (completeNearbyRuinIfReady()) {
            return;
        }

        MapLocation nearbyRuin = findNearbyRuinTarget();
        if (nearbyRuin != null) {
            ruinTarget = nearbyRuin;
        } else {
            ruinTarget = null;
        }

        if (ruinTarget != null) {
            if (rc.getLocation().equals(ruinTarget)) {
                moveRandomly();
                return;
            }

            if (!rc.getLocation().isAdjacentTo(ruinTarget)) {
                moveTowardRuinWorkTile(ruinTarget);
                return;
            }

            if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinTarget)) {
                rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinTarget);
            }

            MapLocation unfinishedPatternTile = null;
            int bestDist = Integer.MAX_VALUE;
            for (MapInfo tile : rc.senseNearbyMapInfos(ruinTarget, 8)) {
                if (tile.getMark() != PaintType.EMPTY && tile.getMark() != tile.getPaint()) {
                    boolean secondary = tile.getMark() == PaintType.ALLY_SECONDARY;
                    if (rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation(), secondary);
                        return;
                    }

                    int d = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                    if (d < bestDist) {
                        bestDist = d;
                        unfinishedPatternTile = tile.getMapLocation();
                    }
                }
            }

            if (unfinishedPatternTile != null) {
                moveToward(unfinishedPatternTile);
                return;
            }

            if (completeRuinIfReady(ruinTarget)) {
                ruinTarget = null;
            }
            return;
        }

        MapLocation paintTarget = findNearestUnpaintedTile();
        if (paintTarget != null) {
            if (rc.canAttack(paintTarget)) {
                rc.attack(paintTarget, false);
                return;
            }
            moveToward(paintTarget);
            paintUnderfoot();
            return;
        }

        moveRandomly();
        paintUnderfoot();
    }

    private boolean completeNearbyRuinIfReady() throws GameActionException {
        if (ruinTarget != null && completeRuinIfReady(ruinTarget)) {
            ruinTarget = null;
            return true;
        }

        for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
            if (completeRuinIfReady(ruin)) {
                if (ruinTarget != null && ruinTarget.equals(ruin)) {
                    ruinTarget = null;
                }
                return true;
            }
        }
        return false;
    }

    private boolean completeRuinIfReady(MapLocation ruin) throws GameActionException {
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin);
            return true;
        }
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruin)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruin);
            return true;
        }
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruin)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruin);
            return true;
        }
        return false;
    }

    private MapLocation findNearbyRuinTarget() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
            if (rc.canSenseLocation(ruin) && rc.canSenseRobotAtLocation(ruin)) continue;
            int d = myLoc.distanceSquaredTo(ruin);
            if (d <= RUIN_ENGAGE_DISTANCE_SQ && d < bestDist) {
                bestDist = d;
                best = ruin;
            }
        }

        return best;
    }

    private MapLocation findNearestUnpaintedTile() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (!tile.isPassable()) continue;
            if (tile.getPaint().isAlly()) continue;

            MapLocation loc = tile.getMapLocation();
            int d = myLoc.distanceSquaredTo(loc);
            if (d < bestDist) {
                bestDist = d;
                best = loc;
            }
        }

        return best;
    }
}
