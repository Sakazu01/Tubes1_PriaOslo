package greedybot;

import battlecode.common.*;

public class Navigation {

    static final Direction[] directions = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    // state buat bug navigation
    private static boolean lagi_bug = false;
    private static boolean bugKanan = true;
    private static MapLocation bugTarget = null;
    private static int bugStartDist = Integer.MAX_VALUE;
    private static int nyangkutCount = 0;
    private static MapLocation posSebelumnya = null;
    private static Direction bugDir = null;

    public static boolean pathTo(MapLocation target) throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isMovementReady() || target == null) return false;
        if (Globals.myLoc.equals(target)) return false;

        // kalau target beda, reset bug
        if (bugTarget == null || !bugTarget.equals(target)) {
            bugTarget = target;
            lagi_bug = false;
            nyangkutCount = 0;
        }

        if (posSebelumnya != null && posSebelumnya.equals(Globals.myLoc)) {
            nyangkutCount++;
            if (nyangkutCount >= 3) {
                // nyangkut, random dulu biar lepas
                lagi_bug = false;
                nyangkutCount = 0;
                return moveRandom();
            }
        } else {
            nyangkutCount = 0;
        }
        posSebelumnya = Globals.myLoc;

        if (!lagi_bug) {
            Direction d = Globals.myLoc.directionTo(target);
            if (bisaGerak(d)) { rc.move(d); return true; }

            Direction best = arahTerbaik(target);
            if (best != null) { rc.move(best); return true; }

            // mulai bug
            lagi_bug = true;
            bugStartDist = Globals.myLoc.distanceSquaredTo(target);
            bugKanan = Globals.rng.nextBoolean();
            bugDir = Globals.myLoc.directionTo(target);
        }

        if (lagi_bug) {
            Direction d = Globals.myLoc.directionTo(target);
            if (bisaGerak(d) && Globals.myLoc.distanceSquaredTo(target) < bugStartDist) {
                lagi_bug = false;
                rc.move(d);
                return true;
            }
            for (int i = 0; i < 8; i++) {
                if (bisaGerak(bugDir)) {
                    rc.move(bugDir);
                    bugDir = bugKanan ? bugDir.rotateLeft() : bugDir.rotateRight();
                    return true;
                }
                bugDir = bugKanan ? bugDir.rotateRight() : bugDir.rotateLeft();
            }
        }
        return false;
    }

    // zigzag biar nggak ketebak, selang-seling kiri kanan
    public static boolean zigzagTo(MapLocation target, boolean zigLeft) throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isMovementReady() || target == null) return false;

        Direction d = Globals.myLoc.directionTo(target);
        Direction samping = zigLeft ? d.rotateLeft() : d.rotateRight();

        if (bisaGerak(samping)) { rc.move(samping); return true; }
        if (bisaGerak(d)) { rc.move(d); return true; }
        return pathTo(target);
    }

    // gerak ke target tanpa ngehindarin tower musuh (khusus combat)
    public static boolean moveToward(MapLocation target) throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isMovementReady() || target == null) return false;

        Direction d = Globals.myLoc.directionTo(target);
        Direction[] coba = { d, d.rotateLeft(), d.rotateRight(), d.rotateLeft().rotateLeft(), d.rotateRight().rotateRight() };
        for (Direction dir : coba) {
            if (rc.canMove(dir)) { rc.move(dir); return true; }
        }
        return moveRandom();
    }

    public static boolean retreatFrom(MapLocation ancaman) throws GameActionException {
        if (!Globals.rc.isMovementReady()) return false;
        Direction kabur = ancaman.directionTo(Globals.myLoc);
        Direction[] coba = { kabur, kabur.rotateLeft(), kabur.rotateRight() };
        for (Direction d : coba) {
            if (bisaGerak(d)) { Globals.rc.move(d); return true; }
        }
        return false;
    }

    public static boolean moveRandom() throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isMovementReady()) return false;
        int start = Globals.rng.nextInt(8);
        for (int i = 0; i < 8; i++) {
            Direction d = directions[(start + i) % 8];
            if (rc.canMove(d)) { rc.move(d); return true; }
        }
        return false;
    }

    // cek apakah aman bergerak ke arah ini (hindari range tower musuh)
    private static boolean bisaGerak(Direction dir) throws GameActionException {
        if (!Globals.rc.canMove(dir)) return false;
        MapLocation dest = Globals.myLoc.add(dir);
        for (RobotInfo musuh : Globals.nearbyEnemies) {
            if (Globals.isTowerType(musuh.getType()) && dest.distanceSquaredTo(musuh.getLocation()) <= 9)
                return false;
        }
        return true;
    }

    // pilih arah yang paling deket ke target + prefer cat sendiri
    private static Direction arahTerbaik(MapLocation target) throws GameActionException {
        Direction best = null;
        int bestSkor = Integer.MIN_VALUE;

        for (Direction dir : directions) {
            if (!bisaGerak(dir)) continue;
            MapLocation dest = Globals.myLoc.add(dir);
            int skorJarak = -dest.distanceSquaredTo(target);

            int skorCat = 0;
            if (Globals.rc.canSenseLocation(dest)) {
                PaintType pt = Globals.rc.senseMapInfo(dest).getPaint();
                if (isAllyPaint(pt)) skorCat = 3;
                else if (pt == PaintType.EMPTY) skorCat = 1;
                else skorCat = -2;
            }

            int skor = skorJarak * 10 + skorCat;
            if (skor > bestSkor) { bestSkor = skor; best = dir; }
        }
        return best;
    }

    public static boolean isAllyPaint(PaintType pt) {
        return pt == PaintType.ALLY_PRIMARY || pt == PaintType.ALLY_SECONDARY;
    }

    public static boolean isEnemyPaint(PaintType pt) {
        return pt == PaintType.ENEMY_PRIMARY || pt == PaintType.ENEMY_SECONDARY;
    }
}
