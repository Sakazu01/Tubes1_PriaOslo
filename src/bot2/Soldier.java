package bot2;

import battlecode.common.*;

public class Soldier {

    static MapLocation assignedRuin = null;
    static MapLocation exploreTarget = null;
    static int idleCount = 0;
    static int kuadran = -1;
    static int cornerIdx = 0; // urutan pojok yang mau dikunjungi

    static final UnitType[] TOWER_TYPES = {
        UnitType.LEVEL_ONE_MONEY_TOWER,
        UnitType.LEVEL_ONE_PAINT_TOWER,
        UnitType.LEVEL_ONE_DEFENSE_TOWER
    };

    static void run() throws GameActionException {
        if (kuadran < 0) kuadran = Globals.rc.getID() % 4;
        if (Globals.rc.isActionReady()) aksi();
        gerak();
        if (Globals.rc.isActionReady()) aksi();
    }

    static void aksi() throws GameActionException {
        RobotController rc = Globals.rc;
        int paint = Globals.paint;

        // selesaikan tower — prioritas tertinggi selalu
        for (MapInfo info : Globals.nearby) {
            if (!info.hasRuin()) continue;
            MapLocation ruin = info.getMapLocation();
            if (rc.canSenseLocation(ruin) && rc.senseRobotAtLocation(ruin) != null) continue;
            for (UnitType t : TOWER_TYPES) {
                if (rc.canCompleteTowerPattern(t, ruin)) {
                    rc.completeTowerPattern(t, ruin);
                    if (ruin.equals(assignedRuin)) assignedRuin = null;
                    return;
                }
            }
        }

        // tandai ruin
        if (paint > 20) {
            for (MapInfo info : Globals.nearby) {
                if (!info.hasRuin()) continue;
                MapLocation ruin = info.getMapLocation();
                if (rc.canSenseLocation(ruin) && rc.senseRobotAtLocation(ruin) != null) continue;
                if (Globals.isRuinClaimed(ruin)) continue;
                UnitType tipe = pilihTowerType();
                if (rc.canMarkTowerPattern(tipe, ruin)) {
                    rc.markTowerPattern(tipe, ruin);
                    assignedRuin = ruin;
                    broadcastKlaim(ruin);
                    return;
                }
            }
        }

        // kalo lagi fokus bangun tower, cat pola dulu — skip semua yang lain
        if (assignedRuin != null) {
            if (paint > 15) {
                MapLocation bestTile = null;
                int bd = Integer.MAX_VALUE;
                for (MapInfo info : Globals.nearby) {
                    if (!info.hasRuin()) continue;
                    MapLocation ruin = info.getMapLocation();
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dy = -2; dy <= 2; dy++) {
                            MapLocation loc = new MapLocation(ruin.x + dx, ruin.y + dy);
                            if (!rc.canSenseLocation(loc)) continue;
                            MapInfo ti = rc.senseMapInfo(loc);
                            if (ti.isWall() || ti.hasRuin()) continue;
                            PaintType mark = ti.getMark();
                            if (mark == PaintType.EMPTY || mark == ti.getPaint()) continue;
                            int d = Globals.myLoc.distanceSquaredTo(loc);
                            if (d <= 9 && d < bd) { bd = d; bestTile = loc; }
                        }
                    }
                }
                if (bestTile != null && rc.canSenseLocation(bestTile)) {
                    boolean sec = rc.senseMapInfo(bestTile).getMark() == PaintType.ALLY_SECONDARY;
                    if (rc.canAttack(bestTile)) { rc.attack(bestTile, sec); return; }
                }
            }
            return; // kalau ga ada tile pola yang bisa dicat, tunggu saja
        }

        // tidak ada assigned ruin — lanjut ke aksi lain

        // serang tower musuh
        if (paint > 20) {
            RobotInfo best = null;
            int bestScore = 0;
            for (RobotInfo e : Globals.enemies) {
                if (!Globals.isTowerType(e.getType())) continue;
                if (Globals.myLoc.distanceSquaredTo(e.getLocation()) > 9) continue;
                int s = 5000 + (2000 - e.getHealth());
                if (s > bestScore) { bestScore = s; best = e; }
            }
            if (best != null && rc.canAttack(best.getLocation())) {
                rc.attack(best.getLocation());
                return;
            }
        }

        // cat area sekitar ruin kosong
        if (paint > 20) {
            outer:
            for (MapInfo info : Globals.nearby) {
                if (!info.hasRuin()) continue;
                MapLocation ruinLoc = info.getMapLocation();
                for (MapInfo t : Globals.nearby) {
                    if (Navigation.isAllyPaint(t.getPaint())
                            && t.getMapLocation().distanceSquaredTo(ruinLoc) <= 8)
                        continue outer;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        MapLocation loc = new MapLocation(ruinLoc.x + dx, ruinLoc.y + dy);
                        if (!rc.canSenseLocation(loc)) continue;
                        MapInfo ti = rc.senseMapInfo(loc);
                        if (ti.isWall() || ti.hasRuin()) continue;
                        if (rc.canAttack(loc)) { rc.attack(loc); return; }
                    }
                }
            }
        }

        // cat territory
        if (paint > 30) {
            MapLocation bestTile = null;
            int bestScore = 0;
            for (MapInfo info : Globals.nearby) {
                MapLocation loc = info.getMapLocation();
                if (info.isWall() || info.hasRuin()) continue;
                if (Navigation.isAllyPaint(info.getPaint())) continue;
                if (Globals.myLoc.distanceSquaredTo(loc) > 9) continue;
                if (!rc.canAttack(loc)) continue;

                int score = (info.getPaint() == PaintType.EMPTY) ? 10 : 4;
                for (MapInfo adj : Globals.nearby) {
                    if (Navigation.isAllyPaint(adj.getPaint())
                            && adj.getMapLocation().distanceSquaredTo(loc) <= 2)
                        score += 3;
                }
                if (score > bestScore) { bestScore = score; bestTile = loc; }
            }
            if (bestTile != null) {
                boolean sec = rc.canSenseLocation(bestTile)
                    && rc.senseMapInfo(bestTile).getMark() == PaintType.ALLY_SECONDARY;
                rc.attack(bestTile, sec);
            }
        }
    }

    static void gerak() throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isMovementReady()) return;

        int paint = Globals.paint;
        MapLocation target = null;
        int best = 0;

        // refuel kritis
        int urgency = Math.max(0, (50 - paint) * 40);
        if (urgency > best) {
            MapLocation tower = Globals.nearestPaintTower(Globals.myLoc);
            if (tower == null) tower = Globals.nearestAllyTower(Globals.myLoc);
            if (tower != null) {
                int s = urgency - Globals.myLoc.distanceSquaredTo(tower);
                if (s > best) { best = s; target = tower; }
            }
        }

        // kalo punya assigned ruin, ini prioritas utama — skornya tinggi banget
        if (assignedRuin != null) {
            if (rc.canSenseLocation(assignedRuin) && rc.senseRobotAtLocation(assignedRuin) != null) {
                assignedRuin = null;
            } else {
                MapLocation unpainted = cariTilePolaBelumDicat(assignedRuin);
                MapLocation dest = (unpainted != null) ? unpainted : assignedRuin;
                // skor 8000 — menang vs semua kecuali refuel kritis (max ~2000)
                int s = 8000 - Globals.myLoc.distanceSquaredTo(dest) * 5;
                if (s > best) { best = s; target = dest; }
            }
        }

        // cari ruin baru
        if (assignedRuin == null && paint > 50) {
            for (MapInfo info : Globals.nearby) {
                if (!info.hasRuin()) continue;
                MapLocation loc = info.getMapLocation();
                if (rc.canSenseLocation(loc) && rc.senseRobotAtLocation(loc) != null) continue;
                if (Globals.isRuinClaimed(loc)) continue;
                int s = 5000 - Globals.myLoc.distanceSquaredTo(loc) * 5;
                if (s > best) { best = s; target = loc; }
            }
            if (target != null) {
                for (MapInfo info : Globals.nearby) {
                    if (info.hasRuin() && info.getMapLocation().equals(target)) {
                        assignedRuin = target;
                        break;
                    }
                }
            }
        }

        // kiting enemy tower kalo ga ada urusan ruin
        if (assignedRuin == null && paint > 40 && rc.getHealth() > 80) {
            for (RobotInfo e : Globals.enemies) {
                if (!Globals.isTowerType(e.getType())) continue;
                int s = 2000 - Globals.myLoc.distanceSquaredTo(e.getLocation()) * 3;
                if (s > best) { best = s; target = e.getLocation(); }
            }
        }

        if (target != null) {
            boolean targetMusuh = false;
            for (RobotInfo e : Globals.enemies) {
                if (Globals.isTowerType(e.getType()) && e.getLocation().equals(target)) {
                    targetMusuh = true;
                    break;
                }
            }
            if (targetMusuh) {
                int dist = Globals.myLoc.distanceSquaredTo(target);
                if (rc.isActionReady() && dist > 9) Navigation.moveToward(target);
                else if (dist <= 9) Navigation.retreatFrom(target);
                else if (Globals.round % 2 == 0) Navigation.moveToward(target);
            } else {
                Navigation.pathTo(target);
            }
        } else {
            moveEkspansi();
        }
    }

    static void moveEkspansi() throws GameActionException {
        if (!Navigation.moveExpansion()) {
            // refresh target kalo sudah nyampe atau belum ada
            if (exploreTarget == null || Globals.myLoc.distanceSquaredTo(exploreTarget) <= 9) {
                idleCount++;
                if (idleCount > 10) {
                    Globals.symType = (Globals.symType + 1) % 3;
                    idleCount = 0;
                }
                exploreTarget = nextExploreTarget();
            }
            Navigation.pathTo(exploreTarget);
        }
    }

    // eksplorasi lebih sistematis — prioritaskan prediksi musuh, lalu pojok map secara giliran
    static MapLocation nextExploreTarget() {
        // prioritas pertama: prediksi lokasi musuh
        MapLocation mt = Globals.firstMoneyTower();
        if (mt != null) return Globals.predictEnemy(mt);

        // kunjungi 4 pojok map secara bergantian berdasarkan kuadran + giliran
        // biar coverage map lebih rata
        int margin = 5;
        MapLocation[] corners = {
            new MapLocation(margin, margin),
            new MapLocation(Globals.mapW - margin, margin),
            new MapLocation(margin, Globals.mapH - margin),
            new MapLocation(Globals.mapW - margin, Globals.mapH - margin)
        };

        // tiap soldier mulai dari pojok berbeda sesuai kuadrannya
        MapLocation target = corners[(kuadran + cornerIdx) % 4];
        cornerIdx = (cornerIdx + 1) % 4;
        return target;
    }

    static MapLocation cariTilePolaBelumDicat(MapLocation ruin) throws GameActionException {
        RobotController rc = Globals.rc;
        MapLocation best = null;
        int bd = Integer.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                MapLocation loc = new MapLocation(ruin.x + dx, ruin.y + dy);
                if (!rc.canSenseLocation(loc)) continue;
                MapInfo ti = rc.senseMapInfo(loc);
                if (ti.isWall() || ti.hasRuin()) continue;
                PaintType mark = ti.getMark();
                if (mark == PaintType.EMPTY || mark == ti.getPaint()) continue;
                int d = Globals.myLoc.distanceSquaredTo(loc);
                if (d < bd) { bd = d; best = loc; }
            }
        }
        return best;
    }

    static void broadcastKlaim(MapLocation ruin) throws GameActionException {
        int msg = (3 << 12) | (ruin.x << 6) | ruin.y;
        for (RobotInfo ally : Globals.allies) {
            if (Globals.rc.canSendMessage(ally.getLocation(), msg)) {
                Globals.rc.sendMessage(ally.getLocation(), msg);
                break;
            }
        }
    }

    static UnitType pilihTowerType() {
        int paint = Globals.countTowerType(1);
        int money = Globals.countTowerType(0);
        if (paint < 2) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (money < paint) return UnitType.LEVEL_ONE_MONEY_TOWER;
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }
}
