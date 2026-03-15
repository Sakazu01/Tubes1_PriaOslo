package bot1;

import battlecode.common.*;
import java.util.Random;

/**
 * @brief Mobile mopper entity.
 *
 *        Idle: helps paint-depleted allies via transferPaint, delivers
 *        paint to non-paint towers (courier), mops enemy paint (single
 *        target or AOE mopSwing), and explores the map.
 *        Ganking: moves toward enemy tower and attacks nearby enemies.
 *        Low paint: returns to the nearest ally tower for resupply.
 */
public class Mopper extends Robot {

    // ---- constants ----

    /** @brief Ally paint amount at or below which help is offered. */
    private static final int DEPLETED_PAINT_THRESHOLD = 10;

    /** @brief Amount of paint transferred to a depleted ally. */
    private static final int HELP_TRANSFER_AMOUNT = 20;

    /** @brief Minimum own paint to attempt courier delivery. */
    private static final int COURIER_MIN_PAINT = 100;

    /** @brief Amount of paint delivered to a non-paint tower. */
    private static final int COURIER_TRANSFER_AMOUNT = 50;

    /** @brief Squared distance threshold to pick a new explore target. */
    private static final int EXPLORE_ARRIVAL_DIST_SQ = 8;

    // ---- state ----

    private MapLocation exploreTarget = null;
    private final Random rng;

    /**
     * @brief            Constructor.
     * @param rc         The RobotController for this mopper.
     */
    public Mopper(RobotController rc){
        super(rc);
        rng = new Random(rc.getID());
    }

    /**
     * @brief            Main turn loop. After shared Robot processing,
     *                   runs mopper-specific behaviour: return for paint,
     *                   execute gank, or idle (help allies / courier /
     *                   mop / explore).
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
     *                   1. Help a nearby paint-depleted ally robot.
     *                   2. Deliver surplus paint to a non-paint tower.
     *                   3. Mop nearby enemy paint (AOE then single).
     *                   4. Explore toward a random map location.
     * @throws GameActionException if a game action fails.
     */
    private void idleBehavior() throws GameActionException {
        if(helpDepletedAlly()) return;
        if(tryCourier()) return;
        if(mopNearbyEnemy()) return;
        explore();
    }

    /**
     * @brief            Find the nearest allied robot with paint at or
     *                   below DEPLETED_PAINT_THRESHOLD and transfer paint
     *                   to it. If the ally is out of transfer range,
     *                   move toward it instead.
     * @return           true if an action was taken.
     * @throws GameActionException if a game action fails.
     */
    private boolean helpDepletedAlly() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for(RobotInfo ally : allies){
            if(isTowerType(ally.type)) continue;
            if(ally.paintAmount > DEPLETED_PAINT_THRESHOLD) continue;
            if(rc.canTransferPaint(ally.location, HELP_TRANSFER_AMOUNT)){
                rc.transferPaint(ally.location, HELP_TRANSFER_AMOUNT);
                return true;
            }
            move_to(ally.location);
            return true;
        }
        return false;
    }

    /**
     * @brief            If this mopper has surplus paint, deliver some to
     *                   a nearby non-paint ally tower (defense or money).
     * @return           true if paint was transferred.
     * @throws GameActionException if a game action fails.
     */
    private boolean tryCourier() throws GameActionException {
        if(rc.getPaint() < COURIER_MIN_PAINT) return false;
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for(RobotInfo ally : allies){
            if(!isTowerType(ally.type)) continue;
            if(isPaintTower(ally.type)) continue;
            if(rc.canTransferPaint(ally.location, COURIER_TRANSFER_AMOUNT)){
                rc.transferPaint(ally.location, COURIER_TRANSFER_AMOUNT);
                return true;
            }
        }
        return false;
    }

    /**
     * @brief            Attempt to remove enemy paint. First tries
     *                   mopSwing (AOE) in any direction that has enemy
     *                   paint, then falls back to a single-target attack.
     *                   If an enemy tile is visible but out of range, the
     *                   mopper moves toward it.
     * @return           true if an action was taken.
     * @throws GameActionException if a game action fails.
     */
    private boolean mopNearbyEnemy() throws GameActionException {
        for(Direction dir : directions){
            if(!rc.canMopSwing(dir)) continue;
            MapLocation target = rc.getLocation().add(dir);
            if(rc.canSenseLocation(target)
                    && rc.senseMapInfo(target).getPaint().isEnemy()){
                rc.mopSwing(dir);
                return true;
            }
        }

        MapInfo[] nearby = rc.senseNearbyMapInfos();
        for(MapInfo tile : nearby){
            if(!tile.getPaint().isEnemy()) continue;
            if(rc.canAttack(tile.getMapLocation())){
                rc.attack(tile.getMapLocation());
                return true;
            }
            move_to(tile.getMapLocation());
            return true;
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
     * @brief            Move toward the gank target and attack the first
     *                   enemy in range.
     * @throws GameActionException if a game action fails.
     */
    private void handleGank() throws GameActionException {
        move_to(gankTarget);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for(RobotInfo e : enemies){
            if(rc.canAttack(e.location)){
                rc.attack(e.location);
                return;
            }
        }
    }

    // ---- helpers ----

    /**
     * @brief            Check whether a UnitType is a paint tower variant.
     * @param type       The UnitType to test.
     * @return           true if the type is a Level 1/2/3 paint tower.
     */
    private static boolean isPaintTower(UnitType type){
        return type == UnitType.LEVEL_ONE_PAINT_TOWER
            || type == UnitType.LEVEL_TWO_PAINT_TOWER
            || type == UnitType.LEVEL_THREE_PAINT_TOWER;
    }
}
