package bot3;

import java.util.ArrayList;

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
 * Tower entity implementation.
 *
 * Responsibilities:
 * - Keep landmark knowledge updated.
 * - Relay newly discovered landmarks to nearby ally towers.
 * - Perform periodic two-way sync with nearby ally towers.
 */
public class Tower extends Entity {
    private final ArrayList<Integer> pendingInlineReports = new ArrayList<>();


    int Level;
    int lastSpawned;

    UnitType[] spawnorder = {UnitType.SOLDIER, UnitType.MOPPER, UnitType.SPLASHER, UnitType.MOPPER};

    public Tower(RobotController rc) {
        super(rc);
        this.Level = levelFromType(rc.getType());
        this.lastSpawned = 0;
    }

    private static int levelFromType(UnitType t) {
        if (t == UnitType.LEVEL_TWO_PAINT_TOWER || t == UnitType.LEVEL_TWO_MONEY_TOWER || t == UnitType.LEVEL_TWO_DEFENSE_TOWER) return 2;
        if (t == UnitType.LEVEL_THREE_PAINT_TOWER || t == UnitType.LEVEL_THREE_MONEY_TOWER || t == UnitType.LEVEL_THREE_DEFENSE_TOWER) return 3;
        return 1;
    }

    @Override
    public void run() throws GameActionException {
        scan();
        processMessages();
        boolean attackedEnemy = attackEnemies();
        if (!attackedEnemy) {
            paintGround();
        }
        spawnTurn();

        if (sync_phase == SYNC_IDLE && shouldInitSync()) {
            tryInitSync();
        }

        if (sync_phase == SYNC_IDLE) {
            sendInlineReports();
        }

        handleSyncSend();
        count++;
    }

    /** Greedy: if there is no enemy to shoot, paint enemy tiles first, then any non-ally tile. */
    private void paintGround() throws GameActionException {
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation enemyPaintTarget = null;
        MapLocation neutralTarget = null;
        int enemyMinDist = Integer.MAX_VALUE;
        int neutralMinDist = Integer.MAX_VALUE;
        MapLocation myLoc = rc.getLocation();

        for (MapInfo tile : nearby) {
            MapLocation loc = tile.getMapLocation();
            if (!rc.canAttack(loc)) continue;

            PaintType paint = tile.getPaint();
            int d = myLoc.distanceSquaredTo(loc);

            if (paint.isEnemy() && d < enemyMinDist) {
                enemyMinDist = d;
                enemyPaintTarget = loc;
            } else if (!paint.isAlly() && d < neutralMinDist) {
                neutralMinDist = d;
                neutralTarget = loc;
            }
        }

        if (enemyPaintTarget != null) {
            rc.attack(enemyPaintTarget);
            return;
        }
        if (neutralTarget != null) {
            rc.attack(neutralTarget);
        }
    }

    /** Greedy: attack the enemy with the lowest HP that is in attack range. */
    private boolean attackEnemies() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return false;
        RobotInfo target = enemies[0];
        for (RobotInfo e : enemies)
            if (e.health < target.health) target = e;
        if (rc.canAttack(target.location)) {
            rc.attack(target.location);
            return true;
        }
        return false;
    }

    /** Greedy: spawn the next unit in the Soldier->Mopper->Splasher->Mopper rotation. */
    private void spawnTurn() throws GameActionException {
        UnitType toSpawn = spawnorder[lastSpawned % spawnorder.length];
        for (Direction dir : directions) {
            MapLocation loc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(toSpawn, loc)) {
                rc.buildRobot(toSpawn, loc);
                lastSpawned++;
                return;
            }
        }
    }

    @Override
    protected void onNewLandmark(int packedEntry) {
        // Entity constructor calls scan(), which may invoke this override
        // before Tower fields are initialized.
        if (pendingInlineReports != null) {
            pendingInlineReports.add(packedEntry);
        }
    }

    private void processMessages() throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        for (Message msg : messages) {
            int senderId = msg.getSenderID();
            int data = msg.getBytes();

            if (sync_phase != SYNC_IDLE && senderId == sync_partner_id) {
                handleSyncMessage(data);
                continue;
            }

            if (isInlineReport(data)) {
                receiveInlineReport(data);
                continue;
            }

            if (isSyncHeader(data) && sync_phase == SYNC_IDLE) {
                MapLocation senderLoc = findAllyTowerLocationById(senderId);
                if (senderLoc == null) {
                    continue;
                }

                sync_initiator = false;
                sync_partner_id = senderId;
                sync_target_loc = senderLoc;
                sync_phase = SYNC_R_RECV;
                sync_remaining = -2;
                handleSyncMessage(data);
            }
        }
    }

    private void tryInitSync() throws GameActionException {
        RobotInfo partner = findNearbyAllyTower();
        if (partner == null) {
            return;
        }

        sync_initiator = true;
        sync_partner_id = partner.ID;
        sync_target_loc = partner.location;
        sync_payload = buildSyncPayloadAll();
        sync_cursor = 0;
        sync_phase = SYNC_I_H1;
    }

    private RobotInfo findNearbyAllyTower() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.ID != rc.getID() && isTowerType(ally.type)) {
                return ally;
            }
        }
        return null;
    }

    private MapLocation findAllyTowerLocationById(int id) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.ID == id && isTowerType(ally.type)) {
                return ally.location;
            }
        }
        return null;
    }

    private void sendInlineReports() throws GameActionException {
        if (pendingInlineReports.isEmpty()) {
            return;
        }

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int entry = pendingInlineReports.remove(0);
        int msg = buildInlineReport(entryType(entry), entryX(entry), entryY(entry));

        for (RobotInfo ally : allies) {
            if (ally.ID != rc.getID() && isTowerType(ally.type) && rc.canSendMessage(ally.location, msg)) {
                rc.sendMessage(ally.location, msg);
            }
        }
    }
}
