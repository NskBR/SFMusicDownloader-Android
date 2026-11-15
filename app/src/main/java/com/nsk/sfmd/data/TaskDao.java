package com.nsk.sfmd.data;
import androidx.room.*;
import java.util.List;
@Dao public interface TaskDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC") List<TaskRecord> all();
    @Insert(onConflict = OnConflictStrategy.REPLACE) void save(TaskRecord row);
    @Query("DELETE FROM downloads WHERE id = :id") void delete(String id);
}
