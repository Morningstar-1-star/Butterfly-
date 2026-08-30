package com.example.bunkr.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BunkrDao {

    // Albums
    @Query("SELECT * FROM bunkr_albums ORDER BY createdAt DESC")
    fun getAllAlbumsFlow(): Flow<List<BunkrAlbumEntity>>

    @Query("SELECT * FROM bunkr_albums ORDER BY createdAt DESC")
    suspend fun getAllAlbumsList(): List<BunkrAlbumEntity>

    @Query("SELECT * FROM bunkr_albums WHERE albumId = :albumId LIMIT 1")
    suspend fun getAlbumById(albumId: String): BunkrAlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: BunkrAlbumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<BunkrAlbumEntity>)

    @Query("UPDATE bunkr_albums SET isEnabled = :isEnabled WHERE albumId = :albumId")
    suspend fun setAlbumEnabled(albumId: String, isEnabled: Boolean)

    @Query("DELETE FROM bunkr_albums WHERE albumId = :albumId")
    suspend fun deleteAlbum(albumId: String)

    @Query("DELETE FROM bunkr_albums")
    suspend fun clearAllAlbums()

    // Files
    @Query("SELECT * FROM bunkr_files ORDER BY orderIndex ASC, lastUpdated DESC")
    fun getAllFilesFlow(): Flow<List<BunkrFileEntity>>

    @Query("SELECT * FROM bunkr_files ORDER BY orderIndex ASC, lastUpdated DESC")
    suspend fun getAllFilesList(): List<BunkrFileEntity>

    @Query("SELECT * FROM bunkr_files WHERE albumId = :albumId ORDER BY orderIndex ASC")
    fun getFilesForAlbumFlow(albumId: String): Flow<List<BunkrFileEntity>>

    @Query("SELECT * FROM bunkr_files WHERE albumId = :albumId ORDER BY orderIndex ASC")
    suspend fun getFilesForAlbumList(albumId: String): List<BunkrFileEntity>

    @Query("SELECT * FROM bunkr_files WHERE fileId = :fileId LIMIT 1")
    suspend fun getFileById(fileId: String): BunkrFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: BunkrFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<BunkrFileEntity>)

    @Query("UPDATE bunkr_files SET streamUrl = :streamUrl, streamUrlExpiry = :expiry WHERE fileId = :fileId")
    suspend fun updateStreamUrl(fileId: String, streamUrl: String?, expiry: Long)

    @Query("UPDATE bunkr_files SET isAvailable = :isAvailable WHERE fileId = :fileId")
    suspend fun setFileAvailable(fileId: String, isAvailable: Boolean)

    @Query("DELETE FROM bunkr_files WHERE fileId = :fileId")
    suspend fun deleteFile(fileId: String)

    @Query("DELETE FROM bunkr_files WHERE albumId = :albumId")
    suspend fun deleteFilesForAlbum(albumId: String)

    @Query("DELETE FROM bunkr_files")
    suspend fun clearAllFiles()
}
