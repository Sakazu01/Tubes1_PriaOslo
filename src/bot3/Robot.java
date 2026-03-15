package bot3;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.Message;
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

        if (sync_phase == SYNC_IDLE && shouldInitSync()) {
            tryInitSyncWithTower();
        }

        handleSyncSend();

        if (!standstill && sync_phase == SYNC_IDLE) {
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

    /** Hook for Soldier behavior. */
    protected void runSoldier() throws GameActionException {
    }

    /** Hook for Mopper behavior. */
    protected void runMopper() throws GameActionException {
    }

    /** Hook for Splasher behavior. */
    protected void runSplasher() throws GameActionException {
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
                MapLocation senderLoc = findAllyTowerLocationById(senderId);
                if (senderLoc == null) {
                    continue;
                }

                sync_initiator = false;
                sync_partner_id = senderId;
                sync_target_loc = senderLoc;
                sync_phase = SYNC_R_RECV;
                sync_remaining = -2;
                standstill = true;
                handleSyncMessage(data);
            }
        }
    }

    private void tryInitSyncWithTower() throws GameActionException {
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
        standstill = true;
    }

    private RobotInfo findNearbyAllyTower() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.type)) {
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
}
