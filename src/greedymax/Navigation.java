package greedymax;

import battlecode.common.*;

public class Navigation {

    static final Direction[] directions = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    private static MapLocation bugTarget = null;
    private static Direction bugDir = null;

    static boolean pathTo(MapLocation target) throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isMovementReady() || target == null) return false;
        if (Globals.myLoc.equals(target)) return false;

        if (bugTarget == null || !bugTarget.equals(target)) {
            bugTarget = target;
            bugDir = null;
        }

        Direction d = Globals.myLoc.directionTo(target);
        if (rc.canMove(d)) {
            rc.move(d);
            bugDir = null;
            return true;
        }

        if (bugDir == null) bugDir = d;
        for (int i = 0; i < 8; i++) {
            if (rc.canMove(bugDir)) {
                rc.move(bugDir);
                bugDir = bugDir.rotateLeft();
                return true;
            }
            bugDir = bugDir.rotateRight();
        }
        return false;
    }

    static boolean moveToward(MapLocation target) throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isMovementReady() || target == null) return false;
        Direction d = Globals.myLoc.directionTo(target);
        Direction[] coba = { d, d.rotateLeft(), d.rotateRight(),
                d.rotateLeft().rotateLeft(), d.rotateRight().rotateRight() };
        for (Direction dir : coba)
            if (rc.canMove(dir)) { rc.move(dir); return true; }
        return moveRandom();
    }

    static boolean retreatFrom(MapLocation ancaman) throws GameActionException {
        if (!Globals.rc.isMovementReady()) return false;
        Direction kabur = ancaman.directionTo(Globals.myLoc);
        Direction[] coba = { kabur, kabur.rotateLeft(), kabur.rotateRight() };
        for (Direction d : coba)
            if (Globals.rc.canMove(d)) { Globals.rc.move(d); return true; }
        return false;
    }

    static boolean moveRandom() throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isMovementReady()) return false;
        int start = Globals.rng.nextInt(8);
        for (int i = 0; i < 8; i++) {
            Direction d = directions[(start + i) % 8];
            if (rc.canMove(d)) { rc.move(d); return true; }
        }
        return false;
    }

    static boolean moveExpansion() throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isMovementReady()) return false;

        int[] score = new int[8];
        for (MapInfo tile : Globals.nearby) {
            if (!tile.isPassable()) continue;
            PaintType p = tile.getPaint();
            if (isAllyPaint(p)) continue;
            int idx = dirIdx(Globals.myLoc.directionTo(tile.getMapLocation()));
            if (idx < 0) continue;
            score[idx] += (p == PaintType.EMPTY) ? 3 : 1;
        }
        for (RobotInfo e : Globals.enemies) {
            int idx = dirIdx(Globals.myLoc.directionTo(e.getLocation()));
            if (idx >= 0) score[idx] -= 20;
        }

        int bestIdx = -1, bestVal = 0;
        for (int i = 0; i < 8; i++) {
            if (score[i] > bestVal && rc.canMove(directions[i])) {
                bestVal = score[i];
                bestIdx = i;
            }
        }

        if (bestIdx >= 0) { rc.move(directions[bestIdx]); return true; }
        return moveRandom();
    }

    static int dirIdx(Direction d) {
        for (int i = 0; i < directions.length; i++)
            if (directions[i] == d) return i;
        return -1;
    }

    static boolean isAllyPaint(PaintType pt) {
        return pt == PaintType.ALLY_PRIMARY || pt == PaintType.ALLY_SECONDARY;
    }

    static boolean isEnemyPaint(PaintType pt) {
        return pt == PaintType.ENEMY_PRIMARY || pt == PaintType.ENEMY_SECONDARY;
    }
}
