package bot3;

import java.util.ArrayList;
import java.util.HashMap;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * @brief Base class for all Battlecode 2025 entities (robots and towers).
 *
 *        Current version:
 *        Holds the shared map knowledge base, coordinate landmark lists
 *        with timestamps, the full two-way sync state machine, and the
 *        32-bit message protocol used for inter-entity communication.
 */
public abstract class Entity {
    RobotController rc;
    int count;

    MapInfo[][] map;
    final int MAP_HEIGHT;
    final int MAP_WIDTH;

    final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    // ---- coordinate knowledge base (with timestamps) ----

    ArrayList<MapLocation> allyTowers = new ArrayList<>();
    ArrayList<MapLocation> enemyTowers = new ArrayList<>();
    ArrayList<MapLocation> ruins = new ArrayList<>();
    HashMap<MapLocation, Integer> landmarkTimestamps = new HashMap<>();

    static final int COORD_ALLY_TOWER = 0;
    static final int COORD_ENEMY_TOWER = 1;
    static final int COORD_RUIN = 2;

    // ---- sync state ----

    static final int SYNC_THRESHOLD = 50;
    protected int last_sync = 0;

    // I = initiator, R = responder, H = header, D = sending data, RECV = receiving data
    static final int SYNC_IDLE = 0;
    static final int SYNC_I_H1 = 1; // initiate header 1
    static final int SYNC_I_H2 = 2; // initiate header 2
    static final int SYNC_I_DATA = 3; // sending data
    static final int SYNC_I_DONE = 4; // done sending
    static final int SYNC_I_RECV = 5; // receiving data
    static final int SYNC_R_RECV = 6; // receiving data
    static final int SYNC_R_H1 = 7; // responder header 1
    static final int SYNC_R_H2 = 8; // responder header 2
    static final int SYNC_R_DATA = 9; // sending data
    static final int SYNC_R_DONE = 10; // done sending

    protected int sync_phase = SYNC_IDLE;
    protected boolean sync_initiator = false;
    protected int sync_partner_id = -1;
    protected MapLocation sync_target_loc = null;
    protected int[] sync_payload = null;
    protected int sync_cursor = 0;
    protected int sync_remaining = 0;
    protected int sync_partner_last_sync = 0;

    // ---- constructor ----

    /**
     * @brief            Construct an entity, initialize the map grid, and
     *                   perform the first scan.  If this entity is a tower,
     *                   its own location is registered as a known ally tower.
     * @param rc         The RobotController for this entity.
     */
    public Entity(RobotController rc){
        this.rc = rc;
        this.count = 1;

        MAP_HEIGHT = rc.getMapHeight();
        MAP_WIDTH = rc.getMapWidth();
        map = new MapInfo[MAP_WIDTH][MAP_HEIGHT];

        if(isTowerType(rc.getType())){
            allyTowers.add(rc.getLocation());
            landmarkTimestamps.put(rc.getLocation(), 0);
        }
    }

    /**
     * @brief            Base turn loop. Increments the turn counter and
     *                   yields. Subclasses must implement this.
     * @throws GameActionException if a game action fails.
     */
    public abstract void run() throws GameActionException;

    // ---- map helpers ----

    /**
     * @brief            Sense all tiles within vision range and update the
     *                   internal map grid. Tiles containing ruins are
     *                   recorded as COORD_RUIN landmarks. After the tile
     *                   sweep, senseLandmarks() detects nearby towers.
     * @throws GameActionException if senseNearbyMapInfos fails.
     */
    public void scan() throws GameActionException {
        int now = rc.getRoundNum();
        MapInfo[] surrounding = rc.senseNearbyMapInfos();
        for(MapInfo info : surrounding){
            MapLocation loc = info.getMapLocation();
            map[loc.x][loc.y] = info;
            if(info.hasRuin()) addLandmark(COORD_RUIN, loc, now);
        }
        senseLandmarks();
    }

    /**
     * @brief            Clear the entire map grid to null and perform an
     *                   initial scan(). Called once from the constructor.
     * @throws GameActionException if scan() fails.
     */
    public void init_map() throws GameActionException {
        for(int i = 0; i < MAP_WIDTH; i++)
            for(int j = 0; j < MAP_HEIGHT; j++)
                map[i][j] = null;
        scan();
    }

