package bot1;

import battlecode.common.*;
import java.util.Random;

/**
 * @brief Mobile soldier entity.
 *
 *        Current version:
 *        Idle: explores the map, paints empty tiles, and builds ruins
 *        into Level 1 towers (cycling paint/money/defense).
 *        Ganking: moves to an enemy tower and attacks it.
 *        Low paint: returns to the nearest ally tower for resupply.
 */
public class Soldier extends Robot {

    // ---- constants ----

    /** @brief Tower types to cycle through when marking ruins. */
    private static final UnitType[] BUILD_ORDER = {
        UnitType.LEVEL_ONE_PAINT_TOWER,
        UnitType.LEVEL_ONE_MONEY_TOWER,
        UnitType.LEVEL_ONE_DEFENSE_TOWER
    };

    /** @brief Squared distance threshold to pick a new explore target. */
    private static final int EXPLORE_ARRIVAL_DIST_SQ = 8;

    // ---- state ----

    private MapLocation currentRuin = null;
    private MapLocation exploreTarget = null;
    private int buildIndex;
    private final Random rng;

    /**
     * @brief            Constructor. Seeds build-order offset and RNG
     *                   from the robot's unique ID so that different
     *                   soldiers produce different tower types.
     * @param rc         The RobotController for this soldier.
     */
    public Soldier(RobotController rc){
        super(rc);
        rng = new Random(rc.getID());
        buildIndex = rc.getID() % BUILD_ORDER.length;
    }

    /**
     * @brief            Main turn loop. After shared Robot processing
     *                   (messages, scan, sync), runs soldier-specific
     *                   behaviour: return for paint, execute gank, or
     *                   idle (build ruins / paint / explore).
     * @throws GameActionException if a game action fails.
     */
    @Override
    public void run() throws GameActionException {
        super.run();

        if(!standstill && sync_phase == SYNC_IDLE){
            if(shouldReturnForPaint()){
                MapLocation tower = findNearestAllyTower();
                if(tower != null) move_to(tower);
                else explore();
            } else if(gankTarget != null){
                handleGank();
            } else {
                idleBehavior();
            }
        }

        Clock.yield();
    }

    // ---- idle behavior ----

    /**
     * @brief            Default idle behaviour priority chain:
     *                   1. Build a ruin into a tower.
     *                   2. Paint an empty tile within attack range.
     *                   3. Explore toward a random map location.
     * @throws GameActionException if a game action fails.
     */
    private void idleBehavior() throws GameActionException {
        if(tryBuildRuin()) return;
        if(tryPaintNearby()) return;
        explore();
    }

    /**
     * @brief            Attempt to build a tower at a nearby ruin.
     *                   Finds an unoccupied ruin, marks a tower pattern
     *                   (cycling through BUILD_ORDER per soldier), paints
     *                   the marked tiles (skipping enemy paint), and
     *                   completes the tower when all tiles are correct.
     * @return           true if the soldier is busy building (caller
     *                   should not run lower-priority actions).
     * @throws GameActionException if a game action fails.
     */
    private boolean tryBuildRuin() throws GameActionException {
        if(currentRuin != null && rc.canSenseLocation(currentRuin)
                && rc.canSenseRobotAtLocation(currentRuin)){
            currentRuin = null;
        }

        if(currentRuin == null){
            MapLocation[] nearbyRuins = rc.senseNearbyRuins(-1);
            for(MapLocation r : nearbyRuins){
                if(rc.canSenseLocation(r) && !rc.canSenseRobotAtLocation(r)){
                    currentRuin = r;
                    break;
                }
            }
        }

        if(currentRuin == null) return false;

        for(UnitType type : BUILD_ORDER){
            if(rc.canCompleteTowerPattern(type, currentRuin)){
                rc.completeTowerPattern(type, currentRuin);
                currentRuin = null;
                return true;
            }
        }

        MapInfo[] patternTiles = rc.senseNearbyMapInfos(currentRuin, 8);
        boolean hasMarks = false;
        for(MapInfo tile : patternTiles){
            if(tile.getMark() != PaintType.EMPTY){ hasMarks = true; break; }
        }

        if(!hasMarks){
            if(rc.canMarkTowerPattern(BUILD_ORDER[buildIndex], currentRuin)){
                rc.markTowerPattern(BUILD_ORDER[buildIndex], currentRuin);
                buildIndex = (buildIndex + 1) % BUILD_ORDER.length;
                patternTiles = rc.senseNearbyMapInfos(currentRuin, 8);
            } else {
                move_to(currentRuin);
                return true;
            }
        }

        MapLocation moveTarget = null;
        for(MapInfo tile : patternTiles){
            PaintType mark = tile.getMark();
            PaintType paint = tile.getPaint();
            if(mark == PaintType.EMPTY || mark == paint) continue;
            if(!paint.isAlly() && paint != PaintType.EMPTY) continue;

            MapLocation tileLoc = tile.getMapLocation();
            if(rc.canAttack(tileLoc)){
                rc.attack(tileLoc, mark == PaintType.ALLY_SECONDARY);
                return true;
            }
            if(moveTarget == null) moveTarget = tileLoc;
        }

        if(moveTarget != null){
            move_to(moveTarget);
        } else if(rc.canSenseLocation(currentRuin)
                && !rc.canSenseRobotAtLocation(currentRuin)){
            move_to(currentRuin);
        }
        return true;
    }

    /**
     * @brief            Paint an empty, unmarked tile at or near the
     *                   soldier's current position (within attack range).
     * @return           true if a tile was painted.
     * @throws GameActionException if a game action fails.
     */
    private boolean tryPaintNearby() throws GameActionException {
        MapInfo current = rc.senseMapInfo(rc.getLocation());
        if(current.getPaint() == PaintType.EMPTY
                && current.getMark() == PaintType.EMPTY
                && rc.canAttack(rc.getLocation())){
            rc.attack(rc.getLocation(), false);
            return true;
        }

        MapInfo[] nearby = rc.senseNearbyMapInfos(rc.getLocation(), 9);
        for(MapInfo tile : nearby){
            if(tile.getPaint() == PaintType.EMPTY
                    && tile.getMark() == PaintType.EMPTY
                    && tile.isPassable() && !tile.hasRuin()
                    && rc.canAttack(tile.getMapLocation())){
                rc.attack(tile.getMapLocation(), false);
                return true;
            }
        }
        return false;
    }

    /**
     * @brief            Move toward a random map-wide exploration target.
     *                   A new target is chosen when the current one is
     *                   reached (within EXPLORE_ARRIVAL_DIST_SQ).
     * @throws GameActionException if a game action fails.
     */
    private void explore() throws GameActionException {
        if(exploreTarget == null
                || rc.getLocation().distanceSquaredTo(exploreTarget) <= EXPLORE_ARRIVAL_DIST_SQ){
            exploreTarget = new MapLocation(
                rng.nextInt(MAP_WIDTH), rng.nextInt(MAP_HEIGHT));
        }
        move_to(exploreTarget);
    }

    // ---- gank ----

    /**
     * @brief            Move toward the gank target (enemy tower) and
     *                   attack its tile to deal structure damage.
     * @throws GameActionException if a game action fails.
     */
    private void handleGank() throws GameActionException {
        move_to(gankTarget);
        if(rc.canAttack(gankTarget)){
            rc.attack(gankTarget, false);
        }
    }
}
