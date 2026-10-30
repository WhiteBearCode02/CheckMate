package com.whitebearcode02.checkmate.vehicle.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface AccidentDao {
    @Query("SELECT * FROM accident_records WHERE owner_user_id = :userId ORDER BY event_id")
    List<AccidentEntity> load(String userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(AccidentEntity entity);
}
