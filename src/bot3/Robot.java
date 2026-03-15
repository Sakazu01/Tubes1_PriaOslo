package bot3;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * Base movable-robot entity implementation (Soldier/Mopper/Splasher).
 *
 * Extend this class or edit the unit hook methods to implement your algorithm.
 */
public class Robot extends Entity {
    private boolean standstill = false;

    public Robot(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        scan();
        processMessages();

        // Keep mobile units fully reactive: no robot-side sync handshake for now.
        if (sync_phase != SYNC_IDLE) {
            endSync();
        }

        if (!standstill) {
            runUnitTurn();
        }

        count++;
    }

    @Override
    protected void endSync() {
        super.endSync();
        standstill = false;
    }

    protected void runUnitTurn() throws GameActionException {
        UnitType type = rc.getType();
        if (type == UnitType.SOLDIER) {
            runSoldier();
            return;
        }
        if (type == UnitType.MOPPER) {
            runMopper();
            return;
        }
        if (type == UnitType.SPLASHER) {
            runSplasher();
        }
    }

    /** Greedy Soldier: fight any visible enemy first; otherwise head to the nearest ruin. */
    protected void runSoldier() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        // Priority 1: attack the lowest-HP enemy in range, then move toward it
        if (enemies.length > 2) {
            RobotInfo target = enemies[0];
            for (RobotInfo e : enemies)
                if (e.health < target.health) target = e;
            if (rc.canAttack(target.location))
                rc.attack(target.location, false);
            moveToward(target.location);
            return;
        }

        // Priority 2: move toward the closest ruin (sensed or from landmarks)
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation closestRuin = null;
        boolean ruinIsVisible = false;
        int minDist = Integer.MAX_VALUE;
        for (MapInfo tile : nearby) {
            if (tile.hasRuin()) {
                int d = myLoc.distanceSquaredTo(tile.getMapLocation());
                if (d < minDist) {
                    minDist = d;
                    closestRuin = tile.getMapLocation();
                    ruinIsVisible = true;
                }
            }
        }
        if (!ruinIsVisible)
            for (MapLocation loc : ruins) {
                int d = myLoc.distanceSquaredTo(loc);
                if (d < minDist) { minDist = d; closestRuin = loc; }
            }

