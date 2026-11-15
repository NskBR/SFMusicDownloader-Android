package com.nsk.sfmd.data;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName = "downloads")
public class TaskRecord {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String payload = "{}";
    public long createdAt;
}
