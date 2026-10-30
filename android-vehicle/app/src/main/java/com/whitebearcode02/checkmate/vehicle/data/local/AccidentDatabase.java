package com.whitebearcode02.checkmate.vehicle.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {AccidentEntity.class}, version = 1, exportSchema = true)
public abstract class AccidentDatabase extends RoomDatabase {
    public abstract AccidentDao accidents();
}