    /**
     * @brief            Merge another entity's map into this one. Only
     *                   fills in tiles that are currently unknown (null)
     *                   in this entity's map.
     * @param otherMap   The source map to merge from.
     */
    public void updateMap(MapInfo[][] otherMap){
        for(int i = 0; i < MAP_WIDTH; i++)
            for(int j = 0; j < MAP_HEIGHT; j++)
                if(map[i][j] == null && otherMap[i][j] != null)
                    map[i][j] = otherMap[i][j];
    }

    // ---- landmarks ----

    /**
     * @brief            Check whether a UnitType is a tower (i.e. not a
     *                   Soldier, Mopper, or Splasher).
     * @param t          The UnitType to test.
     * @return           true if the type is a tower variant.
     */
    static boolean isTowerType(UnitType t){
        return t != UnitType.SOLDIER && t != UnitType.MOPPER && t != UnitType.SPLASHER;
    }

    /**
     * @brief            Check whether this entity itself is a tower.
     * @return           true if this entity's UnitType is a tower variant.
     */
    boolean isTower(){ return isTowerType(rc.getType()); }

    /**
     * @brief            Register a landmark coordinate with a discovery
     *                   timestamp.  Deduplicates against existing entries.
     *                   Incoming info is treated as current truth: adding
     *                   an ally tower removes any enemy tower or ruin at
     *                   that location (and vice-versa for enemy towers).
     *                   COORD_RUIN is guarded against known towers so that
     *                   scan()'s hasRuin() (true even under active towers)
     *                   does not erase valid tower entries; tower destruction
     *                   is instead handled by senseLandmarks() and by
     *                   applyEntry()/receiveInlineReport() which pre-clear
     *                   conflicting tower entries before calling this method.
     *                   Fires onNewLandmark() if the entry is genuinely new.
     * @param type       COORD_ALLY_TOWER, COORD_ENEMY_TOWER, or COORD_RUIN.
     * @param loc        The map location of the landmark.
     * @param timestamp  The round number when this intel was acquired.
     * @return           true if the entry was new and was added.
     */
    protected boolean addLandmark(int type, MapLocation loc, int timestamp){
        boolean isNew = false;
        switch(type){
            case COORD_ALLY_TOWER:
                isNew = addUniqueLocation(allyTowers, loc);
                if(isNew){
                    ruins.remove(loc);
                    enemyTowers.remove(loc);
                }
                break;
            case COORD_ENEMY_TOWER:
                isNew = addUniqueLocation(enemyTowers, loc);
                if(isNew){
                    ruins.remove(loc);
                    allyTowers.remove(loc);
                }
                break;
            case COORD_RUIN:
                if(!allyTowers.contains(loc) && !enemyTowers.contains(loc))
                    isNew = addUniqueLocation(ruins, loc);
                break;
        }
        if(isNew){
            landmarkTimestamps.put(loc, timestamp);
            onNewLandmark(packEntry(type, loc));
        }
        return isNew;
    }

    /**
     * @brief            Hook called whenever a genuinely new landmark is
     *                   stored. No-op in Entity; Tower overrides this to
     *                   queue the entry for immediate broadcast to nearby
     *                   towers.
     * @param packedEntry The 14-bit packed entry (type + x + y).
     */
    protected void onNewLandmark(int packedEntry){}