        if (closestRuin != null) {
            // Move first so we can enter mark range as soon as possible.
            moveToward(closestRuin);

            // Mark pattern with scaffold-style guard.
            Direction dirToRuin = rc.getLocation().directionTo(closestRuin);
            if (dirToRuin != Direction.CENTER) {
                MapLocation shouldBeMarked = closestRuin.subtract(dirToRuin);
                if (rc.canSenseLocation(shouldBeMarked)
                        && rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY
                        && rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, closestRuin)) {
                    rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, closestRuin);
                }
            }

            // Fill unpainted tiles, then complete.
            if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, closestRuin))
                rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, closestRuin);
            for (MapInfo tile : rc.senseNearbyMapInfos(closestRuin, 8)) {
                if (tile.getMark() != PaintType.EMPTY && tile.getMark() != tile.getPaint()) {
                    boolean sec = tile.getMark() == PaintType.ALLY_SECONDARY;
                    if (rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation(), sec);
                        break;
                    }
                }
            }
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, closestRuin))
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, closestRuin);

            boolean moved = rc.getLocation().isAdjacentTo(closestRuin)
                    || rc.getLocation().equals(closestRuin)
                    || moveToward(closestRuin);
            if (!moved) {
                // If we are stuck, clear stale ruin intel when verifiable and keep exploring.
                if (rc.canSenseLocation(closestRuin) && !rc.senseMapInfo(closestRuin).hasRuin()) {
                    ruins.remove(closestRuin);
                }
                moveRandomly();
            }
            return;
        }

        // Priority 3: no ruin found — explore and paint underfoot to avoid penalty
        moveRandomly();
        MapLocation newLoc = rc.getLocation();
        MapInfo cur = rc.senseMapInfo(newLoc);
        if (!cur.getPaint().isAlly() && rc.canAttack(newLoc))
            rc.attack(newLoc, false);
    }

    /** Greedy Mopper: clean enemy tiles when hostile; otherwise support allies with low paint. */
    protected void runMopper() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        // Priority 1: mop swing in the direction that hits the most enemies
        if (enemies.length > 0) {
            Direction bestDir = null;
            int bestHits = 0;
            for (Direction dir : directions) {
                if (!rc.canMopSwing(dir)) continue;
                int hits = 0;
                for (MapLocation arc : getMopArc(myLoc, dir))
                    for (RobotInfo e : enemies)
                        if (e.location.equals(arc)) hits++;
                if (hits > bestHits) { bestHits = hits; bestDir = dir; }
            }
            if (bestDir != null) {
                rc.mopSwing(bestDir);
            } else {
                // No swing angle hits — clean nearest enemy-painted tile instead
                for (MapInfo tile : rc.senseNearbyMapInfos()) {
                    if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation());
                        break;
                    }
                }
                // Close in on the nearest enemy
                RobotInfo closest = enemies[0];
                int minD = myLoc.distanceSquaredTo(closest.location);
                for (RobotInfo e : enemies) {
                    int d = myLoc.distanceSquaredTo(e.location);
                    if (d < minD) { minD = d; closest = e; }
                }
                moveToward(closest.location);
            }
            return;
        }

        // Priority 2: stick to and refill the ally soldier/mopper/splasher with the least paint
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo needsPaint = null;
        int minPaint = Integer.MAX_VALUE;
        for (RobotInfo ally : allies) {
            if ((ally.type == UnitType.SOLDIER || ally.type == UnitType.MOPPER || ally.type == UnitType.SPLASHER)
                    && ally.paintAmount < minPaint) {
                minPaint = ally.paintAmount;
                needsPaint = ally;
            }
        }
        if (needsPaint != null) {
            int give = Math.max(0, Math.min(rc.getPaint() - 50, 50));
            if (give > 0 && rc.canTransferPaint(needsPaint.location, give))
                rc.transferPaint(needsPaint.location, give);
            moveToward(needsPaint.location);
            return;
        }

        // Priority 3: clean the nearest enemy-painted tile on the ground
        MapLocation enemyTile = null;
        int minTileDist = Integer.MAX_VALUE;
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (tile.getPaint().isEnemy()) {
                int d = myLoc.distanceSquaredTo(tile.getMapLocation());
                if (d < minTileDist) { minTileDist = d; enemyTile = tile.getMapLocation(); }
            }
        }
        if (enemyTile != null) {
            if (rc.canAttack(enemyTile)) rc.attack(enemyTile);
            else moveToward(enemyTile);
            return;
        }

        moveRandomly();
    }

    /** Greedy Splasher: maximise splash hits on enemy clusters; otherwise do SRP (paint uncovered tiles). */
    protected void runSplasher() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        // Priority 1: splash the enemy position that hits the most enemies at once
        if (enemies.length > 0) {
            RobotInfo bestTarget = null;
            int bestHits = 0;
            for (RobotInfo e : enemies) {
                if (!rc.canAttack(e.location)) continue;
                int hits = 0;
                for (RobotInfo other : enemies)
                    if (e.location.isWithinDistanceSquared(other.location, 2)) hits++;
                if (hits > bestHits) { bestHits = hits; bestTarget = e; }
            }
            if (bestTarget != null) {
                rc.attack(bestTarget.location);
            } else {
                // No enemy in range yet — move toward nearest
                RobotInfo closest = enemies[0];
                int minD = myLoc.distanceSquaredTo(closest.location);
                for (RobotInfo e : enemies) {
                    int d = myLoc.distanceSquaredTo(e.location);
                    if (d < minD) { minD = d; closest = e; }
                }
                moveToward(closest.location);
            }
            return;
        }

        // Priority 2: SRP — splash the tile that would paint the most currently unallied tiles
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation bestSplash = null;
        int bestCount = 0;
        for (MapInfo tile : nearby) {
            MapLocation loc = tile.getMapLocation();
            if (!rc.canAttack(loc)) continue;
            int tileCount = 0;
            for (MapInfo t : nearby)
                if (loc.isWithinDistanceSquared(t.getMapLocation(), 2) && !t.getPaint().isAlly())
                    tileCount++;
            if (tileCount > bestCount) { bestCount = tileCount; bestSplash = loc; }
        }
        if (bestSplash != null && bestCount > 0) {
            rc.attack(bestSplash);
            return;
        }

        // Priority 3: rotate through map quadrants for systematic coverage
        int quadrant = (rc.getRoundNum() / 50) % 4;
        MapLocation[] waypoints = {
            new MapLocation(MAP_WIDTH / 4,     MAP_HEIGHT / 4),
            new MapLocation(3 * MAP_WIDTH / 4, MAP_HEIGHT / 4),
            new MapLocation(3 * MAP_WIDTH / 4, 3 * MAP_HEIGHT / 4),
            new MapLocation(MAP_WIDTH / 4,     3 * MAP_HEIGHT / 4)
        };
        if (!moveToward(waypoints[quadrant])) moveRandomly();
    }

    protected boolean moveRandomly() throws GameActionException {
        int start = (rc.getRoundNum() + rc.getID()) % directions.length;
        for (int i = 0; i < directions.length; i++) {
            Direction d = directions[(start + i) % directions.length];
            if (rc.canMove(d)) {
                rc.move(d);
                return true;
            }
        }
        return false;
    }

    /** Move one step toward target; tries straight, then left/right of that direction. */
    protected boolean moveToward(MapLocation target) throws GameActionException {
        Direction dir = rc.getLocation().directionTo(target);
        if (rc.canMove(dir)) { rc.move(dir); return true; }
        Direction left  = dir.rotateLeft();
        Direction right = dir.rotateRight();
        if (rc.canMove(left))  { rc.move(left);  return true; }
        if (rc.canMove(right)) { rc.move(right); return true; }
        return false;
    }

    /** Returns the three map tiles swept by a mop swing in the given direction. */
    private MapLocation[] getMopArc(MapLocation center, Direction dir) {
        return new MapLocation[] {
            center.add(dir.rotateLeft()),
            center.add(dir),
            center.add(dir.rotateRight())
        };
    }

    protected void processMessages() throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        for (Message msg : messages) {
            int senderId = msg.getSenderID();
            int data = msg.getBytes();

            if (sync_phase != SYNC_IDLE && senderId == sync_partner_id) {
                handleSyncMessage(data);
                continue;
            }

            if (isStandstill(data)) {
                standstill = true;
                continue;
            }

            if (isResume(data)) {
                standstill = false;
                continue;
            }

            if (isInlineReport(data)) {
                receiveInlineReport(data);
                continue;
            }

            if (isSyncHeader(data) && sync_phase == SYNC_IDLE) {
                // Robot-side sync is disabled to avoid deadlocks that freeze movement.
                return;
            }
        }
    }

}
