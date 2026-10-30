package com.whitebearcode02.checkmate.vehicle.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

/** The user scope is part of the primary key, not a caller supplied query convenience. */
@Entity(tableName = "accident_records", primaryKeys = {"owner_user_id", "event_id"})
public class AccidentEntity {
    @NonNull @ColumnInfo(name = "owner_user_id") public String ownerUserId;
    @NonNull @ColumnInfo(name = "event_id") public String eventId;
    @NonNull @ColumnInfo(name = "payload") public String payload;

    public AccidentEntity(@NonNull String ownerUserId, @NonNull String eventId, @NonNull String payload) {
        this.ownerUserId = ownerUserId;
        this.eventId = eventId;
        this.payload = payload;
    }
}
