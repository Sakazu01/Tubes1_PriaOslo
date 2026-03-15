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
    private MapLocation currentRuin = null;

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

        // Highest priority: if any nearby ruin pattern is complete, build the tower now.
        MapLocation[] nearbyRuinsForComplete = rc.senseNearbyRuins(-1);
        for (MapLocation r : nearbyRuinsForComplete) {
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, r)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, r);
                if (currentRuin != null && currentRuin.equals(r)) currentRuin = null;
                rc.setIndicatorString("Soldier: completed ruin " + r);
                return;
            }
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, r)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, r);
                if (currentRuin != null && currentRuin.equals(r)) currentRuin = null;
                rc.setIndicatorString("Soldier: completed ruin " + r);
                return;
            }
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, r)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, r);
                if (currentRuin != null && currentRuin.equals(r)) currentRuin = null;
                rc.setIndicatorString("Soldier: completed ruin " + r);
                return;
            }
        }

        // Keep/refresh a persistent ruin objective like bot1.
        if (currentRuin != null && rc.canSenseLocation(currentRuin)) {
            if (!rc.senseMapInfo(currentRuin).hasRuin()) {
                currentRuin = null;
            } else if (rc.canSenseRobotAtLocation(currentRuin)) {
                RobotInfo occ = rc.senseRobotAtLocation(currentRuin);
                if (occ != null && occ.ID != rc.getID()) {
                    currentRuin = null;
                }
            }
        }

        if (currentRuin == null) {
            MapLocation[] nearbyRuins = rc.senseNearbyRuins(-1);
            int minDist = Integer.MAX_VALUE;
            for (MapLocation r : nearbyRuins) {
                if (rc.canSenseLocation(r) && rc.canSenseRobotAtLocation(r)) continue;
                int d = myLoc.distanceSquaredTo(r);
                if (d < minDist) {
                    minDist = d;
                    currentRuin = r;
                }
            }
        }

        // Fallback to shared ruin intel when no directly sensed ruin is available.
        if (currentRuin == null) {
            int minDist = Integer.MAX_VALUE;
            for (MapLocation loc : ruins) {
                if (rc.canSenseLocation(loc) && rc.canSenseRobotAtLocation(loc)) continue;
                int d = myLoc.distanceSquaredTo(loc);
                if (d < minDist) {
                    minDist = d;
                    currentRuin = loc;
                }
            }
        }

        if (currentRuin != null) {
            rc.setIndicatorString("Soldier: ruin target " + currentRuin);
            boolean inWorkRange = rc.getLocation().isAdjacentTo(currentRuin);

            // Standing on ruin center can block completion; step off first.
            if (rc.getLocation().equals(currentRuin)) {
                if (!moveRandomly()) {
                    for (Direction d : directions) {
                        if (rc.canMove(d)) {
                            rc.move(d);
                            break;
                        }
                    }
                }
                return;
            }

            // First, get into range to work on the ruin.
            if (!inWorkRange) {
                boolean moved = moveTowardRuinWorkTile(currentRuin);
                if (!moved) {
                    if (rc.canSenseLocation(currentRuin) && !rc.senseMapInfo(currentRuin).hasRuin()) {
                        ruins.remove(currentRuin);
                        currentRuin = null;
                    }
                    moveRandomly();
                }
                return;
            }

            // Mark pattern as soon as we are in range.
            boolean marked = false;
            if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, currentRuin)) {
                rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, currentRuin);
                marked = true;
            } else if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, currentRuin)) {
                rc.markTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, currentRuin);
                marked = true;
            } else if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, currentRuin)) {
                rc.markTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, currentRuin);
                marked = true;
            }
            if (marked) rc.setIndicatorString("Soldier: marked ruin " + currentRuin);

            // Fill unpainted pattern tiles.
            for (MapInfo tile : rc.senseNearbyMapInfos(currentRuin, 8)) {
                if (tile.getMark() != PaintType.EMPTY && tile.getMark() != tile.getPaint()) {
                    boolean sec = tile.getMark() == PaintType.ALLY_SECONDARY;
                    if (rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation(), sec);
                        break;
                    }
                }
            }
            // Complete whichever marked level-1 pattern is currently valid.
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, currentRuin)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, currentRuin);
                currentRuin = null;
            } else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, currentRuin)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, currentRuin);
                currentRuin = null;
            } else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, currentRuin)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, currentRuin);
                currentRuin = null;
            }
            return;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        // Priority 2: fight only when enemy is visible and no ruin objective exists.
        if (enemies.length > 0) {
            RobotInfo target = enemies[0];
            for (RobotInfo e : enemies)
                if (e.health < target.health) target = e;
            if (rc.canAttack(target.location))
                rc.attack(target.location, false);
            else
                moveToward(target.location);
            return;
        }

        // Priority 3: no ruin and no enemy — explore and paint underfoot to avoid penalty
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

    /** Move toward the best adjacent tile around a ruin instead of the ruin center itself. */
    private boolean moveTowardRuinWorkTile(MapLocation ruin) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (Direction d : directions) {
            MapLocation cand = ruin.add(d);
            int dist = myLoc.distanceSquaredTo(cand);
            if (dist < bestDist) {
                bestDist = dist;
                best = cand;
            }
        }

        if (best != null) return moveToward(best);
        return moveToward(ruin);
    }

    /** Move one step toward target; tries straight, then left/right of that direction. */
    protected boolean moveToward(MapLocation target) throws GameActionException {
        if (target == null || rc.getLocation().equals(target)) return true;

        Direction dir = rc.getLocation().directionTo(target);

        // Lightweight fallback protocol inspired by bot1: forward, right, left,
        // then rotate heading 90 degrees and repeat.
        for (int i = 0; i < 4; i++) {
            if (rc.canMove(dir)) { rc.move(dir); return true; }
            if (rc.canMove(dir.rotateRight())) { rc.move(dir.rotateRight()); return true; }
            if (rc.canMove(dir.rotateLeft())) { rc.move(dir.rotateLeft()); return true; }
            dir = dir.rotateLeft().rotateLeft();
        }
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
