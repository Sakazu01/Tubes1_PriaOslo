package bot2;

import battlecode.common.*;

public class Soldier {

    static MapLocation assignedRuin = null;
    static MapLocation exploreTarget = null;
    static int idleCount = 0;
    static int kuadran = -1;
    static int cornerIdx = 0;

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

        // selesaikan tower kalau bisa
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

        // tandai ruin baru
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

        // kalau lagi fokus ruin, cat pola dulu — skip yang lain
        if (assignedRuin != null) {
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
            if (bestTile != null && rc.canAttack(bestTile)) {
                boolean sec = rc.senseMapInfo(bestTile).getMark() == PaintType.ALLY_SECONDARY;
                rc.attack(bestTile, sec);
            }
            return;
        }

        // serang tower musuh kalau ada dalam jangkauan
        for (RobotInfo e : Globals.enemies) {
            if (!Globals.isTowerType(e.getType())) continue;
            if (rc.canAttack(e.getLocation())) {
                rc.attack(e.getLocation());
                return;
            }
        }

        // cat tile non-sekutu terdekat
        MapLocation catTarget = null;
        int nd = Integer.MAX_VALUE;
        for (MapInfo info : Globals.nearby) {
            if (info.isWall() || info.hasRuin()) continue;
            if (Navigation.isAllyPaint(info.getPaint())) continue;
            if (!rc.canAttack(info.getMapLocation())) continue;
            int d = Globals.myLoc.distanceSquaredTo(info.getMapLocation());
            if (info.getPaint() == PaintType.EMPTY && d < nd) { nd = d; catTarget = info.getMapLocation(); }
        }
        if (catTarget != null) {
            boolean sec = rc.canSenseLocation(catTarget)
                && rc.senseMapInfo(catTarget).getMark() == PaintType.ALLY_SECONDARY;
            rc.attack(catTarget, sec);
        }
    }

    static void gerak() throws GameActionException {
        RobotController rc = Globals.rc;
        if (!rc.isMovementReady()) return;

        // refuel kalau hampir habis
        if (Globals.paint < 40) {
            MapLocation tower = Globals.nearestPaintTower(Globals.myLoc);
            if (tower == null) tower = Globals.nearestAllyTower(Globals.myLoc);
            if (tower != null) { Navigation.pathTo(tower); return; }
        }

        // prioritas utama: ruin yang sudah diklaim
        if (assignedRuin != null) {
            if (rc.canSenseLocation(assignedRuin) && rc.senseRobotAtLocation(assignedRuin) != null) {
                assignedRuin = null;
            } else {
                MapLocation dest = cariTilePolaBelumDicat(assignedRuin);
                Navigation.pathTo(dest != null ? dest : assignedRuin);
                return;
            }
        }

        // cari ruin baru terdekat
        MapLocation ruinTarget = null;
        int ruinDist = Integer.MAX_VALUE;
        for (MapInfo info : Globals.nearby) {
            if (!info.hasRuin()) continue;
            MapLocation loc = info.getMapLocation();
            if (rc.canSenseLocation(loc) && rc.senseRobotAtLocation(loc) != null) continue;
            if (Globals.isRuinClaimed(loc)) continue;
            int d = Globals.myLoc.distanceSquaredTo(loc);
            if (d < ruinDist) { ruinDist = d; ruinTarget = loc; }
        }
        if (ruinTarget != null) {
            assignedRuin = ruinTarget;
            Navigation.pathTo(ruinTarget);
            return;
        }

        // kiting tower musuh kalau ga ada ruin
        for (RobotInfo e : Globals.enemies) {
            if (!Globals.isTowerType(e.getType())) continue;
            int dist = Globals.myLoc.distanceSquaredTo(e.getLocation());
            if (dist <= 9) Navigation.retreatFrom(e.getLocation());
            else Navigation.moveToward(e.getLocation());
            return;
        }

        moveEkspansi();
    }

    static void moveEkspansi() throws GameActionException {
        if (!Navigation.moveExpansion()) {
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

    static MapLocation nextExploreTarget() {
        MapLocation mt = Globals.firstMoneyTower();
        if (mt != null) return Globals.predictEnemy(mt);

        int margin = 5;
        MapLocation[] corners = {
            new MapLocation(margin, margin),
            new MapLocation(Globals.mapW - margin, margin),
            new MapLocation(margin, Globals.mapH - margin),
            new MapLocation(Globals.mapW - margin, Globals.mapH - margin)
        };
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
