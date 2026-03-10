package bot1;

import battlecode.common.*;

public class Robot extends Entity{
    MapInfo[][] map = new MapInfo[60][60];

    public Robot(RobotController rc){
        super(rc);

        for(MapInfo[] row : map){
            for(MapInfo info : row){
                info = null;
            }
        }

        scan();
    }

    @Override
    public void run() throws GameActionException {
        super.run();
        scan();
    }

    public void scan(){
        MapInfo[] surrounding = rc.senseNearbyMapInfos();
        for(MapInfo info : surrounding){
            map[info.getMapLocation().x][info.getMapLocation().y] = info;
        }
    }
}
