package com.nsk.sfmd.data;
import androidx.room.*;
@Database(entities = {TaskRecord.class}, version = 1, exportSchema = false)
public abstract class TaskDatabase extends RoomDatabase {
    public abstract TaskDao tasks();
}
