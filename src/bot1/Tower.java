package bot1;

import battlecode.common.*;
import java.util.ArrayList;

/**
 * @brief Stationary tower entity.
 *
 *        Acts as a coordination hub: receives intel from robots via sync
 *        or inline reports, relays new entries to all nearby entities via
 *        broadcastMessage, attacks enemies in range, issues gank commands
 *        when enough ally robots are idle and an enemy tower is known, and
 *        spawns robots (Soldier -> Mopper -> Splasher cycle).
 *
 *        Bytecode-optimised: senses allies/enemies once per turn and
 *        caches the results; skips the full map-grid scan that only
 *        robots need for pathfinding.
 */
public class Tower extends Entity {

    // ---- constants ----

    /** @brief Minimum team money required before spawning a robot. */
    static final int MONEY_THRESHOLD = 300;

    /** @brief Minimum turns between consecutive spawns (0 = game-native cooldown only). */
    static final int SPAWN_COOLDOWN = 0;

    /** @brief Minimum turns between consecutive gank broadcasts. */
    static final int GANK_COOLDOWN = 40;

    /** @brief Minimum ally robots in vision to trigger a gank. */
    static final int GANK_ALLY_THRESHOLD = 3;

    /** @brief How often (in turns) to run the lightweight landmark scan. */
    static final int SCAN_INTERVAL = 25;

    // ---- state ----

    int level;
    int spawnIndex = 0;
    int lastSpawnRound = -SPAWN_COOLDOWN;

    /** @brief Spawn order: soldier first, then mopper, then splasher. */
    UnitType[] spawnOrder = {UnitType.SOLDIER, UnitType.MOPPER, UnitType.SPLASHER};

    private ArrayList<Integer> pendingBroadcast = new ArrayList<>();

    private MapLocation lastGankTarget = null;
    private int lastGankRound = -GANK_COOLDOWN;

    // Per-turn cached sense results to avoid duplicate API calls.
    private RobotInfo[] cachedAllies;
    private RobotInfo[] cachedEnemies;

    /**
     * @brief            Constructor.
     * @param rc         The RobotController for this tower.
     * @param level      Starting upgrade level.
     */
    public Tower(RobotController rc, int level){
        super(rc);
        this.level = level;
    }

    /**
     * @brief            Towers don't pathfind, so skip the expensive
     *                   full-grid initialisation that Entity.init_map()
     *                   performs. Only run a lightweight landmark scan.
     */
    @Override
    public void init_map() throws GameActionException {
        scanLandmarks();
    }

    /**
     * @brief            Lightweight scan for towers: detect nearby ally
     *                   and enemy towers and ruins without iterating
     *                   every visible tile into the map grid.
     * @throws GameActionException if sensing fails.
     */
    private void scanLandmarks() throws GameActionException {
        int now = rc.getRoundNum();

        MapLocation[] nearbyRuins = rc.senseNearbyRuins(-1);
        for(MapLocation r : nearbyRuins){
            addLandmark(COORD_RUIN, r, now);
        }

        RobotInfo[] allies = cachedAllies != null
                ? cachedAllies
                : rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo[] enemies = cachedEnemies != null
                ? cachedEnemies
                : rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        ArrayList<MapLocation> seenAlly = new ArrayList<>();
        ArrayList<MapLocation> seenEnemy = new ArrayList<>();

        for(RobotInfo r : allies)
            if(isTowerType(r.type)){
                addLandmark(COORD_ALLY_TOWER, r.location, now);
                seenAlly.add(r.location);
            }
        for(RobotInfo r : enemies)
            if(isTowerType(r.type)){
                addLandmark(COORD_ENEMY_TOWER, r.location, now);
                seenEnemy.add(r.location);
            }

        ArrayList<MapLocation> destroyed = new ArrayList<>();
        for(MapLocation loc : allyTowers)
            if(rc.canSenseLocation(loc) && !seenAlly.contains(loc))
                destroyed.add(loc);
        for(MapLocation loc : enemyTowers)
            if(rc.canSenseLocation(loc) && !seenEnemy.contains(loc))
                destroyed.add(loc);
        for(MapLocation loc : destroyed){
            allyTowers.remove(loc);
            enemyTowers.remove(loc);
            addLandmark(COORD_RUIN, loc, now);
        }
    }

    /**
     * @brief            Main turn loop.  Senses allies and enemies once,
     *                   then processes messages, scans landmarks on
     *                   interval, drives sync/broadcast, attacks, evaluates
     *                   gank, and spawns.  trySpawn is called first to
     *                   guarantee it runs before bytecodes are exhausted.
     * @throws GameActionException if a game action fails.
     */
    @Override
    public void run() throws GameActionException {
        count++;

        cachedAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        cachedEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        trySpawn();
        attackEnemies();

        processMessages();
        if(count % SCAN_INTERVAL == 0) scanLandmarks();

        if(sync_phase != SYNC_IDLE){
            if(rc.getRoundNum() - sync_start_round > SYNC_TIMEOUT){
                endSync();
            } else {
                updateSyncTarget();
                handleSyncSend();
            }
        } else if(shouldInitSync()){
            trySyncWithNearbyTower();
        }

        broadcastToTowers();
        tryGank();

        cachedAllies = null;
        cachedEnemies = null;
    }

    // ---- message handling ----