    /**
     * @brief            Detect ally and enemy towers within vision range
     *                   and register them as landmarks.  After registering
     *                   visible towers, checks all previously known tower
     *                   locations that fall inside vision range: if a known
     *                   tower is no longer visible, it is downgraded to a
     *                   COORD_RUIN (tower was destroyed).  Called at the
     *                   end of scan().
     * @throws GameActionException if senseNearbyRobots fails.
     */
    protected void senseLandmarks() throws GameActionException {
        int now = rc.getRoundNum();

        ArrayList<MapLocation> seenAlly = new ArrayList<>();
        ArrayList<MapLocation> seenEnemy = new ArrayList<>();

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for(RobotInfo r : allies)
            if(isTowerType(r.type)){
                addLandmark(COORD_ALLY_TOWER, r.location, now);
                seenAlly.add(r.location);
            }
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
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
     * @brief            Append a location to a list only if it is not
     *                   already present.
     * @param list       The target list.
     * @param loc        The location to add.
     * @return           true if the location was new and was appended.
     */
    protected boolean addUniqueLocation(ArrayList<MapLocation> list, MapLocation loc){
        if(!list.contains(loc)){ list.add(loc); return true; }
        return false;
    }

    // ========================================================================
    //  MESSAGE PROTOCOL
    //
    //  Bits 31-28: [source(1)][nocoord(1)][sync(1)][reserved(1)]
    //    x110 = sync header
    //    x111 = inline report
    //    x100 = standstill
    //    x000 = resume
    //
    //  Two sync headers per phase (distinguished by bit 27):
    //    Time header  (bit27=0): x110 0 | last_sync_time(12) | spare(15)
    //    Count header (bit27=1): x110 1 | entry_count(8)     | spare(19)
    //
    //  Sync data (full 32 bits): [entry1(14)][entry2(14)][valid(2)][spare(2)]
    //  Entry = [type(2)][x(6)][y(6)]
    //
    //  Inline report: x111 | type(2) | x(6) | y(6) | spare(14)
    // ========================================================================

    /** @brief Test whether bits 30-28 match the sync header pattern (x110). */
    static boolean isSyncHeader(int msg)      { return ((msg >>> 28) & 0x7) == 0x6; }
    /** @brief Test for sync time header (x110, bit27=0). */
    static boolean isSyncTimeHeader(int msg)  { return isSyncHeader(msg) && ((msg >>> 27) & 1) == 0; }
    /** @brief Test for sync count header (x110, bit27=1). */
    static boolean isSyncCountHeader(int msg) { return isSyncHeader(msg) && ((msg >>> 27) & 1) == 1; }
    /** @brief Test for inline report (x111). */
    static boolean isInlineReport(int msg)    { return ((msg >>> 28) & 0x7) == 0x7; }
    /** @brief Test for standstill command (x100). */
    static boolean isStandstill(int msg)      { return ((msg >>> 28) & 0x7) == 0x4; }
    /** @brief Test for resume command (x000). */
    static boolean isResume(int msg)          { return ((msg >>> 28) & 0x7) == 0x0; }

    /**
     * @brief            Extract the 12-bit last_sync_time from a sync time
     *                   header.
     * @param msg        The raw 32-bit message.
     * @return           The last_sync round number (0-4095).
     */
    static int parseSyncTime(int msg)  { return (msg >>> 15) & 0xFFF; }

    /**
     * @brief            Extract the 8-bit entry count from a sync count
     *                   header.
     * @param msg        The raw 32-bit message.
     * @return           The number of entries to follow (0-255).
     */
    static int parseSyncCount(int msg) { return (msg >>> 19) & 0xFF; }

    /**
     * @brief            Build a sync time header carrying this entity's
     *                   last_sync round. The receiver uses this to filter
     *                   its response payload (delta sync).
     * @param lastSyncTime The round number to embed.
     * @return           The assembled 32-bit message.
     */
    int buildSyncTimeHeader(int lastSyncTime){
        int msg = isTower() ? 0 : (1 << 31);
        msg |= (0x6 << 28);
        msg |= (lastSyncTime & 0xFFF) << 15;
        return msg;
    }

    /**
     * @brief            Build a sync count header telling the receiver how
     *                   many packed entries will follow in data messages.
     * @param entryCount The number of entries (0-255).
     * @return           The assembled 32-bit message.
     */
    int buildSyncCountHeader(int entryCount){
        int msg = isTower() ? 0 : (1 << 31);
        msg |= (0x6 << 28) | (1 << 27);
        msg |= (entryCount & 0xFF) << 19;
        return msg;
    }

    /**
     * @brief            Build an inline report message carrying a single
     *                   landmark coordinate. Does not trigger standstill
     *                   on the receiver.
     * @param type       COORD_ALLY_TOWER, COORD_ENEMY_TOWER, or COORD_RUIN.
     * @param x          The x-coordinate (0-63).
     * @param y          The y-coordinate (0-63).
     * @return           The assembled 32-bit message.
     */
    int buildInlineReport(int type, int x, int y){
        int msg = isTower() ? 0 : (1 << 31);
        msg |= (0x7 << 28);
        msg |= (type & 0x3) << 26;
        msg |= (x & 0x3F) << 20;
        msg |= (y & 0x3F) << 14;
        return msg;
    }

    /**
     * @brief            Build a standstill command (x100). Tells a robot
     *                   to halt movement until a resume is received.
     * @return           The assembled 32-bit message.
     */
    int buildStandstillMsg(){
        int msg = isTower() ? 0 : (1 << 31);
        msg |= (0x4 << 28);
        return msg;
    }

    /**
     * @brief            Build a resume command (x000). Clears standstill
     *                   on the receiver and signals sync phase transitions.
     * @return           The assembled 32-bit message.
     */
    int buildResumeMsg(){ return isTower() ? 0 : (1 << 31); }

    // ---- entry packing ----

    /**
     * @brief            Pack a landmark into a 14-bit entry: [type(2)][x(6)][y(6)].
     * @param type       COORD_ALLY_TOWER, COORD_ENEMY_TOWER, or COORD_RUIN.
     * @param loc        The map location.
     * @return           The packed 14-bit entry.
     */
    static int packEntry(int type, MapLocation loc){
        return ((type & 0x3) << 12) | ((loc.x & 0x3F) << 6) | (loc.y & 0x3F);
    }
    /** @brief Extract the 2-bit type from a packed entry. */
    static int entryType(int e) { return (e >> 12) & 0x3;  }
    /** @brief Extract the 6-bit x-coordinate from a packed entry. */
    static int entryX(int e)    { return (e >> 6)  & 0x3F; }
    /** @brief Extract the 6-bit y-coordinate from a packed entry. */
    static int entryY(int e)    { return e         & 0x3F; }

    /**
     * @brief            Pack two entries and a valid-count into a 32-bit
     *                   sync data message: [e1(14)][e2(14)][valid(2)][spare(2)].
     * @param e1         First packed entry.
     * @param e2         Second packed entry (ignored when valid==1).
     * @param valid      Number of valid entries in this message (1 or 2).
     * @return           The assembled 32-bit data message.
     */
    static int packSyncData(int e1, int e2, int valid){
        return (e1 << 18) | (e2 << 4) | ((valid & 0x3) << 2);
    }
    /** @brief Extract the first entry from a sync data message. */
    static int syncEntry1(int msg) { return (msg >>> 18) & 0x3FFF; }
    /** @brief Extract the second entry from a sync data message. */
    static int syncEntry2(int msg) { return (msg >>>  4) & 0x3FFF; }
    /** @brief Extract the valid-count (1 or 2) from a sync data message. */
    static int syncValid(int msg)  { return (msg >>>  2) & 0x3;    }

    // ---- sync payload builders ----

    /**
     * @brief            Pack every known landmark (ally towers, enemy towers,
     *                   ruins) into an array of 14-bit entries. Used by the
     *                   tower-tower sync initiator which sends all knowledge.
     * @return           An int array of packed entries.
     */
    protected int[] buildSyncPayloadAll(){
        int n = allyTowers.size() + enemyTowers.size() + ruins.size();
        int[] out = new int[n];
        int i = 0;
        for(MapLocation l : allyTowers)  out[i++] = packEntry(COORD_ALLY_TOWER, l);
        for(MapLocation l : enemyTowers) out[i++] = packEntry(COORD_ENEMY_TOWER, l);
        for(MapLocation l : ruins)       out[i++] = packEntry(COORD_RUIN, l);
        return out;
    }

    /**
     * @brief            Pack only landmarks whose timestamp is strictly
     *                   greater than the given cutoff. Used by the sync
     *                   responder to send a delta: only entries the partner
     *                   doesn't yet know.
     * @param cutoffTime Entries with timestamp <= cutoffTime are excluded.
     * @return           An int array of packed entries.
     */
    protected int[] buildSyncPayloadFiltered(int cutoffTime){
        ArrayList<Integer> entries = new ArrayList<>();
        for(MapLocation l : allyTowers){
            Integer ts = landmarkTimestamps.get(l);
            if(ts != null && ts > cutoffTime) entries.add(packEntry(COORD_ALLY_TOWER, l));
        }
        for(MapLocation l : enemyTowers){
            Integer ts = landmarkTimestamps.get(l);
            if(ts != null && ts > cutoffTime) entries.add(packEntry(COORD_ENEMY_TOWER, l));
        }
        for(MapLocation l : ruins){
            Integer ts = landmarkTimestamps.get(l);
            if(ts != null && ts > cutoffTime) entries.add(packEntry(COORD_RUIN, l));
        }
        int[] out = new int[entries.size()];
        for(int i = 0; i < out.length; i++) out[i] = entries.get(i);
        return out;
    }

    // ---- sync state machine: sending (one step per turn) ----

    /**
     * @brief            Advance the send side of the two-way sync state
     *                   machine by one step. Called once per turn from
     *                   run(). Handles both initiator phases (I_H1..I_DONE)
     *                   and responder phases (R_H1..R_DONE).
     *
     *                   Initiator flow: H1 -> H2 -> DATA -> DONE(resume) -> I_RECV.
     *                   Responder flow: H1 -> H2 -> DATA -> DONE(resume) -> endSync.
     *
     * @throws GameActionException if sendMessage fails.
     */
    protected void handleSyncSend() throws GameActionException {
        if(sync_target_loc == null || sync_phase == SYNC_IDLE) return;

        switch(sync_phase){
            case SYNC_I_H1: case SYNC_R_H1: {
                int m = buildSyncTimeHeader(last_sync);
                if(rc.canSendMessage(sync_target_loc, m)){
                    rc.sendMessage(sync_target_loc, m);
                    sync_phase++;
                }
                break;
            }
            case SYNC_I_H2: case SYNC_R_H2: {
                int cnt = sync_payload != null ? sync_payload.length : 0;
                int m = buildSyncCountHeader(cnt);
                if(rc.canSendMessage(sync_target_loc, m)){
                    rc.sendMessage(sync_target_loc, m);
                    sync_phase++;
                }
                break;
            }
            case SYNC_I_DATA: case SYNC_R_DATA: {
                if(sync_payload == null || sync_cursor >= sync_payload.length){
                    sync_phase++;
                    return;
                }
                int e1 = sync_payload[sync_cursor], e2 = 0, valid = 1;
                if(sync_cursor + 1 < sync_payload.length){
                    e2 = sync_payload[sync_cursor + 1]; valid = 2;
                }
                int m = packSyncData(e1, e2, valid);
                if(rc.canSendMessage(sync_target_loc, m)){
                    rc.sendMessage(sync_target_loc, m);
                    sync_cursor += valid;
                }
                break;
            }
            case SYNC_I_DONE: {
                int m = buildResumeMsg();
                if(rc.canSendMessage(sync_target_loc, m)){
                    rc.sendMessage(sync_target_loc, m);
                    sync_phase = SYNC_I_RECV;
                    sync_remaining = -2;
                }
                break;
            }
            case SYNC_R_DONE: {
                int m = buildResumeMsg();
                if(rc.canSendMessage(sync_target_loc, m)){
                    rc.sendMessage(sync_target_loc, m);
                    endSync();
                }
                break;
            }
        }
    }

    // ---- sync state machine: receiving (called from processMessages) ----

    /**
     * @brief            Process a single message from the sync partner
     *                   during an active sync. Dispatches based on
     *                   message type:
     *                   - Time header:  store partner's last_sync.
     *                   - Count header: set sync_remaining.
     *                   - Resume:       end sync (initiator) or prepare
     *                                   the response payload (responder).
     *                   - Data:         unpack entries via applyEntry().
     * @param data       The raw 32-bit message payload.
     */
    protected void handleSyncMessage(int data){
        if(isSyncTimeHeader(data)){
            sync_partner_last_sync = parseSyncTime(data);
            sync_remaining = -1;
            return;
        }
        if(isSyncCountHeader(data)){
            sync_remaining = parseSyncCount(data);
            return;
        }
        if(isResume(data)){
            if(sync_initiator && sync_phase == SYNC_I_RECV){
                endSync();
            } else if(!sync_initiator && sync_phase == SYNC_R_RECV){
                prepareResponse();
            }
            return;
        }
        if(sync_remaining > 0){
            int valid = syncValid(data);
            if(valid >= 1){ applyEntry(syncEntry1(data)); sync_remaining--; }
            if(valid >= 2){ applyEntry(syncEntry2(data)); sync_remaining--; }
        }
    }

    /**
     * @brief            Build the responder's outgoing payload, filtered to
     *                   only entries newer than the initiator's last_sync,
     *                   and transition to the responder send phase (R_H1).
     */
    protected void prepareResponse(){
        sync_payload = buildSyncPayloadFiltered(sync_partner_last_sync);
        sync_cursor = 0;
        sync_phase = SYNC_R_H1;
    }

    /**
     * @brief            Decode a 14-bit packed entry and store it as a
     *                   landmark with the current round as timestamp.
     *                   Received info is treated as current truth: if the
     *                   entry is a COORD_RUIN, any conflicting tower entry
     *                   at that location is removed first so the ruin can
     *                   be recorded (tower was destroyed).
     *                   Bounds-checked against MAP_WIDTH / MAP_HEIGHT.
     * @param entry      The packed entry (type + x + y).
     */
    protected void applyEntry(int entry){
        int type = entryType(entry);
        int x = entryX(entry), y = entryY(entry);
        if(x >= MAP_WIDTH || y >= MAP_HEIGHT) return;
        MapLocation loc = new MapLocation(x, y);
        if(type == COORD_RUIN){
            allyTowers.remove(loc);
            enemyTowers.remove(loc);
        }
        addLandmark(type, loc, rc.getRoundNum());
    }

    /**
     * @brief            Parse an inline report message (x111) and store the
     *                   contained coordinate as a landmark. Received info
     *                   is treated as current truth: if the entry is a
     *                   COORD_RUIN, any conflicting tower entry at that
     *                   location is removed first.
     * @param msg        The raw 32-bit inline report message.
     */
    protected void receiveInlineReport(int msg){
        int type = (msg >>> 26) & 0x3;
        int x    = (msg >>> 20) & 0x3F;
        int y    = (msg >>> 14) & 0x3F;
        if(x < MAP_WIDTH && y < MAP_HEIGHT){
            MapLocation loc = new MapLocation(x, y);
            if(type == COORD_RUIN){
                allyTowers.remove(loc);
                enemyTowers.remove(loc);
            }
            addLandmark(type, loc, rc.getRoundNum());
        }
    }

    /**
     * @brief            Reset all sync state variables to idle and update
     *                   last_sync to the current round. Called when the
     *                   two-way exchange completes (by either party).
     *                   Robot overrides this to also clear standstill and
     *                   advance shared-entry indices.
     */
    protected void endSync(){
        sync_phase = SYNC_IDLE;
        sync_payload = null;
        sync_target_loc = null;
        sync_cursor = 0;
        sync_partner_id = -1;
        sync_remaining = 0;
        sync_partner_last_sync = 0;
        sync_initiator = false;
        last_sync = rc.getRoundNum();
    }

    /**
     * @brief            Check whether this entity's landmark knowledge is
     *                   outdated (more than SYNC_THRESHOLD rounds since the
     *                   last completed sync).
     * @return           true if info is stale.
     */
    protected boolean isInfoStale(){
        return rc.getRoundNum() - last_sync >= SYNC_THRESHOLD;
    }

    /**
     * @brief            Check whether conditions are met to start a new
     *                   sync: must be idle and info must be stale.
     * @return           true if a sync should be initiated.
     */
    protected boolean shouldInitSync(){
        return sync_phase == SYNC_IDLE && isInfoStale();
    }
}