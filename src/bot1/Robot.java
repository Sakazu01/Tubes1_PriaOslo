package bot1;

import battlecode.common.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

public class Robot extends Entity{

    private MapLocation moveTarget;
    private LinkedList<MapLocation> plannedPath;
    private boolean exploring;

    // DFS exploration state
    private HashSet<MapLocation> exploreVisited = new HashSet<>();
    private LinkedList<MapLocation> exploreStack = new LinkedList<>();

    /**
     * @brief            constructor.
     * @param rc         the entity.
     */
    public Robot(RobotController rc){
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        super.run();
        move_to(new MapLocation(15, 10));
    }

    /**
     * @brief            move one step toward the target, ant-style.
     *
     *                   the entity's map (populated by scan() and updateMap())
     *                   is the knowledge base. BFS finds the shortest path
     *                   through all known-passable tiles in the map.
     *
     *                   if BFS finds a path, follow it. when a step is blocked
     *                   (e.g. by another bot), the fallback protocol
     *                   (forward -> diag-right -> diag-left, rotate left 90 degrees,
     *                   repeat) navigates around the obstacle, then the bot
     *                   attempts to rejoin or replan.
     *
     *                   if no path can be planned (destination unseen, or
     *                   gaps in map knowledge), the bot explores with DFS
     *                   toward the target. each turn, scan() reveals new
     *                   terrain, and BFS is re-attempted so the bot switches
     *                   to an optimal route as soon as the map allows it.
     * 
     * @param target     the target location to move to.
     * @throws GameActionException if the game action fails.
     */
    public void move_to(MapLocation target) throws GameActionException {
        if (rc.getLocation().equals(target)) {
            resetPathState();
            return;
        }

        if (!target.equals(moveTarget)) {
            resetPathState();
            moveTarget = target;
        }

        if (count % 5 == 0) scan();

        // every turn while exploring, re-attempt BFS on the (possibly refreshed) map
        if (exploring || plannedPath == null || plannedPath.isEmpty()) {
            LinkedList<MapLocation> path = bfs(rc.getLocation(), moveTarget);
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

    private void startExploring() {
        exploring = true;
        exploreVisited.clear();
        exploreStack.clear();
        exploreVisited.add(rc.getLocation());
    }

    // ---- follow known path ----

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

    private void rejoinOrReplan() {
        MapLocation cur = rc.getLocation();

        int idx = plannedPath.indexOf(cur);
        if (idx >= 0) {
            for (int i = 0; i <= idx; i++) plannedPath.pollFirst();
            return;
        }

        LinkedList<MapLocation> repath = bfs(cur, moveTarget);
        if (repath != null) {
            plannedPath = repath;
        } else {
            startExploring();
        }
    }

    // ---- DFS exploration ----

    /**
     * @brief            explore the map using DFS.
     * @throws GameActionException if the game action fails.
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
     * @brief            try to move in a given direction.
     * @param d          the direction to move in.
     * @return           true if the move was successful.
     * @throws GameActionException if the game action fails.
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
     * @brief            force move in a given direction.
     * @param d          the direction to move in.
     * @return           true if the move was successful.
     * @throws GameActionException if the game action fails.
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
     * @brief            try forward, diagonal-right, diagonal-left from the heading,
     *                   then rotate left 90 degrees and repeat.
     * @return           true if a move was made.
     * @throws GameActionException if the game action fails.
     */
    private boolean fallbackMove(Direction dir) throws GameActionException {
        for (int i = 0; i < 4; i++) {
            if (rc.canMove(dir))               { rc.move(dir);              return true; }
            if (rc.canMove(dir.rotateRight())) { rc.move(dir.rotateRight()); return true; }
            if (rc.canMove(dir.rotateLeft()))  { rc.move(dir.rotateLeft());  return true; }
            dir = dir.rotateLeft().rotateLeft();
        }
        return false;
    }

    // ---- BFS on the map knowledge base ----

    /**
     * @brief            BFS over all known-passable tiles in the map.
     *                   any two adjacent tiles that are both non-null and passable
     *                   are implicitly connected -- no explicit edge recording needed.
     *                   aborts early if bytecodes run low to stay within turn budget.
     * @param start      the starting location.
     * @param goal       the goal location.
     * @return           ordered list of waypoints (excluding start), or null.
     */
    private LinkedList<MapLocation> bfs(MapLocation start, MapLocation goal) {
        if (goal.x < 0 || goal.x >= MAP_WIDTH || goal.y < 0 || goal.y >= MAP_HEIGHT) return null;
        if (map[goal.x][goal.y] == null) return null;
        if (!map[goal.x][goal.y].isPassable()) return null;

        HashSet<MapLocation> seen = new HashSet<>();
        HashMap<MapLocation, MapLocation> parent = new HashMap<>();
        LinkedList<MapLocation> queue = new LinkedList<>();

        seen.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            if (Clock.getBytecodesLeft() < 1500) return null;

            MapLocation cur = queue.poll();
            if (cur.equals(goal)) {
                LinkedList<MapLocation> path = new LinkedList<>();
                MapLocation step = goal;
                while (!step.equals(start)) {
                    path.addFirst(step);
                    step = parent.get(step);
                }
                return path;
            }

            for (Direction d : directions) {
                MapLocation nb = cur.add(d);
                if (nb.x < 0 || nb.x >= MAP_WIDTH || nb.y < 0 || nb.y >= MAP_HEIGHT) continue;
                if (map[nb.x][nb.y] == null) continue;
                if (!map[nb.x][nb.y].isPassable()) continue;
                if (seen.add(nb)) {
                    parent.put(nb, cur);
                    queue.add(nb);
                }
            }
        }
        return null;
    }

    /**
     * @brief            reset the path state.
     */
    private void resetPathState() {
        moveTarget = null;
        plannedPath = null;
        exploring = false;
        exploreVisited.clear();
        exploreStack.clear();
    }

}
