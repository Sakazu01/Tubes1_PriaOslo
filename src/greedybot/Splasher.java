package greedybot;

import battlecode.common.*;

public class Splasher {

    static MapLocation tujuan = null;

    public static void run() throws GameActionException {
        RobotController rc = Globals.rc;

        if (Globals.paint <= 80) {
            isiPaint();
            return;
        }

        // kalau ada tower musuh yang bisa kena splash dari luar range-nya, hajar
        // range tower = 9, splasher bisa reach sampe ~13
        if (rc.isActionReady()) {
            for (RobotInfo e : Globals.nearbyEnemies) {
                if (!Globals.isTowerType(e.getType()) || Globals.isDefenseTower(e.getType())) continue;
                int dist = Globals.myLoc.distanceSquaredTo(e.getLocation());
                if (dist > 9 && dist <= 13) {
                    // cari posisi splash yang kena tower
                    MapLocation towerLok = e.getLocation();
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dy = -2; dy <= 2; dy++) {
                            if (dx*dx + dy*dy > 4) continue;
                            MapLocation c = new MapLocation(Globals.myLoc.x + dx, Globals.myLoc.y + dy);
                            if (c.distanceSquaredTo(towerLok) <= 2 && rc.canAttack(c)) { rc.attack(c); return; }
                        }
                    Navigation.pathTo(towerLok);
                    return;
                }
            }
        }

        // cari titik splash terbaik di sekitar
        if (rc.isActionReady() && Globals.paint >= 50) {
            MapLocation titikTerbaik = null;
            int skorTerbaik = 50; // minimum threshold biar ga buang-buang paint

            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (dx*dx + dy*dy > 4) continue;
                    MapLocation pusat = new MapLocation(Globals.myLoc.x + dx, Globals.myLoc.y + dy);
                    if (!rc.canAttack(pusat)) continue;

                    // hitung skor splash 3x3 di sekitar pusat
                    int skor = 0;
                    for (int ex = -1; ex <= 1; ex++) {
                        for (int ey = -1; ey <= 1; ey++) {
                            MapLocation loc = new MapLocation(pusat.x + ex, pusat.y + ey);
                            if (!rc.canSenseLocation(loc)) continue;
                            MapInfo info = rc.senseMapInfo(loc);
                            if (info.isWall() || info.hasRuin()) continue;

                            PaintType cat = info.getPaint();
                            if (Navigation.isEnemyPaint(cat)) {
                                skor += 8;
                                // bonus kalau deket ruin (ngehambat tower musuh)
                                for (int i = 0; i < Globals.numKnownRuins; i++) {
                                    if (loc.distanceSquaredTo(Globals.knownRuins[i]) <= 8) { skor += 15; break; }
                                }
                            } else if (cat == PaintType.EMPTY) {
                                skor += 3;
                            } else {
                                skor -= 5; // jangan nutupin cat sendiri
                            }
                        }
                    }

                    if (skor > skorTerbaik) { skorTerbaik = skor; titikTerbaik = pusat; }
                }
            }

            if (titikTerbaik != null) {
                if (rc.canAttack(titikTerbaik)) rc.attack(titikTerbaik);
                else { Navigation.pathTo(titikTerbaik); if (rc.canAttack(titikTerbaik)) rc.attack(titikTerbaik); }
                return;
            }
        }

        jalan();
    }

    static void isiPaint() throws GameActionException {
        RobotController rc = Globals.rc;
        MapLocation tower = Globals.nearestPaintTower(Globals.myLoc);
        if (tower == null) tower = Globals.nearestAllyTower(Globals.myLoc);
        if (tower == null) { Navigation.moveRandom(); return; }

        if (Globals.myLoc.distanceSquaredTo(tower) <= 2) {
            int kurang = 300 - Globals.paint;
            if (kurang > 0 && rc.canTransferPaint(tower, -kurang)) rc.transferPaint(tower, -kurang);
        } else {
            Navigation.pathTo(tower);
        }
    }

    static void jalan() throws GameActionException {
        // prioritas: ke cat musuh terdekat kalau ada
        MapLocation catMusuh = null;
        int bd = Integer.MAX_VALUE;
        for (MapInfo info : Globals.nearbyMapInfos) {
            if (!Navigation.isEnemyPaint(info.getPaint())) continue;
            int d = Globals.myLoc.distanceSquaredTo(info.getMapLocation());
            if (d < bd) { bd = d; catMusuh = info.getMapLocation(); }
        }
        if (catMusuh != null) { Navigation.pathTo(catMusuh); return; }

        if (tujuan == null || Globals.myLoc.distanceSquaredTo(tujuan) <= 8) {
            if (Globals.numKnownMoneyTowers > 0)
                tujuan = Globals.predictEnemyLocation(Globals.knownMoneyTowers[0]);
            else
                tujuan = new MapLocation(Globals.rng.nextInt(Globals.mapWidth), Globals.rng.nextInt(Globals.mapHeight));
        }
        Navigation.pathTo(tujuan);
    }
}
