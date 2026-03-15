package greedybot;

import battlecode.common.*;

public class Mopper {

    static MapLocation tujuan = null;

    public static void run() throws GameActionException {
        RobotController rc = Globals.rc;

        // isi dulu kalau cat abis
        if (Globals.paint <= 20) {
            MapLocation tower = Globals.nearestPaintTower(Globals.myLoc);
            if (tower == null) tower = Globals.nearestAllyTower(Globals.myLoc);
            if (tower != null) {
                if (Globals.myLoc.distanceSquaredTo(tower) <= 2) {
                    int kurang = 100 - Globals.paint;
                    if (kurang > 0 && rc.canTransferPaint(tower, -kurang)) rc.transferPaint(tower, -kurang);
                } else {
                    Navigation.pathTo(tower);
                }
            } else {
                // ga ada tower, langsung hajar musuh aja
                RobotInfo terdekat = musuhTerdekat();
                if (terdekat != null) Navigation.pathTo(terdekat.getLocation());
                else Navigation.moveRandom();
            }
            return;
        }

        // defend tower ally kalau ada musuh deket
        boolean perluDefend = false;
        for (RobotInfo ally : Globals.nearbyAllies) {
            if (!Globals.isTowerType(ally.getType())) continue;
            for (RobotInfo e : Globals.nearbyEnemies) {
                if (e.getType() != UnitType.SOLDIER && e.getType() != UnitType.SPLASHER) continue;
                if (e.getLocation().distanceSquaredTo(ally.getLocation()) <= 16) { perluDefend = true; break; }
            }
            if (perluDefend) break;
        }

        if (perluDefend && rc.isActionReady()) {
            // coba swing dulu, lebih efisien
            Direction[] cardinal = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
            Direction swingTerbaik = null;
            int swingCount = 0;
            for (Direction dir : cardinal) {
                MapLocation s1 = Globals.myLoc.add(dir);
                MapLocation s2 = s1.add(dir);
                int cnt = 0;
                for (RobotInfo e : Globals.nearbyEnemies) {
                    if (Globals.isTowerType(e.getType())) continue;
                    MapLocation el = e.getLocation();
                    if (el.distanceSquaredTo(s1) <= 2 || el.distanceSquaredTo(s2) <= 2) cnt++;
                }
                if (cnt > swingCount) { swingCount = cnt; swingTerbaik = dir; }
            }
            if (swingTerbaik != null && rc.canMopSwing(swingTerbaik)) {
                rc.mopSwing(swingTerbaik); return;
            }
            for (RobotInfo e : Globals.nearbyEnemies) {
                if (Globals.myLoc.distanceSquaredTo(e.getLocation()) <= 2 && rc.canAttack(e.getLocation())) {
                    rc.attack(e.getLocation()); return;
                }
            }
            RobotInfo terdekat = musuhTerdekat();
            if (terdekat != null) Navigation.pathTo(terdekat.getLocation());
            return;
        }

        // hapus cat musuh deket ruin (prioritas)
        if (rc.isActionReady()) {
            MapLocation catMusuhDekatRuin = null;
            int bd = Integer.MAX_VALUE;
            for (MapInfo info : Globals.nearbyMapInfos) {
                if (!Navigation.isEnemyPaint(info.getPaint())) continue;
                MapLocation loc = info.getMapLocation();
                for (int i = 0; i < Globals.numKnownRuins; i++) {
                    if (loc.distanceSquaredTo(Globals.knownRuins[i]) <= 12) {
                        int d = Globals.myLoc.distanceSquaredTo(loc);
                        if (d < bd) { bd = d; catMusuhDekatRuin = loc; }
                        break;
                    }
                }
            }
            if (catMusuhDekatRuin != null) {
                if (Globals.myLoc.distanceSquaredTo(catMusuhDekatRuin) <= 2 && rc.canAttack(catMusuhDekatRuin)) {
                    rc.attack(catMusuhDekatRuin);
                } else {
                    Navigation.pathTo(catMusuhDekatRuin);
                    if (rc.isActionReady() && rc.canAttack(catMusuhDekatRuin)) rc.attack(catMusuhDekatRuin);
                }
                return;
            }
        }

        // kejar musuh yang paintnya paling dikit (lebih gampang dibunuh)
        if (rc.isActionReady()) {
            RobotInfo targetKejar = null;
            int lowestPaint = Integer.MAX_VALUE;
            for (RobotInfo e : Globals.nearbyEnemies) {
                if (Globals.isTowerType(e.getType())) continue;
                if (e.getPaintAmount() < lowestPaint) { lowestPaint = e.getPaintAmount(); targetKejar = e; }
            }
            if (targetKejar != null) {
                MapLocation eLoc = targetKejar.getLocation();
                // coba swing ke musuh
                Direction[] cardinal = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
                for (Direction dir : cardinal) {
                    MapLocation s1 = Globals.myLoc.add(dir);
                    MapLocation s2 = s1.add(dir);
                    if (eLoc.distanceSquaredTo(s1) <= 2 || eLoc.distanceSquaredTo(s2) <= 2) {
                        if (rc.canMopSwing(dir)) { rc.mopSwing(dir); return; }
                    }
                }
                if (Globals.myLoc.distanceSquaredTo(eLoc) <= 2 && rc.canAttack(eLoc)) {
                    rc.attack(eLoc); return;
                }
                Navigation.pathTo(eLoc);
                return;
            }
        }

        // hapus sembarang cat musuh
        if (rc.isActionReady()) {
            MapLocation catMusuh = null;
            int bd = Integer.MAX_VALUE;
            for (MapInfo info : Globals.nearbyMapInfos) {
                if (!Navigation.isEnemyPaint(info.getPaint())) continue;
                int d = Globals.myLoc.distanceSquaredTo(info.getMapLocation());
                if (d < bd) { bd = d; catMusuh = info.getMapLocation(); }
            }
            if (catMusuh != null) {
                if (Globals.myLoc.distanceSquaredTo(catMusuh) <= 2 && rc.canAttack(catMusuh)) rc.attack(catMusuh);
                else { Navigation.pathTo(catMusuh); if (rc.isActionReady() && rc.canAttack(catMusuh)) rc.attack(catMusuh); }
                return;
            }
        }

        // jalan-jalan
        if (tujuan == null || Globals.myLoc.distanceSquaredTo(tujuan) <= 8) {
            tujuan = new MapLocation(Globals.rng.nextInt(Globals.mapWidth), Globals.rng.nextInt(Globals.mapHeight));
        }
        Navigation.pathTo(tujuan);
    }

    static RobotInfo musuhTerdekat() {
        RobotInfo best = null;
        int bd = Integer.MAX_VALUE;
        for (RobotInfo e : Globals.nearbyEnemies) {
            if (Globals.isTowerType(e.getType())) continue;
            int d = Globals.myLoc.distanceSquaredTo(e.getLocation());
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }
}
