package bot1;

import battlecode.common.*;
import java.util.ArrayList;

/**
 * @brief Stationary tower entity.
 *
 *        current version:
 *        Acts as a knowledge hub: receives intel from robots via sync or
 *        inline reports and immediately relays new entries to all nearby
 *        ally towers via broadcast. Also initiates two-way syncs with
 *        neighboring towers when its own info is stale, and spawns robots
 *        when funds allow.
 */
public class Tower extends Entity {
    final int MONEY_THRESHOLD = 1000;

    int level;
    int lastSpawned;

    UnitType[] spawnOrder = {UnitType.MOPPER, UnitType.SOLDIER, UnitType.SPLASHER};

    private ArrayList<Integer> pendingBroadcast = new ArrayList<>();

    /**
     * @brief            Constructor.
     * @param rc         The RobotController for this tower.
     * @param level      Starting upgrade level.
     */
    public Tower(RobotController rc, int level){
        super(rc);
        this.level = level;
        this.lastSpawned = 0;
    }

    /**
     * @brief            Main turn loop for a tower.  Processes incoming
     *                   messages, scans every 10 turns, drives the sync
     *                   state machine (or initiates tower-tower sync when
     *                   idle and stale), broadcasts newly received intel
     *                   to nearby towers, and spawns a robot if funds
     *                   exceed MONEY_THRESHOLD.
     * @throws GameActionException if a game action fails.
     */
    @Override
    public void run() throws GameActionException {
        count++;

        processMessages();
        if(count % 10 == 0) scan();

        if(sync_phase != SYNC_IDLE){
            updateSyncTarget();
            handleSyncSend();
        } else if(shouldInitSync()){
            trySyncWithNearbyTower();
        }

        broadcastToTowers();

        if(rc.getMoney() > MONEY_THRESHOLD){
            for(Direction dir : directions){
                MapLocation spawnLoc = rc.getLocation().add(dir);
                if(rc.canBuildRobot(spawnOrder[lastSpawned], spawnLoc)){
                    rc.buildRobot(spawnOrder[lastSpawned], spawnLoc);
                    lastSpawned = (lastSpawned + 1) % spawnOrder.length;
                    break;
                }
            }
        }
    }

    // ---- message handling ----

    /**
     * @brief            Read all pending messages and dispatch them.
     *                   During an active sync, partner messages are routed
     *                   to handleSyncMessage(). A sync time header from a
     *                   new sender (while idle) starts the responder flow:
     *                   the tower enters SYNC_R_RECV, stores the partner's
     *                   last_sync, and locates the partner. Inline reports
     *                   are applied via receiveInlineReport().
     */
    private void processMessages(){
        Message[] messages = rc.readMessages(-1);
        for(Message m : messages){
            int data = m.getBytes();

            if(sync_phase != SYNC_IDLE && m.getSenderID() == sync_partner_id){
                handleSyncMessage(data);
                continue;
            }

            if(isSyncTimeHeader(data) && sync_phase == SYNC_IDLE){
                sync_partner_id = m.getSenderID();
                sync_partner_last_sync = parseSyncTime(data);
                sync_remaining = -1;
                sync_phase = SYNC_R_RECV;
                sync_initiator = false;
                if(rc.canSenseRobot(sync_partner_id)){
                    try { sync_target_loc = rc.senseRobot(sync_partner_id).location; }
                    catch(GameActionException e){}
                }
                continue;
            }

            if(isInlineReport(data)){
                receiveInlineReport(data);
                continue;
            }
        }
    }

    // ---- tower-tower sync initiation ----

    /**
     * @brief            Attempt to start a two-way sync with the nearest
     *                   ally tower. Sends all known landmarks (unfiltered)
     *                   since the initiator does not know the partner's
     *                   last_sync before the exchange begins. The partner
     *                   will respond with a filtered delta.
     * @throws GameActionException if senseNearbyRobots fails.
     */
    private void trySyncWithNearbyTower() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for(RobotInfo r : allies){
            if(isTowerType(r.type)){
                sync_payload = buildSyncPayloadAll();
                sync_target_loc = r.location;
                sync_partner_id = r.ID;
                sync_initiator = true;
                sync_cursor = 0;
                sync_phase = SYNC_I_H1;
                return;
            }
        }
    }

    // ---- immediate relay of new intel to nearby towers ----

    /**
     * @brief            Hook called by addLandmark() whenever a genuinely
     *                   new landmark is stored. Queues the packed entry
     *                   for broadcast to nearby towers on this turn.
     * @param packedEntry The 14-bit packed entry (type + x + y).
     */
    @Override
    protected void onNewLandmark(int packedEntry){
        if(pendingBroadcast != null) pendingBroadcast.add(packedEntry);
    }

    /**
     * @brief            Broadcast an inline report for each queued entry to
     *                   all nearby entities using the tower's broadcastMessage
     *                   API. Clears the queue after one pass.
     * @throws GameActionException if broadcastMessage fails.
     */
    private void broadcastToTowers() throws GameActionException {
        if(pendingBroadcast.isEmpty()) return;
        for(int packed : pendingBroadcast){
            int type = entryType(packed);
            int x = entryX(packed), y = entryY(packed);
            int msg = buildInlineReport(type, x, y);
            if(rc.canBroadcastMessage()) rc.broadcastMessage(msg);
        }
        pendingBroadcast.clear();
    }

    /**
     * @brief            Refresh sync_target_loc for mobile partners
     *                   (robots) whose location may change between turns.
     *                   If the partner moved out of sensing range, the sync
     *                   is aborted via endSync().
     */
    private void updateSyncTarget(){
        if(sync_partner_id < 0) return;
        if(rc.canSenseRobot(sync_partner_id)){
            try {
                sync_target_loc = rc.senseRobot(sync_partner_id).location;
            } catch(GameActionException e){
                endSync();
            }
        } else {
            endSync();
        }
    }
}
