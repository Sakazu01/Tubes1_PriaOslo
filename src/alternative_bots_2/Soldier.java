package alternative_bots_2;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

public class Soldier extends Robot {
    private MapLocation ruinTarget = null;

    public Soldier(RobotController rc) {
        super(rc);
    }

    @Override
    protected void runUnitTurn() throws GameActionException {
        if (completeNearbyRuinIfReady()) {
            return;
        }

        if (ruinTarget != null && rc.canSenseLocation(ruinTarget)) {
            if (!rc.senseMapInfo(ruinTarget).hasRuin()) {
                ruinTarget = null;
            }
        }

        if (ruinTarget == null) {
            ruinTarget = findNearestRuinTarget();
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
                return;
            }
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

        MapLocation[] nearbyRuins = rc.senseNearbyRuins(-1);
        for (MapLocation ruin : nearbyRuins) {
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
}
