package com.maxence.offlinebaseltour.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startTime: Long,
    var endTime: Long? = null,
    var distanceMetres: Float = 0f
)

@Entity(tableName = "track_points")
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val latitude: Double,
    val longitude: Double,
    val time: Long,
    val accuracy: Float
)

@Dao
interface TrackDao {
    @Insert
    suspend fun insertTrack(track: Track): Long

    @Update
    suspend fun updateTrack(track: Track)

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): Track?

    @Query("SELECT * FROM tracks WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveTrack(): Track?

    @Insert
    suspend fun insertTrackPoint(point: TrackPoint): Long

    @Query("UPDATE tracks SET distanceMetres = :distance WHERE id = :trackId")
    suspend fun updateDistance(trackId: Long, distance: Float)

    @Transaction
    suspend fun savePointAndDistance(point: TrackPoint, distance: Float) {
        insertTrackPoint(point)
        updateDistance(point.trackId, distance)
    }

    @Query("SELECT * FROM tracks ORDER BY startTime DESC")
    suspend fun getAllTracks(): List<Track>

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY time ASC")
    suspend fun getPointsForTrack(trackId: Long): List<TrackPoint>

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrack(trackId: Long)

    @Query("DELETE FROM track_points WHERE trackId = :trackId")
    suspend fun deletePointsForTrack(trackId: Long)
}
