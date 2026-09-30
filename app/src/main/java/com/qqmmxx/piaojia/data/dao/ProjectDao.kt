package com.qqmmxx.piaojia.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qqmmxx.piaojia.model.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteById(projectId: String)

    @Query("SELECT * FROM projects ORDER BY createdAt DESC, id DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: String): ProjectEntity?

    /** 响应式单项目查询，用于项目详情页 */
    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun getProjectFlowById(projectId: String): Flow<ProjectEntity?>

    @Query("UPDATE projects SET isCompleted = 1 WHERE id = :projectId")
    suspend fun markProjectAsCompleted(projectId: String)

    @Query("UPDATE projects SET isCompleted = 0 WHERE id = :projectId")
    suspend fun markProjectAsUncompleted(projectId: String)
}
