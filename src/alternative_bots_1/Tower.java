package alternative_bots_1;

import battlecode.common.*;

public class Tower {

    static int totalSpawn = 0;

    static void run() throws GameActionException {
        RobotController rc = Globals.rc;

        serangMusuh();

        if (adaMusuhDekat()) {
            if (rc.isActionReady()) spawnUnit(UnitType.SOLDIER);
            return;
        }

        if (rc.isActionReady()) pilihDanSpawn();
        broadcastLokasi();
    }

    static void pilihDanSpawn() throws GameActionException {
        // kalau ada banyak musuh mendekat, spawn soldier terus
        for (RobotInfo e : Globals.enemies) {
            if (e.getType() == UnitType.SOLDIER
                    && Globals.myLoc.distanceSquaredTo(e.getLocation()) <= 16) {
                spawnUnit(UnitType.SOLDIER);
                return;
            }
        }

        // pola spawn: 3 soldier di awal, lalu soldier-mopper-splasher-splasher berulang
        UnitType tipe;
        if (totalSpawn < 3) {
            tipe = UnitType.SOLDIER;
        } else {
            int slot = (totalSpawn - 3) % 4;
            if (slot == 0) tipe = UnitType.SOLDIER;
            else if (slot == 1) tipe = UnitType.MOPPER;
            else tipe = UnitType.SPLASHER;
        }

        spawnUnit(tipe);
    }

    static void spawnUnit(UnitType tipe) throws GameActionException {
        RobotController rc = Globals.rc;
        MapLocation lok = cariLokasiSpawn(tipe);
        if (lok != null && rc.canBuildRobot(tipe, lok)) {
            rc.buildRobot(tipe, lok);
            totalSpawn++;
        }
    }

    static MapLocation cariLokasiSpawn(UnitType tipe) throws GameActionException {
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction d : Navigation.directions) {
            MapLocation loc = Globals.myLoc.add(d);
            if (!Globals.rc.canBuildRobot(tipe, loc)) continue;

            int score = 0;
            if (Globals.rc.canSenseLocation(loc)) {
                PaintType p = Globals.rc.senseMapInfo(loc).getPaint();
                if (Navigation.isAllyPaint(p)) score += 10;
                else if (p == PaintType.EMPTY) score += 5;
            }

            Direction keTengah = Globals.myLoc.directionTo(Globals.center);
            if (d == keTengah || d == keTengah.rotateLeft() || d == keTengah.rotateRight())
                score += 3;

            for (RobotInfo e : Globals.enemies) {
                if (loc.isAdjacentTo(e.getLocation())) { score -= 15; break; }
            }

            if (score > bestScore) { bestScore = score; best = loc; }
        }
        return best;
    }

    static boolean adaMusuhDekat() {
        for (RobotInfo e : Globals.enemies) {
            if (!Globals.isTowerType(e.getType())
                    && Globals.myLoc.distanceSquaredTo(e.getLocation()) <= 16)
                return true;
        }
        return false;
    }

    static void serangMusuh() throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isActionReady()) return;
        RobotInfo target = null;
        int lowestHP = Integer.MAX_VALUE;
        int range = Globals.isDefenseTower(Globals.myType) ? 16 : 9;
        for (RobotInfo e : Globals.enemies) {
            if (Globals.isTowerType(e.getType())) continue;
            if (Globals.myLoc.distanceSquaredTo(e.getLocation()) <= range && e.getHealth() < lowestHP) {
                lowestHP = e.getHealth();
                target = e;
            }
        }
        if (target != null && rc.canAttack(target.getLocation()))
            rc.attack(target.getLocation());
    }

    static void broadcastLokasi() throws GameActionException {
        int tipe = Globals.isPaintTower(Globals.myType) ? 1 : Globals.isDefenseTower(Globals.myType) ? 2 : 0;
        int msg = (tipe << 12) | (Globals.myLoc.x << 6) | Globals.myLoc.y;
        for (RobotInfo ally : Globals.allies) {
            if (Globals.rc.canSendMessage(ally.getLocation(), msg))
                Globals.rc.sendMessage(ally.getLocation(), msg);
        }
    }
}
