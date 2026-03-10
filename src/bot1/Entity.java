package bot1;

import battlecode.common.*;

public class Entity {
    RobotController rc;
    int count;

    MapInfo[][] map;
    final int MAP_HEIGHT;
    final int MAP_WIDTH;

    Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };
    
    public Entity(RobotController rc){
        this.rc = rc;
        this.count = 1;

        MAP_HEIGHT = rc.getMapHeight();
        MAP_WIDTH = rc.getMapWidth();
        map = new MapInfo[MAP_WIDTH][MAP_HEIGHT];
        init_map();
    }

    /**
     * @brief            run the entity's main logic. This is called once per turn.
     * @throws GameActionException
     */
    public void run() throws GameActionException {
        count++;
        
        Clock.yield();
    }

    /**
     * @brief            scan the surrounding area and store it into map. The only important info is
     *                   about terrain. Color information is not important as it is ephemeral.
     */
    public void scan(){
        MapInfo[] surrounding = rc.senseNearbyMapInfos();
        for(MapInfo info : surrounding){
            map[info.getMapLocation().x][info.getMapLocation().y] = info;
        }
    }

    /**
     * @brief            initialize this.map.
     */
    public void init_map(){
        for(int i = 0; i < MAP_WIDTH; i++){
            for(int j = 0; j < MAP_HEIGHT; j++){
                map[i][j] = null;
            }
        }
        scan();
    }

    /**
     * @brief            merge another entity's map knowledge into this one.
     *                   only fills in tiles that this entity doesn't already know about.
     *                   this preserves our own (possibly fresher) scan data while
     *                   gaining knowledge of tiles we've never seen.
     * @param otherMap   the other entity's map array.
     */
    public void updateMap(MapInfo[][] otherMap){
        for(int i = 0; i < MAP_WIDTH; i++){
            for(int j = 0; j < MAP_HEIGHT; j++){
                if(map[i][j] == null && otherMap[i][j] != null){
                    map[i][j] = otherMap[i][j];
                }
            }
        }
    }
}