package greedybot;

import battlecode.common.*;

public class Tower {

    static int totalSpawn = 0;
    static int jmlSoldier = 0, jmlSplasher = 0, jmlMopper = 0;

    public static void run() throws GameActionException {
        RobotController rc = Globals.rc;

        serangMusuh();
        if (adaMusuhDekat()) return; // lagi diserang, skip spawn/upgrade

        if (Globals.money >= 3000 && rc.canUpgradeTower(Globals.myLoc))
            rc.upgradeTower(Globals.myLoc);

        if (rc.isActionReady()) spawnUnit();
        broadcastLokasi();
    }

    static void spawnUnit() throws GameActionException {
        RobotController rc = Globals.rc;
        int rnd = Globals.roundNum;
        UnitType spawn;

        if (totalSpawn == 0) {
            spawn = UnitType.SOLDIER;
        } else {
            float rSoldier  = (float) jmlSoldier  / totalSpawn;
            float rSplasher = (float) jmlSplasher / totalSpawn;
            float rMopper   = (float) jmlMopper   / totalSpawn;

            if (rMopper < 0.20f) spawn = UnitType.MOPPER;
            else if (rnd < Globals.EARLY_GAME_END) spawn = UnitType.SOLDIER;
            else if (rnd < Globals.MID_GAME_END) spawn = (rSoldier > 0.30f || rSplasher < 0.50f) ? UnitType.SPLASHER : UnitType.SOLDIER;
            else spawn = UnitType.SPLASHER;
        }

        MapLocation lok = cariLokSpawn(spawn);
        if (lok != null && rc.canBuildRobot(spawn, lok)) {
            rc.buildRobot(spawn, lok);
            totalSpawn++;
            if (spawn == UnitType.SOLDIER) jmlSoldier++;
            else if (spawn == UnitType.SPLASHER) jmlSplasher++;
            else jmlMopper++;
        }
    }

    static MapLocation cariLokSpawn(UnitType tipe) throws GameActionException {
        MapLocation pos = Globals.myLoc;
        Direction keTengah = pos.directionTo(Globals.mapCenter);
        Direction[] prioritas = {
            keTengah, keTengah.rotateLeft(), keTengah.rotateRight(),
            keTengah.rotateLeft().rotateLeft(), keTengah.rotateRight().rotateRight(),
            keTengah.opposite().rotateLeft(), keTengah.opposite().rotateRight(),
            keTengah.opposite(),
        };
        for (Direction d : prioritas) {
            MapLocation loc = pos.add(d);
            if (Globals.rc.canBuildRobot(tipe, loc)) return loc;
        }
        return null;
    }

    static boolean adaMusuhDekat() {
        for (RobotInfo e : Globals.nearbyEnemies) {
            if (!Globals.isTowerType(e.getType()) && Globals.myLoc.distanceSquaredTo(e.getLocation()) <= 16)
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

        for (RobotInfo e : Globals.nearbyEnemies) {
            if (Globals.isTowerType(e.getType())) continue;
            if (Globals.myLoc.distanceSquaredTo(e.getLocation()) <= range && e.getHealth() < lowestHP) {
                lowestHP = e.getHealth(); target = e;
            }
        }
        if (target != null && rc.canAttack(target.getLocation())) rc.attack(target.getLocation());
    }

    // encode: tipe (3 bit) | x (6 bit) | y (6 bit)
    static void broadcastLokasi() throws GameActionException {
        int tipe = Globals.isPaintTower(Globals.myType) ? 1 : Globals.isDefenseTower(Globals.myType) ? 2 : 0;
        int msg = (tipe << 12) | (Globals.myLoc.x << 6) | Globals.myLoc.y;
        for (RobotInfo ally : Globals.nearbyAllies) {
            if (Globals.rc.canSendMessage(ally.getLocation(), msg)) Globals.rc.sendMessage(ally.getLocation(), msg);
        }
    }

    public static int[] decodeMessage(int msg) {
        return new int[]{ (msg >> 12) & 0x7, (msg >> 6) & 0x3F, msg & 0x3F };
    }
}
