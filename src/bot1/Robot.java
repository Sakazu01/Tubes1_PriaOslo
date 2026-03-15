package bot1;

import battlecode.common.*;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * @brief Mobile entity (Soldier, Mopper, Splasher).
 *
 *        Current version:
 *        Handles pathfinding (A* + DFS exploration with fallback protocol),
 *        the initiator side of the two-way sync protocol with towers, and
 *        background inline reporting of known landmarks.
 */
public class Robot extends Entity{

    private MapLocation moveTarget;
    private LinkedList<MapLocation> plannedPath;
    private boolean exploring;

    // DFS exploration state
    private HashSet<MapLocation> exploreVisited = new HashSet<>();
    private LinkedList<MapLocation> exploreStack = new LinkedList<>();

    protected boolean standstill = false;

    // gank state
    protected MapLocation gankTarget = null;
    protected int gankExpiry = 0;
    static final int GANK_DURATION = 40;

    // tracks which entries have been shared with a tower via sync
    private int sharedAllyIdx = 0;
    private int sharedEnemyIdx = 0;
    private int sharedRuinIdx = 0;

    private int reportCursor = 0;

    // A* persistent arrays (allocated once, reused via generation counter)
    private int pathGen = 0;
    private int[] pathOpenGen;
    private int[] pathClosedGen;
    private int[] pathGCost;
    private int[] pathParent;
    private int[] pathHeapF;
    private int[] pathHeapI;
    private int pathHeapSize;

    /**
     * @brief            Constructor.
     * @param rc         The RobotController for this robot.
     */
    public Robot(RobotController rc){
        super(rc);
    }

    /**
     * @brief            Main turn loop for a robot. Processes incoming
     *                   messages, scans every 5 turns, drives the sync
     *                   state machine (or initiates a new sync / sends
     *                   inline reports when idle), then attempts to move.
     * @throws GameActionException if a game action fails.
     */
    @Override
    public void run() throws GameActionException {
        count++;

        processMessages();
        if(count % 5 == 0) scan();

        if(sync_phase != SYNC_IDLE){
            if(rc.getRoundNum() - sync_start_round > SYNC_TIMEOUT
                    || !rc.canSenseRobot(sync_partner_id)){
                endSync();
            } else {
                handleSyncSend();
            }
        } else {
            if(!standstill) tryInitSync();
            if(!standstill) reportToNearbyTower();
        }

        if(gankTarget != null && rc.getRoundNum() > gankExpiry){
            gankTarget = null;
        }
    }

    // ---- message handling ----

