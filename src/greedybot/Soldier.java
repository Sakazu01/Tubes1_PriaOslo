package greedybot;

import battlecode.common.*;

public class Soldier {

    // mode: 0=jalan2, 1=bangun tower, 2=serang, 3=srp, 4=balik isi paint
    static int mode = 0;
    static MapLocation target = null;
    static UnitType towerType = null;
    static MapLocation balikKe = null;
    static MapLocation tujuan = null;
    static boolean zigLeft = false;
    static int idleCount = 0;

    public static void run() throws GameActionException {
        RobotController rc = Globals.rc;
        int paint = Globals.paint;

        int threshold = Globals.roundNum < 300 ? 30 : 50;
        if (paint <= threshold) {
            if (!(target != null && mode == 1 && countSisaPattern(target) <= 3 && paint > 15)) {
                if (mode != 4) balikKe = (mode == 1 || mode == 3) ? target : null;
                mode = 4;
            }
        }
        if (mode == 4 && paint > 140) mode = 0;

        if (mode != 1 && mode != 4) {
            MapLocation ruin = null; int bd = Integer.MAX_VALUE;
            for (MapInfo info : Globals.nearbyMapInfos) {
                if (!info.hasRuin()) continue;
                MapLocation loc = info.getMapLocation();
                if (rc.canSenseLocation(loc) && rc.senseRobotAtLocation(loc) != null) continue;
                int d = Globals.myLoc.distanceSquaredTo(loc);
                if (d < bd) { bd = d; ruin = loc; }
            }
            if (ruin != null && paint > 40) { target = ruin; towerType = pilihTowerType(); mode = 1; }
        }

        if (mode != 2) {
            MapLocation musuh = null; int lowestHP = Integer.MAX_VALUE;
            for (RobotInfo e : Globals.nearbyEnemies) {
                if (Globals.isTowerType(e.getType()) && e.getHealth() < lowestHP) {
                    lowestHP = e.getHealth(); musuh = e.getLocation();
                }
            }
            if (musuh != null && paint > 30 && rc.getHealth() > 80) { target = musuh; mode = 2; }
        }

        if (mode == 0 && Globals.totalTowers >= 6) {
            MapLocation srp = cariSRP();
            if (srp != null && paint > 60) { target = srp; mode = 3; }
        }

        if      (mode == 4) isiPaint();
        else if (mode == 1) bangunTower();
        else if (mode == 2) serang();
        else if (mode == 3) doSRP();
        else                jalan();

        if (rc.isActionReady() && paint > 30 && (Globals.roundNum >= 150 || mode == 1)) {
            MapInfo myTile = rc.senseMapInfo(Globals.myLoc);
            if (!Navigation.isAllyPaint(myTile.getPaint()) && rc.canAttack(Globals.myLoc)) { rc.attack(Globals.myLoc); }
            else for (MapInfo info : Globals.nearbyMapInfos) {
                MapLocation loc = info.getMapLocation();
                if (Globals.myLoc.distanceSquaredTo(loc) <= 9 && info.getPaint() == PaintType.EMPTY
                        && !info.isWall() && !info.hasRuin() && rc.canAttack(loc)) {
                    rc.attack(loc); break;
                }
            }
        }
    }

    static void jalan() throws GameActionException {
        if (tujuan == null || Globals.myLoc.distanceSquaredTo(tujuan) <= 8) {
            if (balikKe != null) { tujuan = balikKe; balikKe = null; }
            else if (Globals.roundNum < 300 && Globals.numKnownMoneyTowers > 0)
                tujuan = Globals.predictEnemyLocation(Globals.knownMoneyTowers[0]);
            else tujuan = new MapLocation(Globals.rng.nextInt(Globals.mapWidth), Globals.rng.nextInt(Globals.mapHeight));
        }
        idleCount++;
        if (idleCount > 15) {
            Globals.symmetryType = (Globals.symmetryType + 1) % 3;
            tujuan = Globals.predictEnemyLocation(Globals.numKnownMoneyTowers > 0
                ? Globals.knownMoneyTowers[0] : Globals.myLoc);
            idleCount = 0;
        }
        zigLeft = !zigLeft;
        Navigation.zigzagTo(tujuan, zigLeft);
    }

    static void bangunTower() throws GameActionException {
        RobotController rc = Globals.rc;
        if (target == null) { mode = 0; return; }
        if (rc.canSenseLocation(target)) {
            RobotInfo r = rc.senseRobotAtLocation(target);
            if (r != null && Globals.isTowerType(r.getType())) { target = null; towerType = null; mode = 0; return; }
        }
        if (towerType == null) towerType = pilihTowerType();
        if (rc.canCompleteTowerPattern(towerType, target)) {
            rc.completeTowerPattern(towerType, target);
            target = null; towerType = null; mode = 0; idleCount = 0; return;
        }
        if (rc.canMarkTowerPattern(towerType, target)) rc.markTowerPattern(towerType, target);
        MapLocation tile = cariTileBelumDicat(target);
        if (tile != null) {
            if (rc.canAttack(tile)) rc.attack(tile);
            else { Navigation.pathTo(tile); if (rc.canAttack(tile)) rc.attack(tile); }
        } else Navigation.pathTo(target);
        idleCount = 0;
    }