    /**
     * @brief            Read all pending messages and dispatch them.
     *                   During an active sync, partner messages go to
     *                   handleSyncMessage(). Otherwise:
     *                   - Gank (x001): re-broadcast to propagate, store
     *                     target and round (de-duped by lastGankTarget).
     *                   - SyncTimeHeader (x110,bit27=0): starts the
     *                     responder sync flow.
     *                   - InlineReport (x111): stored via
     *                     receiveInlineReport().
     */
    private void processMessages(){
        Message[] messages = rc.readMessages(-1);
        for(Message m : messages){
            int data = m.getBytes();

            if(sync_phase != SYNC_IDLE && m.getSenderID() == sync_partner_id){
                handleSyncMessage(data);
                continue;
            }

            if(isGank(data)){
                int gx = parseGankX(data), gy = parseGankY(data);
                if(gx < MAP_WIDTH && gy < MAP_HEIGHT){
                    MapLocation target = new MapLocation(gx, gy);
                    if(!target.equals(lastGankTarget)){
                        lastGankTarget = target;
                        lastGankRound = rc.getRoundNum();
                        try{ if(rc.canBroadcastMessage()) rc.broadcastMessage(data); }
                        catch(GameActionException e){}
                    }
                }
                continue;
            }

            if(isSyncTimeHeader(data) && sync_phase == SYNC_IDLE){
                sync_partner_id = m.getSenderID();
                sync_partner_last_sync = parseSyncTime(data);
                sync_remaining = -1;
                sync_phase = SYNC_R_RECV;
                sync_start_round = rc.getRoundNum();
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

    // ---- tower-tower sync ----

    /**
     * @brief            Attempt to start a two-way sync with the nearest
     *                   ally tower.  Uses the cached allies array.
     * @throws GameActionException if senseNearbyRobots fails.
     */
    private void trySyncWithNearbyTower() throws GameActionException {
        RobotInfo[] allies = cachedAllies != null
                ? cachedAllies
                : rc.senseNearbyRobots(-1, rc.getTeam());
        for(RobotInfo r : allies){
            if(isTowerType(r.type)){
                sync_payload = buildSyncPayloadAll();
                sync_target_loc = r.location;
                sync_partner_id = r.ID;
                sync_initiator = true;
                sync_cursor = 0;
                sync_phase = SYNC_I_H1;
                sync_start_round = rc.getRoundNum();
                return;
            }
        }
    }

    // ---- broadcast relay ----

    /**
     * @brief            Hook called by addLandmark() for every genuinely
     *                   new landmark.  Queues the packed entry for
     *                   broadcast on this turn.
     * @param packedEntry The 14-bit packed entry (type + x + y).
     */
    @Override
    protected void onNewLandmark(int packedEntry){
        if(pendingBroadcast != null) pendingBroadcast.add(packedEntry);
    }

    /**
     * @brief            Broadcast an inline report for each queued entry
     *                   to all nearby entities via broadcastMessage.
     *                   Clears the queue after one pass.
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

    // ---- combat ----

    /**
     * @brief            Attack the first enemy sensed within range.
     *                   Uses the cached enemies array.
     * @throws GameActionException if attack fails.
     */
    private void attackEnemies() throws GameActionException {
        if(cachedEnemies == null) return;
        for(RobotInfo e : cachedEnemies){
            if(rc.canAttack(e.location)){
                rc.attack(e.location);
                break;
            }
        }
    }

    // ---- gank coordination ----

    /**
     * @brief            Evaluate gank conditions and broadcast a gank
     *                   command if met.  Uses the cached allies array.
     * @throws GameActionException if broadcastMessage fails.
     */
    private void tryGank() throws GameActionException {
        if(sync_phase != SYNC_IDLE) return;
        if(enemyTowers.isEmpty()) return;
        if(rc.getRoundNum() - lastGankRound < GANK_COOLDOWN) return;

        RobotInfo[] allies = cachedAllies != null
                ? cachedAllies
                : rc.senseNearbyRobots(-1, rc.getTeam());
        int robotCount = 0;
        for(RobotInfo a : allies){
            if(!isTowerType(a.type)) robotCount++;
        }
        if(robotCount < GANK_ALLY_THRESHOLD) return;

        MapLocation target = null;
        int bestDist = Integer.MAX_VALUE;
        for(MapLocation et : enemyTowers){
            int d = rc.getLocation().distanceSquaredTo(et);
            if(d < bestDist){ bestDist = d; target = et; }
        }

        int msg = buildGankMsg(target.x, target.y);
        if(rc.canBroadcastMessage()){
            rc.broadcastMessage(msg);
            lastGankRound = rc.getRoundNum();
            lastGankTarget = target;
        }
    }

    // ---- spawning ----

    /**
     * @brief            Spawn a robot if money exceeds MONEY_THRESHOLD
     *                   and SPAWN_COOLDOWN has elapsed.  Called early in
     *                   run() to guarantee it executes before bytecodes
     *                   run out.
     * @throws GameActionException if buildRobot fails.
     */
    private void trySpawn() throws GameActionException {
        if(rc.getMoney() <= MONEY_THRESHOLD) return;
        if(rc.getRoundNum() - lastSpawnRound < SPAWN_COOLDOWN) return;

        for(Direction dir : directions){
            MapLocation spawnLoc = rc.getLocation().add(dir);
            if(rc.canBuildRobot(spawnOrder[spawnIndex], spawnLoc)){
                rc.buildRobot(spawnOrder[spawnIndex], spawnLoc);
                spawnIndex = (spawnIndex + 1) % spawnOrder.length;
                lastSpawnRound = rc.getRoundNum();
                break;
            }
        }
    }

    // ---- sync helpers ----

    /**
     * @brief            Refresh sync_target_loc for mobile partners.
     *                   If the partner is out of sensing range, abort.
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
