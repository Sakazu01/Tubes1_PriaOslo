package bot3;

import java.util.ArrayList;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.Message;
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

    public Tower(RobotController rc, int level) {
        super(rc);
        this.Level = level;
        this.lastSpawned = 0;
    }

    @Override
    public void run() throws GameActionException {
        scan();
        processMessages();

        if (sync_phase == SYNC_IDLE && shouldInitSync()) {
            tryInitSync();
        }

        if (sync_phase == SYNC_IDLE) {
            sendInlineReports();
        }

        handleSyncSend();
        count++;
    }

    @Override
    protected void onNewLandmark(int packedEntry) {
        pendingInlineReports.add(packedEntry);
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
