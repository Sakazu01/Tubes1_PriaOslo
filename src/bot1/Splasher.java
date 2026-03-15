package bot1;

import battlecode.common.*;
import java.util.Random;

/**
 * @brief Mobile splasher entity.
 *
 *        Current version:
 *        Idle: finds the highest-value tile (enemy paint > empty > ally)
 *        within attack range and splashes it with AOE paint. Paints
 *        under itself while exploring.
 *        Ganking: moves toward an enemy tower and attacks it.
 *        Low paint: returns to the nearest ally tower for resupply.
 */
public class Splasher extends Robot {

    // ---- constants ----

    /** @brief Score awarded to an empty tile when choosing a splash target. */
    private static final int SCORE_EMPTY = 2;

    /** @brief Score awarded to an enemy-painted tile. */
    private static final int SCORE_ENEMY = 3;

    /** @brief Squared distance threshold to pick a new explore target. */
    private static final int EXPLORE_ARRIVAL_DIST_SQ = 8;

    // ---- state ----

    private MapLocation exploreTarget = null;
    private final Random rng;

    /**
     * @brief            Constructor.
     * @param rc         The RobotController for this splasher.
     */
    public Splasher(RobotController rc){
        super(rc);
        rng = new Random(rc.getID());
    }

    /**
     * @brief            Main turn loop. After shared Robot processing,
     *                   runs splasher-specific behaviour: return for
     *                   paint, execute gank, or idle (splash / explore).
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
     *                   1. Splash the best nearby target.
     *                   2. Explore toward a random map location.
     * @throws GameActionException if a game action fails.
     */
    private void idleBehavior() throws GameActionException {
        if(trySplash()) return;
        explore();
    }

    /**
     * @brief            Find the highest-scoring tile in attack range and
     *                   splash it.  Enemy paint scores SCORE_ENEMY, empty
     *                   tiles score SCORE_EMPTY, ally tiles score 0.
     * @return           true if a tile was splashed.
     * @throws GameActionException if a game action fails.
     */
    private boolean trySplash() throws GameActionException {
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation bestTarget = null;
        int bestScore = 0;

        for(MapInfo tile : nearby){
            if(!rc.canAttack(tile.getMapLocation())) continue;
            if(!tile.isPassable()) continue;
            int score = 0;
            if(tile.getPaint() == PaintType.EMPTY) score += SCORE_EMPTY;
            else if(tile.getPaint().isEnemy()) score += SCORE_ENEMY;
            if(score > bestScore){
                bestScore = score;
                bestTarget = tile.getMapLocation();
            }
        }

        if(bestTarget != null && bestScore > 0){
            rc.attack(bestTarget);
            return true;
        }
        return false;
    }

    /**
     * @brief            Move toward a random map-wide exploration target.
     *                   Also paints under self if the current tile is not
     *                   already allied. A new target is chosen when the
     *                   current one is reached (within EXPLORE_ARRIVAL_DIST_SQ).
     * @throws GameActionException if a game action fails.
     */
    private void explore() throws GameActionException {
        if(exploreTarget == null
                || rc.getLocation().distanceSquaredTo(exploreTarget) <= EXPLORE_ARRIVAL_DIST_SQ){
            exploreTarget = new MapLocation(
                rng.nextInt(MAP_WIDTH), rng.nextInt(MAP_HEIGHT));
        }
        move_to(exploreTarget);

        MapInfo cur = rc.senseMapInfo(rc.getLocation());
        if(!cur.getPaint().isAlly() && rc.canAttack(rc.getLocation())){
            rc.attack(rc.getLocation());
        }
    }

    // ---- gank ----

    /**
     * @brief            Move toward the gank target (enemy tower) and
     *                   attack the first enemy tower in range.
     * @throws GameActionException if a game action fails.
     */
    private void handleGank() throws GameActionException {
        move_to(gankTarget);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for(RobotInfo e : enemies){
            if(isTowerType(e.type) && rc.canAttack(e.location)){
                rc.attack(e.location);
                return;
            }
        }
    }
}