    static void serang() throws GameActionException {
        RobotController rc = Globals.rc;
        if (target == null) { mode = 0; return; }
        if (rc.canSenseLocation(target)) {
            RobotInfo t = rc.senseRobotAtLocation(target);
            if (t == null || t.getTeam() == Globals.myTeam) { target = null; mode = 0; return; }
        }
        int dist = Globals.myLoc.distanceSquaredTo(target);
        if (rc.isActionReady() && rc.canAttack(target)) {
            rc.attack(target);
            Navigation.retreatFrom(target);
        } else if (dist > 9) {
            if (Globals.roundNum % 2 == 0) Navigation.moveToward(target);
        } else Navigation.retreatFrom(target);
        idleCount = 0;
    }

    static void doSRP() throws GameActionException {
        if (target == null) { mode = 0; return; }
        RobotController rc = Globals.rc;
        if (rc.canCompleteResourcePattern(target)) {
            rc.completeResourcePattern(target);
            target = null; mode = 0; idleCount = 0; return;
        }
        if (rc.canMarkResourcePattern(target)) rc.markResourcePattern(target);
        if (Globals.myLoc.distanceSquaredTo(target) > 2) Navigation.pathTo(target);
        idleCount = 0;
    }

    static void isiPaint() throws GameActionException {
        RobotController rc = Globals.rc;
        MapLocation tower = Globals.nearestPaintTower(Globals.myLoc);
        if (tower == null) tower = Globals.nearestAllyTower(Globals.myLoc);
        if (tower == null) { Navigation.moveRandom(); return; }
        if (Globals.myLoc.distanceSquaredTo(tower) <= 2) {
            int kurang = 200 - Globals.paint;
            if (kurang > 0 && rc.canTransferPaint(tower, -kurang)) rc.transferPaint(tower, -kurang);
            if (Globals.paint > 140) mode = 0;
        } else Navigation.pathTo(tower);
        MapInfo myTile = rc.senseMapInfo(Globals.myLoc);
        if (!Navigation.isAllyPaint(myTile.getPaint()) && rc.canAttack(Globals.myLoc)) rc.attack(Globals.myLoc);
    }

    static UnitType pilihTowerType() {
        if (Globals.numKnownPaintTowers == 0) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (Globals.numKnownMoneyTowers > 0
                && (float) Globals.numKnownMoneyTowers / Globals.numKnownPaintTowers > 3)
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    static int countSisaPattern(MapLocation r) throws GameActionException {
        int c = 0;
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation loc = new MapLocation(r.x+dx, r.y+dy);
                if (!Globals.rc.canSenseLocation(loc)) continue;
                MapInfo info = Globals.rc.senseMapInfo(loc);
                if (!info.isWall() && !info.hasRuin() && !Navigation.isAllyPaint(info.getPaint())) c++;
            }
        return c;
    }

    static MapLocation cariTileBelumDicat(MapLocation r) throws GameActionException {
        MapLocation best = null; int bd = Integer.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation loc = new MapLocation(r.x+dx, r.y+dy);
                if (!Globals.rc.canSenseLocation(loc)) continue;
                MapInfo info = Globals.rc.senseMapInfo(loc);
                if (info.isWall() || info.hasRuin() || Navigation.isAllyPaint(info.getPaint())) continue;
                int d = Globals.myLoc.distanceSquaredTo(loc);
                if (d < bd) { bd = d; best = loc; }
            }
        return best;
    }

    static MapLocation cariSRP() throws GameActionException {
        RobotController rc = Globals.rc;
        outer:
        for (MapInfo info : Globals.nearbyMapInfos) {
            MapLocation loc = info.getMapLocation();
            if (loc.x % 4 != 2 || loc.y % 4 != 2) continue;
            for (int i = 0; i < Globals.numKnownRuins; i++)
                if (loc.distanceSquaredTo(Globals.knownRuins[i]) <= 18) continue outer;
            for (int dx = -2; dx <= 2; dx++)
                for (int dy = -2; dy <= 2; dy++) {
                    MapLocation c = new MapLocation(loc.x+dx, loc.y+dy);
                    if (rc.canSenseLocation(c) && Navigation.isEnemyPaint(rc.senseMapInfo(c).getPaint())) continue outer;
                }
            return loc;
        }
        return null;
    }
}