    /**
     * @brief            Read all pending messages and dispatch them.
     *                   During an active sync, partner messages are routed
     *                   to handleSyncMessage().  Otherwise:
     *                   - Gank (x001): sets gankTarget and gankExpiry.
     *                   - Standstill (x100): sets standstill flag.
     *                   - Resume (x000): clears standstill (when idle).
     *                   - Inline report (x111): stored via receiveInlineReport().
     */
    protected void processMessages(){
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
                    gankTarget = new MapLocation(gx, gy);
                    gankExpiry = rc.getRoundNum() + GANK_DURATION;
                }
                continue;
            }
            if(isStandstill(data)){ standstill = true; continue; }
            if(isResume(data) && sync_phase == SYNC_IDLE){ standstill = false; continue; }
            if(isInlineReport(data)){ receiveInlineReport(data); continue; }
        }
    }

    // ---- sync initiation (robot as initiator) ----

    /**
     * @brief            Attempt to start a two-way sync with the nearest
     *                   ally tower.  Only proceeds when idle and this
     *                   robot's info is stale (shouldInitSync).  Builds
     *                   the unshared payload, sets the robot as initiator,
     *                   and activates standstill for the duration.
     * @throws GameActionException if senseNearbyRobots fails.
     */
    protected void tryInitSync() throws GameActionException {
        if(sync_phase != SYNC_IDLE || !shouldInitSync()) return;

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for(RobotInfo r : allies){
            if(isTowerType(r.type)){
                sync_payload = buildSyncPayloadUnshared();
                sync_target_loc = r.location;
                sync_partner_id = r.ID;
                sync_initiator = true;
                sync_cursor = 0;
                sync_phase = SYNC_I_H1;
                sync_start_round = rc.getRoundNum();
                standstill = true;
                return;
            }
        }
    }

    /**
     * @brief            Pack all landmark entries that have not yet been
     *                   shared with a tower. Uses per-list indices
     *                   (sharedAllyIdx etc.) to track the frontier between
     *                   shared and unshared entries. All entries beyond the
     *                   index are included regardless of their timestamp.
     * @return           An int array of packed entries.
     */
    protected int[] buildSyncPayloadUnshared(){
        sharedAllyIdx = Math.min(sharedAllyIdx, allyTowers.size());
        sharedEnemyIdx = Math.min(sharedEnemyIdx, enemyTowers.size());
        sharedRuinIdx = Math.min(sharedRuinIdx, ruins.size());
        int n = (allyTowers.size() - sharedAllyIdx) +
                (enemyTowers.size() - sharedEnemyIdx) +
                (ruins.size() - sharedRuinIdx);
        if(n <= 0) return new int[0];
        int[] out = new int[n];
        int i = 0;
        for(int j = sharedAllyIdx; j < allyTowers.size(); j++)
            out[i++] = packEntry(COORD_ALLY_TOWER, allyTowers.get(j));
        for(int j = sharedEnemyIdx; j < enemyTowers.size(); j++)
            out[i++] = packEntry(COORD_ENEMY_TOWER, enemyTowers.get(j));
        for(int j = sharedRuinIdx; j < ruins.size(); j++)
            out[i++] = packEntry(COORD_RUIN, ruins.get(j));
        return out;
    }

    /**
     * @brief            Finalize a completed sync exchange. Advances the
     *                   shared-entry indices to the current list sizes so
     *                   that those entries are not re-sent in the next sync,
     *                   clears standstill, and delegates to Entity.endSync()
     *                   to reset protocol state.
     */
    @Override
    protected void endSync(){
        sharedAllyIdx = allyTowers.size();
        sharedEnemyIdx = enemyTowers.size();
        sharedRuinIdx = ruins.size();
        standstill = false;
        super.endSync();
    }

    // ---- inline reporting to a nearby tower ----

    /**
     * @brief            Send one inline report (x111) per turn to the
     *                   nearest ally tower, cycling through all known
     *                   landmarks via reportCursor. Skipped when a sync
     *                   is active. This provides low-latency, best-effort
     *                   sharing without standstill.
     * @throws GameActionException if sendMessage fails.
     */
    protected void reportToNearbyTower() throws GameActionException {
        if(sync_phase != SYNC_IDLE) return;

        int total = allyTowers.size() + enemyTowers.size() + ruins.size();
        if(total == 0) return;

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation towerLoc = null;
        for(RobotInfo r : allies){
            if(isTowerType(r.type)){ towerLoc = r.location; break; }
        }
        if(towerLoc == null) return;

        reportCursor = reportCursor % total;
        int type; MapLocation loc;
        int idx = reportCursor;

        if(idx < allyTowers.size()){
            type = COORD_ALLY_TOWER; loc = allyTowers.get(idx);
        } else {
            idx -= allyTowers.size();
            if(idx < enemyTowers.size()){
                type = COORD_ENEMY_TOWER; loc = enemyTowers.get(idx);
            } else {
                idx -= enemyTowers.size();
                type = COORD_RUIN; loc = ruins.get(idx);
            }
        }

        int msg = buildInlineReport(type, loc.x, loc.y);
        if(rc.canSendMessage(towerLoc, msg)){
            rc.sendMessage(towerLoc, msg);
            reportCursor++;
        }
    }

    /**
     * @brief            Move one step toward the target using an ant-style
     *                   hybrid strategy.
     *
     *                   The entity's map (populated by scan() and
     *                   updateMap()) is the knowledge base. A* (with
     *                   Chebyshev heuristic) finds the shortest path through
     *                   all known-passable tiles, expanding far fewer nodes
     *                   than BFS to stay within bytecode limits.
     *
     *                   If A* finds a path, follow it. When a step is
     *                   blocked (e.g. by another bot), the fallback protocol
     *                   (forward -> diag-right -> diag-left, rotate left
     *                   90 degrees, repeat) navigates around the obstacle,
     *                   then the bot attempts to rejoin or replan.
     *
     *                   If no path can be planned (destination unseen or
     *                   gaps in map knowledge), the bot explores with DFS
     *                   toward the target. Each turn, scan() reveals new
     *                   terrain, and A* is re-attempted so the bot switches
     *                   to an optimal route as soon as the map allows it.
     *
     *                   Does nothing while standstill is active.
     *
     * @param target     The target location to move to.
     * @throws GameActionException if a game action fails.
     */
    public void move_to(MapLocation target) throws GameActionException {
        if (standstill) return;

        if (rc.getLocation().equals(target)) {
            resetPathState();
            return;
        }

        if (!target.equals(moveTarget)) {
            resetPathState();
            moveTarget = target;
        }

        if (count % 5 == 0) scan();

        if (exploring || plannedPath == null || plannedPath.isEmpty()) {
            LinkedList<MapLocation> path = astar(rc.getLocation(), moveTarget);
            if (path != null) {
                plannedPath = path;
                exploring = false;
                exploreVisited.clear();
                exploreStack.clear();
            } else if (!exploring) {
                startExploring();
            }
        }

        if (!exploring && plannedPath != null && !plannedPath.isEmpty()) {
            followKnownPath();
        } else {
            explore();
        }
    }

    // ---- path planning ----

    /**
     * @brief            Initialize the DFS exploration state. Clears
     *                   previous visited/stack and marks the current
     *                   location as visited.
     */
    private void startExploring() {
        exploring = true;
        exploreVisited.clear();
        exploreStack.clear();
        exploreVisited.add(rc.getLocation());
    }

    // ---- follow known path ----

    /**
     * @brief            Follow the next waypoint on the BFS-planned path.
     *                   If the direct step is blocked, falls back to the
     *                   wall-following protocol and attempts to rejoin or
     *                   replan afterward.
     * @throws GameActionException if a game action fails.
     */
    private void followKnownPath() throws GameActionException {
        MapLocation next = plannedPath.peekFirst();

        if (rc.getLocation().equals(next)) {
            plannedPath.pollFirst();
            if (plannedPath.isEmpty()) return;
            next = plannedPath.peekFirst();
        }

        Direction dir = rc.getLocation().directionTo(next);

        if (rc.canMove(dir)) {
            rc.move(dir);
            plannedPath.pollFirst();
            return;
        }

        if (fallbackMove(dir)) {
            rejoinOrReplan();
        }
    }

    /**
     * @brief            After a fallback move diverges from the planned
     *                   path, attempt to rejoin it. If the current
     *                   location lies on the existing path, skip ahead.
     *                   Otherwise, re-run A* or fall back to DFS
     *                   exploration.
     */
    private void rejoinOrReplan() {
        MapLocation cur = rc.getLocation();

        int idx = plannedPath.indexOf(cur);
        if (idx >= 0) {
            for (int i = 0; i <= idx; i++) plannedPath.pollFirst();
            return;
        }

        LinkedList<MapLocation> repath = astar(cur, moveTarget);
        if (repath != null) {
            plannedPath = repath;
        } else {
            startExploring();
        }
    }

    // ---- DFS exploration ----

    /**
     * @brief            Explore the map using DFS biased toward the move
     *                   target. Tries forward, diag-right, diag-left from
     *                   the heading, then rotates left 90 degrees (x4).
     *                   Only visits tiles not yet in exploreVisited.
     *                   When stuck, backtracks via the exploreStack. As a
     *                   last resort, force-moves in any available direction.
     * @throws GameActionException if a game action fails.
     */
    private void explore() throws GameActionException {
        Direction dir = rc.getLocation().directionTo(moveTarget);

        for (int i = 0; i < 4; i++) {
            if (tryExploreMove(dir))                return;
            if (tryExploreMove(dir.rotateRight()))  return;
            if (tryExploreMove(dir.rotateLeft()))   return;
            dir = dir.rotateLeft().rotateLeft();
        }

        if (!exploreStack.isEmpty()) {
            MapLocation prev = exploreStack.removeLast();
            Direction backDir = rc.getLocation().directionTo(prev);
            if (rc.canMove(backDir)) {
                rc.move(backDir);
                return;
            }
        }

        dir = rc.getLocation().directionTo(moveTarget);
        for (int i = 0; i < 4; i++) {
            if (forceMove(dir))               return;
            if (forceMove(dir.rotateRight()))  return;
            if (forceMove(dir.rotateLeft()))   return;
            dir = dir.rotateLeft().rotateLeft();
        }
    }

    /**
     * @brief            Attempt a DFS exploration move: only succeeds if
     *                   the destination tile is reachable and has not been
     *                   visited. Pushes the current location onto the
     *                   backtrack stack on success.
     * @param d          The direction to try.
     * @return           true if the move was executed.
     * @throws GameActionException if a game action fails.
     */
    private boolean tryExploreMove(Direction d) throws GameActionException {
        MapLocation next = rc.getLocation().add(d);
        if (rc.canMove(d) && !exploreVisited.contains(next)) {
            exploreStack.addLast(rc.getLocation());
            exploreVisited.add(next);
            rc.move(d);
            return true;
        }
        return false;
    }

    /**
     * @brief            Unconditionally move in a direction if possible,
     *                   ignoring visited state. Used as a last-resort
     *                   escape when DFS and backtracking both fail.
     * @param d          The direction to try.
     * @return           true if the move was executed.
     * @throws GameActionException if a game action fails.
     */
    private boolean forceMove(Direction d) throws GameActionException {
        if (rc.canMove(d)) {
            rc.move(d);
            return true;
        }
        return false;
    }

    // ---- fallback protocol ----

    /**
     * @brief            Wall-following fallback when the planned step is
     *                   blocked. Tries forward, diag-right, diag-left
     *                   from the current heading, then rotates the heading
     *                   left 90 degrees and repeats (up to 4 rotations).
     * @param dir        The initial heading direction.
     * @return           true if a move was made.
     * @throws GameActionException if a game action fails.
     */
    private boolean fallbackMove(Direction dir) throws GameActionException {
        for (int i = 0; i < 4; i++) {
            if (rc.canMove(dir))               { rc.move(dir);               return true; }
            if (rc.canMove(dir.rotateRight())) { rc.move(dir.rotateRight()); return true; }
            if (rc.canMove(dir.rotateLeft()))  { rc.move(dir.rotateLeft());  return true; }
            dir = dir.rotateLeft().rotateLeft();
        }
        return false;
    }

    // ---- A* on the map knowledge base ----

    /**
     * @brief            Allocate the persistent A* arrays. Called once on
     *                   the first astar() invocation. Cells are indexed as
     *                   x * MAP_HEIGHT + y. Arrays are reused across calls
     *                   via a generation counter (pathGen) so no per-call
     *                   clearing is needed.
     */
    private void initPathArrays(){
        int sz = MAP_WIDTH * MAP_HEIGHT;
        pathOpenGen = new int[sz];
        pathClosedGen = new int[sz];
        pathGCost = new int[sz];
        pathParent = new int[sz];
        pathHeapF = new int[512];
        pathHeapI = new int[512];
    }

    /**
     * @brief            A* over all known-passable tiles in the map using
     *                   Chebyshev distance as the heuristic (admissible for
     *                   8-directional movement with uniform cost). Uses
     *                   array-based data structures and a binary min-heap
     *                   to minimize bytecode cost per node. Aborts early
     *                   if bytecodes run low (< 1500) to stay within the
     *                   turn budget.
     * @param start      The starting location.
     * @param goal       The goal location.
     * @return           Ordered list of waypoints (excluding start), or
     *                   null if no path exists or budget ran out.
     */
    private LinkedList<MapLocation> astar(MapLocation start, MapLocation goal){
        if(goal.x < 0 || goal.x >= MAP_WIDTH || goal.y < 0 || goal.y >= MAP_HEIGHT) return null;
        if(map[goal.x][goal.y] == null || !map[goal.x][goal.y].isPassable()) return null;

        if(pathOpenGen == null) initPathArrays();
        pathGen++;

        int sx = start.x, sy = start.y, gx = goal.x, gy = goal.y;
        int si = sx * MAP_HEIGHT + sy, gi = gx * MAP_HEIGHT + gy;

        pathOpenGen[si] = pathGen;
        pathGCost[si] = 0;
        pathParent[si] = -1;

        pathHeapSize = 0;
        heapPush(Math.max(Math.abs(gx - sx), Math.abs(gy - sy)), si);

        while(pathHeapSize > 0){
            if(Clock.getBytecodesLeft() < 1500) return null;

            int ci = pathHeapI[0];
            heapPop();

            if(pathClosedGen[ci] == pathGen) continue;
            pathClosedGen[ci] = pathGen;

            if(ci == gi){
                LinkedList<MapLocation> path = new LinkedList<>();
                int pi = gi;
                while(pi != si){
                    path.addFirst(new MapLocation(pi / MAP_HEIGHT, pi % MAP_HEIGHT));
                    pi = pathParent[pi];
                }
                return path;
            }

            int cx = ci / MAP_HEIGHT, cy = ci % MAP_HEIGHT;
            int cg = pathGCost[ci];

            for(Direction d : directions){
                int nx = cx + d.dx, ny = cy + d.dy;
                if(nx < 0 || nx >= MAP_WIDTH || ny < 0 || ny >= MAP_HEIGHT) continue;
                if(map[nx][ny] == null || !map[nx][ny].isPassable()) continue;
                int ni = nx * MAP_HEIGHT + ny;
                if(pathClosedGen[ni] == pathGen) continue;

                int ng = cg + 1;
                if(pathOpenGen[ni] != pathGen || ng < pathGCost[ni]){
                    pathOpenGen[ni] = pathGen;
                    pathGCost[ni] = ng;
                    pathParent[ni] = ci;
                    heapPush(ng + Math.max(Math.abs(gx - nx), Math.abs(gy - ny)), ni);
                }
            }
        }
        return null;
    }

    /**
     * @brief            Push an entry onto the A* binary min-heap.
     *                   Grows the backing arrays if capacity is exceeded.
     * @param f          The f-cost (g + h) of the node.
     * @param idx        The map index (x * MAP_HEIGHT + y).
     */
    private void heapPush(int f, int idx){
        if(pathHeapSize >= pathHeapF.length){
            int nc = pathHeapF.length * 2;
            int[] nf = new int[nc], ni = new int[nc];
            System.arraycopy(pathHeapF, 0, nf, 0, pathHeapSize);
            System.arraycopy(pathHeapI, 0, ni, 0, pathHeapSize);
            pathHeapF = nf; pathHeapI = ni;
        }
        int pos = pathHeapSize++;
        pathHeapF[pos] = f;
        pathHeapI[pos] = idx;
        while(pos > 0){
            int par = (pos - 1) >>> 1;
            if(pathHeapF[par] <= pathHeapF[pos]) break;
            int tf = pathHeapF[par]; pathHeapF[par] = pathHeapF[pos]; pathHeapF[pos] = tf;
            int ti = pathHeapI[par]; pathHeapI[par] = pathHeapI[pos]; pathHeapI[pos] = ti;
            pos = par;
        }
    }

    /**
     * @brief            Pop the minimum entry from the A* binary min-heap.
     *                   The caller reads pathHeapI[0] before calling this.
     */
    private void heapPop(){
        pathHeapSize--;
        pathHeapF[0] = pathHeapF[pathHeapSize];
        pathHeapI[0] = pathHeapI[pathHeapSize];
        int pos = 0;
        while(true){
            int left = (pos << 1) + 1, right = left + 1, sm = pos;
            if(left < pathHeapSize && pathHeapF[left] < pathHeapF[sm]) sm = left;
            if(right < pathHeapSize && pathHeapF[right] < pathHeapF[sm]) sm = right;
            if(sm == pos) break;
            int tf = pathHeapF[pos]; pathHeapF[pos] = pathHeapF[sm]; pathHeapF[sm] = tf;
            int ti = pathHeapI[pos]; pathHeapI[pos] = pathHeapI[sm]; pathHeapI[sm] = ti;
            pos = sm;
        }
    }

    /**
     * @brief            Clear all pathfinding state (target, planned path,
     *                   DFS exploration visited set and backtrack stack).
     */
    private void resetPathState() {
        moveTarget = null;
        plannedPath = null;
        exploring = false;
        exploreVisited.clear();
        exploreStack.clear();
    }

    // ---- paint management ----

    /**
     * @brief            Check whether this robot's paint is below 50% capacity.
     * @return           true if the robot should return to a tower for resupply.
     */
    protected boolean shouldReturnForPaint(){
        return rc.getPaint() < 20;
    }

    /**
     * @brief            Find the nearest known ally tower.
     * @return           The MapLocation of the closest tower, or null if none known.
     */
    protected MapLocation findNearestAllyTower(){
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for(MapLocation t : allyTowers){
            int d = rc.getLocation().distanceSquaredTo(t);
            if(d < bestDist){ bestDist = d; best = t; }
        }
        return best;
    }
}
